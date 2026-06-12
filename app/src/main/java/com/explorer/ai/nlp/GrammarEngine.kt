package com.explorer.ai.nlp

/**
 * GrammarEngine v4 — English + technical NLP processor, synthesizer, and code extractor.
 *
 * Key fixes in this version:
 * - isIndexOrTOC() now requires BOTH numeric density AND the specific "word, number, number"
 *   pattern to reject index pages. Previous version rejected valid hardware facts that
 *   ended with numbers.
 * - synthesizeParagraph() now selects the single best sentence per chunk rather than
 *   concatenating entire raw blocks — this prevents index dumps from appearing verbatim.
 * - New: extractBestSentence() picks the most information-dense sentence from a block.
 * - New: buildCoherentAnswer() assembles multi-source facts into one fluent paragraph.
 */
class GrammarEngine {

    private val commonVerbs = setOf(
        "is","are","was","were","be","been","has","have","had","do","does","did",
        "run","execute","load","store","allocate","free","return","initialize","configure",
        "set","get","map","access","read","write","send","receive","connect","start","stop",
        "enable","disable","check","require","use","need","allow","support","contain",
        "provide","define","requires","contains","creates","manages","handles","processes",
        "translates","represents","provides","supports","allows","defines","means",
        "generates","outputs","inputs","transfers","copies","moves","converts","computes",
        "controls","performs","operates","accesses","addresses","maps","refers","indicates",
        "specifies","describes","includes","consist","consists","communicate","install",
        "installs","compile","compiles","link","links","determine","determines","remove",
        "removes","cause","causes","attempt","attempts","display","displays","reset","resets",
        "serve","serves","locate","locates","represent","represents","connects","boots",
        "initializes","halts","downloads","uploads","triggers","renders","consists","contains"
    )

    val ocrCorrections = mapOf(
        Regex("\\bsmail\\b", RegexOption.IGNORE_CASE) to "small",
        Regex("\\bteh\\b", RegexOption.IGNORE_CASE) to "the",
        Regex("\\bfl le\\b", RegexOption.IGNORE_CASE) to "file",
        Regex("\\bfi le\\b", RegexOption.IGNORE_CASE) to "file",
        Regex("\\bof ten\\b", RegexOption.IGNORE_CASE) to "often",
        Regex("\\bpro gram\\b", RegexOption.IGNORE_CASE) to "program",
        Regex("\\binforma tion\\b", RegexOption.IGNORE_CASE) to "information",
        Regex("\\breceiveing\\b", RegexOption.IGNORE_CASE) to "receiving",
        Regex("\\baddres\\b", RegexOption.IGNORE_CASE) to "address",
        Regex("\\badress\\b", RegexOption.IGNORE_CASE) to "address",
        Regex("\\bprocesor\\b", RegexOption.IGNORE_CASE) to "processor",
        Regex("\\bproccesor\\b", RegexOption.IGNORE_CASE) to "processor",
        Regex("\\bmemmory\\b", RegexOption.IGNORE_CASE) to "memory",
        Regex("\\bco.de\\b", RegexOption.IGNORE_CASE) to "code",
        Regex("\\bmodul e\\b", RegexOption.IGNORE_CASE) to "module",
        Regex("\\bsoc cupying\\b", RegexOption.IGNORE_CASE) to "occupying",
        Regex("\\bmus otn\\b", RegexOption.IGNORE_CASE) to "must only",
        Regex("\\bmusotn\\b", RegexOption.IGNORE_CASE) to "must only",
        Regex("\\b(?i)rdram\\b") to "RDRAM",
        Regex("\\b(?i)rcp\\b") to "RCP",
        Regex("\\b(?i)rsp\\b") to "RSP",
        Regex("\\b(?i)rdp\\b") to "RDP",
        Regex("\\b(?i)mips\\b") to "MIPS",
        Regex("\\b(?i)cpu\\b") to "CPU",
        Regex("\\b(?i)dma\\b") to "DMA",
        Regex("\\b(?i)tlb\\b") to "TLB",
        Regex("\\b(?i)vr4300\\b") to "VR4300",
        Regex("\\b(?i)r4300i?\\b") to "R4300i",
        Regex("\\b(?i)nintendo\\s*64\\b") to "Nintendo 64",
        Regex("\\b(it|this|that) are\\b", RegexOption.IGNORE_CASE) to "$1 is",
        Regex("\\ba\\s+([aeiou])", RegexOption.IGNORE_CASE) to "an $1",
        Regex("\\ban\\s+([bcdfghjklmnpqrstvwxyz])", RegexOption.IGNORE_CASE) to "a $1"
    )

    private val strictArtifactPatterns = listOf(
        Regex("^\\s*\\d{1,3}\\s*$"),
        Regex("^\\s*[©®]"),
        Regex("^\\s*Page\\s+\\d+", RegexOption.IGNORE_CASE),
        Regex("^\\.+\\d"),
        Regex("^\\d+\\.+$")
    )

    fun isPureArtifact(line: String): Boolean {
        val t = line.trim()
        if (t.length < 5) return true
        return strictArtifactPatterns.any { it.containsMatchIn(t) }
    }

    /**
     * Detects book index pages.
     *
     * FIXED: Previous implementation used a number-ending regex that matched
     * any sentence ending with a measurement or version number, falsely rejecting
     * valid hardware documentation.
     *
     * Correct index pattern: "Word 12, 47, 93, 115" — a word/phrase followed
     * by 3+ comma-separated page numbers. This is structurally distinct from
     * prose that happens to mention numbers.
     */
    fun isIndexOrTOC(block: String): Boolean {
        val lines = block.trim().split("\n")

        // True index line: "SomeTerm 12, 47, 93" — term then 3+ page numbers
        val strictIndexPattern = Regex("^[A-Za-z][A-Za-z0-9 ,._/-]{1,40}\\s+(\\d{1,3}[,\\s]+){2,}\\d{1,3}\\s*$")

        // TOC line: "Chapter Name ........... 12"
        val tocPattern = Regex("\\.{4,}")

        val indexLineCount = lines.count { line ->
            val t = line.trim()
            strictIndexPattern.matches(t) || tocPattern.containsMatchIn(t)
        }

        // Only reject if >50% of lines are strict index/TOC pattern
        return indexLineCount > lines.size * 0.5 && lines.size >= 3
    }

    fun isCodeSequence(block: String): Boolean {
        val lines = block.split("\n")
        val syntaxIndicators = lines.count {
            it.trim().endsWith(";") ||
            it.trim().endsWith("{") ||
            it.trim().startsWith("#include") ||
            it.trim().startsWith("import ") ||
            it.trim().startsWith("//") ||
            (it.contains("(") && it.contains(")") && it.trim().endsWith("{"))
        }
        val codeSymbolDensity = block.count { it == '{' || it == '}' || it == ';' || it == '=' }
        return syntaxIndicators >= 2 || (codeSymbolDensity > 8 && lines.size > 2)
    }

    fun detectCodeLanguage(block: String): String = when {
        block.contains("#include") || block.contains("std::") || block.contains("->") -> "cpp"
        block.contains("fun ") || block.contains("val ") -> "kotlin"
        block.contains("def ") || (block.contains("import ") && block.contains(":")) -> "python"
        block.contains("public class") || block.contains("System.out") -> "java"
        block.contains("function") || block.contains("let ") || block.contains("=>") -> "javascript"
        else -> "c"
    }

    fun isDiagramOrTable(block: String): Boolean {
        if (isCodeSequence(block)) return false
        val lines = block.split("\n")
        if (lines.size < 2) return false
        val hexCount = Regex("0x[0-9A-Fa-f]+").findAll(block).count()
        val pipeCount = block.count { it == '|' || it == '+' }
        val tabularSpacing = Regex(" {4,}").findAll(block).count()
        val isDenseProse = block.length > 200 && lines.size > 4 && tabularSpacing < 3
        return !isDenseProse && (hexCount >= 3 || pipeCount >= 6 || tabularSpacing >= 5)
    }

    fun normalizeText(text: String, preserveFormatting: Boolean = false, isCode: Boolean = false): String {
        var normalized = text
            .replace(Regex("\\[[A-Z_]+_START[^\\]]*\\]|\\[[A-Z_]+_END\\]"), " ")
            .replace(Regex("---\\s*PAGE_START:\\s*\\d+\\s*---"), " ")
            .replace(Regex("---\\s*PAGE_END:\\s*\\d+\\s*---"), " ")
            .replace(Regex("\\.{4,}"), " ")

        if (isCode) return normalized.trim()

        normalized = normalized.replace(Regex("([a-zA-Z]+)-\\s*\\n\\s*([a-zA-Z]+)"), "$1$2")

        if (!preserveFormatting) {
            normalized = normalized
                .replace(Regex("(?<![.!?:]|-)\\n+"), " ")
                .replace(Regex("[ \\t]{2,}"), " ")
        }

        ocrCorrections.forEach { (pattern, replacement) ->
            normalized = normalized.replace(pattern, replacement)
        }

        return if (preserveFormatting) normalized else normalized.trim()
    }

    fun isCoherentBlock(block: String): Boolean {
        if (block.length < 30) return false
        val words = block.split(Regex("\\s+"))
        if (words.size < 6) return false
        val alphaCount = words.count { it.any { char -> char.isLetter() } }
        if (alphaCount < 4) return false
        val lower = words.map { it.lowercase().replace(Regex("[^a-z]"), "") }
        return lower.any { w ->
            commonVerbs.contains(w) ||
            (w.length > 4 && (w.endsWith("ize") || w.endsWith("ates") || w.endsWith("ated") || w.endsWith("ing"))) ||
            (w.length > 3 && w.endsWith("ed") && !setOf("red","bed","led","fed","wed","need","seed","speed").contains(w))
        }
    }

    /**
     * Splits a block into individual sentences and returns the most information-dense one
     * relevant to the given query terms.
     *
     * This prevents raw multi-sentence blocks (which may contain index-style lines) from
     * being output verbatim.
     */
    fun extractBestSentence(block: String, queryTerms: Set<String>): String {
        val sentences = splitIntoSentences(block)
        if (sentences.isEmpty()) return polishGrammar(block.take(200))

        data class Scored(val text: String, val score: Float)

        val scored = sentences.mapNotNull { s ->
            val clean = s.trim()
            if (clean.length < 20) return@mapNotNull null
            // Reject lines that look like index entries: "Word 12, 47, 93"
            if (clean.matches(Regex("^[A-Za-z][A-Za-z0-9 ]{1,30}\\s+(\\d+[,\\s]+){2,}\\d+\\s*\\.?$"))) return@mapNotNull null
            val words = clean.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()
            val relevance = queryTerms.intersect(words).size.toFloat()
            val lengthBonus = minOf(clean.length.toFloat() / 120f, 1.5f)
            val hasVerb = words.any { commonVerbs.contains(it) || it.endsWith("ing") || it.endsWith("ed") }
            val verbBonus = if (hasVerb) 1.5f else 0.5f
            Scored(clean, relevance * lengthBonus * verbBonus)
        }

        return scored.maxByOrNull { it.score }?.text?.let { polishGrammar(it) }
            ?: polishGrammar(sentences.first())
    }

    fun splitIntoSentences(text: String): List<String> {
        val protected = text
            .replace(Regex("(?i)\\bvs\\."), "vs[D]")
            .replace(Regex("(?i)\\betc\\."), "etc[D]")
            .replace(Regex("(?i)\\be\\.g\\."), "eg[D]")
            .replace(Regex("(?i)\\bi\\.e\\."), "ie[D]")
            .replace(Regex("(?i)\\b(no|fig|vol|ch|pg|pp|sec|mr|mrs|dr|jr|sr)\\."), "$1[D]")
            .replace(Regex("(\\d)\\.(\\d)"), "$1[D]$2")

        return protected.split(Regex("(?<=[.!?])\\s+(?=[A-Z\"'])"))
            .map { it.replace("[D]", ".").trim() }
            .filter { it.length > 15 }
    }

    fun polishGrammar(text: String): String {
        if (text.isEmpty()) return text
        var polished = text.trim()

        // Apply OCR corrections
        ocrCorrections.forEach { (pattern, replacement) ->
            polished = polished.replace(pattern, replacement)
        }

        // Fix spacing around punctuation
        polished = polished
            .replace(Regex("\\s+([.,;:!?])"), "$1")
            .replace(Regex("([.,;:!?])(?=[a-zA-Z])"), "$1 ")
            .replace(Regex("[ \\t]{2,}"), " ")

        // Capitalise first letter of each sentence
        polished = polished.replace(Regex("(^|[.!?]\\s+)([a-z])")) { match ->
            match.value.dropLast(1) + match.value.last().uppercaseChar()
        }

        // Ensure terminal punctuation
        if (!polished.matches(Regex(".*[.!?]$"))) polished += "."

        return polished.trim()
    }

    /**
     * Assembles a coherent multi-sentence answer from a list of best-extracted sentences.
     * Each chunk contributes its single best sentence to avoid raw block dumps.
     *
     * @param chunks raw content chunks from knowledge nodes
     * @param queryTerms used to select the most relevant sentence within each chunk
     */
    fun buildCoherentAnswer(chunks: List<String>, queryTerms: Set<String>): String {
        if (chunks.isEmpty()) return ""

        val transitions = listOf(
            "Furthermore, ", "Additionally, ", "In particular, ",
            "It is also worth noting that ", "Specifically, "
        )

        val sentences = mutableListOf<String>()
        val seenFingerprints = mutableSetOf<String>()

        for (chunk in chunks) {
            val best = extractBestSentence(chunk, queryTerms)
            if (best.length < 20) continue
            val fp = best.lowercase().take(55)
            if (fp in seenFingerprints) continue
            seenFingerprints.add(fp)
            sentences.add(best)
        }

        if (sentences.isEmpty()) return ""
        if (sentences.size == 1) return sentences.first()

        val sb = StringBuilder()
        for ((i, sentence) in sentences.withIndex()) {
            when (i) {
                0 -> sb.append(sentence)
                else -> {
                    val s = sentence.trimStart()
                    val needsTransition = !s.lowercase().let { l ->
                        l.startsWith("however") || l.startsWith("additionally") ||
                        l.startsWith("furthermore") || l.startsWith("in contrast") ||
                        l.startsWith("note that") || l.startsWith("for example") ||
                        l.startsWith("specifically") || l.startsWith("in particular")
                    }
                    if (needsTransition) {
                        val transition = transitions[(i - 1) % transitions.size]
                        val firstChar = s.first()
                        val body = if (firstChar.isUpperCase() &&
                            s.length > 1 && s[1].isLowerCase()) {
                            s.replaceFirstChar { it.lowercaseChar() }
                        } else s
                        sb.append(" ").append(transition).append(body)
                    } else {
                        sb.append(" ").append(s)
                    }
                }
            }
        }

        return polishGrammar(sb.toString())
    }

    /** Kept for backward compat — delegates to buildCoherentAnswer with empty query terms */
    fun synthesizeParagraph(chunks: List<String>): String =
        buildCoherentAnswer(chunks, emptySet())
}
