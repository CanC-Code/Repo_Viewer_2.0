package com.explorer.ai.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.explorer.ai.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

data class UIWorkspaceState(
    val apiKey: String? = null,
    val isCheckingKey: Boolean = true,
    val repoSearchQuery: String = "",
    val activeBranch: String = "",
    val fileTreeNodes: List<GitTreeItem> = emptyList(),
    val expandedFolders: Set<String> = emptySet(),
    val isTreeLoading: Boolean = false,
    val treeError: String? = null,
    
    val isWorkspaceExpanded: Boolean = true, // Toggles top file browser half
    
    val openFilePath: String? = null,
    val openFileContent: String? = null,
    val isFileLoading: Boolean = false,
    
    val chatHistory: List<AppMessage> = emptyList(),
    val activeAiTypingMessage: String? = null,
    val activePromptInput: String = "",
    val isAiStreaming: Boolean = false
)

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val gitHubService = GitHubService()
    private val geminiService = GeminiService()

    private val _uiState = MutableStateFlow(UIWorkspaceState())
    val uiState: StateFlow<UIWorkspaceState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.apiKeyFlow.collect { key ->
                _uiState.update { it.copy(apiKey = key, isCheckingKey = false) }
                if (!key.isNullOrBlank()) geminiService.initialize(key)
            }
        }
    }

    fun updateApiKey(newKey: String) {
        viewModelScope.launch {
            if (newKey.isNotBlank()) {
                preferencesManager.saveApiKey(newKey.trim())
                geminiService.initialize(newKey.trim())
            }
        }
    }

    fun purgeSavedCredentials() {
        viewModelScope.launch {
            preferencesManager.clearApiKey()
            _uiState.update { UIWorkspaceState(apiKey = null, isCheckingKey = false) }
        }
    }

    fun updateSearchQuery(query: String) { _uiState.update { it.copy(repoSearchQuery = query) } }
    fun updatePromptInput(input: String) { _uiState.update { it.copy(activePromptInput = input) } }
    fun toggleWorkspaceVisibility() { _uiState.update { it.copy(isWorkspaceExpanded = !it.isWorkspaceExpanded) } }

    fun toggleFolder(path: String) {
        _uiState.update { state ->
            val newExpanded = if (state.expandedFolders.contains(path)) state.expandedFolders - path else state.expandedFolders + path
            state.copy(expandedFolders = newExpanded)
        }
    }

    // Free-tier local file ingestion (Txt/Markdown/Html). 
    // To support raw binary Epubs/PDFs, this reads the raw encoded text stream.
    fun ingestLocalDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val extractedText = reader.readText()
                reader.close()
                
                _uiState.update { 
                    it.copy(
                        openFilePath = "Local Document: ${uri.lastPathSegment}",
                        openFileContent = extractedText,
                        isWorkspaceExpanded = true
                    ) 
                }
            } catch (e: Exception) {
                val errorMsg = AppMessage(sender = "System", body = "Failed to parse local file: ${e.localizedMessage}")
                _uiState.update { it.copy(chatHistory = it.chatHistory + errorMsg) }
            }
        }
    }

    fun rateMessage(messageId: String, score: Int) {
        _uiState.update { state ->
            val updatedHistory = state.chatHistory.map { if (it.id == messageId) it.copy(feedbackState = score) else it }
            state.copy(chatHistory = updatedHistory)
        }
    }

    fun retryLastPrompt() {
        if (_uiState.value.isAiStreaming) return
        val lastUserMessage = _uiState.value.chatHistory.lastOrNull { it.sender == "User" } ?: return
        
        // Strip the failed/disliked AI response from the working pipeline
        val trimmedHistory = _uiState.value.chatHistory.dropLastWhile { it.sender != "User" }.dropLast(1)
        _uiState.update { it.copy(chatHistory = trimmedHistory, activePromptInput = lastUserMessage.body) }
        dispatchChatPrompt()
    }

    fun exploreGitHubRepository() {
        val targetRepo = _uiState.value.repoSearchQuery.trim()
        if (targetRepo.isBlank() || !targetRepo.contains("/")) {
            _uiState.update { it.copy(treeError = "Enter valid target reference format: 'owner/repo'") }
            return
        }

        _uiState.update { it.copy(isTreeLoading = true, treeError = null, fileTreeNodes = emptyList(), openFilePath = null, openFileContent = null) }

        viewModelScope.launch {
            when (val result = gitHubService.fetchRepositoryData(targetRepo)) {
                is GitHubResult.Success -> {
                    _uiState.update { it.copy(isTreeLoading = false, activeBranch = result.data.first, fileTreeNodes = result.data.second) }
                }
                is GitHubResult.Error -> {
                    _uiState.update { it.copy(isTreeLoading = false, treeError = result.message) }
                }
            }
        }
    }

    fun loadSelectedFileContent(item: GitTreeItem) {
        if (item.type != "blob") {
            toggleFolder(item.path)
            return
        }

        val targetRepo = _uiState.value.repoSearchQuery.trim()
        val currentBranch = _uiState.value.activeBranch
        
        _uiState.update { it.copy(isFileLoading = true, openFilePath = item.path, openFileContent = null) }

        viewModelScope.launch {
            when (val result = gitHubService.fetchFileRawContent(targetRepo, currentBranch, item.path)) {
                is GitHubResult.Success -> _uiState.update { it.copy(isFileLoading = false, openFileContent = result.data) }
                is GitHubResult.Error -> _uiState.update { it.copy(isFileLoading = false, openFileContent = "Error: ${result.message}") }
            }
        }
    }

    fun dispatchChatPrompt() {
        val promptText = _uiState.value.activePromptInput.trim()
        if (promptText.isBlank() || _uiState.value.isAiStreaming) return

        if (!geminiService.isReady()) {
            _uiState.update { it.copy(chatHistory = it.chatHistory + AppMessage(sender = "System", body = "Gemini pipeline initialization missing.")) }
            return
        }

        val activeFileContext = _uiState.value.openFilePath
        val activeFileBody = _uiState.value.openFileContent
        val contextInjectedPrompt = if (!activeFileContext.isNullOrBlank() && !activeFileBody.isNullOrBlank()) {
            "--- ACTIVE CONTEXT: $activeFileContext ---\n$activeFileBody\n--- END CONTEXT ---\n\nUser Query: $promptText"
        } else promptText

        val displayPrompt = AppMessage(sender = "User", body = promptText) // Show clean prompt to user
        _uiState.update { it.copy(chatHistory = it.chatHistory + displayPrompt, activePromptInput = "", isAiStreaming = true, activeAiTypingMessage = "") }

        viewModelScope.launch {
            geminiService.streamChatResponse(contextInjectedPrompt, _uiState.value.chatHistory)
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isAiStreaming = false, activeAiTypingMessage = null,
                            chatHistory = it.chatHistory + AppMessage(sender = "System", body = "Failure: ${exception.localizedMessage}")
                        )
                    }
                }
                .collect { incrementalChunk ->
                    _uiState.update { it.copy(activeAiTypingMessage = (it.activeAiTypingMessage ?: "") + incrementalChunk) }
                }

            val completedAiResponse = _uiState.value.activeAiTypingMessage ?: ""
            _uiState.update {
                it.copy(
                    isAiStreaming = false, activeAiTypingMessage = null,
                    chatHistory = it.chatHistory + AppMessage(sender = "AI", body = completedAiResponse)
                )
            }
        }
    }
}
