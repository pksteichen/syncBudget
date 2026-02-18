package com.syncbudget.app.data

import java.time.LocalDate

enum class TransactionType { EXPENSE, INCOME }

data class CategoryAmount(
    val categoryId: Int,
    val amount: Double
)

data class Transaction(
    val id: Int,
    val type: TransactionType,
    val date: LocalDate,
    val source: String,
    val categoryAmounts: List<CategoryAmount> = emptyList(),
    val amount: Double,
    val isUserCategorized: Boolean = true,
    val isBudgetIncome: Boolean = false
)

fun generateTransactionId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
