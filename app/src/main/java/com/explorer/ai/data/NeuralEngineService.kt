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

class NeuralEngineService {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val engineMutex = Mutex()
    private val grammarEngine = GrammarEngine() // Incorporate the new English rulebook
    
    private val neuralGraph = mutableMapOf<String, NeuronSegment>()
    private val dynamicThesaurus = mutableMapOf<String, MutableMap<String, Int>>()
    
    private val lexiconStopWords = setOf(
        "the", "and", "a", "of", "to", "in", "is", "that", "it", "for", "on", "with", "as", "this", "by", "an", "are", "be", "or"
    )

    private val baseSemanticSynonymMap = mapOf(
        "build" to setOf("compile", "assemble", "make", "cmake", "gradle"),
        "error" to setOf("bug", "crash", "exception", "fail", "segfault", "trace"),
        "bridge" to setOf("jni", "native", "cpp", "c++", "interface"),
        "port" to setOf("recomp", "recompilation", "android", "architecture", "mips", "arm64", "x86", "endianness"),
        "hardware" to setOf("rcp", "rdp", "rsp", "tmem", "dma", "tlb")
    )

    // Changed to accept chunked streaming to prevent OOM errors on massive PDFs
    suspend fun learnFromDocumentChunk(title: String, chunkContent: String): Int = engineMutex.withLock {
        val cleanContent = chunkContent
            .replace(Regex("(?i)--- PAGE \\d+ ---"), "")
            .replace(Regex("-\\s*\\n\\s*"), "")
            .replace(Regex("\\n+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        val sentenceBlocks = cleanContent.split(Regex("(?<=[.!?])\\s+"))
            .filter { grammarEngine.isCoherentEnglish(it) } // Pre-filter garbage before it enters memory
            
        var segmentsCreated = 0
        
        for (sentence in sentenceBlocks) {
            val terms = cleanTokenize(sentence)
            if (terms.size < 4) continue
            
            // Dynamic Vocabulary Mapping
            for (word in terms) {
                if (dynamicThesaurus[word] == null) dynamicThesaurus[word] = mutableMapOf()
                for (otherWord in terms) {
                    if (word != otherWord) {
                        val count = dynamicThesaurus[word]!![otherWord] ?: 0
                        dynamicThesaurus[word]!![otherWord] = count + 1
                    }
                }
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
        val tokenCollectorChannel = Channel<String>(Channel.UNLIMITED)

        if (prompt.lowercase().contains(Regex("(status|topology|brain)"))) {
            val status = "Brain topology active. Tracking ${neuralGraph.size} strictly validated memory segments and ${dynamicThesaurus.size} dynamic vocabulary correlations.".split(" ")
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
            val neutralResponse = ("I lack validated context. Please ingest a relevant document so I can establish correct neural pathways.").split(" ")
            for (word in neutralResponse) { emit("$word "); delay(40) }
            return@flow
        }
        
        val processingJobs = localizedContextChunks.map { targetNode ->
            scope.launch {
                // THE REFLECTION LOOP: Neurons firing to validate output before sending
                val rawOutput = targetNode.contextualDataBody
                val reflectedOutput = reflectAndCorrect(expandedTerms, rawOutput)
                
                if (reflectedOutput.isNotBlank()) {
                    tokenCollectorChannel.send("\nContext via **${targetNode.originSource}**:\n")
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
            tokenCollectorChannel.close()
            compilationStreamActive = false
        }
        
        for (chunk in tokenCollectorChannel) { emit(chunk) }
        safetyMonitor.cancel()
    }

    /**
     * Internal neuron firing: Checks if the intended output makes sense and obeys English rules.
     * If it is babble, it uses the GrammarEngine to reconstruct a coherent thought.
     */
    private fun reflectAndCorrect(promptContext: Set<String>, intendedOutput: String): String {
        if (grammarEngine.isCoherentEnglish(intendedOutput)) {
            // Truth check: Does it actually relate to what was asked?
            val outputTerms = cleanTokenize(intendedOutput)
            if (outputTerms.intersect(promptContext).isNotEmpty()) {
                return intendedOutput
            }
        }
        // If it fails the coherence or truth check, reconstruct it safely.
        return grammarEngine.reconstructThought(promptContext, intendedOutput)
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
            val learnedCorrelations = dynamicThesaurus[term]
            if (learnedCorrelations != null) {
                expanded.addAll(learnedCorrelations.entries.sortedByDescending { it.value }.take(2).map { it.key })
            }
        }
        return expanded
    }
    
    fun clearBrainTopology() {
        neuralGraph.clear()
        dynamicThesaurus.clear()
    }
}
