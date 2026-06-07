package com.explorer.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "User", "AI", or "System"
    val body: String,
    val feedbackState: Int = 0 // 1 = Like, -1 = Dislike, 0 = None
)

/**
 * GeminiService — direct REST client for the Gemini Developer API (v1beta).
 *
 * Uses OkHttp (already a dependency) to call streamGenerateContent with SSE.
 * No Gemini SDK required — com.google.ai.client.generativeai was archived 2025-12-16.
 *
 * Model is configurable at runtime via [setModel]. Defaults to gemini-2.5-flash.
 * The 503 "high demand" error on gemini-2.5-flash is a free-tier capacity spike;
 * switching to gemini-2.5-flash-lite or gemini-3-flash-preview resolves it.
 */
class GeminiService {

    private var apiKey: String? = null
    private var currentModel: String = PreferencesManager.DEFAULT_MODEL.id

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val SYSTEM_PROMPT =
            "You are an expert development engine specializing in repository logic, " +
            "reverse engineering, and clear mobile code reading. " +
            "Answer queries directly. Keep code breakdowns clean, syntactically transparent, and robust. " +
            "When provided with external document context, anchor your responses accurately to the supplied text."
    }

    fun initialize(key: String) {
        apiKey = key.trim()
    }

    fun setModel(modelId: String) {
        currentModel = modelId
    }

    fun isReady(): Boolean = !apiKey.isNullOrBlank()

    fun streamChatResponse(prompt: String, history: List<AppMessage>): Flow<String> = flow {
        val key = apiKey ?: throw IllegalStateException("API key not set")
        val model = currentModel

        val url = "$BASE_URL/$model:streamGenerateContent?alt=sse&key=$key"
        val requestBody = buildRequestBody(prompt, history)

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .build()

        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "empty error body"
                    throw Exception("HTTP ${response.code}: $errorBody")
                }

                val source = response.body?.source()
                    ?: throw Exception("Empty response body from Gemini API")

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data: ")) continue
                    val jsonStr = line.removePrefix("data: ").trim()
                    if (jsonStr == "[DONE]" || jsonStr.isEmpty()) continue

                    try {
                        val json = JSONObject(jsonStr)
                        val candidates = json.optJSONArray("candidates") ?: continue
                        if (candidates.length() == 0) continue
                        val content = candidates.getJSONObject(0).optJSONObject("content") ?: continue
                        val parts = content.optJSONArray("parts") ?: continue
                        for (i in 0 until parts.length()) {
                            val text = parts.getJSONObject(i).optString("text", "")
                            if (text.isNotEmpty()) emit(text)
                        }
                    } catch (_: Exception) { /* malformed chunk — skip */ }
                }
            }
        }
    }

    private fun buildRequestBody(prompt: String, history: List<AppMessage>): String {
        val root = JSONObject()

        root.put(
            "system_instruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
        )

        val contents = JSONArray()
        history
            .filter { it.sender == "User" || it.sender == "AI" }
            .forEach { msg ->
                val role = if (msg.sender == "User") "user" else "model"
                contents.put(
                    JSONObject()
                        .put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", msg.body)))
                )
            }
        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        )

        root.put("contents", contents)
        return root.toString()
    }
}
