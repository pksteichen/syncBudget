package com.syncbudget.app.data.sync

import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.IncomeMode
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SharedSettings
import com.syncbudget.app.data.Transaction
import java.time.LocalDate

/**
 * Shared merge processor for incoming Firestore sync events.
 *
 * Extracted from MainActivity's onBatchChanged callback so that both the
 * foreground (MainActivity) and background (PeriodRefreshWorker) can process
 * incoming DataChangeEvent batches identically.
 *
 * This object is stateless — all mutable state is passed in and returned via
 * [MergeResult]. Side effects (Firestore deletes, SharedPrefs writes, cash
 * recompute) are left to the caller.
 */
object SyncMergeProcessor {

    /**
     * Result of processing a batch of sync events.
     *
     * Collection lists are null when that collection was not touched by any
     * event in the batch — callers should only persist non-null snapshots.
     */
    data class MergeResult(
        val transactions: List<Transaction>?,
        val recurringExpenses: List<RecurringExpense>?,
        val incomeSources: List<IncomeSource>?,
        val savingsGoals: List<SavingsGoal>?,
        val amortizationEntries: List<AmortizationEntry>?,
        val categories: List<Category>?,
        val periodLedger: List<PeriodLedgerEntry>?,
        val sharedSettings: SharedSettings?,
        val conflictDetected: Boolean,
        /** Transactions that had conflict flags set — caller should push these back. */
        val conflictedTransactionsToPushBack: List<Transaction>,
        /** Category IDs that were remapped (duplicates) — caller should delete from Firestore. */
        val categoriesToDeleteFromFirestore: List<Int>,
        /** Pref key→value map for SharedPreferences. Null if no settings event arrived. */
        val settingsPrefsToApply: Map<String, Any>?
    )

    /**
     * Process a batch of [DataChangeEvent]s against the current local state.
     *
     * Works on mutable copies of every input list — originals are never mutated.
     *
     * @param events                   Batch of events from FirestoreDocSync listener.
     * @param currentTransactions      Current in-memory transactions.
     * @param currentRecurringExpenses Current in-memory recurring expenses.
     * @param currentIncomeSources     Current in-memory income sources.
     * @param currentSavingsGoals      Current in-memory savings goals.
     * @param currentAmortizationEntries Current in-memory amortization entries.
     * @param currentCategories        Current in-memory categories.
     * @param currentPeriodLedger      Current in-memory period ledger entries.
     * @param currentSharedSettings    Current in-memory shared settings.
     * @param catIdRemap               Mutable remap of remote→local category IDs.
     *                                 Mutated in place — caller persists this across calls.
     * @param currentBudgetStartDate   Current budget start date (for change detection).
     *
     * @return [MergeResult] with updated lists and side-effect metadata.
     */
    fun processBatch(
        events: List<DataChangeEvent>,
        currentTransactions: List<Transaction>,
        currentRecurringExpenses: List<RecurringExpense>,
        currentIncomeSources: List<IncomeSource>,
        currentSavingsGoals: List<SavingsGoal>,
        currentAmortizationEntries: List<AmortizationEntry>,
        currentCategories: List<Category>,
        currentPeriodLedger: List<PeriodLedgerEntry>,
        currentSharedSettings: SharedSettings,
        catIdRemap: MutableMap<Int, Int>,
        currentBudgetStartDate: LocalDate?
    ): MergeResult {

        // --- Work on mutable copies so originals are never touched ---
        val transactions = currentTransactions.toMutableList()
        val recurringExpenses = currentRecurringExpenses.toMutableList()
        val incomeSources = currentIncomeSources.toMutableList()
        val savingsGoals = currentSavingsGoals.toMutableList()
        val amortizationEntries = currentAmortizationEntries.toMutableList()
        val categories = currentCategories.toMutableList()
        val periodLedger = currentPeriodLedger.toMutableList()
        var mergedSettings: SharedSettings? = null

        // Track which collections were actually modified
        val changedCollections = mutableSetOf<String>()
        var conflictDetected = false
        val conflictedTransactionsToPushBack = mutableListOf<Transaction>()
        val categoriesToDeleteFromFirestore = mutableListOf<Int>()
        var settingsPrefsToApply: MutableMap<String, Any>? = null

        // --- Process each event ---
        for (event in events) {
            changedCollections.add(event.collection)

            when (event.collection) {

                // ── TRANSACTIONS ────────────────────────────────────────
                EncryptedDocSerializer.COLLECTION_TRANSACTIONS -> {
                    var txn = event.record as Transaction

                    // Conflict: another device edited while we had pending edits.
                    // Mark as unverified so the user can review.
                    if (event.isConflict) {
                        txn = txn.copy(isUserCategorized = false)
                        conflictDetected = true
                    }

                    val idx = transactions.indexOfFirst { it.id == txn.id }
                    if (idx >= 0) transactions[idx] = txn else transactions.add(txn)

                    // Push conflict flag back so other devices also see unverified
                    if (event.isConflict) {
                        conflictedTransactionsToPushBack.add(txn)
                    }
                }

                // ── RECURRING EXPENSES ──────────────────────────────────
                EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES -> {
                    val re = event.record as RecurringExpense
                    val idx = recurringExpenses.indexOfFirst { it.id == re.id }
                    if (idx >= 0) recurringExpenses[idx] = re else recurringExpenses.add(re)
                }

                // ── INCOME SOURCES ──────────────────────────────────────
                EncryptedDocSerializer.COLLECTION_INCOME_SOURCES -> {
                    val src = event.record as IncomeSource
                    val idx = incomeSources.indexOfFirst { it.id == src.id }
                    if (idx >= 0) incomeSources[idx] = src else incomeSources.add(src)
                }

                // ── SAVINGS GOALS ───────────────────────────────────────
                EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS -> {
                    val sg = event.record as SavingsGoal
                    val idx = savingsGoals.indexOfFirst { it.id == sg.id }
                    if (idx >= 0) savingsGoals[idx] = sg else savingsGoals.add(sg)
                }

                // ── AMORTIZATION ENTRIES ────────────────────────────────
                EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES -> {
                    val ae = event.record as AmortizationEntry
                    val idx = amortizationEntries.indexOfFirst { it.id == ae.id }
                    if (idx >= 0) amortizationEntries[idx] = ae else amortizationEntries.add(ae)
                }

                // ── CATEGORIES (tag-based dedup) ────────────────────────
                EncryptedDocSerializer.COLLECTION_CATEGORIES -> {
                    val cat = event.record as Category

                    // Tag-based dedup: if a local category already owns this tag,
                    // remap the remote category's ID to the local one and delete
                    // the remote duplicate from Firestore.
                    val localMatch = if (cat.tag.isNotEmpty()) {
                        categories.firstOrNull {
                            it.tag == cat.tag && it.id != cat.id && !it.deleted
                        }
                    } else null

                    if (localMatch != null) {
                        // Record the remap so it persists across batches
                        catIdRemap[cat.id] = localMatch.id

                        // Remap ALL transaction categoryAmounts that reference the
                        // remote category's ID to the local match's ID
                        for (i in transactions.indices) {
                            val t = transactions[i]
                            val newCats = t.categoryAmounts.map { ca ->
                                if (ca.categoryId == cat.id) ca.copy(categoryId = localMatch.id) else ca
                            }
                            if (newCats != t.categoryAmounts) {
                                transactions[i] = t.copy(categoryAmounts = newCats)
                                changedCollections.add(EncryptedDocSerializer.COLLECTION_TRANSACTIONS)
                            }
                        }

                        // Mark this duplicate category for Firestore deletion by caller
                        categoriesToDeleteFromFirestore.add(cat.id)
                    } else {
                        // Normal add-or-replace
                        val idx = categories.indexOfFirst { it.id == cat.id }
                        if (idx >= 0) categories[idx] = cat else categories.add(cat)
                    }
                }

                // ── PERIOD LEDGER ───────────────────────────────────────
                EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER -> {
                    val ple = event.record as PeriodLedgerEntry
                    val idx = periodLedger.indexOfFirst { it.id == ple.id }
                    if (idx >= 0) periodLedger[idx] = ple else periodLedger.add(ple)
                }

                // ── SHARED SETTINGS ─────────────────────────────────────
                EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS -> {
                    val merged = event.record as SharedSettings
                    mergedSettings = merged

                    // Build the prefs map that the caller will apply to SharedPreferences
                    val prefs = mutableMapOf<String, Any>(
                        "currencySymbol" to merged.currency,
                        "budgetPeriod" to merged.budgetPeriod,
                        "resetHour" to merged.resetHour,
                        "resetDayOfWeek" to merged.resetDayOfWeek,
                        "resetDayOfMonth" to merged.resetDayOfMonth,
                        "isManualBudgetEnabled" to merged.isManualBudgetEnabled,
                        "manualBudgetAmount" to merged.manualBudgetAmount.toString(),
                        "weekStartSunday" to merged.weekStartSunday,
                        "matchDays" to merged.matchDays,
                        "matchPercent" to merged.matchPercent.toString(),
                        "matchDollar" to merged.matchDollar,
                        "matchChars" to merged.matchChars,
                        "incomeMode" to merged.incomeMode
                    )

                    // Detect budgetStartDate change — if it differs, include it
                    // along with a refreshed lastRefreshDate (today) so the caller
                    // can write both in one prefs commit.
                    val syncedStartDate = merged.budgetStartDate?.let {
                        try { LocalDate.parse(it) } catch (_: Exception) { null }
                    }
                    if (syncedStartDate != null && syncedStartDate != currentBudgetStartDate) {
                        prefs["budgetStartDate"] = syncedStartDate.toString()
                        prefs["lastRefreshDate"] = LocalDate.now().toString()
                    }

                    settingsPrefsToApply = prefs
                }
            }
        }

        // --- Build result: null for unchanged collections ---
        return MergeResult(
            transactions = if (EncryptedDocSerializer.COLLECTION_TRANSACTIONS in changedCollections)
                transactions.toList() else null,
            recurringExpenses = if (EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES in changedCollections)
                recurringExpenses.toList() else null,
            incomeSources = if (EncryptedDocSerializer.COLLECTION_INCOME_SOURCES in changedCollections)
                incomeSources.toList() else null,
            savingsGoals = if (EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS in changedCollections)
                savingsGoals.toList() else null,
            amortizationEntries = if (EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES in changedCollections)
                amortizationEntries.toList() else null,
            categories = if (EncryptedDocSerializer.COLLECTION_CATEGORIES in changedCollections)
                categories.toList() else null,
            periodLedger = if (EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER in changedCollections)
                periodLedger.toList() else null,
            sharedSettings = mergedSettings,
            conflictDetected = conflictDetected,
            conflictedTransactionsToPushBack = conflictedTransactionsToPushBack.toList(),
            categoriesToDeleteFromFirestore = categoriesToDeleteFromFirestore.toList(),
            settingsPrefsToApply = settingsPrefsToApply?.toMap()
        )
    }
}
