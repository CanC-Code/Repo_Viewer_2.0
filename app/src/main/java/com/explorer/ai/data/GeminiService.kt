package com.explorer.ai.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

data class AppMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "User", "AI", or "System"
    val body: String,
    val feedbackState: Int = 0 // 1 = Like, -1 = Dislike, 0 = None
)

class GeminiService {
    private var model: GenerativeModel? = null

    // FIX: "gemini-1.5-flash" was removed from the v1beta endpoint used by SDK 0.9.0.
    // "gemini-2.0-flash" is the correct current model string.
    // If you need 1.5 specifically, use "gemini-1.5-flash-latest".
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
        // The SDK 0.9.0 wraps deserialization failures (e.g. missing "details" field
        // in 404 error responses) inside its own exception types before propagating.
        // Catching Exception broadly here ensures the ViewModel's .catch {} handler
        // always receives a clean, readable message rather than a raw SDK crash.
        val message = when {
            exception.message?.contains("NOT_FOUND") == true ||
            exception.message?.contains("404") == true ->
                "Model '$currentModelName' not found or not supported by this API key. " +
                "Verify the model name and that your key has access to it."
            exception.message?.contains("MissingField") == true ||
            exception.message?.contains("details") == true ->
                "API response parse error. This usually means an invalid model name or " +
                "revoked API key. (${exception.message})"
            else -> exception.localizedMessage ?: "Unknown streaming error"
        }
        throw RuntimeException(message, exception)
    }
}
