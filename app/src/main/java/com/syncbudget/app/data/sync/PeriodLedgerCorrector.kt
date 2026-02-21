package com.syncbudget.app.data.sync

import com.syncbudget.app.data.BudgetCalculator
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense

object PeriodLedgerCorrector {

    fun correctLedger(
        ledger: List<PeriodLedgerEntry>,
        allRecurringExpenses: List<RecurringExpense>,
        allIncomeSources: List<IncomeSource>,
        budgetPeriod: BudgetPeriod
    ): Double {
        var totalCorrection = 0.0

        for (entry in ledger) {
            if (entry.corrected) continue

            // Reconstruct which records existed at the time of this period reset
            // by checking which records had any clock value <= the clockAtReset
            val activeExpensesAtTime = allRecurringExpenses.filter { re ->
                !re.deleted && maxFieldClock(re) <= entry.clockAtReset
            }
            val activeIncomeAtTime = allIncomeSources.filter { src ->
                !src.deleted && maxFieldClock(src) <= entry.clockAtReset
            }

            // Compute what budgetAmount should have been at that point
            val correctSafeBudget = BudgetCalculator.calculateSafeBudgetAmount(
                activeIncomeAtTime, activeExpensesAtTime, budgetPeriod
            )

            // The difference between what was applied and what should have been applied
            val correction = correctSafeBudget - entry.appliedAmount
            totalCorrection += correction
        }

        return totalCorrection
    }

    private fun maxFieldClock(re: RecurringExpense): Long {
        return maxOf(
            re.source_clock, re.amount_clock, re.repeatType_clock,
            re.repeatInterval_clock, re.startDate_clock,
            re.monthDay1_clock, re.monthDay2_clock, re.deleted_clock
        )
    }

    private fun maxFieldClock(src: IncomeSource): Long {
        return maxOf(
            src.source_clock, src.amount_clock, src.repeatType_clock,
            src.repeatInterval_clock, src.startDate_clock,
            src.monthDay1_clock, src.monthDay2_clock, src.deleted_clock
        )
    }
}
