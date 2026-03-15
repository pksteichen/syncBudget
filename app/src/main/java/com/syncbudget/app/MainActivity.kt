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
import com.syncbudget.app.data.IncomeMode
import com.syncbudget.app.data.SavingsSimulator
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.CategoryRepository
import com.syncbudget.app.data.DEFAULT_CATEGORY_DEFS
import com.syncbudget.app.data.getDoubleCompat
import com.syncbudget.app.data.getAllKnownNamesForTag
import com.syncbudget.app.data.getDefaultCategoryName
import com.syncbudget.app.data.AmortizationRepository
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SavingsGoalRepository
import com.syncbudget.app.data.SuperchargeMode

import kotlin.math.ceil
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.IncomeSourceRepository
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.RecurringExpenseRepository
import com.syncbudget.app.data.RepeatType
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
import com.syncbudget.app.data.sync.SubscriptionReminderReceiver
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
import com.syncbudget.app.ui.screens.BudgetCalendarScreen
import com.syncbudget.app.ui.screens.BudgetCalendarHelpScreen
import com.syncbudget.app.ui.screens.SimulationGraphHelpScreen
import com.syncbudget.app.ui.screens.SimulationGraphScreen
import com.syncbudget.app.ui.screens.SettingsScreen
import com.syncbudget.app.ui.screens.TransactionDialog
import com.syncbudget.app.ui.screens.TransactionsHelpScreen
import com.syncbudget.app.ui.screens.TransactionsScreen
import com.syncbudget.app.ui.strings.AppStrings
import com.syncbudget.app.ui.strings.EnglishStrings
import com.syncbudget.app.ui.strings.SpanishStrings
import com.syncbudget.app.ui.theme.AdAwareAlertDialog
import com.syncbudget.app.ui.theme.DialogStyle
import com.syncbudget.app.ui.theme.DialogWarningButton
import com.syncbudget.app.ui.theme.DialogSecondaryButton
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
import com.syncbudget.app.ui.theme.LocalAppToast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crash logger — writes stack trace to Download/crash_log.txt
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sb = StringBuilder()
                sb.appendLine("=== Crash ${java.time.LocalDateTime.now()} ===")
                sb.appendLine("Thread: ${thread.name}")
                sb.appendLine("Android: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
                sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                sb.appendLine()
                var t: Throwable? = throwable
                while (t != null) {
                    sb.appendLine("${t.javaClass.name}: ${t.message}")
                    for (el in t.stackTrace) sb.appendLine("  at $el")
                    t = t.cause
                    if (t != null) sb.appendLine("Caused by:")
                }
                val dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                java.io.File(dir, "crash_log.txt").appendText(sb.toString())
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        enableEdgeToEdge()
        setContent {
            val soundPlayer = remember { FlipSoundPlayer(this@MainActivity) }
            val lamportClock = remember { LamportClock(this@MainActivity) }

            DisposableEffect(Unit) {
                onDispose { soundPlayer.release() }
            }

            var currentScreen by remember { mutableStateOf("main") }

            // Dashboard quick-add dialog state — check widget intent
            val widgetAction = remember { intent?.action }
            var dashboardShowAddIncome by remember {
                mutableStateOf(widgetAction == com.syncbudget.app.widget.BudgetWidgetProvider.ACTION_ADD_INCOME)
            }
            var dashboardShowAddExpense by remember {
                mutableStateOf(widgetAction == com.syncbudget.app.widget.BudgetWidgetProvider.ACTION_ADD_EXPENSE)
            }

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

            // Pending amount-change confirmations (apply to past transactions?)
            var pendingREAmountUpdate by remember { mutableStateOf<Pair<RecurringExpense, Double>?>(null) } // (updated, oldAmount)
            var pendingISAmountUpdate by remember { mutableStateOf<Pair<IncomeSource, Double>?>(null) } // (updated, oldAmount)

            val context = this@MainActivity
            val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            var currencySymbol by remember { mutableStateOf(prefs.getString("currencySymbol", "$") ?: "$") }
            var digitCount by remember { mutableIntStateOf(prefs.getInt("digitCount", 3)) }
            var showDecimals by remember { mutableStateOf(prefs.getBoolean("showDecimals", false)) }
            var dateFormatPattern by remember { mutableStateOf(prefs.getString("dateFormatPattern", "yyyy-MM-dd") ?: "yyyy-MM-dd") }
            var isPaidUser by remember { mutableStateOf(prefs.getBoolean("isPaidUser", false)) }
            var isSubscriber by remember { mutableStateOf(prefs.getBoolean("isSubscriber", false)) }
            var subscriptionExpiry by remember {
                mutableStateOf(prefs.getLong("subscriptionExpiry",
                    System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            }
            var showWidgetLogo by remember { mutableStateOf(prefs.getBoolean("showWidgetLogo", true)) }

            // Matching configuration
            var matchDays by remember { mutableIntStateOf(prefs.getInt("matchDays", 7)) }
            var matchPercent by remember { mutableDoubleStateOf(
                prefs.getDoubleCompat("matchPercent", 1.0)
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
                prefs.getDoubleCompat("manualBudgetAmount")
            ) }
            var incomeMode by remember { mutableStateOf(
                try { IncomeMode.valueOf(prefs.getString("incomeMode", null) ?: "FIXED") } catch (_: Exception) { IncomeMode.FIXED }
            ) }
            var availableCash by remember { mutableDoubleStateOf(
                prefs.getDoubleCompat("availableCash")
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

            fun markSyncDirty() {
                context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
                    .edit().putBoolean("syncDirty", true).apply()
            }

            fun saveIncomeSources() {
                IncomeSourceRepository.save(context, incomeSources.toList())
                markSyncDirty()
            }

            fun saveRecurringExpenses() {
                RecurringExpenseRepository.save(context, recurringExpenses.toList())
                markSyncDirty()
            }

            fun saveAmortizationEntries() {
                AmortizationRepository.save(context, amortizationEntries.toList())
                markSyncDirty()
            }

            fun saveSavingsGoals() {
                SavingsGoalRepository.save(context, savingsGoals.toList())
                markSyncDirty()
            }

            fun saveTransactions() {
                TransactionRepository.save(context, transactions.toList())
                markSyncDirty()
            }

            fun saveCategories() {
                CategoryRepository.save(context, categories.toList())
                markSyncDirty()
            }

            // persistAvailableCash declared after sync state variables below

            // Cached active (non-deleted) lists — avoids re-filtering on every recomposition
            val activeTransactions: List<Transaction> by remember { derivedStateOf { transactions.filter { !it.deleted } } }
            val activeRecurringExpenses: List<RecurringExpense> by remember { derivedStateOf { recurringExpenses.filter { !it.deleted } } }
            val activeIncomeSources: List<IncomeSource> by remember { derivedStateOf { incomeSources.filter { !it.deleted } } }
            val activeAmortizationEntries: List<AmortizationEntry> by remember { derivedStateOf { amortizationEntries.filter { !it.deleted } } }
            val activeSavingsGoals: List<SavingsGoal> by remember { derivedStateOf { savingsGoals.filter { !it.deleted } } }
            val activeCategories: List<Category> by remember { derivedStateOf { categories.filter { !it.deleted } } }

            // Budget "today" respects resetHour in DAILY mode: before resetHour
            // we're still in yesterday's period. WEEKLY/MONTHLY reset at midnight.
            val budgetToday by remember {
                derivedStateOf {
                    val now = java.time.LocalDateTime.now()
                    if (budgetPeriod == BudgetPeriod.DAILY && resetHour > 0 && now.hour < resetHour)
                        now.toLocalDate().minusDays(1)
                    else
                        now.toLocalDate()
                }
            }

            // Derived safeBudgetAmount — auto-recalculates when income/expenses change
            val safeBudgetAmount by remember {
                derivedStateOf {
                    BudgetCalculator.calculateSafeBudgetAmount(
                        activeIncomeSources,
                        activeRecurringExpenses,
                        budgetPeriod,
                        budgetToday
                    )
                }
            }

            // Derived budgetAmount
            val budgetAmount by remember {
                derivedStateOf {
                    val base = if (isManualBudgetEnabled) manualBudgetAmount else safeBudgetAmount
                    val amortDeductions = BudgetCalculator.activeAmortizationDeductions(
                        activeAmortizationEntries, budgetPeriod, budgetToday
                    )
                    val savingsDeductions = BudgetCalculator.activeSavingsGoalDeductions(
                        activeSavingsGoals, budgetPeriod, budgetToday
                    )
                    val acceleratedDeductions = BudgetCalculator.acceleratedREExtraDeductions(
                        activeRecurringExpenses, budgetPeriod, budgetToday
                    )
                    BudgetCalculator.roundCents(maxOf(0.0, base - amortDeductions - savingsDeductions - acceleratedDeductions))
                }
            }

            // Period ledger
            val periodLedger = remember {
                mutableStateListOf(*PeriodLedgerRepository.load(context).toTypedArray())
            }

            fun savePeriodLedger() {
                PeriodLedgerRepository.save(context, periodLedger.toList())
                markSyncDirty()
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
            var syncProgressMessage by remember { mutableStateOf<String?>(null) }
            var pendingAdminClaim by remember { mutableStateOf<AdminClaim?>(null) }
            var syncRepairAlert by remember { mutableStateOf(false) }
            // availableCash may go negative (= overspent). Guard against NaN/Infinity.
            fun persistAvailableCash() {
                if (availableCash.isNaN() || availableCash.isInfinite()) availableCash = 0.0
                availableCash = BudgetCalculator.roundCents(availableCash)
                prefs.edit().putString("availableCash", availableCash.toString()).apply()
                com.syncbudget.app.widget.BudgetWidgetProvider.updateAllWidgets(context)
            }

            // Deterministic cash recomputation from synced data.
            // All devices with the same synced data compute the same result.
            fun recomputeCash() {
                if (budgetStartDate == null) return
                availableCash = BudgetCalculator.recomputeAvailableCash(
                    budgetStartDate!!, periodLedger.toList(),
                    activeTransactions, activeRecurringExpenses,
                    incomeMode, activeIncomeSources
                )
                persistAvailableCash()
            }

            // Simulation-adjusted available cash: recomputes cash as if the
            // current period's ledger entry used the live budgetAmount.
            // This ensures mid-period budget changes (pause/delete/add) are
            // immediately reflected in the simulation, using the exact same
            // recomputeAvailableCash logic (single source of truth).
            val simAvailableCash by remember {
                derivedStateOf {
                    val bsd = budgetStartDate
                    if (bsd == null) {
                        availableCash
                    } else {
                        val currentPeriod = BudgetCalculator.currentPeriodStart(
                            budgetPeriod, resetDayOfWeek, resetDayOfMonth,
                            resetHour = resetHour
                        )
                        val adjustedLedger = periodLedger.map { entry ->
                            if (entry.periodStartDate.toLocalDate() == currentPeriod) {
                                entry.copy(appliedAmount = budgetAmount)
                            } else entry
                        }
                        BudgetCalculator.recomputeAvailableCash(
                            bsd, adjustedLedger,
                            activeTransactions,
                            activeRecurringExpenses,
                            incomeMode, activeIncomeSources
                        )
                    }
                }
            }

            // One-time migrations — each wrapped in try-catch so a failure
            // in one migration doesn't block subsequent migrations or crash
            // the LaunchedEffect.  Flags are set AFTER success.
            LaunchedEffect(Unit) {
                try {
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
                } catch (e: Exception) { android.util.Log.e("Migration", "fix_stale_budgetstart_ledger_ui failed", e) }

                try {
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
                } catch (e: Exception) { android.util.Log.e("Migration", "restamp_all_period_ledger_ui failed", e) }

                try {
                    if (!syncPrefs.getBoolean("migration_backfill_linked_amounts", false)) {
                        var anyChanged = false
                        val reMap = recurringExpenses.associateBy { it.id }
                        val isMap = incomeSources.associateBy { it.id }
                        transactions.forEachIndexed { i, txn ->
                            var updated = txn
                            if (txn.linkedRecurringExpenseId != null && txn.linkedRecurringExpenseAmount == 0.0) {
                                val re = reMap[txn.linkedRecurringExpenseId]
                                if (re != null) {
                                    updated = updated.copy(linkedRecurringExpenseAmount = re.amount)
                                    anyChanged = true
                                }
                            }
                            if (txn.linkedIncomeSourceId != null && txn.linkedIncomeSourceAmount == 0.0) {
                                val src = isMap[txn.linkedIncomeSourceId]
                                if (src != null) {
                                    updated = updated.copy(linkedIncomeSourceAmount = src.amount)
                                    anyChanged = true
                                }
                            }
                            if (updated !== txn) transactions[i] = updated
                        }
                        if (anyChanged) saveTransactions()
                        syncPrefs.edit().putBoolean("migration_backfill_linked_amounts", true).apply()
                    }
                } catch (e: Exception) { android.util.Log.e("Migration", "backfill_linked_amounts failed", e) }

                try {
                    if (!syncPrefs.getBoolean("migration_add_savings_goal_fields", false)) {
                        saveTransactions()
                        syncPrefs.edit().putBoolean("migration_add_savings_goal_fields", true).apply()
                    }
                } catch (e: Exception) { android.util.Log.e("Migration", "add_savings_goal_fields failed", e) }

                recomputeCash()
            }

            // Check if an expense transaction is fully accounted for in the budget
            // (amortization entries are built into the safe budget amount)
            fun isBudgetAccountedExpense(txn: Transaction): Boolean {
                if (txn.type != TransactionType.EXPENSE) return false
                return txn.linkedAmortizationEntryId != null ||
                    txn.linkedSavingsGoalId != null || txn.linkedSavingsGoalAmount > 0.0
            }

            // For recurring-linked expenses, returns the cash effect (recurringAmount - txnAmount).
            // Positive = saved money, negative = overspent. Null = not a recurring-linked expense.
            fun recurringLinkCashEffect(txn: Transaction): Double? {
                if (txn.type != TransactionType.EXPENSE || txn.linkedRecurringExpenseId == null) return null
                val rememberedAmount = if (txn.linkedRecurringExpenseAmount > 0.0) txn.linkedRecurringExpenseAmount
                    else recurringExpenses.find { it.id == txn.linkedRecurringExpenseId }?.amount ?: return null
                return rememberedAmount - txn.amount
            }

            // Trigger immediate sync when app returns to foreground
            var syncTrigger by remember { mutableIntStateOf(0) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        syncTrigger++
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Foreground sync loop — uses SupervisorJob so exceptions in
            // the sync coroutine don't cancel sibling LaunchedEffects.
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

                // One-time fix: non-admin devices that joined with the old code had
                // iconName_clock stamped to a high value, causing their default icons
                // to overwrite admin's customized icons during CRDT merge.  Reset
                // iconName_clock to 0 so defaults never win.
                if (!isSyncAdmin && !syncPrefs.getBoolean("migration_reset_nonadmin_icon_clock", false)) {
                    var changed = false
                    categories.forEachIndexed { i, c ->
                        if (c.iconName_clock > 0L && c.deviceId == localDeviceId) {
                            categories[i] = c.copy(iconName_clock = 0L)
                            changed = true
                        }
                    }
                    if (changed) saveCategories()
                    syncPrefs.edit().putBoolean("migration_reset_nonadmin_icon_clock", true).apply()
                }

                // Backfill setAsideSoFar for existing REs based on accrued cycle position
                if (!prefs.getBoolean("migration_add_re_setaside_fields", false)) {
                    val migToday = LocalDate.now()
                    val twoYearsAhead = migToday.plusYears(2)
                    var reChanged = false
                    recurringExpenses.forEachIndexed { idx, re ->
                        if (re.deleted || re.setAsideSoFar > 0.0) return@forEachIndexed
                        val occurrences = BudgetCalculator.generateOccurrences(
                            re.repeatType, re.repeatInterval, re.startDate,
                            re.monthDay1, re.monthDay2, migToday, twoYearsAhead
                        )
                        val nextOcc = occurrences.firstOrNull() ?: return@forEachIndexed
                        if (nextOcc == migToday) return@forEachIndexed
                        val prevOcc: LocalDate = when (re.repeatType) {
                            RepeatType.DAYS -> nextOcc.minusDays(re.repeatInterval.toLong())
                            RepeatType.WEEKS -> nextOcc.minusDays((re.repeatInterval * 7).toLong())
                            RepeatType.BI_WEEKLY -> nextOcc.minusDays(14)
                            RepeatType.MONTHS -> nextOcc.minusMonths(re.repeatInterval.toLong())
                            RepeatType.BI_MONTHLY -> {
                                val d1 = re.monthDay1 ?: 1; val d2 = re.monthDay2 ?: 15
                                val cd1 = d1.coerceAtMost(nextOcc.lengthOfMonth())
                                if (nextOcc.dayOfMonth == cd1) {
                                    val pm = nextOcc.minusMonths(1)
                                    pm.withDayOfMonth(d2.coerceAtMost(pm.lengthOfMonth()))
                                } else nextOcc.withDayOfMonth(cd1)
                            }
                            RepeatType.ANNUAL -> nextOcc.minusYears(1)
                        }
                        val daysSince = ChronoUnit.DAYS.between(prevOcc, migToday).toDouble()
                        val totalDays = ChronoUnit.DAYS.between(prevOcc, nextOcc).toDouble()
                        if (totalDays > 0) {
                            val accrued = BudgetCalculator.roundCents((daysSince / totalDays) * re.amount)
                            if (accrued > 0.0) {
                                val clk = lamportClock.tick()
                                recurringExpenses[idx] = re.copy(
                                    setAsideSoFar = accrued, setAsideSoFar_clock = clk
                                )
                                reChanged = true
                            }
                        }
                    }
                    if (reChanged) saveRecurringExpenses()
                    prefs.edit().putBoolean("migration_add_re_setaside_fields", true).apply()
                }

                var consecutiveErrors = 0
                while (true) {
                    // Admin: post subscription expiry to Firestore
                    if (isSyncAdmin) {
                        try {
                            if (isSubscriber) {
                                // Post the subscription expiry date (from Settings test
                                // picker now, from Google Play Billing in production)
                                FirestoreService.updateSubscriptionExpiry(groupId, subscriptionExpiry)
                            } else {
                                // Admin lost subscription: post current time as expiry
                                val currentExpiry = try { FirestoreService.getSubscriptionExpiry(groupId) } catch (_: Exception) { 0L }
                                if (currentExpiry == 0L || currentExpiry > System.currentTimeMillis() + 8 * 24 * 60 * 60 * 1000) {
                                    FirestoreService.updateSubscriptionExpiry(groupId, System.currentTimeMillis())
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // Check subscription grace period — auto-dissolve if expired 7+ days
                    try {
                        val expiry = FirestoreService.getSubscriptionExpiry(groupId)
                        if (expiry > 0L) {
                            val gracePeriodMs = 7L * 24 * 60 * 60 * 1000
                            val elapsed = System.currentTimeMillis() - expiry
                            if (elapsed > gracePeriodMs) {
                                // Add random delay (0-30 min) to prevent all devices dissolving simultaneously
                                val randomDelay = (Math.random() * 30 * 60 * 1000).toLong()
                                delay(randomDelay)
                                // Re-check in case another device already dissolved
                                if (!FirestoreService.isGroupDissolved(groupId)) {
                                    android.util.Log.w("SyncLoop", "Admin subscription expired ${elapsed / (24*60*60*1000)}d ago — auto-dissolving group")
                                    GroupManager.dissolveGroup(context, groupId)
                                }
                                isSyncConfigured = false
                                syncGroupId = null
                                isSyncAdmin = false
                                syncStatus = "off"
                                syncDevices = emptyList()
                                return@LaunchedEffect
                            } else if (elapsed > 0) {
                                // Within grace period — show warning and schedule daily reminder
                                syncErrorMessage = strings.sync.subscriptionExpiredNotice
                                SubscriptionReminderReceiver.scheduleNextReminder(context)
                            } else {
                                // Subscription active — cancel any pending reminders
                                SubscriptionReminderReceiver.cancelReminder(context)
                            }
                        }
                    } catch (_: Exception) {}

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

                        // Continuous per-field rescue: re-stamp only individual
                        // field clocks that fell behind lastPushedClock on
                        // locally-owned records.  Unlike the old blanket approach
                        // this preserves field clocks set by the other device,
                        // preventing rescue from overwriting cross-device edits.
                        // IMPORTANT: use a SINGLE clock tick for the entire batch
                        // so that all rescued clocks == lastPushedClock after push,
                        // preventing an infinite re-stamp → push → re-stamp loop.
                        // Single clock tick shared by BOTH rescue and clk=0 fix.
                        // Using two separate ticks creates sequential values where
                        // the lower one becomes stranded next cycle → infinite loop.
                        val rescueClk = lamportClock.tick()
                        val lpc = syncPrefs.getLong("lastPushedClock", 0L)
                        if (lpc > 0) {
                            val stranded = { clk: Long -> clk in 1 until lpc }
                            val rc = rescueClk
                            var anyRescued = false
                            // When ANY field on a record is stranded, stamp ALL fields
                            // to rc.  Stamping only stranded fields creates mixed clocks
                            // where the highest field advances lastPushedClock past the
                            // lower fields, making them stranded again → infinite loop.
                            for (i in transactions.indices) {
                                val t = transactions[i]
                                if (t.deviceId != localDeviceId) continue
                                if (stranded(t.source_clock) || stranded(t.description_clock) ||
                                    stranded(t.amount_clock) || stranded(t.date_clock) ||
                                    stranded(t.type_clock) || stranded(t.categoryAmounts_clock) ||
                                    stranded(t.isUserCategorized_clock) || stranded(t.excludeFromBudget_clock) ||
                                    stranded(t.isBudgetIncome_clock) || stranded(t.linkedRecurringExpenseId_clock) ||
                                    stranded(t.linkedAmortizationEntryId_clock) || stranded(t.linkedIncomeSourceId_clock) ||
                                    stranded(t.amortizationAppliedAmount_clock) || stranded(t.linkedRecurringExpenseAmount_clock) ||
                                    stranded(t.linkedIncomeSourceAmount_clock) || stranded(t.linkedSavingsGoalId_clock) ||
                                    stranded(t.linkedSavingsGoalAmount_clock) || stranded(t.deviceId_clock) ||
                                    stranded(t.deleted_clock)) {
                                    anyRescued = true
                                    transactions[i] = t.copy(
                                        source_clock = rc, description_clock = rc,
                                        amount_clock = rc, date_clock = rc,
                                        type_clock = rc, categoryAmounts_clock = rc,
                                        isUserCategorized_clock = rc, excludeFromBudget_clock = rc,
                                        isBudgetIncome_clock = rc, linkedRecurringExpenseId_clock = rc,
                                        linkedAmortizationEntryId_clock = rc, linkedIncomeSourceId_clock = rc,
                                        amortizationAppliedAmount_clock = rc, linkedRecurringExpenseAmount_clock = rc,
                                        linkedIncomeSourceAmount_clock = rc, linkedSavingsGoalId_clock = rc,
                                        linkedSavingsGoalAmount_clock = rc, deviceId_clock = rc,
                                        deleted_clock = rc)
                                }
                            }
                            if (anyRescued) saveTransactions()
                            anyRescued = false
                            for (i in recurringExpenses.indices) {
                                val r = recurringExpenses[i]
                                if (r.deviceId != localDeviceId) continue
                                if (stranded(r.source_clock) || stranded(r.description_clock) ||
                                    stranded(r.amount_clock) || stranded(r.repeatType_clock) ||
                                    stranded(r.repeatInterval_clock) || stranded(r.startDate_clock) ||
                                    stranded(r.monthDay1_clock) || stranded(r.monthDay2_clock) ||
                                    stranded(r.deviceId_clock) || stranded(r.deleted_clock)) {
                                    anyRescued = true
                                    recurringExpenses[i] = r.copy(
                                        source_clock = rc, description_clock = rc,
                                        amount_clock = rc, repeatType_clock = rc,
                                        repeatInterval_clock = rc, startDate_clock = rc,
                                        monthDay1_clock = rc, monthDay2_clock = rc,
                                        deviceId_clock = rc, deleted_clock = rc)
                                }
                            }
                            if (anyRescued) saveRecurringExpenses()
                            anyRescued = false
                            for (i in incomeSources.indices) {
                                val s = incomeSources[i]
                                if (s.deviceId != localDeviceId) continue
                                if (stranded(s.source_clock) || stranded(s.description_clock) ||
                                    stranded(s.amount_clock) || stranded(s.repeatType_clock) ||
                                    stranded(s.repeatInterval_clock) || stranded(s.startDate_clock) ||
                                    stranded(s.monthDay1_clock) || stranded(s.monthDay2_clock) ||
                                    stranded(s.deviceId_clock) || stranded(s.deleted_clock)) {
                                    anyRescued = true
                                    incomeSources[i] = s.copy(
                                        source_clock = rc, description_clock = rc,
                                        amount_clock = rc, repeatType_clock = rc,
                                        repeatInterval_clock = rc, startDate_clock = rc,
                                        monthDay1_clock = rc, monthDay2_clock = rc,
                                        deviceId_clock = rc, deleted_clock = rc)
                                }
                            }
                            if (anyRescued) saveIncomeSources()
                            // Rescue period ledger entries with stranded clocks
                            anyRescued = false
                            for (i in periodLedger.indices) {
                                val e = periodLedger[i]
                                if (e.deviceId != localDeviceId) continue
                                if (stranded(e.clock)) {
                                    anyRescued = true
                                    periodLedger[i] = e.copy(clock = rc)
                                }
                            }
                            if (anyRescued) savePeriodLedger()
                            // Rescue savings goals with stranded clocks
                            anyRescued = false
                            for (i in savingsGoals.indices) {
                                val g = savingsGoals[i]
                                if (g.deviceId != localDeviceId) continue
                                if (stranded(g.name_clock) || stranded(g.targetAmount_clock) ||
                                    stranded(g.totalSavedSoFar_clock) || stranded(g.contributionPerPeriod_clock) ||
                                    stranded(g.isPaused_clock) || stranded(g.deviceId_clock) ||
                                    stranded(g.deleted_clock)) {
                                    anyRescued = true
                                    savingsGoals[i] = g.copy(
                                        name_clock = rc, targetAmount_clock = rc,
                                        totalSavedSoFar_clock = rc, contributionPerPeriod_clock = rc,
                                        isPaused_clock = rc, deviceId_clock = rc, deleted_clock = rc)
                                }
                            }
                            if (anyRescued) saveSavingsGoals()
                            // Rescue amortization entries with stranded clocks
                            anyRescued = false
                            for (i in amortizationEntries.indices) {
                                val e = amortizationEntries[i]
                                if (e.deviceId != localDeviceId) continue
                                if (stranded(e.source_clock) || stranded(e.amount_clock) ||
                                    stranded(e.totalPeriods_clock) || stranded(e.startDate_clock) ||
                                    stranded(e.isPaused_clock) || stranded(e.deviceId_clock) ||
                                    stranded(e.deleted_clock)) {
                                    anyRescued = true
                                    amortizationEntries[i] = e.copy(
                                        source_clock = rc, amount_clock = rc,
                                        totalPeriods_clock = rc, startDate_clock = rc,
                                        isPaused_clock = rc, deviceId_clock = rc, deleted_clock = rc)
                                }
                            }
                            if (anyRescued) saveAmortizationEntries()
                            // Rescue categories with stranded clocks
                            anyRescued = false
                            for (i in categories.indices) {
                                val c = categories[i]
                                if (c.deviceId != localDeviceId) continue
                                if (stranded(c.name_clock) || stranded(c.tag_clock) ||
                                    stranded(c.deviceId_clock) || stranded(c.deleted_clock)) {
                                    anyRescued = true
                                    categories[i] = c.copy(
                                        name_clock = rc, tag_clock = rc,
                                        deviceId_clock = rc, deleted_clock = rc)
                                }
                            }
                            if (anyRescued) saveCategories()
                        }

                        // Continuous fix: stamp critical field clocks that are still 0
                        // on records already in the sync system.  This ensures the
                        // DeltaBuilder piggybacking can include them, preventing
                        // skeleton records on the receiving device.
                        // Runs every cycle because CSV import can re-introduce clk=0.
                        // Uses a single clock tick for the entire batch to prevent
                        // push loop oscillation (same fix as rescue above).
                        run {
                            var changed = false
                            val clk0Fix = rescueClk  // same tick as rescue
                            transactions.forEachIndexed { i, t ->
                                // Only fix records in the sync system (have a deviceId)
                                if (t.deviceId.isEmpty()) return@forEachIndexed
                                val needsSource = t.source_clock == 0L
                                val needsAmount = t.amount_clock == 0L
                                val needsDate = t.date_clock == 0L
                                val needsType = t.type_clock == 0L
                                val needsDesc = t.description_clock == 0L
                                val needsDeviceId = t.deviceId_clock == 0L
                                val needsExclude = t.excludeFromBudget && t.excludeFromBudget_clock == 0L
                                val needsBudgetIncome = t.isBudgetIncome && t.isBudgetIncome_clock == 0L
                                if (needsSource || needsAmount || needsDate || needsType ||
                                    needsDesc || needsDeviceId || needsExclude || needsBudgetIncome) {
                                    changed = true
                                    transactions[i] = t.copy(
                                        source_clock = if (needsSource) clk0Fix else t.source_clock,
                                        amount_clock = if (needsAmount) clk0Fix else t.amount_clock,
                                        date_clock = if (needsDate) clk0Fix else t.date_clock,
                                        type_clock = if (needsType) clk0Fix else t.type_clock,
                                        description_clock = if (needsDesc) clk0Fix else t.description_clock,
                                        deviceId_clock = if (needsDeviceId) clk0Fix else t.deviceId_clock,
                                        excludeFromBudget_clock = if (needsExclude) clk0Fix else t.excludeFromBudget_clock,
                                        isBudgetIncome_clock = if (needsBudgetIncome) clk0Fix else t.isBudgetIncome_clock
                                    )
                                }
                            }
                            if (changed) saveTransactions()
                        }

                        // Merge disk transactions into memory before snapshotting.
                        // The widget writes directly to disk; without this, the
                        // sync loop would overwrite disk with the stale in-memory
                        // list, silently erasing widget-added transactions.
                        val diskTxns = TransactionRepository.load(context)
                        val memTxnIds = transactions.map { it.id }.toSet()
                        var diskMerged = false
                        for (dt in diskTxns) {
                            if (dt.id !in memTxnIds) {
                                transactions.add(dt)
                                diskMerged = true
                            }
                        }
                        if (diskMerged) {
                            saveTransactions()
                            recomputeCash()
                        }

                        // Capture snapshots before sync — records added to
                        // live lists during the sync must be preserved.
                        val syncTxns = transactions.toList()
                        val syncRe = recurringExpenses.toList()
                        val syncIs = incomeSources.toList()
                        val syncSg = savingsGoals.toList()
                        val syncAe = amortizationEntries.toList()
                        val syncCat = categories.toList()
                        val syncPl = periodLedger.toList()

                        val result = engine.sync(
                            syncTxns, syncRe, syncIs, syncSg, syncAe, syncCat,
                            sharedSettings,
                            existingCatIdRemap = existingRemap,
                            periodLedgerEntries = syncPl,
                            onProgress = { msg -> syncProgressMessage = msg }
                        )
                        if (result.success) {
                            // Find records added to live lists DURING the sync
                            // (IDs not in the snapshot we passed to engine.sync).
                            // Without this, clear()+addAll(merged) would silently
                            // drop transactions the user entered while the sync
                            // was doing network I/O.
                            val syncTxnIds = syncTxns.map { it.id }.toSet()
                            val addedTxns = transactions.filter { it.id !in syncTxnIds }
                            val syncReIds = syncRe.map { it.id }.toSet()
                            val addedRe = recurringExpenses.filter { it.id !in syncReIds }
                            val syncIsIds = syncIs.map { it.id }.toSet()
                            val addedIs = incomeSources.filter { it.id !in syncIsIds }
                            val syncSgIds = syncSg.map { it.id }.toSet()
                            val addedSg = savingsGoals.filter { it.id !in syncSgIds }
                            val syncAeIds = syncAe.map { it.id }.toSet()
                            val addedAe = amortizationEntries.filter { it.id !in syncAeIds }

                            Snapshot.withMutableSnapshot {
                                result.mergedTransactions?.let { merged ->
                                    transactions.clear()
                                    transactions.addAll(merged)
                                    for (txn in addedTxns) {
                                        if (transactions.none { it.id == txn.id }) transactions.add(txn)
                                    }
                                }
                                result.mergedRecurringExpenses?.let { merged ->
                                    recurringExpenses.clear()
                                    recurringExpenses.addAll(merged)
                                    for (re in addedRe) {
                                        if (recurringExpenses.none { it.id == re.id }) recurringExpenses.add(re)
                                    }
                                }
                                result.mergedIncomeSources?.let { merged ->
                                    incomeSources.clear()
                                    incomeSources.addAll(merged)
                                    for (src in addedIs) {
                                        if (incomeSources.none { it.id == src.id }) incomeSources.add(src)
                                    }
                                }
                                result.mergedSavingsGoals?.let { merged ->
                                    savingsGoals.clear()
                                    savingsGoals.addAll(merged)
                                    for (sg in addedSg) {
                                        if (savingsGoals.none { it.id == sg.id }) savingsGoals.add(sg)
                                    }
                                }
                                result.mergedAmortizationEntries?.let { merged ->
                                    amortizationEntries.clear()
                                    amortizationEntries.addAll(merged)
                                    for (ae in addedAe) {
                                        if (amortizationEntries.none { it.id == ae.id }) amortizationEntries.add(ae)
                                    }
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
                            // Re-stamp records created during sync so their clocks
                            // are above lastPushedClock (which may have been inflated
                            // by foreign records in the push).  Without this, the
                            // record stays in the local list but is never pushed
                            // because DeltaBuilder skips fields with clock ≤ lastPushedClock.
                            if (addedTxns.isNotEmpty()) {
                                for (added in addedTxns) {
                                    val idx = transactions.indexOfFirst { it.id == added.id }
                                    if (idx >= 0) {
                                        val clk = lamportClock.tick()
                                        transactions[idx] = transactions[idx].copy(
                                            source_clock = clk, description_clock = clk,
                                            amount_clock = clk, date_clock = clk,
                                            type_clock = clk, categoryAmounts_clock = clk,
                                            isUserCategorized_clock = clk, excludeFromBudget_clock = clk,
                                            isBudgetIncome_clock = clk,
                                            linkedRecurringExpenseId_clock = clk,
                                            linkedAmortizationEntryId_clock = clk,
                                            linkedIncomeSourceId_clock = clk,
                                            amortizationAppliedAmount_clock = clk,
                                            linkedRecurringExpenseAmount_clock = clk,
                                            linkedIncomeSourceAmount_clock = clk,
                                            deviceId_clock = clk, deleted_clock = clk
                                        )
                                    }
                                }
                            }
                            if (addedRe.isNotEmpty()) {
                                for (added in addedRe) {
                                    val idx = recurringExpenses.indexOfFirst { it.id == added.id }
                                    if (idx >= 0) {
                                        val clk = lamportClock.tick()
                                        recurringExpenses[idx] = recurringExpenses[idx].copy(
                                            source_clock = clk, description_clock = clk,
                                            amount_clock = clk, repeatType_clock = clk,
                                            repeatInterval_clock = clk, startDate_clock = clk,
                                            monthDay1_clock = clk, monthDay2_clock = clk,
                                            deviceId_clock = clk, deleted_clock = clk
                                        )
                                    }
                                }
                            }
                            if (addedIs.isNotEmpty()) {
                                for (added in addedIs) {
                                    val idx = incomeSources.indexOfFirst { it.id == added.id }
                                    if (idx >= 0) {
                                        val clk = lamportClock.tick()
                                        incomeSources[idx] = incomeSources[idx].copy(
                                            source_clock = clk, description_clock = clk,
                                            amount_clock = clk, repeatType_clock = clk,
                                            repeatInterval_clock = clk, startDate_clock = clk,
                                            monthDay1_clock = clk, monthDay2_clock = clk,
                                            deviceId_clock = clk, deleted_clock = clk
                                        )
                                    }
                                }
                            }
                            if (addedSg.isNotEmpty()) {
                                for (added in addedSg) {
                                    val idx = savingsGoals.indexOfFirst { it.id == added.id }
                                    if (idx >= 0) {
                                        val clk = lamportClock.tick()
                                        savingsGoals[idx] = savingsGoals[idx].copy(
                                            name_clock = clk, targetAmount_clock = clk,
                                            targetDate_clock = clk, totalSavedSoFar_clock = clk,
                                            contributionPerPeriod_clock = clk, isPaused_clock = clk,
                                            deviceId_clock = clk, deleted_clock = clk
                                        )
                                    }
                                }
                            }
                            if (addedAe.isNotEmpty()) {
                                for (added in addedAe) {
                                    val idx = amortizationEntries.indexOfFirst { it.id == added.id }
                                    if (idx >= 0) {
                                        val clk = lamportClock.tick()
                                        amortizationEntries[idx] = amortizationEntries[idx].copy(
                                            source_clock = clk, description_clock = clk,
                                            amount_clock = clk, totalPeriods_clock = clk,
                                            startDate_clock = clk, isPaused_clock = clk,
                                            deviceId_clock = clk, deleted_clock = clk
                                        )
                                    }
                                }
                            }
                            result.mergedTransactions?.let { saveTransactions() }
                            if (addedTxns.isNotEmpty()) saveTransactions()
                            result.mergedRecurringExpenses?.let { saveRecurringExpenses() }
                            if (addedRe.isNotEmpty()) saveRecurringExpenses()
                            result.mergedIncomeSources?.let { saveIncomeSources() }
                            if (addedIs.isNotEmpty()) saveIncomeSources()
                            result.mergedSavingsGoals?.let { saveSavingsGoals() }
                            if (addedSg.isNotEmpty()) saveSavingsGoals()
                            result.mergedAmortizationEntries?.let { saveAmortizationEntries() }
                            if (addedAe.isNotEmpty()) saveAmortizationEntries()
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
                                incomeMode = try { IncomeMode.valueOf(merged.incomeMode) } catch (_: Exception) { IncomeMode.FIXED }
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
                                    .putString("incomeMode", merged.incomeMode)
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
                                try {
                                    val json = org.json.JSONObject(remap.mapKeys { it.key.toString() })
                                    syncPrefs.edit().putString("catIdRemap", json.toString()).commit()
                                } catch (e: Exception) {
                                    android.util.Log.e("SyncLoop", "Failed to save catIdRemap: ${e.message}")
                                }
                            }
                            syncStatus = "synced"
                            syncErrorMessage = null
                            syncProgressMessage = null
                            syncPrefs.edit().putBoolean("syncDirty", false).apply()
                            lastSyncTime = "just now"
                            consecutiveErrors = 0
                            pendingAdminClaim = result.pendingAdminClaim
                            if (result.repairAttempted) syncRepairAlert = true
                            // Compute stale days
                            val lastSync = syncPrefs.getLong("lastSuccessfulSync", 0L)
                            staleDays = if (lastSync > 0L) ((System.currentTimeMillis() - lastSync) / (24 * 60 * 60 * 1000L)).toInt() else 0
                            // Refresh device list & admin status
                            try {
                                syncDevices = GroupManager.getDevices(groupId)
                                isSyncAdmin = GroupManager.isAdmin(context)
                                // Auto-populate roster with current device names (additive)
                                val currentRoster = try {
                                    val obj = org.json.JSONObject(sharedSettings.deviceRoster)
                                    obj.keys().asSequence().associateWith { obj.getString(it) }.toMutableMap()
                                } catch (_: Exception) { mutableMapOf() }
                                var rosterChanged = false
                                for (dev in syncDevices) {
                                    val name = dev.deviceName.ifEmpty { dev.deviceId.take(8) }
                                    if (currentRoster[dev.deviceId] != name) {
                                        currentRoster[dev.deviceId] = name
                                        rosterChanged = true
                                    }
                                }
                                if (rosterChanged) {
                                    val clock = lamportClock.tick()
                                    sharedSettings = sharedSettings.copy(
                                        deviceRoster = org.json.JSONObject(currentRoster as Map<*, *>).toString(),
                                        deviceRoster_clock = clock,
                                        lastChangedBy = localDeviceId
                                    )
                                    SharedSettingsRepository.save(context, sharedSettings)
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("SyncLoop", "Failed to refresh device list", e)
                            }
                        } else {
                            syncStatus = "error"
                            syncErrorMessage = result.error
                            syncProgressMessage = null
                            consecutiveErrors++
                            pendingAdminClaim = result.pendingAdminClaim
                            // Auto-leave only on explicit admin actions: the
                            // SyncEngine checks Firestore for a "removed" flag on
                            // the device doc or a "dissolved" status on the group
                            // doc.  Only those produce "removed_from_group" or
                            // "group_deleted" — transient errors, cache staleness,
                            // and offline periods never trigger auto-leave.
                            if ((result.error == "removed_from_group" || result.error == "group_deleted") && !isSyncAdmin) {
                                GroupManager.leaveGroup(context, localOnly = true)
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
                        syncProgressMessage = null
                        consecutiveErrors++
                        // Write sync errors to crash_log.txt for debugging
                        try {
                            val sb = StringBuilder()
                            sb.appendLine("=== SyncLoop Error ${java.time.LocalDateTime.now()} ===")
                            sb.appendLine("Android: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
                            sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                            sb.appendLine()
                            var t: Throwable? = e
                            while (t != null) {
                                sb.appendLine("${t.javaClass.name}: ${t.message}")
                                for (el in t.stackTrace) sb.appendLine("  at $el")
                                t = t.cause
                                if (t != null) sb.appendLine("Caused by:")
                            }
                            sb.appendLine()
                            val dir = android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS
                            )
                            java.io.File(dir, "crash_log.txt").appendText(sb.toString())
                        } catch (_: Exception) {}
                    } finally {
                        syncFileLock.unlock()
                    }
                    // Exponential backoff: 60s on success, doubling on errors
                    // up to 5 minutes max. Short-circuit if user triggers sync.
                    val backoffMs = if (consecutiveErrors == 0) 60_000L
                        else minOf(60_000L * (1L shl minOf(consecutiveErrors, 3)), 300_000L)
                    val before = syncTrigger
                    val deadline = System.currentTimeMillis() + backoffMs
                    while (syncTrigger == before && System.currentTimeMillis() < deadline) {
                        delay(1_000)
                    }
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
                    excludeFromBudget_clock = clock,
                    isBudgetIncome_clock = clock,
                    linkedRecurringExpenseId_clock = clock,
                    linkedAmortizationEntryId_clock = clock,
                    linkedIncomeSourceId_clock = clock,
                    amortizationAppliedAmount_clock = clock,
                    linkedRecurringExpenseAmount_clock = clock,
                    linkedIncomeSourceAmount_clock = clock,
                    linkedSavingsGoalId_clock = clock,
                    linkedSavingsGoalAmount_clock = clock,
                    deviceId_clock = clock
                )
                // If linking to a savings goal, deduct from goal's totalSavedSoFar
                if (stamped.linkedSavingsGoalId != null) {
                    val gIdx = savingsGoals.indexOfFirst { it.id == stamped.linkedSavingsGoalId }
                    if (gIdx >= 0) {
                        val g = savingsGoals[gIdx]
                        val goalClock = lamportClock.tick()
                        savingsGoals[gIdx] = g.copy(
                            totalSavedSoFar = maxOf(0.0, g.totalSavedSoFar - stamped.amount),
                            totalSavedSoFar_clock = goalClock
                        )
                        saveSavingsGoals()
                    }
                }
                transactions.add(stamped)
                saveTransactions()
                recomputeCash()
            }

            // Reload transactions from disk on resume to pick up widget-added entries
            LaunchedEffect(syncTrigger) {
                if (syncTrigger == 0) return@LaunchedEffect  // skip initial composition
                val diskTransactions = TransactionRepository.load(context)
                if (diskTransactions.size != transactions.size ||
                    diskTransactions.map { it.id }.toSet() != transactions.map { it.id }.toSet()) {
                    transactions.clear()
                    transactions.addAll(diskTransactions)
                    recomputeCash()
                }
            }

            // Linking chain: recurring/amortization/income match (no duplicate check)
            fun runLinkingChain(txn: Transaction) {
                val alreadyLinked = txn.linkedRecurringExpenseId != null || txn.linkedAmortizationEntryId != null || txn.linkedIncomeSourceId != null || txn.linkedSavingsGoalId != null
                if (!alreadyLinked) {
                    val recurringMatch = findRecurringExpenseMatch(txn, activeRecurringExpenses, percentTolerance, matchDollar, matchChars, matchDays)
                    if (recurringMatch != null) {
                        dashPendingRecurringTxn = txn
                        dashPendingRecurringMatch = recurringMatch
                        dashShowRecurringDialog = true
                    } else {
                        val amortizationMatch = findAmortizationMatch(txn, activeAmortizationEntries, percentTolerance, matchDollar, matchChars)
                        if (amortizationMatch != null) {
                            dashPendingAmortizationTxn = txn
                            dashPendingAmortizationMatch = amortizationMatch
                            dashShowAmortizationDialog = true
                        } else {
                            val budgetMatch = findBudgetIncomeMatch(txn, activeIncomeSources, matchChars, matchDays)
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

            // Full matching chain: duplicate check first, then linking
            fun runMatchingChain(txn: Transaction) {
                val dup = findDuplicate(txn, activeTransactions, percentTolerance, matchDollar, matchDays, matchChars)
                if (dup != null) {
                    dashPendingManualSave = txn
                    dashManualDuplicateMatch = dup
                    dashShowManualDuplicateDialog = true
                } else {
                    runLinkingChain(txn)
                }
            }

            val dateFormatter = remember(dateFormatPattern) {
                DateTimeFormatter.ofPattern(dateFormatPattern)
            }
            val existingIds = transactions.map { it.id }.toSet()
            val categoryMap = categories.associateBy { it.id }

            // One-time simulation trace on startup
            LaunchedEffect(Unit) {
                try {
                    val trace = SavingsSimulator.traceSimulation(
                        incomeSources = activeIncomeSources,
                        recurringExpenses = activeRecurringExpenses,
                        budgetPeriod = budgetPeriod,
                        baseBudget = if (isManualBudgetEnabled) manualBudgetAmount else safeBudgetAmount,
                        amortizationEntries = activeAmortizationEntries,
                        savingsGoals = activeSavingsGoals,
                        availableCash = simAvailableCash,
                        resetDayOfWeek = resetDayOfWeek,
                        resetDayOfMonth = resetDayOfMonth,
                        currencySymbol = currencySymbol,
                        today = budgetToday
                    )
                    val file = java.io.File("/storage/emulated/0/Download/simulation_trace.txt")
                    file.writeText(trace)
                } catch (_: Exception) { }
            }

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
                            now.toLocalDate().minusDays(1) else now.toLocalDate()
                        // Use aligned period start for counting, not raw lastRefreshDate,
                        // to prevent drift when the user misses opening the app.
                        val currentPeriod = BudgetCalculator.currentPeriodStart(
                            budgetPeriod, resetDayOfWeek, resetDayOfMonth, resetHour = resetHour
                        )
                        val missedPeriods = BudgetCalculator.countPeriodsCompleted(lastRefreshDate!!, currentPeriod, budgetPeriod)
                        if (missedPeriods > 0) {
                            lastRefreshDate = currentPeriod

                            // Create one ledger entry per missed period using aligned dates
                            for (period in 0 until missedPeriods) {
                                val periodsBack = (missedPeriods - 1 - period).toLong()
                                val periodDate = when (budgetPeriod) {
                                    BudgetPeriod.DAILY -> currentPeriod.minusDays(periodsBack)
                                    BudgetPeriod.WEEKLY -> currentPeriod.minusWeeks(periodsBack)
                                    BudgetPeriod.MONTHLY -> currentPeriod.minusMonths(periodsBack)
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
                            val savingsClk = lamportClock.tick()
                            for (period in 0 until missedPeriods) {
                                val periodsBack = (missedPeriods - 1 - period).toLong()
                                val periodDate = when (budgetPeriod) {
                                    BudgetPeriod.DAILY -> currentPeriod.minusDays(periodsBack)
                                    BudgetPeriod.WEEKLY -> currentPeriod.minusWeeks(periodsBack)
                                    BudgetPeriod.MONTHLY -> currentPeriod.minusMonths(periodsBack)
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
                                                            totalSavedSoFar = goal.totalSavedSoFar + deduction,
                                                            totalSavedSoFar_clock = savingsClk
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
                                                        totalSavedSoFar = goal.totalSavedSoFar + contribution,
                                                        totalSavedSoFar_clock = savingsClk
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            saveSavingsGoals()

                            // Update RE set-aside tracking for each catch-up period
                            val reClk = lamportClock.tick()
                            var reChanged = false
                            for (period in 0 until missedPeriods) {
                                val periodsBack = (missedPeriods - 1 - period).toLong()
                                val periodDate = when (budgetPeriod) {
                                    BudgetPeriod.DAILY -> currentPeriod.minusDays(periodsBack)
                                    BudgetPeriod.WEEKLY -> currentPeriod.minusWeeks(periodsBack)
                                    BudgetPeriod.MONTHLY -> currentPeriod.minusMonths(periodsBack)
                                }
                                val periodEnd = when (budgetPeriod) {
                                    BudgetPeriod.DAILY -> periodDate.plusDays(1)
                                    BudgetPeriod.WEEKLY -> periodDate.plusWeeks(1)
                                    BudgetPeriod.MONTHLY -> periodDate.plusMonths(1)
                                }
                                recurringExpenses.forEachIndexed { idx, re ->
                                    if (re.deleted) return@forEachIndexed
                                    val occurrences = BudgetCalculator.generateOccurrences(
                                        re.repeatType, re.repeatInterval, re.startDate,
                                        re.monthDay1, re.monthDay2, periodDate, periodEnd.minusDays(1)
                                    )
                                    if (occurrences.isNotEmpty()) {
                                        // Due date reached: reset set-aside, deactivate accelerated
                                        recurringExpenses[idx] = re.copy(
                                            setAsideSoFar = 0.0,
                                            setAsideSoFar_clock = reClk,
                                            isAccelerated = if (re.isAccelerated) false else re.isAccelerated,
                                            isAccelerated_clock = if (re.isAccelerated) reClk else re.isAccelerated_clock
                                        )
                                        reChanged = true
                                    } else {
                                        val increment = if (re.isAccelerated) {
                                            val periodsLeft = BudgetCalculator.periodsUntilNextOccurrence(re, budgetPeriod, periodDate)
                                            if (periodsLeft > 0) BudgetCalculator.roundCents(
                                                (re.amount - re.setAsideSoFar) / periodsLeft
                                            ) else 0.0
                                        } else {
                                            BudgetCalculator.normalPerPeriodDeduction(re, budgetPeriod, periodDate)
                                        }
                                        if (increment > 0) {
                                            recurringExpenses[idx] = re.copy(
                                                setAsideSoFar = minOf(re.setAsideSoFar + increment, re.amount),
                                                setAsideSoFar_clock = reClk
                                            )
                                            reChanged = true
                                        }
                                    }
                                }
                            }
                            if (reChanged) saveRecurringExpenses()

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
                    dump.appendLine("availableCash (prefs): ${prefs.getDoubleCompat("availableCash")}")
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

                    dump.appendLine("── Recurring Expenses ──")
                    for (re in recurringExpenses.sortedBy { it.source }) {
                        dump.appendLine("  id=${re.id} '${re.source}' amt=${re.amount} ${re.repeatType}/${re.repeatInterval} dev=${re.deviceId.take(8)}… del=${re.deleted} setAside=${re.setAsideSoFar} accel=${re.isAccelerated}")
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
                            txn.linkedRecurringExpenseId?.let { "reId=$it(clk=${txn.linkedRecurringExpenseId_clock},reAmt=${txn.linkedRecurringExpenseAmount})" },
                            txn.linkedAmortizationEntryId?.let { "aeId=$it(clk=${txn.linkedAmortizationEntryId_clock},appl=${txn.amortizationAppliedAmount})" },
                            txn.linkedIncomeSourceId?.let { "isId=$it(clk=${txn.linkedIncomeSourceId_clock},isAmt=${txn.linkedIncomeSourceAmount})" },
                            txn.linkedSavingsGoalId?.let { "sgId=$it(clk=${txn.linkedSavingsGoalId_clock},sgAmt=${txn.linkedSavingsGoalAmount})" }
                        ).joinToString(" ").ifEmpty { "" }
                        val flagDesc = listOfNotNull(
                            if (txn.excludeFromBudget) "ef=true(clk=${txn.excludeFromBudget_clock})" else null,
                            if (txn.isBudgetIncome) "bi=true" else null
                        ).joinToString(" ").let { if (it.isNotEmpty()) "$it " else "" }
                        dump.appendLine("  ${txn.date} ${txn.type} ${txn.amount} '${txn.source}' dev=${txn.deviceId.take(8)}… ba=$budgetAccounted ${flagDesc}aClk=${txn.amount_clock} cClk=${txn.categoryAmounts_clock} dIdClk=${txn.deviceId_clock} $linkDesc $catDesc")
                        if (txn.type == TransactionType.EXPENSE && !budgetAccounted && !txn.excludeFromBudget) totalExpense += txn.amount
                        else if (txn.type == TransactionType.INCOME && !txn.isBudgetIncome && !txn.excludeFromBudget) totalIncome += txn.amount
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
                    android.util.Log.e("DiagDump", "Diag write failed: ${e.message}")
                }
                true
            }

            val adBannerHeight = if (!isPaidUser) 50.dp else 0.dp
            SyncBudgetTheme(strings = strings, adBannerHeight = adBannerHeight) {
              val toastState = LocalAppToast.current
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
                        Text(strings.dashboard.adPlaceholder, color = Color.Gray, fontSize = 12.sp)
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
                            "simulation_graph_help" -> "simulation_graph"
                            "simulation_graph" -> "future_expenditures"
                            "budget_config" -> "settings"
                            "budget_calendar_help" -> "budget_calendar"
                            "family_sync" -> "settings"
                            "family_sync_help" -> "family_sync"
                            else -> "main"
                        }
                    }
                }

                val doSyncNow: () -> Unit = {
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
                            val sTxns = transactions.toList()
                            val sRe = recurringExpenses.toList()
                            val sIs = incomeSources.toList()
                            val sSg = savingsGoals.toList()
                            val sAe = amortizationEntries.toList()
                            val sCat = categories.toList()
                            val sPl = periodLedger.toList()
                            val result = engine.sync(
                                sTxns, sRe, sIs, sSg, sAe, sCat,
                                sharedSettings,
                                existingCatIdRemap = existRemap,
                                periodLedgerEntries = sPl,
                                onProgress = { msg -> syncProgressMessage = msg }
                            )
                            if (result.success) {
                                val sTxnIds = sTxns.map { it.id }.toSet()
                                val aT = transactions.filter { it.id !in sTxnIds }
                                val sReIds = sRe.map { it.id }.toSet()
                                val aR = recurringExpenses.filter { it.id !in sReIds }
                                val sIsIds = sIs.map { it.id }.toSet()
                                val aI = incomeSources.filter { it.id !in sIsIds }
                                val sSgIds = sSg.map { it.id }.toSet()
                                val aS = savingsGoals.filter { it.id !in sSgIds }
                                val sAeIds = sAe.map { it.id }.toSet()
                                val aE = amortizationEntries.filter { it.id !in sAeIds }
                                result.mergedTransactions?.let { m -> transactions.clear(); transactions.addAll(m); aT.forEach { t -> if (transactions.none { it.id == t.id }) transactions.add(t) }; saveTransactions() }
                                result.mergedRecurringExpenses?.let { m -> recurringExpenses.clear(); recurringExpenses.addAll(m); aR.forEach { r -> if (recurringExpenses.none { it.id == r.id }) recurringExpenses.add(r) }; saveRecurringExpenses() }
                                result.mergedIncomeSources?.let { m -> incomeSources.clear(); incomeSources.addAll(m); aI.forEach { s -> if (incomeSources.none { it.id == s.id }) incomeSources.add(s) }; saveIncomeSources() }
                                result.mergedSavingsGoals?.let { m -> savingsGoals.clear(); savingsGoals.addAll(m); aS.forEach { g -> if (savingsGoals.none { it.id == g.id }) savingsGoals.add(g) }; saveSavingsGoals() }
                                result.mergedAmortizationEntries?.let { m -> amortizationEntries.clear(); amortizationEntries.addAll(m); aE.forEach { e -> if (amortizationEntries.none { it.id == e.id }) amortizationEntries.add(e) }; saveAmortizationEntries() }
                                result.mergedCategories?.let { categories.clear(); categories.addAll(it); saveCategories() }
                                result.mergedPeriodLedgerEntries?.let { periodLedger.clear(); periodLedger.addAll(it); savePeriodLedger() }
                                for (addedList in listOf(aT)) {
                                    for (added in addedList) {
                                        val idx = transactions.indexOfFirst { it.id == added.id }
                                        if (idx >= 0) {
                                            val clk = lamportClock.tick()
                                            transactions[idx] = transactions[idx].copy(
                                                source_clock = clk, description_clock = clk,
                                                amount_clock = clk, date_clock = clk,
                                                type_clock = clk, categoryAmounts_clock = clk,
                                                isUserCategorized_clock = clk, excludeFromBudget_clock = clk,
                                                isBudgetIncome_clock = clk,
                                                linkedRecurringExpenseId_clock = clk,
                                                linkedAmortizationEntryId_clock = clk,
                                                linkedIncomeSourceId_clock = clk,
                                                amortizationAppliedAmount_clock = clk,
                                                linkedRecurringExpenseAmount_clock = clk,
                                                linkedIncomeSourceAmount_clock = clk,
                                                deviceId_clock = clk, deleted_clock = clk)
                                        }
                                    }
                                    if (addedList.isNotEmpty()) saveTransactions()
                                }
                                for (added in aR) {
                                    val idx = recurringExpenses.indexOfFirst { it.id == added.id }
                                    if (idx >= 0) {
                                        val clk = lamportClock.tick()
                                        recurringExpenses[idx] = recurringExpenses[idx].copy(
                                            source_clock = clk, description_clock = clk,
                                            amount_clock = clk, repeatType_clock = clk,
                                            repeatInterval_clock = clk, startDate_clock = clk,
                                            monthDay1_clock = clk, monthDay2_clock = clk,
                                            deviceId_clock = clk, deleted_clock = clk)
                                    }
                                }
                                if (aR.isNotEmpty()) saveRecurringExpenses()
                                for (added in aI) {
                                    val idx = incomeSources.indexOfFirst { it.id == added.id }
                                    if (idx >= 0) {
                                        val clk = lamportClock.tick()
                                        incomeSources[idx] = incomeSources[idx].copy(
                                            source_clock = clk, description_clock = clk,
                                            amount_clock = clk, repeatType_clock = clk,
                                            repeatInterval_clock = clk, startDate_clock = clk,
                                            monthDay1_clock = clk, monthDay2_clock = clk,
                                            deviceId_clock = clk, deleted_clock = clk)
                                    }
                                }
                                if (aI.isNotEmpty()) saveIncomeSources()
                                for (added in aS) {
                                    val idx = savingsGoals.indexOfFirst { it.id == added.id }
                                    if (idx >= 0) {
                                        val clk = lamportClock.tick()
                                        savingsGoals[idx] = savingsGoals[idx].copy(
                                            name_clock = clk, targetAmount_clock = clk,
                                            targetDate_clock = clk, totalSavedSoFar_clock = clk,
                                            contributionPerPeriod_clock = clk, isPaused_clock = clk,
                                            deviceId_clock = clk, deleted_clock = clk)
                                    }
                                }
                                if (aS.isNotEmpty()) saveSavingsGoals()
                                for (added in aE) {
                                    val idx = amortizationEntries.indexOfFirst { it.id == added.id }
                                    if (idx >= 0) {
                                        val clk = lamportClock.tick()
                                        amortizationEntries[idx] = amortizationEntries[idx].copy(
                                            source_clock = clk, description_clock = clk,
                                            amount_clock = clk, totalPeriods_clock = clk,
                                            startDate_clock = clk, isPaused_clock = clk,
                                            deviceId_clock = clk, deleted_clock = clk)
                                    }
                                }
                                if (aE.isNotEmpty()) saveAmortizationEntries()
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
                                        .putString("incomeMode", merged.incomeMode)
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
                                syncProgressMessage = null
                                syncPrefs.edit().putBoolean("syncDirty", false).apply()
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
                                syncProgressMessage = null
                                pendingAdminClaim = result.pendingAdminClaim
                                if ((result.error == "removed_from_group" || result.error == "group_deleted") && !isSyncAdmin) {
                                    GroupManager.leaveGroup(context, localOnly = true)
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
                            syncProgressMessage = null
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
                        savingsGoals = activeSavingsGoals,
                        transactions = activeTransactions,
                        categories = activeCategories,
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
                        syncRepairAlert = syncRepairAlert,
                        onDismissRepairAlert = { syncRepairAlert = false },
                        onSyncNow = doSyncNow,
                        onSupercharge = { allocations, modes ->
                            val superClk = lamportClock.tick()
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
                                                totalSavedSoFar_clock = superClk,
                                                contributionPerPeriod = newContribution,
                                                contributionPerPeriod_clock = superClk
                                            )
                                        } else {
                                            goal.copy(
                                                totalSavedSoFar = goal.totalSavedSoFar + capped,
                                                totalSavedSoFar_clock = superClk
                                            )
                                        }
                                        savingsGoals[idx] = updatedGoal
                                        deposits.add(goal.name to capped)
                                    }
                                }
                            }
                            if (deposits.isNotEmpty()) {
                                saveSavingsGoals()
                                // Create internal expense transactions so recomputeAvailableCash
                                // reflects the immediate cash outflow. These are hidden from the
                                // transaction list (filtered by "Savings: " source prefix).
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
                        onNavigateToQuickStart = { /* TODO: currentScreen = "quick_start" */ },
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
                            com.syncbudget.app.widget.BudgetWidgetProvider.updateAllWidgets(context)
                        },
                        isSubscriber = isSubscriber,
                        onSubscriberChange = { newValue ->
                            isSubscriber = newValue
                            prefs.edit().putBoolean("isSubscriber", newValue).apply()
                        },
                        subscriptionExpiry = subscriptionExpiry,
                        onSubscriptionExpiryChange = { newValue ->
                            subscriptionExpiry = newValue
                            prefs.edit().putLong("subscriptionExpiry", newValue).apply()
                        },
                        showWidgetLogo = showWidgetLogo,
                        onWidgetLogoChange = { newValue ->
                            showWidgetLogo = newValue
                            prefs.edit().putBoolean("showWidgetLogo", newValue).apply()
                            com.syncbudget.app.widget.BudgetWidgetProvider.updateAllWidgets(context)
                        },
                        categories = activeCategories,
                        transactions = activeTransactions,
                        onAddCategory = { cat ->
                            val clock = lamportClock.tick()
                            categories.add(cat.copy(
                                deviceId = localDeviceId,
                                name_clock = clock,
                                iconName_clock = clock,
                                tag_clock = if (cat.tag.isNotEmpty()) clock else 0L,
                                charted_clock = clock,
                                widgetVisible_clock = clock,
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
                        onToggleCharted = { cat ->
                            val idx = categories.indexOfFirst { it.id == cat.id }
                            if (idx >= 0) {
                                val clock = lamportClock.tick()
                                categories[idx] = categories[idx].copy(
                                    charted = !categories[idx].charted,
                                    charted_clock = clock
                                )
                                saveCategories()
                            }
                        },
                        onToggleWidgetVisible = { cat ->
                            val idx = categories.indexOfFirst { it.id == cat.id }
                            if (idx >= 0) {
                                val clock = lamportClock.tick()
                                categories[idx] = categories[idx].copy(
                                    widgetVisible = !categories[idx].widgetVisible,
                                    widgetVisible_clock = clock
                                )
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
                        transactions = activeTransactions,
                        currencySymbol = currencySymbol,
                        dateFormatPattern = dateFormatPattern,
                        categories = activeCategories,
                        isPaidUser = isPaidUser,
                        isSubscriber = isSubscriber,
                        recurringExpenses = activeRecurringExpenses,
                        amortizationEntries = activeAmortizationEntries,
                        incomeSources = activeIncomeSources,
                        savingsGoals = activeSavingsGoals,
                        matchDays = matchDays,
                        matchPercent = matchPercent,
                        matchDollar = matchDollar,
                        matchChars = matchChars,
                        chartPalette = chartPalette,
                        showAttribution = sharedSettings.showAttribution && isSyncConfigured,
                        deviceNameMap = run {
                            val roster = try {
                                val obj = org.json.JSONObject(sharedSettings.deviceRoster)
                                obj.keys().asSequence().associateWith { obj.getString(it) }
                            } catch (_: Exception) { emptyMap() }
                            // Live devices override roster (freshest names), roster fills in former members
                            roster + syncDevices.associate { it.deviceId to it.deviceName.ifEmpty { it.deviceId.take(8) } }
                        },
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
                                    excludeFromBudget_clock = if (updated.excludeFromBudget != prev.excludeFromBudget) clock else prev.excludeFromBudget_clock,
                                    isBudgetIncome_clock = if (updated.isBudgetIncome != prev.isBudgetIncome) clock else prev.isBudgetIncome_clock,
                                    linkedRecurringExpenseId_clock = if (updated.linkedRecurringExpenseId != prev.linkedRecurringExpenseId) clock else prev.linkedRecurringExpenseId_clock,
                                    linkedAmortizationEntryId_clock = if (updated.linkedAmortizationEntryId != prev.linkedAmortizationEntryId) clock else prev.linkedAmortizationEntryId_clock,
                                    linkedIncomeSourceId_clock = if (updated.linkedIncomeSourceId != prev.linkedIncomeSourceId) clock else prev.linkedIncomeSourceId_clock,
                                    // If user manually unlinks, clear remembered amounts (linked-in-error → full amount applies)
                                    amortizationAppliedAmount = if (prev.linkedAmortizationEntryId != null && updated.linkedAmortizationEntryId == null) 0.0 else prev.amortizationAppliedAmount,
                                    amortizationAppliedAmount_clock = if (prev.linkedAmortizationEntryId != null && updated.linkedAmortizationEntryId == null) clock else prev.amortizationAppliedAmount_clock,
                                    linkedRecurringExpenseAmount = if (prev.linkedRecurringExpenseId != null && updated.linkedRecurringExpenseId == null) 0.0 else prev.linkedRecurringExpenseAmount,
                                    linkedRecurringExpenseAmount_clock = if (prev.linkedRecurringExpenseId != null && updated.linkedRecurringExpenseId == null) clock else prev.linkedRecurringExpenseAmount_clock,
                                    linkedIncomeSourceAmount = if (prev.linkedIncomeSourceId != null && updated.linkedIncomeSourceId == null) 0.0 else prev.linkedIncomeSourceAmount,
                                    linkedIncomeSourceAmount_clock = if (prev.linkedIncomeSourceId != null && updated.linkedIncomeSourceId == null) clock else prev.linkedIncomeSourceAmount_clock,
                                    linkedSavingsGoalId_clock = if (updated.linkedSavingsGoalId != prev.linkedSavingsGoalId) clock else prev.linkedSavingsGoalId_clock,
                                    // Manual unlink from savings goal: clear remembered amount, restore funds to goal
                                    linkedSavingsGoalAmount = if (prev.linkedSavingsGoalId != null && updated.linkedSavingsGoalId == null) 0.0
                                        else if (updated.linkedSavingsGoalId != null && prev.linkedSavingsGoalId == null) updated.linkedSavingsGoalAmount
                                        else prev.linkedSavingsGoalAmount,
                                    linkedSavingsGoalAmount_clock = if (updated.linkedSavingsGoalId != prev.linkedSavingsGoalId) clock else prev.linkedSavingsGoalAmount_clock
                                )
                                // Handle savings goal link/unlink effects
                                val wasLinkedToGoal = prev.linkedSavingsGoalId
                                val nowLinkedToGoal = updated.linkedSavingsGoalId
                                if (wasLinkedToGoal != null && nowLinkedToGoal == null) {
                                    // Manual unlink: restore funds to goal
                                    val gIdx = savingsGoals.indexOfFirst { it.id == wasLinkedToGoal }
                                    if (gIdx >= 0) {
                                        val g = savingsGoals[gIdx]
                                        val goalClock = lamportClock.tick()
                                        savingsGoals[gIdx] = g.copy(
                                            totalSavedSoFar = g.totalSavedSoFar + prev.linkedSavingsGoalAmount,
                                            totalSavedSoFar_clock = goalClock
                                        )
                                        saveSavingsGoals()
                                    }
                                } else if (wasLinkedToGoal == null && nowLinkedToGoal != null) {
                                    // Newly linked: deduct from goal
                                    val gIdx = savingsGoals.indexOfFirst { it.id == nowLinkedToGoal }
                                    if (gIdx >= 0) {
                                        val g = savingsGoals[gIdx]
                                        val goalClock = lamportClock.tick()
                                        savingsGoals[gIdx] = g.copy(
                                            totalSavedSoFar = maxOf(0.0, g.totalSavedSoFar - updated.amount),
                                            totalSavedSoFar_clock = goalClock
                                        )
                                        saveSavingsGoals()
                                    }
                                }
                                saveTransactions()
                            }
                            recomputeCash()
                        },
                        onDeleteTransaction = { txn ->
                            val idx = transactions.indexOfFirst { it.id == txn.id }
                            if (idx >= 0) {
                                val t = transactions[idx]
                                // If linked to savings goal, restore funds
                                if (t.linkedSavingsGoalId != null && t.linkedSavingsGoalAmount > 0.0) {
                                    val gIdx = savingsGoals.indexOfFirst { it.id == t.linkedSavingsGoalId }
                                    if (gIdx >= 0) {
                                        val g = savingsGoals[gIdx]
                                        val goalClock = lamportClock.tick()
                                        savingsGoals[gIdx] = g.copy(
                                            totalSavedSoFar = g.totalSavedSoFar + t.linkedSavingsGoalAmount,
                                            totalSavedSoFar_clock = goalClock
                                        )
                                        saveSavingsGoals()
                                    }
                                }
                                transactions[idx] = t.copy(
                                    deleted = true,
                                    deleted_clock = lamportClock.tick()
                                )
                                saveTransactions()
                            }
                            recomputeCash()
                        },
                        onDeleteTransactions = { ids ->
                            val clock = lamportClock.tick()
                            var goalsChanged = false
                            transactions.forEachIndexed { index, txn ->
                                if (txn.id in ids && !txn.deleted) {
                                    // Restore savings goal funds for linked transactions
                                    if (txn.linkedSavingsGoalId != null && txn.linkedSavingsGoalAmount > 0.0) {
                                        val gIdx = savingsGoals.indexOfFirst { it.id == txn.linkedSavingsGoalId }
                                        if (gIdx >= 0) {
                                            val g = savingsGoals[gIdx]
                                            val goalClock = lamportClock.tick()
                                            savingsGoals[gIdx] = g.copy(
                                                totalSavedSoFar = g.totalSavedSoFar + txn.linkedSavingsGoalAmount,
                                                totalSavedSoFar_clock = goalClock
                                            )
                                            goalsChanged = true
                                        }
                                    }
                                    transactions[index] = txn.copy(
                                        deleted = true,
                                        deleted_clock = clock
                                    )
                                }
                            }
                            if (goalsChanged) saveSavingsGoals()
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
                            manualBudgetAmount = prefs.getDoubleCompat("manualBudgetAmount")
                            availableCash = prefs.getDoubleCompat("availableCash")
                            budgetStartDate = prefs.getString("budgetStartDate", null)?.let { LocalDate.parse(it) }
                            lastRefreshDate = prefs.getString("lastRefreshDate", null)?.let { LocalDate.parse(it) }
                            weekStartSunday = prefs.getBoolean("weekStartSunday", true)
                            matchDays = prefs.getInt("matchDays", 7)
                            matchPercent = prefs.getDoubleCompat("matchPercent", 1.0)
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
                                        linkedIncomeSourceId_clock = 0L,
                                        amortizationAppliedAmount_clock = 0L,
                                        linkedRecurringExpenseAmount_clock = 0L,
                                        linkedIncomeSourceAmount_clock = 0L,
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
                        incomeMode = incomeMode,
                        onAdjustIncomeAmount = { srcId, newAmount ->
                            val idx = incomeSources.indexOfFirst { it.id == srcId }
                            if (idx >= 0 && incomeSources[idx].amount != newAmount) {
                                val clock = lamportClock.tick()
                                incomeSources[idx] = incomeSources[idx].copy(
                                    amount = newAmount,
                                    amount_clock = clock
                                )
                                saveIncomeSources()
                            }
                        },
                        onAddAmortization = { entry ->
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
                        onDeleteAmortization = { entry ->
                            val idx = amortizationEntries.indexOfFirst { it.id == entry.id }
                            if (idx >= 0) {
                                val clock = lamportClock.tick()
                                amortizationEntries[idx] = amortizationEntries[idx].copy(
                                    deleted = true, deleted_clock = clock
                                )
                                saveAmortizationEntries()
                            }
                        },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "transactions_help" }
                    )
                    "future_expenditures" -> FutureExpendituresScreen(
                        isPaidUser = isPaidUser,
                        isSubscriber = isSubscriber,
                        savingsGoals = activeSavingsGoals,
                        transactions = activeTransactions,
                        currencySymbol = currencySymbol,
                        budgetPeriod = budgetPeriod,
                        dateFormatPattern = dateFormatPattern,
                        recurringExpenses = activeRecurringExpenses,
                        incomeSources = activeIncomeSources,
                        amortizationEntries = activeAmortizationEntries,
                        baseBudget = if (isManualBudgetEnabled) manualBudgetAmount else safeBudgetAmount,
                        availableCash = simAvailableCash,
                        resetDayOfWeek = resetDayOfWeek,
                        resetDayOfMonth = resetDayOfMonth,
                        today = budgetToday,
                        isManualOverBudget = isManualBudgetEnabled && manualBudgetAmount > safeBudgetAmount,
                        budgetPeriodLabel = when (budgetPeriod) {
                            BudgetPeriod.DAILY -> strings.futureExpenditures.savingsPeriodDaily
                            BudgetPeriod.WEEKLY -> strings.futureExpenditures.savingsPeriodWeekly
                            BudgetPeriod.MONTHLY -> strings.futureExpenditures.savingsPeriodMonthly
                        },
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
                                val clock = lamportClock.tick()
                                savingsGoals[idx] = savingsGoals[idx].copy(deleted = true, deleted_clock = clock)
                                saveSavingsGoals()
                                // Unlink any transactions linked to this goal
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedSavingsGoalId == goal.id) {
                                        transactions[i] = txn.copy(
                                            linkedSavingsGoalId = null,
                                            linkedSavingsGoalId_clock = clock
                                        )
                                    }
                                }
                                saveTransactions()
                                recomputeCash()
                            }
                        },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "future_expenditures_help" },
                        onViewChart = { currentScreen = "simulation_graph" }
                    )
                    "simulation_graph" -> SimulationGraphScreen(
                        incomeSources = activeIncomeSources,
                        recurringExpenses = activeRecurringExpenses,
                        budgetPeriod = budgetPeriod,
                        baseBudget = if (isManualBudgetEnabled) manualBudgetAmount else safeBudgetAmount,
                        amortizationEntries = activeAmortizationEntries,
                        savingsGoals = activeSavingsGoals,
                        availableCash = simAvailableCash,
                        resetDayOfWeek = resetDayOfWeek,
                        resetDayOfMonth = resetDayOfMonth,
                        currencySymbol = currencySymbol,
                        today = budgetToday,
                        onBack = { currentScreen = "future_expenditures" },
                        onHelpClick = { currentScreen = "simulation_graph_help" }
                    )
                    "amortization" -> AmortizationScreen(
                        amortizationEntries = activeAmortizationEntries,
                        currencySymbol = currencySymbol,
                        budgetPeriod = budgetPeriod,
                        dateFormatPattern = dateFormatPattern,
                        transactions = activeTransactions,
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
                                val clock = lamportClock.tick()
                                // Calculate how much has already been amortized
                                val today = java.time.LocalDate.now()
                                val elapsed = when (budgetPeriod) {
                                    BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(entry.startDate, today).toInt()
                                    BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(entry.startDate, today).toInt()
                                    BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(entry.startDate, today).toInt()
                                }.coerceIn(0, entry.totalPeriods)
                                val perPeriod = BudgetCalculator.roundCents(entry.amount / entry.totalPeriods.toDouble())
                                val appliedAmount = BudgetCalculator.roundCents(perPeriod * elapsed)

                                amortizationEntries[idx] = amortizationEntries[idx].copy(deleted = true, deleted_clock = clock)
                                saveAmortizationEntries()
                                // Unlink transactions and record the already-applied portion
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedAmortizationEntryId == entry.id) {
                                        transactions[i] = txn.copy(
                                            linkedAmortizationEntryId = null,
                                            linkedAmortizationEntryId_clock = clock,
                                            amortizationAppliedAmount = appliedAmount,
                                            amortizationAppliedAmount_clock = clock
                                        )
                                    }
                                }
                                saveTransactions()
                                recomputeCash()
                            }
                        },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "amortization_help" }
                    )
                    "recurring_expenses" -> RecurringExpensesScreen(
                        recurringExpenses = activeRecurringExpenses,
                        transactions = activeTransactions,
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
                                deviceId_clock = clock,
                                setAsideSoFar_clock = if (expense.setAsideSoFar > 0.0) clock else 0L,
                                isAccelerated_clock = if (expense.isAccelerated) clock else 0L
                            ))
                            saveRecurringExpenses()
                        },
                        onUpdateRecurringExpense = { updated ->
                            val idx = recurringExpenses.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = recurringExpenses[idx]
                                val amountChanged = updated.amount != old.amount
                                val hasLinkedTxns = amountChanged && transactions.any {
                                    it.linkedRecurringExpenseId == updated.id && !it.deleted
                                }
                                val clock = lamportClock.tick()
                                recurringExpenses[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deviceId_clock = old.deviceId_clock,
                                    deleted = old.deleted,
                                    deleted_clock = old.deleted_clock,
                                    source_clock = if (updated.source != old.source) clock else old.source_clock,
                                    description_clock = if (updated.description != old.description) clock else old.description_clock,
                                    amount_clock = if (amountChanged) clock else old.amount_clock,
                                    repeatType_clock = if (updated.repeatType != old.repeatType) clock else old.repeatType_clock,
                                    repeatInterval_clock = if (updated.repeatInterval != old.repeatInterval) clock else old.repeatInterval_clock,
                                    startDate_clock = if (updated.startDate != old.startDate) clock else old.startDate_clock,
                                    monthDay1_clock = if (updated.monthDay1 != old.monthDay1) clock else old.monthDay1_clock,
                                    monthDay2_clock = if (updated.monthDay2 != old.monthDay2) clock else old.monthDay2_clock,
                                    setAsideSoFar_clock = if (updated.setAsideSoFar != old.setAsideSoFar) clock else old.setAsideSoFar_clock,
                                    isAccelerated_clock = if (updated.isAccelerated != old.isAccelerated) clock else old.isAccelerated_clock
                                )
                                saveRecurringExpenses()
                                if (hasLinkedTxns) {
                                    pendingREAmountUpdate = Pair(updated, old.amount)
                                }
                            }
                        },
                        onDeleteRecurringExpense = { expense ->
                            val idx = recurringExpenses.indexOfFirst { it.id == expense.id }
                            if (idx >= 0) {
                                val clock = lamportClock.tick()
                                recurringExpenses[idx] = recurringExpenses[idx].copy(deleted = true, deleted_clock = clock)
                                saveRecurringExpenses()
                                // Unlink any transactions linked to this expense
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedRecurringExpenseId == expense.id) {
                                        transactions[i] = txn.copy(
                                            linkedRecurringExpenseId = null,
                                            linkedRecurringExpenseId_clock = clock
                                        )
                                    }
                                }
                                saveTransactions()
                                recomputeCash()
                            }
                        },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "recurring_expenses_help" }
                    )
                    "budget_config" -> BudgetConfigScreen(
                        incomeSources = activeIncomeSources,
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
                                val amountChanged = updated.amount != old.amount
                                val hasLinkedTxns = amountChanged && transactions.any {
                                    it.linkedIncomeSourceId == updated.id && !it.deleted
                                }
                                val clock = lamportClock.tick()
                                incomeSources[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deviceId_clock = old.deviceId_clock,
                                    deleted = old.deleted,
                                    deleted_clock = old.deleted_clock,
                                    source_clock = if (updated.source != old.source) clock else old.source_clock,
                                    description_clock = if (updated.description != old.description) clock else old.description_clock,
                                    amount_clock = if (amountChanged) clock else old.amount_clock,
                                    repeatType_clock = if (updated.repeatType != old.repeatType) clock else old.repeatType_clock,
                                    repeatInterval_clock = if (updated.repeatInterval != old.repeatInterval) clock else old.repeatInterval_clock,
                                    startDate_clock = if (updated.startDate != old.startDate) clock else old.startDate_clock,
                                    monthDay1_clock = if (updated.monthDay1 != old.monthDay1) clock else old.monthDay1_clock,
                                    monthDay2_clock = if (updated.monthDay2 != old.monthDay2) clock else old.monthDay2_clock
                                )
                                saveIncomeSources()
                                if (hasLinkedTxns) {
                                    pendingISAmountUpdate = Pair(updated, old.amount)
                                }
                            }
                        },
                        onDeleteIncomeSource = { src ->
                            val idx = incomeSources.indexOfFirst { it.id == src.id }
                            if (idx >= 0) {
                                val clock = lamportClock.tick()
                                incomeSources[idx] = incomeSources[idx].copy(deleted = true, deleted_clock = clock)
                                saveIncomeSources()
                                // Unlink any transactions linked to this income source
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedIncomeSourceId == src.id) {
                                        transactions[i] = txn.copy(
                                            linkedIncomeSourceId = null,
                                            linkedIncomeSourceId_clock = clock
                                        )
                                    }
                                }
                                saveTransactions()
                                recomputeCash()
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
                            // Auto-switch from ACTUAL_ADJUST to ACTUAL when manual override is enabled
                            if (enabled && incomeMode == IncomeMode.ACTUAL_ADJUST) {
                                incomeMode = IncomeMode.ACTUAL
                                prefs.edit().putString("incomeMode", "ACTUAL").apply()
                                if (isSyncConfigured) {
                                    val clock2 = lamportClock.tick()
                                    sharedSettings = sharedSettings.copy(incomeMode = "ACTUAL", incomeMode_clock = clock2, lastChangedBy = localDeviceId)
                                }
                            }
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
                            budgetStartDate = BudgetCalculator.currentPeriodStart(budgetPeriod, resetDayOfWeek, resetDayOfMonth, tz, resetHour)
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
                        incomeMode = incomeMode.name,
                        onIncomeModeChange = { modeName ->
                            val mode = try { IncomeMode.valueOf(modeName) } catch (_: Exception) { IncomeMode.FIXED }
                            incomeMode = mode
                            prefs.edit().putString("incomeMode", modeName).apply()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(incomeMode = modeName, incomeMode_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                            recomputeCash()
                        },
                        onBack = { currentScreen = "settings" },
                        onHelpClick = { currentScreen = "budget_config_help" }
                    )
                    "family_sync" -> FamilySyncScreen(
                        isConfigured = isSyncConfigured,
                        isSubscriber = isSubscriber,
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
                        orphanedDeviceIds = remember(transactions.toList(), syncDevices, localDeviceId, sharedSettings.deviceRoster) {
                            val roster = try {
                                val obj = org.json.JSONObject(sharedSettings.deviceRoster)
                                obj.keys().asSequence().toSet()
                            } catch (_: Exception) { emptySet() }
                            val knownIds = syncDevices.map { it.deviceId }.toSet() + localDeviceId + roster
                            transactions.toList()
                                .map { it.deviceId }
                                .filter { it.isNotEmpty() && it !in knownIds }
                                .toSet()
                        },
                        deviceRoster = remember(sharedSettings.deviceRoster) {
                            try {
                                val obj = org.json.JSONObject(sharedSettings.deviceRoster)
                                obj.keys().asSequence().associateWith { obj.getString(it) }
                            } catch (_: Exception) { emptyMap() }
                        },
                        onSaveDeviceRoster = { roster ->
                            val clock = lamportClock.tick()
                            val json = org.json.JSONObject(roster).toString()
                            sharedSettings = sharedSettings.copy(
                                deviceRoster = json,
                                deviceRoster_clock = clock,
                                lastChangedBy = localDeviceId
                            )
                            SharedSettingsRepository.save(context, sharedSettings)
                        },
                        onPurgeStaleRoster = {
                            val txnDeviceIds = transactions.toList()
                                .map { it.deviceId }
                                .filter { it.isNotEmpty() }
                                .toSet()
                            val currentIds = syncDevices.map { it.deviceId }.toSet()
                            val currentRoster = try {
                                val obj = org.json.JSONObject(sharedSettings.deviceRoster)
                                obj.keys().asSequence().associateWith { obj.getString(it) }
                            } catch (_: Exception) { emptyMap() }
                            // Keep roster entries that have transactions OR are current devices
                            val pruned = currentRoster.filterKeys { it in txnDeviceIds || it in currentIds }
                            if (pruned.size < currentRoster.size) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(
                                    deviceRoster = org.json.JSONObject(pruned).toString(),
                                    deviceRoster_clock = clock,
                                    lastChangedBy = localDeviceId
                                )
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
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
                        syncProgressMessage = syncProgressMessage,
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
                                                excludeFromBudget_clock = stampClock,
                                                isBudgetIncome_clock = stampClock,
                                                linkedRecurringExpenseId_clock = stampClock,
                                                linkedAmortizationEntryId_clock = stampClock,
                                                linkedIncomeSourceId_clock = stampClock,
                                                amortizationAppliedAmount_clock = stampClock,
                                                linkedRecurringExpenseAmount_clock = stampClock,
                                                linkedIncomeSourceAmount_clock = stampClock,
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
                                                charted_clock = stampClock, widgetVisible_clock = stampClock,
                                                deleted_clock = stampClock, deviceId_clock = stampClock
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
                                                deleted_clock = stampClock, deviceId_clock = stampClock
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
                                                isPaused_clock = stampClock, deleted_clock = stampClock,
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
                                                deleted_clock = stampClock, deviceId_clock = stampClock
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
                                                deleted_clock = stampClock, deviceId_clock = stampClock
                                            )
                                        }
                                    }
                                    saveIncomeSources()
                                    // Do NOT stamp period ledger entries during join.
                                    // The snapshot bootstrap will provide the correct
                                    // entries with proper applied amounts.  Stamping
                                    // them here creates entries with applied=0.0 and
                                    // high clocks that poison the admin's data via CRDT.

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
                                                    name_clock = stampClock,
                                                    // Don't stamp iconName_clock — joining device's
                                                    // default icons should never override admin's
                                                    // customizations in CRDT merge
                                                    iconName_clock = 0L,
                                                    tag_clock = if (c.tag.isNotEmpty()) stampClock else 0L,
                                                    charted_clock = stampClock, widgetVisible_clock = stampClock,
                                                    deleted_clock = stampClock, deviceId_clock = stampClock
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
                                                    excludeFromBudget_clock = stampClock,
                                                    isBudgetIncome_clock = stampClock,
                                                    linkedRecurringExpenseId_clock = stampClock,
                                                    linkedAmortizationEntryId_clock = stampClock,
                                                    linkedIncomeSourceId_clock = stampClock,
                                                    amortizationAppliedAmount_clock = stampClock,
                                                    linkedRecurringExpenseAmount_clock = stampClock,
                                                    linkedIncomeSourceAmount_clock = stampClock,
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
                                                    deleted_clock = stampClock, deviceId_clock = stampClock
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
                                                    deleted_clock = stampClock, deviceId_clock = stampClock
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
                                                    deleted_clock = stampClock, deviceId_clock = stampClock
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
                                                    isPaused_clock = stampClock, deleted_clock = stampClock,
                                                    deviceId_clock = stampClock
                                                )
                                            }
                                        }
                                        saveAmortizationEntries()
                                    } else {
                                        syncErrorMessage = "Invalid or expired pairing code"
                                    }
                                } catch (e: Exception) {
                                    syncStatus = "error"
                                    syncErrorMessage = e.message
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
                                        android.util.Log.d("SyncEngine", "Dissolving group $gId")
                                        GroupManager.dissolveGroup(context, gId) { msg ->
                                            syncProgressMessage = msg
                                        }
                                        android.util.Log.d("SyncEngine", "Group dissolved successfully")
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
                                        syncProgressMessage = null
                                    } catch (e: Exception) {
                                        android.util.Log.e("SyncEngine", "Dissolve failed", e)
                                        syncStatus = "error"
                                        syncProgressMessage = null
                                        toastState.show(strings.sync.dissolveError)
                                    }
                                }
                            }
                        },
                        onSyncNow = doSyncNow,
                        onGeneratePairingCode = {
                            val gId = syncGroupId
                            val key = GroupManager.getEncryptionKey(context)
                            if (gId != null && key != null) {
                                coroutineScope.launch {
                                    try {
                                        // Write a fresh snapshot so the new device can bootstrap
                                        val eng = SyncEngine(context, gId, localDeviceId, key, lamportClock)
                                        eng.writeSnapshot(
                                            transactions.toList(), recurringExpenses.toList(),
                                            incomeSources.toList(), savingsGoals.toList(),
                                            amortizationEntries.toList(), categories.toList(),
                                            sharedSettings, periodLedger.toList()
                                        )
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
                                    // Update permanent roster
                                    val currentRoster = try {
                                        val obj = org.json.JSONObject(sharedSettings.deviceRoster)
                                        obj.keys().asSequence().associateWith { obj.getString(it) }.toMutableMap()
                                    } catch (_: Exception) { mutableMapOf() }
                                    currentRoster[targetDeviceId] = newName
                                    val clock = lamportClock.tick()
                                    sharedSettings = sharedSettings.copy(
                                        deviceRoster = org.json.JSONObject(currentRoster as Map<*, *>).toString(),
                                        deviceRoster_clock = clock,
                                        lastChangedBy = localDeviceId
                                    )
                                    SharedSettingsRepository.save(context, sharedSettings)
                                } catch (_: Exception) {}
                            }
                        },
                        onRemoveDevice = { targetDeviceId ->
                            val gId = syncGroupId ?: return@FamilySyncScreen
                            coroutineScope.launch {
                                try {
                                    FirestoreService.removeDevice(gId, targetDeviceId)
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
                    "simulation_graph_help" -> SimulationGraphHelpScreen(
                        onBack = { currentScreen = "simulation_graph" }
                    )
                    "budget_calendar" -> BudgetCalendarScreen(
                        recurringExpenses = activeRecurringExpenses,
                        incomeSources = activeIncomeSources,
                        currencySymbol = currencySymbol,
                        weekStartSunday = weekStartSunday,
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "budget_calendar_help" }
                    )
                    "budget_calendar_help" -> BudgetCalendarHelpScreen(
                        onBack = { currentScreen = "budget_calendar" }
                    )
                }

                // Dashboard quick-add dialogs (rendered over any screen)
                if (dashboardShowAddIncome) {
                    TransactionDialog(
                        title = strings.common.addNewIncomeTransaction,
                        sourceLabel = strings.common.sourceLabel,
                        categories = activeCategories,
                        existingIds = existingIds,
                        currencySymbol = currencySymbol,
                        dateFormatter = dateFormatter,
                        chartPalette = chartPalette,
                        recurringExpenses = activeRecurringExpenses,
                        amortizationEntries = activeAmortizationEntries,
                        incomeSources = activeIncomeSources,
                        savingsGoals = activeSavingsGoals,
                        pastSources = activeTransactions.groupingBy { it.source }.eachCount().entries.sortedByDescending { it.value }.map { it.key },
                        budgetPeriod = budgetPeriod,
                        onDismiss = { dashboardShowAddIncome = false },
                        onSave = { txn ->
                            runMatchingChain(txn)
                            dashboardShowAddIncome = false
                        },
                        onAddAmortization = { entry ->
                            val clock = lamportClock.tick()
                            amortizationEntries.add(entry.copy(
                                deviceId = localDeviceId,
                                source_clock = clock, description_clock = clock,
                                amount_clock = clock, totalPeriods_clock = clock,
                                startDate_clock = clock, isPaused_clock = clock,
                                deviceId_clock = clock
                            ))
                            saveAmortizationEntries()
                        },
                        onDeleteAmortization = { entry ->
                            val idx = amortizationEntries.indexOfFirst { it.id == entry.id }
                            if (idx >= 0) {
                                amortizationEntries[idx] = amortizationEntries[idx].copy(
                                    deleted = true, deleted_clock = lamportClock.tick()
                                )
                                saveAmortizationEntries()
                            }
                        }
                    )
                }

                if (dashboardShowAddExpense) {
                    TransactionDialog(
                        title = strings.common.addNewExpenseTransaction,
                        sourceLabel = strings.common.merchantLabel,
                        categories = activeCategories,
                        existingIds = existingIds,
                        currencySymbol = currencySymbol,
                        dateFormatter = dateFormatter,
                        isExpense = true,
                        chartPalette = chartPalette,
                        recurringExpenses = activeRecurringExpenses,
                        amortizationEntries = activeAmortizationEntries,
                        incomeSources = activeIncomeSources,
                        savingsGoals = activeSavingsGoals,
                        pastSources = activeTransactions.groupingBy { it.source }.eachCount().entries.sortedByDescending { it.value }.map { it.key },
                        budgetPeriod = budgetPeriod,
                        onDismiss = { dashboardShowAddExpense = false },
                        onSave = { txn ->
                            runMatchingChain(txn)
                            dashboardShowAddExpense = false
                        },
                        onAddAmortization = { entry ->
                            val clock = lamportClock.tick()
                            amortizationEntries.add(entry.copy(
                                deviceId = localDeviceId,
                                source_clock = clock, description_clock = clock,
                                amount_clock = clock, totalPeriods_clock = clock,
                                startDate_clock = clock, isPaused_clock = clock,
                                deviceId_clock = clock
                            ))
                            saveAmortizationEntries()
                        },
                        onDeleteAmortization = { entry ->
                            val idx = amortizationEntries.indexOfFirst { it.id == entry.id }
                            if (idx >= 0) {
                                amortizationEntries[idx] = amortizationEntries[idx].copy(
                                    deleted = true, deleted_clock = lamportClock.tick()
                                )
                                saveAmortizationEntries()
                            }
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
                            val txn = dashPendingManualSave!!
                            dashPendingManualSave = null
                            dashManualDuplicateMatch = null
                            dashShowManualDuplicateDialog = false
                            runLinkingChain(txn)
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
                            val txn = dashPendingManualSave!!
                            dashPendingManualSave = null
                            dashManualDuplicateMatch = null
                            dashShowManualDuplicateDialog = false
                            runLinkingChain(txn)
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
                            val updatedTxn = txn.copy(
                                linkedRecurringExpenseId = dashPendingRecurringMatch!!.id,
                                linkedRecurringExpenseAmount = dashPendingRecurringMatch!!.amount
                            )
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
                                linkedIncomeSourceId = dashPendingBudgetIncomeMatch!!.id,
                                linkedIncomeSourceAmount = dashPendingBudgetIncomeMatch!!.amount,
                                categoryAmounts = if (recurringIncomeCatId != null)
                                    listOf(CategoryAmount(recurringIncomeCatId, baseTxn.amount))
                                else baseTxn.categoryAmounts,
                                isUserCategorized = true
                            )
                            // ACTUAL_ADJUST: update the income source BEFORE adding txn
                            // so recomputeCash sees matching amounts (delta = 0)
                            if (incomeMode == IncomeMode.ACTUAL_ADJUST) {
                                val srcId = dashPendingBudgetIncomeMatch!!.id
                                val idx = incomeSources.indexOfFirst { it.id == srcId }
                                if (idx >= 0 && incomeSources[idx].amount != baseTxn.amount) {
                                    val clock = lamportClock.tick()
                                    incomeSources[idx] = incomeSources[idx].copy(
                                        amount = baseTxn.amount,
                                        amount_clock = clock
                                    )
                                    saveIncomeSources()
                                }
                            }
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

                // Confirmation dialog: apply recurring expense amount change to past transactions?
                pendingREAmountUpdate?.let { (updated, oldAmount) ->
                    AdAwareAlertDialog(
                        onDismissRequest = {
                            pendingREAmountUpdate = null
                            recomputeCash()
                        },
                        title = { Text(strings.common.applyToPastTitle) },
                        text = { Text(strings.common.applyToPastBody) },
                        style = DialogStyle.WARNING,
                        confirmButton = {
                            DialogWarningButton(onClick = {
                                val clock = lamportClock.tick()
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedRecurringExpenseId == updated.id && !txn.deleted) {
                                        transactions[i] = txn.copy(
                                            linkedRecurringExpenseAmount = updated.amount,
                                            linkedRecurringExpenseAmount_clock = clock
                                        )
                                    }
                                }
                                saveTransactions()
                                pendingREAmountUpdate = null
                                recomputeCash()
                            }) { Text(strings.common.applyToPastConfirm) }
                        },
                        dismissButton = {
                            DialogSecondaryButton(onClick = {
                                pendingREAmountUpdate = null
                                recomputeCash()
                            }) { Text(strings.common.applyToPastDeny) }
                        }
                    )
                }

                // Confirmation dialog: apply income source amount change to past transactions?
                pendingISAmountUpdate?.let { (updated, oldAmount) ->
                    AdAwareAlertDialog(
                        onDismissRequest = {
                            pendingISAmountUpdate = null
                            recomputeCash()
                        },
                        title = { Text(strings.common.applyToPastTitle) },
                        text = { Text(strings.common.applyToPastBody) },
                        style = DialogStyle.WARNING,
                        confirmButton = {
                            DialogWarningButton(onClick = {
                                val clock = lamportClock.tick()
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedIncomeSourceId == updated.id && !txn.deleted) {
                                        transactions[i] = txn.copy(
                                            linkedIncomeSourceAmount = updated.amount,
                                            linkedIncomeSourceAmount_clock = clock
                                        )
                                    }
                                }
                                saveTransactions()
                                pendingISAmountUpdate = null
                                recomputeCash()
                            }) { Text(strings.common.applyToPastConfirm) }
                        },
                        dismissButton = {
                            DialogSecondaryButton(onClick = {
                                pendingISAmountUpdate = null
                                recomputeCash()
                            }) { Text(strings.common.applyToPastDeny) }
                        }
                    )
                }
                } // Box(weight)
              } // Column
            } // SyncBudgetTheme
        }
    }
}
