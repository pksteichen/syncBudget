@file:JvmName("SyncFilters")
package com.syncbudget.app.data.sync

import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.Transaction

// Skeleton records are incomplete CRDT records (e.g. source_clock==0 or amount_clock==0)
// accepted during sync for delivery-guarantee but missing critical field values.
// If the completing delta never arrives, skeletons persist with $0.00 amounts or empty
// names, corrupting budget calculations. We keep them stored for CRDT consistency but
// filter them out of .active so they never reach the UI or budget logic.

@get:JvmName("activeTransactions")
val List<Transaction>.active: List<Transaction>
    get() = filter {
        !it.deleted &&
        it.source_clock != 0L &&
        it.amount_clock != 0L &&
        it.source.isNotEmpty()
    }

@get:JvmName("activeRecurringExpenses")
val List<RecurringExpense>.active: List<RecurringExpense>
    get() = filter {
        !it.deleted &&
        it.source_clock != 0L &&
        it.amount_clock != 0L &&
        it.source.isNotEmpty()
    }

@get:JvmName("activeIncomeSources")
val List<IncomeSource>.active: List<IncomeSource>
    get() = filter {
        !it.deleted &&
        it.source_clock != 0L &&
        it.amount_clock != 0L &&
        it.source.isNotEmpty()
    }

@get:JvmName("activeSavingsGoals")
val List<SavingsGoal>.active: List<SavingsGoal>
    get() = filter {
        !it.deleted &&
        it.name_clock != 0L &&
        it.name.isNotEmpty()
    }

@get:JvmName("activeAmortizationEntries")
val List<AmortizationEntry>.active: List<AmortizationEntry>
    get() = filter {
        !it.deleted &&
        it.source_clock != 0L &&
        it.amount_clock != 0L &&
        it.source.isNotEmpty()
    }

// Categories are created locally, never received as skeletons.
// Only filter on deleted + non-empty name (no clock check).
// Solo users who never synced have name_clock=0 on all categories.
@get:JvmName("activeCategories")
val List<Category>.active: List<Category>
    get() = filter {
        !it.deleted &&
        it.name.isNotEmpty()
    }
