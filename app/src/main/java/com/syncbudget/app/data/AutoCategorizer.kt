package com.syncbudget.app.data

import java.time.LocalDate

fun autoCategorize(
    imported: Transaction,
    existing: List<Transaction>,
    categories: List<Category>
): Transaction {
    val sixMonthsAgo = imported.date.minusMonths(6)
    val source = imported.source.lowercase()

    // Find existing transactions from same merchant within 6 months
    val matchingTxns = existing
        .filter { ex ->
            ex.categoryAmounts.isNotEmpty() &&
            !ex.date.isBefore(sixMonthsAgo) &&
            sharesFiveCharSubstring(source, ex.source.lowercase())
        }
        .sortedByDescending { it.date }
        .take(10)

    val bestCategoryId = if (matchingTxns.isNotEmpty()) {
        matchingTxns
            .flatMap { it.categoryAmounts }
            .groupBy { it.categoryId }
            .maxByOrNull { it.value.size }
            ?.key
    } else null

    val categoryId = bestCategoryId
        ?: categories.find { it.tag == "other" }?.id
        ?: return imported

    return imported.copy(
        categoryAmounts = listOf(CategoryAmount(categoryId, imported.amount)),
        isUserCategorized = false
    )
}

private fun sharesFiveCharSubstring(a: String, b: String): Boolean {
    if (a.length < 5 || b.length < 5) return a == b
    val substrings = mutableSetOf<String>()
    for (i in 0..a.length - 5) {
        substrings.add(a.substring(i, i + 5))
    }
    for (i in 0..b.length - 5) {
        if (b.substring(i, i + 5) in substrings) return true
    }
    return false
}
