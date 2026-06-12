package com.explorer.ai.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.explorer.ai.nlp.GrammarEngine
import com.explorer.ai.ui.DocumentRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.UUID
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

data class KnowledgeNode(
    val id: String = UUID.randomUUID().toString(),
    val contentChunk: String,
    val keywords: Set<String>,
    val source: String,
    val isDiagramData: Boolean = false,
    val isCodeData: Boolean = false,
    val codeLanguage: String = "text",
    var hitCount: Int = 0
) : Serializable

class NeuralEngineService(private val context: Context) : DocumentRetriever {

    private val grammar = GrammarEngine()

    private val memoryFile = File(context.filesDir, "neural_neurons.dat")
    private val vocabularyFile = File(context.filesDir, "neural_vocabulary.dat")

    private var knowledgeGraph = HashMap<String, KnowledgeNode>()
    private var coOccurrence = HashMap<String, HashMap<String, Int>>()
    private val conversationBuffer = ArrayDeque<Pair<String, String>>(8)

    private val stopWords = hashSetOf(
        "the","and","a","of","to","in","is","that","it","for","on","with","as","this","by",
        "an","how","what","where","why","can","you","do","does","are","be","or","at","from",
        "but","not","was","were","has","have","had","will","would","could","should","may",
        "might","its","their","there","then","when","which","who","tell","me","about","give",
        "please","also","just","very","more","some","than","into","they","been","being"
    )

    private val synonymSeeds = mapOf(
        "code"       to setOf("script","function","class","method","implementation","snippet","algorithm","syntax","c","cpp","macro"),
        "hardware"   to setOf("chip","processor","cpu","memory","ram","register","bus","board","circuit","component","physical","silicon"),
        "address"    to setOf("pointer","location","offset","base","mapped","mapping","segment","kseg","kuseg","virtual","physical"),
        "cpu"        to setOf("processor","mips","r4300","vr4300","core","pipeline","risc","vr4300i","central","unit"),
        "memory"     to setOf("ram","rdram","dram","cache","buffer","store","storage","heap","stack","sram","byte","word"),
        "n64"        to setOf("nintendo64","ultra64","rcp","rsp","rdp","reality","console","cartridge","nintendo","n64"),
        "graphics"   to setOf("rcp","rdp","rsp","display","render","pixel","texture","polygon","vi","framebuffer","gpu","gpu"),
        "chipset"    to setOf("cpu","rcp","rdp","rsp","vr4300","mips","processor","chip","silicon","aic","system"),
        "register"   to setOf("gpr","cop0","cop1","t0","v0","a0","sp","ra","gp","fp","reg","regfile"),
        "interrupt"  to setOf("exception","handler","vector","isr","signal","irq","trap","mi","vi","fault"),
        "dma"        to setOf("transfer","direct","access","channel","burst","sync","async","pi","si","copy"),
        "audio"      to setOf("sound","music","sfx","dsp","sample","frequency","pcm","adpcm","midi","ai"),
        "debug"      to setOf("debugger","breakpoint","trace","inspect","halt","reset","target","sn","gdb","monitor"),
        "install"    to setOf("installation","setup","configure","directory","autoexec","path","environment","system")
    )

    init { restoreNeurons() }

    private fun saveNeuronsAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ObjectOutputStream(memoryFile.outputStream()).use { it.writeObject(knowledgeGraph) }
                ObjectOutputStream(vocabularyFile.outputStream()).use { it.writeObject(coOccurrence) }
            } catch (e: Exception) {
                Log.e("NeuralEngine", "Save fault: ${e.message}")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun restoreNeurons() {
        try {
            if (memoryFile.exists()) {
                ObjectInputStream(memoryFile.inputStream()).use {
                    knowledgeGraph = it.readObject() as HashMap<String, KnowledgeNode>
                }
            }
            if (vocabularyFile.exists()) {
                ObjectInputStream(vocabularyFile.inputStream()).use {
                    coOccurrence = it.readObject() as HashMap<String, HashMap<String, Int>>
                }
            }
            Log.d("NeuralEngine", "Restored ${knowledgeGraph.size} neurons.")
        } catch (e: Exception) {
            Log.e("NeuralEngine", "Memory restore fault — fresh start: ${e.message}")
            knowledgeGraph = HashMap()
            coOccurrence = HashMap()
        }
    }

    suspend fun indexPdfDocument(uri: Uri, onProgress: ((Int, Int) -> Unit)? = null) =
        withContext(Dispatchers.IO) {
            try {
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

    fun learnFromText(rawText: String, sourceLabel: String): Int {
        val blocks = rawText.split(Regex("\\n\\s*\\n")).filter { it.length > 20 }
        var created = 0

        for (block in blocks) {
            // Reject confirmed index/TOC pages — uses stricter pattern now
            if (grammar.isIndexOrTOC(block)) continue

            val isCode = grammar.isCodeSequence(block)
            val isDiagram = !isCode && grammar.isDiagramOrTable(block)

            if (!isCode && !isDiagram && (grammar.isPureArtifact(block) || !grammar.isCoherentBlock(block))) continue

            val cleanBlock = grammar.normalizeText(block, preserveFormatting = isDiagram || isCode, isCode = isCode)
            if (cleanBlock.length < 15) continue

            val tokenList = tokenize(cleanBlock).toList()
            if (tokenList.size < 4 && !isDiagram && !isCode) continue

            // Build sliding-window co-occurrence
            val windowSize = 4
            for (i in tokenList.indices) {
                val w = tokenList[i]
                val map = coOccurrence.getOrPut(w) { HashMap() }
                for (j in max(0, i - windowSize)..min(tokenList.size - 1, i + windowSize)) {
                    if (i != j) {
                        val other = tokenList[j]
                        map[other] = (map[other] ?: 0) + 1
                    }
                }
            }

            knowledgeGraph[UUID.randomUUID().toString()] = KnowledgeNode(
                contentChunk = cleanBlock,
                keywords = tokenList.toSet(),
                source = sourceLabel,
                isDiagramData = isDiagram,
                isCodeData = isCode,
                codeLanguage = if (isCode) grammar.detectCodeLanguage(cleanBlock) else "text"
            )
            created++
        }

        saveNeuronsAsync()
        return created
    }

    override suspend fun search(query: String, topK: Int): List<String> =
        withContext(Dispatchers.IO) {
            retrieveTopNodes(query, topK).map { it.contentChunk }
                .ifEmpty { listOf("No relevant information found in the ingested documents.") }
        }

    /**
     * Generates a coherent natural-language response.
     *
     * Key fix: instead of passing raw contentChunk strings to synthesizeParagraph()
     * (which dumped entire blocks verbatim), we now call buildCoherentAnswer() with
     * the query terms so each chunk contributes only its BEST SENTENCE — selected for
     * relevance, verb presence, and information density.
     */
    suspend fun generateResponse(query: String): String = withContext(Dispatchers.Default) {
        if (knowledgeGraph.isEmpty()) {
            return@withContext "I have no knowledge yet. Please ingest a source repository or technical manual first."
        }

        val polishedQuery = grammar.normalizeText(query, preserveFormatting = false, isCode = false)
        val queryTokens = tokenize(polishedQuery)
        val expanded = if (queryTokens.isEmpty()) tokenize(resolveFollowUp(polishedQuery)) else expandTerms(queryTokens)

        if (expanded.isEmpty()) {
            return@withContext "Could you rephrase that? I need more specific terms to search the knowledge base."
        }

        val isCodeRequest = queryTokens.any { synonymSeeds["code"]?.contains(it) == true || it == "code" }
        val topNodes = retrieveTopNodes(polishedQuery, if (isCodeRequest) 6 else 5, isCodeRequest)

        if (topNodes.isEmpty()) {
            return@withContext buildClarificationRequest(query, queryTokens)
        }

        topNodes.forEach { it.hitCount++ }
        saveNeuronsAsync()

        val sources = topNodes.map { it.source }.distinct()

        // ── Prose answer ──────────────────────────────────────────────────────
        // Pass query terms so extractBestSentence() picks the most relevant
        // sentence from each chunk — NOT the whole raw block
        val textChunks = topNodes
            .filter { !it.isDiagramData && !it.isCodeData }
            .distinctBy { it.contentChunk.lowercase().take(50) }
            .take(4)
            .map { it.contentChunk }

        val synthesizedAnswer = grammar.buildCoherentAnswer(textChunks, expanded)

        // ── Code blocks ───────────────────────────────────────────────────────
        val codeBlocks = topNodes.filter { it.isCodeData }.distinctBy { it.contentChunk.take(50) }
        val codeOutput = if (codeBlocks.isNotEmpty()) {
            "\n\n**Relevant Code:**\n" + codeBlocks.joinToString("\n\n") { node ->
                "```${node.codeLanguage}\n${node.contentChunk}\n```"
            }
        } else ""

        // ── Diagram/table blocks ──────────────────────────────────────────────
        val diagramBlocks = topNodes.filter { it.isDiagramData }.map { it.contentChunk }.distinct()
        val diagramOutput = if (diagramBlocks.isNotEmpty()) {
            "\n\n**Structural Data:**\n" + diagramBlocks.take(2).joinToString("\n\n")
        } else ""

        // ── Source attribution ────────────────────────────────────────────────
        val sourceNote = when {
            sources.size == 1 -> " (Source: ${sources.first()})"
            sources.size > 1  -> " (Sources: ${sources.joinToString(", ")})"
            else -> ""
        }

        val finalAnswer = when {
            synthesizedAnswer.isNotBlank() -> synthesizedAnswer + codeOutput + diagramOutput + sourceNote
            codeOutput.isNotBlank() -> "Here is the relevant code from the ingested documents:$codeOutput$sourceNote"
            diagramOutput.isNotBlank() -> "Here is the relevant structural data:$diagramOutput$sourceNote"
            else -> buildClarificationRequest(query, queryTokens)
        }

        if (conversationBuffer.size >= 8) conversationBuffer.removeFirst()
        conversationBuffer.addLast(Pair(polishedQuery, finalAnswer.take(300)))

        finalAnswer
    }

    fun resolveFollowUp(query: String): String {
        val lower = query.lowercase().trim()
        val isVague = lower.length < 20 ||
            lower.matches(Regex(".*\\b(it|this|that|they|those|its|their|same|above|below|previous)\\b.*"))
        return if (isVague && conversationBuffer.isNotEmpty()) {
            "${conversationBuffer.last().first} — more specifically: $query"
        } else query
    }

    private fun buildClarificationRequest(query: String, queryTokens: Set<String>): String {
        val partialSources = knowledgeGraph.values
            .filter { node -> queryTokens.any { t -> node.keywords.any { k -> k.contains(t, true) || t.contains(k, true) } } }
            .map { it.source }.toSet().take(3)

        val topics = listOf(
            "N64 hardware specifications (CPU, memory, chipset)",
            "memory addressing and RDRAM layout",
            "DMA transfers and system bus",
            "audio, graphics, RCP subsystems",
            "installation, debugging, and development tools"
        )

        return if (partialSources.isNotEmpty()) {
            "I found related material in ${partialSources.joinToString(", ") { "\"$it\"" }}. " +
            "Could you be more specific? I can answer questions about: " +
            topics.take(3).joinToString("; ") + "."
        } else {
            "I have ${knowledgeGraph.size} facts in my knowledge base but could not find a match for " +
            "\"${queryTokens.take(4).joinToString(" ")}\". " +
            "Try asking about: " + topics.joinToString("; ") + "."
        }
    }

    private fun retrieveTopNodes(query: String, topK: Int, prioritizeCode: Boolean = false): List<KnowledgeNode> {
        val tokens = tokenize(query)
        val expanded = expandTerms(tokens)
        val queryCorePhrase = tokens.joinToString(" ")

        return knowledgeGraph.values.mapNotNull { node ->
            val overlap = node.keywords.intersect(expanded).size
            if (overlap == 0) return@mapNotNull null
            val idf = ln(knowledgeGraph.size.toFloat() / (1f + overlap))
            var score = overlap * (1f + idf) * (1f + node.hitCount * 0.05f)
            if (queryCorePhrase.isNotEmpty() && node.contentChunk.contains(queryCorePhrase, ignoreCase = true)) score *= 2.5f
            if (prioritizeCode && node.isCodeData) score *= 3.0f
            Pair(node, score)
        }.sortedByDescending { it.second }.take(topK).map { it.first }
    }

    private fun expandTerms(base: Set<String>): Set<String> {
        val result = mutableSetOf<String>()
        result.addAll(base)
        for (term in base) {
            synonymSeeds.forEach { (key, synonyms) ->
                if (key == term || synonyms.contains(term)) { result.add(key); result.addAll(synonyms) }
            }
            coOccurrence[term]?.entries?.sortedByDescending { it.value }?.take(3)?.forEach { result.add(it.key) }
        }
        return result
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-zA-Z0-9_\\-\$]"))
            .map { it.trim('.', ',', ';', ':') }
            .filter { t -> t.length > 2 && !stopWords.contains(t) && !t.matches(Regex("\\d+")) }
            .toSet()

    suspend fun generateFallbackResponse(query: String): String = generateResponse(resolveFollowUp(query))

    fun getStatusSummary(): String {
        if (knowledgeGraph.isEmpty()) return "No documents ingested yet. Tap 'Ingest Technical Manual (PDF)' to begin."
        val sources = knowledgeGraph.values.map { it.source }.toSet()
        val codeNodes = knowledgeGraph.values.count { it.isCodeData }
        val diagNodes = knowledgeGraph.values.count { it.isDiagramData }
        return "Knowledge base active: ${knowledgeGraph.size} neurons mapped " +
               "($codeNodes code sequences, $diagNodes data structures) " +
               "from ${sources.size} document(s) " +
               "(${sources.joinToString(", ") { it.substringAfterLast("/") }})."
    }

    fun clearAll() {
        knowledgeGraph.clear()
        coOccurrence.clear()
        conversationBuffer.clear()
        memoryFile.delete()
        vocabularyFile.delete()
    }
}
