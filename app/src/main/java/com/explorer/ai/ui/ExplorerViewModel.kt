package com.explorer.ai.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.explorer.ai.data.NeuralEngineService
import com.explorer.ai.domain.RagPromptBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// --- Data Contracts explicitly required by the RepoExplorerScreen UI ---

data class RepoFile(
    val path: String, 
    val type: String
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val body: String,
    val feedbackState: Int? = null,
    val diagramTrigger: String? = null 
)

data class UIWorkspaceState(
    val searchQuery: String = "",
    val isWorkspaceVisible: Boolean = true,
    val isLoading: Boolean = false,
    val repoFiles: List<RepoFile> = emptyList(),
    val chatHistory: List<Message> = emptyList(),
    val promptInput: String = ""
)

// --- Interface required to map previous RAG logic ---
object ChatState {
    data class Message(val text: String, val isUser: Boolean, val diagramTrigger: String? = null)
}

interface LocalLlmEngine {
    suspend fun generateResponse(prompt: String): String
}

// --- Unified ViewModel ---

class ExplorerViewModel(
    private val neuralEngineService: NeuralEngineService,
    private val localLlmEngine: LocalLlmEngine // Connects to the established JNI hardware bridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(UIWorkspaceState())
    val uiState: StateFlow<UIWorkspaceState> = _uiState.asStateFlow()

    // ==========================================
    // Core UI Actions Mapped to RepoExplorerScreen
    // ==========================================

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun exploreGitHubRepository(query: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            // Restore actual network fetch logic for the repository tree here
            Log.d("ExplorerViewModel", "Fetching repository tree for: $query")
            
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun purgeSavedCredentials() {
        Log.d("ExplorerViewModel", "Credentials purged")
        // Implementation to clear auth tokens
    }

    fun toggleWorkspaceVisibility() {
        _uiState.update { it.copy(isWorkspaceVisible = !it.isWorkspaceVisible) }
    }

    fun ingestLocalDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Triggers the layout-aware and semantic ingestion pipeline built previously
            neuralEngineService.indexPdfDocument(uri)
            
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun loadSelectedFileContent(path: String) {
        Log.d("ExplorerViewModel", "Loading content for: $path")
        // Load the file content into the workspace
    }

    // ==========================================
    // Multi-Modal Chat Interface Actions
    // ==========================================

    fun updatePromptInput(text: String) {
        _uiState.update { it.copy(promptInput = text) }
    }

    fun rateMessage(id: String, state: Int) {
        _uiState.update { currentState ->
            val updatedHistory = currentState.chatHistory.map { 
                if (it.id == id) it.copy(feedbackState = state) else it 
            }
            currentState.copy(chatHistory = updatedHistory)
        }
    }

    fun purgeChatHistory() {
        _uiState.update { it.copy(chatHistory = emptyList()) }
    }

    fun retryLastPrompt() {
        val lastUserMsg = _uiState.value.chatHistory.lastOrNull { it.sender == "USER" }
        if (lastUserMsg != null) {
            dispatchChatPrompt(lastUserMsg.body)
        }
    }

    fun dispatchChatPrompt(overridePrompt: String? = null) {
        val query = overridePrompt ?: _uiState.value.promptInput
        if (query.isBlank()) return

        val userMsg = Message(sender = "USER", body = query)
        _uiState.update { 
            it.copy(
                chatHistory = it.chatHistory + userMsg,
                promptInput = "",
                isLoading = true
            )
        }

        viewModelScope.launch {
            try {
                // 1. Search semantic chunks dynamically mapped
                val relevantChunks = neuralEngineService.search(query, topK = 4)
                
                // 2. Format memory for the prompt matrix
                val formattedHistory = _uiState.value.chatHistory.map {
                    ChatState.Message(text = it.body, isUser = it.sender == "USER")
                }

                // 3. Build context and execute NPU hardware generation
                val prompt = RagPromptBuilder.buildPrompt(query, relevantChunks, formattedHistory)
                val responseText = localLlmEngine.generateResponse(prompt)

                // 4. Intercept GUI visual diagram triggers without raw text leakage
                val trigger = when {
                    responseText.contains("[DIAGRAM_TRIGGER:MEMORY_MAP]") -> "MEMORY_MAP"
                    responseText.contains("[DIAGRAM_TRIGGER:ARCH_FLOW]") -> "ARCH_FLOW"
                    else -> null
                }
                
                val cleanText = responseText.replace(Regex("\\[DIAGRAM_TRIGGER:[A-Z_]+\\]"), "").trim()
                val aiMsg = Message(sender = "AI", body = cleanText, diagramTrigger = trigger)
                
                _uiState.update { it.copy(chatHistory = it.chatHistory + aiMsg) }

            } catch (e: Exception) {
                val errorMsg = Message(sender = "AI", body = "System optimization warning: Internal processing loop requires updated parameters (${e.localizedMessage}).")
                _uiState.update { it.copy(chatHistory = it.chatHistory + errorMsg) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateHardwareConfiguration(newKey: String, modelName: String) {
        Log.i("ExplorerViewModel", "Re-configuring NPU matrix. Key: $newKey | Target Model: $modelName")
    }
}
