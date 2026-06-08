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
                    contextualDataBody = "${historicalNode.contextualDataBody}\n[Evolved Understanding via $title]:\n$block",
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
                        tokenCollectorChannel.send("\n```kotlin\n// Reference Path: ${targetNode.originSource}\n$line\n
```\n")
                    } else if (line.isNotBlank()) {
                        tokenCollectorChannel.send("• $line\n")
                    }
                    delay(80)
                }
            }
        }
        
        emit("[ON-DEVICE KNOWLEDGE SYNTHESIS ENGINE]\n")
        emit("Traversed ${localizedContextChunks.size} relevant knowledge nodes with independent neural weight vectors.\n\n")
        emit("### Integrated Analysis Insights:\n")
        
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
    
    fun getNetworkTopologyDetails(): String {
        val count = neuralGraph.size
        val interconnections = neuralGraph.values.sumOf { it.relationalWeights.size }
        return "Operational Brain Topology: $count discrete segments established with $interconnections dynamic path link mappings."
    }
    
    fun isReady(): Boolean = true
    fun clearBrainTopology() {
        neuralGraph.clear()
    }
}
