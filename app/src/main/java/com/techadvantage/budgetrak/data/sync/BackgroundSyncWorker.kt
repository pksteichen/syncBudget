package com.techadvantage.budgetrak.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.techadvantage.budgetrak.data.*
import com.techadvantage.budgetrak.widget.BudgetWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
        private const val ONESHOT_WORK_NAME = "period_refresh_oneshot"

        // Guard against double-fire: the periodic worker and the FCM-triggered
        // one-shot use different unique work names (WORK_NAME vs ONESHOT_WORK_NAME),
        // so WorkManager's KEEP policy doesn't dedupe between them. If a periodic
        // run is already executing when an FCM arrives, WorkManager happily starts
        // the one-shot in parallel — duplicating listeners, RTDB pings, and reads.
        // This in-process flag catches that case.
        //
        // Also read by FcmService.handleWakeForSync() to keep the FCM thread alive
        // while the worker runs in the same process (without this, onMessageReceived
        // returns, Android kills the process, and the enqueued worker gets deferred
        // indefinitely under Doze/App Standby).
        internal val isRunning = AtomicBoolean(false)

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /**
         * Fire a one-shot run immediately. On Android 12+ (API 31+) this uses
         * an expedited request so it bypasses Doze / App-Standby buckets for
         * the run's ~10 min window, which is essential on OEMs (Samsung,
         * Xiaomi, etc.) that aggressively defer regular WorkManager jobs. The
         * OutOfQuotaPolicy fallback lets it run as a normal OneTimeWorkRequest
         * if the expedited quota is exhausted.
         *
         * Pre-S skips expedited since that would require a foreground-service
         * notification. Older OS versions get the regular one-shot.
         *
         * Uses `enqueueUniqueWork(ONESHOT_WORK_NAME, KEEP, …)` so FCM bursts
         * (e.g. 500 txns imported on another device triggering 500 sync_push
         * messages) collapse into a single actual sync run. If a one-shot is
         * already pending or running, subsequent enqueues are dropped.
         */
        fun runOnce(context: Context) {
            val builder = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_WORK_NAME, ExistingWorkPolicy.KEEP, builder.build()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (!isRunning.compareAndSet(false, true)) {
            com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("Worker skipped: another run already in progress")
            return Result.success()
        }
        try {
            // Skip if the app is visible — foreground handles everything
            if (com.techadvantage.budgetrak.MainActivity.isAppActive) {
                return Result.success()
            }

            // If foreground ViewModel is alive (stopped but in memory), only check listener health
            val vm = com.techadvantage.budgetrak.MainViewModel.instance?.get()
            if (vm != null) {
                com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("Worker Tier 2: ViewModel alive, sync=${vm.isSyncConfigured}")
                if (vm.isSyncConfigured) {
                    // Proactively refresh App Check token before it expires.
                    // getAppCheckToken(false) returns cached token even if about to expire.
                    // Check expiry and force-refresh if within 5 min of expiration.
                    try {
                        val token = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                            com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                                .getAppCheckToken(false).await()
                        }
                        if (token != null) {
                            val remainingMs = token.expireTimeMillis - System.currentTimeMillis()
                            if (remainingMs < 35 * 60 * 1000L) {
                                val refreshed = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                                    com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                                        .getAppCheckToken(true).await()
                                }
                                if (refreshed != null) {
                                    com.techadvantage.budgetrak.BudgeTrakApplication.tokenLog(
                                        "Proactive token refresh (Worker Tier 2): was ${remainingMs/1000}s from expiry"
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    val ds = vm.docSync
                    if (ds != null && !ds.isListening) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            ds.startListeners()
                        }
                        Log.i(TAG, "Restarted dead foreground listeners")
                        com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("Worker Tier 2: restarted dead foreground listeners")
                    }

                    // Ping RTDB lastSeen so device roster stays fresh
                    pingRtdbLastSeen(applicationContext)

                    // Receipt sync (paid / subscriber). Cheap no-op if there's
                    // nothing to do: pending queue empty, flag clock unchanged,
                    // no missing referenced receipts. The foreground drainers
                    // on viewModelScope may not be firing reliably while
                    // backgrounded (Doze / CPU scheduling), so this is a
                    // periodic catch-up.
                    if (vm.isPaidUser || vm.isSubscriber) {
                        // Skip if foreground receipt-sync coroutines are
                        // already doing this work — they hold the same
                        // semantics (per-receipt queue atomicity, same
                        // retry-counter prefs). Tier 2 is a backstop for when
                        // Doze / CPU scheduling stalls the fg coroutines,
                        // not a parallel worker.
                        if (vm.isReceiptSyncActive()) {
                            Log.i(TAG, "Tier 2 receipt sync skipped: foreground drainer/retry active")
                        } else try {
                            // Snapshot all VM state at entry so a concurrent
                            // subscription / group change can't produce torn
                            // reads mid-sync.
                            val gid = vm.syncGroupId
                            val deviceId = vm.localDeviceId
                            val txns = vm.transactions.toList()
                            val key = GroupManager.getEncryptionKey(applicationContext)
                            if (gid != null && key != null && deviceId.isNotBlank()) {
                                val devices = RealtimePresenceService.getDevices(gid)
                                val receiptSync = ReceiptSyncManager(
                                    applicationContext, gid, deviceId, key
                                ) { msg -> Log.i(TAG, "Receipt (Tier 2): $msg") }
                                receiptSync.syncReceipts(txns, devices)
                            } else {
                                Log.i(TAG, "Tier 2 receipt sync skipped: gid=${gid != null} key=${key != null} device=${deviceId.isNotBlank()}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Tier 2 receipt sync failed: ${e.message}")
                        }
                    }
                }

                return Result.success()
            }

            // ViewModel is dead (process restarted) — run full background sync
            com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("Worker Tier 3: ViewModel dead, full sync")

            val syncPrefs = applicationContext.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
            val groupId = syncPrefs.getString("groupId", null)

            // Only sign in to Firebase when sync is configured — solo users
            // don't need auth and this saves a network round-trip every 15 min.
            if (groupId != null && com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance()
                        .signInAnonymously()
                        .await()
                } catch (e: Exception) {
                    Log.w(TAG, "Anonymous auth failed: ${e.message}")
                    // Continue without sync — period refresh can still run from local data
                }
            }

            // Proactive App Check token refresh — same logic as Tier 2 but essential
            // here because the ViewModel (and its keep-alive loop) is dead. Without this,
            // all Firestore network reads and RTDB writes fail with PERMISSION_DENIED.
            if (groupId != null) {
                try {
                    val token = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                        com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                            .getAppCheckToken(false).await()
                    }
                    if (token != null) {
                        val remainingMs = token.expireTimeMillis - System.currentTimeMillis()
                        if (remainingMs < 35 * 60 * 1000L) {
                            val refreshed = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                                com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                                    .getAppCheckToken(true).await()
                            }
                            if (refreshed != null) {
                                com.techadvantage.budgetrak.BudgeTrakApplication.tokenLog(
                                    "Proactive token refresh (Worker Tier 3): was ${remainingMs/1000}s from expiry"
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

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
                    // Persist pushed doc keys so the next worker's FirestoreDocSync
                    // can filter echoes of our own writes via recentPushes + lastEditBy.
                    persistBackgroundPushKeys(refreshResult)
                }

                // Push sync side effects (conflicted transactions, category deletes)
                if (mergeResult != null) {
                    pushSyncSideEffects(mergeResult, groupId, encryptionKey, deviceId)
                }

            }

            // ── Step 4b: Recheck consistency mismatch if flagged ──
            if (groupId != null) {
                val mismatchAt = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getLong("checksumMismatchAt", 0L)
                if (mismatchAt > 0 && System.currentTimeMillis() - mismatchAt > 60 * 60 * 1000L) {
                    val vm = com.techadvantage.budgetrak.MainViewModel.instance?.get()
                    if (vm != null) {
                        vm.recheckConsistency()
                    }
                }
            }

            // ── Step 5: RTDB lastSeen ping (device roster freshness) ──
            pingRtdbLastSeen(applicationContext)

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
        } finally {
            isRunning.set(false)
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

            // Wait for in-flight deserialization to complete before stopping.
            docSync.awaitDeserializationComplete()

            // Graceful stop: detach Firestore listeners but do NOT cancel
            // deserializeScope children. Firestore can fire in-flight callbacks
            // ~10-20ms after remove(), and we want those (e.g. sharedSettings)
            // to complete so their events land in accumulatedEvents.
            docSync.stopListeners(graceful = true)

            // Drain any late callbacks that fired after remove() (up to 1s).
            docSync.awaitDeserializationComplete(1_000)
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

        // Get current budget start date and archive cutoff
        val appPrefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentBudgetStartDate = appPrefs.getString("budgetStartDate", null)?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }
        val archiveCutoff = sharedSettings.archiveCutoffDate?.let {
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
                currentBudgetStartDate = currentBudgetStartDate,
                archiveCutoffDate = archiveCutoff
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
        if (result.archivedIncoming.isNotEmpty()) {
            val existing = TransactionRepository.loadArchive(applicationContext)
            val existingIds = existing.map { it.id }.toSet()
            val newEntries = result.archivedIncoming.filter { it.id !in existingIds }
            if (newEntries.isNotEmpty()) {
                TransactionRepository.saveArchive(applicationContext, existing + newEntries)
            }
        }
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
                ?.toDoubleOrNull() ?: 0.0,
            carryForwardBalance = sharedSettings.carryForwardBalance,
            archiveCutoffDate = sharedSettings.archiveCutoffDate?.let {
                try { java.time.LocalDate.parse(it) } catch (_: Exception) { null }
            }
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

        val settings = SharedSettingsRepository.load(applicationContext)
        val archiveCutoff = settings.archiveCutoffDate?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }

        val cash = BudgetCalculator.recomputeAvailableCash(
            budgetStartDate, periodLedger,
            transactions.active, recurringExpenses.active,
            incomeMode, incomeSources.active,
            settings.carryForwardBalance, archiveCutoff
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

    // ── Persist background push keys for echo suppression ───────────────

    private fun persistBackgroundPushKeys(result: PeriodRefreshService.RefreshResult) {
        val now = System.currentTimeMillis()
        val keys = mutableMapOf<String, Long>()
        for (re in result.updatedRecurringExpenses) {
            keys["${EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES}:${re.id}"] = now
        }
        for (sg in result.updatedSavingsGoals) {
            keys["${EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS}:${sg.id}"] = now
        }
        for (ple in result.newLedgerEntries) {
            keys["${EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER}:${ple.id}"] = now
        }
        if (keys.isEmpty()) return

        // Merge with existing (prune entries older than 20 min)
        val prefs = applicationContext.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
        val cutoff = now - 20 * 60 * 1000L
        val existing = try {
            val json = JSONObject(prefs.getString("bgPushKeys", "{}") ?: "{}")
            json.keys().asSequence().associate { it to json.getLong(it) }.filterValues { it > cutoff }
        } catch (_: Exception) { emptyMap() }

        val merged = existing.toMutableMap()
        merged.putAll(keys)
        prefs.edit().putString("bgPushKeys", JSONObject(merged.mapKeys { it.key }).toString()).apply()
        Log.i(TAG, "Persisted ${keys.size} background push keys for echo suppression")
    }

    // ── RTDB lastSeen ping ────────────────────────────────────────────

    private suspend fun pingRtdbLastSeen(context: Context) {
        val syncPrefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
        val groupId = syncPrefs.getString("groupId", null) ?: return
        val deviceId = SyncIdGenerator.getOrCreateDeviceId(context)
        try {
            val db = com.google.firebase.database.FirebaseDatabase.getInstance()
            db.reference.child("groups/$groupId/presence/$deviceId/lastSeen")
                .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                .await()
            com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("RTDB lastSeen pinged")
        } catch (e: Exception) {
            Log.w(TAG, "RTDB lastSeen ping failed: ${e.message}")
            com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("RTDB lastSeen ping failed: ${e.message}")
        }
    }
}
