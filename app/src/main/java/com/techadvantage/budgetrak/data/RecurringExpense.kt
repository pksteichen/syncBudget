package com.techadvantage.budgetrak.data

import java.time.LocalDate

data class RecurringExpense(
    val id: Int,
    val source: String,
    val description: String = "",
    val amount: Double,
    val repeatType: RepeatType = RepeatType.MONTHS,
    val repeatInterval: Int = 1,
    val startDate: LocalDate? = null,
    val monthDay1: Int? = null,
    val monthDay2: Int? = null,
    // Sync fields
    val deviceId: String = "",
    val deleted: Boolean = false,
    // Set-aside tracking
    val setAsideSoFar: Double = 0.0,
    val isAccelerated: Boolean = false
)

fun generateRecurringExpenseId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
