package com.syncbudget.app.data

import java.time.LocalDate

data class AmortizationEntry(
    val id: Int,
    val source: String,
    val amount: Double,
    val totalPeriods: Int,
    val startDate: LocalDate,
    // Sync fields
    val deviceId: String = "",
    val deleted: Boolean = false,
    val source_clock: Long = 0L,
    val amount_clock: Long = 0L,
    val totalPeriods_clock: Long = 0L,
    val startDate_clock: Long = 0L,
    val deleted_clock: Long = 0L
)

fun generateAmortizationEntryId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
