package com.explorer.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.explorer.ai.data.AppMessage
import com.explorer.ai.data.GeminiService
import com.explorer.ai.data.GitHubResult
import com.explorer.ai.data.GitHubService
import com.explorer.ai.data.GitTreeItem
import com.explorer.ai.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UIWorkspaceState(
    val apiKey: String? = null,
    val isCheckingKey: Boolean = true,
    val repoSearchQuery: String = "",
    val activeBranch: String = "",
    val fileTreeNodes: List<GitTreeItem> = emptyList(),
    val isTreeLoading: Boolean = false,
    val treeError: String? = null,
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
        // Asynchronously observe local data storage definitions for stored keys
        viewModelScope.launch {
            preferencesManager.apiKeyFlow.collect { key ->
                _uiState.update { it.copy(apiKey = key, isCheckingKey = false) }
                if (!key.isNullOrBlank()) {
                    geminiService.initialize(key)
                }
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

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(repoSearchQuery = query) }
    }

    fun updatePromptInput(input: String) {
        _uiState.update { it.copy(activePromptInput = input) }
    }

    fun exploreGitHubRepository() {
        val targetRepo = _uiState.value.repoSearchQuery.trim()
        if (targetRepo.isBlank() || !targetRepo.contains("/")) {
            _uiState.update { it.copy(treeError = "Enter valid target reference format: 'owner/repo'") }
            return
        }

        _uiState.update { 
            it.copy(
                isTreeLoading = true, 
                treeError = null, 
                fileTreeNodes = emptyList(),
                openFilePath = null,
                openFileContent = null
            ) 
        }

        viewModelScope.launch {
            when (val result = gitHubService.fetchRepositoryData(targetRepo)) {
                is GitHubResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            isTreeLoading = false,
                            activeBranch = result.data.first,
                            fileTreeNodes = result.data.second
                        )
                    }
                }
                is GitHubResult.Error -> {
                    _uiState.update { it.copy(isTreeLoading = false, treeError = result.message) }
                }
            }
        }
    }

    fun loadSelectedFileContent(item: GitTreeItem) {
        val targetRepo = _uiState.value.repoSearchQuery.trim()
        val currentBranch = _uiState.value.activeBranch
        
        if (item.type != "blob") return // Do not request structural structural nodes directly

        _uiState.update { it.copy(isFileLoading = true, openFilePath = item.path, openFileContent = null) }

        viewModelScope.launch {
            when (val result = gitHubService.fetchFileRawContent(targetRepo, currentBranch, item.path)) {
                is GitHubResult.Success -> {
                    _uiState.update { it.copy(isFileLoading = false, openFileContent = result.data) }
                }
                is GitHubResult.Error -> {
                    _uiState.update { it.copy(isFileLoading = false, openFileContent = "Error loading content: ${result.message}") }
                }
            }
        }
    }

    fun dispatchChatPrompt() {
        val promptText = _uiState.value.activePromptInput.trim()
        if (promptText.isBlank() || _uiState.value.isAiStreaming) return

        if (!geminiService.isReady()) {
            _uiState.update { 
                it.copy(
                    chatHistory = it.chatHistory + AppMessage("System", "Error: Gemini pipeline initialization missing. Configure API settings.")
                )
            }
            return
        }

        // Build composite string injecting targeted file snapshots for local contextual anchoring
        val activeFileContext = _uiState.value.openFilePath
        val activeFileBody = _uiState.value.openFileContent
        val contextInjectedPrompt = if (!activeFileContext.isNullOrBlank() && !activeFileBody.isNullOrBlank()) {
            "--- START CONTEXT FILE TARGET: $activeFileContext ---\n" +
            activeFileBody + "\n" +
            "--- END CONTEXT FILE TARGET ---\n\n" +
            "User Query relative to codebase reference context:\n$promptText"
        } else {
            promptText
        }

        val updatedHistory = _uiState.value.chatHistory + AppMessage("User", promptText)
        
        _uiState.update { 
            it.copy(
                chatHistory = updatedHistory,
                activePromptInput = "",
                isAiStreaming = true,
                activeAiTypingMessage = ""
            )
        }

        viewModelScope.launch {
            geminiService.streamChatResponse(contextInjectedPrompt, _uiState.value.chatHistory)
                .catch { exception ->
                    val errorMessage = exception.localizedMessage ?: "Unknown transmission interface error"
                    _uiState.update {
                        it.copy(
                            isAiStreaming = false,
                            activeAiTypingMessage = null,
                            chatHistory = it.chatHistory + AppMessage("System", "Pipeline Failure: $errorMessage")
                        )
                    }
                }
                .collect { incrementalChunk ->
                    _uiState.update { 
                        it.copy(activeAiTypingMessage = (it.activeAiTypingMessage ?: "") + incrementalChunk)
                    }
                }

            // Flush reactive buffers upon clean termination of streaming interface lifecycle
            val completedAiResponse = _uiState.value.activeAiTypingMessage ?: ""
            _uiState.update {
                it.copy(
                    isAiStreaming = false,
                    activeAiTypingMessage = null,
                    chatHistory = it.chatHistory + AppMessage("AI", completedAiResponse)
                )
            }
        }
    }
}
