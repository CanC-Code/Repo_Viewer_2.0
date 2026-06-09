package com.githubrepoexplorerai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubrepoexplorerai.domain.RagPromptBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val localLlmEngine: LocalLlmEngine,       // Your existing local inference interface
    private val documentRetriever: DocumentRetriever  // Your existing vector/search interface
) : ViewModel() {

    // Holds the continuous live state of the conversation
    private val _chatHistory = MutableStateFlow<List<ChatState.Message>>(emptyList())
    val chatHistory: StateFlow<List<ChatState.Message>> = _chatHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun sendMessage(query: String) {
        if (query.isBlank()) return

        // 1. Append User Message
        val userMsg = ChatState.Message(text = query, isUser = true)
        _chatHistory.value = _chatHistory.value + userMsg
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // 2. Retrieve Document Chunks
                val relevantChunks = documentRetriever.search(query, topK = 4)
                
                // 3. Build Context-Aware Prompt
                val prompt = RagPromptBuilder.buildPrompt(
                    query = query,
                    retrievedContext = relevantChunks,
                    chatHistory = _chatHistory.value // Pass history for correlation
                )

                // 4. Fire Local Inference
                val responseText = localLlmEngine.generateResponse(prompt)

                // 5. Append AI Response natively
                val aiMsg = ChatState.Message(text = responseText.trim(), isUser = false)
                _chatHistory.value = _chatHistory.value + aiMsg

            } catch (e: Exception) {
                val errorMsg = ChatState.Message(
                    text = "System needs enhanced context: I encountered a local processing error (${e.localizedMessage}). Please try asking differently.",
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

// Interfaces to ensure compile-time validity with your existing engine logic
interface LocalLlmEngine {
    suspend fun generateResponse(prompt: String): String
}

interface DocumentRetriever {
    suspend fun search(query: String, topK: Int): List<String>
}
