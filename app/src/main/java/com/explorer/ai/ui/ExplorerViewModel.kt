package com.explorer.ai.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.explorer.ai.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val isWorkspaceExpanded: Boolean = true,
    val openFilePath: String? = null,
    val openFileContent: String? = null,
    val isFileLoading: Boolean = false,
    val chatHistory: List<AppMessage> = emptyList(),
    val activeAiTypingMessage: String? = null,
    val activePromptInput: String = "",
    val isAiStreaming: Boolean = false,
    val selectedModelId: String = PreferencesManager.DEFAULT_MODEL.id,
    val isSettingsSheetOpen: Boolean = false
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
            }
        }
    }

    fun onPromptInputChanged(newInput: String) {
        _uiState.update { it.copy(activePromptInput = newInput) }
    }

    fun updateApiKey(newKey: String) {
        viewModelScope.launch {
            preferencesManager.saveApiKey(newKey)
        }
    }

    fun closeSettingsSheet() {
        _uiState.update { it.copy(isSettingsSheetOpen = false) }
    }

    fun openSettingsSheet() {
        _uiState.update { it.copy(isSettingsSheetOpen = true) }
    }

    fun ingestLocalDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFileLoading = true, treeError = null) }
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            reader.readText()
                        }
                    } ?: ""
                }
                val displayName = withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                        } else {
                            "unknown"
                        }
                    } ?: "unknown"
                }
                _uiState.update {
                    it.copy(
                        openFilePath = displayName,
                        openFileContent = content,
                        isFileLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isFileLoading = false,
                        treeError = "Failed to read document: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun sendPrompt(promptText: String) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isBlank() || _uiState.value.isAiStreaming) return

        if (!geminiService.isReady()) {
            _uiState.update {
                it.copy(
                    chatHistory = it.chatHistory + AppMessage(
                        sender = "System",
                        body = "Gemini pipeline initialization missing."
                    )
                )
            }
            return
        }

        val activeFileContext = _uiState.value.openFilePath
        val activeFileBody = _uiState.value.openFileContent
        val contextInjectedPrompt = if (!activeFileContext.isNullOrBlank() && !activeFileBody.isNullOrBlank()) {
            "--- ACTIVE CONTEXT: $activeFileContext ---\n$activeFileBody\n--- END CONTEXT ---\n\nUser Query: $cleanPrompt"
        } else cleanPrompt

        val historyBeforeThisTurn = _uiState.value.chatHistory
        val displayPrompt = AppMessage(sender = "User", body = cleanPrompt)

        _uiState.update {
            it.copy(
                chatHistory = it.chatHistory + displayPrompt,
                activePromptInput = "",
                isAiStreaming = true,
                activeAiTypingMessage = ""
            )
        }

        viewModelScope.launch {
            try {
                geminiService.streamChatResponse(contextInjectedPrompt, historyBeforeThisTurn)
                    .collect { chunk ->
                        _uiState.update {
                            it.copy(activeAiTypingMessage = (it.activeAiTypingMessage ?: "") + chunk)
                        }
                    }

                val completedResponse = _uiState.value.activeAiTypingMessage ?: ""
                _uiState.update {
                    it.copy(
                        isAiStreaming = false,
                        activeAiTypingMessage = null,
                        chatHistory = it.chatHistory + AppMessage(sender = "AI", body = completedResponse)
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isAiStreaming = false,
                        activeAiTypingMessage = null,
                        chatHistory = it.chatHistory + AppMessage(
                            sender = "System",
                            body = "Failure: ${exception.localizedMessage}"
                        )
                    )
                }
            }
        }
    }
}