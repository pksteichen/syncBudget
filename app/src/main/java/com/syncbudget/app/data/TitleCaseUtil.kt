package com.syncbudget.app.data

private val minorWords = setOf(
    "a", "an", "the",
    "and", "but", "or", "nor", "for", "yet", "so",
    "at", "by", "in", "of", "on", "to", "up", "as"
)

/**
 * APA-style title case: capitalize all words except minor words (articles,
 * short prepositions, conjunctions). The first word is always capitalized.
 * Hyphenated parts are each capitalized individually.
 */
fun toApaTitleCase(text: String): String {
    if (text.isBlank()) return text
    val tokens = text.split(" ")
    val firstIdx = tokens.indexOfFirst { it.isNotEmpty() }
    return tokens.mapIndexed { i, word ->
        if (word.isEmpty()) word
        else {
            val isFirst = i == firstIdx
            word.lowercase().split("-").joinToString("-") { part ->
                if (part.isEmpty()) part
                else if (isFirst || part !in minorWords) {
                    part.replaceFirstChar { c -> c.uppercase() }
                } else part
            }
        }
    }.joinToString(" ")
}
