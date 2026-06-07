package com.explorer.ai.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class AppMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val body: String,
    val feedbackState: Int = 0
)

class GeminiService {
    private var model: GenerativeModel? = null

    fun initialize(apiKey: String) {
        model = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
    }

    fun isReady(): Boolean = model != null

    fun streamChatResponse(prompt: String, history: List<AppMessage>): Flow<String> = flow {
        val generativeModel = model ?: throw IllegalStateException("Model not initialized")

        // Map your app history to SDK content format
        val chat = generativeModel.startChat(
            history = history.dropLast(1).map {
                content(role = if (it.sender == "User") "user" else "model") { text(it.body) }
            }
        )

        // The SDK handles all SSE streaming internally
        chat.sendMessageStream(prompt).collect { response ->
            response.text?.let { emit(it) }
        }
    }
}
