package com.syncbudget.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

fun findRecurringExpenseMatch(
    incoming: Transaction,
    recurringExpenses: List<RecurringExpense>
): RecurringExpense? {
    return recurringExpenses.find { re ->
        amountMatches(incoming.amount, re.amount) &&
        merchantMatches(incoming.source, re.source)
    }
}

fun findAmortizationMatch(
    incoming: Transaction,
    entries: List<AmortizationEntry>
): AmortizationEntry? {
    return entries.find { entry ->
        amountMatches(incoming.amount, entry.amount) &&
        merchantMatches(incoming.source, entry.source)
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
    incomeSources: List<IncomeSource>
): IncomeSource? {
    if (incoming.type != TransactionType.INCOME) return null
    return incomeSources.find { source ->
        amountMatches(incoming.amount, source.amount) &&
        merchantMatches(incoming.source, source.source)
    }
}

fun findDuplicate(incoming: Transaction, existing: List<Transaction>): Transaction? {
    return existing.find { ex ->
        amountMatches(incoming.amount, ex.amount) &&
        dateMatches(incoming, ex) &&
        merchantMatches(incoming.source, ex.source)
    }
}

internal fun amountMatches(a1: Double, a2: Double): Boolean {
    val maxVal = maxOf(abs(a1), abs(a2))
    if (maxVal == 0.0) return true
    val withinPercent = abs(a1 - a2) / maxVal <= 0.01
    val withinRounded = abs(a1.roundToLong() - a2.roundToLong()) <= 1
    return withinPercent || withinRounded
}

private fun dateMatches(t1: Transaction, t2: Transaction): Boolean {
    val daysBetween = abs(ChronoUnit.DAYS.between(t1.date, t2.date))
    return daysBetween <= 7
}

internal fun merchantMatches(s1: String, s2: String): Boolean {
    val a = s1.lowercase()
    val b = s2.lowercase()
    if (a.length < 5 || b.length < 5) {
        return a == b
    }
    val substrings = mutableSetOf<String>()
    for (i in 0..a.length - 5) {
        substrings.add(a.substring(i, i + 5))
    }
    for (i in 0..b.length - 5) {
        if (b.substring(i, i + 5) in substrings) return true
    }
    return false
}
