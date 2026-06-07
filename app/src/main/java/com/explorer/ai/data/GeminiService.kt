package com.explorer.ai.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class AppMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "User", "AI", or "System"
    val body: String,
    val feedbackState: Int = 0 // 1 = Like, -1 = Dislike, 0 = None
)

class GeminiService {
    private var generativeModel: GenerativeModel? = null

    fun initialize(apiKey: String) {
        generativeModel = GenerativeModel(
            // "gemini-1.5-flash" is NOT_FOUND on the v1beta endpoint used by this SDK.
            // "gemini-1.5-flash-latest" is the correct alias that resolves on v1beta.
            modelName = "gemini-1.5-flash-latest",
            apiKey = apiKey,
            systemInstruction = content {
                text("You are an expert development engine specializing in repository logic, reverse engineering, and clear mobile code reading. " +
                     "Answer queries directly. Keep code breakdowns clean, syntactically transparent, and robust. " +
                     "When provided with external document context, anchor your responses accurately to the supplied text.")
            }
        )
    }

    fun isReady(): Boolean = generativeModel != null

    fun streamChatResponse(prompt: String, history: List<AppMessage>): Flow<String> = flow {
        val model = generativeModel ?: throw IllegalStateException("Model pipeline has not been active yet")

        // Map application history cleanly into native GenAI SDK roles, ignoring local system warnings
        val sdkHistory = history.filter { it.sender == "User" || it.sender == "AI" }.map { msg ->
            content(role = if (msg.sender == "User") "user" else "model") { text(msg.body) }
        }

        val chat = model.startChat(history = sdkHistory)

        // Stream parts reactively back to the rendering components
        chat.sendMessageStream(prompt).collect { responseChunk ->
            responseChunk.text?.let { emit(it) }
        }
    }
}
