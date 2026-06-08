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
    
    // Dynamic Co-occurrence Associative Memory (Word -> {Correlated Word -> Frequency})
    private val dynamicThesaurus = mutableMapOf<String, MutableMap<String, Int>>()
    
    private var activeWorkspaceContext: Pair<String, String>? = null
    
    private val lexiconStopWords = setOf(
        "the", "and", "a", "of", "to", "in", "is", "that", "it", "for", "on", "with", "as", "this", "by", "an", "how", "what", "where", "why", "can", "you", "do", "does", "are", "be", "or"
    )

    private val baseSemanticSynonymMap = mapOf(
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
        // Grammatical Sanitation: Fix broken english caused by PDF/OCR formatting artifacts
        val cleanContent = rawContent
            .replace(Regex("(?i)--- PAGE \\d+ ---"), "") // Remove pagination
            .replace(Regex("-\\s*\\n\\s*"), "") // Reconnect hyphenated line-breaks
            .replace(Regex("\\n+"), " ") // Collapse hard returns so sentences stay intact
            .replace(Regex("\\s{2,}"), " ") // Normalize spaces
            .trim()

        // Extract complete, structurally sound sentences only
        val sentenceBlocks = cleanContent.split(Regex("(?<=[.!?])\\s+"))
            .filter { it.length > 25 && it.split(" ").size > 4 }
            
        var internalSegmentsCreated = 0
        
        for (sentence in sentenceBlocks) {
            val terms = cleanTokenize(sentence)
            if (terms.size < 4) continue
            
            // Build Contextual Dictionary On-The-Fly
            for (word in terms) {
                if (dynamicThesaurus[word] == null) dynamicThesaurus[word] = mutableMapOf()
                for (otherWord in terms) {
                    if (word != otherWord) {
                        val count = dynamicThesaurus[word]!![otherWord] ?: 0
                        dynamicThesaurus[word]!![otherWord] = count + 1
                    }
                }
            }
            
            if (sentence.lowercase().contains(Regex("(must|should|always|never|avoid|leak|error|deprecated|vulnerability|ensure|critical)"))) {
                extractedRules.add(KnowledgeRule(title, sentence, terms.filter { it.length > 4 }.toSet()))
            }
            
            val matchingSegmentId = searchConflictResolutionLayer(terms)
            
            if (matchingSegmentId != null) {
                val historicalNode = neuralGraph[matchingSegmentId]!!
                val evolvedNode = historicalNode.copy(
                    contextualDataBody = historicalNode.contextualDataBody + "\n\n" + sentence,
                    compilationTimestamp = System.currentTimeMillis()
                )
                neuralGraph[matchingSegmentId] = evolvedNode
                recalculatePathingWeights(evolvedNode)
            } else {
                val newNode = NeuronSegment(
                    conceptHeadline = terms.take(4).joinToString(" ").uppercase(),
                    associatedKeywords = terms,
                    contextualDataBody = sentence,
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
        val expandedTerms = expandWithDynamicThesaurus(rawTerms)
        
        val mdTick = "\u0060\u0060\u0060"
        val tokenCollectorChannel = Channel<String>(Channel.UNLIMITED)
        
        val isAnalysisRequest = expandedTerms.intersect(baseSemanticSynonymMap["analyze"] ?: emptySet()).isNotEmpty() || prompt.lowercase().contains("analyze")
        val isStatusRequest = prompt.lowercase().contains(Regex("(status|topology|brain|how are you)"))

        if (isStatusRequest) {
            val status = getNetworkTopologyDetails().split(" ")
            for (word in status) { emit("$word "); delay(30) }
            return@flow
        }

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
                    tokenCollectorChannel.send("• *Constraint:* \"${formatGrammar(rule.ruleConstraint)}\"\n")
                    
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
                val bestSentence = sentences.firstOrNull { sentence -> 
                    expandedTerms.any { term -> sentence.lowercase().contains(term) } 
                }
                
                if (bestSentence != null && bestSentence.isNotBlank()) {
                    val prefixOptions = listOf(
                        "Based on my analysis of ",
                        "Reviewing the knowledge extracted from ",
                        "My contextual synthesis of "
                    )
                    tokenCollectorChannel.send("\n" + prefixOptions.random() + "**" + targetNode.originSource + "**:\n")
                    
                    val properSentence = formatGrammar(bestSentence)
                    val words = properSentence.split(" ")
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

    private fun expandWithDynamicThesaurus(baseTerms: Set<String>): Set<String> {
        val expanded = mutableSetOf<String>()
        expanded.addAll(baseTerms)
        
        for (term in baseTerms) {
            // Apply base mapped synonyms
            baseSemanticSynonymMap.forEach { (key, synonyms) ->
                if (key == term || synonyms.contains(term)) {
                    expanded.add(key)
                    expanded.addAll(synonyms)
                }
            }
            
            // Apply On-The-Fly dynamically learned semantic correlations (Top 3 most frequently co-occurring words)
            val learnedCorrelations = dynamicThesaurus[term]
            if (learnedCorrelations != null) {
                val topCorrelated = learnedCorrelations.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { it.key }
                expanded.addAll(topCorrelated)
            }
        }
        return expanded
    }
    
    // Syntactic wrapper to ensure robotic fragments sound like perfect English
    private fun formatGrammar(rawSentence: String): String {
        var formatted = rawSentence.trim()
        if (formatted.isNotEmpty()) {
            formatted = formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            if (!formatted.matches(Regex(".*[.!?]$"))) {
                formatted += "."
            }
        }
        return formatted
    }
    
    fun getNetworkTopologyDetails(): String {
        val count = neuralGraph.size
        val rules = extractedRules.size
        val dictSize = dynamicThesaurus.size
        return "Brain topology online. Tracking $count context segments, $rules learned architectural constraints, and $dictSize dynamic vocabulary correlations."
    }
    
    fun isReady(): Boolean = true
    
    fun clearBrainTopology() {
        neuralGraph.clear()
        extractedRules.clear()
        dynamicThesaurus.clear()
        activeWorkspaceContext = null
    }
}
