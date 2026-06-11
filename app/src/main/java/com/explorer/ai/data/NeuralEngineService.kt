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

// ─── Data models ─────────────────────────────────────────────────────────────

data class KnowledgeNode(
    val id: String = UUID.randomUUID().toString(),
    val sentence: String,
    val keywords: Set<String>,
    val source: String,
    var hitCount: Int = 0       // incremented on each query match — live reinforcement
)

data class KnowledgeRule(
    val source: String,
    val sentence: String,
    val keywords: Set<String>
)

// ─── Service ─────────────────────────────────────────────────────────────────

class NeuralEngineService(private val context: Context) : DocumentRetriever {

    private val grammar = GrammarEngine()

    // Keyword-indexed knowledge graph
    private val knowledgeGraph = mutableMapOf<String, KnowledgeNode>()

    // Live co-occurrence vocabulary — built as documents are ingested
    private val coOccurrence = mutableMapOf<String, MutableMap<String, Int>>()

    // Constraint/imperative rules extracted from warnings, notes, must/never sentences
    private val rules = mutableListOf<KnowledgeRule>()

    // Rolling conversation buffer: last 8 (user → ai) pairs for follow-up resolution
    private val conversationBuffer = ArrayDeque<Pair<String, String>>(8)

    private val stopWords = setOf(
        "the","and","a","of","to","in","is","that","it","for","on","with","as","this","by",
        "an","how","what","where","why","can","you","do","does","are","be","or","at","from",
        "but","not","was","were","has","have","had","will","would","could","should","may",
        "might","its","their","there","then","when","which","who","tell","me","about","give",
        "please","also","just","very","more","some","than","into","they","been","being","have"
    )

    // Seeded synonym map; co-occurrence expands this at runtime
    private val synonymSeeds = mapOf(
        "hardware"   to setOf("chip","processor","cpu","memory","ram","register","bus","board","circuit","component"),
        "address"    to setOf("pointer","location","offset","base","mapped","mapping","segment","kseg","kuseg"),
        "cpu"        to setOf("processor","mips","r4300","vr4300","core","pipeline","risc","vr4300i"),
        "memory"     to setOf("ram","rdram","dram","cache","buffer","store","storage","heap","stack","sram"),
        "n64"        to setOf("nintendo64","ultra64","rcp","rsp","rdp","reality","console","cartridge","nintendo"),
        "graphics"   to setOf("rcp","rdp","rsp","display","render","pixel","texture","polygon","vi","framebuffer"),
        "register"   to setOf("t0","v0","a0","sp","ra","gp","fp","reg","regfile","gpr","cop0","cop1"),
        "install"    to setOf("installation","setup","configure","directory","autoexec","path","environment"),
        "error"      to setOf("fault","warning","exception","crash","fail","invalid","illegal","undefined","overflow"),
        "debug"      to setOf("debugger","breakpoint","trace","inspect","halt","reset","target","sn","jtag","gdb"),
        "audio"      to setOf("sound","music","sfx","dsp","sample","frequency","pcm","adpcm","midi","ai"),
        "dma"        to setOf("transfer","direct","memory","access","channel","burst","sync","async","pi","si"),
        "interrupt"  to setOf("exception","handler","vector","isr","signal","irq","trap","mi","vi"),
        "chipset"    to setOf("cpu","rcp","rdp","rsp","vr4300","mips","processor","chip","silicon","aic"),
        "cartridge"  to setOf("rom","pak","slot","connector","bus","cart","mask","rom","flash")
    )

    // ── PDF ingestion ─────────────────────────────────────────────────────────

    suspend fun indexPdfDocument(uri: Uri, onProgress: ((Int, Int) -> Unit)? = null) =
        withContext(Dispatchers.IO) {
            try {
                Log.d("NeuralEngine", "Starting document ingestion...")
                val rawText = PdfProcessor.visuallyReadAndExtractUri(context, uri, onProgress)
                val sourceName = uri.lastPathSegment
                    ?.substringAfterLast("/")
                    ?.substringAfterLast("%2F")
                    ?: "document"
                val count = learnFromText(rawText, sourceName)
                Log.d("NeuralEngine", "Ingestion complete: $count nodes from '$sourceName'")
            } catch (e: Exception) {
                Log.e("NeuralEngine", "Ingestion fault: ${e.message}")
            }
        }

    // ── Core learning pipeline ────────────────────────────────────────────────

    fun learnFromText(rawText: String, sourceLabel: String): Int {
        val cleaned = rawText
            .replace(Regex("---\\s*(PAGE_START|PAGE_END):\\s*\\d+\\s*---"), " ")
            .replace(Regex("\\[PARAGRAPH_START[^\\]]*\\]|\\[PARAGRAPH_END\\]"), " ")
            .replace(Regex("\\[COLUMN_START\\]|\\[COLUMN_END\\]"), " ")
            .replace(Regex("\\[VISUAL_ANCHOR:[^\\]]+\\]"), " ")
            .replace(Regex("(\\b\\d{1,4}(?:,\\s*\\d{1,4}){2,}\\b)"), " ") // Strip index page clusters early
            .replace(Regex("\\.{4,}"), " ")
            .replace(Regex("-\\s*\\n\\s+([a-z])"), "$1")
            .replace(Regex("\\s*[•·▸►▶‣⁃∙◦]\\s+"), "\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        val sentences = grammar.splitIntoSentences(cleaned)
        var created = 0

        for (sentence in sentences) {
            if (!grammar.isCoherentEnglish(sentence)) continue
            val tokens = tokenize(sentence)
            if (tokens.size < 3) continue

            // Build co-occurrence thesaurus live
            for (w in tokens) {
                val map = coOccurrence.getOrPut(w) { mutableMapOf() }
                for (other in tokens) {
                    if (w != other) map[other] = (map[other] ?: 0) + 1
                }
            }

            // Extract imperative/constraint rules
            if (sentence.lowercase().contains(
                    Regex("\\b(must|should|never|always|avoid|warning|note|important|critical|error|fault|illegal|undefined|require)\\b")
                )) {
                rules.add(KnowledgeRule(sourceLabel, sentence, tokens))
            }

            knowledgeGraph[UUID.randomUUID().toString()] =
                KnowledgeNode(sentence = sentence, keywords = tokens, source = sourceLabel)
            created++
        }

        Log.d("NeuralEngine", "Learned $created sentences. Total: ${knowledgeGraph.size}")
        return created
    }

    // ── DocumentRetriever interface ───────────────────────────────────────────

    override suspend fun search(query: String, topK: Int): List<String> =
        withContext(Dispatchers.IO) {
            retrieveTopNodes(query, topK).map { it.sentence }
                .ifEmpty { listOf("No knowledge found for: $query") }
        }

    // ── Response generation ───────────────────────────────────────────────────

    suspend fun generateResponse(query: String): String = withContext(Dispatchers.Default) {
        if (knowledgeGraph.isEmpty()) {
            return@withContext "I have no ingested knowledge yet. Tap 'Ingest Technical Manual (PDF)' to load a document, then ask me anything about it."
        }

        // Status query shortcut
        if (query.lowercase().matches(Regex(".*(status|how many|nodes|vocabulary|what.*know|what.*learned|brain).*"))) {
            return@withContext getStatusSummary()
        }

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) {
            return@withContext "Could you rephrase that? I wasn't able to extract searchable terms from your query."
        }

        val expanded = expandTerms(queryTokens)

        // Score every node with TF-IDF-style weighting + hit count boost
        data class Scored(val node: KnowledgeNode, val score: Float)
        val candidates = knowledgeGraph.values.mapNotNull { node ->
            val overlap = node.keywords.intersect(expanded).size
            if (overlap == 0) return@mapNotNull null
            val idf = ln(knowledgeGraph.size.toFloat() / (1f + overlap))
            val score = overlap * (1f + idf) * (1f + node.hitCount * 0.05f)
            Scored(node, score)
        }

        if (candidates.isEmpty()) {
            return@withContext buildClarificationRequest(query, queryTokens)
        }

        val topNodes = candidates.sortedByDescending { it.score }.take(6).map { it.node }
        // Live reinforcement — frequently-matched nodes surface faster in future queries
        topNodes.forEach { it.hitCount++ }

        // Build deduplicated cohesive answer from top nodes
        val seen = mutableSetOf<String>()
        val parts = mutableListOf<String>()

        for (node in topNodes) {
            if (parts.size >= 5) break
            val clean = cleanForDisplay(node.sentence)
            val fingerprint = clean.take(60).lowercase()
            if (fingerprint in seen || clean.length < 20) continue
            seen.add(fingerprint)
            // Enforce sentence termination and casing
            parts.add(grammar.formatGrammar(clean))
        }

        if (parts.isEmpty()) return@withContext buildClarificationRequest(query, queryTokens)

        // Form a structured paragraph rather than disjointed concatenations
        val answer = parts.joinToString(" ")

        // Store in conversation buffer for follow-up resolution
        if (conversationBuffer.size >= 8) conversationBuffer.removeFirst()
        conversationBuffer.addLast(Pair(query, answer.take(280)))

        answer
    }

    // ── Follow-up resolution ──────────────────────────────────────────────────

    fun resolveFollowUp(query: String): String {
        val lower = query.lowercase().trim()
        val isVague = lower.length < 20 ||
            lower.matches(Regex(".*\\b(it|this|that|they|those|its|their|same|above|below|previous)\\b.*"))
        return if (isVague && conversationBuffer.isNotEmpty()) {
            val last = conversationBuffer.last()
            "${last.first} — specifically: $query"
        } else query
    }

    // ── Clarification builder ─────────────────────────────────────────────────

    private fun buildClarificationRequest(query: String, queryTokens: Set<String>): String {
        val partialSources = knowledgeGraph.values
            .filter { node -> queryTokens.any { t -> node.keywords.any { k -> k.contains(t, true) || t.contains(k, true) } } }
            .map { it.source }.toSet().take(3)

        val topics = listOf(
            "N64 hardware specifications (CPU, memory, registers)",
            "memory addresses and RDRAM layout",
            "DMA transfers and alignment requirements",
            "audio, graphics, and RCP subsystems",
            "installation, debugging, and development tools"
        )

        return if (partialSources.isNotEmpty()) {
            "I found related material in ${partialSources.joinToString(", ") { "\"$it\"" }}. " +
            "Could you be more specific? I can answer questions about: " +
            topics.take(3).joinToString("; ") + "."
        } else {
            "I have ${knowledgeGraph.size} facts in my knowledge base but nothing matched " +
            "\"${queryTokens.take(4).joinToString(" ")}\". " +
            "Try asking about: " + topics.joinToString("; ") + "."
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun retrieveTopNodes(query: String, topK: Int): List<KnowledgeNode> {
        val tokens = tokenize(query)
        val expanded = expandTerms(tokens)
        return knowledgeGraph.values
            .filter { it.keywords.intersect(expanded).isNotEmpty() }
            .sortedByDescending { it.keywords.intersect(expanded).size + it.hitCount * 0.05f }
            .take(topK)
    }

    private fun expandTerms(base: Set<String>): Set<String> {
        val result = mutableSetOf<String>()
        result.addAll(base)
        for (term in base) {
            synonymSeeds.forEach { (key, synonyms) ->
                if (key == term || synonyms.contains(term)) {
                    result.add(key); result.addAll(synonyms)
                }
            }
            coOccurrence[term]?.entries
                ?.sortedByDescending { it.value }?.take(5)
                ?.forEach { result.add(it.key) }
        }
        return result
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-zA-Z0-9_\\-\$]"))
            .map { it.trim('.', ',', ';', ':') }
            .filter { t -> t.length > 2 && !stopWords.contains(t) && !t.matches(Regex("\\d+")) }
            .toSet()

    private fun cleanForDisplay(sentence: String): String = sentence
        .replace(Regex("---\\s*(PAGE_START|PAGE_END):\\s*\\d+\\s*---"), "")
        .replace(Regex("\\[PARAGRAPH_START[^\\]]*\\]|\\[PARAGRAPH_END\\]"), "")
        .replace(Regex("©\\s*SN\\s*Systems\\s*Ltd[^.]*\\.?"), "")
        .replace(Regex("^\\s*(Page\\s+\\d+-\\d+\\s+)"), "")
        .replace(Regex("^\\s*(CHAPTER|SECTION|APPENDIX)\\s+[\\dA-Z]+\\s+"), "")
        .replace(Regex("\\s*NINTENDO\\s+64\\s+PROGRAMMING\\s+MANUAL\\s+(DRAFT\\s+)?\\d*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b\\d{1,4}(?:,\\s*\\d{1,4})+\\b"), "") // Strip any inline page number references
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()

    // ── Fallback for interface compatibility ──────────────────────────────────

    suspend fun generateFallbackResponse(query: String): String =
        generateResponse(resolveFollowUp(query))

    // ── Status ────────────────────────────────────────────────────────────────

    fun getStatusSummary(): String {
        if (knowledgeGraph.isEmpty()) return "No documents ingested yet. Tap 'Ingest Technical Manual (PDF)' to begin."
        val sources = knowledgeGraph.values.map { it.source }.toSet()
        return "Knowledge base active: ${knowledgeGraph.size} facts from ${sources.size} source(s) " +
               "(${sources.joinToString(", ") { it.substringAfterLast("/") }}). " +
               "${rules.size} constraint rules. ${coOccurrence.size} vocabulary terms learned."
    }

    fun clearAll() {
        knowledgeGraph.clear()
        coOccurrence.clear()
        rules.clear()
        conversationBuffer.clear()
    }
}
