package com.techadvantage.budgetrak.data

import java.time.LocalDate

data class AmortizationEntry(
    val id: Int,
    val source: String,
    val description: String = "",
    val amount: Double,
    val totalPeriods: Int,
    val startDate: LocalDate,
    // Sync fields
    val deviceId: String = "",
    val deleted: Boolean = false,
    val isPaused: Boolean = false
)

fun generateAmortizationEntryId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
