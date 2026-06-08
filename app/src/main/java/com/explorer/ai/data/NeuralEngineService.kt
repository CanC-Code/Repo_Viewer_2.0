package com.explorer.ai.data

import com.explorer.ai.nlp.GrammarEngine
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
    val associatedKeywords: Set<String>,
    val contextualDataBody: String,
    val originSource: String,
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
    private val grammarEngine = GrammarEngine()
    
    private val neuralGraph = mutableMapOf<String, NeuronSegment>()
    private val extractedRules = mutableListOf<KnowledgeRule>()
    private val dynamicThesaurus = mutableMapOf<String, MutableMap<String, Int>>()
    
    private var activeWorkspaceContext: Pair<String, String>? = null
    
    private val lexiconStopWords = setOf(
        "the", "and", "a", "of", "to", "in", "is", "that", "it", "for", "on", "with", "as", "this", "by", "an", "how", "what", "where", "why", "can", "you", "do", "does", "are", "be", "or"
    )

    // Base generic mappings; all domain-specific knowledge is now dynamically learned via ingestion
    private val baseSemanticSynonymMap = mapOf(
        "analyze" to setOf("review", "flaw", "issue", "check", "vulnerability", "leak", "problem", "audit"),
        "meaning" to setOf("define", "definition", "explain", "describe", "purpose"),
        "how" to setOf("method", "process", "instruction", "steps", "way")
    )

    fun setActiveWorkspaceContext(fileName: String, fileContent: String) {
        activeWorkspaceContext = Pair(fileName, fileContent)
    }

    suspend fun learnFromDocumentChunk(title: String, chunkContent: String): Int = engineMutex.withLock {
        val cleanContent = chunkContent
            .replace(Regex("(?i)--- PAGE \\d+ ---"), "")
            .replace(Regex("-\\s*\\n\\s*"), "")
            .replace(Regex("\\n+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        val sentenceBlocks = cleanContent.split(Regex("(?<=[.!?])\\s+"))
            .filter { grammarEngine.isCoherentEnglish(it) } // Strict garbage filtration
            
        var segmentsCreated = 0
        
        for (sentence in sentenceBlocks) {
            val terms = cleanTokenize(sentence)
            if (terms.size < 4) continue
            
            // Dynamic Vocabulary Mapping (On-The-Fly Contextual Intelligence)
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
            
            val newNode = NeuronSegment(
                associatedKeywords = terms,
                contextualDataBody = sentence,
                originSource = title
            )
            neuralGraph[newNode.id] = newNode
            segmentsCreated++
        }
        return@withLock segmentsCreated
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
                        "Based on my current validated knowledge, I did not identify any architectural flaws or rule violations.").split(" ")
                for (word in safeMsg) { emit("$word "); delay(30) }
                return@flow
            }

            emit("I have analyzed the active file against learned constraints. I found the following potential issues:\n\n")
            
            val processingJob = scope.launch {
                matchedRules.take(3).forEach { rule ->
                    val verifiedRuleText = grammarEngine.extractBestAnswer(rule.ruleConstraint, rule.triggerKeywords)
                    if (verifiedRuleText.isNotBlank()) {
                        tokenCollectorChannel.send("⚠️ **Potential Violation (Learned from ${rule.sourceTitle}):**\n")
                        tokenCollectorChannel.send("• *Constraint:* \"$verifiedRuleText\"\n")
                        
                        val suspectedLine = activeLines.firstOrNull { line -> 
                            rule.triggerKeywords.any { trigger -> line.lowercase().contains(trigger) && line.isNotBlank() }
                        }
                        
                        if (suspectedLine != null) {
                            tokenCollectorChannel.send("• *Identified Conflict:* \n$mdTick\n${suspectedLine.trim()}\n$mdTick\n\n")
                        } else {
                            tokenCollectorChannel.send("• *Identified Conflict:* Contextual overlaps require manual review based on this rule.\n\n")
                        }
                        delay(400)
                    }
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
        
        // Strict relevance threshold. Prevents random matching when the engine truly doesn't know.
        val validMatches = optimalMatches.filter { it.second > 1.2f }.take(3).map { it.first }
        
        if (validMatches.isEmpty() || expandedTerms.isEmpty()) {
            val neutralResponse = ("I lack validated context for this query. " +
                    "Please ingest relevant documentation so I can analyze the topic accurately.").split(" ")
            for (word in neutralResponse) { emit("$word "); delay(40) }
            return@flow
        }
        
        var outputsGenerated = 0
        val processingJobs = validMatches.map { targetNode ->
            scope.launch {
                // Truth Verification Loop
                val reflectedOutput = reflectAndVerifyTruth(expandedTerms, targetNode.contextualDataBody)
                
                if (reflectedOutput.isNotBlank()) {
                    outputsGenerated++
                    tokenCollectorChannel.send("\nBased on **" + targetNode.originSource + "**:\n")
                    val words = reflectedOutput.split(" ")
                    for (word in words) {
                        tokenCollectorChannel.send("$word ")
                        delay(30)
                    }
                    tokenCollectorChannel.send("\n")
                }
            }
        }
        
        var compilationStreamActive = true
        val safetyMonitor = scope.launch {
            processingJobs.joinAll()
            
            // If the verification loop suppressed all outputs because they failed the truth check
            if (outputsGenerated == 0) {
                tokenCollectorChannel.send("My internal evaluation determined the available memory nodes did not directly answer your query. Context is insufficient.")
            }
            
            tokenCollectorChannel.close()
            compilationStreamActive = false
        }
        
        for (chunk in tokenCollectorChannel) { emit(chunk) }
        safetyMonitor.cancel()
    }

    /**
     * The internal correction mechanism.
     * Extracts the best exact sentence, then mathematically verifies if the 
     * resulting sentence actually contains the queried concepts. If it is an unrelated hallucination, it returns empty.
     */
    private fun reflectAndVerifyTruth(promptContext: Set<String>, rawMemoryData: String): String {
        val bestSentence = grammarEngine.extractBestAnswer(rawMemoryData, promptContext)
        if (bestSentence.isBlank()) return ""

        val outputTokens = cleanTokenize(bestSentence)
        val semanticOverlap = outputTokens.intersect(promptContext)
        
        // Truth Check: If the sentence doesn't share any foundational terms with the prompt, it's irrelevant.
        if (semanticOverlap.isEmpty()) {
            return ""
        }
        
        return bestSentence
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
            baseSemanticSynonymMap.forEach { (key, synonyms) ->
                if (key == term || synonyms.contains(term)) {
                    expanded.add(key)
                    expanded.addAll(synonyms)
                }
            }
            // Add top 3 co-occurring terms learned entirely from user documents
            val learnedCorrelations = dynamicThesaurus[term]
            if (learnedCorrelations != null) {
                expanded.addAll(learnedCorrelations.entries.sortedByDescending { it.value }.take(3).map { it.key })
            }
        }
        return expanded
    }
    
    fun getNetworkTopologyDetails(): String {
        val count = neuralGraph.size
        val rules = extractedRules.size
        val dictSize = dynamicThesaurus.size
        return "Brain topology online. Tracking $count coherent context segments, $rules architectural constraints, and $dictSize dynamic vocabulary correlations."
    }
    
    fun isReady(): Boolean = true
    
    fun clearBrainTopology() {
        neuralGraph.clear()
        extractedRules.clear()
        dynamicThesaurus.clear()
        activeWorkspaceContext = null
    }
}
