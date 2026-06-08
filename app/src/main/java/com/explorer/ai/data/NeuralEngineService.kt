package com.explorer.ai.data

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.ln

data class AppMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val body: String,
    val feedbackState: Int = 0
)

data class NeuronSegment(
    val id: String = UUID.randomUUID().toString(),
    val conceptHeadline: String,
    val associatedKeywords: Set<String>,
    val contextualDataBody: String,
    val originSource: String,
    val compilationTimestamp: Long = System.currentTimeMillis(),
    val relationalWeights: MutableMap<String, Float> = mutableMapOf()
)

// A specialized node that tracks constraints, warnings, and architectural rules
data class KnowledgeRule(
    val sourceTitle: String,
    val ruleConstraint: String,
    val triggerKeywords: Set<String>
)

class NeuralEngineService {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val engineMutex = Mutex()
    
    private val neuralGraph = mutableMapOf<String, NeuronSegment>()
    private val extractedRules = mutableListOf<KnowledgeRule>()
    
    // The specific repository file the user is currently looking at
    private var activeWorkspaceContext: Pair<String, String>? = null
    
    private val lexiconStopWords = setOf(
        "the", "and", "a", "of", "to", "in", "is", "that", "it", "for", "on", "with", "as", "this", "by", "an", "how", "what", "where", "why", "can", "you", "do", "does"
    )

    // Deeply expanded semantic map targeting complex porting, recompilation, and system architectures
    private val semanticSynonymMap = mapOf(
        "build" to setOf("compile", "assemble", "make", "cmake", "gradle", "ninja"),
        "error" to setOf("bug", "crash", "exception", "fail", "log", "segfault", "trace"),
        "bridge" to setOf("jni", "native", "cpp", "c++", "interface", "wrapper", "extern"),
        "port" to setOf("recomp", "recompilation", "android", "architecture", "mips", "arm64", "x86", "endianness"),
        "explain" to setOf("summarize", "describe", "details", "overview", "how"),
        "analyze" to setOf("review", "flaw", "issue", "check", "vulnerability", "leak", "problem", "audit")
    )

    fun setActiveWorkspaceContext(fileName: String, fileContent: String) {
        activeWorkspaceContext = Pair(fileName, fileContent)
    }

    suspend fun learnFromDocument(title: String, rawContent: String): Int = engineMutex.withLock {
        val paragraphBlocks = rawContent.split(Regex("(\\n\\r?\\n|\\.\\s)"))
            .map { it.trim() }
            .filter { it.length > 20 }
            
        var internalSegmentsCreated = 0
        
        for (block in paragraphBlocks) {
            val terms = cleanTokenize(block)
            if (terms.size < 4) continue
            
            // Heuristic Rule Extraction: Identify constraints for future flaw analysis
            if (block.lowercase().contains(Regex("(must|should|always|never|avoid|leak|error|deprecated|vulnerability|ensure|critical)"))) {
                extractedRules.add(KnowledgeRule(title, block, terms.filter { it.length > 4 }.toSet()))
            }
            
            val matchingSegmentId = searchConflictResolutionLayer(terms)
            
            if (matchingSegmentId != null) {
                val historicalNode = neuralGraph[matchingSegmentId]!!
                val evolvedNode = historicalNode.copy(
                    contextualDataBody = historicalNode.contextualDataBody + "\n\n" + block,
                    compilationTimestamp = System.currentTimeMillis()
                )
                neuralGraph[matchingSegmentId] = evolvedNode
                recalculatePathingWeights(evolvedNode)
            } else {
                val newNode = NeuronSegment(
                    conceptHeadline = terms.take(4).joinToString(" ").uppercase(),
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

    private fun searchConflictResolutionLayer(incomingTerms: Set<String>): String? {
        var maximumAffinityScore = 0f
        var optimalMatchedId: String? = null
        
        for ((id, segment) in neuralGraph) {
            val mutualIntersections = segment.associatedKeywords.intersect(incomingTerms).size
            if (mutualIntersections == 0) continue
            val calculationAffinity = mutualIntersections.toFloat() / (segment.associatedKeywords.size + incomingTerms.size - mutualIntersections)
            
            if (calculationAffinity > 0.35f && calculationAffinity > maximumAffinityScore) {
                maximumAffinityScore = calculationAffinity
                optimalMatchedId = id
            }
        }
        return optimalMatchedId
    }

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

    fun streamSynthesisInteraction(prompt: String, conversationHistory: List<AppMessage>): Flow<String> = flow {
        val rawTerms = cleanTokenize(prompt)
        val expandedTerms = expandWithSynonyms(rawTerms)
        
        val mdTick = "\u0060\u0060\u0060"
        val tokenCollectorChannel = Channel<String>(Channel.UNLIMITED)
        
        // Intent Recognition
        val isAnalysisRequest = expandedTerms.intersect(semanticSynonymMap["analyze"] ?: emptySet()).isNotEmpty() || prompt.lowercase().contains("analyze")
        val isStatusRequest = prompt.lowercase().contains(Regex("(status|topology|brain|how are you)"))

        if (isStatusRequest) {
            val status = getNetworkTopologyDetails().split(" ")
            for (word in status) { emit("$word "); delay(30) }
            return@flow
        }

        // FLAW IDENTIFICATION & HEURISTIC ANALYSIS MODE
        if (isAnalysisRequest && activeWorkspaceContext != null) {
            val (fileName, fileContent) = activeWorkspaceContext!!
            emit("[ANALYSIS ENGINE INITIATED]\nTarget: $fileName\n\n")
            
            val activeLines = fileContent.lines()
            val codeTokens = cleanTokenize(fileContent)
            val matchedRules = extractedRules.filter { rule -> rule.triggerKeywords.intersect(codeTokens).size > 2 }
            
            if (matchedRules.isEmpty()) {
                val safeMsg = ("I have cross-referenced **$fileName** against my ingested documentation. " +
                        "Based on my current knowledge parameters, I did not identify any critical architectural flaws or rule violations.").split(" ")
                for (word in safeMsg) { emit("$word "); delay(30) }
                return@flow
            }

            emit("I have analyzed the active file against constraints learned from your reference materials. I found the following potential issues:\n\n")
            
            val processingJob = scope.launch {
                matchedRules.take(3).forEach { rule ->
                    tokenCollectorChannel.send("⚠️ **Potential Violation (Learned from ${rule.sourceTitle}):**\n")
                    tokenCollectorChannel.send("• *Constraint:* \"${rule.ruleConstraint}\"\n")
                    
                    // Attempt to find the specific line in the code causing the flaw
                    val suspectedLine = activeLines.firstOrNull { line -> 
                        rule.triggerKeywords.any { trigger -> line.lowercase().contains(trigger) && line.isNotBlank() }
                    }
                    
                    if (suspectedLine != null) {
                        tokenCollectorChannel.send("• *Identified Conflict:* \n$mdTick\n${suspectedLine.trim()}\n$mdTick\n\n")
                    } else {
                        tokenCollectorChannel.send("• *Identified Conflict:* Found architectural overlaps requiring manual review based on this rule.\n\n")
                    }
                    delay(400)
                }
            }
            
            var active = true
            val monitor = scope.launch { processingJob.join(); tokenCollectorChannel.close(); active = false }
            for (chunk in tokenCollectorChannel) { emit(chunk) }
            monitor.cancel()
            return@flow
        }

        // STANDARD KNOWLEDGE SYNTHESIS MODE
        val optimalMatches = mutableListOf<Pair<NeuronSegment, Float>>()
        engineMutex.withLock {
            for (segment in neuralGraph.values) {
                val matches = segment.associatedKeywords.intersect(expandedTerms).size
                if (matches > 0) {
                    val tfIdfScore = matches.toFloat() * (1.0f + ln(neuralGraph.size.toFloat() / (1f + matches.toFloat())))
                    optimalMatches.add(Pair(segment, tfIdfScore))
                }
            }
        }
        
        optimalMatches.sortByDescending { it.second }
        val localizedContextChunks = optimalMatches.take(3).map { it.first }
        
        if (localizedContextChunks.isEmpty()) {
            val neutralResponse = ("I lack the contextual knowledge to respond. " +
                    "Please open a relevant repository file or attach a document so I can learn the necessary parameters.").split(" ")
            for (word in neutralResponse) { emit("$word "); delay(40) }
            return@flow
        }
        
        val processingJobs = localizedContextChunks.map { targetNode ->
            scope.launch {
                val sentences = targetNode.contextualDataBody.split(Regex("(?<=[.!?])\\s+"))
                val relevantSentences = sentences.filter { sentence -> 
                    expandedTerms.any { term -> sentence.lowercase().contains(term) } 
                }.take(3).joinToString(" ")
                
                if (relevantSentences.isNotBlank()) {
                    tokenCollectorChannel.send("\nBased on **" + targetNode.originSource + "**:\n")
                    val words = relevantSentences.split(" ")
                    for (word in words) {
                        tokenCollectorChannel.send("$word ")
                        delay(30)
                    }
                    tokenCollectorChannel.send("\n")
                }
            }
        }
        
        emit("Synthesizing context from " + localizedContextChunks.size.toString() + " neural pathways:\n")
        
        var compilationStreamActive = true
        val safetyMonitor = scope.launch {
            processingJobs.joinAll()
            tokenCollectorChannel.close()
            compilationStreamActive = false
        }
        
        for (chunk in tokenCollectorChannel) { emit(chunk) }
        safetyMonitor.cancel()
    }

    private fun cleanTokenize(inputString: String): Set<String> {
        return inputString.lowercase()
            .split(Regex("[^a-zA-Z0-9_\\-\\.]"))
            .map { it.trim() }
            .filter { it.length > 2 && !lexiconStopWords.contains(it) }
            .toSet()
    }

    private fun expandWithSynonyms(baseTerms: Set<String>): Set<String> {
        val expanded = mutableSetOf<String>()
        expanded.addAll(baseTerms)
        for (term in baseTerms) {
            semanticSynonymMap.forEach { (key, synonyms) ->
                if (key == term || synonyms.contains(term)) {
                    expanded.add(key)
                    expanded.addAll(synonyms)
                }
            }
        }
        return expanded
    }
    
    fun getNetworkTopologyDetails(): String {
        val count = neuralGraph.size
        val rules = extractedRules.size
        return "Brain topology online. Tracking $count context segments and $rules learned architectural constraints."
    }
    
    fun isReady(): Boolean = true
    
    fun clearBrainTopology() {
        neuralGraph.clear()
        extractedRules.clear()
        activeWorkspaceContext = null
    }
}
