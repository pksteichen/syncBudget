package com.syncbudget.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.syncbudget.app.data.*
import com.syncbudget.app.widget.BudgetWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Background worker that syncs from Firestore, runs period refresh, updates
 * the widget, and pushes changes back to Firestore.
 *
 * Runs every 15 minutes via WorkManager. Handles full data sync, period
 * refresh, cash recomputation, and widget updates.
 * Skips if the app is active in the foreground (MainViewModel handles everything).
 */
class BackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BackgroundSyncWorker"
        private const val WORK_NAME = "period_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackgroundSyncWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        try {
            // Skip if the app is active — foreground handles sync + refresh
            if (com.syncbudget.app.MainActivity.isAppActive) {
                return Result.success()
            }

            // Ensure Firebase anonymous auth
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance()
                        .signInAnonymously()
                        .await()
                } catch (e: Exception) {
                    Log.w(TAG, "Anonymous auth failed: ${e.message}")
                    // Continue without sync — period refresh can still run from local data
                }
            }

            val syncPrefs = applicationContext.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
            val groupId = syncPrefs.getString("groupId", null)

            var mergeResult: SyncMergeProcessor.MergeResult? = null
            var encryptionKey: ByteArray? = null
            var deviceId: String? = null

            // ── Step 1: Sync from Firestore if configured ──
            if (groupId != null) {
                encryptionKey = GroupManager.getEncryptionKey(applicationContext)
                deviceId = SyncIdGenerator.getOrCreateDeviceId(applicationContext)

                if (encryptionKey != null) {
                    mergeResult = syncFromFirestore(groupId, encryptionKey, deviceId, syncPrefs)
                }
            }

            // ── Step 2: Apply synced settings to SharedPrefs (before period refresh reads them) ──
            if (mergeResult != null) {
                saveMergeResult(mergeResult, syncPrefs)
            }

            // ── Step 3: Run period refresh from (possibly updated) local data ──
            val appPrefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val refreshResult = runPeriodRefresh(appPrefs)

            // ── Step 3b: Always recompute cash from local data ──
            // PeriodRefreshService recomputes cash when periods are missed, but
            // when sync delivered new data without a period boundary (e.g., a
            // transaction from another device), we must still recompute so the
            // widget reflects the updated cash.
            if (refreshResult == null) {
                recomputeCashFromDisk(appPrefs)
            }

            // ── Step 4: Push changes to Firestore if sync configured ──
            if (groupId != null && encryptionKey != null && deviceId != null) {
                // Push period refresh results
                if (refreshResult != null) {
                    pushRefreshResults(refreshResult, groupId, encryptionKey, deviceId)
                }

                // Push sync side effects (conflicted transactions, category deletes)
                if (mergeResult != null) {
                    pushSyncSideEffects(mergeResult, groupId, encryptionKey, deviceId)
                }

            }

            // ── Step 5: RTDB lastSeen ping (device roster freshness) ──
            if (groupId != null && deviceId != null) {
                try {
                    val db = com.google.firebase.database.FirebaseDatabase.getInstance()
                    db.reference.child("groups/$groupId/presence/$deviceId/lastSeen")
                        .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                } catch (_: Exception) {}
            }

            // ── Step 6: Background receipt photo sync (paid users only) ──
            if (groupId != null && encryptionKey != null && deviceId != null) {
                try {
                    val appPrefsReceipt = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val photoCapable = appPrefsReceipt.getBoolean("isPaidUser", false) ||
                            appPrefsReceipt.getBoolean("isSubscriber", false)
                    if (photoCapable) {
                        val txns = TransactionRepository.load(applicationContext)
                        val devices = RealtimePresenceService.getDevices(groupId)
                        val receiptSync = ReceiptSyncManager(
                            applicationContext, groupId, deviceId, encryptionKey
                        ) { msg -> Log.i(TAG, "Receipt: $msg") }
                        receiptSync.syncReceipts(txns, devices)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Background receipt sync failed: ${e.message}")
                }
            }

            // ── Step 7: Update widget ──
            BudgetWidgetProvider.updateAllWidgets(applicationContext)

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "BackgroundSyncWorker failed: ${e.message}", e)
            return Result.success() // don't retry, wait for next scheduled run
        }
    }

    // ── Sync from Firestore via short-lived listeners ──────────────────

    private suspend fun syncFromFirestore(
        groupId: String,
        encryptionKey: ByteArray,
        deviceId: String,
        syncPrefs: android.content.SharedPreferences
    ): SyncMergeProcessor.MergeResult? {
        val docSync = FirestoreDocSync(applicationContext, groupId, deviceId, encryptionKey)

        val accumulatedEvents = java.util.Collections.synchronizedList(mutableListOf<DataChangeEvent>())

        docSync.onBatchChanged = { events ->
            accumulatedEvents.addAll(events)
        }

        try {
            docSync.startListeners()

            // Wait for all 8 collections to deliver their initial snapshot.
            // awaitInitialSync tracks via markCollectionDelivered() which fires
            // even for empty filtered results — no more 60s timeout waste.
            docSync.awaitInitialSync(60_000)

            docSync.stopListeners()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore sync failed: ${e.message}")
            try { docSync.stopListeners() } catch (_: Exception) {}
            return null
        }

        if (accumulatedEvents.isEmpty()) return null

        // Load current data from disk
        val transactions = TransactionRepository.load(applicationContext)
        val recurringExpenses = RecurringExpenseRepository.load(applicationContext)
        val incomeSources = IncomeSourceRepository.load(applicationContext)
        val savingsGoals = SavingsGoalRepository.load(applicationContext)
        val amortizationEntries = AmortizationRepository.load(applicationContext)
        val categories = CategoryRepository.load(applicationContext)
        val periodLedger = PeriodLedgerRepository.load(applicationContext)
        val sharedSettings = SharedSettingsRepository.load(applicationContext)

        // Build catIdRemap from sync_engine prefs
        val catIdRemap: MutableMap<Int, Int> = try {
            val remapJson = syncPrefs.getString("catIdRemap", null)
            if (remapJson != null) {
                val json = JSONObject(remapJson)
                json.keys().asSequence().associate {
                    it.toInt() to json.getInt(it)
                }.toMutableMap()
            } else mutableMapOf()
        } catch (_: Exception) { mutableMapOf() }

        // Get current budget start date
        val appPrefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentBudgetStartDate = appPrefs.getString("budgetStartDate", null)?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }

        // Process the accumulated events
        val result = withContext(Dispatchers.Default) {
            SyncMergeProcessor.processBatch(
                events = accumulatedEvents.toList(),
                currentTransactions = transactions,
                currentRecurringExpenses = recurringExpenses,
                currentIncomeSources = incomeSources,
                currentSavingsGoals = savingsGoals,
                currentAmortizationEntries = amortizationEntries,
                currentCategories = categories,
                currentPeriodLedger = periodLedger,
                currentSharedSettings = sharedSettings,
                catIdRemap = catIdRemap,
                currentBudgetStartDate = currentBudgetStartDate
            )
        }

        // Persist catIdRemap back if it was modified
        if (catIdRemap.isNotEmpty()) {
            syncPrefs.edit().putString(
                "catIdRemap",
                JSONObject(catIdRemap.mapKeys { it.key.toString() }).toString()
            ).apply()
        }

        return result
    }

    // ── Save merge result to disk ──────────────────────────────────────

    private fun saveMergeResult(
        result: SyncMergeProcessor.MergeResult,
        syncPrefs: android.content.SharedPreferences
    ) {
        result.transactions?.let { TransactionRepository.save(applicationContext, it) }
        result.recurringExpenses?.let { RecurringExpenseRepository.save(applicationContext, it) }
        result.incomeSources?.let { IncomeSourceRepository.save(applicationContext, it) }
        result.savingsGoals?.let { SavingsGoalRepository.save(applicationContext, it) }
        result.amortizationEntries?.let { AmortizationRepository.save(applicationContext, it) }
        result.categories?.let { CategoryRepository.save(applicationContext, it) }
        result.periodLedger?.let { PeriodLedgerRepository.save(applicationContext, it) }
        result.sharedSettings?.let { SharedSettingsRepository.save(applicationContext, it) }

        // Apply synced settings to SharedPreferences
        result.settingsPrefsToApply?.let { prefs ->
            val appPrefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val editor = appPrefs.edit()
            for ((key, value) in prefs) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    else -> editor.putString(key, value.toString())
                }
            }
            editor.apply()
        }
    }

    // ── Run period refresh ─────────────────────────────────────────────

    private fun runPeriodRefresh(
        appPrefs: android.content.SharedPreferences
    ): PeriodRefreshService.RefreshResult? {
        val budgetStartDate = appPrefs.getString("budgetStartDate", null)?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        } ?: return null

        val lastRefreshDate = appPrefs.getString("lastRefreshDate", null)?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        } ?: budgetStartDate

        val budgetPeriod = try {
            BudgetPeriod.valueOf(appPrefs.getString("budgetPeriod", null) ?: "DAILY")
        } catch (_: Exception) { BudgetPeriod.DAILY }

        val incomeMode = try {
            IncomeMode.valueOf(appPrefs.getString("incomeMode", null) ?: "FIXED")
        } catch (_: Exception) { IncomeMode.FIXED }

        val sharedSettings = SharedSettingsRepository.load(applicationContext)

        val config = PeriodRefreshService.RefreshConfig(
            budgetStartDate = budgetStartDate,
            lastRefreshDate = lastRefreshDate,
            budgetPeriod = budgetPeriod,
            resetHour = appPrefs.getInt("resetHour", 0),
            resetDayOfWeek = appPrefs.getInt("resetDayOfWeek", 1),
            resetDayOfMonth = appPrefs.getInt("resetDayOfMonth", 1),
            familyTimezone = sharedSettings.familyTimezone,
            localDeviceId = SyncIdGenerator.getOrCreateDeviceId(applicationContext),
            incomeMode = incomeMode,
            isManualBudgetEnabled = appPrefs.getBoolean("isManualBudgetEnabled", false),
            manualBudgetAmount = appPrefs.getString("manualBudgetAmount", "0.0")
                ?.toDoubleOrNull() ?: 0.0
        )

        return PeriodRefreshService.refreshIfNeeded(applicationContext, config)
    }

    // ── Recompute cash from disk (when period refresh was not needed) ──

    private fun recomputeCashFromDisk(appPrefs: android.content.SharedPreferences) {
        val budgetStartDate = appPrefs.getString("budgetStartDate", null)?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        } ?: return

        val incomeMode = try {
            IncomeMode.valueOf(appPrefs.getString("incomeMode", null) ?: "FIXED")
        } catch (_: Exception) { IncomeMode.FIXED }

        val periodLedger = PeriodLedgerRepository.load(applicationContext)
        val transactions = TransactionRepository.load(applicationContext)
        val recurringExpenses = RecurringExpenseRepository.load(applicationContext)
        val incomeSources = IncomeSourceRepository.load(applicationContext)

        val cash = BudgetCalculator.recomputeAvailableCash(
            budgetStartDate, periodLedger,
            transactions.active, recurringExpenses.active,
            incomeMode, incomeSources.active
        )

        val currentCash = appPrefs.getDoubleCompat("availableCash")
        if (cash != currentCash) {
            appPrefs.edit().putString("availableCash", cash.toString()).apply()
        }
    }

    // ── Push period refresh results to Firestore ───────────────────────

    private suspend fun pushRefreshResults(
        result: PeriodRefreshService.RefreshResult,
        groupId: String,
        encryptionKey: ByteArray,
        deviceId: String
    ) {
        withContext(Dispatchers.IO) {
            // Push new ledger entries (create-if-absent: first writer wins)
            for (entry in result.newLedgerEntries) {
                try {
                    FirestoreDocService.createDocIfAbsent(
                        groupId,
                        EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER,
                        entry.id.toString(),
                        EncryptedDocSerializer.toFieldMap(entry, encryptionKey, deviceId)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Push ledger entry failed: ${e.message}")
                }
            }

            // Push updated savings goals
            for (sg in result.updatedSavingsGoals) {
                try {
                    FirestoreDocService.updateFields(
                        groupId,
                        EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS,
                        sg.id.toString(),
                        EncryptedDocSerializer.fieldUpdate(
                            sg, setOf("totalSavedSoFar"), encryptionKey, deviceId
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Push savings goal failed: ${e.message}")
                }
            }

            // Push updated recurring expenses
            for (re in result.updatedRecurringExpenses) {
                try {
                    FirestoreDocService.updateFields(
                        groupId,
                        EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES,
                        re.id.toString(),
                        EncryptedDocSerializer.fieldUpdate(
                            re, setOf("setAsideSoFar", "isAccelerated"), encryptionKey, deviceId
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Push recurring expense failed: ${e.message}")
                }
            }
        }
    }

    // ── Push sync side effects (conflicts, category deletes) ───────────

    private suspend fun pushSyncSideEffects(
        result: SyncMergeProcessor.MergeResult,
        groupId: String,
        encryptionKey: ByteArray,
        deviceId: String
    ) {
        withContext(Dispatchers.IO) {
            // Delete remapped duplicate categories from Firestore
            for (catId in result.categoriesToDeleteFromFirestore) {
                try {
                    FirestoreDocService.deleteDoc(
                        groupId,
                        EncryptedDocSerializer.COLLECTION_CATEGORIES,
                        catId.toString()
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Delete remapped category failed: ${e.message}")
                }
            }

            // Push back conflicted transactions with isUserCategorized=false
            for (txn in result.conflictedTransactionsToPushBack) {
                try {
                    FirestoreDocService.updateFields(
                        groupId,
                        EncryptedDocSerializer.COLLECTION_TRANSACTIONS,
                        txn.id.toString(),
                        EncryptedDocSerializer.fieldUpdate(
                            txn, setOf("isUserCategorized"), encryptionKey, deviceId
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Push conflicted transaction failed: ${e.message}")
                }
            }
        }
    }
}
