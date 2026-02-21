@file:JvmName("SyncFilters")
package com.syncbudget.app.data.sync

import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.Transaction

@get:JvmName("activeTransactions")
val List<Transaction>.active: List<Transaction>
    get() = filter { !it.deleted }

@get:JvmName("activeRecurringExpenses")
val List<RecurringExpense>.active: List<RecurringExpense>
    get() = filter { !it.deleted }

@get:JvmName("activeIncomeSources")
val List<IncomeSource>.active: List<IncomeSource>
    get() = filter { !it.deleted }

@get:JvmName("activeSavingsGoals")
val List<SavingsGoal>.active: List<SavingsGoal>
    get() = filter { !it.deleted }

@get:JvmName("activeAmortizationEntries")
val List<AmortizationEntry>.active: List<AmortizationEntry>
    get() = filter { !it.deleted }

@get:JvmName("activeCategories")
val List<Category>.active: List<Category>
    get() = filter { !it.deleted }
