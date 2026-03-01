package com.syncbudget.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.BudgetCalculator
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.CategoryRepository
import com.syncbudget.app.data.DEFAULT_CATEGORY_DEFS
import com.syncbudget.app.data.getAllKnownNamesForTag
import com.syncbudget.app.data.getDefaultCategoryName
import com.syncbudget.app.data.AmortizationRepository
import com.syncbudget.app.data.SavingsGoalRepository
import com.syncbudget.app.data.SuperchargeMode
import com.syncbudget.app.data.calculatePerPeriodDeduction
import kotlin.math.ceil
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.IncomeSourceRepository
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.RecurringExpenseRepository
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionRepository
import com.syncbudget.app.data.TransactionType
import com.syncbudget.app.data.generateTransactionId
import com.syncbudget.app.data.SharedSettings
import com.syncbudget.app.data.SharedSettingsRepository
import com.syncbudget.app.data.sync.DeviceInfo
import com.syncbudget.app.data.sync.FirestoreService
import com.syncbudget.app.data.sync.GroupManager
import java.time.ZoneId
import com.syncbudget.app.data.sync.LamportClock
import com.syncbudget.app.data.sync.PeriodLedgerEntry
import com.syncbudget.app.data.sync.PeriodLedgerRepository
import com.syncbudget.app.data.sync.SyncEngine
import com.syncbudget.app.data.sync.SyncIdGenerator
import com.syncbudget.app.data.sync.SyncWorker
import com.syncbudget.app.data.sync.active
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.syncbudget.app.data.FullBackupSerializer
import com.syncbudget.app.data.findAmortizationMatch
import com.syncbudget.app.data.findBudgetIncomeMatch
import com.syncbudget.app.data.findDuplicate
import com.syncbudget.app.data.findRecurringExpenseMatch
import com.syncbudget.app.data.isRecurringDateCloseEnough
import com.syncbudget.app.sound.FlipSoundPlayer
import com.syncbudget.app.ui.screens.AmortizationConfirmDialog
import com.syncbudget.app.ui.screens.AmortizationHelpScreen
import com.syncbudget.app.ui.screens.AmortizationScreen
import com.syncbudget.app.ui.screens.BudgetConfigHelpScreen
import com.syncbudget.app.ui.screens.BudgetConfigScreen
import com.syncbudget.app.ui.screens.BudgetIncomeConfirmDialog
import com.syncbudget.app.ui.screens.DashboardHelpScreen
import com.syncbudget.app.ui.screens.DuplicateResolutionDialog
import com.syncbudget.app.data.sync.AdminClaim
import com.syncbudget.app.ui.screens.FamilySyncHelpScreen
import com.syncbudget.app.ui.screens.FamilySyncScreen
import com.syncbudget.app.ui.screens.FutureExpendituresHelpScreen
import com.syncbudget.app.ui.screens.FutureExpendituresScreen
import com.syncbudget.app.ui.screens.MainScreen
import com.syncbudget.app.ui.screens.RecurringExpenseConfirmDialog
import com.syncbudget.app.ui.screens.RecurringExpensesHelpScreen
import com.syncbudget.app.ui.screens.RecurringExpensesScreen
import com.syncbudget.app.ui.screens.SettingsHelpScreen
import com.syncbudget.app.ui.screens.SettingsScreen
import com.syncbudget.app.ui.screens.TransactionDialog
import com.syncbudget.app.ui.screens.TransactionsHelpScreen
import com.syncbudget.app.ui.screens.TransactionsScreen
import com.syncbudget.app.ui.strings.AppStrings
import com.syncbudget.app.ui.strings.EnglishStrings
import com.syncbudget.app.ui.strings.SpanishStrings
import com.syncbudget.app.ui.theme.SyncBudgetTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.syncbudget.app.ui.theme.LocalAdBannerHeight
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val soundPlayer = remember { FlipSoundPlayer(this@MainActivity) }
            val lamportClock = remember { LamportClock(this@MainActivity) }

            DisposableEffect(Unit) {
                onDispose { soundPlayer.release() }
            }

            var currentScreen by remember { mutableStateOf("main") }

            // Dashboard quick-add dialog state
            var dashboardShowAddIncome by remember { mutableStateOf(false) }
            var dashboardShowAddExpense by remember { mutableStateOf(false) }

            // Dashboard matching state
            var dashPendingManualSave by remember { mutableStateOf<Transaction?>(null) }
            var dashManualDuplicateMatch by remember { mutableStateOf<Transaction?>(null) }
            var dashShowManualDuplicateDialog by remember { mutableStateOf(false) }

            var dashPendingRecurringTxn by remember { mutableStateOf<Transaction?>(null) }
            var dashPendingRecurringMatch by remember { mutableStateOf<RecurringExpense?>(null) }
            var dashShowRecurringDialog by remember { mutableStateOf(false) }

            var dashPendingAmortizationTxn by remember { mutableStateOf<Transaction?>(null) }
            var dashPendingAmortizationMatch by remember { mutableStateOf<AmortizationEntry?>(null) }
            var dashShowAmortizationDialog by remember { mutableStateOf(false) }

            var dashPendingBudgetIncomeTxn by remember { mutableStateOf<Transaction?>(null) }
            var dashPendingBudgetIncomeMatch by remember { mutableStateOf<IncomeSource?>(null) }
            var dashShowBudgetIncomeDialog by remember { mutableStateOf(false) }

            val context = this@MainActivity
            val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            var currencySymbol by remember { mutableStateOf(prefs.getString("currencySymbol", "$") ?: "$") }
            var digitCount by remember { mutableIntStateOf(prefs.getInt("digitCount", 3)) }
            var showDecimals by remember { mutableStateOf(prefs.getBoolean("showDecimals", false)) }
            var dateFormatPattern by remember { mutableStateOf(prefs.getString("dateFormatPattern", "yyyy-MM-dd") ?: "yyyy-MM-dd") }
            var isPaidUser by remember { mutableStateOf(prefs.getBoolean("isPaidUser", false)) }

            // Matching configuration
            var matchDays by remember { mutableIntStateOf(prefs.getInt("matchDays", 7)) }
            var matchPercent by remember { mutableDoubleStateOf(
                try { prefs.getString("matchPercent", null)?.toDoubleOrNull() } catch (_: ClassCastException) { null }
                    ?: try { prefs.getFloat("matchPercent", 1.0f).toDouble() } catch (_: ClassCastException) { 1.0 }
            ) }
            var matchDollar by remember { mutableIntStateOf(prefs.getInt("matchDollar", 1)) }
            var matchChars by remember { mutableIntStateOf(prefs.getInt("matchChars", 5)) }
            var weekStartSunday by remember { mutableStateOf(prefs.getBoolean("weekStartSunday", true)) }
            var chartPalette by remember { mutableStateOf(prefs.getString("chartPalette", "Sunset") ?: "Sunset") }
            var appLanguage by remember { mutableStateOf(prefs.getString("appLanguage", "en") ?: "en") }
            val strings: AppStrings = if (appLanguage == "es") SpanishStrings else EnglishStrings
            var budgetPeriod by remember {
                mutableStateOf(
                    try { BudgetPeriod.valueOf(prefs.getString("budgetPeriod", "DAILY") ?: "DAILY") }
                    catch (_: Exception) { BudgetPeriod.DAILY }
                )
            }
            var resetHour by remember { mutableIntStateOf(prefs.getInt("resetHour", 0)) }
            var resetDayOfWeek by remember { mutableIntStateOf(prefs.getInt("resetDayOfWeek", 7)) }
            var resetDayOfMonth by remember { mutableIntStateOf(prefs.getInt("resetDayOfMonth", 1)) }

            // Budget state
            var isManualBudgetEnabled by remember { mutableStateOf(prefs.getBoolean("isManualBudgetEnabled", false)) }
            var manualBudgetAmount by remember { mutableDoubleStateOf(
                prefs.getString("manualBudgetAmount", null)?.toDoubleOrNull() ?: 0.0
            ) }
            var availableCash by remember { mutableDoubleStateOf(
                prefs.getString("availableCash", null)?.toDoubleOrNull() ?: 0.0
            ) }
            var budgetStartDate by remember {
                mutableStateOf<LocalDate?>(
                    prefs.getString("budgetStartDate", null)?.let { LocalDate.parse(it) }
                )
            }
            var lastRefreshDate by remember {
                mutableStateOf<LocalDate?>(
                    prefs.getString("lastRefreshDate", null)?.let { LocalDate.parse(it) }
                )
            }

            val transactions = remember {
                mutableStateListOf(*TransactionRepository.load(context).toTypedArray())
            }
            val categories = remember {
                val loaded = CategoryRepository.load(context).toMutableList()
                var changed = false

                for (def in DEFAULT_CATEGORY_DEFS) {
                    val byTag = loaded.indexOfFirst { it.tag == def.tag }
                    if (byTag >= 0) continue
                    val usedIds = loaded.map { it.id }.toSet()
                    var id: Int
                    do { id = (0..65535).random() } while (id in usedIds)
                    val name = getDefaultCategoryName(def.tag, strings) ?: def.tag
                    loaded.add(Category(id = id, name = name, iconName = def.iconName, tag = def.tag))
                    changed = true
                }
                if (changed) CategoryRepository.save(context, loaded)
                mutableStateListOf(*loaded.toTypedArray())
            }

            val incomeSources = remember {
                mutableStateListOf(*IncomeSourceRepository.load(context).toTypedArray())
            }

            val recurringExpenses = remember {
                mutableStateListOf(*RecurringExpenseRepository.load(context).toTypedArray())
            }

            val amortizationEntries = remember {
                mutableStateListOf(*AmortizationRepository.load(context).toTypedArray())
            }

            val savingsGoals = remember {
                mutableStateListOf(*SavingsGoalRepository.load(context).toTypedArray())
            }

            fun saveIncomeSources() {
                IncomeSourceRepository.save(context, incomeSources.toList())
            }

            fun saveRecurringExpenses() {
                RecurringExpenseRepository.save(context, recurringExpenses.toList())
            }

            fun saveAmortizationEntries() {
                AmortizationRepository.save(context, amortizationEntries.toList())
            }

            fun saveSavingsGoals() {
                SavingsGoalRepository.save(context, savingsGoals.toList())
            }

            fun saveTransactions() {
                TransactionRepository.save(context, transactions.toList())
            }

            fun saveCategories() {
                CategoryRepository.save(context, categories.toList())
            }

            // persistAvailableCash declared after sync state variables below

            // Derived safeBudgetAmount — auto-recalculates when income/expenses change
            val safeBudgetAmount by remember {
                derivedStateOf {
                    BudgetCalculator.calculateSafeBudgetAmount(
                        incomeSources.toList().active,
                        recurringExpenses.toList().active,
                        budgetPeriod
                    )
                }
            }

            // Derived budgetAmount
            val budgetAmount by remember {
                derivedStateOf {
                    val base = if (isManualBudgetEnabled) manualBudgetAmount else safeBudgetAmount
                    val amortDeductions = BudgetCalculator.activeAmortizationDeductions(
                        amortizationEntries.toList().active, budgetPeriod
                    )
                    val savingsDeductions = BudgetCalculator.activeSavingsGoalDeductions(
                        savingsGoals.toList().active, budgetPeriod
                    )
                    BudgetCalculator.roundCents(maxOf(0.0, base - amortDeductions - savingsDeductions))
                }
            }

            // Period ledger
            val periodLedger = remember {
                mutableStateListOf(*PeriodLedgerRepository.load(context).toTypedArray())
            }

            fun savePeriodLedger() {
                PeriodLedgerRepository.save(context, periodLedger.toList())
            }

            // ── Shared Settings (for sync) ──
            var sharedSettings by remember { mutableStateOf(SharedSettingsRepository.load(context)) }

            // ── Family Sync state ──
            val syncPrefs = remember { context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE) }
            var isSyncConfigured by remember { mutableStateOf(GroupManager.isConfigured(context)) }
            var syncGroupId by remember { mutableStateOf(GroupManager.getGroupId(context)) }
            var isSyncAdmin by remember { mutableStateOf(GroupManager.isAdmin(context)) }
            var syncStatus by remember { mutableStateOf(if (GroupManager.isConfigured(context)) "synced" else "off") }
            var lastSyncTime by remember { mutableStateOf<String?>(null) }
            var syncDevices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
            var generatedPairingCode by remember { mutableStateOf<String?>(null) }
            val localDeviceId = remember { SyncIdGenerator.getOrCreateDeviceId(context) }
            val coroutineScope = rememberCoroutineScope()
            var staleDays by remember { mutableIntStateOf(0) }
            var syncErrorMessage by remember { mutableStateOf<String?>(null) }
            var pendingAdminClaim by remember { mutableStateOf<AdminClaim?>(null) }

            // availableCash may go negative (= overspent). Guard against NaN/Infinity.
            fun persistAvailableCash() {
                if (availableCash.isNaN() || availableCash.isInfinite()) availableCash = 0.0
                availableCash = BudgetCalculator.roundCents(availableCash)
                prefs.edit().putString("availableCash", availableCash.toString()).apply()
            }

            // Deterministic cash recomputation from synced data.
            // All devices with the same synced data compute the same result.
            fun recomputeCash() {
                if (budgetStartDate == null) return
                availableCash = BudgetCalculator.recomputeAvailableCash(
                    budgetStartDate!!, periodLedger.toList(),
                    transactions.toList().active, recurringExpenses.toList().active
                )
                persistAvailableCash()
            }

            // One-time migration: fix stale budget-start ledger entry.
            // If the entry on budgetStartDate has a clock older than
            // budgetStartDate_clock, it's from before the reset and wrong.
            LaunchedEffect(Unit) {
                if (!syncPrefs.getBoolean("migration_fix_stale_budgetstart_ledger_ui", false)) {
                    val bsd = budgetStartDate
                    if (bsd != null) {
                        val bsdEpochDay = bsd.toEpochDay().toInt()
                        val bsdEntry = periodLedger.find { it.id == bsdEpochDay }
                        if (bsdEntry != null && bsdEntry.clock < sharedSettings.budgetStartDate_clock) {
                            val nextDayEntry = periodLedger.find { it.id == bsdEpochDay + 1 }
                            val correctAmount = nextDayEntry?.appliedAmount ?: budgetAmount
                            val lamportClock = LamportClock(context)
                            val migClock = lamportClock.tick()
                            val idx = periodLedger.indexOfFirst { it.id == bsdEpochDay }
                            if (idx >= 0) {
                                periodLedger[idx] = periodLedger[idx].copy(
                                    appliedAmount = correctAmount,
                                    clock = migClock,
                                    deviceId = localDeviceId
                                )
                                savePeriodLedger()
                            }
                        }
                    }
                    syncPrefs.edit().putBoolean("migration_fix_stale_budgetstart_ledger_ui", true).apply()
                }
                // Admin re-stamps ALL period ledger entries so they get re-pushed
                // to non-admin devices that may be missing entries.
                if (!syncPrefs.getBoolean("migration_restamp_all_period_ledger_ui", false)) {
                    if (isSyncAdmin && periodLedger.isNotEmpty()) {
                        val lc = LamportClock(context)
                        val migClock = lc.tick()
                        for (i in periodLedger.indices) {
                            periodLedger[i] = periodLedger[i].copy(
                                clock = migClock, deviceId = localDeviceId
                            )
                        }
                        savePeriodLedger()
                    }
                    syncPrefs.edit().putBoolean("migration_restamp_all_period_ledger_ui", true).apply()
                }
                recomputeCash()
            }

            // Check if an expense transaction is fully accounted for in the budget
            // (amortization entries are built into the safe budget amount)
            fun isBudgetAccountedExpense(txn: Transaction): Boolean {
                if (txn.type != TransactionType.EXPENSE) return false
                return txn.linkedAmortizationEntryId != null
            }

            // For recurring-linked expenses, returns the cash effect (recurringAmount - txnAmount).
            // Positive = saved money, negative = overspent. Null = not a recurring-linked expense.
            fun recurringLinkCashEffect(txn: Transaction): Double? {
                if (txn.type != TransactionType.EXPENSE || txn.linkedRecurringExpenseId == null) return null
                val re = recurringExpenses.find { it.id == txn.linkedRecurringExpenseId } ?: return null
                return re.amount - txn.amount
            }

            // Foreground sync loop
            LaunchedEffect(isSyncConfigured) {
                if (!isSyncConfigured) return@LaunchedEffect
                val groupId = GroupManager.getGroupId(context) ?: return@LaunchedEffect
                val key = GroupManager.getEncryptionKey(context) ?: return@LaunchedEffect
                val engine = SyncEngine(context, groupId, localDeviceId, key, lamportClock)

                // Initial device list fetch
                try {
                    syncDevices = GroupManager.getDevices(groupId)
                } catch (e: Exception) {
                    android.util.Log.w("SyncLoop", "Failed to fetch initial device list", e)
                }

                // One-time migration: stamp tag_clock on categories that have
                // non-empty tags but tag_clock=0 (created before tag_clock was added).
                // Without this, tags are never included in sync deltas.
                if (!syncPrefs.getBoolean("migration_tag_clock_done", false)) {
                    var stamped = false
                    val migClock = lamportClock.tick()
                    categories.forEachIndexed { i, c ->
                        if (c.tag.isNotEmpty() && c.tag_clock == 0L) {
                            categories[i] = c.copy(tag_clock = migClock)
                            stamped = true
                        }
                    }
                    if (stamped) saveCategories()
                    syncPrefs.edit().putBoolean("migration_tag_clock_done", true).apply()
                }

                // One-time migration: stamp description_clock on records with non-blank descriptions.
                // Only stamps non-blank descriptions so non-admin devices (with empty descriptions)
                // don't create competing clock values that would overwrite real descriptions.
                if (!syncPrefs.getBoolean("migration_description_clock_done_v2", false)) {
                    val migClock = lamportClock.tick()
                    var changed = false
                    transactions.forEachIndexed { i, t ->
                        if (t.description.isNotBlank() && t.description_clock == 0L) {
                            transactions[i] = t.copy(description_clock = migClock)
                            changed = true
                        }
                    }
                    if (changed) saveTransactions()
                    changed = false
                    recurringExpenses.forEachIndexed { i, r ->
                        if (r.description.isNotBlank() && r.description_clock == 0L) {
                            recurringExpenses[i] = r.copy(description_clock = migClock)
                            changed = true
                        }
                    }
                    if (changed) saveRecurringExpenses()
                    changed = false
                    incomeSources.forEachIndexed { i, s ->
                        if (s.description.isNotBlank() && s.description_clock == 0L) {
                            incomeSources[i] = s.copy(description_clock = migClock)
                            changed = true
                        }
                    }
                    if (changed) saveIncomeSources()
                    changed = false
                    amortizationEntries.forEachIndexed { i, e ->
                        if (e.description.isNotBlank() && e.description_clock == 0L) {
                            amortizationEntries[i] = e.copy(description_clock = migClock)
                            changed = true
                        }
                    }
                    if (changed) saveAmortizationEntries()
                    syncPrefs.edit().putBoolean("migration_description_clock_done_v2", true).apply()
                }

                // One-time cleanup: remove skeleton categories (name_clock=0, empty name)
                // that were created as local defaults but superseded by real synced categories.
                // The catIdRemap already redirects any transaction references.
                if (!syncPrefs.getBoolean("migration_remove_skeleton_categories", false)) {
                    val before = categories.size
                    categories.removeAll { it.name.isEmpty() && it.name_clock == 0L }
                    if (categories.size < before) saveCategories()
                    syncPrefs.edit().putBoolean("migration_remove_skeleton_categories", true).apply()
                }

                while (true) {
                    // File-based lock works across processes (unlike ReentrantLock)
                    val syncFileLock = SyncWorker.createSyncLock(context)
                    if (!syncFileLock.tryLock()) {
                        delay(5_000)
                        continue
                    }
                    try {
                        syncStatus = "syncing"
                        // Load persisted category ID remap
                        val remapJson = syncPrefs.getString("catIdRemap", null)
                        val existingRemap = if (remapJson != null) {
                            try {
                                val json = org.json.JSONObject(remapJson)
                                json.keys().asSequence().associate { it.toInt() to json.getInt(it) }
                            } catch (_: Exception) { emptyMap() }
                        } else emptyMap<Int, Int>()

                        val result = engine.sync(
                            transactions.toList(),
                            recurringExpenses.toList(),
                            incomeSources.toList(),
                            savingsGoals.toList(),
                            amortizationEntries.toList(),
                            categories.toList(),
                            sharedSettings,
                            existingCatIdRemap = existingRemap,
                            periodLedgerEntries = periodLedger.toList()
                        )
                        if (result.success) {
                            Snapshot.withMutableSnapshot {
                                result.mergedTransactions?.let { merged ->
                                    transactions.clear()
                                    transactions.addAll(merged)
                                }
                                result.mergedRecurringExpenses?.let { merged ->
                                    recurringExpenses.clear()
                                    recurringExpenses.addAll(merged)
                                }
                                result.mergedIncomeSources?.let { merged ->
                                    incomeSources.clear()
                                    incomeSources.addAll(merged)
                                }
                                result.mergedSavingsGoals?.let { merged ->
                                    savingsGoals.clear()
                                    savingsGoals.addAll(merged)
                                }
                                result.mergedAmortizationEntries?.let { merged ->
                                    amortizationEntries.clear()
                                    amortizationEntries.addAll(merged)
                                }
                                result.mergedCategories?.let { merged ->
                                    categories.clear()
                                    categories.addAll(merged)
                                }
                                result.mergedPeriodLedgerEntries?.let { merged ->
                                    periodLedger.clear()
                                    periodLedger.addAll(merged)
                                }
                            }
                            result.mergedTransactions?.let { saveTransactions() }
                            result.mergedRecurringExpenses?.let { saveRecurringExpenses() }
                            result.mergedIncomeSources?.let { saveIncomeSources() }
                            result.mergedSavingsGoals?.let { saveSavingsGoals() }
                            result.mergedAmortizationEntries?.let { saveAmortizationEntries() }
                            result.mergedCategories?.let { saveCategories() }
                            result.mergedPeriodLedgerEntries?.let { savePeriodLedger() }
                            result.mergedSharedSettings?.let { merged ->
                                sharedSettings = merged
                                // Apply synced settings to local state
                                currencySymbol = merged.currency
                                budgetPeriod = try { BudgetPeriod.valueOf(merged.budgetPeriod) } catch (_: Exception) { budgetPeriod }
                                resetHour = merged.resetHour
                                resetDayOfWeek = merged.resetDayOfWeek
                                resetDayOfMonth = merged.resetDayOfMonth
                                isManualBudgetEnabled = merged.isManualBudgetEnabled
                                manualBudgetAmount = merged.manualBudgetAmount
                                weekStartSunday = merged.weekStartSunday
                                matchDays = merged.matchDays
                                matchPercent = merged.matchPercent
                                matchDollar = merged.matchDollar
                                matchChars = merged.matchChars
                                // Apply synced budgetStartDate
                                val syncedStartDate = merged.budgetStartDate?.let {
                                    try { LocalDate.parse(it) } catch (_: Exception) { null }
                                }
                                val budgetStartChanged = syncedStartDate != null && syncedStartDate != budgetStartDate
                                if (budgetStartChanged) {
                                    budgetStartDate = syncedStartDate
                                    lastRefreshDate = LocalDate.now()
                                }
                                // Write all synced settings to app_prefs
                                val prefsEditor = prefs.edit()
                                    .putString("currencySymbol", merged.currency)
                                    .putString("budgetPeriod", merged.budgetPeriod)
                                    .putInt("resetHour", merged.resetHour)
                                    .putInt("resetDayOfWeek", merged.resetDayOfWeek)
                                    .putInt("resetDayOfMonth", merged.resetDayOfMonth)
                                    .putBoolean("isManualBudgetEnabled", merged.isManualBudgetEnabled)
                                    .putString("manualBudgetAmount", merged.manualBudgetAmount.toString())
                                    .putBoolean("weekStartSunday", merged.weekStartSunday)
                                    .putInt("matchDays", merged.matchDays)
                                    .putString("matchPercent", merged.matchPercent.toString())
                                    .putInt("matchDollar", merged.matchDollar)
                                    .putInt("matchChars", merged.matchChars)
                                if (budgetStartChanged) {
                                    prefsEditor
                                        .putString("budgetStartDate", budgetStartDate.toString())
                                        .putString("lastRefreshDate", lastRefreshDate.toString())
                                }
                                prefsEditor.apply()
                            }
                            // Recompute cash from synced data
                            recomputeCash()
                            // Persist updated category ID remap
                            result.catIdRemap?.let { remap ->
                                val json = org.json.JSONObject(remap.mapKeys { it.key.toString() })
                                syncPrefs.edit().putString("catIdRemap", json.toString()).apply()
                            }
                            syncStatus = "synced"
                            syncErrorMessage = null
                            lastSyncTime = "just now"
                            pendingAdminClaim = result.pendingAdminClaim
                            // Compute stale days
                            val lastSync = syncPrefs.getLong("lastSuccessfulSync", 0L)
                            staleDays = if (lastSync > 0L) ((System.currentTimeMillis() - lastSync) / (24 * 60 * 60 * 1000L)).toInt() else 0
                            // Refresh device list & admin status
                            try {
                                syncDevices = GroupManager.getDevices(groupId)
                                isSyncAdmin = GroupManager.isAdmin(context)
                            } catch (e: Exception) {
                                android.util.Log.w("SyncLoop", "Failed to refresh device list", e)
                            }
                        } else {
                            syncStatus = "error"
                            syncErrorMessage = result.error
                            pendingAdminClaim = result.pendingAdminClaim
                            // Handle auto-leave on removal
                            if (result.error == "removed_from_group" || result.error == "group_deleted") {
                                GroupManager.leaveGroup(context)
                                syncPrefs.edit()
                                    .remove("catIdRemap")
                                    .remove("lastSyncVersion")
                                    .remove("lastPushedClock")
                                    .remove("lastSuccessfulSync")
                                    .apply()
                                isSyncConfigured = false
                                syncGroupId = null
                                isSyncAdmin = false
                                syncStatus = "off"
                                lastSyncTime = null
                                syncDevices = emptyList()
                                pendingAdminClaim = null
                                staleDays = 0
                                syncErrorMessage = null
                                return@LaunchedEffect
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SyncLoop", "Foreground sync failed", e)
                        syncStatus = "error"
                    } finally {
                        syncFileLock.unlock()
                    }
                    delay(60_000)
                }
            }

            // Schedule background sync when configured
            LaunchedEffect(isSyncConfigured) {
                if (isSyncConfigured) {
                    SyncWorker.schedule(context)
                }
            }

            // Percent tolerance for matching
            val percentTolerance = matchPercent / 100.0

            // Helper to add a transaction with budget effects
            fun addTransactionWithBudgetEffect(txn: Transaction) {
                val clock = lamportClock.tick()
                val stamped = txn.copy(
                    deviceId = localDeviceId,
                    source_clock = clock,
                    description_clock = clock,
                    amount_clock = clock,
                    date_clock = clock,
                    type_clock = clock,
                    categoryAmounts_clock = clock,
                    isUserCategorized_clock = clock,
                    isBudgetIncome_clock = clock,
                    linkedRecurringExpenseId_clock = clock,
                    linkedAmortizationEntryId_clock = clock,
                    deviceId_clock = clock
                )
                transactions.add(stamped)
                saveTransactions()
                recomputeCash()
            }

            // Matching chain for dashboard-added transactions
            fun runMatchingChain(txn: Transaction) {
                val alreadyLinked = txn.linkedRecurringExpenseId != null || txn.linkedAmortizationEntryId != null
                val activeTransactions = transactions.toList().active
                val activeRecurring = recurringExpenses.toList().active
                val activeAmort = amortizationEntries.toList().active
                val activeIncome = incomeSources.toList().active
                val dup = findDuplicate(txn, activeTransactions, percentTolerance, matchDollar, matchDays, matchChars)
                if (dup != null) {
                    dashPendingManualSave = txn
                    dashManualDuplicateMatch = dup
                    dashShowManualDuplicateDialog = true
                } else if (!alreadyLinked) {
                    val recurringMatch = findRecurringExpenseMatch(txn, activeRecurring, percentTolerance, matchDollar, matchChars, matchDays)
                    if (recurringMatch != null) {
                        dashPendingRecurringTxn = txn
                        dashPendingRecurringMatch = recurringMatch
                        dashShowRecurringDialog = true
                    } else {
                        val amortizationMatch = findAmortizationMatch(txn, activeAmort, percentTolerance, matchDollar, matchChars)
                        if (amortizationMatch != null) {
                            dashPendingAmortizationTxn = txn
                            dashPendingAmortizationMatch = amortizationMatch
                            dashShowAmortizationDialog = true
                        } else {
                            val budgetMatch = findBudgetIncomeMatch(txn, activeIncome, matchChars, matchDays)
                            if (budgetMatch != null) {
                                dashPendingBudgetIncomeTxn = txn
                                dashPendingBudgetIncomeMatch = budgetMatch
                                dashShowBudgetIncomeDialog = true
                            } else {
                                addTransactionWithBudgetEffect(txn)
                            }
                        }
                    }
                } else {
                    addTransactionWithBudgetEffect(txn)
                }
            }

            val dateFormatter = remember(dateFormatPattern) {
                DateTimeFormatter.ofPattern(dateFormatPattern)
            }
            val existingIds = transactions.map { it.id }.toSet()
            val categoryMap = categories.associateBy { it.id }

            // Period refresh — checks immediately on launch and every 30s
            // while the app is open so the UI updates when a period boundary
            // passes without needing a restart.
            LaunchedEffect(Unit) {
                while (true) {
                    if (budgetStartDate != null && lastRefreshDate != null) {
                        // For DAILY periods, respect resetHour: the budget "day" starts
                        // at resetHour, not midnight. Before resetHour we're still in
                        // yesterday's period.
                        val now = java.time.LocalDateTime.now()
                        val today = if (budgetPeriod == BudgetPeriod.DAILY && resetHour > 0 && now.hour < resetHour)
                            now.toLocalDate().minusDays(1)
                        else
                            now.toLocalDate()
                        val missedPeriods = BudgetCalculator.countPeriodsCompleted(lastRefreshDate!!, today, budgetPeriod)
                        if (missedPeriods > 0) {
                            lastRefreshDate = today

                            // Create one ledger entry per missed period with CRDT stamping
                            for (period in 0 until missedPeriods) {
                                val periodsBack = (missedPeriods - 1 - period).toLong()
                                val periodDate = when (budgetPeriod) {
                                    BudgetPeriod.DAILY -> today.minusDays(periodsBack)
                                    BudgetPeriod.WEEKLY -> today.minusWeeks(periodsBack)
                                    BudgetPeriod.MONTHLY -> today.minusMonths(periodsBack)
                                }
                                val alreadyRecorded = periodLedger.any {
                                    it.periodStartDate.toLocalDate() == periodDate
                                }
                                if (!alreadyRecorded) {
                                    val entryClock = lamportClock.tick()
                                    periodLedger.add(
                                        PeriodLedgerEntry(
                                            periodStartDate = periodDate.atStartOfDay(),
                                            appliedAmount = budgetAmount,
                                            clockAtReset = entryClock,
                                            deviceId = localDeviceId,
                                            clock = entryClock
                                        )
                                    )
                                }
                            }
                            savePeriodLedger()

                            // Update savings goals totalSavedSoFar for non-paused, non-complete items.
                            // Use the correct date for each catch-up period so periodsLeft
                            // decreases properly (instead of using today for all iterations).
                            for (period in 0 until missedPeriods) {
                                val periodsBack = (missedPeriods - 1 - period).toLong()
                                val periodDate = when (budgetPeriod) {
                                    BudgetPeriod.DAILY -> today.minusDays(periodsBack)
                                    BudgetPeriod.WEEKLY -> today.minusWeeks(periodsBack)
                                    BudgetPeriod.MONTHLY -> today.minusMonths(periodsBack)
                                }
                                savingsGoals.forEachIndexed { idx, goal ->
                                    if (!goal.isPaused && !goal.deleted) {
                                        val remaining = goal.targetAmount - goal.totalSavedSoFar
                                        if (remaining > 0) {
                                            if (goal.targetDate != null) {
                                                if (periodDate.isBefore(goal.targetDate)) {
                                                    val periods = when (budgetPeriod) {
                                                        BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(periodDate, goal.targetDate)
                                                        BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(periodDate, goal.targetDate)
                                                        BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(periodDate, goal.targetDate)
                                                    }
                                                    if (periods > 0) {
                                                        val deduction = BudgetCalculator.roundCents(minOf(remaining / periods.toDouble(), remaining))
                                                        savingsGoals[idx] = goal.copy(
                                                            totalSavedSoFar = goal.totalSavedSoFar + deduction
                                                        )
                                                    }
                                                }
                                            } else {
                                                val contribution = BudgetCalculator.roundCents(minOf(
                                                    goal.contributionPerPeriod,
                                                    remaining
                                                ))
                                                if (contribution > 0) {
                                                    savingsGoals[idx] = goal.copy(
                                                        totalSavedSoFar = goal.totalSavedSoFar + contribution
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            saveSavingsGoals()

                            recomputeCash()
                            prefs.edit()
                                .putString("lastRefreshDate", lastRefreshDate.toString())
                                .apply()
                        }
                    }
                    delay(30_000) // Re-check every 30 seconds
                }
            }

            // ── One-time CRDT state dump to Downloads ──
            remember {
                try {
                    val dlDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val dump = StringBuilder()
                    dump.appendLine("=== CRDT State Dump ${java.time.LocalDateTime.now()} ===")
                    dump.appendLine("DeviceId: $localDeviceId")
                    dump.appendLine("isAdmin: $isSyncAdmin")
                    dump.appendLine("isSyncConfigured: $isSyncConfigured")
                    dump.appendLine()
                    dump.appendLine("── App Prefs ──")
                    dump.appendLine("availableCash (prefs): ${prefs.getString("availableCash", "MISSING")}")
                    dump.appendLine("availableCash (state): $availableCash")
                    dump.appendLine("budgetStartDate: $budgetStartDate")
                    dump.appendLine("lastRefreshDate: $lastRefreshDate")
                    dump.appendLine("budgetPeriod: $budgetPeriod")
                    dump.appendLine("budgetAmount (derived): $budgetAmount")
                    dump.appendLine("safeBudgetAmount (derived): $safeBudgetAmount")
                    dump.appendLine("isManualBudget: $isManualBudgetEnabled  manualAmount: $manualBudgetAmount")
                    dump.appendLine()
                    dump.appendLine("── SharedSettings (CRDT) ──")
                    dump.appendLine("availableCash: ${sharedSettings.availableCash}  clock: ${sharedSettings.availableCash_clock}")
                    dump.appendLine("budgetStartDate: ${sharedSettings.budgetStartDate}  clock: ${sharedSettings.budgetStartDate_clock}")
                    dump.appendLine("budgetPeriod: ${sharedSettings.budgetPeriod}  clock: ${sharedSettings.budgetPeriod_clock}")
                    dump.appendLine("manualBudgetAmount: ${sharedSettings.manualBudgetAmount}  clock: ${sharedSettings.manualBudgetAmount_clock}")
                    dump.appendLine("isManualBudgetEnabled: ${sharedSettings.isManualBudgetEnabled}  clock: ${sharedSettings.isManualBudgetEnabled_clock}")
                    dump.appendLine("currency: ${sharedSettings.currency}  clock: ${sharedSettings.currency_clock}")
                    dump.appendLine("lastChangedBy: ${sharedSettings.lastChangedBy}")
                    dump.appendLine()
                    // Sync metadata
                    dump.appendLine("── Sync Metadata ──")
                    dump.appendLine("lastPushedClock: ${syncPrefs.getLong("lastPushedClock", 0L)}")
                    dump.appendLine("lastSyncVersion: ${syncPrefs.getLong("lastSyncVersion", 0L)}")
                    val remapJsonDump = syncPrefs.getString("catIdRemap", null)
                    dump.appendLine("catIdRemap: ${remapJsonDump ?: "(empty)"}")
                    dump.appendLine()

                    dump.appendLine("── Categories ──")
                    val categoryMap = categories.associateBy { it.id }
                    for (cat in categories.sortedBy { it.id }) {
                        dump.appendLine("  id=${cat.id} '${cat.name}' tag=${cat.tag} icon=${cat.iconName} dev=${cat.deviceId.take(8)}… del=${cat.deleted} nClk=${cat.name_clock} tClk=${cat.tag_clock} dIdClk=${cat.deviceId_clock}")
                    }
                    dump.appendLine()

                    dump.appendLine("── Transactions (active, in current period) ──")
                    val activeTxns = transactions.filter { !it.deleted }
                    val periodTxns = if (budgetStartDate != null) activeTxns.filter { !it.date.isBefore(budgetStartDate) } else activeTxns
                    var totalExpense = 0.0
                    var totalIncome = 0.0
                    for (txn in periodTxns.sortedBy { it.date }) {
                        val budgetAccounted = if (txn.type == TransactionType.EXPENSE) isBudgetAccountedExpense(txn) else false
                        val catDesc = if (txn.categoryAmounts.isEmpty()) "cats=NONE"
                            else txn.categoryAmounts.joinToString(",") { ca ->
                                val name = categoryMap[ca.categoryId]?.name ?: "???"
                                "${ca.categoryId}($name):${ca.amount}"
                            }
                        val linkDesc = listOfNotNull(
                            txn.linkedRecurringExpenseId?.let { "reId=$it(clk=${txn.linkedRecurringExpenseId_clock})" },
                            txn.linkedAmortizationEntryId?.let { "aeId=$it(clk=${txn.linkedAmortizationEntryId_clock})" }
                        ).joinToString(" ").ifEmpty { "" }
                        dump.appendLine("  ${txn.date} ${txn.type} ${txn.amount} '${txn.source}' dev=${txn.deviceId.take(8)}… ba=$budgetAccounted aClk=${txn.amount_clock} cClk=${txn.categoryAmounts_clock} dIdClk=${txn.deviceId_clock} $linkDesc $catDesc")
                        if (txn.type == TransactionType.EXPENSE && !budgetAccounted) totalExpense += txn.amount
                        else if (txn.type == TransactionType.INCOME && !txn.isBudgetIncome) totalIncome += txn.amount
                    }
                    dump.appendLine("Period expense total (non-budget-accounted): $totalExpense")
                    dump.appendLine("Period extra income total: $totalIncome")
                    dump.appendLine("Expected cash = budgetAmount($budgetAmount) - expenses($totalExpense) + income($totalIncome) = ${budgetAmount - totalExpense + totalIncome}")
                    dump.appendLine("Actual cash = $availableCash")
                    dump.appendLine("Difference = ${availableCash - (budgetAmount - totalExpense + totalIncome)}")
                    dump.appendLine()
                    dump.appendLine("── Period Ledger ──")
                    for (entry in periodLedger) {
                        dump.appendLine("  ${entry.periodStartDate} applied=${entry.appliedAmount} clock=${entry.clockAtReset}")
                    }
                    dump.appendLine("=== End CRDT Dump ===")
                    // Use MediaStore to write to Downloads (works without permissions on Android 10+)
                    val resolver = context.contentResolver
                    val existing = resolver.query(
                        android.provider.MediaStore.Files.getContentUri("external"),
                        arrayOf(android.provider.MediaStore.MediaColumns._ID),
                        "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                        arrayOf("sync_diag.txt", "Download/"),
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
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "sync_diag.txt")
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
                        }
                        resolver.insert(android.provider.MediaStore.Files.getContentUri("external"), values)
                    }
                    if (uri != null) {
                        resolver.openOutputStream(uri, "wa")?.use { it.write((dump.toString() + "\n").toByteArray()) }
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Diag write failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
                true
            }

            SyncBudgetTheme(strings = strings) {
              val adBannerHeight = if (!isPaidUser) 50.dp else 0.dp
              CompositionLocalProvider(LocalAdBannerHeight provides adBannerHeight) {
              Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                // Ad banner placeholder (320x50 standard banner)
                if (!isPaidUser) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(Color.Black)
                            .border(1.dp, Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ad", color = Color.Gray, fontSize = 12.sp)
                    }
                }

                // Screen content
                Box(modifier = Modifier.weight(1f)) {
                if (currentScreen != "main") {
                    BackHandler {
                        currentScreen = when (currentScreen) {
                            "settings_help" -> "settings"
                            "transactions_help" -> "transactions"
                            "future_expenditures_help" -> "future_expenditures"
                            "amortization_help" -> "amortization"
                            "recurring_expenses_help" -> "recurring_expenses"
                            "budget_config_help" -> "budget_config"
                            "budget_config" -> "settings"
                            "family_sync" -> "settings"
                            "family_sync_help" -> "family_sync"
                            else -> "main"
                        }
                    }
                }

                when (currentScreen) {
                    "main" -> MainScreen(
                        soundPlayer = soundPlayer,
                        currencySymbol = currencySymbol,
                        digitCount = digitCount,
                        showDecimals = showDecimals,
                        availableCash = availableCash,
                        budgetAmount = budgetAmount,
                        budgetStartDate = budgetStartDate?.toString(),
                        budgetPeriodLabel = when (budgetPeriod) {
                            BudgetPeriod.DAILY -> strings.common.periodDay
                            BudgetPeriod.WEEKLY -> strings.common.periodWeek
                            BudgetPeriod.MONTHLY -> strings.common.periodMonth
                        },
                        savingsGoals = savingsGoals.toList().active,
                        transactions = transactions.toList().active,
                        categories = categories.toList().active,
                        onSettingsClick = { currentScreen = "settings" },
                        onNavigate = { currentScreen = it },
                        onAddIncome = {
                            dashboardShowAddIncome = true
                        },
                        onAddExpense = {
                            dashboardShowAddExpense = true
                        },
                        weekStartDay = if (weekStartSunday) java.time.DayOfWeek.SUNDAY else java.time.DayOfWeek.MONDAY,
                        chartPalette = chartPalette,
                        dateFormatPattern = dateFormatPattern,
                        budgetPeriod = budgetPeriod,
                        syncStatus = syncStatus,
                        staleDays = staleDays,
                        syncDevices = syncDevices,
                        localDeviceId = localDeviceId,
                        onSupercharge = { allocations, modes ->
                            val deposits = mutableListOf<Pair<String, Double>>() // goalName to capped amount
                            for ((goalId, amount) in allocations) {
                                val idx = savingsGoals.indexOfFirst { it.id == goalId }
                                if (idx >= 0) {
                                    val goal = savingsGoals[idx]
                                    val remaining = goal.targetAmount - goal.totalSavedSoFar
                                    val capped = minOf(amount, remaining)
                                    if (capped > 0) {
                                        val newRemaining = remaining - capped
                                        val mode = modes[goalId]
                                        val updatedGoal = if (
                                            goal.targetDate != null &&
                                            mode == SuperchargeMode.ACHIEVE_SOONER
                                        ) {
                                            val currentContribution = calculatePerPeriodDeduction(goal, budgetPeriod)
                                            if (currentContribution > 0 && newRemaining > 0) {
                                                val periodsNeeded = ceil(newRemaining / currentContribution).toLong()
                                                val today = LocalDate.now()
                                                val newTargetDate = when (budgetPeriod) {
                                                    BudgetPeriod.DAILY -> today.plusDays(periodsNeeded)
                                                    BudgetPeriod.WEEKLY -> today.plusWeeks(periodsNeeded)
                                                    BudgetPeriod.MONTHLY -> today.plusMonths(periodsNeeded)
                                                }
                                                goal.copy(
                                                    totalSavedSoFar = goal.totalSavedSoFar + capped,
                                                    targetDate = newTargetDate
                                                )
                                            } else {
                                                goal.copy(totalSavedSoFar = goal.totalSavedSoFar + capped)
                                            }
                                        } else if (
                                            goal.targetDate == null &&
                                            goal.contributionPerPeriod > 0 &&
                                            mode == SuperchargeMode.REDUCE_CONTRIBUTIONS
                                        ) {
                                            val currentPeriodsRemaining = ceil(
                                                remaining / goal.contributionPerPeriod
                                            ).toLong()
                                            val newContribution = if (currentPeriodsRemaining > 0 && newRemaining > 0)
                                                newRemaining / currentPeriodsRemaining.toDouble()
                                            else 0.0
                                            goal.copy(
                                                totalSavedSoFar = goal.totalSavedSoFar + capped,
                                                contributionPerPeriod = newContribution
                                            )
                                        } else {
                                            goal.copy(
                                                totalSavedSoFar = goal.totalSavedSoFar + capped
                                            )
                                        }
                                        savingsGoals[idx] = updatedGoal
                                        deposits.add(goal.name to capped)
                                    }
                                }
                            }
                            if (deposits.isNotEmpty()) {
                                saveSavingsGoals()
                                // Create expense transactions so recomputation includes
                                // the cash outflow (CRDT-correct — syncs to other devices).
                                val currentIds = transactions.map { it.id }.toSet()
                                for ((goalName, depositAmount) in deposits) {
                                    val txn = Transaction(
                                        id = generateTransactionId(currentIds + transactions.map { it.id }.toSet()),
                                        source = "Savings: $goalName",
                                        amount = depositAmount,
                                        date = LocalDate.now(),
                                        type = TransactionType.EXPENSE
                                    )
                                    addTransactionWithBudgetEffect(txn)
                                }
                            }
                        }
                    )
                    "settings" -> SettingsScreen(
                        currencySymbol = currencySymbol,
                        appLanguage = appLanguage,
                        onLanguageChange = { lang ->
                            appLanguage = lang
                            prefs.edit().putString("appLanguage", lang).apply()
                            val newStrings: AppStrings = if (lang == "es") SpanishStrings else EnglishStrings
                            var catChanged = false
                            categories.forEachIndexed { idx, cat ->
                                if (cat.tag.isNotEmpty()) {
                                    val allKnown = getAllKnownNamesForTag(cat.tag)
                                    if (cat.name in allKnown) {
                                        val newName = getDefaultCategoryName(cat.tag, newStrings)
                                        if (newName != null && newName != cat.name) {
                                            categories[idx] = cat.copy(name = newName)
                                            catChanged = true
                                        }
                                    }
                                }
                            }
                            if (catChanged) saveCategories()
                        },
                        onNavigateToBudgetConfig = { currentScreen = "budget_config" },
                        onNavigateToFamilySync = { currentScreen = "family_sync" },
                        matchDays = matchDays,
                        onMatchDaysChange = {
                            matchDays = it; prefs.edit().putInt("matchDays", it).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(matchDays = it, matchDays_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        matchPercent = matchPercent,
                        onMatchPercentChange = {
                            matchPercent = it; prefs.edit().putString("matchPercent", it.toString()).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(matchPercent = it, matchPercent_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        matchDollar = matchDollar,
                        onMatchDollarChange = {
                            matchDollar = it; prefs.edit().putInt("matchDollar", it).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(matchDollar = it, matchDollar_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        matchChars = matchChars,
                        onMatchCharsChange = {
                            matchChars = it; prefs.edit().putInt("matchChars", it).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(matchChars = it, matchChars_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        chartPalette = chartPalette,
                        onChartPaletteChange = { chartPalette = it; prefs.edit().putString("chartPalette", it).apply() },
                        budgetPeriod = budgetPeriod.name,
                        weekStartSunday = weekStartSunday,
                        onWeekStartChange = {
                            weekStartSunday = it; prefs.edit().putBoolean("weekStartSunday", it).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(weekStartSunday = it, weekStartSunday_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        onCurrencyChange = {
                            currencySymbol = it
                            prefs.edit().putString("currencySymbol", it).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(currency = it, currency_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        isSyncConfigured = isSyncConfigured,
                        isAdmin = isSyncAdmin,
                        showDecimals = showDecimals,
                        onDecimalsChange = {
                            showDecimals = it
                            prefs.edit().putBoolean("showDecimals", it).apply()
                        },
                        dateFormatPattern = dateFormatPattern,
                        onDateFormatChange = {
                            dateFormatPattern = it
                            prefs.edit().putString("dateFormatPattern", it).apply()
                        },
                        isPaidUser = isPaidUser,
                        onPaidUserChange = { newValue ->
                            isPaidUser = newValue
                            prefs.edit().putBoolean("isPaidUser", newValue).apply()
                        },
                        categories = categories.toList().active,
                        transactions = transactions.toList().active,
                        onAddCategory = { cat ->
                            val clock = lamportClock.tick()
                            categories.add(cat.copy(
                                deviceId = localDeviceId,
                                name_clock = clock,
                                iconName_clock = clock,
                                tag_clock = if (cat.tag.isNotEmpty()) clock else 0L,
                                deviceId_clock = clock
                            ))
                            saveCategories()
                        },
                        onUpdateCategory = { updated ->
                            val idx = categories.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = categories[idx]
                                val clock = lamportClock.tick()
                                // Don't overwrite deviceId — it's a CRDT field
                                // tracking the original creator, not the last editor.
                                // Only stamp the fields that actually changed.
                                categories[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deviceId_clock = old.deviceId_clock,
                                    deleted = old.deleted,
                                    deleted_clock = old.deleted_clock,
                                    tag_clock = old.tag_clock,
                                    name_clock = if (updated.name != old.name) clock else old.name_clock,
                                    iconName_clock = if (updated.iconName != old.iconName) clock else old.iconName_clock
                                )
                                saveCategories()
                            }
                        },
                        onDeleteCategory = { cat ->
                            val idx = categories.indexOfFirst { it.id == cat.id }
                            if (idx >= 0) {
                                categories[idx] = categories[idx].copy(deleted = true, deleted_clock = lamportClock.tick())
                                saveCategories()
                            }
                        },
                        onReassignCategory = { fromId, toId ->
                            val clock = lamportClock.tick()
                            transactions.forEachIndexed { index, txn ->
                                val updated = txn.categoryAmounts.map { ca ->
                                    if (ca.categoryId == fromId) {
                                        // Check if toId already exists in this transaction
                                        val existingTo = txn.categoryAmounts.find { it.categoryId == toId }
                                        if (existingTo != null) ca.copy(categoryId = -1) // mark for merge
                                        else ca.copy(categoryId = toId)
                                    } else ca
                                }
                                // Merge amounts if both fromId and toId existed
                                val markedForMerge = updated.find { it.categoryId == -1 }
                                val finalAmounts = if (markedForMerge != null) {
                                    val mergedAmount = (updated.find { it.categoryId == toId }?.amount ?: 0.0) + markedForMerge.amount
                                    updated.filter { it.categoryId != -1 && it.categoryId != toId } +
                                        CategoryAmount(toId, mergedAmount)
                                } else updated
                                if (finalAmounts != txn.categoryAmounts) {
                                    transactions[index] = txn.copy(
                                        categoryAmounts = finalAmounts,
                                        categoryAmounts_clock = clock
                                    )
                                }
                            }
                            saveTransactions()
                        },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "settings_help" }
                    )
                    "transactions" -> TransactionsScreen(
                        transactions = transactions.toList().active,
                        currencySymbol = currencySymbol,
                        dateFormatPattern = dateFormatPattern,
                        categories = categories.toList().active,
                        isPaidUser = isPaidUser,
                        recurringExpenses = recurringExpenses.toList().active,
                        amortizationEntries = amortizationEntries.toList().active,
                        incomeSources = incomeSources.toList().active,
                        matchDays = matchDays,
                        matchPercent = matchPercent,
                        matchDollar = matchDollar,
                        matchChars = matchChars,
                        chartPalette = chartPalette,
                        showAttribution = sharedSettings.showAttribution && isSyncConfigured,
                        deviceNameMap = syncDevices.associate { it.deviceId to it.deviceName.ifEmpty { it.deviceId.take(8) } },
                        localDeviceId = localDeviceId,
                        onAddTransaction = { txn ->
                            addTransactionWithBudgetEffect(txn)
                        },
                        onUpdateTransaction = { updated ->
                            val old = transactions.find { it.id == updated.id }
                            val index = transactions.indexOfFirst { it.id == updated.id }
                            if (index >= 0) {
                                val prev = transactions[index]
                                val clock = lamportClock.tick()
                                // Don't overwrite deviceId — it's a CRDT field
                                // tracking the original creator, not the last editor.
                                transactions[index] = updated.copy(
                                    deviceId = prev.deviceId,
                                    deviceId_clock = prev.deviceId_clock,
                                    deleted = prev.deleted,
                                    deleted_clock = prev.deleted_clock,
                                    source_clock = if (updated.source != prev.source) clock else prev.source_clock,
                                    description_clock = if (updated.description != prev.description) clock else prev.description_clock,
                                    amount_clock = if (updated.amount != prev.amount) clock else prev.amount_clock,
                                    date_clock = if (updated.date != prev.date) clock else prev.date_clock,
                                    type_clock = if (updated.type != prev.type) clock else prev.type_clock,
                                    categoryAmounts_clock = if (updated.categoryAmounts != prev.categoryAmounts) clock else prev.categoryAmounts_clock,
                                    isUserCategorized_clock = if (updated.isUserCategorized != prev.isUserCategorized) clock else prev.isUserCategorized_clock,
                                    isBudgetIncome_clock = if (updated.isBudgetIncome != prev.isBudgetIncome) clock else prev.isBudgetIncome_clock,
                                    linkedRecurringExpenseId_clock = if (updated.linkedRecurringExpenseId != prev.linkedRecurringExpenseId) clock else prev.linkedRecurringExpenseId_clock,
                                    linkedAmortizationEntryId_clock = if (updated.linkedAmortizationEntryId != prev.linkedAmortizationEntryId) clock else prev.linkedAmortizationEntryId_clock
                                )
                                saveTransactions()
                            }
                            recomputeCash()
                        },
                        onDeleteTransaction = { txn ->
                            val idx = transactions.indexOfFirst { it.id == txn.id }
                            if (idx >= 0) {
                                transactions[idx] = transactions[idx].copy(
                                    deleted = true,
                                    deleted_clock = lamportClock.tick()
                                )
                                saveTransactions()
                            }
                            recomputeCash()
                        },
                        onDeleteTransactions = { ids ->
                            val clock = lamportClock.tick()
                            transactions.forEachIndexed { index, txn ->
                                if (txn.id in ids && !txn.deleted) {
                                    transactions[index] = txn.copy(
                                        deleted = true,
                                        deleted_clock = clock
                                    )
                                }
                            }
                            saveTransactions()
                            recomputeCash()
                        },
                        onSerializeFullBackup = {
                            FullBackupSerializer.serialize(context)
                        },
                        onLoadFullBackup = { jsonContent ->
                            FullBackupSerializer.restoreFullState(context, jsonContent)

                            // Reload all lists from repositories
                            val newTxns = TransactionRepository.load(context)
                            transactions.clear(); transactions.addAll(newTxns)

                            val newCats = CategoryRepository.load(context)
                            categories.clear(); categories.addAll(newCats)

                            val newRE = RecurringExpenseRepository.load(context)
                            recurringExpenses.clear(); recurringExpenses.addAll(newRE)

                            val newIS = IncomeSourceRepository.load(context)
                            incomeSources.clear(); incomeSources.addAll(newIS)

                            val newAE = AmortizationRepository.load(context)
                            amortizationEntries.clear(); amortizationEntries.addAll(newAE)

                            val newSG = SavingsGoalRepository.load(context)
                            savingsGoals.clear(); savingsGoals.addAll(newSG)

                            val newPL = PeriodLedgerRepository.load(context)
                            periodLedger.clear(); periodLedger.addAll(newPL)

                            sharedSettings = SharedSettingsRepository.load(context)

                            // Reload local prefs
                            currencySymbol = prefs.getString("currencySymbol", "$") ?: "$"
                            digitCount = prefs.getInt("digitCount", 3)
                            showDecimals = prefs.getBoolean("showDecimals", false)
                            dateFormatPattern = prefs.getString("dateFormatPattern", "yyyy-MM-dd") ?: "yyyy-MM-dd"
                            chartPalette = prefs.getString("chartPalette", "Sunset") ?: "Sunset"
                            appLanguage = prefs.getString("appLanguage", "en") ?: "en"
                            budgetPeriod = try { BudgetPeriod.valueOf(prefs.getString("budgetPeriod", "DAILY") ?: "DAILY") }
                                           catch (_: Exception) { BudgetPeriod.DAILY }
                            resetHour = prefs.getInt("resetHour", 0)
                            resetDayOfWeek = prefs.getInt("resetDayOfWeek", 7)
                            resetDayOfMonth = prefs.getInt("resetDayOfMonth", 1)
                            isManualBudgetEnabled = prefs.getBoolean("isManualBudgetEnabled", false)
                            manualBudgetAmount = prefs.getString("manualBudgetAmount", null)?.toDoubleOrNull() ?: 0.0
                            availableCash = prefs.getString("availableCash", null)?.toDoubleOrNull() ?: 0.0
                            budgetStartDate = prefs.getString("budgetStartDate", null)?.let { LocalDate.parse(it) }
                            lastRefreshDate = prefs.getString("lastRefreshDate", null)?.let { LocalDate.parse(it) }
                            weekStartSunday = prefs.getBoolean("weekStartSunday", true)
                            matchDays = prefs.getInt("matchDays", 7)
                            matchPercent = (try { prefs.getString("matchPercent", null)?.toDoubleOrNull() } catch (_: ClassCastException) { null })
                                ?: try { prefs.getFloat("matchPercent", 1.0f).toDouble() } catch (_: ClassCastException) { 1.0 }
                            matchDollar = prefs.getInt("matchDollar", 1)
                            matchChars = prefs.getInt("matchChars", 5)

                            // Handle family sync: dissolve old group, create new one.
                            // Clear all deviceIds and clocks first so records get
                            // properly stamped with THIS device's identity when a
                            // new group is created (the backup may have come from a
                            // different device).
                            if (isSyncConfigured) {
                                // Clear sync identity on all records
                                transactions.forEachIndexed { i, t ->
                                    transactions[i] = t.copy(deviceId = "", source_clock = 0L,
                                        description_clock = 0L,
                                        amount_clock = 0L, date_clock = 0L, type_clock = 0L,
                                        categoryAmounts_clock = 0L, isUserCategorized_clock = 0L,
                                        isBudgetIncome_clock = 0L,
                                        linkedRecurringExpenseId_clock = 0L,
                                        linkedAmortizationEntryId_clock = 0L,
                                        deleted_clock = 0L,
                                        deviceId_clock = 0L)
                                }
                                saveTransactions()
                                categories.forEachIndexed { i, c ->
                                    categories[i] = c.copy(deviceId = "", name_clock = 0L,
                                        iconName_clock = 0L, tag_clock = 0L, deleted_clock = 0L,
                                        deviceId_clock = 0L)
                                }
                                saveCategories()
                                recurringExpenses.forEachIndexed { i, r ->
                                    recurringExpenses[i] = r.copy(deviceId = "", source_clock = 0L,
                                        description_clock = 0L,
                                        amount_clock = 0L, repeatType_clock = 0L,
                                        repeatInterval_clock = 0L, startDate_clock = 0L,
                                        monthDay1_clock = 0L, monthDay2_clock = 0L, deleted_clock = 0L,
                                        deviceId_clock = 0L)
                                }
                                saveRecurringExpenses()
                                incomeSources.forEachIndexed { i, s ->
                                    incomeSources[i] = s.copy(deviceId = "", source_clock = 0L,
                                        description_clock = 0L,
                                        amount_clock = 0L, repeatType_clock = 0L,
                                        repeatInterval_clock = 0L, startDate_clock = 0L,
                                        monthDay1_clock = 0L, monthDay2_clock = 0L, deleted_clock = 0L,
                                        deviceId_clock = 0L)
                                }
                                saveIncomeSources()
                                savingsGoals.forEachIndexed { i, g ->
                                    savingsGoals[i] = g.copy(deviceId = "", name_clock = 0L,
                                        targetAmount_clock = 0L, targetDate_clock = 0L,
                                        totalSavedSoFar_clock = 0L, contributionPerPeriod_clock = 0L,
                                        isPaused_clock = 0L, deleted_clock = 0L,
                                        deviceId_clock = 0L)
                                }
                                saveSavingsGoals()
                                amortizationEntries.forEachIndexed { i, e ->
                                    amortizationEntries[i] = e.copy(deviceId = "", source_clock = 0L,
                                        description_clock = 0L,
                                        amount_clock = 0L, totalPeriods_clock = 0L,
                                        startDate_clock = 0L, isPaused_clock = 0L, deleted_clock = 0L,
                                        deviceId_clock = 0L)
                                }
                                saveAmortizationEntries()

                                // Reset lastPushedClock so all records get pushed to new group.
                                // Do NOT reset the lamport clock — it must stay monotonic.
                                // Higher clock values are harmless; resetting could cause
                                // collisions if SyncWorker is still running with the old group.
                                context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
                                    .edit().putLong("lastPushedClock", 0L)
                                    .putBoolean("pushClockFixApplied", true).apply()

                                val oldGroupId = syncGroupId
                                if (oldGroupId != null) {
                                    coroutineScope.launch {
                                        try {
                                            GroupManager.dissolveGroup(context, oldGroupId)
                                        } catch (_: Exception) {}
                                        val newGroup = GroupManager.createGroup(context)
                                        // Register admin device and initialize group doc
                                        FirestoreService.registerDevice(
                                            newGroup.groupId, localDeviceId,
                                            GroupManager.getDeviceName(context), isAdmin = true
                                        )
                                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        db.collection("groups").document(newGroup.groupId)
                                            .set(mapOf("nextDeltaVersion" to 1L, "createdAt" to System.currentTimeMillis(), "lastActivity" to System.currentTimeMillis()))
                                        isSyncConfigured = true
                                        syncGroupId = newGroup.groupId
                                        isSyncAdmin = true
                                        syncStatus = "synced"
                                        lastSyncTime = null
                                        syncDevices = emptyList()
                                        generatedPairingCode = null
                                    }
                                }
                            }
                        },
                        isSyncConfigured = isSyncConfigured,
                        isSyncAdmin = isSyncAdmin,
                        budgetPeriod = budgetPeriod,
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "transactions_help" }
                    )
                    "future_expenditures" -> FutureExpendituresScreen(
                        savingsGoals = savingsGoals.toList().active,
                        currencySymbol = currencySymbol,
                        budgetPeriod = budgetPeriod,
                        dateFormatPattern = dateFormatPattern,
                        onAddGoal = { goal ->
                            val clock = lamportClock.tick()
                            savingsGoals.add(goal.copy(
                                deviceId = localDeviceId,
                                name_clock = clock,
                                targetAmount_clock = clock,
                                targetDate_clock = clock,
                                totalSavedSoFar_clock = clock,
                                contributionPerPeriod_clock = clock,
                                isPaused_clock = clock,
                                deviceId_clock = clock
                            ))
                            saveSavingsGoals()
                        },
                        onUpdateGoal = { updated ->
                            val idx = savingsGoals.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = savingsGoals[idx]
                                val clock = lamportClock.tick()
                                savingsGoals[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deviceId_clock = old.deviceId_clock,
                                    deleted = old.deleted,
                                    deleted_clock = old.deleted_clock,
                                    name_clock = if (updated.name != old.name) clock else old.name_clock,
                                    targetAmount_clock = if (updated.targetAmount != old.targetAmount) clock else old.targetAmount_clock,
                                    targetDate_clock = if (updated.targetDate != old.targetDate) clock else old.targetDate_clock,
                                    totalSavedSoFar_clock = if (updated.totalSavedSoFar != old.totalSavedSoFar) clock else old.totalSavedSoFar_clock,
                                    contributionPerPeriod_clock = if (updated.contributionPerPeriod != old.contributionPerPeriod) clock else old.contributionPerPeriod_clock,
                                    isPaused_clock = if (updated.isPaused != old.isPaused) clock else old.isPaused_clock
                                )
                                saveSavingsGoals()
                            }
                        },
                        onDeleteGoal = { goal ->
                            val idx = savingsGoals.indexOfFirst { it.id == goal.id }
                            if (idx >= 0) {
                                savingsGoals[idx] = savingsGoals[idx].copy(deleted = true, deleted_clock = lamportClock.tick())
                                saveSavingsGoals()
                            }
                        },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "future_expenditures_help" }
                    )
                    "amortization" -> AmortizationScreen(
                        amortizationEntries = amortizationEntries.toList().active,
                        currencySymbol = currencySymbol,
                        budgetPeriod = budgetPeriod,
                        dateFormatPattern = dateFormatPattern,
                        onAddEntry = { entry ->
                            val clock = lamportClock.tick()
                            amortizationEntries.add(entry.copy(
                                deviceId = localDeviceId,
                                source_clock = clock,
                                description_clock = clock,
                                amount_clock = clock,
                                totalPeriods_clock = clock,
                                startDate_clock = clock,
                                isPaused_clock = clock,
                                deviceId_clock = clock
                            ))
                            saveAmortizationEntries()
                        },
                        onUpdateEntry = { updated ->
                            val idx = amortizationEntries.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = amortizationEntries[idx]
                                val clock = lamportClock.tick()
                                amortizationEntries[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deviceId_clock = old.deviceId_clock,
                                    deleted = old.deleted,
                                    deleted_clock = old.deleted_clock,
                                    source_clock = if (updated.source != old.source) clock else old.source_clock,
                                    description_clock = if (updated.description != old.description) clock else old.description_clock,
                                    amount_clock = if (updated.amount != old.amount) clock else old.amount_clock,
                                    totalPeriods_clock = if (updated.totalPeriods != old.totalPeriods) clock else old.totalPeriods_clock,
                                    startDate_clock = if (updated.startDate != old.startDate) clock else old.startDate_clock,
                                    isPaused_clock = if (updated.isPaused != old.isPaused) clock else old.isPaused_clock
                                )
                                saveAmortizationEntries()
                            }
                        },
                        onDeleteEntry = { entry ->
                            val idx = amortizationEntries.indexOfFirst { it.id == entry.id }
                            if (idx >= 0) {
                                amortizationEntries[idx] = amortizationEntries[idx].copy(deleted = true, deleted_clock = lamportClock.tick())
                                saveAmortizationEntries()
                            }
                        },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "amortization_help" }
                    )
                    "recurring_expenses" -> RecurringExpensesScreen(
                        recurringExpenses = recurringExpenses.toList().active,
                        currencySymbol = currencySymbol,
                        dateFormatPattern = dateFormatPattern,
                        onAddRecurringExpense = { expense ->
                            val clock = lamportClock.tick()
                            recurringExpenses.add(expense.copy(
                                deviceId = localDeviceId,
                                source_clock = clock,
                                description_clock = clock,
                                amount_clock = clock,
                                repeatType_clock = clock,
                                repeatInterval_clock = clock,
                                startDate_clock = clock,
                                monthDay1_clock = clock,
                                monthDay2_clock = clock,
                                deviceId_clock = clock
                            ))
                            saveRecurringExpenses()
                        },
                        onUpdateRecurringExpense = { updated ->
                            val idx = recurringExpenses.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = recurringExpenses[idx]
                                val clock = lamportClock.tick()
                                recurringExpenses[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deviceId_clock = old.deviceId_clock,
                                    deleted = old.deleted,
                                    deleted_clock = old.deleted_clock,
                                    source_clock = if (updated.source != old.source) clock else old.source_clock,
                                    description_clock = if (updated.description != old.description) clock else old.description_clock,
                                    amount_clock = if (updated.amount != old.amount) clock else old.amount_clock,
                                    repeatType_clock = if (updated.repeatType != old.repeatType) clock else old.repeatType_clock,
                                    repeatInterval_clock = if (updated.repeatInterval != old.repeatInterval) clock else old.repeatInterval_clock,
                                    startDate_clock = if (updated.startDate != old.startDate) clock else old.startDate_clock,
                                    monthDay1_clock = if (updated.monthDay1 != old.monthDay1) clock else old.monthDay1_clock,
                                    monthDay2_clock = if (updated.monthDay2 != old.monthDay2) clock else old.monthDay2_clock
                                )
                                saveRecurringExpenses()
                            }
                        },
                        onDeleteRecurringExpense = { expense ->
                            val idx = recurringExpenses.indexOfFirst { it.id == expense.id }
                            if (idx >= 0) {
                                recurringExpenses[idx] = recurringExpenses[idx].copy(deleted = true, deleted_clock = lamportClock.tick())
                                saveRecurringExpenses()
                            }
                        },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "recurring_expenses_help" }
                    )
                    "budget_config" -> BudgetConfigScreen(
                        incomeSources = incomeSources.toList().active,
                        currencySymbol = currencySymbol,
                        dateFormatPattern = dateFormatPattern,
                        onAddIncomeSource = { src ->
                            val clock = lamportClock.tick()
                            incomeSources.add(src.copy(
                                deviceId = localDeviceId,
                                source_clock = clock,
                                description_clock = clock,
                                amount_clock = clock,
                                repeatType_clock = clock,
                                repeatInterval_clock = clock,
                                startDate_clock = clock,
                                monthDay1_clock = clock,
                                monthDay2_clock = clock,
                                deviceId_clock = clock
                            ))
                            saveIncomeSources()
                        },
                        onUpdateIncomeSource = { updated ->
                            val idx = incomeSources.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = incomeSources[idx]
                                val clock = lamportClock.tick()
                                incomeSources[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deviceId_clock = old.deviceId_clock,
                                    deleted = old.deleted,
                                    deleted_clock = old.deleted_clock,
                                    source_clock = if (updated.source != old.source) clock else old.source_clock,
                                    description_clock = if (updated.description != old.description) clock else old.description_clock,
                                    amount_clock = if (updated.amount != old.amount) clock else old.amount_clock,
                                    repeatType_clock = if (updated.repeatType != old.repeatType) clock else old.repeatType_clock,
                                    repeatInterval_clock = if (updated.repeatInterval != old.repeatInterval) clock else old.repeatInterval_clock,
                                    startDate_clock = if (updated.startDate != old.startDate) clock else old.startDate_clock,
                                    monthDay1_clock = if (updated.monthDay1 != old.monthDay1) clock else old.monthDay1_clock,
                                    monthDay2_clock = if (updated.monthDay2 != old.monthDay2) clock else old.monthDay2_clock
                                )
                                saveIncomeSources()
                            }
                        },
                        onDeleteIncomeSource = { src ->
                            val idx = incomeSources.indexOfFirst { it.id == src.id }
                            if (idx >= 0) {
                                incomeSources[idx] = incomeSources[idx].copy(deleted = true, deleted_clock = lamportClock.tick())
                                saveIncomeSources()
                            }
                        },
                        budgetPeriod = budgetPeriod,
                        onBudgetPeriodChange = {
                            budgetPeriod = it; prefs.edit().putString("budgetPeriod", it.name).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(budgetPeriod = it.name, budgetPeriod_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        resetHour = resetHour,
                        onResetHourChange = {
                            resetHour = it; prefs.edit().putInt("resetHour", it).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(resetHour = it, resetHour_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        resetDayOfWeek = resetDayOfWeek,
                        onResetDayOfWeekChange = {
                            resetDayOfWeek = it; prefs.edit().putInt("resetDayOfWeek", it).apply()
                            // Keep weekStartSunday in sync: Sunday(7)→true, Monday(1)→false
                            val newWeekStart = (it == 7)
                            if (weekStartSunday != newWeekStart) {
                                weekStartSunday = newWeekStart
                                prefs.edit().putBoolean("weekStartSunday", newWeekStart).apply()
                            }
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(
                                    resetDayOfWeek = it, resetDayOfWeek_clock = clock,
                                    weekStartSunday = newWeekStart, weekStartSunday_clock = clock,
                                    lastChangedBy = localDeviceId
                                )
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        resetDayOfMonth = resetDayOfMonth,
                        onResetDayOfMonthChange = {
                            resetDayOfMonth = it; prefs.edit().putInt("resetDayOfMonth", it).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(resetDayOfMonth = it, resetDayOfMonth_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        safeBudgetAmount = safeBudgetAmount,
                        isManualBudgetEnabled = isManualBudgetEnabled,
                        manualBudgetAmount = manualBudgetAmount,
                        onManualBudgetToggle = { enabled ->
                            isManualBudgetEnabled = enabled
                            prefs.edit().putBoolean("isManualBudgetEnabled", enabled).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(isManualBudgetEnabled = enabled, isManualBudgetEnabled_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        onManualBudgetAmountChange = { amount ->
                            manualBudgetAmount = amount
                            prefs.edit().putString("manualBudgetAmount", amount.toString()).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(manualBudgetAmount = amount, manualBudgetAmount_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        budgetStartDate = budgetStartDate?.format(DateTimeFormatter.ofPattern(dateFormatPattern)),
                        onResetBudget = {
                            val tz = if (isSyncConfigured && sharedSettings.familyTimezone.isNotEmpty())
                                ZoneId.of(sharedSettings.familyTimezone) else null
                            budgetStartDate = BudgetCalculator.currentPeriodStart(budgetPeriod, resetDayOfWeek, resetDayOfMonth, tz)
                            lastRefreshDate = budgetStartDate
                            // Record period ledger entry with CRDT stamping
                            val entryClock = lamportClock.tick()
                            val entryDate = budgetStartDate!!.atStartOfDay()
                            val alreadyRecorded = periodLedger.any {
                                it.periodStartDate.toLocalDate() == budgetStartDate
                            }
                            if (!alreadyRecorded) {
                                periodLedger.add(
                                    PeriodLedgerEntry(
                                        periodStartDate = entryDate,
                                        appliedAmount = budgetAmount,
                                        clockAtReset = entryClock,
                                        deviceId = localDeviceId,
                                        clock = entryClock
                                    )
                                )
                            }
                            savePeriodLedger()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(
                                    budgetStartDate = budgetStartDate?.toString(),
                                    budgetStartDate_clock = clock,
                                    lastChangedBy = localDeviceId
                                )
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                            recomputeCash()
                            prefs.edit()
                                .putString("budgetStartDate", budgetStartDate.toString())
                                .putString("lastRefreshDate", lastRefreshDate.toString())
                                .apply()
                        },
                        isSyncConfigured = isSyncConfigured,
                        isAdmin = isSyncAdmin,
                        onBack = { currentScreen = "settings" },
                        onHelpClick = { currentScreen = "budget_config_help" }
                    )
                    "family_sync" -> FamilySyncScreen(
                        isConfigured = isSyncConfigured,
                        groupId = syncGroupId,
                        isAdmin = isSyncAdmin,
                        deviceName = GroupManager.getDeviceName(context),
                        localDeviceId = localDeviceId,
                        devices = syncDevices,
                        syncStatus = syncStatus,
                        lastSyncTime = lastSyncTime,
                        familyTimezone = sharedSettings.familyTimezone,
                        onTimezoneChange = { tz ->
                            val clock = lamportClock.tick()
                            sharedSettings = sharedSettings.copy(
                                familyTimezone = tz,
                                familyTimezone_clock = clock,
                                lastChangedBy = localDeviceId
                            )
                            SharedSettingsRepository.save(context, sharedSettings)
                        },
                        showAttribution = sharedSettings.showAttribution,
                        onShowAttributionChange = { enabled ->
                            val clock = lamportClock.tick()
                            sharedSettings = sharedSettings.copy(
                                showAttribution = enabled,
                                showAttribution_clock = clock,
                                lastChangedBy = localDeviceId
                            )
                            SharedSettingsRepository.save(context, sharedSettings)
                        },
                        staleDays = staleDays,
                        pendingAdminClaim = pendingAdminClaim,
                        onClaimAdmin = {
                            coroutineScope.launch {
                                try {
                                    val gId = syncGroupId ?: return@launch
                                    val now = System.currentTimeMillis()
                                    val claim = AdminClaim(
                                        claimantDeviceId = localDeviceId,
                                        claimantName = GroupManager.getDeviceName(context),
                                        claimedAt = now,
                                        expiresAt = now + 24 * 60 * 60 * 1000L
                                    )
                                    FirestoreService.createAdminClaim(gId, claim)
                                    pendingAdminClaim = FirestoreService.getAdminClaim(gId)
                                } catch (_: Exception) {}
                            }
                        },
                        onObjectClaim = {
                            coroutineScope.launch {
                                try {
                                    val gId = syncGroupId ?: return@launch
                                    FirestoreService.addObjection(gId, localDeviceId)
                                    pendingAdminClaim = FirestoreService.getAdminClaim(gId)
                                } catch (_: Exception) {}
                            }
                        },
                        syncErrorMessage = syncErrorMessage,
                        onCreateGroup = { nickname ->
                            coroutineScope.launch {
                                try {
                                    GroupManager.setDeviceName(context, nickname)
                                    val info = GroupManager.createGroup(context)

                                    // Update UI state immediately after local group creation
                                    syncGroupId = info.groupId
                                    isSyncAdmin = true
                                    isSyncConfigured = true
                                    syncStatus = "syncing"

                                    // Register this device as admin
                                    FirestoreService.registerDevice(
                                        info.groupId,
                                        localDeviceId,
                                        nickname,
                                        isAdmin = true
                                    )
                                    // Initialize group doc with nextDeltaVersion and lastActivity for TTL
                                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    db.collection("groups").document(info.groupId)
                                        .set(mapOf("nextDeltaVersion" to 1L, "createdAt" to System.currentTimeMillis(), "lastActivity" to System.currentTimeMillis()))
                                    // Initialize SharedSettings from current app_prefs
                                    val clock = lamportClock.tick()
                                    sharedSettings = SharedSettings(
                                        currency = currencySymbol,
                                        budgetPeriod = budgetPeriod.name,
                                        budgetStartDate = budgetStartDate?.toString(),
                                        isManualBudgetEnabled = isManualBudgetEnabled,
                                        manualBudgetAmount = manualBudgetAmount,
                                        weekStartSunday = weekStartSunday,
                                        resetDayOfWeek = resetDayOfWeek,
                                        resetDayOfMonth = resetDayOfMonth,
                                        resetHour = resetHour,
                                        familyTimezone = java.util.TimeZone.getDefault().id,
                                        matchDays = matchDays,
                                        matchPercent = matchPercent,
                                        matchDollar = matchDollar,
                                        matchChars = matchChars,
                                        lastChangedBy = localDeviceId,
                                        currency_clock = clock,
                                        budgetPeriod_clock = clock,
                                        budgetStartDate_clock = clock,
                                        isManualBudgetEnabled_clock = clock,
                                        manualBudgetAmount_clock = clock,
                                        weekStartSunday_clock = clock,
                                        resetDayOfWeek_clock = clock,
                                        resetDayOfMonth_clock = clock,
                                        resetHour_clock = clock,
                                        familyTimezone_clock = clock,
                                        matchDays_clock = clock,
                                        matchPercent_clock = clock,
                                        matchDollar_clock = clock,
                                        matchChars_clock = clock
                                    )
                                    SharedSettingsRepository.save(context, sharedSettings)

                                    // Stamp all existing data with sync clocks so they push on first sync.
                                    // Only stamp records that have never been stamped (clock == 0) or
                                    // have no deviceId (empty). Do NOT re-stamp records from other
                                    // devices — those are synced records and belong to their creator.
                                    val stampClock = lamportClock.tick()
                                    transactions.forEachIndexed { i, t ->
                                        if (t.source_clock == 0L || t.deviceId.isEmpty()) {
                                            transactions[i] = t.copy(
                                                deviceId = localDeviceId,
                                                source_clock = stampClock, description_clock = stampClock,
                                                amount_clock = stampClock,
                                                date_clock = stampClock, type_clock = stampClock,
                                                categoryAmounts_clock = stampClock,
                                                isUserCategorized_clock = stampClock,
                                                isBudgetIncome_clock = stampClock,
                                                linkedRecurringExpenseId_clock = stampClock,
                                                linkedAmortizationEntryId_clock = stampClock,
                                                deviceId_clock = stampClock
                                            )
                                        }
                                    }
                                    saveTransactions()
                                    categories.forEachIndexed { i, c ->
                                        if (c.name_clock == 0L || c.deviceId.isEmpty()) {
                                            categories[i] = c.copy(
                                                deviceId = localDeviceId,
                                                name_clock = stampClock, iconName_clock = stampClock,
                                                tag_clock = if (c.tag.isNotEmpty()) stampClock else 0L,
                                                deviceId_clock = stampClock
                                            )
                                        }
                                    }
                                    saveCategories()
                                    savingsGoals.forEachIndexed { i, g ->
                                        if (g.name_clock == 0L || g.deviceId.isEmpty()) {
                                            savingsGoals[i] = g.copy(
                                                deviceId = localDeviceId,
                                                name_clock = stampClock, targetAmount_clock = stampClock,
                                                targetDate_clock = stampClock, totalSavedSoFar_clock = stampClock,
                                                contributionPerPeriod_clock = stampClock, isPaused_clock = stampClock,
                                                deviceId_clock = stampClock
                                            )
                                        }
                                    }
                                    saveSavingsGoals()
                                    amortizationEntries.forEachIndexed { i, e ->
                                        if (e.source_clock == 0L || e.deviceId.isEmpty()) {
                                            amortizationEntries[i] = e.copy(
                                                deviceId = localDeviceId,
                                                source_clock = stampClock, description_clock = stampClock,
                                                amount_clock = stampClock,
                                                totalPeriods_clock = stampClock, startDate_clock = stampClock,
                                                isPaused_clock = stampClock,
                                                deviceId_clock = stampClock
                                            )
                                        }
                                    }
                                    saveAmortizationEntries()
                                    recurringExpenses.forEachIndexed { i, r ->
                                        if (r.source_clock == 0L || r.deviceId.isEmpty()) {
                                            recurringExpenses[i] = r.copy(
                                                deviceId = localDeviceId,
                                                source_clock = stampClock, description_clock = stampClock,
                                                amount_clock = stampClock,
                                                repeatType_clock = stampClock, repeatInterval_clock = stampClock,
                                                startDate_clock = stampClock, monthDay1_clock = stampClock,
                                                monthDay2_clock = stampClock,
                                                deviceId_clock = stampClock
                                            )
                                        }
                                    }
                                    saveRecurringExpenses()
                                    incomeSources.forEachIndexed { i, s ->
                                        if (s.source_clock == 0L || s.deviceId.isEmpty()) {
                                            incomeSources[i] = s.copy(
                                                deviceId = localDeviceId,
                                                source_clock = stampClock, description_clock = stampClock,
                                                amount_clock = stampClock,
                                                repeatType_clock = stampClock, repeatInterval_clock = stampClock,
                                                startDate_clock = stampClock, monthDay1_clock = stampClock,
                                                monthDay2_clock = stampClock,
                                                deviceId_clock = stampClock
                                            )
                                        }
                                    }
                                    saveIncomeSources()
                                    // Stamp period ledger entries for sync
                                    periodLedger.forEachIndexed { i, e ->
                                        if (e.clock == 0L || e.deviceId.isEmpty()) {
                                            periodLedger[i] = e.copy(
                                                deviceId = localDeviceId,
                                                clock = stampClock
                                            )
                                        }
                                    }
                                    savePeriodLedger()

                                    syncStatus = "synced"
                                } catch (_: Exception) {
                                    syncStatus = "error"
                                }
                            }
                        },
                        onJoinGroup = { code, nickname ->
                            coroutineScope.launch {
                                try {
                                    GroupManager.setDeviceName(context, nickname)
                                    val success = GroupManager.joinGroup(context, code)
                                    if (success) {
                                        syncGroupId = GroupManager.getGroupId(context)
                                        isSyncAdmin = false
                                        isSyncConfigured = true
                                        syncStatus = "synced"

                                        // Stamp existing records so they push on the
                                        // first sync — especially categories, which
                                        // other devices need to build catIdRemap.
                                        val stampClock = lamportClock.tick()
                                        categories.forEachIndexed { i, c ->
                                            if (c.name_clock == 0L || c.deviceId.isEmpty()) {
                                                categories[i] = c.copy(
                                                    deviceId = localDeviceId,
                                                    name_clock = stampClock, iconName_clock = stampClock,
                                                    tag_clock = if (c.tag.isNotEmpty()) stampClock else 0L,
                                                    deviceId_clock = stampClock
                                                )
                                            }
                                        }
                                        saveCategories()
                                        transactions.forEachIndexed { i, t ->
                                            if (t.source_clock == 0L || t.deviceId.isEmpty()) {
                                                transactions[i] = t.copy(
                                                    deviceId = localDeviceId,
                                                    source_clock = stampClock, description_clock = stampClock,
                                                    amount_clock = stampClock,
                                                    date_clock = stampClock, type_clock = stampClock,
                                                    categoryAmounts_clock = stampClock,
                                                    isUserCategorized_clock = stampClock,
                                                    isBudgetIncome_clock = stampClock,
                                                    linkedRecurringExpenseId_clock = stampClock,
                                                    linkedAmortizationEntryId_clock = stampClock,
                                                    deviceId_clock = stampClock
                                                )
                                            }
                                        }
                                        saveTransactions()
                                        recurringExpenses.forEachIndexed { i, r ->
                                            if (r.source_clock == 0L || r.deviceId.isEmpty()) {
                                                recurringExpenses[i] = r.copy(
                                                    deviceId = localDeviceId,
                                                    source_clock = stampClock, description_clock = stampClock,
                                                    amount_clock = stampClock,
                                                    repeatType_clock = stampClock, repeatInterval_clock = stampClock,
                                                    startDate_clock = stampClock, monthDay1_clock = stampClock,
                                                    monthDay2_clock = stampClock,
                                                    deviceId_clock = stampClock
                                                )
                                            }
                                        }
                                        saveRecurringExpenses()
                                        incomeSources.forEachIndexed { i, s ->
                                            if (s.source_clock == 0L || s.deviceId.isEmpty()) {
                                                incomeSources[i] = s.copy(
                                                    deviceId = localDeviceId,
                                                    source_clock = stampClock, description_clock = stampClock,
                                                    amount_clock = stampClock,
                                                    repeatType_clock = stampClock, repeatInterval_clock = stampClock,
                                                    startDate_clock = stampClock, monthDay1_clock = stampClock,
                                                    monthDay2_clock = stampClock,
                                                    deviceId_clock = stampClock
                                                )
                                            }
                                        }
                                        saveIncomeSources()
                                        savingsGoals.forEachIndexed { i, g ->
                                            if (g.name_clock == 0L || g.deviceId.isEmpty()) {
                                                savingsGoals[i] = g.copy(
                                                    deviceId = localDeviceId,
                                                    name_clock = stampClock, targetAmount_clock = stampClock,
                                                    targetDate_clock = stampClock, totalSavedSoFar_clock = stampClock,
                                                    contributionPerPeriod_clock = stampClock, isPaused_clock = stampClock,
                                                    deviceId_clock = stampClock
                                                )
                                            }
                                        }
                                        saveSavingsGoals()
                                        amortizationEntries.forEachIndexed { i, e ->
                                            if (e.source_clock == 0L || e.deviceId.isEmpty()) {
                                                amortizationEntries[i] = e.copy(
                                                    deviceId = localDeviceId,
                                                    source_clock = stampClock, description_clock = stampClock,
                                                    amount_clock = stampClock,
                                                    totalPeriods_clock = stampClock, startDate_clock = stampClock,
                                                    isPaused_clock = stampClock,
                                                    deviceId_clock = stampClock
                                                )
                                            }
                                        }
                                        saveAmortizationEntries()
                                    }
                                } catch (_: Exception) {
                                    syncStatus = "error"
                                }
                            }
                        },
                        onLeaveGroup = {
                            coroutineScope.launch {
                                GroupManager.leaveGroup(context)
                                syncPrefs.edit()
                                    .remove("catIdRemap")
                                    .remove("lastSyncVersion")
                                    .remove("lastPushedClock")
                                    .remove("lastSuccessfulSync")
                                    .apply()
                                isSyncConfigured = false
                                syncGroupId = null
                                isSyncAdmin = false
                                syncStatus = "off"
                                lastSyncTime = null
                                syncDevices = emptyList()
                                pendingAdminClaim = null
                                staleDays = 0
                                syncErrorMessage = null
                            }
                        },
                        onDissolveGroup = {
                            val gId = syncGroupId
                            if (gId != null) {
                                syncStatus = "syncing"
                                coroutineScope.launch {
                                    try {
                                        GroupManager.dissolveGroup(context, gId)
                                        syncPrefs.edit()
                                            .remove("catIdRemap")
                                            .remove("lastSyncVersion")
                                            .remove("lastPushedClock")
                                            .remove("lastSuccessfulSync")
                                            .apply()
                                        isSyncConfigured = false
                                        syncGroupId = null
                                        isSyncAdmin = false
                                        syncStatus = "off"
                                        lastSyncTime = null
                                        syncDevices = emptyList()
                                        pendingAdminClaim = null
                                        staleDays = 0
                                        syncErrorMessage = null
                                    } catch (_: Exception) {
                                        syncStatus = "error"
                                        android.widget.Toast.makeText(context, strings.sync.dissolveError, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        onSyncNow = {
                            coroutineScope.launch {
                                val gId = GroupManager.getGroupId(context) ?: return@launch
                                val key = GroupManager.getEncryptionKey(context) ?: return@launch
                                val engine = SyncEngine(context, gId, localDeviceId, key, lamportClock)
                                syncStatus = "syncing"
                                try {
                                    val rmJson = syncPrefs.getString("catIdRemap", null)
                                    val existRemap = if (rmJson != null) {
                                        try { val j = org.json.JSONObject(rmJson); j.keys().asSequence().associate { it.toInt() to j.getInt(it) } }
                                        catch (_: Exception) { emptyMap() }
                                    } else emptyMap<Int, Int>()
                                    val result = engine.sync(
                                        transactions.toList(),
                                        recurringExpenses.toList(),
                                        incomeSources.toList(),
                                        savingsGoals.toList(),
                                        amortizationEntries.toList(),
                                        categories.toList(),
                                        sharedSettings,
                                        existingCatIdRemap = existRemap,
                                        periodLedgerEntries = periodLedger.toList()
                                    )
                                    if (result.success) {
                                        result.mergedTransactions?.let { transactions.clear(); transactions.addAll(it); saveTransactions() }
                                        result.mergedRecurringExpenses?.let { recurringExpenses.clear(); recurringExpenses.addAll(it); saveRecurringExpenses() }
                                        result.mergedIncomeSources?.let { incomeSources.clear(); incomeSources.addAll(it); saveIncomeSources() }
                                        result.mergedSavingsGoals?.let { savingsGoals.clear(); savingsGoals.addAll(it); saveSavingsGoals() }
                                        result.mergedAmortizationEntries?.let { amortizationEntries.clear(); amortizationEntries.addAll(it); saveAmortizationEntries() }
                                        result.mergedCategories?.let { categories.clear(); categories.addAll(it); saveCategories() }
                                        result.mergedPeriodLedgerEntries?.let { periodLedger.clear(); periodLedger.addAll(it); savePeriodLedger() }
                                        result.mergedSharedSettings?.let { merged ->
                                            sharedSettings = merged
                                            currencySymbol = merged.currency
                                            budgetPeriod = try { BudgetPeriod.valueOf(merged.budgetPeriod) } catch (_: Exception) { budgetPeriod }
                                            resetHour = merged.resetHour
                                            resetDayOfWeek = merged.resetDayOfWeek
                                            resetDayOfMonth = merged.resetDayOfMonth
                                            isManualBudgetEnabled = merged.isManualBudgetEnabled
                                            manualBudgetAmount = merged.manualBudgetAmount
                                            weekStartSunday = merged.weekStartSunday
                                            matchDays = merged.matchDays
                                            matchPercent = merged.matchPercent
                                            matchDollar = merged.matchDollar
                                            matchChars = merged.matchChars
                                            val syncedStartDate = merged.budgetStartDate?.let {
                                                try { LocalDate.parse(it) } catch (_: Exception) { null }
                                            }
                                            val budgetStartChanged = syncedStartDate != null && syncedStartDate != budgetStartDate
                                            if (budgetStartChanged) {
                                                budgetStartDate = syncedStartDate
                                                lastRefreshDate = LocalDate.now()
                                            }
                                            val prefsEditor = prefs.edit()
                                                .putString("currencySymbol", merged.currency)
                                                .putString("budgetPeriod", merged.budgetPeriod)
                                                .putInt("resetHour", merged.resetHour)
                                                .putInt("resetDayOfWeek", merged.resetDayOfWeek)
                                                .putInt("resetDayOfMonth", merged.resetDayOfMonth)
                                                .putBoolean("isManualBudgetEnabled", merged.isManualBudgetEnabled)
                                                .putString("manualBudgetAmount", merged.manualBudgetAmount.toString())
                                                .putBoolean("weekStartSunday", merged.weekStartSunday)
                                                .putInt("matchDays", merged.matchDays)
                                                .putString("matchPercent", merged.matchPercent.toString())
                                                .putInt("matchDollar", merged.matchDollar)
                                                .putInt("matchChars", merged.matchChars)
                                            if (budgetStartChanged) {
                                                prefsEditor
                                                    .putString("budgetStartDate", budgetStartDate.toString())
                                                    .putString("lastRefreshDate", lastRefreshDate.toString())
                                            }
                                            prefsEditor.apply()
                                        }
                                        recomputeCash()
                                        result.catIdRemap?.let { remap ->
                                            val rj = org.json.JSONObject(remap.mapKeys { it.key.toString() })
                                            syncPrefs.edit().putString("catIdRemap", rj.toString()).apply()
                                        }
                                        syncStatus = "synced"
                                        syncErrorMessage = null
                                        lastSyncTime = "just now"
                                        pendingAdminClaim = result.pendingAdminClaim
                                        val lastSync = syncPrefs.getLong("lastSuccessfulSync", 0L)
                                        staleDays = if (lastSync > 0L) ((System.currentTimeMillis() - lastSync) / (24 * 60 * 60 * 1000L)).toInt() else 0
                                        try {
                                            syncDevices = GroupManager.getDevices(gId)
                                            isSyncAdmin = GroupManager.isAdmin(context)
                                        } catch (_: Exception) {}
                                    } else {
                                        syncStatus = "error"
                                        syncErrorMessage = result.error
                                        pendingAdminClaim = result.pendingAdminClaim
                                        if (result.error == "removed_from_group" || result.error == "group_deleted") {
                                            GroupManager.leaveGroup(context)
                                            isSyncConfigured = false
                                            syncGroupId = null
                                            isSyncAdmin = false
                                            syncStatus = "off"
                                            lastSyncTime = null
                                            syncDevices = emptyList()
                                            pendingAdminClaim = null
                                        }
                                    }
                                } catch (_: Exception) {
                                    syncStatus = "error"
                                }
                            }
                        },
                        onGeneratePairingCode = {
                            val gId = syncGroupId
                            val key = GroupManager.getEncryptionKey(context)
                            if (gId != null && key != null) {
                                coroutineScope.launch {
                                    try {
                                        generatedPairingCode = GroupManager.generatePairingCode(context, gId, key)
                                    } catch (_: Exception) {}
                                }
                            }
                        },
                        generatedPairingCode = generatedPairingCode,
                        onDismissPairingCode = { generatedPairingCode = null },
                        onRenameDevice = { targetDeviceId, newName ->
                            val gId = syncGroupId ?: return@FamilySyncScreen
                            coroutineScope.launch {
                                try {
                                    FirestoreService.updateDeviceName(gId, targetDeviceId, newName)
                                    // If renaming self, also update local prefs
                                    if (targetDeviceId == localDeviceId) {
                                        GroupManager.setDeviceName(context, newName)
                                    }
                                    // Refresh device list
                                    syncDevices = GroupManager.getDevices(gId)
                                } catch (_: Exception) {}
                            }
                        },
                        onHelpClick = { currentScreen = "family_sync_help" },
                        onBack = {
                            generatedPairingCode = null
                            currentScreen = "settings"
                        }
                    )
                    "dashboard_help" -> DashboardHelpScreen(
                        onBack = { currentScreen = "main" }
                    )
                    "settings_help" -> SettingsHelpScreen(
                        onBack = { currentScreen = "settings" }
                    )
                    "transactions_help" -> TransactionsHelpScreen(
                        onBack = { currentScreen = "transactions" }
                    )
                    "future_expenditures_help" -> FutureExpendituresHelpScreen(
                        onBack = { currentScreen = "future_expenditures" }
                    )
                    "amortization_help" -> AmortizationHelpScreen(
                        onBack = { currentScreen = "amortization" }
                    )
                    "recurring_expenses_help" -> RecurringExpensesHelpScreen(
                        onBack = { currentScreen = "recurring_expenses" }
                    )
                    "budget_config_help" -> BudgetConfigHelpScreen(
                        onBack = { currentScreen = "budget_config" }
                    )
                    "family_sync_help" -> FamilySyncHelpScreen(
                        onBack = { currentScreen = "family_sync" }
                    )
                }

                // Dashboard quick-add dialogs (rendered over any screen)
                if (dashboardShowAddIncome) {
                    TransactionDialog(
                        title = strings.common.addNewIncomeTransaction,
                        sourceLabel = strings.common.sourceLabel,
                        categories = categories.toList().active,
                        existingIds = existingIds,
                        currencySymbol = currencySymbol,
                        dateFormatter = dateFormatter,
                        chartPalette = chartPalette,
                        onDismiss = { dashboardShowAddIncome = false },
                        onSave = { txn ->
                            runMatchingChain(txn)
                            dashboardShowAddIncome = false
                        }
                    )
                }

                if (dashboardShowAddExpense) {
                    TransactionDialog(
                        title = strings.common.addNewExpenseTransaction,
                        sourceLabel = strings.common.merchantLabel,
                        categories = categories.toList().active,
                        existingIds = existingIds,
                        currencySymbol = currencySymbol,
                        dateFormatter = dateFormatter,
                        isExpense = true,
                        chartPalette = chartPalette,
                        onDismiss = { dashboardShowAddExpense = false },
                        onSave = { txn ->
                            runMatchingChain(txn)
                            dashboardShowAddExpense = false
                        }
                    )
                }

                // Dashboard duplicate resolution dialog
                if (dashShowManualDuplicateDialog && dashPendingManualSave != null && dashManualDuplicateMatch != null) {
                    DuplicateResolutionDialog(
                        existingTransaction = dashManualDuplicateMatch!!,
                        newTransaction = dashPendingManualSave!!,
                        currencySymbol = currencySymbol,
                        dateFormatter = dateFormatter,
                        categoryMap = categoryMap,
                        showIgnoreAll = false,
                        onIgnore = {
                            addTransactionWithBudgetEffect(dashPendingManualSave!!)
                            dashPendingManualSave = null
                            dashManualDuplicateMatch = null
                            dashShowManualDuplicateDialog = false
                        },
                        onKeepNew = {
                            val dup = dashManualDuplicateMatch!!
                            val dupIdx = transactions.indexOfFirst { it.id == dup.id }
                            if (dupIdx >= 0) {
                                transactions[dupIdx] = transactions[dupIdx].copy(
                                    deleted = true,
                                    deleted_clock = lamportClock.tick()
                                )
                            }
                            saveTransactions()
                            addTransactionWithBudgetEffect(dashPendingManualSave!!)
                            dashPendingManualSave = null
                            dashManualDuplicateMatch = null
                            dashShowManualDuplicateDialog = false
                        },
                        onKeepExisting = {
                            dashPendingManualSave = null
                            dashManualDuplicateMatch = null
                            dashShowManualDuplicateDialog = false
                        },
                        onIgnoreAll = {}
                    )
                }

                // Dashboard recurring expense match dialog
                if (dashShowRecurringDialog && dashPendingRecurringTxn != null && dashPendingRecurringMatch != null) {
                    val dateCloseEnough = isRecurringDateCloseEnough(dashPendingRecurringTxn!!.date, dashPendingRecurringMatch!!)
                    RecurringExpenseConfirmDialog(
                        transaction = dashPendingRecurringTxn!!,
                        recurringExpense = dashPendingRecurringMatch!!,
                        currencySymbol = currencySymbol,
                        dateFormatter = dateFormatter,
                        showDateAdvisory = !dateCloseEnough,
                        onConfirmRecurring = {
                            val txn = dashPendingRecurringTxn!!
                            val updatedTxn = txn.copy(linkedRecurringExpenseId = dashPendingRecurringMatch!!.id)
                            addTransactionWithBudgetEffect(updatedTxn)
                            dashPendingRecurringTxn = null
                            dashPendingRecurringMatch = null
                            dashShowRecurringDialog = false
                        },
                        onNotRecurring = {
                            addTransactionWithBudgetEffect(dashPendingRecurringTxn!!)
                            dashPendingRecurringTxn = null
                            dashPendingRecurringMatch = null
                            dashShowRecurringDialog = false
                        }
                    )
                }

                // Dashboard amortization match dialog
                if (dashShowAmortizationDialog && dashPendingAmortizationTxn != null && dashPendingAmortizationMatch != null) {
                    AmortizationConfirmDialog(
                        transaction = dashPendingAmortizationTxn!!,
                        amortizationEntry = dashPendingAmortizationMatch!!,
                        currencySymbol = currencySymbol,
                        dateFormatter = dateFormatter,
                        onConfirmAmortization = {
                            val txn = dashPendingAmortizationTxn!!
                            val updatedTxn = txn.copy(linkedAmortizationEntryId = dashPendingAmortizationMatch!!.id)
                            addTransactionWithBudgetEffect(updatedTxn)
                            dashPendingAmortizationTxn = null
                            dashPendingAmortizationMatch = null
                            dashShowAmortizationDialog = false
                        },
                        onNotAmortized = {
                            addTransactionWithBudgetEffect(dashPendingAmortizationTxn!!)
                            dashPendingAmortizationTxn = null
                            dashPendingAmortizationMatch = null
                            dashShowAmortizationDialog = false
                        }
                    )
                }

                // Dashboard budget income match dialog
                if (dashShowBudgetIncomeDialog && dashPendingBudgetIncomeTxn != null && dashPendingBudgetIncomeMatch != null) {
                    BudgetIncomeConfirmDialog(
                        transaction = dashPendingBudgetIncomeTxn!!,
                        incomeSource = dashPendingBudgetIncomeMatch!!,
                        currencySymbol = currencySymbol,
                        dateFormatter = dateFormatter,
                        onConfirmBudgetIncome = {
                            val recurringIncomeCatId = categories.find { it.tag == "recurring_income" }?.id
                            val baseTxn = dashPendingBudgetIncomeTxn!!
                            val txn = baseTxn.copy(
                                isBudgetIncome = true,
                                categoryAmounts = if (recurringIncomeCatId != null)
                                    listOf(CategoryAmount(recurringIncomeCatId, baseTxn.amount))
                                else baseTxn.categoryAmounts,
                                isUserCategorized = true
                            )
                            addTransactionWithBudgetEffect(txn)
                            dashPendingBudgetIncomeTxn = null
                            dashPendingBudgetIncomeMatch = null
                            dashShowBudgetIncomeDialog = false
                        },
                        onNotBudgetIncome = {
                            addTransactionWithBudgetEffect(dashPendingBudgetIncomeTxn!!)
                            dashPendingBudgetIncomeTxn = null
                            dashPendingBudgetIncomeMatch = null
                            dashShowBudgetIncomeDialog = false
                        }
                    )
                }
                } // Box(weight)
              } // Column
              } // CompositionLocalProvider
            } // SyncBudgetTheme
        }
    }
}
