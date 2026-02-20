package com.syncbudget.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SavingsGoal(
    val id: Int,
    val name: String,
    val targetAmount: Double,
    val targetDate: LocalDate? = null,
    val totalSavedSoFar: Double = 0.0,
    val contributionPerPeriod: Double = 0.0,
    val isPaused: Boolean = false
)

enum class SuperchargeMode { REDUCE_CONTRIBUTIONS, ACHIEVE_SOONER }

fun calculatePerPeriodDeduction(
    goal: SavingsGoal,
    budgetPeriod: BudgetPeriod
): Double {
    val remaining = goal.targetAmount - goal.totalSavedSoFar
    if (remaining <= 0) return 0.0
    if (goal.targetDate != null) {
        val today = LocalDate.now()
        if (!today.isBefore(goal.targetDate)) return remaining
        val periods = when (budgetPeriod) {
            BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(today, goal.targetDate)
            BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(today, goal.targetDate)
            BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(today, goal.targetDate)
        }
        if (periods <= 0) return remaining
        return remaining / periods.toDouble()
    } else {
        return minOf(goal.contributionPerPeriod, remaining)
    }
}

fun generateSavingsGoalId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
