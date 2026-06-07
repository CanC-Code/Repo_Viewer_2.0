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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

data class AppMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, 
    val body: String,
    val feedbackState: Int = 0 
)

class GeminiService {
    private var apiKey: String? = null
    private val client = OkHttpClient()

    fun initialize(key: String) {
        apiKey = key
    }

    fun isReady(): Boolean = !apiKey.isNullOrBlank()

    fun streamChatResponse(prompt: String, history: List<AppMessage>): Flow<String> = flow {
        val key = apiKey ?: throw IllegalStateException("Model pipeline not initialized")

        val historyToProcess = history.dropLast(1).filter { it.sender == "User" || it.sender == "AI" }
        
        val fullContext = buildString {
            appendLine("You are an expert development engine specializing in repository logic and clear mobile code reading.")
            if (historyToProcess.isNotEmpty()) {
                appendLine("--- HISTORY ---")
                historyToProcess.forEach { msg -> appendLine("[${msg.sender.uppercase()}]: ${msg.body}") }
            }
            appendLine("[PROMPT]: $prompt")
        }

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", fullContext) })
                    })
                })
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        
        // Corrected URL: Removed alt=sse and confirmed standard endpoint path
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent?key=$key"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

        if (!response.isSuccessful) {
            val errorMsg = response.body?.string() ?: "Unknown API Error"
            throw IOException("Pipeline 404/Error (HTTP ${response.code}): $errorMsg")
        }

        response.body?.byteStream()?.let { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = reader.readLine()
            
            while (line != null) {
                // Look for lines containing text data
                if (line.startsWith("data: ")) {
                    val jsonStr = line.removePrefix("data: ").trim()
                    if (jsonStr.isNotEmpty()) {
                        try {
                            val jsonChunk = JSONObject(jsonStr)
                            val candidates = jsonChunk.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val content = candidates.getJSONObject(0).optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null && parts.length() > 0) {
                                    val text = parts.getJSONObject(0).optString("text", "")
                                    if (text.isNotEmpty()) emit(text)
                                }
                            }
                        } catch (e: Exception) { /* Skip partials */ }
                    }
                }
                line = reader.readLine()
            }
        }
    }
}
