package com.explorer.ai.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.explorer.ai.nlp.GrammarEngine
import com.explorer.ai.ui.DocumentRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.UUID
import kotlin.math.ln

data class KnowledgeNode(
    val id: String = UUID.randomUUID().toString(),
    val contentChunk: String, 
    val keywords: Set<String>,
    val source: String,
    val isDiagramData: Boolean = false,
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
        "please","also","just","very","more","some","than","into","they","been","being","have"
    )

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
        "interrupt"  to setOf("exception","handler","vector","isr","signal","irq","trap","mi","vi")
    )

    init {
        restoreNeurons()
    }

    private fun saveNeurons() {
        try {
            ObjectOutputStream(memoryFile.outputStream()).use { it.writeObject(knowledgeGraph) }
            ObjectOutputStream(vocabularyFile.outputStream()).use { it.writeObject(coOccurrence) }
            Log.d("NeuralEngine", "Neurons successfully committed to permanent memory.")
        } catch (e: Exception) {
            Log.e("NeuralEngine", "Failed to save neurons: ${e.message}")
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
            Log.d("NeuralEngine", "Restored ${knowledgeGraph.size} neurons from permanent memory.")
        } catch (e: Exception) {
            Log.e("NeuralEngine", "Memory load fault. Initializing clean brain: ${e.message}")
            knowledgeGraph = HashMap()
            coOccurrence = HashMap()
        }
    }

    suspend fun indexPdfDocument(uri: Uri, onProgress: ((Int, Int) -> Unit)? = null) =
        withContext(Dispatchers.IO) {
            try {
                Log.d("NeuralEngine", "Starting document ingestion...")
                val rawText = PdfProcessor.visuallyReadAndExtractUri(context, uri, onProgress)
                val sourceName = uri.lastPathSegment?.substringAfterLast("/") ?: "document"
                val count = learnFromText(rawText, sourceName)
                Log.d("NeuralEngine", "Ingestion complete: $count nodes from '$sourceName'")
            } catch (e: Exception) {
                Log.e("NeuralEngine", "Ingestion fault: ${e.message}")
            }
        }

    fun learnFromText(rawText: String, sourceLabel: String): Int {
        val blocks = rawText.split(Regex("\\n\\s*\\n")).filter { it.length > 40 }
        var created = 0

        for (block in blocks) {
            val isDiagram = grammar.isDiagramOrTable(block)
            
            if (!isDiagram && (grammar.isPureArtifact(block) || !grammar.isCoherentBlock(block))) continue
            
            val cleanBlock = grammar.normalizeText(block, preserveFormatting = isDiagram)
            val tokens = tokenize(cleanBlock)
            if (tokens.size < 4 && !isDiagram) continue

            for (w in tokens) {
                val map = coOccurrence.getOrPut(w) { HashMap() }
                for (other in tokens) {
                    if (w != other) map[other] = (map[other] ?: 0) + 1
                }
            }

            knowledgeGraph[UUID.randomUUID().toString()] = KnowledgeNode(
                contentChunk = cleanBlock, 
                keywords = tokens, 
                source = sourceLabel,
                isDiagramData = isDiagram
            )
            created++
        }

        saveNeurons()
        return created
    }

    override suspend fun search(query: String, topK: Int): List<String> =
        withContext(Dispatchers.IO) {
            retrieveTopNodes(query, topK).map { it.contentChunk }
                .ifEmpty { listOf("No relevant information found in the ingested documents.") }
        }

    suspend fun generateResponse(query: String): String = withContext(Dispatchers.Default) {
        if (knowledgeGraph.isEmpty()) {
            return@withContext "I have no knowledge yet. Please ingest a technical manual first."
        }

        val polishedQuery = grammar.normalizeText(query)
        val queryTokens = tokenize(polishedQuery)
        val expanded = if (queryTokens.isEmpty()) tokenize(resolveFollowUp(polishedQuery)) else expandTerms(queryTokens)
        
        if (expanded.isEmpty()) {
            return@withContext "Could you rephrase that? I need more specific keywords to search the manuals."
        }

        val topNodes = retrieveTopNodes(polishedQuery, 4)
        
        if (topNodes.isEmpty()) {
            return@withContext buildClarificationRequest(queryTokens)
        }

        topNodes.forEach { it.hitCount++ }
        saveNeurons()

        val sources = topNodes.map { it.source }.distinct()
        
        // Strict fuzzy deduplication to prevent sentence recycling
        val uniqueTextBlocks = mutableListOf<String>()
        topNodes.filter { !it.isDiagramData }.forEach { node ->
            val nodeFingerprint = node.contentChunk.lowercase().take(50)
            if (uniqueTextBlocks.none { it.lowercase().contains(nodeFingerprint) }) {
                uniqueTextBlocks.add(node.contentChunk)
            }
        }

        // Limit prose synthesis to top 3 unique chunks to preserve structural readability
        val synthesizedAnswer = grammar.synthesizeParagraph(uniqueTextBlocks.take(3))

        val header = if (sources.size > 1) {
            "Verified from multiple sources (${sources.joinToString(", ")}):\n\n"
        } else {
            "Verified from reference manual (${sources.firstOrNull() ?: "internal records"}):\n\n"
        }

        // Isolate diagram blocks entirely from prose paragraphs
        val diagramBlocks = topNodes.filter { it.isDiagramData }.map { it.contentChunk }.distinct()
        val diagramOutput = if (diagramBlocks.isNotEmpty()) {
            "\n\n[Extracted Structural Data / Memory Map]\n" + diagramBlocks.take(2).joinToString("\n\n---\n\n")
        } else ""

        val finalResponse = header + synthesizedAnswer + diagramOutput

        if (conversationBuffer.size >= 8) conversationBuffer.removeFirst()
        conversationBuffer.addLast(Pair(polishedQuery, finalResponse.take(280)))

        finalResponse
    }

    fun resolveFollowUp(query: String): String {
        val lower = query.lowercase().trim()
        val isVague = lower.length < 20 || lower.matches(Regex(".*\\b(it|this|that|they|those|its|their|same|above|below|previous)\\b.*"))
        return if (isVague && conversationBuffer.isNotEmpty()) {
            "${conversationBuffer.last().first} $query"
        } else query
    }

    private fun buildClarificationRequest(queryTokens: Set<String>): String {
        return "I couldn't find a definitive answer for \"${queryTokens.take(4).joinToString(" ")}\" in the ingested documents. " +
               "Could you specify the subsystem (e.g., CPU, Memory, RCP) you are asking about?"
    }

    private fun retrieveTopNodes(query: String, topK: Int): List<KnowledgeNode> {
        val tokens = tokenize(query)
        val expanded = expandTerms(tokens)
        return knowledgeGraph.values.mapNotNull { node ->
            val overlap = node.keywords.intersect(expanded).size
            if (overlap == 0) return@mapNotNull null
            
            val idf = ln(knowledgeGraph.size.toFloat() / (1f + overlap))
            var score = overlap * (1f + idf) * (1f + node.hitCount * 0.05f)
            
            // Apply heavy scoring boost for exact string matches
            if (node.contentChunk.contains(query, ignoreCase = true)) score *= 2.5f
            
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
            coOccurrence[term]?.entries?.sortedByDescending { it.value }?.take(2)?.forEach { result.add(it.key) }
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
        if (knowledgeGraph.isEmpty()) return "No documents ingested yet."
        val sources = knowledgeGraph.values.map { it.source }.toSet()
        return "Knowledge base active: ${knowledgeGraph.size} neurons mapped from ${sources.size} document(s)."
    }

    fun clearAll() {
        knowledgeGraph.clear()
        coOccurrence.clear()
        conversationBuffer.clear()
        memoryFile.delete()
        vocabularyFile.delete()
    }
}
