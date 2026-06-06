package com.explorer.ai.data

import com.google.ai.client.generativeai.GenerativeModel
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

        // Transform system histories into a single composite string block 
        // to pass cleanly into the GenAI content stream.
        val fullContext = buildString {
            appendLine("System instructions: You are an expert development engine specializing in repository logic and clear mobile code reading. Answer queries directly. Keep code breakdowns clean, syntactically transparent, and robust.")
            appendLine()
            
            // Re-inflate conversational timeline frames
            history.takeLast(6).forEach { msg ->
                appendLine("[${msg.sender.uppercase()}]:")
                appendLine(msg.body)
                appendLine()
            }
            
            appendLine("[USER]:")
            appendLine(prompt)
        }

        // Stream parts reactively back to the rendering components
        model.generateContentStream(fullContext).collect { responseChunk ->
            responseChunk.text?.let { emit(it) }
        }
    }
}
