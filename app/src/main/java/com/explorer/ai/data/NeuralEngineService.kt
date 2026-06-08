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

class NeuralEngineService {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val engineMutex = Mutex()
    private val neuralGraph = mutableMapOf<String, NeuronSegment>()
    
    // Core stop words
    private val lexiconStopWords = setOf(
        "the", "and", "a", "of", "to", "in", "is", "that", "it", "for", "on", "with", "as", "this", "by", "an", "how", "what", "where", "why", "can", "you", "do", "does"
    )

    // Semantic Mapping: Expands user queries to cover technical terminology automatically
    private val semanticSynonymMap = mapOf(
        "build" to setOf("compile", "assemble", "make", "cmake", "gradle"),
        "error" to setOf("bug", "crash", "exception", "fail", "log"),
        "bridge" to setOf("jni", "native", "cpp", "c++", "interface"),
        "port" to setOf("recomp", "recompilation", "android", "architecture"),
        "explain" to setOf("summarize", "describe", "details", "overview")
    )

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
        
        // Intent Recognition Heuristics
        val isExplanationRequest = prompt.lowercase().contains(Regex("(explain|what is|how does|summarize)"))
        val isCodeRequest = prompt.lowercase().contains(Regex("(find|code|function|class|file)"))
        val isStatusRequest = prompt.lowercase().contains(Regex("(status|topology|brain|how are you)"))

        if (isStatusRequest) {
            val status = getNetworkTopologyDetails().split(" ")
            for (word in status) { emit("$word "); delay(30) }
            return@flow
        }

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
            val neutralResponse = ("I have searched my active neural pathways, but I lack the context to answer this. " +
                    "Please open a relevant repository file or attach a document so I can learn from it.").split(" ")
            for (word in neutralResponse) { emit("$word "); delay(40) }
            return@flow
        }

        val tokenCollectorChannel = Channel<String>(Channel.UNLIMITED)
        val mdTick = "\u0060\u0060\u0060"
        
        val processingJobs = localizedContextChunks.map { targetNode ->
            scope.launch {
                if (isCodeRequest) {
                    // Extract code-heavy blocks
                    val lines = targetNode.contextualDataBody.lines()
                    val codeLines = lines.filter { it.contains(Regex("(\\{|\\}|\\(|\\)|=|fun |class |val |var |import )")) }
                    if (codeLines.isNotEmpty()) {
                        tokenCollectorChannel.send("\n**Found in ${targetNode.originSource}:**\n$mdTick\n")
                        codeLines.take(10).forEach { tokenCollectorChannel.send(it + "\n"); delay(40) }
                        tokenCollectorChannel.send("$mdTick\n")
                    }
                } else if (isExplanationRequest || true) {
                    // Extract readable summaries (default fallback)
                    val sentences = targetNode.contextualDataBody.split(Regex("(?<=[.!?])\\s+"))
                    val relevantSentences = sentences.filter { sentence -> 
                        expandedTerms.any { term -> sentence.lowercase().contains(term) } 
                    }.take(3).joinToString(" ")
                    
                    if (relevantSentences.isNotBlank()) {
                        tokenCollectorChannel.send("\nAccording to my integration of **" + targetNode.originSource + "**:\n")
                        val words = relevantSentences.split(" ")
                        for (word in words) {
                            tokenCollectorChannel.send("$word ")
                            delay(40) // Simulate natural conversational typing speed
                        }
                        tokenCollectorChannel.send("\n")
                    }
                }
            }
        }
        
        emit("Based on my analysis of " + localizedContextChunks.size.toString() + " internal knowledge nodes:\n")
        
        var compilationStreamActive = true
        val safetyMonitor = scope.launch {
            processingJobs.joinAll()
            tokenCollectorChannel.close()
            compilationStreamActive = false
        }
        
        for (chunk in tokenCollectorChannel) {
            emit(chunk)
        }
        
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
        val interconnections = neuralGraph.values.sumOf { it.relationalWeights.size }
        return "All systems optimal. My current brain topology contains " + count.toString() + 
               " discrete knowledge segments linked by " + interconnections.toString() + " active neural pathways."
    }
    
    fun isReady(): Boolean = true
    
    fun clearBrainTopology() {
        neuralGraph.clear()
    }
}
