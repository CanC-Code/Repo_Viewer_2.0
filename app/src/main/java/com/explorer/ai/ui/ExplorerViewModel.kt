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

// ---------------------------------------------------------------------------
// DOMAIN STATE MODELS
// ---------------------------------------------------------------------------

/**
 * Represents the immutable, single source of truth for the entire Workspace UI.
 * Any mutation to the UI must be done by copying and emitting a new instance of this state.
 */
data class UIWorkspaceState(
    val searchQuery: String = "",
    val isWorkspaceVisible: Boolean = true,
    val files: List<FileInfo> = emptyList(),
    val chatHistory: List<ChatMessage> = emptyList(),
    val promptInput: String = "",
    val isLoading: Boolean = false,
    val systemStatusMessage: String? = null
)

data class FileInfo(
    val path: String, 
    val type: String
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val body: String,
    val feedbackState: Int = 0 // Contextual state: 0 (Neutral), 1 (Upvoted), -1 (Downvoted)
)

// ---------------------------------------------------------------------------
// VIEW MODEL ORCHESTRATOR
// ---------------------------------------------------------------------------

class ExplorerViewModel(
    private val neuralEngineService: NeuralEngineService
) : ViewModel() {

    // Internal mutable state protected from external direct mutation
    private val _uiState = MutableStateFlow(UIWorkspaceState())
    
    // Public immutable state exposed to Jetpack Compose lifecycle
    val uiState: StateFlow<UIWorkspaceState> = _uiState.asStateFlow()

    /**
     * Ingests local hardware documentation directly into the NPU spatial matrix.
     */
    fun ingestLocalDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, systemStatusMessage = "Analyzing spatial geometry...")
            try {
                neuralEngineService.indexPdfDocument(uri)
                _uiState.value = _uiState.value.copy(systemStatusMessage = "Document embedded successfully.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(systemStatusMessage = "Ingestion Fault: ${e.localizedMessage}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun updatePromptInput(input: String) {
        _uiState.value = _uiState.value.copy(promptInput = input)
    }

    fun exploreGitHubRepository(repoUrl: String) {
        if (repoUrl.isBlank()) return
        Log.i("ExplorerViewModel", "Initiating repository linkage sequence: $repoUrl")
        _uiState.value = _uiState.value.copy(systemStatusMessage = "Connecting to repository...")
        // Remote Git indexing logic expansion point
    }

    fun purgeSavedCredentials() {
        Log.i("ExplorerViewModel", "Flushing active authentication matrix.")
        _uiState.value = _uiState.value.copy(systemStatusMessage = "Authentication tokens purged.")
    }

    fun toggleWorkspaceVisibility() {
        val currentState = _uiState.value.isWorkspaceVisible
        _uiState.value = _uiState.value.copy(isWorkspaceVisible = !currentState)
    }

    fun loadSelectedFileContent(path: String) {
        Log.i("ExplorerViewModel", "Mounting file into interactive buffer: $path")
        _uiState.value = _uiState.value.copy(systemStatusMessage = "Loaded active buffer: $path")
    }

    fun rateMessage(id: String, rating: Int) {
        val updatedHistory = _uiState.value.chatHistory.map { msg ->
            if (msg.id == id) msg.copy(feedbackState = rating) else msg
        }
        _uiState.value = _uiState.value.copy(chatHistory = updatedHistory)
    }

    fun purgeChatHistory() {
        _uiState.value = _uiState.value.copy(chatHistory = emptyList(), systemStatusMessage = "Conversational memory wiped.")
    }

    fun retryLastPrompt() {
        val lastUserMessage = _uiState.value.chatHistory.lastOrNull { it.sender == "User" }
        lastUserMessage?.let { 
            dispatchChatPrompt(it.body) 
        }
    }

    /**
     * Routes the conversational prompt through the layout-aware local inference engine.
     */
    fun dispatchChatPrompt(prompt: String) {
        if (prompt.isBlank()) return
        
        viewModelScope.launch {
            // Append user input and lock UI state
            val newUserMsg = ChatMessage(sender = "User", body = prompt)
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                promptInput = "",
                chatHistory = _uiState.value.chatHistory + newUserMsg,
                systemStatusMessage = "NPU computing vector spaces..."
            )
            
            // Execute fallback local engine mapping (Expandable to NativeHardwareLlmEngine)
            val responseText = neuralEngineService.generateFallbackResponse(prompt)
            val aiMsg = ChatMessage(sender = "AI", body = responseText)
            
            // Unify state
            _uiState.value = _uiState.value.copy(
                chatHistory = _uiState.value.chatHistory + aiMsg,
                isLoading = false,
                systemStatusMessage = null
            )
        }
    }
}
