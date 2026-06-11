package com.explorer.ai.nlp

/**
 * English + technical document NLP processor.
 * Shifted from isolated sentence validation to paragraph/block context validation
 * to preserve semantic structure for downstream LLM inference.
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

    // Lines matching these entirely are purely artifacts
    private val strictArtifactPatterns = listOf(
        Regex("^\\s*\\d{1,3}\\s*$"),              
        Regex("^\\s*[©®]"),                        
        Regex("^\\s*Page\\s+\\d+", RegexOption.IGNORE_CASE),
        Regex("^[A-Z][A-Z\\s,\\.]{12,}$"),        
        Regex("^\\.+\\d"),                         
        Regex("^\\d+\\.+$"),
        Regex("^([a-zA-Z]+\\s*\\d{1,4}\\s*)+$")
    )

    fun isPureArtifact(line: String): Boolean {
        val t = line.trim()
        if (t.length < 5) return true
        return strictArtifactPatterns.any { it.matches(t) }
    }

    /**
     * Cleans layout artifacts out of a paragraph without discarding the technical content.
     */
    fun scrubInlineArtifacts(text: String): String {
        return text
            .replace(Regex("\\.{4,}"), " ") // TOC dots
            .replace(Regex("(\\b\\d{1,4}(?:,\\s*\\d{1,4}){3,}\\b)"), " ") // Inline index arrays
            .replace(Regex("\\[[A-Z_]+_START[^\\]]*\\]|\\[[A-Z_]+_END\\]"), " ") // Layout tags
            .replace(Regex("---\\s*(PAGE_START|PAGE_END):\\s*\\d+\\s*---"), " ")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
    }

    /**
     * Validates that a chunk of text contains enough structure to be useful context for an LLM.
     */
    fun isCoherentBlock(block: String): Boolean {
        val cleaned = scrubInlineArtifacts(block)
        if (cleaned.length < 30) return false
        
        val words = cleaned.split(Regex("\\s+"))
        if (words.size < 6) return false

        val alphaCount = words.count { it.any { char -> char.isLetter() } }
        if (alphaCount < 4) return false

        val lower = words.map { it.lowercase().replace(Regex("[^a-z]"), "") }
        val hasVerb = lower.any { w ->
            commonVerbs.contains(w) ||
            (w.length > 4 && (w.endsWith("ize") || w.endsWith("ates") || w.endsWith("ated"))) ||
            (w.length > 3 && w.endsWith("ed") && !setOf("red","bed","led","fed","wed","need","seed","speed").contains(w))
        }

        return hasVerb
    }

    fun splitIntoSentences(text: String): List<String> {
        val p = text
            .replace(Regex("(?i)\\bvs\\."), "vs[D]")
            .replace(Regex("(?i)\\betc\\."), "etc[D]")
            .replace(Regex("(?i)\\be\\.g\\."), "eg[D]")
            .replace(Regex("(?i)\\bi\\.e\\."), "ie[D]")
            .replace(Regex("(?i)\\b(no|fig|vol|ch|pg|pp|sec|mr|mrs|dr|jr|sr)\\."), "$1[D]")
            .replace(Regex("(\\d)\\.(\\d)"), "$1[D]$2")

        return p.split(Regex("(?<=[.!?])\\s+(?=[A-Z\$])"))
            .map { it.replace("[D]", ".").trim() }
            .filter { it.isNotEmpty() }
    }

    fun formatGrammar(text: String): String {
        var c = text.replace(Regex("\\s+"), " ").trim()
        if (c.isNotEmpty() && !c.matches(Regex(".*[.!?]$"))) c += "."
        return c.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
