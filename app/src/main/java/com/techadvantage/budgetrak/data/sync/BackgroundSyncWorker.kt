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
 *
 * The actual work body lives in companion `runFullSyncInline`, which can
 * also be invoked directly by FcmService — this lets sync_push wake events
 * complete inside the 10s FCM runtime budget instead of relying on
 * WorkManager to dispatch the worker before Doze / App Standby kills the
 * process. WorkManager remains as a fallback (15-min periodic + FCM
 * `runOnce` if inline returns false).
 */
class BackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BackgroundSyncWorker"
        private const val WORK_NAME = "period_refresh"
        private const val ONESHOT_WORK_NAME = "period_refresh_oneshot"
        private const val BOUNDARY_WORK_NAME = "period_boundary_oneshot"

        // Phase 4-alt: how recent must a successful FCM-inline run be for the
        // periodic worker to skip the heavy Firestore listener bring-up?
        // 30 min covers the 15-min periodic cadence with one cycle of slack
        // for a missed FCM. Set to 0 to disable (always run full Tier 3).
        private const val INLINE_FRESHNESS_MS = 30 * 60 * 1000L
        internal const val KEY_LAST_INLINE_AT = "lastInlineSyncCompletedAt"

        // Guards against double-fire across (a) periodic vs FCM-one-shot
        // worker enqueues (different unique names → KEEP doesn't dedup) and
        // (b) inline FCM call vs WorkManager dispatch racing in the same
        // process. Both paths route through runFullSyncInline below, which
        // is the sole owner of this flag.
        internal val isRunning = AtomicBoolean(false)

        /**
         * Arm the right kind of background work for the current sync state.
         * Sync users get the 15-min periodic (slim-path-eligible per Phase 4-alt
         * when fresh FCM-inline ran). Solo users get a single one-shot at the
         * next period boundary; the boundary worker self-rearms at the end of
         * its slim run, so the chain perpetuates with zero polling between
         * boundaries (battery + CPU win).
         *
         * Idempotent — call freely on app start, group join, group leave,
         * settings change.
         */
        fun schedule(context: Context) {
            val syncPrefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
            val isSyncConfigured = syncPrefs.getString("groupId", null) != null
            if (isSyncConfigured) {
                // Cancel any solo boundary one-shot left over from a previous
                // configuration (user just joined a group).
                WorkManager.getInstance(context).cancelUniqueWork(BOUNDARY_WORK_NAME)
                val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                    15, TimeUnit.MINUTES
                ).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
                )
            } else {
                // Solo path — drop the periodic, arm a one-shot at next boundary.
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                scheduleNextBoundary(context)
            }
        }

        /**
         * Enqueue (or replace) a one-shot worker scheduled at the next period
         * boundary. Called from `schedule()` for solo users and from the slim
         * Tier-3 path at end of every solo run to perpetuate the chain.
         *
         * REPLACE policy + a unique work name means settings changes (e.g.,
         * resetHour adjustment via `MainViewModel`) re-arm cleanly without
         * piling up stale wakes.
         */
        fun scheduleNextBoundary(context: Context) {
            val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val budgetPeriod = try {
                com.techadvantage.budgetrak.data.BudgetPeriod.valueOf(
                    appPrefs.getString("budgetPeriod", null) ?: "DAILY"
                )
            } catch (_: Exception) { com.techadvantage.budgetrak.data.BudgetPeriod.DAILY }
            val resetHour = appPrefs.getInt("resetHour", 0)
            val resetDayOfWeek = appPrefs.getInt("resetDayOfWeek", 1)
            val resetDayOfMonth = appPrefs.getInt("resetDayOfMonth", 1)
            val sharedSettings = SharedSettingsRepository.load(context)
            val nextBoundary = com.techadvantage.budgetrak.data.PeriodRefreshService.nextBoundaryAt(
                budgetPeriod, resetHour, resetDayOfWeek, resetDayOfMonth, sharedSettings.familyTimezone
            )
            // Clamp to [60s, 24h] — guards against clock-skew producing a 0/-ve
            // delay (would fire immediately + tight loop) or a multi-day delay
            // for misconfigured periods.
            val deltaMs = (nextBoundary.toEpochMilli() - System.currentTimeMillis())
                .coerceIn(60_000L, 24L * 60 * 60 * 1000L)
            val builder = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                .setInitialDelay(deltaMs, TimeUnit.MILLISECONDS)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                BOUNDARY_WORK_NAME, ExistingWorkPolicy.REPLACE, builder.build()
            )
            com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent(
                "Boundary scheduled: +${deltaMs/1000}s (budget=$budgetPeriod resetHour=$resetHour)"
            )
        }

        /**
         * Fire a one-shot worker run. Used as a fallback when inline FCM
         * execution times out or is preempted; FcmService prefers
         * `runFullSyncInline` so the work happens in the FCM service
         * process directly.
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
            // Cancel both the periodic and the boundary one-shot — callers
            // (e.g., GroupManager.leaveGroup) expect "stop all background sync
            // work for this configuration." `schedule()` will rearm the
            // appropriate kind for the new state.
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(BOUNDARY_WORK_NAME)
        }

        /**
         * Run the full Tier 1/2/3 sync pipeline inline in the calling
         * coroutine. Used by FcmService so wake events can complete sync
         * work within the FCM 10-second runtime budget instead of relying
         * on WorkManager to dispatch a worker before the process is killed
         * under Doze / App Standby. Also called by `doWork()` itself, so
         * worker + FCM share the exact same code path.
         *
         * Tier routing (same as the legacy worker):
         *  - Tier 1 (app foregrounded): no-op — main app handles everything
         *  - Tier 2 (ViewModel alive): listener health, App Check refresh,
         *    receipt sync, RTDB ping
         *  - Tier 3 (ViewModel dead): full sync from Firestore + period
         *    refresh + receipt sync + push results + widget update
         *
         * @param timeBudgetMs maximum wall-clock time before the body is
         *     cancelled. Null = no budget (worker / cold-start use this).
         *     FCM passes 7_000ms to leave 3s headroom in the 10s window.
         * @return true if the body ran to completion, false if dedup
         *     skipped or the budget expired before completion.
         */
        suspend fun runFullSyncInline(
            context: Context,
            sourceLabel: String,
            timeBudgetMs: Long? = null
        ): Boolean {
            if (!isRunning.compareAndSet(false, true)) {
                com.techadvantage.budgetrak.BudgeTrakApplication
                    .syncEvent("$sourceLabel: skipped (another run already in progress)")
                return false
            }
            val startMs = System.currentTimeMillis()
            try {
                // workDone = real sync work happened (Tier 2/3 ran past their
                // offline-skip checks and reached the end). timedOut = the
                // budget elapsed before the body returned. Keeping these
                // separate so we don't stamp the slim-path window after an
                // offline-skipped FCM heartbeat (which would suppress 30 min
                // of legitimate sync attempts after the network returns).
                val timedOut: Boolean
                val workDone: Boolean = if (timeBudgetMs != null) {
                    val result = kotlinx.coroutines.withTimeoutOrNull(timeBudgetMs) {
                        runFullSyncBody(context, sourceLabel)
                    }
                    timedOut = (result == null)
                    result ?: false
                } else {
                    timedOut = false
                    runFullSyncBody(context, sourceLabel)
                }
                if (timedOut) {
                    val elapsed = System.currentTimeMillis() - startMs
                    com.techadvantage.budgetrak.BudgeTrakApplication
                        .syncEvent("$sourceLabel: TIME-BUDGET-EXPIRED at ${elapsed}ms (budget=${timeBudgetMs}ms)")
                }
                if (workDone && sourceLabel.startsWith("FCM-")) {
                    // Phase 4-alt: stamp last-successful-FCM-inline so the
                    // periodic worker can take the slim path until it's stale.
                    context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
                        .edit().putLong(KEY_LAST_INLINE_AT, System.currentTimeMillis()).apply()
                }
                return workDone
            } finally {
                isRunning.set(false)
            }
        }

        /**
         * Run the diagnostic dump pipeline inline. Used by FcmService for
         * `debug_request` FCM messages and by DebugDumpWorker as a fallback.
         * Sets the `fcm_debug_requested` pref so concurrent dispatches see
         * the work as pending; the body clears the pref after a successful
         * upload to dedup.
         */
        suspend fun runDebugDumpInline(
            context: Context,
            timeBudgetMs: Long? = null
        ): Boolean {
            context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("fcm_debug_requested", true).apply()
            val startMs = System.currentTimeMillis()
            try {
                if (timeBudgetMs != null) {
                    val ok = kotlinx.coroutines.withTimeoutOrNull(timeBudgetMs) {
                        runDebugDumpBody(context)
                        true
                    }
                    if (ok != true) {
                        val elapsed = System.currentTimeMillis() - startMs
                        com.techadvantage.budgetrak.BudgeTrakApplication
                            .syncEvent("DebugDump-inline: TIME-BUDGET-EXPIRED at ${elapsed}ms (budget=${timeBudgetMs}ms)")
                        return false
                    }
                    return true
                } else {
                    runDebugDumpBody(context)
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "DebugDump-inline failed: ${e.message}", e)
                return false
            }
        }
    }

    override suspend fun doWork(): Result {
        try {
            runFullSyncInline(applicationContext, "Worker", timeBudgetMs = null)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Worker was cancelled by the system (Samsung power management,
            // App Standby bucket transition, quota exhaustion, process
            // death). Log stopReason where available (API 31+).
            val reason = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) stopReason else -1
            } catch (_: Throwable) { -1 }
            com.techadvantage.budgetrak.BudgeTrakApplication
                .syncEvent("BackgroundSyncWorker: CANCELLED (stopReason=$reason msg=${ce.message})")
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "BackgroundSyncWorker failed: ${e.message}", e)
        }
        return Result.success()
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Inline sync pipeline body (file-private, callable from worker doWork()
// via runFullSyncInline OR from FcmService directly)
// ──────────────────────────────────────────────────────────────────────────

private const val SYNC_TAG = "BackgroundSync"

/**
 * Returns true if real sync work was done; false if any path early-returned
 * without doing meaningful work (Tier 1 app-active skip, Tier 2/3 offline
 * skip, sync-not-configured skip). The caller uses the return value to
 * decide whether to stamp `KEY_LAST_INLINE_AT` for the slim-path window —
 * stamping after an offline-skip would suppress the next 30 min of real
 * sync attempts.
 */
private suspend fun runFullSyncBody(context: Context, sourceLabel: String): Boolean {
    // Tier 1: app foregrounded — main app handles everything
    if (com.techadvantage.budgetrak.MainActivity.isAppActive) {
        com.techadvantage.budgetrak.BudgeTrakApplication
            .syncEvent("$sourceLabel: app active, skipped")
        return false
    }

    val vm = com.techadvantage.budgetrak.MainViewModel.instance?.get()
    if (vm != null) {
        return runTier2(context, vm, sourceLabel)
    }
    return runTier3(context, sourceLabel)
}

private suspend fun runTier2(
    context: Context,
    vm: com.techadvantage.budgetrak.MainViewModel,
    sourceLabel: String
): Boolean {
    com.techadvantage.budgetrak.BudgeTrakApplication
        .syncEvent("$sourceLabel Tier 2: ViewModel alive, sync=${vm.isSyncConfigured}")
    if (!vm.isSyncConfigured) return false

    // Skip the entire Tier 2 work cycle when offline — every operation here
    // (App Check, listener restart, RTDB ping, receipt sync) hits the network
    // and would burn full timeouts. Firestore + RTDB SDKs auto-reconnect when
    // network returns, and MainViewModel.networkCallback.onAvailable kicks
    // the upload drainer to resume queued receipts.
    if (!NetworkUtils.isOnline(context)) {
        com.techadvantage.budgetrak.BudgeTrakApplication
            .syncEvent("$sourceLabel Tier 2: offline, skipping")
        return false
    }

    // Proactively refresh App Check token before it expires.
    try {
        val token = kotlinx.coroutines.withTimeoutOrNull(10_000) {
            com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                .getAppCheckToken(false).await()
        }
        if (token != null) {
            val remainingMs = token.expireTimeMillis - System.currentTimeMillis()
            // Threshold dropped 35→16 min on 2026-04-25 to keep inline-FCM
            // runs out of the proactive-refresh path. 4 h token TTL × 15-min
            // server heartbeats means at most one heartbeat per cycle sees
            // the token below 16 min remaining; that one refreshes, the
            // other ~15 skip. If a heartbeat is missed and the token
            // briefly expires, `triggerFullRestart` handles `PERMISSION_DENIED`
            // recovery on the next cycle.
            if (remainingMs < 16 * 60 * 1000L) {
                val refreshed = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                    com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                        .getAppCheckToken(true).await()
                }
                if (refreshed != null) {
                    com.techadvantage.budgetrak.BudgeTrakApplication.tokenLog(
                        "Proactive token refresh ($sourceLabel Tier 2): was ${remainingMs/1000}s from expiry"
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
        Log.i(SYNC_TAG, "Restarted dead foreground listeners")
        com.techadvantage.budgetrak.BudgeTrakApplication
            .syncEvent("$sourceLabel Tier 2: restarted dead foreground listeners")
    }

    pingRtdbLastSeen(context)

    if (vm.isPaidUser || vm.isSubscriber) {
        if (vm.isReceiptSyncActive()) {
            Log.i(SYNC_TAG, "Tier 2 receipt sync skipped: foreground drainer/retry active")
        } else try {
            val gid = vm.syncGroupId
            val deviceId = vm.localDeviceId
            val txns = vm.transactions.toList()
            val key = GroupManager.getEncryptionKey(context)
            if (gid != null && key != null && deviceId.isNotBlank()) {
                val devices = resolveDevicesForReceiptSync(context, gid, vm)
                val receiptSync = ReceiptSyncManager(
                    context, gid, deviceId, key
                ) { msg ->
                    com.techadvantage.budgetrak.BudgeTrakApplication
                        .syncEvent("ReceiptSync(Tier2): $msg")
                }
                val updatedTxns = receiptSync.syncReceipts(txns, devices)
                // Propagate receipt-slot changes (most commonly clearLostReceiptSlot
                // nulling a receiptId on a transaction) back to the live VM list so
                // open dialogs / the transactions screen don't keep displaying a
                // photo frame for a slot that was just cleared on disk + Firestore.
                // The Firestore listener can't repair this because pushTransaction
                // sets lastEditBy = our deviceId and the listener's echo filter
                // skips own-device updates.
                val changed = updatedTxns.filter { after ->
                    val before = txns.find { it.id == after.id }
                    before != null && (
                        before.receiptId1 != after.receiptId1 ||
                        before.receiptId2 != after.receiptId2 ||
                        before.receiptId3 != after.receiptId3 ||
                        before.receiptId4 != after.receiptId4 ||
                        before.receiptId5 != after.receiptId5
                    )
                }
                if (changed.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        for (after in changed) {
                            val idx = vm.transactions.indexOfFirst { it.id == after.id }
                            if (idx >= 0) {
                                val current = vm.transactions[idx]
                                vm.transactions[idx] = current.copy(
                                    receiptId1 = after.receiptId1,
                                    receiptId2 = after.receiptId2,
                                    receiptId3 = after.receiptId3,
                                    receiptId4 = after.receiptId4,
                                    receiptId5 = after.receiptId5
                                )
                            }
                        }
                    }
                }
            } else {
                Log.i(SYNC_TAG, "Tier 2 receipt sync skipped: gid=${gid != null} key=${key != null} device=${deviceId.isNotBlank()}")
            }
        } catch (e: Exception) {
            Log.w(SYNC_TAG, "Tier 2 receipt sync failed: ${e.message}")
        }
    }
    return true
}

private suspend fun runTier3(context: Context, sourceLabel: String): Boolean {
    val tier3StartMs = System.currentTimeMillis()
    val syncPrefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
    val groupId = syncPrefs.getString("groupId", null)
    val isSyncConfigured = groupId != null

    // Phase 3 + Phase 4-alt routing: skip Firestore listener bring-up when
    //  - Solo user (no group → nothing to sync).
    //  - Sync user, periodic worker, fresh FCM-inline ran in last 30 min,
    //    no consistency mismatch pending (FCM has been doing the work).
    // Slim path = period refresh + cash recompute + widget update (~25 ms).
    val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val mismatchAt = appPrefs.getLong("checksumMismatchAt", 0L)
    val mismatchPending = mismatchAt > 0L &&
            (System.currentTimeMillis() - mismatchAt) > 60 * 60 * 1000L
    val freshFcmInline = if (isSyncConfigured && sourceLabel == "Worker") {
        val lastInline = syncPrefs.getLong(
            com.techadvantage.budgetrak.data.sync.BackgroundSyncWorker.KEY_LAST_INLINE_AT, 0L
        )
        lastInline > 0L && (System.currentTimeMillis() - lastInline) < 30 * 60 * 1000L
    } else false
    val takeSlimPath = !isSyncConfigured || (freshFcmInline && !mismatchPending)

    if (takeSlimPath) {
        val refreshResult = runPeriodRefresh(context, appPrefs)
        if (refreshResult == null) recomputeCashFromDisk(context, appPrefs)
        BudgetWidgetProvider.updateAllWidgets(context)
        // Solo users self-rearm at the next period boundary (Phase 3).
        if (!isSyncConfigured) {
            com.techadvantage.budgetrak.data.sync.BackgroundSyncWorker.scheduleNextBoundary(context)
        }
        val elapsedMs = System.currentTimeMillis() - tier3StartMs
        com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent(
            "$sourceLabel Tier 3 SLIM: complete in ${elapsedMs}ms " +
                    "(solo=${!isSyncConfigured} freshFcm=$freshFcmInline)"
        )
        // Slim path did real local work (period refresh + cash recompute +
        // widget). Return true — though FCM- prefix doesn't take this path
        // in normal flow (slim is gated on sourceLabel == "Worker"), so
        // this won't extend the slim-path stamp window.
        return true
    }

    val standbyBucket = try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? android.app.usage.UsageStatsManager
        usm?.appStandbyBucket ?: -1
    } catch (_: Exception) { -1 }
    com.techadvantage.budgetrak.BudgeTrakApplication
        .syncEvent("$sourceLabel Tier 3: ViewModel dead, full sync (standbyBucket=$standbyBucket)")

    // Skip the full-sync work cycle when offline. WorkManager will retry the
    // worker on its normal cadence, by which time the network may be back.
    if (!NetworkUtils.isOnline(context)) {
        com.techadvantage.budgetrak.BudgeTrakApplication
            .syncEvent("$sourceLabel Tier 3: offline, skipping")
        return false
    }

    // Anonymous auth (only when sync is configured — solo users skip)
    if (groupId != null && com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance()
                .signInAnonymously()
                .await()
        } catch (e: Exception) {
            Log.w(SYNC_TAG, "Anonymous auth failed: ${e.message}")
        }
    }

    // Proactive App Check refresh — essential here because the ViewModel's
    // keep-alive loop is dead. Without this, all Firestore network reads
    // and RTDB writes fail with PERMISSION_DENIED.
    if (groupId != null) {
        try {
            val token = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                    .getAppCheckToken(false).await()
            }
            if (token != null) {
                val remainingMs = token.expireTimeMillis - System.currentTimeMillis()
                // Threshold dropped 35→16 min on 2026-04-25 to keep inline-FCM
            // runs out of the proactive-refresh path. 4 h token TTL × 15-min
            // server heartbeats means at most one heartbeat per cycle sees
            // the token below 16 min remaining; that one refreshes, the
            // other ~15 skip. If a heartbeat is missed and the token
            // briefly expires, `triggerFullRestart` handles `PERMISSION_DENIED`
            // recovery on the next cycle.
            if (remainingMs < 16 * 60 * 1000L) {
                    val refreshed = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                        com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                            .getAppCheckToken(true).await()
                    }
                    if (refreshed != null) {
                        com.techadvantage.budgetrak.BudgeTrakApplication.tokenLog(
                            "Proactive token refresh ($sourceLabel Tier 3): was ${remainingMs/1000}s from expiry"
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
        encryptionKey = GroupManager.getEncryptionKey(context)
        deviceId = SyncIdGenerator.getOrCreateDeviceId(context)
        if (encryptionKey != null) {
            mergeResult = syncFromFirestore(context, groupId, encryptionKey, deviceId, syncPrefs)
        }
    }

    // ── Step 2: Apply synced settings to SharedPrefs ──
    if (mergeResult != null) {
        saveMergeResult(context, mergeResult, syncPrefs)
    }

    // ── Step 2b: Background receipt photo sync — runs early so Samsung
    // power-management cancellation later in the worker doesn't kill it. ──
    if (groupId != null && encryptionKey != null && deviceId != null) {
        try {
            val photoCapable = appPrefs.getBoolean("isPaidUser", false) ||
                    appPrefs.getBoolean("isSubscriber", false)
            if (photoCapable) {
                val txns = TransactionRepository.load(context)
                // Tier 3 cold-start: RTDB hasn't completed auth handshake
                // yet. Use the fallback chain so receipt sync sees the
                // peer's photo-capable bit even on first-call boot.
                val devices = resolveDevicesForReceiptSync(context, groupId, vm = null)
                val receiptSync = ReceiptSyncManager(
                    context, groupId, deviceId, encryptionKey
                ) { msg ->
                    com.techadvantage.budgetrak.BudgeTrakApplication
                        .syncEvent("ReceiptSync(Tier3): $msg")
                }
                receiptSync.syncReceipts(txns, devices)
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            com.techadvantage.budgetrak.BudgeTrakApplication
                .syncEvent("Tier 3: receipt sync CANCELLED (worker being stopped)")
            throw ce
        } catch (e: Exception) {
            Log.w(SYNC_TAG, "Background receipt sync failed: ${e.message}")
        }
    }

    // ── Step 3: Run period refresh from (possibly updated) local data ──
    val refreshResult = runPeriodRefresh(context, appPrefs)

    // ── Step 3b: Always recompute cash from local data ──
    if (refreshResult == null) {
        recomputeCashFromDisk(context, appPrefs)
    }

    // ── Step 4: Push changes to Firestore if sync configured ──
    if (groupId != null && encryptionKey != null && deviceId != null) {
        if (refreshResult != null) {
            pushRefreshResults(refreshResult, groupId, encryptionKey, deviceId)
            persistBackgroundPushKeys(context, refreshResult)
        }
        if (mergeResult != null) {
            pushSyncSideEffects(mergeResult, groupId, encryptionKey, deviceId)
        }
    }

    // ── Step 4b: Recheck consistency mismatch if flagged ──
    // Re-read mismatchAt: a Tier-3 push or merge can flip the flag mid-run.
    if (groupId != null) {
        val mismatchAtNow = appPrefs.getLong("checksumMismatchAt", 0L)
        if (mismatchAtNow > 0 && System.currentTimeMillis() - mismatchAtNow > 60 * 60 * 1000L) {
            val vm = com.techadvantage.budgetrak.MainViewModel.instance?.get()
            if (vm != null) {
                vm.recheckConsistency()
            }
        }
    }

    // ── Step 5: RTDB lastSeen ping ──
    pingRtdbLastSeen(context)

    // ── Step 6: Update widget ──
    BudgetWidgetProvider.updateAllWidgets(context)

    val elapsedMs = System.currentTimeMillis() - tier3StartMs
    com.techadvantage.budgetrak.BudgeTrakApplication
        .syncEvent("$sourceLabel Tier 3: complete in ${elapsedMs}ms")
    return true
}

// ── Resolve photo-capable device list with fallback chain ──────────────
//
// Cold-start Tier 3 calls `RealtimePresenceService.getDevices()` which
// reads RTDB presence. RTDB hasn't completed its auth handshake yet, so
// the read returns empty / no photo-capable devices. Receipt sync then
// runs as a no-op for photo work because `photoCapableDeviceIds` is the
// empty set. Fallback chain (each tier writes to a SharedPref cache on
// success so subsequent fallback attempts can hit the cache instead of
// network):
//   1. VM's `syncDevices` (Tier 2 only — VM has fresh data already)
//   2. RTDB presence read (the original path)
//   3. Firestore `groups/{gid}/devices/*` device docs (auth via Firestore
//      is established by Tier 3's syncFromFirestore step)
//   4. SharedPref cache from prior successful sync
private suspend fun resolveDevicesForReceiptSync(
    context: Context,
    groupId: String,
    vm: com.techadvantage.budgetrak.MainViewModel?
): List<DeviceInfo> {
    if (vm != null) {
        val vmDevices = vm.syncDevices
        if (vmDevices.any { it.photoCapable }) {
            cachePhotoCapableDevices(context, groupId, vmDevices)
            return vmDevices
        }
    }

    val rtdbDevices = try {
        RealtimePresenceService.getDevices(groupId)
    } catch (e: Exception) {
        Log.w(SYNC_TAG, "RTDB getDevices failed: ${e.message}")
        emptyList()
    }
    if (rtdbDevices.any { it.photoCapable }) {
        cachePhotoCapableDevices(context, groupId, rtdbDevices)
        return rtdbDevices
    }

    val fsDevices = try {
        FirestoreService.getDevices(groupId).map { rec ->
            val rt = rtdbDevices.find { it.deviceId == rec.deviceId }
            DeviceInfo(
                deviceId = rec.deviceId,
                deviceName = rec.deviceName.ifEmpty { rt?.deviceName ?: "" },
                isAdmin = rec.isAdmin,
                lastSeen = maxOf(rec.lastSeen, rt?.lastSeen ?: 0L),
                online = rt?.online ?: false,
                photoCapable = rec.photoCapable,
                uploadSpeedBps = if ((rt?.uploadSpeedBps ?: 0L) > 0) rt!!.uploadSpeedBps else rec.uploadSpeedBps,
                uploadSpeedMeasuredAt = if ((rt?.uploadSpeedMeasuredAt ?: 0L) > 0) rt!!.uploadSpeedMeasuredAt else rec.uploadSpeedMeasuredAt
            )
        }
    } catch (e: Exception) {
        Log.w(SYNC_TAG, "Firestore getDevices fallback failed: ${e.message}")
        emptyList()
    }
    if (fsDevices.any { it.photoCapable }) {
        cachePhotoCapableDevices(context, groupId, fsDevices)
        com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent(
            "resolveDevices: RTDB empty, used Firestore fallback (n=${fsDevices.count { it.photoCapable }})"
        )
        return fsDevices
    }

    val cachedIds = readCachedPhotoCapableDevices(context, groupId)
    if (cachedIds.isNotEmpty()) {
        com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent(
            "resolveDevices: RTDB+Firestore empty, used SharedPref cache (n=${cachedIds.size})"
        )
        return cachedIds.map { id ->
            DeviceInfo(
                deviceId = id,
                deviceName = "",
                isAdmin = false,
                lastSeen = 0L,
                online = false,
                photoCapable = true
            )
        }
    }

    com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent(
        "resolveDevices: ALL fallbacks failed for group $groupId — receipt sync will no-op"
    )
    return rtdbDevices
}

private fun cachePhotoCapableDevices(context: Context, groupId: String, devices: List<DeviceInfo>) {
    val ids = devices.filter { it.photoCapable }.map { it.deviceId }
    if (ids.isEmpty()) return
    try {
        context.getSharedPreferences("receipt_sync_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("photo_capable_devices_$groupId", ids.joinToString(","))
            .apply()
    } catch (_: Exception) {}
}

private fun readCachedPhotoCapableDevices(context: Context, groupId: String): List<String> {
    return try {
        context.getSharedPreferences("receipt_sync_prefs", Context.MODE_PRIVATE)
            .getString("photo_capable_devices_$groupId", null)
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

// ── Sync from Firestore via short-lived listeners ──────────────────────

private suspend fun syncFromFirestore(
    context: Context,
    groupId: String,
    encryptionKey: ByteArray,
    deviceId: String,
    syncPrefs: android.content.SharedPreferences
): SyncMergeProcessor.MergeResult? {
    val docSync = FirestoreDocSync(context, groupId, deviceId, encryptionKey)
    val accumulatedEvents = java.util.Collections.synchronizedList(mutableListOf<DataChangeEvent>())

    docSync.onBatchChanged = { events ->
        accumulatedEvents.addAll(events)
    }

    try {
        docSync.startListeners()
        docSync.awaitInitialSync(60_000)
        docSync.awaitDeserializationComplete()
        docSync.stopListeners(graceful = true)
        docSync.awaitDeserializationComplete(1_000)
    } catch (e: Exception) {
        Log.w(SYNC_TAG, "Firestore sync failed: ${e.message}")
        try { docSync.stopListeners() } catch (_: Exception) {}
        return null
    }

    if (accumulatedEvents.isEmpty()) return null

    val transactions = TransactionRepository.load(context)
    val recurringExpenses = RecurringExpenseRepository.load(context)
    val incomeSources = IncomeSourceRepository.load(context)
    val savingsGoals = SavingsGoalRepository.load(context)
    val amortizationEntries = AmortizationRepository.load(context)
    val categories = CategoryRepository.load(context)
    val periodLedger = PeriodLedgerRepository.load(context)
    val sharedSettings = SharedSettingsRepository.load(context)

    val catIdRemap: MutableMap<Int, Int> = try {
        val remapJson = syncPrefs.getString("catIdRemap", null)
        if (remapJson != null) {
            val json = JSONObject(remapJson)
            json.keys().asSequence().associate {
                it.toInt() to json.getInt(it)
            }.toMutableMap()
        } else mutableMapOf()
    } catch (_: Exception) { mutableMapOf() }

    val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val currentBudgetStartDate = appPrefs.getString("budgetStartDate", null)?.let {
        try { LocalDate.parse(it) } catch (_: Exception) { null }
    }
    val archiveCutoff = sharedSettings.archiveCutoffDate?.let {
        try { LocalDate.parse(it) } catch (_: Exception) { null }
    }

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

    if (catIdRemap.isNotEmpty()) {
        syncPrefs.edit().putString(
            "catIdRemap",
            JSONObject(catIdRemap.mapKeys { it.key.toString() }).toString()
        ).apply()
    }

    return result
}

// ── Save merge result to disk ──────────────────────────────────────────

private fun saveMergeResult(
    context: Context,
    result: SyncMergeProcessor.MergeResult,
    syncPrefs: android.content.SharedPreferences
) {
    result.transactions?.let { TransactionRepository.save(context, it) }
    if (result.archivedIncoming.isNotEmpty()) {
        val existing = TransactionRepository.loadArchive(context)
        val existingIds = existing.map { it.id }.toSet()
        val newEntries = result.archivedIncoming.filter { it.id !in existingIds }
        if (newEntries.isNotEmpty()) {
            TransactionRepository.saveArchive(context, existing + newEntries)
        }
    }
    result.recurringExpenses?.let { RecurringExpenseRepository.save(context, it) }
    result.incomeSources?.let { IncomeSourceRepository.save(context, it) }
    result.savingsGoals?.let { SavingsGoalRepository.save(context, it) }
    result.amortizationEntries?.let { AmortizationRepository.save(context, it) }
    result.categories?.let { CategoryRepository.save(context, it) }
    result.periodLedger?.let { PeriodLedgerRepository.save(context, it) }
    result.sharedSettings?.let { SharedSettingsRepository.save(context, it) }

    result.settingsPrefsToApply?.let { prefs ->
        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
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

// ── Run period refresh ─────────────────────────────────────────────────

private fun runPeriodRefresh(
    context: Context,
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

    val sharedSettings = SharedSettingsRepository.load(context)

    val config = PeriodRefreshService.RefreshConfig(
        budgetStartDate = budgetStartDate,
        lastRefreshDate = lastRefreshDate,
        budgetPeriod = budgetPeriod,
        resetHour = appPrefs.getInt("resetHour", 0),
        resetDayOfWeek = appPrefs.getInt("resetDayOfWeek", 1),
        resetDayOfMonth = appPrefs.getInt("resetDayOfMonth", 1),
        familyTimezone = sharedSettings.familyTimezone,
        localDeviceId = SyncIdGenerator.getOrCreateDeviceId(context),
        incomeMode = incomeMode,
        isManualBudgetEnabled = appPrefs.getBoolean("isManualBudgetEnabled", false),
        manualBudgetAmount = appPrefs.getString("manualBudgetAmount", "0.0")
            ?.toDoubleOrNull() ?: 0.0,
        carryForwardBalance = sharedSettings.carryForwardBalance,
        archiveCutoffDate = sharedSettings.archiveCutoffDate?.let {
            try { java.time.LocalDate.parse(it) } catch (_: Exception) { null }
        }
    )

    return PeriodRefreshService.refreshIfNeeded(context, config)
}

// ── Recompute cash from disk ──────────────────────────────────────────

private fun recomputeCashFromDisk(context: Context, appPrefs: android.content.SharedPreferences) {
    val budgetStartDate = appPrefs.getString("budgetStartDate", null)?.let {
        try { LocalDate.parse(it) } catch (_: Exception) { null }
    } ?: return

    val incomeMode = try {
        IncomeMode.valueOf(appPrefs.getString("incomeMode", null) ?: "FIXED")
    } catch (_: Exception) { IncomeMode.FIXED }

    val periodLedger = PeriodLedgerRepository.load(context)
    val transactions = TransactionRepository.load(context)
    val recurringExpenses = RecurringExpenseRepository.load(context)
    val incomeSources = IncomeSourceRepository.load(context)

    val settings = SharedSettingsRepository.load(context)
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

// ── Push period refresh results ────────────────────────────────────────

private suspend fun pushRefreshResults(
    result: PeriodRefreshService.RefreshResult,
    groupId: String,
    encryptionKey: ByteArray,
    deviceId: String
) {
    withContext(Dispatchers.IO) {
        for (entry in result.newLedgerEntries) {
            try {
                FirestoreDocService.createDocIfAbsent(
                    groupId,
                    EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER,
                    entry.id.toString(),
                    EncryptedDocSerializer.toFieldMap(entry, encryptionKey, deviceId)
                )
            } catch (e: Exception) {
                Log.w(SYNC_TAG, "Push ledger entry failed: ${e.message}")
            }
        }

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
                Log.w(SYNC_TAG, "Push savings goal failed: ${e.message}")
            }
        }

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
                Log.w(SYNC_TAG, "Push recurring expense failed: ${e.message}")
            }
        }
    }
}

// ── Push sync side effects ────────────────────────────────────────────

private suspend fun pushSyncSideEffects(
    result: SyncMergeProcessor.MergeResult,
    groupId: String,
    encryptionKey: ByteArray,
    deviceId: String
) {
    withContext(Dispatchers.IO) {
        for (catId in result.categoriesToDeleteFromFirestore) {
            try {
                FirestoreDocService.deleteDoc(
                    groupId,
                    EncryptedDocSerializer.COLLECTION_CATEGORIES,
                    catId.toString()
                )
            } catch (e: Exception) {
                Log.w(SYNC_TAG, "Delete remapped category failed: ${e.message}")
            }
        }

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
                Log.w(SYNC_TAG, "Push conflicted transaction failed: ${e.message}")
            }
        }
    }
}

// ── Persist background push keys for echo suppression ──────────────────

private fun persistBackgroundPushKeys(context: Context, result: PeriodRefreshService.RefreshResult) {
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

    val prefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
    val cutoff = now - 20 * 60 * 1000L
    val existing = try {
        val json = JSONObject(prefs.getString("bgPushKeys", "{}") ?: "{}")
        json.keys().asSequence().associate { it to json.getLong(it) }.filterValues { it > cutoff }
    } catch (_: Exception) { emptyMap() }

    val merged = existing.toMutableMap()
    merged.putAll(keys)
    prefs.edit().putString("bgPushKeys", JSONObject(merged.mapKeys { it.key }).toString()).apply()
    Log.i(SYNC_TAG, "Persisted ${keys.size} background push keys for echo suppression")
}

// ── RTDB lastSeen ping ────────────────────────────────────────────────

private suspend fun pingRtdbLastSeen(context: Context) {
    val syncPrefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
    val groupId = syncPrefs.getString("groupId", null) ?: return
    val deviceId = SyncIdGenerator.getOrCreateDeviceId(context)
    try {
        // 10s timeout: without this, a stuck RTDB connection can hang the
        // calling Tier 2 / Tier 3 indefinitely. WorkManager's 10-min ceiling
        // eventually cancels the worker, but the cancellation cascades into
        // the receipt-sync that runs after the ping (observed twice in the
        // 2026-04-26 dump: 08:19 + 10:11 — both started ~12 min into a
        // Worker run, both ended in ReceiptSync `Job was cancelled`).
        val ok = kotlinx.coroutines.withTimeoutOrNull(10_000) {
            val db = com.google.firebase.database.FirebaseDatabase.getInstance()
            db.reference.child("groups/$groupId/presence/$deviceId/lastSeen")
                .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                .await()
            true
        }
        if (ok == true) {
            com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("RTDB lastSeen pinged")
        } else {
            com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("RTDB lastSeen ping TIMED OUT (10s)")
        }
    } catch (e: Exception) {
        Log.w(SYNC_TAG, "RTDB lastSeen ping failed: ${e.message}")
        com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("RTDB lastSeen ping failed: ${e.message}")
    }
}

// ──────────────────────────────────────────────────────────────────────
// Debug-dump body (callable inline from FCM, or via DebugDumpWorker)
// ──────────────────────────────────────────────────────────────────────

private suspend fun runDebugDumpBody(context: Context) {
    if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance()
                .signInAnonymously()
                .await()
        } catch (e: Exception) {
            Log.w("DebugDump", "Anonymous auth failed: ${e.message}")
            return
        }
    }

    val syncPrefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
    val fcmPrefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)

    if (!fcmPrefs.getBoolean("fcm_debug_requested", false)) {
        return
    }

    try {
        val groupId = syncPrefs.getString("groupId", null)
        val deviceId = SyncIdGenerator.getOrCreateDeviceId(context)
        val devName = GroupManager.getDeviceName(context)
        val keyBase64 = SecurePrefs.get(context).getString("encryptionKey", null)
            ?: syncPrefs.getString("encryptionKey", null)
        if (groupId != null && keyBase64 != null) {
            val key = android.util.Base64.decode(keyBase64, android.util.Base64.NO_WRAP)
            val supportDir = com.techadvantage.budgetrak.data.BackupManager.getSupportDir()
            val diagText = try {
                val fresh = com.techadvantage.budgetrak.data.DiagDumpBuilder.build(context)
                java.io.File(supportDir, "sync_diag.txt").writeText(fresh)
                val diagDevName = devName.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)
                if (diagDevName.isNotEmpty()) {
                    java.io.File(supportDir, "sync_diag_${diagDevName}.txt").writeText(fresh)
                }
                fresh
            } catch (e: Exception) {
                Log.w("DebugDump", "Fresh dump failed: ${e.message}")
                try {
                    java.io.File(supportDir, "sync_diag.txt").readText()
                } catch (_: Exception) { "(no diag file)" }
            }
            val syncLogText = try {
                java.io.File(supportDir, "native_sync_log.txt").readText()
            } catch (_: Exception) { "" }
            val tokenLogText = try {
                java.io.File(supportDir, "token_log.txt").readText()
            } catch (_: Exception) { "" }
            val combinedLog = if (tokenLogText.isNotEmpty())
                "$syncLogText\n\n── Token Log ──\n$tokenLogText"
            else syncLogText
            val logcatText = if (com.techadvantage.budgetrak.BuildConfig.DEBUG) {
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "1000"))
                    val t = p.inputStream.bufferedReader().readText()
                    p.waitFor()
                    t
                } catch (e: Exception) {
                    Log.w("DebugDump", "Logcat capture failed: ${e.message}")
                    null
                }
            } else null
            FirestoreService.uploadDebugFiles(groupId, deviceId, devName, combinedLog, diagText, key, logcatText)
        }
    } catch (e: Exception) {
        Log.e("DebugDump", "Debug upload failed: ${e.message}")
    }
    fcmPrefs.edit().putBoolean("fcm_debug_requested", false).apply()
}
