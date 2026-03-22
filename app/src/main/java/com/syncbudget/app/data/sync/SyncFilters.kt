@file:JvmName("SyncFilters")
package com.syncbudget.app.data.sync

import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.Transaction

// Skeleton records are incomplete CRDT records received via sync with
// missing critical field VALUES (empty source, empty name).  We filter
// on data content, NOT on clock values — solo users who never synced
// have clock=0 on all records, but their data is valid.

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
