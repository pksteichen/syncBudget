package com.syncbudget.app.data

import java.time.LocalDate

data class RecurringExpense(
    val id: Int,
    val source: String,
    val amount: Double,
    val repeatType: RepeatType = RepeatType.MONTHS,
    val repeatInterval: Int = 1,
    val startDate: LocalDate? = null,
    val monthDay1: Int? = null,
    val monthDay2: Int? = null
)

fun generateRecurringExpenseId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
