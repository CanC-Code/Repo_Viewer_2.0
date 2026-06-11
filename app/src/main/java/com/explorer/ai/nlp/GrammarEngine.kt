package com.explorer.ai.nlp

/**
 * English + technical document NLP processor and text synthesizer.
 * Operates purely on-device to clean, normalize, and structurally synthesize 
 * extracted PDF text into cohesive, readable paragraphs.
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

    // Common PDF extraction/OCR typos and grammatical corrections
    private val ocrCorrections = mapOf(
        Regex("\\bteh\\b", RegexOption.IGNORE_CASE) to "the",
        Regex("\\breceiveing\\b", RegexOption.IGNORE_CASE) to "receiving",
        Regex("\\baddres\\b", RegexOption.IGNORE_CASE) to "address",
        Regex("\\badress\\b", RegexOption.IGNORE_CASE) to "address",
        Regex("\\bVR 4300\\b", RegexOption.IGNORE_CASE) to "VR4300",
        Regex("\\bR 4300\\b", RegexOption.IGNORE_CASE) to "R4300",
        Regex("\\bRDRA M\\b", RegexOption.IGNORE_CASE) to "RDRAM",
        Regex("\\bprocesor\\b", RegexOption.IGNORE_CASE) to "processor",
        Regex("\\bproccesor\\b", RegexOption.IGNORE_CASE) to "processor",
        Regex("\\bmemmory\\b", RegexOption.IGNORE_CASE) to "memory",
        Regex("\\b(it|this|that) are\\b", RegexOption.IGNORE_CASE) to "$1 is",
        Regex("\\ba\\s+([aeiou])", RegexOption.IGNORE_CASE) to "an $1"
    )

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
     * Resolves hyphenation and normalizes the text block prior to ingestion.
     */
    fun normalizeText(text: String): String {
        var normalized = text
            // Fix line-break hyphenation (e.g., "process-\nor" -> "processor")
            .replace(Regex("([a-zA-Z]+)-\\s*\\n\\s*([a-zA-Z]+)"), "$1$2")
            // Remove TOC dots and inline artifacts
            .replace(Regex("\\.{4,}"), " ")
            .replace(Regex("(\\b\\d{1,4}(?:,\\s*\\d{1,4}){3,}\\b)"), " ")
            .replace(Regex("\\[[A-Z_]+_START[^\\]]*\\]|\\[[A-Z_]+_END\\]"), " ")
            .replace(Regex("---\\s*(PAGE_START|PAGE_END):\\s*\\d+\\s*---"), " ")
            // Flatten newlines mid-sentence
            .replace(Regex("(?<!\\.)\\n+(?=[a-z])"), " ")
            .replace(Regex("[ \\t]{2,}"), " ")

        // Apply OCR typo correction matrix
        ocrCorrections.forEach { (pattern, replacement) ->
            normalized = normalized.replace(pattern, replacement)
        }

        return normalized.trim()
    }

    /**
     * Ensures an extracted block has actual semantic value before saving.
     */
    fun isCoherentBlock(block: String): Boolean {
        if (block.length < 30) return false
        val words = block.split(Regex("\\s+"))
        if (words.size < 6) return false

        val alphaCount = words.count { it.any { char -> char.isLetter() } }
        if (alphaCount < 4) return false

        val lower = words.map { it.lowercase().replace(Regex("[^a-z]"), "") }
        return lower.any { w ->
            commonVerbs.contains(w) ||
            (w.length > 4 && (w.endsWith("ize") || w.endsWith("ates") || w.endsWith("ated"))) ||
            (w.length > 3 && w.endsWith("ed") && !setOf("red","bed","led","fed","wed","need","seed","speed").contains(w))
        }
    }

    /**
     * Takes raw extracted text and enforces proper grammar, punctuation, and capitalization.
     */
    fun polishGrammar(text: String): String {
        var polished = text.trim()
        if (polished.isEmpty()) return polished

        // Ensure sentences start with a capital letter
        polished = polished.replace(Regex("(^|[.!?]\\s+)([a-z])")) {
            it.value.uppercase()
        }

        // Clean up loose punctuation spaces
        polished = polished.replace(Regex("\\s+([.,;:!?])"), "$1")
            .replace(Regex("([.,;:!?])(?=[a-zA-Z])"), "$1 ")
            .replace(Regex("[ \\t]{2,}"), " ")

        // Ensure period at the end
        if (!polished.matches(Regex(".*[.!?]$"))) {
            polished += "."
        }

        return polished
    }

    /**
     * Synthesizes multiple disjointed chunks into a cohesive paragraph using transitional phrases.
     */
    fun synthesizeParagraph(chunks: List<String>): String {
        if (chunks.isEmpty()) return ""
        if (chunks.size == 1) return polishGrammar(chunks.first())

        val stringBuilder = StringBuilder()
        
        for ((index, chunk) in chunks.withIndex()) {
            val polishedChunk = polishGrammar(chunk)
            
            when (index) {
                0 -> stringBuilder.append(polishedChunk)
                1 -> stringBuilder.append(" Furthermore, ").append(polishedChunk.replaceFirstChar { it.lowercase() })
                else -> stringBuilder.append(" Additionally, ").append(polishedChunk.replaceFirstChar { it.lowercase() })
            }
        }
        
        return polishGrammar(stringBuilder.toString())
    }
}
