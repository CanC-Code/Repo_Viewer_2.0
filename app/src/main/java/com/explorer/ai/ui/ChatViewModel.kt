package com.explorer.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.explorer.ai.domain.RagPromptBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val localLlmEngine: LocalLlmEngine,
    private val documentRetriever: DocumentRetriever
) : ViewModel() {

    private val _chatHistory = MutableStateFlow<List<ChatState.Message>>(emptyList())
    val chatHistory: StateFlow<List<ChatState.Message>> = _chatHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun sendMessage(query: String) {
        if (query.isBlank()) return

        val userMsg = ChatState.Message(text = query, isUser = true)
        _chatHistory.value = _chatHistory.value + userMsg
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val relevantChunks = documentRetriever.search(query, topK = 4)
                
                val prompt = RagPromptBuilder.buildPrompt(
                    query = query,
                    retrievedContext = relevantChunks,
                    chatHistory = _chatHistory.value
                )

                // Pass to the NPU via JNI bridge
                val responseText = localLlmEngine.generateResponse(prompt)

                // Parse the native string for our internal GUI triggers
                val trigger = when {
                    responseText.contains("[DIAGRAM_TRIGGER:MEMORY_MAP]") -> "MEMORY_MAP"
                    responseText.contains("[DIAGRAM_TRIGGER:ARCH_FLOW]") -> "ARCH_FLOW"
                    else -> null
                }
                
                // Strip the token so it doesn't render as raw text to the user
                val cleanText = responseText.replace(Regex("\\[DIAGRAM_TRIGGER:[A-Z_]+\\]"), "").trim()

                val aiMsg = ChatState.Message(text = cleanText, isUser = false, diagramTrigger = trigger)
                _chatHistory.value = _chatHistory.value + aiMsg

            } catch (e: Exception) {
                val errorMsg = ChatState.Message(
                    text = "System optimization warning: Internal processing loop requires updated parameters (${e.localizedMessage}). Let's re-verify the file layout.",
                    isUser = false
                )
                _chatHistory.value = _chatHistory.value + errorMsg
            } finally {
                _isLoading.value = false
            }
        }
    }
}

object ChatState {
    data class Message(
        val text: String, 
        val isUser: Boolean,
        val diagramTrigger: String? = null // Enables visual state switching
    )
}

interface LocalLlmEngine {
    suspend fun generateResponse(prompt: String): String
}

interface DocumentRetriever {
    suspend fun search(query: String, topK: Int): List<String>
}
