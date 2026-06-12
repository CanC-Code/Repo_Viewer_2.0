package com.explorer.ai.nlp

/**
 * English + technical document NLP processor and text synthesizer.
 * Enforces hardware-specific capitalization, prevents acronym corruption, 
 * and handles strict spatial diagram parsing with robust newline flattening.
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

    private val ocrCorrections = mapOf(
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

    fun isDiagramOrTable(block: String): Boolean {
        val lines = block.split("\n")
        if (lines.size < 2) return false
        
        val hexCount = Regex("0x[0-9A-Fa-f]+").findAll(block).count()
        val pipeCount = block.count { it == '|' || it == '+' || it == '=' || it == '-' }
        val tabularSpacing = Regex(" {4,}").findAll(block).count()
        
        val isDenseProse = block.length > 200 && lines.size > 4 && tabularSpacing < 3
        
        return !isDenseProse && (hexCount >= 3 || pipeCount >= 6 || tabularSpacing >= 5)
    }

    fun normalizeText(text: String, preserveFormatting: Boolean = false): String {
        var normalized = text
            .replace(Regex("([a-zA-Z]+)-\\s*\\n\\s*([a-zA-Z]+)"), "$1$2")
            .replace(Regex("\\.{4,}"), " ")
            .replace(Regex("\\[[A-Z_]+_START[^\\]]*\\]|\\[[A-Z_]+_END\\]"), " ")
            .replace(Regex("---\\s*(PAGE_START|PAGE_END):\\s*\\d+\\s*---"), " ")

        if (!preserveFormatting) {
            // FIXED: Safely flattens newlines as long as the line doesn't end in structural punctuation.
            // This prevents proper nouns on the next line from causing a shattered sentence.
            normalized = normalized.replace(Regex("(?<![.!?:]|-)\\n+"), " ")
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
            (w.length > 4 && (w.endsWith("ize") || w.endsWith("ates") || w.endsWith("ated"))) ||
            (w.length > 3 && w.endsWith("ed") && !setOf("red","bed","led","fed","wed","need","seed","speed").contains(w))
        }
    }

    fun polishGrammar(text: String): String {
        var polished = text.trim()
        if (polished.isEmpty()) return polished

        polished = polished.replace(Regex("(^|[.!?]\\s+)([a-z])")) { it.value.uppercase() }
        polished = polished.replace(Regex("\\s+([.,;:!?])"), "$1")
            .replace(Regex("([.,;:!?])(?=[a-zA-Z])"), "$1 ")
            .replace(Regex("[ \\t]{2,}"), " ")

        if (!polished.matches(Regex(".*[.!?]$"))) polished += "."
        
        ocrCorrections.forEach { (pattern, replacement) ->
            polished = polished.replace(pattern, replacement)
        }
        
        return polished
    }

    fun synthesizeParagraph(chunks: List<String>): String {
        if (chunks.isEmpty()) return ""
        if (chunks.size == 1) return polishGrammar(chunks.first())

        val stringBuilder = StringBuilder()
        val naturalTransitions = setOf("however,", "additionally,", "furthermore,", "in contrast,", "for example,", "note that", "therefore,")

        for ((index, chunk) in chunks.withIndex()) {
            val polishedChunk = polishGrammar(chunk)
            val lowerChunk = polishedChunk.lowercase()
            val startsWithTransition = naturalTransitions.any { lowerChunk.startsWith(it) }
            
            val firstWord = polishedChunk.takeWhile { it.isLetterOrDigit() }
            val isAcronym = firstWord.length > 1 && (firstWord.all { it.isUpperCase() } || firstWord.matches(Regex("^[A-Z]+\\d+.*")))

            if (index == 0) {
                stringBuilder.append(polishedChunk)
            } else {
                stringBuilder.append(" ")
                if (!startsWithTransition) {
                    val transition = if (index % 2 != 0) "Furthermore, " else "Additionally, "
                    stringBuilder.append(transition)
                    
                    if (isAcronym) {
                        stringBuilder.append(polishedChunk)
                    } else {
                        stringBuilder.append(polishedChunk.replaceFirstChar { it.lowercase() })
                    }
                } else {
                    stringBuilder.append(polishedChunk)
                }
            }
        }
        return polishGrammar(stringBuilder.toString())
    }
}
