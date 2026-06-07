package com.explorer.ai.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.MissingFieldException

data class AppMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "User", "AI", or "System"
    val body: String,
    val feedbackState: Int = 0 // 1 = Like, -1 = Dislike, 0 = None
)

class GeminiService {
    private var model: GenerativeModel? = null

    // FIX 1: "gemini-1.5-flash" is removed from the v1beta endpoint.
    // Use "gemini-2.0-flash" (stable, fast, free tier available).
    // If you need 1.5 specifically, the correct string is "gemini-1.5-flash-latest".
    private var currentModelName: String = "gemini-2.0-flash"

    fun initialize(apiKey: String, modelName: String = currentModelName) {
        currentModelName = modelName
        model = GenerativeModel(
            modelName = currentModelName,
            apiKey = apiKey,
            systemInstruction = content {
                text("You are an expert development engine specializing in repository logic, reverse engineering, and clear mobile code reading. Answer queries directly. Keep code breakdowns clean, syntactically transparent, and robust. When provided with external document context, anchor your responses accurately to the supplied text.")
            }
        )
    }

    fun getCurrentModelName(): String = currentModelName

    fun isReady(): Boolean = model != null

    fun streamChatResponse(prompt: String, history: List<AppMessage>): Flow<String> = flow {
        val generativeModel = model ?: throw IllegalStateException("Model not initialized")

        val chat = generativeModel.startChat(
            history = history.dropLast(1).filter { it.sender == "User" || it.sender == "AI" }.map {
                content(role = if (it.sender == "User") "user" else "model") { text(it.body) }
            }
        )

        chat.sendMessageStream(prompt).collect { response ->
            response.text?.let { emit(it) }
        }
    }.catch { exception ->
        // FIX 2: SDK 0.9.0 crashes with MissingFieldException when Google's API
        // returns a 404/error JSON that lacks the "details" field the SDK expects.
        // We unwrap it here into a readable message instead of letting it propagate
        // as an unhandled kotlinx.serialization crash.
        val message = when (exception) {
            is MissingFieldException ->
                "API error: Model '${currentModelName}' not found or not supported. " +
                "Check your model name and API key permissions. (${exception.message})"
            else -> exception.localizedMessage ?: "Unknown streaming error"
        }
        throw RuntimeException(message, exception)
    }
}
