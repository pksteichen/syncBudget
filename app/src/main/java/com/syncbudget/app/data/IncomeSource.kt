package com.syncbudget.app.data

import java.time.LocalDate

enum class RepeatType { DAYS, WEEKS, BI_WEEKLY, MONTHS, BI_MONTHLY }

data class IncomeSource(
    val id: Int,
    val source: String,
    val amount: Double,
    val repeatType: RepeatType = RepeatType.MONTHS,
    val repeatInterval: Int = 1,
    val startDate: LocalDate? = null,
    val monthDay1: Int? = null,
    val monthDay2: Int? = null,
    // Sync fields
    val deviceId: String = "",
    val deleted: Boolean = false,
    val source_clock: Long = 0L,
    val amount_clock: Long = 0L,
    val repeatType_clock: Long = 0L,
    val repeatInterval_clock: Long = 0L,
    val startDate_clock: Long = 0L,
    val monthDay1_clock: Long = 0L,
    val monthDay2_clock: Long = 0L,
    val deleted_clock: Long = 0L
)

fun generateIncomeSourceId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
