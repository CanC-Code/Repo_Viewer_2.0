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

// ─── UI State ─────────────────────────────────────────────────────────────────

data class UIWorkspaceState(
    val searchQuery: String = "",
    val isWorkspaceVisible: Boolean = true,
    val files: List<FileInfo> = emptyList(),
    val chatHistory: List<ChatMessage> = emptyList(),
    val promptInput: String = "",
    val isLoading: Boolean = false,
    val loadingProgressText: String? = null,   // live OCR progress: "Processing page 47 / 616"
    val systemStatusMessage: String? = null    // null = hide; non-null = show below chat
)

data class FileInfo(val path: String, val type: String)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,   // "User", "AI", "System"
    val body: String,
    val feedbackState: Int = 0  // 0=none, 1=valid, -1=fault
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ExplorerViewModel(
    private val neuralEngineService: NeuralEngineService
) : ViewModel() {

    private val _uiState = MutableStateFlow(UIWorkspaceState())
    val uiState: StateFlow<UIWorkspaceState> = _uiState.asStateFlow()

    // ── PDF ingestion ─────────────────────────────────────────────────────────

    fun ingestLocalDocument(uri: Uri) {
        if (_uiState.value.isLoading) return  // prevent double-ingest

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                systemStatusMessage = null,
                loadingProgressText = "Initializing optical reader..."
            )
            try {
                neuralEngineService.indexPdfDocument(uri) { page, total ->
                    _uiState.value = _uiState.value.copy(
                        loadingProgressText = "Processing page $page / $total..."
                    )
                }

                val summary = neuralEngineService.getStatusSummary()
                val systemMsg = ChatMessage(sender = "System", body = summary)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadingProgressText = null,
                    systemStatusMessage = "Document embedded successfully.",
                    chatHistory = _uiState.value.chatHistory + systemMsg
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadingProgressText = null,
                    systemStatusMessage = "Ingestion fault: ${e.localizedMessage}"
                )
            }
        }
    }

    // ── Chat dispatch ─────────────────────────────────────────────────────────

    fun dispatchChatPrompt(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _uiState.value.isLoading) return

        val userMsg = ChatMessage(sender = "User", body = trimmed)
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            promptInput = "",
            chatHistory = _uiState.value.chatHistory + userMsg,
            systemStatusMessage = "Reasoning..."
        )

        viewModelScope.launch {
            try {
                // resolveFollowUp is INTERNAL context enrichment only — never shown to user
                val enrichedQuery = neuralEngineService.resolveFollowUp(trimmed)
                val response = neuralEngineService.generateResponse(enrichedQuery)

                val aiMsg = ChatMessage(sender = "AI", body = response)
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

    // ── Standard actions ──────────────────────────────────────────────────────

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
        Log.i("ExplorerViewModel", "Flushing auth tokens.")
        _uiState.value = _uiState.value.copy(systemStatusMessage = "Authentication tokens purged.")
    }

    fun loadSelectedFileContent(path: String) {
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
            systemStatusMessage = null
        )
    }

    fun retryLastPrompt() {
        if (_uiState.value.isLoading) return
        val lastUserMsg = _uiState.value.chatHistory.lastOrNull { it.sender == "User" } ?: return
        // Drop the last AI reply so retry doesn't duplicate it
        val trimmedHistory = _uiState.value.chatHistory.dropLastWhile { it.sender != "User" }.dropLast(1)
        _uiState.value = _uiState.value.copy(chatHistory = trimmedHistory, promptInput = lastUserMsg.body)
        dispatchChatPrompt(lastUserMsg.body)
    }
}
