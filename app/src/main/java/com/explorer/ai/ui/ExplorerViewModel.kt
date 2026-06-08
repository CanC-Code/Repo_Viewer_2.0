package com.explorer.ai.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.explorer.ai.data.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class UIWorkspaceState(
    val apiKey: String? = "LOCAL_MODE_ACTIVE", 
    val isCheckingKey: Boolean = false,
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
    val selectedModel: String = "Local Neural Graph Engine"
)

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val gitHubService = GitHubService()
    private val localNeuralService = NeuralEngineService()

    private val _uiState = MutableStateFlow(UIWorkspaceState())
    val uiState: StateFlow<UIWorkspaceState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.chatHistoryFlow.collect { jsonString ->
                if (!jsonString.isNullOrBlank()) {
                    try {
                        val array = JSONArray(jsonString)
                        val loadedHistory = mutableListOf<AppMessage>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            loadedHistory.add(AppMessage(
                                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                                sender = obj.optString("sender"),
                                body = obj.optString("body"),
                                feedbackState = obj.optInt("feedbackState", 0)
                            ))
                        }
                        _uiState.update { it.copy(chatHistory = loadedHistory) }
                    } catch (e: Exception) { }
                }
            }
        }
    }

    private fun saveChatHistoryToDisk(history: List<AppMessage>) {
        viewModelScope.launch {
            val array = JSONArray()
            history.forEach { msg ->
                val obj = JSONObject()
                obj.put("id", msg.id)
                obj.put("sender", msg.sender)
                obj.put("body", msg.body)
                obj.put("feedbackState", msg.feedbackState)
                array.put(obj)
            }
            preferencesManager.saveChatHistory(array.toString())
        }
    }

    fun purgeChatHistory() {
        viewModelScope.launch {
            preferencesManager.clearChatHistory()
            localNeuralService.clearBrainTopology()
            _uiState.update { it.copy(chatHistory = emptyList()) }
        }
    }

    fun updateApiKey(newKey: String) { }
    fun selectModel(modelName: String) { }

    fun updateSearchQuery(query: String) { _uiState.update { it.copy(repoSearchQuery = query) } }
    fun updatePromptInput(input: String) { _uiState.update { it.copy(activePromptInput = input) } }
    fun toggleWorkspaceVisibility() { _uiState.update { it.copy(isWorkspaceExpanded = !it.isWorkspaceExpanded) } }

    fun toggleFolder(path: String) {
        _uiState.update { state ->
            val newExpanded = if (state.expandedFolders.contains(path)) state.expandedFolders - path else state.expandedFolders + path
            state.copy(expandedFolders = newExpanded)
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        return result ?: uri.lastPathSegment ?: "Unknown_Document"
    }

    fun ingestLocalDocument(context: Context, uri: Uri) {
        _uiState.update { it.copy(isFileLoading = true, isWorkspaceExpanded = true, openFilePath = "Processing Document...") }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = getFileName(context, uri)
                val mimeType = context.contentResolver.getType(uri) ?: ""
                var totalNodesCreated = 0

                if (fileName.endsWith(".pdf", true) || mimeType.contains("pdf")) {
                    // PAGINATED EXTRACTION: Prevents massive documents from freezing the app
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val document = PDDocument.load(inputStream)
                        val stripper = PDFTextStripper()
                        stripper.sortByPosition = true
                        
                        for (page in 1..document.numberOfPages) {
                            stripper.startPage = page
                            stripper.endPage = page
                            val pageText = stripper.getText(document)
                            if (pageText.isNotBlank()) {
                                totalNodesCreated += localNeuralService.learnFromDocumentChunk("$fileName (Pg $page)", pageText)
                            }
                            
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(openFilePath = "Parsing Pg $page / ${document.numberOfPages}...") }
                            }
                        }
                        document.close()
                    }
                } else {
                    val extractedText = when {
                        fileName.endsWith(".epub", true) || mimeType.contains("epub") -> extractEpubText(context, uri)
                        else -> {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val reader = BufferedReader(InputStreamReader(inputStream))
                            val text = reader.readText()
                            reader.close()
                            text
                        }
                    }
                    totalNodesCreated = localNeuralService.learnFromDocumentChunk(fileName, extractedText)
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isFileLoading = false, openFilePath = "Ingested: $fileName") }
                    val systemLog = AppMessage(sender = "System", body = "Ingestion complete. Synthesized $totalNodesCreated validated concepts.")
                    val updatedHistory = _uiState.value.chatHistory + systemLog
                    _uiState.update { it.copy(chatHistory = updatedHistory) }
                    saveChatHistoryToDisk(updatedHistory)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isFileLoading = false, openFilePath = null) }
                    val errorMsg = AppMessage(sender = "System", body = "Failed to parse file: ${e.localizedMessage}")
                    val newHistory = _uiState.value.chatHistory + errorMsg
                    _uiState.update { it.copy(chatHistory = newHistory) }
                    saveChatHistoryToDisk(newHistory)
                }
            }
        }
    }

    private fun extractEpubText(context: Context, uri: Uri): String {
        val builder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && (entry.name.endsWith(".html") || entry.name.endsWith(".xhtml") || entry.name.endsWith(".htm"))) {
                        val reader = BufferedReader(InputStreamReader(zip))
                        val htmlContent = reader.readText()
                        val plainText = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString()
                        builder.append(plainText).append("\n\n")
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return builder.toString().trim()
    }

    fun exploreGitHubRepository() {
        val targetRepo = _uiState.value.repoSearchQuery.trim()
        if (targetRepo.isBlank() || !targetRepo.contains("/")) {
            _uiState.update { it.copy(treeError = "Enter valid format: 'owner/repo'") }
            return
        }

        _uiState.update { it.copy(isTreeLoading = true, treeError = null, fileTreeNodes = emptyList(), openFilePath = null, openFileContent = null) }

        viewModelScope.launch {
            when (val result = gitHubService.fetchRepositoryData(targetRepo)) {
                is GitHubResult.Success -> _uiState.update { it.copy(isTreeLoading = false, activeBranch = result.data.first, fileTreeNodes = result.data.second) }
                is GitHubResult.Error -> _uiState.update { it.copy(isTreeLoading = false, treeError = result.message) }
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
                is GitHubResult.Success -> {
                    val fileName = item.path.substringAfterLast("/")
                    localNeuralService.learnFromDocumentChunk(fileName, result.data)
                    _uiState.update { it.copy(isFileLoading = false, openFileContent = result.data) }
                }
                is GitHubResult.Error -> _uiState.update { it.copy(isFileLoading = false, openFileContent = "Error: ${result.message}") }
            }
        }
    }

    fun dispatchChatPrompt() {
        val promptText = _uiState.value.activePromptInput.trim()
        if (promptText.isBlank() || _uiState.value.isAiStreaming) return

        val displayPrompt = AppMessage(sender = "User", body = promptText)
        val uiHistory = _uiState.value.chatHistory + displayPrompt
        _uiState.update { it.copy(chatHistory = uiHistory, activePromptInput = "", isAiStreaming = true, activeAiTypingMessage = "") }
        saveChatHistoryToDisk(uiHistory)

        viewModelScope.launch {
            localNeuralService.streamSynthesisInteraction(promptText, uiHistory)
                .catch { exception ->
                    val errorMsg = AppMessage(sender = "System", body = "Internal Pipeline Failure: ${exception.localizedMessage}")
                    val failedHistory = _uiState.value.chatHistory + errorMsg
                    _uiState.update { it.copy(isAiStreaming = false, activeAiTypingMessage = null, chatHistory = failedHistory) }
                    saveChatHistoryToDisk(failedHistory)
                }
                .collect { incrementalChunk ->
                    _uiState.update { it.copy(activeAiTypingMessage = (it.activeAiTypingMessage ?: "") + incrementalChunk) }
                }

            val completedAiResponse = _uiState.value.activeAiTypingMessage ?: ""
            if (completedAiResponse.isNotBlank()) {
                val finalHistory = _uiState.value.chatHistory + AppMessage(sender = "AI", body = completedAiResponse)
                _uiState.update { it.copy(isAiStreaming = false, activeAiTypingMessage = null, chatHistory = finalHistory) }
                saveChatHistoryToDisk(finalHistory)
            }
        }
    }
}
