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
 * GeminiService — direct REST client for the Gemini Developer API.
 *
 * WHY THIS EXISTS:
 * com.google.ai.client.generativeai was archived on 2025-12-16 and is no longer
 * maintained. Its final version (0.9.0) carries a kotlinx.serialization schema that
 * does not match the current GRpcError payload shape, causing a
 * MissingFieldException on every API error. Additionally, gemini-1.5-flash* model
 * aliases are unavailable on new API key projects as of April 2025.
 *
 * The Firebase AI Logic SDK (the official replacement) requires a Firebase project
 * and google-services.json — incompatible with this app's direct API key design.
 *
 * Solution: call the v1beta REST endpoint over OkHttp, which is already a declared
 * dependency. No new libraries required. Model: gemini-2.5-flash (stable, free tier).
 */
class GeminiService {

    private var apiKey: String? = null

    // Extended timeout — Gemini 2.5 Flash with thinking can take a few seconds
    // before the first SSE chunk arrives.
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val MODEL = "gemini-2.5-flash"
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

    fun isReady(): Boolean = !apiKey.isNullOrBlank()

    /**
     * Streams a chat response via Server-Sent Events (SSE).
     * Emits incremental text chunks as they arrive.
     *
     * REST endpoint:
     *   POST /v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=KEY
     *
     * SSE line format: "data: {JSON}" — we strip the prefix and parse
     * candidates[0].content.parts[0].text from each chunk.
     */
    fun streamChatResponse(prompt: String, history: List<AppMessage>): Flow<String> = flow {
        val key = apiKey ?: throw IllegalStateException("API key not set")

        val url = "$BASE_URL/$MODEL:streamGenerateContent?alt=sse&key=$key"

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

                // Read SSE lines. Each data line is:
                //   data: {"candidates":[{"content":{"parts":[{"text":"..."}],...},...}],...}
                // Blank lines separate events — we only care about data lines.
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break

                    if (!line.startsWith("data: ")) continue
                    val jsonStr = line.removePrefix("data: ").trim()
                    if (jsonStr == "[DONE]" || jsonStr.isEmpty()) continue

                    try {
                        val json = JSONObject(jsonStr)
                        val candidates = json.optJSONArray("candidates") ?: continue
                        if (candidates.length() == 0) continue

                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content") ?: continue
                        val parts = content.optJSONArray("parts") ?: continue

                        for (i in 0 until parts.length()) {
                            val text = parts.getJSONObject(i).optString("text", "")
                            if (text.isNotEmpty()) {
                                // Emit on the flow — collected upstream on Main dispatcher
                                emit(text)
                            }
                        }
                    } catch (_: Exception) {
                        // Malformed chunk — skip and continue streaming
                    }
                }
            }
        }
    }

    /**
     * Builds the JSON request body.
     *
     * Structure:
     * {
     *   "system_instruction": { "parts": [{"text": "..."}] },
     *   "contents": [
     *     { "role": "user",  "parts": [{"text": "..."}] },
     *     { "role": "model", "parts": [{"text": "..."}] },
     *     ...
     *     { "role": "user",  "parts": [{"text": "<current prompt>"}] }
     *   ]
     * }
     *
     * The Gemini REST API requires that:
     *  - roles alternate user/model
     *  - the final turn is always "user"
     *  - "System" sender messages from app state are excluded
     */
    private fun buildRequestBody(prompt: String, history: List<AppMessage>): String {
        val root = JSONObject()

        // System instruction
        val systemInstruction = JSONObject().put(
            "parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))
        )
        root.put("system_instruction", systemInstruction)

        // Build contents array from history + current prompt
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

        // Append the current user prompt as the final turn
        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        )

        root.put("contents", contents)

        return root.toString()
    }
}
