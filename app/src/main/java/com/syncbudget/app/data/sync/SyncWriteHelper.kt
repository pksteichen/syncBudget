package com.syncbudget.app.data.sync

import android.util.Log
import com.syncbudget.app.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Convenience layer for pushing records to Firestore on local edits.
 *
 * Initialized when sync starts (listeners attached). Disposed on leave.
 * All pushes are fire-and-forget on IO dispatcher — Firestore SDK handles
 * offline queue and retry internally.
 */
object SyncWriteHelper {

    private const val TAG = "SyncWriteHelper"
    private var docSync: FirestoreDocSync? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun initialize(docSync: FirestoreDocSync) {
        this.docSync = docSync
        Log.i(TAG, "Initialized")
    }

    fun dispose() {
        scope.coroutineContext[Job]?.cancelChildren()
        docSync = null
        Log.i(TAG, "Disposed")
    }

    fun isInitialized(): Boolean = docSync != null

    // ── push individual records ─────────────────────────────────────────

    fun pushTransaction(txn: Transaction) = push(txn)
    fun pushRecurringExpense(re: RecurringExpense) = push(re)
    fun pushIncomeSource(src: IncomeSource) = push(src)
    fun pushSavingsGoal(sg: SavingsGoal) = push(sg)
    fun pushAmortizationEntry(ae: AmortizationEntry) = push(ae)
    fun pushCategory(cat: Category) = push(cat)
    fun pushPeriodLedgerEntry(ple: PeriodLedgerEntry) = push(ple)
    fun pushSharedSettings(ss: SharedSettings) = push(ss)

    // ── push lists (for bulk operations) ────────────────────────────────

    fun pushTransactions(txns: List<Transaction>) = txns.forEach { push(it) }
    fun pushCategories(cats: List<Category>) = cats.forEach { push(it) }

    // ── internal ────────────────────────────────────────────────────────

    private fun push(record: Any) {
        val sync = docSync ?: return
        scope.launch {
            try {
                sync.pushRecord(record)
            } catch (e: Exception) {
                Log.e(TAG, "Push failed for ${record::class.simpleName}", e)
                // Firestore SDK will retry automatically for network errors.
                // For encryption or serialization errors, log and move on.
            }
        }
    }
}
