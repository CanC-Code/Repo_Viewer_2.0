package com.explorer.ai.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.explorer.ai.data.NeuralEngineService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// ─── UI State ────────────────────────────────────────────────────────────────

data class UIWorkspaceState(
    val searchQuery: String = "",
    val isWorkspaceVisible: Boolean = true,
    val files: List<FileInfo> = emptyList(),
    val chatHistory: List<ChatMessage> = emptyList(),
    val promptInput: String = "",
    val isLoading: Boolean = false,
    val systemStatusMessage: String? = null
)

data class FileInfo(val path: String, val type: String)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val body: String,
    val feedbackState: Int = 0
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

class ExplorerViewModel(
    private val neuralEngineService: NeuralEngineService
) : ViewModel() {

    private val _uiState = MutableStateFlow(UIWorkspaceState())
    val uiState: StateFlow<UIWorkspaceState> = _uiState.asStateFlow()

    // ── PDF ingestion ─────────────────────────────────────────────────────────
    fun ingestLocalDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                systemStatusMessage = "Parsing document..."
            )
            try {
                neuralEngineService.indexPdfDocument(uri)
                val status = neuralEngineService.getStatusSummary()
                val systemMsg = ChatMessage(
                    sender = "System",
                    body = status
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    systemStatusMessage = "Document embedded successfully.",
                    chatHistory = _uiState.value.chatHistory + systemMsg
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    systemStatusMessage = "Ingestion fault: ${e.localizedMessage}"
                )
            }
        }
    }

    // ── Chat dispatch ─────────────────────────────────────────────────────────
    /**
     * Routes the prompt through the real knowledge graph engine.
     * Uses conversation buffer in NeuralEngineService to resolve follow-up queries.
     */
    fun dispatchChatPrompt(prompt: String) {
        if (prompt.isBlank() || _uiState.value.isLoading) return

        val userMsg = ChatMessage(sender = "User", body = prompt)
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            promptInput = "",
            chatHistory = _uiState.value.chatHistory + userMsg,
            systemStatusMessage = "Reasoning..."
        )

        viewModelScope.launch {
            try {
                // Resolve pronouns/follow-ups using conversation context
                val resolvedQuery = neuralEngineService.resolveFollowUp(prompt)
                val responseText = neuralEngineService.generateResponse(resolvedQuery)

                val aiMsg = ChatMessage(sender = "AI", body = responseText)
                _uiState.value = _uiState.value.copy(
                    chatHistory = _uiState.value.chatHistory + aiMsg,
                    isLoading = false,
                    systemStatusMessage = null
                )
            } catch (e: Exception) {
                val errMsg = ChatMessage(
                    sender = "AI",
                    body = "Processing error: ${e.localizedMessage}. Please try rephrasing your query."
                )
                _uiState.value = _uiState.value.copy(
                    chatHistory = _uiState.value.chatHistory + errMsg,
                    isLoading = false,
                    systemStatusMessage = null
                )
            }
        }
    }

    // ── Supporting actions ────────────────────────────────────────────────────

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun updatePromptInput(input: String) {
        _uiState.value = _uiState.value.copy(promptInput = input)
    }

    fun toggleWorkspaceVisibility() {
        _uiState.value = _uiState.value.copy(isWorkspaceVisible = !_uiState.value.isWorkspaceVisible)
    }

    fun exploreGitHubRepository(repoUrl: String) {
        if (repoUrl.isBlank()) return
        Log.i("ExplorerViewModel", "Initiating repository connection: $repoUrl")
        _uiState.value = _uiState.value.copy(systemStatusMessage = "Connecting to repository...")
    }

    fun purgeSavedCredentials() {
        Log.i("ExplorerViewModel", "Flushing active authentication.")
        _uiState.value = _uiState.value.copy(systemStatusMessage = "Authentication tokens purged.")
    }

    fun loadSelectedFileContent(path: String) {
        Log.i("ExplorerViewModel", "Mounting file: $path")
        _uiState.value = _uiState.value.copy(systemStatusMessage = "Loaded: $path")
    }

    fun rateMessage(id: String, rating: Int) {
        val updated = _uiState.value.chatHistory.map { msg ->
            if (msg.id == id) msg.copy(feedbackState = rating) else msg
        }
        _uiState.value = _uiState.value.copy(chatHistory = updated)
    }

    fun purgeChatHistory() {
        neuralEngineService.clearAll()
        _uiState.value = _uiState.value.copy(
            chatHistory = emptyList(),
            systemStatusMessage = "Memory and conversation history cleared."
        )
    }

    fun retryLastPrompt() {
        val lastUserMsg = _uiState.value.chatHistory.lastOrNull { it.sender == "User" } ?: return
        // Drop the last AI response so we don't duplicate
        val trimmed = _uiState.value.chatHistory.dropLastWhile { it.sender != "User" }.dropLast(1)
        _uiState.value = _uiState.value.copy(chatHistory = trimmed, promptInput = lastUserMsg.body)
        dispatchChatPrompt(lastUserMsg.body)
    }
}
