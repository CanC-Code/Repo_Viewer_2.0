package com.explorer.ai.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class AppMessage(
    val sender: String, // "User", "AI", or "System"
    val body: String
)

class GeminiService {
    private var generativeModel: GenerativeModel? = null

    fun initialize(apiKey: String) {
        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
    }

    fun isReady(): Boolean = generativeModel != null

    fun streamChatResponse(prompt: String, history: List<AppMessage>): Flow<String> = flow {
        val model = generativeModel ?: throw IllegalStateException("Model pipeline has not been active yet")

        // Transform system histories directly into compliant GenAI content frames
        val requestContext = content {
            // Hard system-instruction injected directly to force output formatting structure
            text("System instructions: You are an expert development engine specializing in repository logic and clear mobile code reading. " +
                 "Answer queries directly. Keep code breakdowns clean, syntactically transparent, and robust.")
            
            // Re-inflate conversational timeline frames while pruning buffer overflows
            history.takeLast(6).forEach { msg ->
                when (msg.sender) {
                    "User" -> user { text(msg.body) }
                    "AI" -> model { text(msg.body) }
                }
            }
            user { text(prompt) }
        }

        // Stream parts reactively back to the rendering components
        model.generateContentStream(requestContext).collect { responseChunk ->
            responseChunk.text?.let { emit(it) }
        }
    }
}
