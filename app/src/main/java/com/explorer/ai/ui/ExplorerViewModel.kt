    fun dispatchChatPrompt() {
        val promptText = _uiState.value.activePromptInput.trim()
        if (promptText.isBlank() || _uiState.value.isAiStreaming) return

        if (!geminiService.isReady()) return

        // 1. Prepare UI State
        val displayPrompt = AppMessage(sender = "User", body = promptText)
        val uiHistory = _uiState.value.chatHistory + displayPrompt
        _uiState.update { it.copy(chatHistory = uiHistory, activePromptInput = "", isAiStreaming = true, activeAiTypingMessage = "") }
        
        // 2. Inject Active File Content as a "System" context preamble automatically
        val finalPrompt = if (!uiState.value.openFileContent.isNullOrBlank()) {
             "File Context: ${uiState.value.openFilePath}\n${uiState.value.openFileContent}\n\nUser Question: $promptText"
        } else promptText

        // 3. Collect from SDK
        viewModelScope.launch {
            geminiService.streamChatResponse(finalPrompt, uiHistory)
                .collect { incrementalChunk ->
                    _uiState.update { it.copy(activeAiTypingMessage = (it.activeAiTypingMessage ?: "") + incrementalChunk) }
                }

            val completedAiResponse = _uiState.value.activeAiTypingMessage ?: ""
            val finalHistory = uiHistory + AppMessage(sender = "AI", body = completedAiResponse)
            _uiState.update { it.copy(isAiStreaming = false, activeAiTypingMessage = null, chatHistory = finalHistory) }
            saveChatHistoryToDisk(finalHistory)
        }
    }
