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
    val isBudgetIncome: Boolean = false,
    // Sync fields
    val deviceId: String = "",
    val deleted: Boolean = false,
    val source_clock: Long = 0L,
    val amount_clock: Long = 0L,
    val date_clock: Long = 0L,
    val type_clock: Long = 0L,
    val categoryAmounts_clock: Long = 0L,
    val isUserCategorized_clock: Long = 0L,
    val isBudgetIncome_clock: Long = 0L,
    val deleted_clock: Long = 0L
)

fun generateTransactionId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
