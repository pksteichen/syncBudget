package com.syncbudget.app.data

import java.time.LocalDate

data class AmortizationEntry(
    val id: Int,
    val source: String,
    val amount: Double,
    val totalPeriods: Int,
    val startDate: LocalDate
)

fun generateAmortizationEntryId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
