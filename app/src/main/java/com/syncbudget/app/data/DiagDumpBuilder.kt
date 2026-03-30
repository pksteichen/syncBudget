package com.syncbudget.app.data

import android.content.Context
import com.syncbudget.app.data.sync.PeriodLedgerEntry
import com.syncbudget.app.data.sync.PeriodLedgerRepository
import com.syncbudget.app.data.sync.SyncIdGenerator
import com.syncbudget.app.data.sync.SyncWriteHelper
import com.syncbudget.app.data.sync.active
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Generates a diagnostic state dump from disk data (SharedPrefs + JSON repos).
 *
 * Usable from both foreground (MainActivity) and background (SyncWorker).
 * The foreground version in MainActivity can still override with live Compose
 * state if desired, but this provides a complete dump from persisted data.
 */
object DiagDumpBuilder {

    fun build(context: Context): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val syncPrefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
        val deviceId = SyncIdGenerator.getOrCreateDeviceId(context)

        // Load all data from disk
        val transactions = TransactionRepository.load(context)
        val recurringExpenses = RecurringExpenseRepository.load(context)
        val incomeSources = IncomeSourceRepository.load(context)
        val savingsGoals = SavingsGoalRepository.load(context)
        val amortizationEntries = AmortizationRepository.load(context)
        val categories = CategoryRepository.load(context)
        val periodLedger = PeriodLedgerRepository.load(context)
        val sharedSettings = SharedSettingsRepository.load(context)

        // Parse prefs
        val budgetStartDate = prefs.getString("budgetStartDate", null)?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }
        val lastRefreshDate = prefs.getString("lastRefreshDate", null)?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }
        val budgetPeriod = try {
            BudgetPeriod.valueOf(prefs.getString("budgetPeriod", null) ?: "DAILY")
        } catch (_: Exception) { BudgetPeriod.DAILY }
        val incomeMode = try {
            IncomeMode.valueOf(prefs.getString("incomeMode", null) ?: "FIXED")
        } catch (_: Exception) { IncomeMode.FIXED }
        val isManualBudgetEnabled = prefs.getBoolean("isManualBudgetEnabled", false)
        val manualBudgetAmount = prefs.getString("manualBudgetAmount", "0.0")
            ?.toDoubleOrNull() ?: 0.0
        val availableCashPrefs = prefs.getDoubleCompat("availableCash")
        val isAdmin = syncPrefs.getBoolean("isAdmin", false)
        val isSyncConfigured = syncPrefs.getString("groupId", null) != null

        // Compute derived values from disk data
        val activeIS = incomeSources.active
        val activeRE = recurringExpenses.active
        val activeAE = amortizationEntries.active
        val activeSG = savingsGoals.active
        val today = LocalDate.now()
        val safeBudgetAmount = BudgetCalculator.calculateSafeBudgetAmount(
            activeIS, activeRE, budgetPeriod, today
        )
        val budgetAmount = BudgetCalculator.computeFullBudgetAmount(
            activeIS, activeRE, activeAE, activeSG,
            budgetPeriod, isManualBudgetEnabled, manualBudgetAmount, today
        )

        // Recompute cash for verification
        val activeTxns = transactions.active
        val verifyCash = if (budgetStartDate != null) {
            BudgetCalculator.recomputeAvailableCash(
                budgetStartDate, periodLedger, activeTxns, activeRE, incomeMode, activeIS
            )
        } else 0.0

        val ledgerCredits = if (budgetStartDate != null) {
            periodLedger
                .filter { !it.periodStartDate.toLocalDate().isBefore(budgetStartDate) }
                .groupBy { it.periodStartDate.toLocalDate() }
                .values.map { entries -> entries.maxByOrNull { it.periodStartDate } ?: entries.first() }
                .sumOf { it.appliedAmount }
        } else 0.0

        val categoryMap = categories.associateBy { it.id }

        // Build dump
        val dump = StringBuilder()
        dump.appendLine("=== State Dump ${LocalDateTime.now()} ===")
        dump.appendLine("DeviceId: $deviceId")
        dump.appendLine("isAdmin: $isAdmin")
        dump.appendLine("isSyncConfigured: $isSyncConfigured")
        dump.appendLine()
        dump.appendLine("── App Prefs ──")
        dump.appendLine("availableCash (prefs): $availableCashPrefs")
        dump.appendLine("availableCash (state): $availableCashPrefs")
        dump.appendLine("budgetStartDate: $budgetStartDate")
        dump.appendLine("lastRefreshDate: $lastRefreshDate")
        dump.appendLine("budgetPeriod: $budgetPeriod")
        dump.appendLine("budgetAmount (derived): $budgetAmount")
        dump.appendLine("safeBudgetAmount (derived): $safeBudgetAmount")
        dump.appendLine("isManualBudget: $isManualBudgetEnabled  manualAmount: $manualBudgetAmount")
        dump.appendLine()
        dump.appendLine("── SharedSettings ──")
        dump.appendLine("availableCash: ${sharedSettings.availableCash}")
        dump.appendLine("budgetStartDate: ${sharedSettings.budgetStartDate}")
        dump.appendLine("budgetPeriod: ${sharedSettings.budgetPeriod}")
        dump.appendLine("manualBudgetAmount: ${sharedSettings.manualBudgetAmount}")
        dump.appendLine("isManualBudgetEnabled: ${sharedSettings.isManualBudgetEnabled}")
        dump.appendLine("currency: ${sharedSettings.currency}")
        dump.appendLine("lastChangedBy: ${sharedSettings.lastChangedBy}")
        dump.appendLine()

        // Sync metadata
        dump.appendLine("── Sync Metadata ──")
        val nativeDocsDone = syncPrefs.getBoolean("migration_native_docs_done", false)
        dump.appendLine("syncMode: ${if (nativeDocsDone) "FIRESTORE_NATIVE" else "CRDT_LEGACY"}")
        dump.appendLine("migration_native_docs_done: $nativeDocsDone")
        dump.appendLine("syncStatus: synced")
        dump.appendLine("SyncWriteHelper.isInitialized: ${SyncWriteHelper.isInitialized()}")
        val remapJsonDump = syncPrefs.getString("catIdRemap", null)
        dump.appendLine("catIdRemap: ${remapJsonDump ?: "(empty)"}")

        // Include file-based sync log tail
        try {
            val nativeLogFile = java.io.File(BackupManager.getSupportDir(), "native_sync_log.txt")
            if (nativeLogFile.exists()) {
                val lines = nativeLogFile.readLines()
                val tail = lines.takeLast(50)
                dump.appendLine()
                dump.appendLine("── Native Sync Log (last ${tail.size} lines) ──")
                tail.forEach { dump.appendLine(it) }
            }
        } catch (_: Exception) {}
        dump.appendLine()

        dump.appendLine("── Categories ──")
        for (cat in categories.sortedBy { it.id }) {
            dump.appendLine("  id=${cat.id} '${cat.name}' tag=${cat.tag} icon=${cat.iconName} dev=${cat.deviceId.take(8)}… del=${cat.deleted}")
        }
        dump.appendLine()

        dump.appendLine("── Recurring Expenses ──")
        for (re in recurringExpenses.sortedBy { it.source }) {
            dump.appendLine("  id=${re.id} '${re.source}' amt=${re.amount} ${re.repeatType}/${re.repeatInterval} dev=${re.deviceId.take(8)}… del=${re.deleted} setAside=${re.setAsideSoFar} accel=${re.isAccelerated}")
        }
        dump.appendLine()

        dump.appendLine("── Transactions (active, in current period) ──")
        val periodTxns = if (budgetStartDate != null)
            activeTxns.filter { !it.date.isBefore(budgetStartDate) } else activeTxns
        for (txn in periodTxns.sortedBy { it.date }) {
            val ba = if (txn.type == TransactionType.EXPENSE) isBudgetAccounted(txn) else false
            val catDesc = if (txn.categoryAmounts.isEmpty()) "cats=NONE"
            else txn.categoryAmounts.joinToString(",") { ca ->
                val name = categoryMap[ca.categoryId]?.name ?: "???"
                "${ca.categoryId}($name):${ca.amount}"
            }
            val linkDesc = listOfNotNull(
                txn.linkedRecurringExpenseId?.let { "reId=$it(reAmt=${txn.linkedRecurringExpenseAmount})" },
                txn.linkedAmortizationEntryId?.let { "aeId=$it(appl=${txn.amortizationAppliedAmount})" },
                txn.linkedIncomeSourceId?.let { "isId=$it(isAmt=${txn.linkedIncomeSourceAmount})" },
                txn.linkedSavingsGoalId?.let { "sgId=$it(sgAmt=${txn.linkedSavingsGoalAmount})" }
            ).joinToString(" ").ifEmpty { "" }
            val flagDesc = listOfNotNull(
                if (txn.excludeFromBudget) "ef=true" else null,
                if (txn.isBudgetIncome) "bi=true" else null
            ).joinToString(" ").let { if (it.isNotEmpty()) "$it " else "" }
            dump.appendLine("  ${txn.date} ${txn.type} ${txn.amount} '${txn.source}' dev=${txn.deviceId.take(8)}… ba=$ba ${flagDesc}$linkDesc $catDesc")
        }

        dump.appendLine("── Cash Verification ──")
        dump.appendLine("Ledger credits (sum of ${periodLedger.size} entries): $ledgerCredits")
        dump.appendLine("Recomputed cash (BudgetCalculator): $verifyCash")
        dump.appendLine("Stored cash (prefs): $availableCashPrefs")
        dump.appendLine("Match: ${kotlin.math.abs(verifyCash - availableCashPrefs) < 0.01}")
        dump.appendLine()
        dump.appendLine("── Period Ledger ──")
        for (entry in periodLedger) {
            dump.appendLine("  ${entry.periodStartDate} applied=${entry.appliedAmount}")
        }
        dump.appendLine("=== End Dump ===")
        return dump.toString()
    }

    /** Same logic as MainActivity.isBudgetAccountedExpense */
    private fun isBudgetAccounted(txn: Transaction): Boolean {
        if (txn.type != TransactionType.EXPENSE) return false
        if (txn.linkedAmortizationEntryId != null) return true
        if (txn.linkedSavingsGoalId != null || txn.linkedSavingsGoalAmount > 0.0) {
            return txn.linkedSavingsGoalAmount >= txn.amount
        }
        return false
    }

    /** Write text to a file in Download/BudgeTrak/support/ (with MediaStore fallback). */
    fun writeDiagToMediaStore(context: Context, fileName: String, text: String) {
        try {
            val file = java.io.File(BackupManager.getSupportDir(), fileName)
            file.writeText(text)
        } catch (e: Exception) {
            android.util.Log.w("DiagDump", "Direct write failed for $fileName, trying MediaStore: ${e.message}")
            try {
                val resolver = context.contentResolver
                val existing = resolver.query(
                    android.provider.MediaStore.Files.getContentUri("external"),
                    arrayOf(android.provider.MediaStore.MediaColumns._ID),
                    "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                    arrayOf(fileName, "Download/BudgeTrak/support/"),
                    null
                )
                val uri = if (existing != null && existing.moveToFirst()) {
                    val id = existing.getLong(0)
                    existing.close()
                    android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Files.getContentUri("external"), id
                    )
                } else {
                    existing?.close()
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/BudgeTrak/support/")
                    }
                    resolver.insert(android.provider.MediaStore.Files.getContentUri("external"), values)
                }
                if (uri != null) {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                }
            } catch (e2: Exception) {
                android.util.Log.e("DiagDump", "MediaStore write also failed for $fileName: ${e2.message}")
            }
        }
    }

    fun sanitizeDeviceName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)
}
