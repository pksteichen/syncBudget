package com.techadvantage.budgetrak.data

fun autoCategorize(
    imported: Transaction,
    existing: List<Transaction>,
    categories: List<Category>,
    minChars: Int = 5
): Transaction {
    val sixMonthsAgo = imported.date.minusMonths(6)
    val source = imported.source.lowercase()

    // Find existing transactions from same merchant within 6 months
    val matchingTxns = existing
        .filter { ex ->
            ex.categoryAmounts.isNotEmpty() &&
            !ex.date.isBefore(sixMonthsAgo) &&
            sharesFiveCharSubstring(source, ex.source.lowercase(), minChars)
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

private fun sharesFiveCharSubstring(s1: String, s2: String, minChars: Int = 5): Boolean {
    // Strip non-alphanumeric so "Wal-Mart"/"Walmart" and "O'Riley"/"ORiley" match
    val a = s1.replace(Regex("[^a-z0-9]"), "")
    val b = s2.replace(Regex("[^a-z0-9]"), "")
    if (a.length < minChars || b.length < minChars) return a == b
    val substrings = mutableSetOf<String>()
    for (i in 0..a.length - minChars) {
        substrings.add(a.substring(i, i + minChars))
    }
    for (i in 0..b.length - minChars) {
        if (b.substring(i, i + minChars) in substrings) return true
    }
    return false
}
