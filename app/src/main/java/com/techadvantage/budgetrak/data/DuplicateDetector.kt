package com.techadvantage.budgetrak.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

fun filterAlreadyLoadedDays(
    fileTransactions: List<Transaction>,
    appTransactions: List<Transaction>
): List<Transaction> {
    val fileByDate = fileTransactions.groupBy { it.date }
    val appByDate = appTransactions.groupBy { it.date }
    val result = mutableListOf<Transaction>()

    for ((date, fileTxns) in fileByDate) {
        val appTxns = appByDate[date]
        if (appTxns == null) {
            // No app transactions for this day — keep all file transactions
            result.addAll(fileTxns)
            continue
        }

        // Build a count map of app amounts (rounded to cents) for O(1) lookup
        val appPool = HashMap<Long, Int>()
        for (t in appTxns) {
            val cents = Math.round(t.amount * 100)
            appPool[cents] = (appPool[cents] ?: 0) + 1
        }
        val matched = mutableListOf<Transaction>()
        val unmatched = mutableListOf<Transaction>()

        for (txn in fileTxns) {
            val fileCents = Math.round(txn.amount * 100)
            val count = appPool[fileCents] ?: 0
            if (count > 0) {
                appPool[fileCents] = count - 1
                matched.add(txn)
            } else {
                unmatched.add(txn)
            }
        }

        val total = fileTxns.size
        val matchCount = matched.size

        if (total <= 5) {
            // Require 100% match to skip the day
            if (matchCount == total) {
                // Day already loaded — skip all
            } else {
                result.addAll(fileTxns)
            }
        } else {
            // Require >= 80% match to consider the day already loaded
            if (matchCount.toDouble() / total >= 0.8) {
                // Day already loaded — keep only unmatched transactions
                result.addAll(unmatched)
            } else {
                result.addAll(fileTxns)
            }
        }
    }

    return result
}

/**
 * Check if a transaction date is near any expected occurrence of a schedule.
 * Uses BudgetCalculator.generateOccurrences to produce exact dates, then
 * checks if the transaction falls within [dayWindow] days of any of them.
 */
private fun dateNearOccurrence(
    transactionDate: LocalDate,
    repeatType: RepeatType,
    repeatInterval: Int,
    startDate: LocalDate?,
    monthDay1: Int?,
    monthDay2: Int?,
    dayWindow: Int
): Boolean {
    // Generate occurrences in a window around the transaction date
    val rangeStart = transactionDate.minusDays(dayWindow.toLong())
    val rangeEnd = transactionDate.plusDays(dayWindow.toLong())
    val occurrences = BudgetCalculator.generateOccurrences(
        repeatType, repeatInterval, startDate, monthDay1, monthDay2, rangeStart, rangeEnd
    )
    return occurrences.any { occ ->
        abs(ChronoUnit.DAYS.between(transactionDate, occ)) <= dayWindow
    }
}

/**
 * Find the closest expected occurrence to a transaction date and return
 * how many days apart they are, or null if no occurrence can be computed.
 * Used for the date advisory (warns if > 2 days off).
 */
fun nearestOccurrenceDistance(
    transactionDate: LocalDate,
    repeatType: RepeatType,
    repeatInterval: Int,
    startDate: LocalDate?,
    monthDay1: Int?,
    monthDay2: Int?
): Long? {
    val rangeStart = transactionDate.minusDays(15)
    val rangeEnd = transactionDate.plusDays(15)
    val occurrences = BudgetCalculator.generateOccurrences(
        repeatType, repeatInterval, startDate, monthDay1, monthDay2, rangeStart, rangeEnd
    )
    if (occurrences.isEmpty()) return null
    return occurrences.minOf { abs(ChronoUnit.DAYS.between(transactionDate, it)) }
}

/**
 * Find the closest expected occurrence date to a transaction date.
 * Returns the occurrence LocalDate, or null if none can be computed.
 */
fun nearestOccurrenceDate(
    transactionDate: LocalDate,
    repeatType: RepeatType,
    repeatInterval: Int,
    startDate: LocalDate?,
    monthDay1: Int?,
    monthDay2: Int?
): LocalDate? {
    val rangeStart = transactionDate.minusDays(15)
    val rangeEnd = transactionDate.plusDays(15)
    val occurrences = BudgetCalculator.generateOccurrences(
        repeatType, repeatInterval, startDate, monthDay1, monthDay2, rangeStart, rangeEnd
    )
    if (occurrences.isEmpty()) return null
    return occurrences.minByOrNull { abs(ChronoUnit.DAYS.between(transactionDate, it)) }
}

fun isRecurringDateCloseEnough(transactionDate: LocalDate, re: RecurringExpense): Boolean {
    val distance = nearestOccurrenceDistance(
        transactionDate, re.repeatType, re.repeatInterval,
        re.startDate, re.monthDay1, re.monthDay2
    )
    return distance == null || distance <= 2
}

/** Return all matching recurring expenses, ranked by closest date then closest amount. */
fun findRecurringExpenseMatches(
    incoming: Transaction,
    recurringExpenses: List<RecurringExpense>,
    percentTolerance: Double = 0.01,
    dollarTolerance: Int = 1,
    minChars: Int = 5,
    dayWindow: Int = 7
): List<RecurringExpense> {
    return recurringExpenses.filter { re ->
        amountMatches(incoming.amount, re.amount, percentTolerance, dollarTolerance) &&
        merchantMatches(incoming.source, re.source, minChars) &&
        dateNearOccurrence(
            incoming.date, re.repeatType, re.repeatInterval,
            re.startDate, re.monthDay1, re.monthDay2, dayWindow
        )
    }.sortedWith(compareBy<RecurringExpense> {
        nearestOccurrenceDistance(incoming.date, it.repeatType, it.repeatInterval, it.startDate, it.monthDay1, it.monthDay2) ?: Long.MAX_VALUE
    }.thenBy { abs(incoming.amount - it.amount) })
}

/** Return all matching amortization entries, ranked by closest amount. */
fun findAmortizationMatches(
    incoming: Transaction,
    entries: List<AmortizationEntry>,
    percentTolerance: Double = 0.01,
    dollarTolerance: Int = 1,
    minChars: Int = 5
): List<AmortizationEntry> {
    return entries.filter { entry ->
        amountMatches(incoming.amount, entry.amount, percentTolerance, dollarTolerance) &&
        merchantMatches(incoming.source, entry.source, minChars)
    }.sortedBy { abs(incoming.amount - it.amount) }
}

/** Return all matching income sources, ranked by closest date then closest amount. */
fun findBudgetIncomeMatches(
    incoming: Transaction,
    incomeSources: List<IncomeSource>,
    minChars: Int = 5,
    dayWindow: Int = 7
): List<IncomeSource> {
    if (incoming.type != TransactionType.INCOME) return emptyList()
    return incomeSources.filter { source ->
        merchantMatches(incoming.source, source.source, minChars) &&
        dateNearOccurrence(
            incoming.date, source.repeatType, source.repeatInterval,
            source.startDate, source.monthDay1, source.monthDay2, dayWindow
        )
    }.sortedWith(compareBy<IncomeSource> {
        nearestOccurrenceDistance(incoming.date, it.repeatType, it.repeatInterval, it.startDate, it.monthDay1, it.monthDay2) ?: Long.MAX_VALUE
    }.thenBy { abs(incoming.amount - it.amount) })
}

/** Return all matching duplicates, ranked by closest amount then closest date. */
fun findDuplicates(
    incoming: Transaction,
    existing: List<Transaction>,
    percentTolerance: Double = 0.01,
    dollarTolerance: Int = 1,
    dayWindow: Int = 7,
    minChars: Int = 5
): List<Transaction> {
    return existing.filter { ex ->
        amountMatches(incoming.amount, ex.amount, percentTolerance, dollarTolerance) &&
        dateMatches(incoming, ex, dayWindow) &&
        merchantMatches(incoming.source, ex.source, minChars)
    }.sortedWith(compareBy<Transaction> {
        abs(incoming.amount - it.amount)
    }.thenBy { abs(ChronoUnit.DAYS.between(incoming.date, it.date)) })
}

internal fun amountMatches(
    a1: Double,
    a2: Double,
    percentTolerance: Double = 0.01,
    dollarTolerance: Int = 1
): Boolean {
    val maxVal = maxOf(abs(a1), abs(a2))
    if (maxVal == 0.0) return true
    val withinPercent = abs(a1 - a2) / maxVal <= percentTolerance
    val withinRounded = abs(a1.roundToLong() - a2.roundToLong()) <= dollarTolerance
    return withinPercent || withinRounded
}

private fun dateMatches(t1: Transaction, t2: Transaction, dayWindow: Int = 7): Boolean {
    val daysBetween = abs(ChronoUnit.DAYS.between(t1.date, t2.date))
    return daysBetween <= dayWindow
}

internal fun merchantMatches(s1: String, s2: String, minChars: Int = 5): Boolean {
    // Strip non-alphanumeric characters so "Wal-Mart"/"Walmart" and "O'Riley"/"ORiley" match
    val a = s1.lowercase().replace(Regex("[^a-z0-9]"), "")
    val b = s2.lowercase().replace(Regex("[^a-z0-9]"), "")
    if (a.length < minChars || b.length < minChars) {
        return a == b
    }
    val substrings = mutableSetOf<String>()
    for (i in 0..a.length - minChars) {
        substrings.add(a.substring(i, i + minChars))
    }
    for (i in 0..b.length - minChars) {
        if (b.substring(i, i + minChars) in substrings) return true
    }
    return false
}
