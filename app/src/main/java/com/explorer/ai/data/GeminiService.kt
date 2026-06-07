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
    private var model: GenerativeModel? = null

    fun initialize(apiKey: String) {
        model = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            systemInstruction = content {
                text("You are an expert development engine specializing in repository logic, reverse engineering, and clear mobile code reading. Answer queries directly. Keep code breakdowns clean, syntactically transparent, and robust. When provided with external document context, anchor your responses accurately to the supplied text.")
            }
        )
    }

    fun isReady(): Boolean = model != null

    fun streamChatResponse(prompt: String, history: List<AppMessage>): Flow<String> = flow {
        val generativeModel = model ?: throw IllegalStateException("Model not initialized")

        // Map the internal app history to the strict SDK Content structure
        val chat = generativeModel.startChat(
            history = history.dropLast(1).filter { it.sender == "User" || it.sender == "AI" }.map {
                content(role = if (it.sender == "User") "user" else "model") { text(it.body) }
            }
        )

        // The SDK automatically maps Server-Sent Events to a standard Kotlin Flow
        chat.sendMessageStream(prompt).collect { response ->
            response.text?.let { emit(it) }
        }
    }
}
