package com.techadvantage.budgetrak.data

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
    val description: String = "",
    val categoryAmounts: List<CategoryAmount> = emptyList(),
    val amount: Double,
    val isUserCategorized: Boolean = true,
    val excludeFromBudget: Boolean = false,
    val isBudgetIncome: Boolean = false,
    val linkedRecurringExpenseId: Int? = null,
    val linkedAmortizationEntryId: Int? = null,
    val linkedIncomeSourceId: Int? = null,
    val amortizationAppliedAmount: Double = 0.0,
    val linkedRecurringExpenseAmount: Double = 0.0,
    val linkedIncomeSourceAmount: Double = 0.0,
    val linkedSavingsGoalId: Int? = null,
    val linkedSavingsGoalAmount: Double = 0.0,
    // Receipt photo slots (up to 5 per transaction)
    val receiptId1: String? = null,
    val receiptId2: String? = null,
    val receiptId3: String? = null,
    val receiptId4: String? = null,
    val receiptId5: String? = null,
    // Sync fields
    val deviceId: String = "",
    val deleted: Boolean = false
)

fun generateTransactionId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
