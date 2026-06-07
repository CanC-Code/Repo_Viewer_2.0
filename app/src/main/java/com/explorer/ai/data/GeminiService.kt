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
            modelName = "gemini-1.5-flash",
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

        // Build a robust transcript to bypass strict SDK role-alternation crashes.
        // We drop the LAST message in history because the ViewModel already added the current prompt to the UI state.
        val historyToProcess = history.dropLast(1).filter { it.sender == "User" || it.sender == "AI" }
        
        val fullContext = buildString {
            if (historyToProcess.isNotEmpty()) {
                appendLine("--- PREVIOUS CONVERSATION HISTORY ---")
                historyToProcess.forEach { msg ->
                    appendLine("[${msg.sender.uppercase()}]:")
                    appendLine(msg.body)
                    appendLine()
                }
                appendLine("--- END HISTORY ---")
                appendLine()
            }
            appendLine("[USER CURRENT PROMPT]:")
            appendLine(prompt)
        }

        // Generate the stream using the zero-shot robust string block
        model.generateContentStream(fullContext).collect { responseChunk ->
            responseChunk.text?.let { emit(it) }
        }
    }
}
