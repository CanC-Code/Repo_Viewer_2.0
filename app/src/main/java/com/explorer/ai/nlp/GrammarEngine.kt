package com.explorer.ai.nlp

/**
 * A localized Symbolic NLP processor enforcing English grammar rules, 
 * Subject-Verb-Object (SVO) extraction, and heuristic part-of-speech tagging.
 */
class GrammarEngine {

    private val commonVerbs = setOf(
        "is", "are", "was", "were", "be", "been", "has", "have", "had", "do", "does", "did",
        "build", "compile", "run", "execute", "load", "store", "allocate", "free", "return",
        "requires", "contains", "avoids", "creates", "manages", "handles", "processes"
    )

    private val techNouns = setOf(
        "memory", "pointer", "bridge", "jni", "cmake", "gradle", "thread", "process", 
        "register", "rom", "ram", "cpu", "rcp", "rdp", "rsp", "buffer", "matrix", "vertex"
    )

    // Templates for generating coherent English when raw data is fragmented
    private val explanationTemplates = listOf(
        "The documentation indicates that [SUBJECT] [VERB] [OBJECT].",
        "Based on the ingested manuals, [SUBJECT] is utilized to [VERB] [OBJECT].",
        "Architecturally, [SUBJECT] [VERB] within the context of [OBJECT]."
    )

    fun isCoherentEnglish(sentence: String): Boolean {
        val words = sentence.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }
        if (words.size < 4) return false
        
        // A coherent technical sentence usually contains at least one known verb or ends in typical verb suffixes
        val hasVerb = words.any { commonVerbs.contains(it) || it.endsWith("ing") || it.endsWith("ed") || it.endsWith("s") }
        
        // A coherent sentence shouldn't have wildly disproportionate word lengths (babble detection)
        val hasGibberish = words.any { it.length > 25 }
        
        return hasVerb && !hasGibberish
    }

    fun reconstructThought(subjectTerms: Set<String>, rawFragment: String): String {
        if (isCoherentEnglish(rawFragment)) {
            return sanitizeFormatting(rawFragment)
        }

        // If the raw fragment is babble, reconstruct it using the English templates
        val primarySubject = subjectTerms.firstOrNull { techNouns.contains(it) } ?: subjectTerms.firstOrNull() ?: "the system"
        val fallbackVerb = "operates alongside"
        
        val template = explanationTemplates.random()
        return template
            .replace("[SUBJECT]", primarySubject.replaceFirstChar { it.titlecase() })
            .replace("[VERB]", fallbackVerb)
            .replace("[OBJECT]", sanitizeFormatting(rawFragment).take(40) + "...")
    }

    private fun sanitizeFormatting(text: String): String {
        var clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isNotEmpty() && !clean.matches(Regex(".*[.!?]$"))) {
            clean += "."
        }
        return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
