package com.explorer.ai.nlp

/**
 * NLP processor for both natural English and technical/hardware documentation.
 * Handles hex addresses, register names, hardware specs, and mixed content
 * without falsely rejecting valid technical sentences.
 */
class GrammarEngine {

    private val commonVerbs = setOf(
        "is", "are", "was", "were", "be", "been", "has", "have", "had", "do", "does", "did",
        "build", "compile", "run", "execute", "load", "store", "allocate", "free", "return",
        "initialize", "configure", "set", "get", "map", "access", "read", "write", "send",
        "receive", "connect", "disconnect", "start", "stop", "enable", "disable", "check",
        "require", "use", "need", "allow", "support", "contain", "provide", "define",
        "requires", "contains", "avoids", "creates", "manages", "handles", "processes",
        "translates", "represents", "provides", "supports", "allows", "defines", "means",
        "generates", "outputs", "inputs", "transfers", "copies", "moves", "converts",
        "calculates", "computes", "controls", "performs", "operates", "accesses",
        "addresses", "maps", "points", "refers", "indicates", "specifies", "describes",
        "include", "includes", "contained", "consist", "consists", "interface", "interfaces",
        "communicate", "communicates", "install", "installs", "installed", "compile",
        "compiles", "assembled", "link", "links"
    )

    private val technicalTermPatterns = listOf(
        Regex("0x[0-9A-Fa-f]+"),
        Regex("\\$[a-z][0-9a-z]+"),
        Regex("[A-Z][A-Z0-9_]{2,}"),
        Regex("\\d+[Kk][Bb]?"),
        Regex("\\d+[Mm][Bb]?"),
        Regex("[A-Za-z]+[0-9]+[A-Za-z0-9]*"),
        Regex("[A-Za-z]+\\.[A-Za-z]+")
    )

    fun isCoherentEnglish(sentence: String): Boolean {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return false

        val words = trimmed.split(Regex("\\s+"))
        if (words.size < 3) return false

        var alphaWordCount = 0
        var pureNumberCount = 0
        var technicalTokenCount = 0
        var gibberishCount = 0

        for (word in words) {
            val clean = word.replace(Regex("[.,;:!?\"'()\\[\\]]"), "")
            when {
                clean.isEmpty() -> {}
                technicalTermPatterns.any { it.containsMatchIn(clean) } -> technicalTokenCount++
                clean.matches(Regex("[0-9]+")) -> pureNumberCount++
                clean.matches(Regex("[a-zA-Z]+[a-zA-Z\\-]*")) -> alphaWordCount++
                clean.length > 30 -> gibberishCount++
                else -> {
                    val alphaRatio = clean.count { it.isLetter() }.toFloat() / clean.length
                    if (alphaRatio > 0.5f) alphaWordCount++ else pureNumberCount++
                }
            }
        }

        if (gibberishCount > 1) return false

        val meaningfulTokens = alphaWordCount + technicalTokenCount
        val totalTokens = meaningfulTokens + pureNumberCount

        if (totalTokens == 0) return false

        if (technicalTokenCount > 0 && technicalTokenCount >= alphaWordCount) {
            return alphaWordCount >= 2
        }

        val numberRatio = pureNumberCount.toFloat() / totalTokens
        if (numberRatio > 0.6f && technicalTokenCount == 0) return false

        if (alphaWordCount < 2) return false

        val lowerWords = words.map { it.lowercase().replace(Regex("[^a-z]"), "") }.filter { it.isNotEmpty() }

        val hasVerb = lowerWords.any { word ->
            commonVerbs.contains(word) ||
            word.endsWith("ing") ||
            word.endsWith("ed") ||
            word.endsWith("tion") ||
            word.endsWith("ize") ||
            word.endsWith("ise")
        }

        val hasTechnicalContext = technicalTokenCount >= 1 && alphaWordCount >= 3

        return hasVerb || hasTechnicalContext
    }

    fun extractRelevantSentences(context: String, queryTerms: Set<String>, maxResults: Int = 5): List<String> {
        val sentences = splitIntoSentences(context)

        data class ScoredSentence(val text: String, val score: Int)

        val scored = sentences.mapNotNull { sentence ->
            if (!isCoherentEnglish(sentence)) return@mapNotNull null
            val sentenceWords = sentence.lowercase().split(Regex("[^a-zA-Z0-9_\\-]+")).filter { it.isNotEmpty() }.toSet()
            val overlap = queryTerms.intersect(sentenceWords).size
            if (overlap == 0) return@mapNotNull null
            ScoredSentence(sentence, overlap)
        }

        return scored
            .sortedByDescending { it.score }
            .take(maxResults)
            .map { formatGrammar(it.text) }
    }

    fun extractBestAnswer(context: String, queryTerms: Set<String>): String {
        return extractRelevantSentences(context, queryTerms, 1).firstOrNull() ?: ""
    }

    fun splitIntoSentences(text: String): List<String> {
        val protected = text
            .replace(Regex("(?i)\\bvs\\."), "vs[DOT]")
            .replace(Regex("(?i)\\betc\\."), "etc[DOT]")
            .replace(Regex("(?i)\\be\\.g\\."), "e[DOT]g[DOT]")
            .replace(Regex("(?i)\\bi\\.e\\."), "i[DOT]e[DOT]")
            .replace(Regex("(?i)\\bno\\."), "no[DOT]")
            .replace(Regex("(?i)\\bfig\\."), "fig[DOT]")
            .replace(Regex("(?i)\\bvol\\."), "vol[DOT]")
            .replace(Regex("(?i)\\bch\\."), "ch[DOT]")
            .replace(Regex("(\\d)\\.(\\d)"), "$1[DOT]$2")

        val rawSentences = protected.split(Regex("(?<=[.!?])\\s+(?=[A-Z0-9\$])"))

        return rawSentences.map { s ->
            s.replace("[DOT]", ".").trim()
        }.filter { it.isNotEmpty() }
    }

    fun formatGrammar(text: String): String {
        var clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isNotEmpty() && !clean.matches(Regex(".*[.!?]$"))) {
            clean += "."
        }
        return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
