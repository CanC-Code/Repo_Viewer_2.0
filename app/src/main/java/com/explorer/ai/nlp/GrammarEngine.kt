package com.explorer.ai.nlp

/**
 * English + technical document NLP processor.
 *
 * Handles:
 * - Natural prose sentences
 * - Hardware documentation (hex addresses, register names, chip specs)
 * - PDF artifacts: actively filters TOC lines, page numbers, bare chapter labels, and index dumps
 * - Bullet-merged run-on text from PDFBox
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
        "serve","serves","locate","locates","represent","represents","connect","connects",
        "boot","boots","initialize","initializes","halt","halts","ping","download","upload",
        "trigger","triggers","render","renders"
    )

    private val technicalTermPatterns = listOf(
        Regex("0x[0-9A-Fa-f]+"),               // Hex addresses: 0x80000000
        Regex("\\$[a-z][0-9a-z]+"),            // MIPS regs: $t0, $v0, $sp
        Regex("\\b[A-Z][A-Z0-9_]{2,}\\b"),     // Constants: RDRAM, CPU, RSP, KSEG0
        Regex("\\b\\d+[Kk][Bb]?\\b"),          // Sizes: 4KB, 64K
        Regex("\\b\\d+[Mm][Bb]?\\b"),          // Sizes: 4MB
        Regex("\\b[A-Za-z]{2,}[0-9]+[A-Za-z0-9]*\\b"), // N64, R4300, VR4300, IEEE32
        Regex("\\b[A-Za-z]{3,}\\.[A-Za-z]{2,}\\b")     // AUTOEXEC.BAT, README.TXT
    )

    // Lines matching these patterns are PDF artifacts — never store as knowledge
    private val artifactPatterns = listOf(
        Regex("\\.{4,}"),                          // TOC leader dots
        Regex("^\\s*\\d{1,3}\\s*$"),              // Bare page number
        Regex("^\\s*[©®]"),                        // Copyright line start
        Regex("^\\s*Page\\s+\\d", RegexOption.IGNORE_CASE),
        Regex("^[A-Z][A-Z\\s,\\.]{12,}$"),        // ALL CAPS section header with no verb
        Regex("^\\.+\\d"),                         // .....10-4
        Regex("^\\d+\\.+$"),                       // 10.......
        Regex("^\\s*\\[PARAGRAPH"),                // PDFBox layout tokens
        Regex("^\\s*\\[COLUMN"),
        Regex("^\\s*---\\s*PAGE"),
        Regex("(\\b\\d{1,4}(?:,\\s*\\d{1,4}){2,}\\b)"), // INDEX ARTIFACTS: e.g., 41, 48, 49, 55
        Regex("([a-zA-Z]+\\s*\\d{1,4}\\s*)+$")     // Index lines ending in isolated numbers
    )

    fun isArtifact(line: String): Boolean {
        val t = line.trim()
        if (t.length < 6) return true
        return artifactPatterns.any { it.containsMatchIn(t) }
    }

    fun isCoherentEnglish(sentence: String): Boolean {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty() || isArtifact(trimmed)) return false

        val words = trimmed.split(Regex("\\s+"))
        if (words.size < 4) return false  // min 4 words to avoid short fragments

        // Aggressively reject dense comma lists (Index/Glossary catch)
        val commaCount = trimmed.count { it == ',' }
        if (commaCount > 3 && words.size < commaCount * 4) return false

        var alpha = 0; var numeric = 0; var technical = 0; var gibberish = 0

        for (word in words) {
            val clean = word.replace(Regex("[.,;:!?\"'()\\[\\]{}]"), "")
            when {
                clean.isEmpty() -> {}
                clean.matches(Regex("\\.{2,}")) -> { gibberish++; continue } // dot sequences
                technicalTermPatterns.any { it.containsMatchIn(clean) } -> technical++
                clean.matches(Regex("[0-9]+")) -> numeric++
                clean.matches(Regex("[a-zA-Z][a-zA-Z\\-]*")) -> alpha++
                clean.length > 35 -> gibberish++
                else -> { val r = clean.count { it.isLetter() }.toFloat() / clean.length; if (r > 0.5f) alpha++ else numeric++ }
            }
        }

        if (gibberish > 0) return false

        val total = alpha + technical + numeric
        if (total == 0) return false

        // Reject number-heavy lines, preventing floating memory offsets from acting as sentences
        if (numeric.toFloat() / total > 0.45f && technical == 0) return false
        if (alpha < 3) return false

        // Ensure index dumps fail even if technical terms are present
        if (technical > 0 && alpha < 3) return false

        val lower = words.map { it.lowercase().replace(Regex("[^a-z]"), "") }.filter { it.isNotEmpty() }
        
        // Removed "tion" and "ing" as they are overwhelmingly nouns/gerunds in technical manuals
        val hasVerb = lower.any { w ->
            commonVerbs.contains(w) ||
            (w.length > 4 && w.endsWith("ize")) ||
            (w.length > 4 && w.endsWith("ates")) ||
            (w.length > 4 && w.endsWith("ated")) ||
            (w.length > 3 && w.endsWith("ed") && !setOf("red","bed","led","fed","wed","need","seed","speed","feed").contains(w))
        }
        
        // Must have a verb, or be a highly descriptive technical definition without being an index array
        return hasVerb || (technical >= 1 && alpha >= 5 && commaCount <= 2 && numeric <= 2)
    }

    /**
     * Scores sentences by relevance to a set of query terms.
     * Shorter, focused sentences are preferred over bloated page-dumps.
     */
    fun extractRelevantSentences(context: String, queryTerms: Set<String>, maxResults: Int = 3): List<String> {
        data class Scored(val text: String, val score: Float)

        val sentences = splitIntoSentences(context)
        val scored = sentences.mapNotNull { s ->
            if (!isCoherentEnglish(s)) return@mapNotNull null
            val words = s.lowercase().split(Regex("[^a-zA-Z0-9_\\-]+")).filter { it.length > 2 }.toSet()
            val overlap = queryTerms.intersect(words).size
            if (overlap == 0) return@mapNotNull null
            // Penalise very long sentences (likely merged PDF paragraphs)
            val lengthFactor = 1f / (1f + s.length / 180f)
            Scored(s, overlap * lengthFactor)
        }

        return scored.sortedByDescending { it.score }.take(maxResults).map { formatGrammar(it.text) }
    }

    fun extractBestAnswer(context: String, queryTerms: Set<String>): String =
        extractRelevantSentences(context, queryTerms, 1).firstOrNull() ?: ""

    fun splitIntoSentences(text: String): List<String> {
        // Protect common abbreviations from triggering false splits
        val p = text
            .replace(Regex("(?i)\\bvs\\."), "vs[D]")
            .replace(Regex("(?i)\\betc\\."), "etc[D]")
            .replace(Regex("(?i)\\be\\.g\\."), "eg[D]")
            .replace(Regex("(?i)\\bi\\.e\\."), "ie[D]")
            .replace(Regex("(?i)\\b(no|fig|vol|ch|pg|pp|sec|mr|mrs|dr|jr|sr)\\."), "$1[D]")
            .replace(Regex("(\\d)\\.(\\d)"), "$1[D]$2")  // decimals

        return p.split(Regex("(?<=[.!?])\\s+(?=[A-Z\$])"))
            .map { it.replace("[D]", ".").trim() }
            .filter { it.isNotEmpty() && !isArtifact(it) }
    }

    fun formatGrammar(text: String): String {
        var c = text.replace(Regex("\\s+"), " ").trim()
        if (c.isNotEmpty() && !c.matches(Regex(".*[.!?]$"))) c += "."
        return c.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
