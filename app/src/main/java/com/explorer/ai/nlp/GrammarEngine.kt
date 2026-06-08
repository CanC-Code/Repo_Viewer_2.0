package com.explorer.ai.nlp

/**
 * A generic, strictly enforced NLP processor to ensure data integrity.
 * It prevents tables, code blocks, and index garbage from polluting the neural graph.
 */
class GrammarEngine {

    // A broad, generic set of technical verbs used to validate sentence coherence
    private val commonVerbs = setOf(
        "is", "are", "was", "were", "be", "been", "has", "have", "had", "do", "does", "did",
        "build", "compile", "run", "execute", "load", "store", "allocate", "free", "return",
        "requires", "contains", "avoids", "creates", "manages", "handles", "processes", 
        "translates", "represents", "provides", "supports", "allows", "defines", "means"
    )

    /**
     * Alpha-Symbolic Coherence Check.
     * Rejects raw numbers, table indexes, and fragmented OCR data.
     */
    fun isCoherentEnglish(sentence: String): Boolean {
        val words = sentence.trim().split(Regex("\\s+"))
        
        // A descriptive sentence rarely has fewer than 5 words
        if (words.size < 5) return false 

        var alphaCount = 0
        var nonAlphaCount = 0

        for (word in words) {
            if (word.matches(Regex("[a-zA-Z]+[a-zA-Z\\-]*[a-zA-Z]*[.,;!?]?"))) {
                alphaCount++
            } else {
                nonAlphaCount++
            }
        }
        
        // If a string is highly dense in numbers or symbols, it is a table, memory map, or garbage. Reject it.
        if (nonAlphaCount > alphaCount * 0.4) return false

        val lowerWords = words.map { it.lowercase().replace(Regex("[^a-z]"), "") }
        val hasVerb = lowerWords.any { commonVerbs.contains(it) || it.endsWith("ing") || it.endsWith("ed") || it.endsWith("s") }
        val hasGibberish = lowerWords.any { it.length > 25 }
        
        return hasVerb && !hasGibberish
    }

    /**
     * Instead of hallucinating templates, this scans a block of valid context 
     * and extracts the exact, unaltered sentence that best answers the query.
     */
    fun extractBestAnswer(context: String, queryTerms: Set<String>): String {
        val sentences = context.split(Regex("(?<=[.!?])\\s+"))
        
        val bestSentence = sentences.maxByOrNull { sentence ->
            val sentenceWords = sentence.lowercase().split(Regex("\\W+"))
            queryTerms.intersect(sentenceWords.toSet()).size
        } ?: ""
        
        if (isCoherentEnglish(bestSentence)) {
            return formatGrammar(bestSentence)
        }
        return ""
    }

    private fun formatGrammar(text: String): String {
        var clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isNotEmpty() && !clean.matches(Regex(".*[.!?]$"))) {
            clean += "."
        }
        return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
