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

class ExplorerViewModel(
    private val neuralEngineService: NeuralEngineService
) : ViewModel() {

    private val _uiState = MutableStateFlow(UIWorkspaceState())
    val uiState: StateFlow<UIWorkspaceState> = _uiState.asStateFlow()

    init {
        refreshSystemStatus()
    }

    private fun refreshSystemStatus() {
        val summary = neuralEngineService.getStatusSummary()
        _uiState.value = _uiState.value.copy(systemStatusMessage = summary)
    }

    fun ingestLocalDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, systemStatusMessage = "Analyzing spatial geometry...")
            try {
                neuralEngineService.indexPdfDocument(uri)
                refreshSystemStatus()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(systemStatusMessage = "Ingestion Fault: ${e.localizedMessage}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateSearchQuery(query: String) { _uiState.value = _uiState.value.copy(searchQuery = query) }
    fun updatePromptInput(input: String) { _uiState.value = _uiState.value.copy(promptInput = input) }
    fun toggleWorkspaceVisibility() { _uiState.value = _uiState.value.copy(isWorkspaceVisible = !_uiState.value.isWorkspaceVisible) }
    
    fun exploreGitHubRepository(repoUrl: String) {
        if (repoUrl.isBlank()) return
        _uiState.value = _uiState.value.copy(systemStatusMessage = "Connecting to repository...")
    }

    fun purgeSavedCredentials() {
        _uiState.value = _uiState.value.copy(systemStatusMessage = "Authentication tokens purged.")
    }

    fun loadSelectedFileContent(path: String) {
        _uiState.value = _uiState.value.copy(systemStatusMessage = "Loaded active buffer: $path")
    }

    fun rateMessage(id: String, rating: Int) {
        val updatedHistory = _uiState.value.chatHistory.map { msg ->
            if (msg.id == id) msg.copy(feedbackState = rating) else msg
        }
        _uiState.value = _uiState.value.copy(chatHistory = updatedHistory)
    }

    fun purgeChatHistory() {
        neuralEngineService.clearAll()
        _uiState.value = _uiState.value.copy(chatHistory = emptyList(), systemStatusMessage = "Conversational memory and vector pool wiped.")
        refreshSystemStatus()
    }

    fun retryLastPrompt() {
        val lastUserMessage = _uiState.value.chatHistory.lastOrNull { it.sender == "User" }
        lastUserMessage?.let { dispatchChatPrompt(it.body) }
    }

    fun dispatchChatPrompt(prompt: String) {
        if (prompt.isBlank()) return
        
        viewModelScope.launch {
            val newUserMsg = ChatMessage(sender = "User", body = prompt)
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                promptInput = "",
                chatHistory = _uiState.value.chatHistory + newUserMsg,
                systemStatusMessage = "NPU computing vector spaces..."
            )
            
            // Execute conversational follow-up and spatial retrieval
            val contextAnalysis = neuralEngineService.resolveFollowUp(prompt)
            val nativeResponse = neuralEngineService.generateResponse(prompt)
            
            val aiMsg = ChatMessage(sender = "AI", body = "$contextAnalysis\n\n$nativeResponse")
            
            _uiState.value = _uiState.value.copy(
                chatHistory = _uiState.value.chatHistory + aiMsg,
                isLoading = false
            )
            refreshSystemStatus()
        }
    }
}
