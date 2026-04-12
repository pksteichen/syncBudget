@file:JvmName("SyncFilters")
package com.techadvantage.budgetrak.data.sync

import com.techadvantage.budgetrak.data.AmortizationEntry
import com.techadvantage.budgetrak.data.Category
import com.techadvantage.budgetrak.data.IncomeSource
import com.techadvantage.budgetrak.data.RecurringExpense
import com.techadvantage.budgetrak.data.SavingsGoal
import com.techadvantage.budgetrak.data.Transaction

// Skeleton records are incomplete records received via sync with
// missing critical field values (empty source, empty name).
// We filter on data content — records with valid data are always kept.

@get:JvmName("activeTransactions")
val List<Transaction>.active: List<Transaction>
    get() = filter { !it.deleted && it.source.isNotEmpty() }

@get:JvmName("activeRecurringExpenses")
val List<RecurringExpense>.active: List<RecurringExpense>
    get() = filter { !it.deleted && it.source.isNotEmpty() }

@get:JvmName("activeIncomeSources")
val List<IncomeSource>.active: List<IncomeSource>
    get() = filter { !it.deleted && it.source.isNotEmpty() }

@get:JvmName("activeSavingsGoals")
val List<SavingsGoal>.active: List<SavingsGoal>
    get() = filter { !it.deleted && it.name.isNotEmpty() }

@get:JvmName("activeAmortizationEntries")
val List<AmortizationEntry>.active: List<AmortizationEntry>
    get() = filter { !it.deleted && it.source.isNotEmpty() }

@get:JvmName("activeCategories")
val List<Category>.active: List<Category>
    get() = filter { !it.deleted && it.name.isNotEmpty() }
