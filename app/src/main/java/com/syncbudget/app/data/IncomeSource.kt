package com.syncbudget.app.data

import java.time.LocalDate

enum class RepeatType { DAYS, WEEKS, BI_WEEKLY, MONTHS, BI_MONTHLY, ANNUAL }

data class IncomeSource(
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
    val deleted: Boolean = false
)

fun generateIncomeSourceId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
