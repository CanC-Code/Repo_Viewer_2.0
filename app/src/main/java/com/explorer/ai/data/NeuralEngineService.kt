package com.explorer.ai.data

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.ln

// Drop-in replacement for data messaging within the repo workspace layout
data class AppMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "User", "AI", or "System"
    val body: String,
    val feedbackState: Int = 0
)

/**
 * Represents an autonomous, self-contained node of synthesized knowledge.
 * Operates independently and maintains internal association weight parameters.
 */
data class NeuronSegment(
    val id: String = UUID.randomUUID().toString(),
    val conceptHeadline: String,
    val associatedKeywords: Set<String>,
    val contextualDataBody: String,
    val originSource: String,
    val compilationTimestamp: Long = System.currentTimeMillis(),
    val relationalWeights: MutableMap<String, Float> = mutableMapOf() // Target Neuron ID -> Weight Strength
)

class NeuralEngineService {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val engineMutex = Mutex()
    
    // Core On-Device Memory Graph Repository
    private val neuralGraph = mutableMapOf<String, NeuronSegment>()
    
    // Conceptual stop words to filter out before symbolic text mapping
    private val lexiconStopWords = setOf(
        "the", "and", "a", "of", "to", "in", "is", "that", "it", "for", "on", "with", "as", "this", "by", "an"
    )

    /**
     * Ingests, tokenizes, and breaks down raw file or document streams into 
     * discrete neuron chunks, executing dynamic weight updates over the existing matrix.
     */
    suspend fun learnFromDocument(title: String, rawContent: String): Int = engineMutex.withLock {
        val paragraphBlocks = rawContent.split(Regex("(\\n\\r?\\n|\\.\\s)"))
            .map { it.trim() }
            .filter { it.length > 20 }
            
        var internalSegmentsCreated = 0
        
        for (block in paragraphBlocks) {
            val terms = cleanTokenize(block)
            if (terms.size < 4) continue
            
            val matchingSegmentId = searchConflictResolutionLayer(terms)
            
            if (matchingSegmentId != null) {
                // Conflict/Evolution: Update existing node with newly synthesized code structure or context
                val historicalNode = neuralGraph[matchingSegmentId]!!
                val evolvedNode = historicalNode.copy(
                    contextualDataBody = historicalNode.contextualDataBody + "\n[Evolved Understanding via " + title + "]:\n" + block,
                    compilationTimestamp = System.currentTimeMillis()
                )
                neuralGraph[matchingSegmentId] = evolvedNode
                recalculatePathingWeights(evolvedNode)
            } else {
                // Creation: Instantiate an entirely new operational Neuron Segment
                val newNode = NeuronSegment(
                    conceptHeadline = terms.take(5).joinToString(" ").uppercase(),
                    associatedKeywords = terms,
                    contextualDataBody = block,
                    originSource = title
                )
                neuralGraph[newNode.id] = newNode
                recalculatePathingWeights(newNode)
                internalSegmentsCreated++
            }
        }
        return@withLock internalSegmentsCreated
    }

    /**
     * Resolves updates vs new creation. Checks if incoming text conflicts with, 
     * optimizes, or matches an already established knowledge path.
     */
    private fun searchConflictResolutionLayer(incomingTerms: Set<String>): String? {
        var maximumAffinityScore = 0f
        var optimalMatchedId: String? = null
        
        for ((id, segment) in neuralGraph) {
            val mutualIntersections = segment.associatedKeywords.intersect(incomingTerms).size
            if (mutualIntersections == 0) continue
            
            // Jaccard similarity vectoring over symbolic tokens
            val calculationAffinity = mutualIntersections.toFloat() / 
                    (segment.associatedKeywords.size + incomingTerms.size - mutualIntersections)
            
            if (calculationAffinity > 0.40f && calculationAffinity > maximumAffinityScore) {
                maximumAffinityScore = calculationAffinity
                optimalMatchedId = id
            }
        }
        return optimalMatchedId
    }

    /**
     * Establishes dynamic relational weights across nodes based on cross-term frequencies.
     */
    private fun recalculatePathingWeights(node: NeuronSegment) {
        for ((targetId, targetNode) in neuralGraph) {
            if (node.id == targetId) continue
            val matchingIntersection = node.associatedKeywords.intersect(targetNode.associatedKeywords).size
            if (matchingIntersection > 1) {
                val balancedWeight = matchingIntersection.toFloat() / (node.associatedKeywords.size)
                node.relationalWeights[targetId] = balancedWeight
                targetNode.relationalWeights[node.id] = balancedWeight
            }
        }
    }

    /**
     * Streams interactive synthesized conversational responses back to the UI workspace, 
     * traversing multiple independent asynchronous concept pathways simultaneously.
     */
    fun streamSynthesisInteraction(prompt: String, conversationHistory: List<AppMessage>): Flow<String> = flow {
        val userTerms = cleanTokenize(prompt)
        val optimalMatches = mutableListOf<Pair<NeuronSegment, Float>>()
        
        engineMutex.withLock {
            for (segment in neuralGraph.values) {
                val matches = segment.associatedKeywords.intersect(userTerms).size
                if (matches > 0) {
                    val tfIdfScore = matches.toFloat() * (1.0f + ln(neuralGraph.size.toFloat() / (1f + matches.toFloat())))
                    optimalMatches.add(Pair(segment, tfIdfScore))
                }
            }
        }
        
        optimalMatches.sortByDescending { it.second }
        val localizedContextChunks = optimalMatches.take(4).map { it.first }
        
        if (localizedContextChunks.isEmpty()) {
            val neutralResponse = ("The Entity is running cleanly, but lacks relevant localized knowledge " +
                    "to respond to this prompt. Attach reference code or foundational textbooks using the " +
                    "clip button to build my on-device neuron configuration.").split(" ")
            for (word in neutralResponse) {
                emit("$word ")
                delay(40)
            }
            return@flow
        }
        
        // Multi-channel path synthesis simulation
        val tokenCollectorChannel = Channel<String>(Channel.UNLIMITED)
        val processingJobs = localizedContextChunks.map { targetNode ->
            scope.launch {
                val lines = targetNode.contextualDataBody.lines()
                for (line in lines) {
                    if (line.contains(Regex("(fun|class|interface|val|var|import|package|build|xml)"))) {
                        tokenCollectorChannel.send("\n
http://googleusercontent.com/immersive_entry_chip/0

### 2. `app/src/main/java/com/explorer/ai/ui/ExplorerViewModel.kt`

```kotlin
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
    val apiKey: String? = "LOCAL_MODE_ACTIVE", // Neutralizes the initial setup lock screen
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
    
    // Core internal, on-device engine pipeline instance swap
    private val localNeuralService = NeuralEngineService()

    private val _uiState = MutableStateFlow(UIWorkspaceState())
    val uiState: StateFlow<UIWorkspaceState> = _uiState.asStateFlow()

    init {
        // Load operational chat tracking context from local preferences
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

    fun updateApiKey(newKey: String) {
        // Kept out of caution to prevent upstream crashes in compilation setups
    }

    fun selectModel(modelName: String) {
        // Seamless static model assignment override
    }

    fun purgeSavedCredentials() {
        viewModelScope.launch {
            preferencesManager.clearChatHistory()
            localNeuralService.clearBrainTopology()
            _uiState.update { UIWorkspaceState(apiKey = "LOCAL_MODE_ACTIVE", isCheckingKey = false) }
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

                val extractedText = when {
                    fileName.endsWith(".pdf", true) || mimeType.contains("pdf") -> extractPdfText(context, uri)
                    fileName.endsWith(".epub", true) || mimeType.contains("epub") -> extractEpubText(context, uri)
                    else -> {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val text = reader.readText()
                        reader.close()
                        text
                    }
                }

                if (extractedText.isBlank()) throw Exception("Extracted document contains no readable text.")

                // On-Device Ingestion Loop Execution
                val createdNodes = localNeuralService.learnFromDocument(fileName, extractedText)
                val topologyAlert = localNeuralService.getNetworkTopologyDetails()

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isFileLoading = false, openFilePath = "Ingested: $fileName", openFileContent = topologyAlert) }
                    
                    val systemLog = AppMessage(
                        sender = "System", 
                        body = "Successfully internalized $fileName. Synthesized $createdNodes new concepts into memory graph pathways."
                    )
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

    private fun extractPdfText(context: Context, uri: Uri): String {
        var text = ""
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            text = stripper.getText(document)
            document.close()
        }
        return text.trim()
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

    fun rateMessage(messageId: String, score: Int) {
        val updatedHistory = _uiState.value.chatHistory.map { if (it.id == messageId) it.copy(feedbackState = score) else it }
        _uiState.update { it.copy(chatHistory = updatedHistory) }
        saveChatHistoryToDisk(updatedHistory)
    }

    fun retryLastPrompt() {
        if (_uiState.value.isAiStreaming) return
        val lastUserMessage = _uiState.value.chatHistory.lastOrNull { it.sender == "User" } ?: return

        val trimmedHistory = _uiState.value.chatHistory.dropLastWhile { it.sender != "User" }.dropLast(1)
        _uiState.update { it.copy(chatHistory = trimmedHistory, activePromptInput = lastUserMessage.body) }
        saveChatHistoryToDisk(trimmedHistory)
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
                    // Instantly ingest the active clicked repository file into the core neural segments graph
                    localNeuralService.learnFromDocument(item.path.substringAfterLast("/"), result.data)
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
