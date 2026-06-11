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

/**
 * Interface defining the bridge to the actual LLM (e.g., Gemini, MediaPipe, OpenAI).
 * Attach an implementation to enable grammatically flawless, context-aware generation.
 */
interface LlmInferenceEngine {
    suspend fun generateCohesiveReply(prompt: String): String
}

data class KnowledgeNode(
    val id: String = UUID.randomUUID().toString(),
    val contentChunk: String, // Stores the full paragraph to maintain coreference (e.g., "it", "this")
    val keywords: Set<String>,
    val source: String,
    var hitCount: Int = 0
)

class NeuralEngineService(private val context: Context) : DocumentRetriever {

    private val grammar = GrammarEngine()
    
    // Wire up your LLM client here to execute true RAG inference
    var llmEngine: LlmInferenceEngine? = null

    private val knowledgeGraph = mutableMapOf<String, KnowledgeNode>()
    private val coOccurrence = mutableMapOf<String, MutableMap<String, Int>>()
    private val conversationBuffer = ArrayDeque<Pair<String, String>>(8)

    private val stopWords = setOf(
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
        "interrupt"  to setOf("exception","handler","vector","isr","signal","irq","trap","mi","vi"),
        "chipset"    to setOf("cpu","rcp","rdp","rsp","vr4300","mips","processor","chip","silicon","aic"),
        "cartridge"  to setOf("rom","pak","slot","connector","bus","cart","mask","rom","flash")
    )

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
        val cleaned = grammar.scrubInlineArtifacts(rawText)
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        // Chunk by natural paragraphs to preserve local context and resolve pronouns
        val blocks = cleaned.split(Regex("\\n\\s*\\n")).filter { it.length > 40 }
        var created = 0

        for (block in blocks) {
            if (grammar.isPureArtifact(block) || !grammar.isCoherentBlock(block)) continue
            
            val cleanBlock = block.replace("\n", " ").trim()
            val tokens = tokenize(cleanBlock)
            if (tokens.size < 4) continue

            // Build co-occurrence thesaurus
            for (w in tokens) {
                val map = coOccurrence.getOrPut(w) { mutableMapOf() }
                for (other in tokens) {
                    if (w != other) map[other] = (map[other] ?: 0) + 1
                }
            }

            knowledgeGraph[UUID.randomUUID().toString()] =
                KnowledgeNode(contentChunk = cleanBlock, keywords = tokens, source = sourceLabel)
            created++
        }

        Log.d("NeuralEngine", "Learned $created context blocks. Total: ${knowledgeGraph.size}")
        return created
    }

    override suspend fun search(query: String, topK: Int): List<String> =
        withContext(Dispatchers.IO) {
            retrieveTopNodes(query, topK).map { it.contentChunk }
                .ifEmpty { listOf("No knowledge found for: $query") }
        }

    suspend fun generateResponse(query: String): String = withContext(Dispatchers.Default) {
        if (knowledgeGraph.isEmpty()) {
            return@withContext "I have no ingested knowledge yet. Tap 'Ingest Technical Manual (PDF)' to load a document, then ask me anything about it."
        }

        val queryTokens = tokenize(query)
        val expanded = if (queryTokens.isEmpty()) tokenize(resolveFollowUp(query)) else expandTerms(queryTokens)
        
        if (expanded.isEmpty() && llmEngine == null) {
            return@withContext "Could you rephrase that? I wasn't able to extract searchable terms from your query."
        }

        val topNodes = retrieveTopNodes(query, 4)
        
        if (topNodes.isEmpty() && llmEngine == null) {
            return@withContext buildClarificationRequest(queryTokens)
        }

        topNodes.forEach { it.hitCount++ }

        // TRUE LLM INFERENCE PIPELINE
        // The LLM synthesizes the extracted blocks into a perfectly cohesive response.
        if (llmEngine != null) {
            val contextKnowledge = topNodes.joinToString("\n\n") { "[Source: ${it.source}]\n${it.contentChunk}" }
            val prompt = """
                You are an expert technical assistant analyzing hardware manuals.
                Answer the user's question using ONLY the provided Context below.
                Synthesize the information into a single cohesive, grammatically correct paragraph.
                Do not just list disjointed facts. If the answer is not in the Context, state that you do not have enough information.
                
                Context:
                $contextKnowledge
                
                User Question: $query
            """.trimIndent()

            val llmReply = llmEngine!!.generateCohesiveReply(prompt)
            
            if (conversationBuffer.size >= 8) conversationBuffer.removeFirst()
            conversationBuffer.addLast(Pair(query, llmReply.take(280)))
            
            return@withContext llmReply
        }

        // Extractive Fallback (if the LLM delegate is not attached)
        val bestChunk = topNodes.firstOrNull()?.contentChunk ?: return@withContext buildClarificationRequest(queryTokens)
        val answer = grammar.formatGrammar(bestChunk)

        if (conversationBuffer.size >= 8) conversationBuffer.removeFirst()
        conversationBuffer.addLast(Pair(query, answer.take(280)))

        answer
    }

    fun resolveFollowUp(query: String): String {
        val lower = query.lowercase().trim()
        val isVague = lower.length < 20 || lower.matches(Regex(".*\\b(it|this|that|they|those|its|their|same|above|below|previous)\\b.*"))
        return if (isVague && conversationBuffer.isNotEmpty()) {
            "${conversationBuffer.last().first} $query"
        } else query
    }

    private fun buildClarificationRequest(queryTokens: Set<String>): String {
        return "I have ${knowledgeGraph.size} knowledge blocks but nothing specifically matched " +
               "\"${queryTokens.take(4).joinToString(" ")}\". " +
               "Could you elaborate or ask about hardware specs, memory addresses, or system architecture?"
    }

    private fun retrieveTopNodes(query: String, topK: Int): List<KnowledgeNode> {
        val tokens = tokenize(query)
        val expanded = expandTerms(tokens)
        return knowledgeGraph.values.mapNotNull { node ->
            val overlap = node.keywords.intersect(expanded).size
            if (overlap == 0) return@mapNotNull null
            val idf = ln(knowledgeGraph.size.toFloat() / (1f + overlap))
            val score = overlap * (1f + idf) * (1f + node.hitCount * 0.05f)
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
        return "Knowledge base active: ${knowledgeGraph.size} context blocks from ${sources.size} source(s)."
    }

    fun clearAll() {
        knowledgeGraph.clear()
        coOccurrence.clear()
        conversationBuffer.clear()
    }
}
