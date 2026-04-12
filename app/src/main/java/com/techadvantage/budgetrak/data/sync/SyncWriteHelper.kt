package com.techadvantage.budgetrak.data.sync

import android.util.Log
import com.techadvantage.budgetrak.data.*
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
    private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

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

    // ── push lists (batched with chunking, fallback to individual) ─────

    fun pushTransactions(txns: List<Transaction>) = pushBatch(txns)
    fun pushCategories(cats: List<Category>) = pushBatch(cats)

    /** Batch push records. Retries once, then falls back to individual pushes. */
    fun pushBatch(records: List<Any>) {
        if (records.isEmpty()) return
        val sync = docSync ?: return
        scope.launch {
            try {
                sync.pushRecordsBatch(records)
            } catch (e: Exception) {
                Log.w(TAG, "Batch push failed (${records.size} records), retrying once: ${e.message}")
                try {
                    sync.pushRecordsBatch(records)
                } catch (e2: Exception) {
                    Log.w(TAG, "Batch retry failed, falling back to individual pushes: ${e2.message}")
                    for (record in records) {
                        try { sync.pushRecord(record) } catch (e3: Exception) {
                            Log.e(TAG, "Individual push failed: ${e3.message}")
                        }
                    }
                }
            }
        }
    }

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
