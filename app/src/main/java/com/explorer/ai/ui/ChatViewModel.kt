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
                // Vector engine isolates text blocks along with physical graphic references
                val relevantChunks = documentRetriever.search(query, topK = 4)
                
                val prompt = RagPromptBuilder.buildPrompt(
                    query = query,
                    retrievedContext = relevantChunks,
                    chatHistory = _chatHistory.value
                )

                val responseText = localLlmEngine.generateResponse(prompt)

                val aiMsg = ChatState.Message(text = responseText.trim(), isUser = false)
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
        val isUser: Boolean
    )
}

interface LocalLlmEngine {
    suspend fun generateResponse(prompt: String): String
}

interface DocumentRetriever {
    suspend fun search(query: String, topK: Int): List<String>
}
