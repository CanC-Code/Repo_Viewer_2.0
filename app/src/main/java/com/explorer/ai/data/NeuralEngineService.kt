package com.explorer.ai.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.explorer.ai.nlp.GrammarEngine
import com.explorer.ai.ui.DocumentRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.ln
import kotlin.math.min

// ─── Knowledge node stored in memory ────────────────────────────────────────
data class KnowledgeNode(
    val id: String = UUID.randomUUID().toString(),
    val sentence: String,
    val keywords: Set<String>,
    val source: String,
    // live-updated: how often this node matched a query → reinforces relevance over time
    var hitCount: Int = 0
)

// ─── Constraint rule extracted from imperative sentences ────────────────────
data class KnowledgeRule(
    val source: String,
    val sentence: String,
    val keywords: Set<String>
)

class NeuralEngineService(private val context: Context) : DocumentRetriever {

    private val grammar = GrammarEngine()

    // Primary knowledge store – all ingested facts live here
    private val knowledgeGraph = mutableMapOf<String, KnowledgeNode>()

    // Co-occurrence thesaurus: built live as documents are ingested
    // word → (related_word → co-occurrence count)
    private val coOccurrence = mutableMapOf<String, MutableMap<String, Int>>()

    // Extracted imperative rules (must/should/never/warning etc.)
    private val rules = mutableListOf<KnowledgeRule>()

    // Conversation context: last N user turns held in memory for follow-up resolution
    private val conversationBuffer = ArrayDeque<Pair<String, String>>(10) // (user, ai)

    private val stopWords = setOf(
        "the","and","a","of","to","in","is","that","it","for","on","with","as","this","by",
        "an","how","what","where","why","can","you","do","does","are","be","or","at","from",
        "but","not","was","were","has","have","had","will","would","could","should","may",
        "might","its","their","there","then","when","which","who","tell","me","about","give"
    )

    // ── Seeded synonym map; expands itself via co-occurrence during ingestion ──
    private val synonymSeeds = mapOf(
        "hardware" to setOf("chip","processor","cpu","memory","ram","register","bus","board","circuit"),
        "address"  to setOf("pointer","location","offset","base","mapped","mapping","segment"),
        "cpu"      to setOf("processor","mips","r4300","vr4300","core","pipeline","risc"),
        "memory"   to setOf("ram","rdram","dram","cache","buffer","store","storage","heap","stack"),
        "n64"      to setOf("nintendo64","ultra64","rcp","rsp","rdp","reality","console","cartridge"),
        "graphics" to setOf("rcp","rdp","rsp","display","render","pixel","texture","polygon","vi"),
        "register" to setOf("r0","t0","v0","a0","sp","ra","gp","fp","reg","regfile"),
        "install"  to setOf("installation","setup","configure","configure","directory","autoexec"),
        "error"    to setOf("fault","warning","exception","crash","fail","invalid","illegal","undefined"),
        "debug"    to setOf("debugger","breakpoint","trace","inspect","halt","reset","target","sn")
    )

    // ── PDF ingestion entry point ─────────────────────────────────────────────
    suspend fun indexPdfDocument(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            Log.d("NeuralEngine", "Starting document ingestion...")
            val rawText = PdfProcessor.extractTextAndAssetsFromUri(context, uri)
            val nodeCount = learnFromText(rawText, sourceLabel = uri.lastPathSegment ?: "document.pdf")
            Log.d("NeuralEngine", "Ingestion complete. Stored $nodeCount knowledge nodes.")
        } catch (e: Exception) {
            Log.e("NeuralEngine", "Ingestion fault: ${e.message}")
        }
    }

    // ── Core learning pipeline ────────────────────────────────────────────────
    fun learnFromText(rawText: String, sourceLabel: String): Int {
        // 1. Pre-process: strip TOC leaders, page markers, bullet artifacts
        val cleaned = rawText
            .replace(Regex("---\\s*PAGE_START:\\s*\\d+\\s*---"), " ")
            .replace(Regex("---\\s*PAGE_END:\\s*\\d+\\s*---"), " ")
            .replace(Regex("\\[PARAGRAPH_START\\]|\\[PARAGRAPH_END\\]"), " ")
            .replace(Regex("\\[COLUMN_START\\]|\\[COLUMN_END\\]"), " ")
            .replace(Regex("\\[VISUAL_ANCHOR:[^\\]]+\\]"), " ")
            .replace(Regex("\\.{4,}"), " ")          // TOC dots
            .replace(Regex("-\\s*\\n\\s+([a-z])"), "$1") // de-hyphenate
            .replace(Regex("\\s*[•·▸►▶‣⁃∙◦]\\s+"), "\n") // bullets → newlines
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        val sentences = grammar.splitIntoSentences(cleaned)
        var created = 0

        for (sentence in sentences) {
            if (!grammar.isCoherentEnglish(sentence)) continue
            val tokens = tokenize(sentence)
            if (tokens.size < 3) continue

            // 2. Build live co-occurrence thesaurus
            for (w in tokens) {
                val map = coOccurrence.getOrPut(w) { mutableMapOf() }
                for (other in tokens) {
                    if (w != other) map[other] = (map[other] ?: 0) + 1
                }
            }

            // 3. Extract constraint rules
            if (sentence.lowercase().contains(
                    Regex("\\b(must|should|never|always|avoid|warning|note|important|critical|error|fault|illegal|undefined)\\b")
                )) {
                rules.add(KnowledgeRule(sourceLabel, sentence, tokens))
            }

            // 4. Store knowledge node
            val node = KnowledgeNode(sentence = sentence, keywords = tokens, source = sourceLabel)
            knowledgeGraph[node.id] = node
            created++
        }

        Log.d("NeuralEngine", "Learned $created sentences from '$sourceLabel'. Graph size: ${knowledgeGraph.size}")
        return created
    }

    // ── DocumentRetriever interface: used by ChatViewModel if wired ───────────
    override suspend fun search(query: String, topK: Int): List<String> = withContext(Dispatchers.IO) {
        val results = retrieveRelevantNodes(query, topK)
        if (results.isEmpty()) return@withContext listOf("No indexed knowledge found for: $query")
        results.map { it.sentence }
    }

    // ── Main response generation ──────────────────────────────────────────────
    /**
     * Generates a coherent natural-language response to a query by:
     * 1. Expanding query terms via synonym seeds + learned co-occurrence
     * 2. Scoring all knowledge nodes by relevance (TF-IDF-style)
     * 3. Reinforcing hit nodes (live weight update)
     * 4. Synthesising a clean multi-sentence answer grouped by source
     * 5. Detecting low-confidence and requesting clarification if needed
     */
    suspend fun generateResponse(query: String): String = withContext(Dispatchers.Default) {
        if (knowledgeGraph.isEmpty()) {
            return@withContext "I have no ingested knowledge yet. Use the 'Ingest Technical Manual (PDF)' button to load a document so I can answer questions about it."
        }

        // Special: status request
        if (query.lowercase().matches(Regex(".*(status|topology|nodes|how many|brain|memory).*(have|contain|store|learn|know).*"))) {
            return@withContext getStatusSummary()
        }

        val queryTokens = tokenize(query)
        val expanded = expandTerms(queryTokens)

        // Score nodes
        val candidates = knowledgeGraph.values.mapNotNull { node ->
            val overlap = node.keywords.intersect(expanded).size
            if (overlap == 0) return@mapNotNull null
            val idf = ln(knowledgeGraph.size.toFloat() / (1f + overlap))
            val score = overlap * (1f + idf) * (1f + node.hitCount * 0.05f)
            Pair(node, score)
        }

        if (candidates.isEmpty()) {
            // Low confidence: ask for clarification
            val clarification = buildClarificationRequest(query, queryTokens)
            return@withContext clarification
        }

        val topNodes = candidates.sortedByDescending { it.second }
            .take(8)
            .map { it.first }

        // Reinforce hit nodes (live weight update)
        topNodes.forEach { it.hitCount++ }

        // Deduplicate and group by source
        val usedSentences = mutableSetOf<String>()
        val grouped = topNodes.groupBy { it.source }

        val answer = buildString {
            var sentenceCount = 0
            val maxSentences = 5

            for ((source, nodes) in grouped) {
                if (sentenceCount >= maxSentences) break

                val allText = nodes.joinToString(" ") { it.sentence }
                val relevant = grammar.extractRelevantSentences(allText, expanded, maxResults = 2)

                for (sentence in relevant) {
                    if (sentence in usedSentences) continue
                    if (sentenceCount >= maxSentences) break

                    // Clean the sentence of source labels before appending
                    val clean = sentence
                        .replace(Regex("\\(Pg \\d+\\)"), "")
                        .replace(Regex("Based on [^:]+:"), "")
                        .trim()

                    if (clean.length > 20) {
                        if (sentenceCount == 0) append(clean) else append(" $clean")
                        usedSentences.add(sentence)
                        sentenceCount++
                    }
                }
            }
        }.trim()

        if (answer.isBlank()) {
            return@withContext buildClarificationRequest(query, queryTokens)
        }

        // Store this exchange in conversation buffer for follow-up context
        val truncatedAnswer = answer.take(300)
        if (conversationBuffer.size >= 10) conversationBuffer.removeFirst()
        conversationBuffer.addLast(Pair(query, truncatedAnswer))

        return@withContext grammar.formatGrammar(answer)
    }

    // ── Retrieves top-K nodes for a query ─────────────────────────────────────
    private fun retrieveRelevantNodes(query: String, topK: Int): List<KnowledgeNode> {
        val tokens = tokenize(query)
        val expanded = expandTerms(tokens)
        return knowledgeGraph.values
            .filter { it.keywords.intersect(expanded).isNotEmpty() }
            .sortedByDescending { it.keywords.intersect(expanded).size.toFloat() + it.hitCount * 0.05f }
            .take(topK)
    }

    // ── Clarification request when query matches nothing ─────────────────────
    private fun buildClarificationRequest(query: String, queryTokens: Set<String>): String {
        // Check if query terms partially match anything
        val partialMatches = knowledgeGraph.values
            .filter { node -> queryTokens.any { t -> node.keywords.any { k -> k.contains(t) || t.contains(k) } } }
            .take(3)
            .map { it.source }
            .toSet()

        return if (partialMatches.isNotEmpty()) {
            "I found related content in: ${partialMatches.joinToString(", ")}. " +
            "Could you be more specific? For example, are you asking about the hardware specifications, " +
            "software installation, memory addressing, or error codes?"
        } else {
            "I don't have specific knowledge about '${queryTokens.take(3).joinToString(" ")}' in my current documents. " +
            "I have ${knowledgeGraph.size} facts loaded. " +
            "Try asking about: N64 hardware, memory addresses, CPU registers, installation, or debugging."
        }
    }

    // ── Resolves follow-up pronouns using conversation buffer ─────────────────
    fun resolveFollowUp(query: String): String {
        val lower = query.lowercase()
        // If query is very short or uses pronouns, augment with last context
        if ((lower.length < 25 || lower.contains(Regex("\\b(it|this|that|they|those|its|their)\\b")))
            && conversationBuffer.isNotEmpty()) {
            val lastExchange = conversationBuffer.last()
            return "${lastExchange.first} $query"
        }
        return query
    }

    // ── Term expansion: seed synonyms + live co-occurrence ───────────────────
    private fun expandTerms(base: Set<String>): Set<String> {
        val result = mutableSetOf<String>()
        result.addAll(base)
        for (term in base) {
            // Seed synonyms
            synonymSeeds.forEach { (key, synonyms) ->
                if (key == term || synonyms.contains(term)) {
                    result.add(key); result.addAll(synonyms)
                }
            }
            // Learned co-occurrence (top 5 most-correlated words)
            coOccurrence[term]?.entries
                ?.sortedByDescending { it.value }
                ?.take(5)
                ?.forEach { result.add(it.key) }
        }
        return result
    }

    // ── Tokenizer ─────────────────────────────────────────────────────────────
    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9_\\-\$]"))
            .map { it.trim('.', ',', ';', ':') }
            .filter { t -> t.length > 2 && !stopWords.contains(t) && !t.matches(Regex("\\d+")) }
            .toSet()
    }

    // ── Fallback (kept for interface compatibility) ───────────────────────────
    suspend fun generateFallbackResponse(query: String): String {
        val resolved = resolveFollowUp(query)
        return generateResponse(resolved)
    }

    fun getStatusSummary(): String {
        val sources = knowledgeGraph.values.map { it.source }.toSet()
        val ruleCount = rules.size
        val vocabSize = coOccurrence.size
        return "Knowledge base active. I have learned ${knowledgeGraph.size} facts from ${sources.size} source(s) " +
               "(${sources.joinToString(", ") { it.substringAfterLast("/") }}). " +
               "I know $ruleCount constraint rules and have built a vocabulary of $vocabSize correlated terms."
    }

    fun clearAll() {
        knowledgeGraph.clear()
        coOccurrence.clear()
        rules.clear()
        conversationBuffer.clear()
    }
}
