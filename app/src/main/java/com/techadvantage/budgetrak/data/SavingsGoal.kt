package com.techadvantage.budgetrak.data

data class SavingsGoal(
    val id: Int,
    val name: String,
    val targetAmount: Double,
    val totalSavedSoFar: Double = 0.0,
    val contributionPerPeriod: Double = 0.0,
    val isPaused: Boolean = false,
    // Sync fields
    val deviceId: String = "",
    val deleted: Boolean = false
)

enum class SuperchargeMode { REDUCE_CONTRIBUTIONS, ACHIEVE_SOONER }

fun calculatePerPeriodDeduction(goal: SavingsGoal): Double {
    val remaining = goal.targetAmount - goal.totalSavedSoFar
    if (remaining <= 0) return 0.0
    return minOf(goal.contributionPerPeriod, remaining)
}

fun generateSavingsGoalId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (1..Int.MAX_VALUE).random()
    } while (id in existingIds)
    return id
}
