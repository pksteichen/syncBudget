package com.syncbudget.app.data

import java.time.LocalDate

data class FutureExpenditure(
    val id: Int,
    val description: String,
    val amount: Double,
    val targetDate: LocalDate,
    val totalSavedSoFar: Double = 0.0,
    val isPaused: Boolean = false
)

fun generateFutureExpenditureId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
