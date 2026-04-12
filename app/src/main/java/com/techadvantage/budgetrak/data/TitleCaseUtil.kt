package com.techadvantage.budgetrak.data

private val minorWords = setOf(
    "a", "an", "the",
    "and", "but", "or", "nor", "for", "yet", "so",
    "at", "by", "in", "of", "on", "to", "up", "as"
)

/**
 * APA-style title case: capitalize all words except minor words (articles,
 * short prepositions, conjunctions). The first word is always capitalized.
 * Hyphenated parts are each capitalized individually.
 *
 * User-typed capitals are preserved in two cases:
 *   - Short all-caps words (≤4 letters) are treated as acronyms (USA, BMW, NASA).
 *   - Words with internal mixed case (lowercase first letter + any uppercase
 *     after position 0) are treated as user-styled tokens (iPhone, eBay).
 *
 * Longer all-caps words still get Title-Cased (DOORDASH → Doordash) so a
 * stuck Caps Lock doesn't ruin the merchant list.
 */
fun toApaTitleCase(text: String): String {
    if (text.isBlank()) return text
    val tokens = text.split(" ")
    val firstIdx = tokens.indexOfFirst { it.isNotEmpty() }
    return tokens.mapIndexed { i, word ->
        if (word.isEmpty()) return@mapIndexed word
        val isFirst = i == firstIdx
        word.split("-").joinToString("-") { part ->
            if (part.isEmpty()) return@joinToString part

            // Acronym heuristic: short, all letters uppercase (non-letters allowed).
            val letters = part.filter { it.isLetter() }
            val isShortAcronym = letters.isNotEmpty() &&
                letters.length <= 4 &&
                letters.all { it.isUpperCase() }
            if (isShortAcronym) return@joinToString part

            // User mixed-case heuristic: starts lowercase, has uppercase after position 0.
            val firstChar = part.firstOrNull()
            val hasInternalCaps = part.drop(1).any { it.isUpperCase() }
            if (firstChar != null && firstChar.isLowerCase() && hasInternalCaps) {
                return@joinToString part
            }

            // Standard Title Case path: lowercase the part, capitalize first letter
            // unless it's a minor word in non-first position.
            val lower = part.lowercase()
            if (isFirst || lower !in minorWords) {
                lower.replaceFirstChar { c -> c.uppercase() }
            } else lower
        }
    }.joinToString(" ")
}
