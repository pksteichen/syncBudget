package com.syncbudget.app.data

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

        // Build a mutable pool of app amounts (rounded to cents)
        val appPool = appTxns.map { Math.round(it.amount * 100) }.toMutableList()
        val matched = mutableListOf<Transaction>()
        val unmatched = mutableListOf<Transaction>()

        for (txn in fileTxns) {
            val fileCents = Math.round(txn.amount * 100)
            val idx = appPool.indexOf(fileCents)
            if (idx >= 0) {
                appPool.removeAt(idx)
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

fun findRecurringExpenseMatch(
    incoming: Transaction,
    recurringExpenses: List<RecurringExpense>,
    percentTolerance: Float = 0.01f,
    dollarTolerance: Int = 1,
    minChars: Int = 5
): RecurringExpense? {
    return recurringExpenses.find { re ->
        amountMatches(incoming.amount, re.amount, percentTolerance, dollarTolerance) &&
        merchantMatches(incoming.source, re.source, minChars)
    }
}

fun findAmortizationMatch(
    incoming: Transaction,
    entries: List<AmortizationEntry>,
    percentTolerance: Float = 0.01f,
    dollarTolerance: Int = 1,
    minChars: Int = 5
): AmortizationEntry? {
    return entries.find { entry ->
        amountMatches(incoming.amount, entry.amount, percentTolerance, dollarTolerance) &&
        merchantMatches(incoming.source, entry.source, minChars)
    }
}

fun isRecurringDateCloseEnough(transactionDate: LocalDate, re: RecurringExpense): Boolean {
    val txDay = transactionDate.dayOfMonth
    return when (re.repeatType) {
        RepeatType.MONTHS -> {
            val expected = re.monthDay1 ?: return true
            abs(txDay - expected) <= 2
        }
        RepeatType.BI_MONTHLY -> {
            val d1 = re.monthDay1 ?: return true
            val d2 = re.monthDay2 ?: return true
            abs(txDay - d1) <= 2 || abs(txDay - d2) <= 2
        }
        else -> true
    }
}

fun findBudgetIncomeMatch(
    incoming: Transaction,
    incomeSources: List<IncomeSource>,
    minChars: Int = 5
): IncomeSource? {
    if (incoming.type != TransactionType.INCOME) return null
    return incomeSources.find { source ->
        merchantMatches(incoming.source, source.source, minChars)
    }
}

fun findDuplicate(
    incoming: Transaction,
    existing: List<Transaction>,
    percentTolerance: Float = 0.01f,
    dollarTolerance: Int = 1,
    dayWindow: Int = 7,
    minChars: Int = 5
): Transaction? {
    return existing.find { ex ->
        amountMatches(incoming.amount, ex.amount, percentTolerance, dollarTolerance) &&
        dateMatches(incoming, ex, dayWindow) &&
        merchantMatches(incoming.source, ex.source, minChars)
    }
}

internal fun amountMatches(
    a1: Double,
    a2: Double,
    percentTolerance: Float = 0.01f,
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
    val a = s1.lowercase()
    val b = s2.lowercase()
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
