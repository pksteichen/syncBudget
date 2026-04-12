package com.techadvantage.budgetrak.data.sync

import com.techadvantage.budgetrak.data.AmortizationEntry
import com.techadvantage.budgetrak.data.BudgetPeriod
import com.techadvantage.budgetrak.data.Category
import com.techadvantage.budgetrak.data.IncomeMode
import com.techadvantage.budgetrak.data.IncomeSource
import com.techadvantage.budgetrak.data.RecurringExpense
import com.techadvantage.budgetrak.data.SavingsGoal
import com.techadvantage.budgetrak.data.SharedSettings
import com.techadvantage.budgetrak.data.Transaction
import java.time.LocalDate

/**
 * Shared merge processor for incoming Firestore sync events.
 *
 * Extracted from MainActivity's onBatchChanged callback so that both the
 * foreground (MainViewModel) and background (BackgroundSyncWorker) can process
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
        val settingsPrefsToApply: Map<String, Any>?,
        /** Transactions that belong in the archive (date before archiveCutoffDate). */
        val archivedIncoming: List<Transaction> = emptyList()
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
        currentBudgetStartDate: LocalDate?,
        archiveCutoffDate: LocalDate? = null
    ): MergeResult {

        // --- Work on mutable copies so originals are never touched ---
        val transactions = currentTransactions.toMutableList()
        val recurringExpenses = currentRecurringExpenses.toMutableList()
        val incomeSources = currentIncomeSources.toMutableList()
        val savingsGoals = currentSavingsGoals.toMutableList()
        val amortizationEntries = currentAmortizationEntries.toMutableList()
        val categories = currentCategories.toMutableList()
        val periodLedger = currentPeriodLedger.toMutableList()

        // Pre-build ID→index maps for O(1) lookup during event processing
        val txnIndex = HashMap<Int, Int>(transactions.size * 2).also {
            for (i in transactions.indices) it[transactions[i].id] = i
        }
        val reIndex = HashMap<Int, Int>(recurringExpenses.size * 2).also {
            for (i in recurringExpenses.indices) it[recurringExpenses[i].id] = i
        }
        val isIndex = HashMap<Int, Int>(incomeSources.size * 2).also {
            for (i in incomeSources.indices) it[incomeSources[i].id] = i
        }
        val sgIndex = HashMap<Int, Int>(savingsGoals.size * 2).also {
            for (i in savingsGoals.indices) it[savingsGoals[i].id] = i
        }
        val aeIndex = HashMap<Int, Int>(amortizationEntries.size * 2).also {
            for (i in amortizationEntries.indices) it[amortizationEntries[i].id] = i
        }
        val catIndex = HashMap<Int, Int>(categories.size * 2).also {
            for (i in categories.indices) it[categories[i].id] = i
        }
        val pleIndex = HashMap<Int, Int>(periodLedger.size * 2).also {
            for (i in periodLedger.indices) it[periodLedger[i].id] = i
        }
        var mergedSettings: SharedSettings? = null

        // Track which collections were actually modified
        val changedCollections = mutableSetOf<String>()
        var conflictDetected = false
        val conflictedTransactionsToPushBack = mutableListOf<Transaction>()
        val categoriesToDeleteFromFirestore = mutableListOf<Int>()
        val archivedIncoming = mutableListOf<Transaction>()
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

                    // Route pre-cutoff transactions to archive instead of active list
                    if (archiveCutoffDate != null && txn.date.isBefore(archiveCutoffDate)) {
                        archivedIncoming.add(txn)
                    } else {
                        val idx = txnIndex[txn.id]
                        if (idx != null) transactions[idx] = txn
                        else { txnIndex[txn.id] = transactions.size; transactions.add(txn) }
                    }

                    // Push conflict flag back so other devices also see unverified
                    if (event.isConflict) {
                        conflictedTransactionsToPushBack.add(txn)
                    }
                }

                // ── RECURRING EXPENSES ──────────────────────────────────
                EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES -> {
                    val re = event.record as RecurringExpense
                    val idx = reIndex[re.id]
                    if (idx != null) recurringExpenses[idx] = re
                    else { reIndex[re.id] = recurringExpenses.size; recurringExpenses.add(re) }
                }

                // ── INCOME SOURCES ──────────────────────────────────────
                EncryptedDocSerializer.COLLECTION_INCOME_SOURCES -> {
                    val src = event.record as IncomeSource
                    val idx = isIndex[src.id]
                    if (idx != null) incomeSources[idx] = src
                    else { isIndex[src.id] = incomeSources.size; incomeSources.add(src) }
                }

                // ── SAVINGS GOALS ───────────────────────────────────────
                EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS -> {
                    val sg = event.record as SavingsGoal
                    val idx = sgIndex[sg.id]
                    if (idx != null) savingsGoals[idx] = sg
                    else { sgIndex[sg.id] = savingsGoals.size; savingsGoals.add(sg) }
                }

                // ── AMORTIZATION ENTRIES ────────────────────────────────
                EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES -> {
                    val ae = event.record as AmortizationEntry
                    val idx = aeIndex[ae.id]
                    if (idx != null) amortizationEntries[idx] = ae
                    else { aeIndex[ae.id] = amortizationEntries.size; amortizationEntries.add(ae) }
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
                        val idx = catIndex[cat.id]
                        if (idx != null) categories[idx] = cat
                        else { catIndex[cat.id] = categories.size; categories.add(cat) }
                    }
                }

                // ── PERIOD LEDGER ───────────────────────────────────────
                EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER -> {
                    val ple = event.record as PeriodLedgerEntry
                    val idx = pleIndex[ple.id]
                    if (idx != null) periodLedger[idx] = ple
                    else { pleIndex[ple.id] = periodLedger.size; periodLedger.add(ple) }
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
            settingsPrefsToApply = settingsPrefsToApply?.toMap(),
            archivedIncoming = archivedIncoming.toList()
        )
    }
}
