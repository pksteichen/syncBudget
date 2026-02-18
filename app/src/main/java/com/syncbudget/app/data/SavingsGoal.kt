package com.syncbudget.app.data

data class SavingsGoal(
    val id: Int,
    val name: String,
    val targetAmount: Double,
    val savedSoFar: Double = 0.0,
    val contributionPerPeriod: Double = 0.0,
    val isPaused: Boolean = false
)

fun generateSavingsGoalId(existingIds: Set<Int>): Int {
    var id: Int
    do {
        id = (0..65535).random()
    } while (id in existingIds)
    return id
}
