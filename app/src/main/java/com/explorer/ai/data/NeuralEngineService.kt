package com.explorer.ai.data

import com.explorer.ai.nlp.GrammarEngine
import kotlinx.coroutines.*
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
    val grammarEngine = GrammarEngine()

    private val neuralGraph = mutableMapOf<String, NeuronSegment>()
    private val extractedRules = mutableListOf<KnowledgeRule>()
    private val dynamicThesaurus = mutableMapOf<String, MutableMap<String, Int>>()

    private var activeWorkspaceContext: Pair<String, String>? = null

    private val lexiconStopWords = setOf(
        "the", "and", "a", "of", "to", "in", "is", "that", "it", "for", "on", "with",
        "as", "this", "by", "an", "how", "what", "where", "why", "can", "you", "do",
        "does", "are", "be", "or", "at", "from", "but", "not", "was", "were", "has",
        "have", "had", "will", "would", "could", "should", "may", "might", "its", "their"
    )

    private val baseSemanticSynonymMap = mapOf(
        "analyze" to setOf("review", "check", "audit", "inspect", "examine"),
        "meaning" to setOf("define", "definition", "explain", "describe", "purpose"),
        "how" to setOf("method", "process", "instruction", "steps", "procedure"),
        "hardware" to setOf("chip", "processor", "cpu", "memory", "ram", "register", "bus"),
        "address" to setOf("pointer", "location", "offset", "base", "mapped", "mapping"),
        "cpu" to setOf("processor", "mips", "r4300", "vr4300", "core", "pipeline"),
        "memory" to setOf("ram", "rdram", "dram", "cache", "buffer", "store", "storage"),
        "n64" to setOf("nintendo64", "ultra64", "rcp", "rsp", "rdp", "reality")
    )

    fun setActiveWorkspaceContext(fileName: String, fileContent: String) {
        activeWorkspaceContext = Pair(fileName, fileContent)
    }

    suspend fun learnFromDocumentChunk(title: String, chunkContent: String): Int = engineMutex.withLock {
        // Clean up common PDF artifacts (hyphenation, page headers, excessive whitespace)
        val cleanContent = chunkContent
            .replace(Regex("(?i)--- PAGE \\d+ ---"), "")
            .replace(Regex("-\\s*\\n\\s*"), "")       // de-hyphenate line breaks
            .replace(Regex("\\f"), "\n")               // form feeds to newlines
            .replace(Regex("\\r\\n?"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()

        val sentenceBlocks = grammarEngine.splitIntoSentences(cleanContent)
            .filter { grammarEngine.isCoherentEnglish(it) }

        var segmentsCreated = 0

        for (sentence in sentenceBlocks) {
            val terms = cleanTokenize(sentence)
            if (terms.size < 3) continue

            // Build co-occurrence vocabulary from this sentence
            for (word in terms) {
                if (dynamicThesaurus[word] == null) dynamicThesaurus[word] = mutableMapOf()
                for (otherWord in terms) {
                    if (word != otherWord) {
                        val count = dynamicThesaurus[word]!![otherWord] ?: 0
                        dynamicThesaurus[word]!![otherWord] = count + 1
                    }
                }
            }

            // Extract constraint/rule sentences
            if (sentence.lowercase().contains(
                    Regex("(must|should|always|never|avoid|error|deprecated|ensure|critical|warning|note|important)")
                )) {
                extractedRules.add(KnowledgeRule(title, sentence, terms.filter { it.length > 3 }.toSet()))
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

    /**
     * Synthesises a response from the knowledge graph in a sequential, coherent manner.
     * The previous version ran parallel coroutines writing to a shared channel which
     * caused word interleaving. This version collects all results first then streams them.
     */
    fun streamSynthesisInteraction(prompt: String, conversationHistory: List<AppMessage>): Flow<String> = flow {
        val rawTerms = cleanTokenize(prompt)
        val expandedTerms = expandWithDynamicThesaurus(rawTerms)

        val isStatusRequest = prompt.lowercase().contains(Regex("(status|topology|brain|how are you|how many|nodes)"))
        val isAnalysisRequest = expandedTerms.intersect(baseSemanticSynonymMap["analyze"] ?: emptySet()).isNotEmpty()
                || prompt.lowercase().contains("analyze")

        // --- Status query ---
        if (isStatusRequest) {
            val status = getNetworkTopologyDetails()
            emitWordByWord(status)
            return@flow
        }

        // --- Analysis query against active file ---
        if (isAnalysisRequest && activeWorkspaceContext != null) {
            val (fileName, fileContent) = activeWorkspaceContext!!
            val codeTokens = cleanTokenize(fileContent)
            val matchedRules = extractedRules.filter { rule -> rule.triggerKeywords.intersect(codeTokens).size > 2 }

            if (matchedRules.isEmpty()) {
                emitWordByWord("I have cross-referenced $fileName against my ingested documentation. Based on my current knowledge, I did not identify any architectural flaws or rule violations.")
                return@flow
            }

            emit("I have analyzed the active file against learned constraints. Potential issues found:\n\n")
            val lines = fileContent.lines()

            for (rule in matchedRules.take(3)) {
                val bestConstraint = grammarEngine.extractBestAnswer(rule.ruleConstraint, rule.triggerKeywords)
                if (bestConstraint.isNotBlank()) {
                    emit("⚠️ **Constraint (from ${rule.sourceTitle}):**\n")
                    emit("• $bestConstraint\n")
                    val suspectedLine = lines.firstOrNull { line ->
                        rule.triggerKeywords.any { t -> line.lowercase().contains(t) && line.isNotBlank() }
                    }
                    if (suspectedLine != null) {
                        emit("• Conflict: `${suspectedLine.trim()}`\n\n")
                    }
                }
            }
            return@flow
        }

        // --- Empty knowledge base ---
        if (neuralGraph.isEmpty()) {
            emitWordByWord("I have no ingested knowledge yet. Please attach a document using the 📎 button so I can learn from it.")
            return@flow
        }

        // --- Standard knowledge query ---
        // Score all nodes, collect sequentially (no parallel coroutines)
        val candidates = mutableListOf<Pair<NeuronSegment, Float>>()
        engineMutex.withLock {
            for (segment in neuralGraph.values) {
                val matches = segment.associatedKeywords.intersect(expandedTerms).size
                if (matches > 0) {
                    val score = matches.toFloat() * (1.0f + ln(neuralGraph.size.toFloat() / (1f + matches.toFloat())))
                    candidates.add(Pair(segment, score))
                }
            }
        }

        candidates.sortByDescending { it.second }
        val topNodes = candidates.filter { it.second > 1.0f }.take(6).map { it.first }

        if (topNodes.isEmpty() || expandedTerms.isEmpty()) {
            emitWordByWord("I don't have validated context for that query in my current knowledge base. Try ingesting a relevant document first.")
            return@flow
        }

        // Group nodes by source so we can present coherent grouped answers
        val bySource = topNodes.groupBy { it.originSource }

        var totalOutputs = 0
        for ((source, nodes) in bySource) {
            // Collect all the relevant sentences from this source group
            val allContext = nodes.joinToString(" ") { it.contextualDataBody }
            val relevantSentences = grammarEngine.extractRelevantSentences(allContext, expandedTerms, maxResults = 3)

            if (relevantSentences.isNotEmpty()) {
                totalOutputs++
                emit("\n**From $source:**\n")
                for (sentence in relevantSentences) {
                    val verified = verifyRelevance(sentence, expandedTerms)
                    if (verified.isNotBlank()) {
                        emit("$verified\n")
                        delay(15) // Small pacing delay; no word splitting = no scrambling
                    }
                }
            }
        }

        if (totalOutputs == 0) {
            emitWordByWord("My knowledge base contains related topics, but I could not find a direct answer to your specific question. Try rephrasing or ingesting more documentation.")
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.emitWordByWord(text: String) {
        val words = text.split(" ")
        for (word in words) {
            emit("$word ")
            delay(25)
        }
    }

    private fun verifyRelevance(sentence: String, queryTerms: Set<String>): String {
        if (sentence.isBlank()) return ""
        val tokens = cleanTokenize(sentence)
        val overlap = tokens.intersect(queryTerms)
        return if (overlap.isNotEmpty()) sentence else ""
    }

    private fun cleanTokenize(inputString: String): Set<String> {
        return inputString.lowercase()
            .split(Regex("[^a-zA-Z0-9_\\-\\.\\$]"))
            .map { it.trim().trimEnd('.', ',', ';') }
            .filter { token ->
                token.length > 2 &&
                !lexiconStopWords.contains(token) &&
                !token.matches(Regex("\\d+")) // skip bare numbers
            }
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
                expanded.addAll(learnedCorrelations.entries.sortedByDescending { it.value }.take(5).map { it.key })
            }
        }
        return expanded
    }

    fun getNetworkTopologyDetails(): String {
        val count = neuralGraph.size
        val rules = extractedRules.size
        val dictSize = dynamicThesaurus.size
        val sources = neuralGraph.values.map { it.originSource }.toSet().size
        return "Knowledge base online. I have learned $count facts from $sources source(s), extracted $rules constraint rules, and built a vocabulary of $dictSize term correlations."
    }

    fun isReady(): Boolean = true

    fun clearBrainTopology() {
        neuralGraph.clear()
        extractedRules.clear()
        dynamicThesaurus.clear()
        activeWorkspaceContext = null
    }
}
