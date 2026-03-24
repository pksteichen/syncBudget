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
import com.syncbudget.app.data.sync.FcmSender
import com.syncbudget.app.data.sync.FirestoreService
import com.syncbudget.app.data.sync.GroupManager
import java.time.ZoneId
import com.syncbudget.app.data.sync.PeriodLedgerEntry
import com.syncbudget.app.data.sync.PeriodLedgerRepository
import com.syncbudget.app.data.sync.SyncIdGenerator
import com.syncbudget.app.data.sync.FirestoreDocSync
import com.syncbudget.app.data.sync.SyncWriteHelper
import com.syncbudget.app.data.sync.EncryptedDocSerializer
import kotlinx.coroutines.awaitCancellation
import com.syncbudget.app.data.sync.SubscriptionReminderReceiver
import com.syncbudget.app.data.sync.SyncWorker
import com.syncbudget.app.data.sync.active
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.syncbudget.app.data.BackupManager
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
import com.syncbudget.app.ui.screens.QuickStartOverlay
import com.syncbudget.app.ui.screens.QuickStartStep
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
import com.syncbudget.app.ui.theme.AdAwareDialog
import com.syncbudget.app.ui.theme.DialogDangerButton
import com.syncbudget.app.ui.theme.DialogPrimaryButton
import com.syncbudget.app.ui.theme.DialogFooter
import com.syncbudget.app.ui.theme.DialogHeader
import com.syncbudget.app.ui.theme.DialogStyle
import com.syncbudget.app.ui.theme.DialogWarningButton
import com.syncbudget.app.ui.theme.DialogSecondaryButton
import com.syncbudget.app.ui.theme.SyncBudgetTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncbudget.app.ui.theme.LocalAppToast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.tasks.await

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
                val dir = BackupManager.getSupportDir()
                java.io.File(dir, "crash_log.txt").appendText(sb.toString())
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        enableEdgeToEdge()
        setContent {
            val soundPlayer = remember { FlipSoundPlayer(this@MainActivity) }

            DisposableEffect(Unit) {
                onDispose { soundPlayer.release() }
            }

            // Sign in anonymously to Firebase — required for Firestore security
            // rules that check request.auth != null.  Completely invisible to user.
            var firebaseAuthReady by remember { mutableStateOf(
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
            ) }
            LaunchedEffect(Unit) {
                if (!firebaseAuthReady) {
                    try {
                        com.google.firebase.auth.FirebaseAuth.getInstance()
                            .signInAnonymously()
                            .await()
                        firebaseAuthReady = true
                    } catch (e: Exception) {
                        android.util.Log.w("Auth", "Anonymous sign-in failed: ${e.message}")
                        // App continues — Firestore may reject requests until auth succeeds
                    }
                }
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
            var quickStartStep by remember { mutableStateOf<QuickStartStep?>(null) }
            var subscriptionExpiry by remember {
                mutableStateOf(prefs.getLong("subscriptionExpiry",
                    System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            }
            var showWidgetLogo by remember { mutableStateOf(prefs.getBoolean("showWidgetLogo", true)) }

            val backupPrefs = remember { context.getSharedPreferences("backup_prefs", android.content.Context.MODE_PRIVATE) }
            var backupsEnabled by remember { mutableStateOf(backupPrefs.getBoolean("backups_enabled", false)) }
            var backupFrequencyWeeks by remember { mutableIntStateOf(backupPrefs.getInt("backup_frequency_weeks", 1)) }
            var backupRetention by remember { mutableIntStateOf(backupPrefs.getInt("backup_retention", 1)) }
            var lastBackupDate by remember { mutableStateOf<String?>(backupPrefs.getString("last_backup_date", null)) }
            var showBackupPasswordDialog by remember { mutableStateOf(false) }
            var showDisableBackupDialog by remember { mutableStateOf(false) }
            var showRestoreDialog by remember { mutableStateOf(false) }
            var showSavePhotosDialog by remember { mutableStateOf(false) }

            // Matching configuration
            var matchDays by remember { mutableIntStateOf(prefs.getInt("matchDays", 7)) }
            var matchPercent by remember { mutableDoubleStateOf(
                prefs.getDoubleCompat("matchPercent", 1.0)
            ) }
            var matchDollar by remember { mutableIntStateOf(prefs.getInt("matchDollar", 1)) }
            var matchChars by remember { mutableIntStateOf(prefs.getInt("matchChars", 5)) }
            var weekStartSunday by remember { mutableStateOf(prefs.getBoolean("weekStartSunday", true)) }
            var chartPalette by remember { mutableStateOf(prefs.getString("chartPalette", "Sunset") ?: "Sunset") }
            // Default to device language if we support it, otherwise English
            val deviceLang = java.util.Locale.getDefault().language
            val defaultLang = if (deviceLang == "es") "es" else "en"
            var appLanguage by remember { mutableStateOf(prefs.getString("appLanguage", null) ?: defaultLang) }
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
                    val devId = SyncIdGenerator.getOrCreateDeviceId(context)
                    loaded.add(Category(id = id, name = name, iconName = def.iconName, tag = def.tag,
                        charted = def.charted, widgetVisible = def.widgetVisible,
                        deviceId = devId))
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
                // Dedup by ID before saving — duplicates can accumulate from
                // widget disk merge or "added during sync" preservation.
                val deduped = transactions.groupBy { it.id }
                    .values.map { group -> group.first() }
                if (deduped.size < transactions.size) {
                    android.util.Log.w("MainActivity", "Deduped ${transactions.size - deduped.size} duplicate transactions")
                    transactions.clear()
                    transactions.addAll(deduped)
                }
                TransactionRepository.save(context, transactions.toList())
                markSyncDirty()
            }

            fun saveCategories() {
                CategoryRepository.save(context, categories.toList())
                markSyncDirty()
            }

            // persistAvailableCash declared after sync state variables below

            // Cached active lists — filters deleted AND skeleton records (incomplete
            // CRDT records with clock==0 or empty source/name).  Uses .active from
            // SyncFilters.kt so budget calculations never see incomplete data.
            val activeTransactions: List<Transaction> by remember { derivedStateOf { transactions.toList().active } }
            val activeRecurringExpenses: List<RecurringExpense> by remember { derivedStateOf { recurringExpenses.toList().active } }
            val activeIncomeSources: List<IncomeSource> by remember { derivedStateOf { incomeSources.toList().active } }
            val activeAmortizationEntries: List<AmortizationEntry> by remember { derivedStateOf { amortizationEntries.toList().active } }
            val activeSavingsGoals: List<SavingsGoal> by remember { derivedStateOf { savingsGoals.toList().active } }
            val activeCategories: List<Category> by remember { derivedStateOf { categories.toList().active } }

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
            val staleDays = 0  // deprecated — Firestore-native sync has no stale limit
            var syncErrorMessage by remember { mutableStateOf<String?>(null) }
            var syncProgressMessage by remember { mutableStateOf<String?>(null) }
            var pendingAdminClaim by remember { mutableStateOf<AdminClaim?>(null) }
            var syncRepairAlert by remember { mutableStateOf(
                prefs.getBoolean("syncRepairAlert", false)
            ) }
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
                        val simTz = if (isSyncConfigured && sharedSettings.familyTimezone.isNotEmpty())
                            java.time.ZoneId.of(sharedSettings.familyTimezone) else null
                        val currentPeriod = BudgetCalculator.currentPeriodStart(
                            budgetPeriod, resetDayOfWeek, resetDayOfMonth, simTz, resetHour
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
                // Strip legacy clock fields from local JSON files.
                // Old CRDT data had _clock fields on every record; the
                // data classes no longer have them so the next save drops
                // them.  This forces a re-save of everything at once.
                try {
                    if (!prefs.getBoolean("migration_strip_clock_fields", false)) {
                        TransactionRepository.save(context, transactions.toList())
                        RecurringExpenseRepository.save(context, recurringExpenses.toList())
                        IncomeSourceRepository.save(context, incomeSources.toList())
                        SavingsGoalRepository.save(context, savingsGoals.toList())
                        AmortizationRepository.save(context, amortizationEntries.toList())
                        CategoryRepository.save(context, categories.toList())
                        PeriodLedgerRepository.save(context, periodLedger.toList())
                        SharedSettingsRepository.save(context, sharedSettings)
                        prefs.edit().putBoolean("migration_strip_clock_fields", true).apply()
                    }
                } catch (e: Exception) { android.util.Log.e("Migration", "strip_clock_fields failed", e) }
                try {
                    if (!syncPrefs.getBoolean("migration_fix_stale_budgetstart_ledger_ui", false)) {
                        val bsd = budgetStartDate
                        if (bsd != null) {
                            val bsdEpochDay = bsd.toEpochDay().toInt()
                            val bsdEntry = periodLedger.find { it.id == bsdEpochDay }
                            if (bsdEntry != null) {
                                val nextDayEntry = periodLedger.find { it.id == bsdEpochDay + 1 }
                                val correctAmount = nextDayEntry?.appliedAmount ?: budgetAmount
                                val idx = periodLedger.indexOfFirst { it.id == bsdEpochDay }
                                if (idx >= 0) {
                                    periodLedger[idx] = periodLedger[idx].copy(
                                        appliedAmount = correctAmount,
                                        deviceId = localDeviceId
                                    )
                                    savePeriodLedger()
                                }
                            }
                        }
                        syncPrefs.edit().putBoolean("migration_fix_stale_budgetstart_ledger_ui", true).apply()
                    }
                } catch (e: Exception) { android.util.Log.e("Migration", "fix_stale_budgetstart_ledger_ui failed", e) }

                // Removed: migration_restamp_all_period_ledger_ui (clock-only, no longer needed)

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
                    if (!syncPrefs.getBoolean("migration_backfill_linked_amounts_v2", false)) {
                        var anyChanged = false
                        val reMap = recurringExpenses.associateBy { it.id }
                        val isMap = incomeSources.associateBy { it.id }
                        transactions.forEachIndexed { i, txn ->
                            var updated = txn
                            if (txn.linkedRecurringExpenseId != null && txn.linkedRecurringExpenseAmount == 0.0) {
                                val re = reMap[txn.linkedRecurringExpenseId]
                                if (re != null) {
                                    updated = updated.copy(
                                        linkedRecurringExpenseAmount = re.amount,
                                    )
                                    anyChanged = true
                                }
                            }
                            if (txn.linkedIncomeSourceId != null && txn.linkedIncomeSourceAmount == 0.0) {
                                val src = isMap[txn.linkedIncomeSourceId]
                                if (src != null) {
                                    updated = updated.copy(
                                        linkedIncomeSourceAmount = src.amount,
                                    )
                                    anyChanged = true
                                }
                            }
                            if (updated !== txn) transactions[i] = updated
                        }
                        if (anyChanged) saveTransactions()
                        syncPrefs.edit().putBoolean("migration_backfill_linked_amounts_v2", true).apply()
                    }
                } catch (e: Exception) { android.util.Log.e("Migration", "backfill_linked_amounts_v2 failed", e) }

                try {
                    if (!syncPrefs.getBoolean("migration_add_savings_goal_fields", false)) {
                        saveTransactions()
                        syncPrefs.edit().putBoolean("migration_add_savings_goal_fields", true).apply()
                    }
                } catch (e: Exception) { android.util.Log.e("Migration", "add_savings_goal_fields failed", e) }

                // One-time dedup: remove duplicate transactions that accumulated
                // from widget disk merge or "added during sync" preservation.
                try {
                    if (!syncPrefs.getBoolean("migration_dedup_transactions", false)) {
                        val before = transactions.size
                        saveTransactions() // saveTransactions() includes dedup
                        val after = transactions.size
                        if (before != after) {
                            android.util.Log.d("Migration", "Deduped transactions: $before -> $after")
                        }
                        syncPrefs.edit().putBoolean("migration_dedup_transactions", true).apply()
                    }
                } catch (e: Exception) { android.util.Log.e("Migration", "dedup_transactions failed", e) }

                // Removed: migration_stamp_category_clocks (clock-only, no longer needed)

                // One-time migration: assign "supercharge" category to existing
                // savings goal deposit transactions (identified by "Savings: " source prefix).
                try {
                    if (!syncPrefs.getBoolean("migration_supercharge_category", false)) {
                        val superchargeCatId = categories.find { it.tag == "supercharge" }?.id
                        if (superchargeCatId != null) {
                            var changed = false
                            transactions.forEachIndexed { i, txn ->
                                if (txn.source.startsWith("Savings: ") && txn.categoryAmounts.isEmpty()) {
                                    transactions[i] = txn.copy(
                                        categoryAmounts = listOf(CategoryAmount(superchargeCatId, txn.amount)),
                                    )
                                    changed = true
                                }
                            }
                            if (changed) saveTransactions()
                        }
                        syncPrefs.edit().putBoolean("migration_supercharge_category", true).apply()
                    }
                } catch (e: Exception) { android.util.Log.e("Migration", "supercharge_category failed", e) }

                recomputeCash()

                // Ensure BudgeTrak directory tree exists so users can find it for backup recovery
                BackupManager.getBudgetrakDir()  // creates Download/BudgeTrak/
                BackupManager.getSupportDir()     // creates Download/BudgeTrak/support/
                BackupManager.getBackupDir()      // creates Download/BudgeTrak/backups/

                // Dump receipt file inventory to support dir
                try {
                    val receiptDir = java.io.File(context.filesDir, "receipts")
                    val thumbDir = java.io.File(context.filesDir, "receipt_thumbs")
                    val sb = StringBuilder()
                    sb.appendLine("=== Receipt File Inventory ${java.time.LocalDateTime.now()} ===")
                    val receiptFiles = receiptDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                    sb.appendLine("Full-size receipts: ${receiptFiles.size} files")
                    for (f in receiptFiles) {
                        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
                        val kb = "%.1f".format(f.length() / 1024.0)
                        sb.appendLine("  ${f.name}  ${opts.outWidth}x${opts.outHeight}  ${kb} KB")
                    }
                    val thumbFiles = thumbDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                    sb.appendLine("Thumbnails: ${thumbFiles.size} files")
                    for (f in thumbFiles) {
                        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
                        val kb = "%.1f".format(f.length() / 1024.0)
                        sb.appendLine("  ${f.name}  ${opts.outWidth}x${opts.outHeight}  ${kb} KB")
                    }
                    // Check for orphaned receiptIds (transaction references a file that doesn't exist)
                    // Only clean these for solo users — in a sync group, missing files will be
                    // recovered via the photo ledger re-upload process.
                    if (!isSyncConfigured) {
                        var orphansCleaned = 0
                        transactions.forEachIndexed { idx, txn ->
                            var changed = false
                            var t = txn
                            val receiptDir2 = java.io.File(context.filesDir, "receipts")
                            fun fileExists(rid: String?) = rid != null && java.io.File(receiptDir2, "$rid.jpg").exists()
                            if (t.receiptId1 != null && !fileExists(t.receiptId1)) { t = t.copy(receiptId1 = null); changed = true }
                            if (t.receiptId2 != null && !fileExists(t.receiptId2)) { t = t.copy(receiptId2 = null); changed = true }
                            if (t.receiptId3 != null && !fileExists(t.receiptId3)) { t = t.copy(receiptId3 = null); changed = true }
                            if (t.receiptId4 != null && !fileExists(t.receiptId4)) { t = t.copy(receiptId4 = null); changed = true }
                            if (t.receiptId5 != null && !fileExists(t.receiptId5)) { t = t.copy(receiptId5 = null); changed = true }
                            if (changed) {
                                transactions[idx] = t
                                orphansCleaned++
                            }
                        }
                        if (orphansCleaned > 0) {
                            sb.appendLine("Cleaned $orphansCleaned transactions with orphaned receiptIds (solo device)")
                            saveTransactions()
                        }
                    }

                    // Clean orphaned files (on disk but not referenced by any transaction)
                    val allReceiptIds = com.syncbudget.app.data.sync.ReceiptManager.collectAllReceiptIds(transactions)
                    com.syncbudget.app.data.sync.ReceiptManager.cleanOrphans(context, allReceiptIds)

                    java.io.File(BackupManager.getSupportDir(), "receipts.txt").writeText(sb.toString())

                    // Photo ledger: which transactions reference which receiptIds
                    val ledger = StringBuilder()
                    ledger.appendLine("=== Photo Ledger ${java.time.LocalDateTime.now()} ===")
                    val linkedTxns = mutableListOf<String>()
                    for (txn in transactions) {
                        val rids = listOfNotNull(txn.receiptId1, txn.receiptId2, txn.receiptId3, txn.receiptId4, txn.receiptId5)
                        if (rids.isNotEmpty()) {
                            linkedTxns.add("  txn#${txn.id} ${txn.date} ${txn.source.take(25)}: ${rids.joinToString(", ") { it.take(8) }}")
                        }
                    }
                    ledger.appendLine("Transactions with photos: ${linkedTxns.size}")
                    linkedTxns.forEach { ledger.appendLine(it) }
                    java.io.File(BackupManager.getSupportDir(), "photo_ledger.txt").writeText(ledger.toString())
                } catch (_: Exception) {}

                // Receipt local storage pruning
                val pruneAge = sharedSettings.receiptPruneAgeDays
                if (pruneAge != null) {
                    try {
                        val pruneDate = java.time.LocalDate.now().minusDays(pruneAge.toLong())
                        var pruned = 0
                        transactions.forEachIndexed { idx, txn ->
                            if (txn.date.isBefore(pruneDate)) {
                                val ids = com.syncbudget.app.data.sync.ReceiptManager.getReceiptIds(txn)
                                if (ids.isNotEmpty()) {
                                    for (rid in ids) {
                                        com.syncbudget.app.data.sync.ReceiptManager.deleteLocalReceipt(context, rid)
                                    }
                                    transactions[idx] = txn.copy(
                                        receiptId1 = null,
                                        receiptId2 = null,
                                        receiptId3 = null,
                                        receiptId4 = null,
                                        receiptId5 = null
                                    )
                                    pruned += ids.size
                                }
                            }
                        }
                        if (pruned > 0) {
                            saveTransactions()
                            android.util.Log.d("ReceiptPrune", "Pruned $pruned receipt references older than $pruneAge days")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("ReceiptPrune", "Pruning failed: ${e.message}")
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (com.syncbudget.app.data.BackupManager.isBackupDue(context)) {
                    val pwd = com.syncbudget.app.data.BackupManager.getPassword(context)
                    if (pwd != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.syncbudget.app.data.BackupManager.performBackup(context, pwd)
                        }
                        lastBackupDate = backupPrefs.getString("last_backup_date", null)
                    }
                }
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
            var lastManualSyncTime by remember { mutableStateOf(0L) }
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

            // Firestore-native sync — real-time listeners replace polling loop.
            LaunchedEffect(isSyncConfigured) {
                if (!isSyncConfigured) return@LaunchedEffect
                val groupId = GroupManager.getGroupId(context) ?: return@LaunchedEffect
                val key = GroupManager.getEncryptionKey(context) ?: return@LaunchedEffect
                val docSync = FirestoreDocSync(context, groupId, localDeviceId, key)
                SyncWriteHelper.initialize(docSync)

                // Initial device list fetch
                try {
                    syncDevices = GroupManager.getDevices(groupId)
                } catch (e: Exception) {
                    android.util.Log.w("SyncLoop", "Failed to fetch initial device list", e)
                }

                // Register FCM token for push notifications
                try {
                    val fcmPrefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                    var fcmToken = fcmPrefs.getString("fcm_token", null)
                    if (fcmToken == null) {
                        fcmToken = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                            .token.await()
                        fcmPrefs.edit().putString("fcm_token", fcmToken).apply()
                    }
                    if (fcmToken != null) {
                        FirestoreService.storeFcmToken(groupId, localDeviceId, fcmToken)
                        fcmPrefs.edit().putBoolean("token_needs_upload", false).apply()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FCM", "Token registration failed: ${e.message}")
                }

                // One-time cleanup: remove skeleton categories (empty name)
                // that were created as local defaults but superseded by real synced categories.
                // The catIdRemap already redirects any transaction references.
                if (!syncPrefs.getBoolean("migration_remove_skeleton_categories", false)) {
                    val before = categories.size
                    categories.removeAll { it.name.isEmpty() }
                    if (categories.size < before) saveCategories()
                    syncPrefs.edit().putBoolean("migration_remove_skeleton_categories", true).apply()
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
                                recurringExpenses[idx] = re.copy(
                                    setAsideSoFar = accrued, 
                                )
                                reChanged = true
                            }
                        }
                    }
                    if (reChanged) saveRecurringExpenses()
                    prefs.edit().putBoolean("migration_add_re_setaside_fields", true).apply()
                }

                // ── Firestore-native listener setup ──

                // Listener callback: update in-memory state when remote changes arrive
                docSync.onDataChanged = { event ->
                    try {
                        when (event.collection) {
                            EncryptedDocSerializer.COLLECTION_TRANSACTIONS -> {
                                val txn = event.record as Transaction
                                val idx = transactions.indexOfFirst { it.id == txn.id }
                                if (idx >= 0) transactions[idx] = txn else transactions.add(txn)
                                TransactionRepository.save(context, transactions.toList())
                                recomputeCash()
                            }
                            EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES -> {
                                val re = event.record as RecurringExpense
                                val idx = recurringExpenses.indexOfFirst { it.id == re.id }
                                if (idx >= 0) recurringExpenses[idx] = re else recurringExpenses.add(re)
                                RecurringExpenseRepository.save(context, recurringExpenses.toList())
                                recomputeCash()
                            }
                            EncryptedDocSerializer.COLLECTION_INCOME_SOURCES -> {
                                val src = event.record as IncomeSource
                                val idx = incomeSources.indexOfFirst { it.id == src.id }
                                if (idx >= 0) incomeSources[idx] = src else incomeSources.add(src)
                                IncomeSourceRepository.save(context, incomeSources.toList())
                                recomputeCash()
                            }
                            EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS -> {
                                val sg = event.record as SavingsGoal
                                val idx = savingsGoals.indexOfFirst { it.id == sg.id }
                                if (idx >= 0) savingsGoals[idx] = sg else savingsGoals.add(sg)
                                SavingsGoalRepository.save(context, savingsGoals.toList())
                                recomputeCash()
                            }
                            EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES -> {
                                val ae = event.record as AmortizationEntry
                                val idx = amortizationEntries.indexOfFirst { it.id == ae.id }
                                if (idx >= 0) amortizationEntries[idx] = ae else amortizationEntries.add(ae)
                                AmortizationRepository.save(context, amortizationEntries.toList())
                                recomputeCash()
                            }
                            EncryptedDocSerializer.COLLECTION_CATEGORIES -> {
                                val cat = event.record as Category
                                // Tag-based dedup: if a remote category has the same tag
                                // as a local one, keep local and remap transactions
                                val localMatch = if (cat.tag.isNotEmpty()) {
                                    categories.firstOrNull {
                                        it.tag == cat.tag && it.id != cat.id && !it.deleted
                                    }
                                } else null
                                if (localMatch != null) {
                                    // Remap: use local category, discard remote duplicate
                                    val remapJson = syncPrefs.getString("catIdRemap", null)
                                    val remap = if (remapJson != null) {
                                        try {
                                            val json = org.json.JSONObject(remapJson)
                                            json.keys().asSequence().associate { it.toInt() to json.getInt(it) }.toMutableMap()
                                        } catch (_: Exception) { mutableMapOf() }
                                    } else mutableMapOf()
                                    remap[cat.id] = localMatch.id
                                    syncPrefs.edit().putString("catIdRemap",
                                        org.json.JSONObject(remap.mapKeys { it.key.toString() }).toString()
                                    ).apply()
                                    // Apply remap to transactions
                                    var remapped = false
                                    for (i in transactions.indices) {
                                        val t = transactions[i]
                                        val newCats = t.categoryAmounts.map { ca ->
                                            if (ca.categoryId == cat.id) ca.copy(categoryId = localMatch.id) else ca
                                        }
                                        if (newCats != t.categoryAmounts) {
                                            transactions[i] = t.copy(categoryAmounts = newCats)
                                            remapped = true
                                        }
                                    }
                                    if (remapped) TransactionRepository.save(context, transactions.toList())
                                } else {
                                    val idx = categories.indexOfFirst { it.id == cat.id }
                                    if (idx >= 0) categories[idx] = cat else categories.add(cat)
                                    CategoryRepository.save(context, categories.toList())
                                }
                            }
                            EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER -> {
                                val ple = event.record as PeriodLedgerEntry
                                val idx = periodLedger.indexOfFirst { it.id == ple.id }
                                if (idx >= 0) periodLedger[idx] = ple else periodLedger.add(ple)
                                PeriodLedgerRepository.save(context, periodLedger.toList())
                                recomputeCash()
                            }
                            EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS -> {
                                val merged = event.record as SharedSettings
                                sharedSettings = merged
                                SharedSettingsRepository.save(context, merged)
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
                                val syncedStartDate = merged.budgetStartDate?.let {
                                    try { java.time.LocalDate.parse(it) } catch (_: Exception) { null }
                                }
                                if (syncedStartDate != null && syncedStartDate != budgetStartDate) {
                                    budgetStartDate = syncedStartDate
                                    lastRefreshDate = java.time.LocalDate.now()
                                    prefs.edit()
                                        .putString("budgetStartDate", budgetStartDate.toString())
                                        .putString("lastRefreshDate", lastRefreshDate.toString())
                                        .apply()
                                }
                                prefs.edit()
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
                                    .apply()
                                recomputeCash()
                            }
                        }
                        com.syncbudget.app.widget.BudgetWidgetProvider.updateAllWidgets(context)
                    } catch (e: Exception) {
                        android.util.Log.e("SyncListener", "Failed to handle change: ${event.collection}", e)
                    }
                }

                // Start real-time listeners
                try {
                    docSync.startListeners()
                    android.util.Log.i("SyncLoop", "Firestore-native listeners started for group $groupId")
                } catch (e: Exception) {
                    android.util.Log.e("SyncLoop", "Failed to start listeners", e)
                }

                // One-time migration: push all local data to Firestore native docs
                if (!syncPrefs.getBoolean("migration_native_docs_done", false)) {
                    try {
                        syncStatus = "syncing"
                        syncProgressMessage = "Migrating data..."
                        docSync.pushAllRecords(
                            transactions.toList(),
                            recurringExpenses.toList(),
                            incomeSources.toList(),
                            savingsGoals.toList(),
                            amortizationEntries.toList(),
                            categories.toList(),
                            periodLedger.toList(),
                            sharedSettings
                        )
                        syncPrefs.edit().putBoolean("migration_native_docs_done", true).apply()
                        syncProgressMessage = null
                    } catch (e: Exception) {
                        android.util.Log.e("SyncLoop", "Migration failed", e)
                        syncProgressMessage = null
                    }
                }

                syncStatus = "synced"
                lastSyncTime = "just now"
                var healthCheckCounter = 0

                try {
                    // Periodic loop: push dirty changes + health checks
                    while (true) {
                        delay(5_000) // 5 seconds between checks
                        healthCheckCounter++

                        // Push local changes to Firestore when dirty
                        if (syncPrefs.getBoolean("syncDirty", false)) {
                            try {
                                android.util.Log.i("SyncLoop", "Dirty flag set — pushing all records")
                                docSync.pushAllRecords(
                                    transactions.toList(),
                                    recurringExpenses.toList(),
                                    incomeSources.toList(),
                                    savingsGoals.toList(),
                                    amortizationEntries.toList(),
                                    categories.toList(),
                                    periodLedger.toList(),
                                    sharedSettings
                                )
                                syncPrefs.edit().putBoolean("syncDirty", false).apply()
                                lastSyncTime = "just now"
                            } catch (e: Exception) {
                                android.util.Log.e("SyncLoop", "Dirty push failed", e)
                            }
                        }

                        // Health checks every ~5 minutes (60 * 5s = 300s)
                        if (healthCheckCounter % 60 == 0) {
                            try {
                                // Check group dissolution
                                if (FirestoreService.isGroupDissolved(groupId)) {
                                    GroupManager.leaveGroup(context, localOnly = true)
                                    isSyncConfigured = false
                                    syncGroupId = null
                                    isSyncAdmin = false
                                    syncStatus = "off"
                                    syncDevices = emptyList()
                                    return@LaunchedEffect
                                }
                                // Check device removal
                                if (FirestoreService.isDeviceRemoved(groupId, localDeviceId)) {
                                    GroupManager.leaveGroup(context, localOnly = true)
                                    isSyncConfigured = false
                                    syncGroupId = null
                                    isSyncAdmin = false
                                    syncStatus = "off"
                                    syncDevices = emptyList()
                                    return@LaunchedEffect
                                }
                                // Subscription expiry check
                                val expiry = FirestoreService.getSubscriptionExpiry(groupId)
                                if (expiry > 0L) {
                                    val gracePeriodMs = 7L * 24 * 60 * 60 * 1000
                                    val elapsed = System.currentTimeMillis() - expiry
                                    if (elapsed > gracePeriodMs) {
                                        if (!FirestoreService.isGroupDissolved(groupId)) {
                                            GroupManager.dissolveGroup(context, groupId)
                                        }
                                        isSyncConfigured = false
                                        syncGroupId = null
                                        isSyncAdmin = false
                                        syncStatus = "off"
                                        syncDevices = emptyList()
                                        return@LaunchedEffect
                                    } else if (elapsed > 0) {
                                        syncErrorMessage = strings.sync.subscriptionExpiredNotice
                                        SubscriptionReminderReceiver.scheduleNextReminder(context)
                                    } else {
                                        SubscriptionReminderReceiver.cancelReminder(context)
                                    }
                                }
                                // Admin: post subscription expiry
                                if (isSyncAdmin) {
                                    if (isSubscriber) {
                                        FirestoreService.updateSubscriptionExpiry(groupId, subscriptionExpiry)
                                    }
                                }
                                // Refresh device list
                                syncDevices = GroupManager.getDevices(groupId)
                                isSyncAdmin = GroupManager.isAdmin(context)
                                // Update device metadata — keep appSyncVersion=2
                                // to avoid triggering "update_required" in old
                                // SyncWorker until it's fully replaced.
                                FirestoreService.updateDeviceMetadata(
                                    groupId, localDeviceId,
                                    syncVersion = 0L,
                                    appSyncVersion = 2,
                                    minSyncVersion = 2
                                )
                                // Update group activity timestamp
                                FirestoreService.updateGroupActivity(groupId)
                            } catch (e: Exception) {
                                android.util.Log.w("SyncLoop", "Health check failed", e)
                            }
                        }
                    }
                } finally {
                    docSync.stopListeners()
                    SyncWriteHelper.dispose()
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
                val stamped = txn.copy(
                    deviceId = localDeviceId,
                )
                // If linking to a savings goal, deduct from goal's totalSavedSoFar
                if (stamped.linkedSavingsGoalId != null) {
                    val gIdx = savingsGoals.indexOfFirst { it.id == stamped.linkedSavingsGoalId }
                    if (gIdx >= 0) {
                        val g = savingsGoals[gIdx]
                        savingsGoals[gIdx] = g.copy(
                            totalSavedSoFar = maxOf(0.0, g.totalSavedSoFar - stamped.amount),
                        )
                        saveSavingsGoals()
                    }
                }
                // Guard against duplicate IDs (e.g., double-tap or recomposition replay)
                if (transactions.none { it.id == stamped.id }) {
                    transactions.add(stamped)
                }
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

            // Auto-launch quick start guide if no income sources exist
            // (indicates a brand new user who hasn't set up their budget)
            LaunchedEffect(Unit) {
                if (incomeSources.isEmpty() && !isSyncConfigured &&
                    !prefs.getBoolean("quickStartCompleted", false)) {
                    quickStartStep = QuickStartStep.WELCOME
                }
            }

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
                    val file = java.io.File(BackupManager.getSupportDir(), "simulation_trace.txt")
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
                        val refreshTz = if (isSyncConfigured && sharedSettings.familyTimezone.isNotEmpty())
                            java.time.ZoneId.of(sharedSettings.familyTimezone) else null
                        val currentPeriod = BudgetCalculator.currentPeriodStart(
                            budgetPeriod, resetDayOfWeek, resetDayOfMonth, refreshTz, resetHour
                        )
                        val refreshDate = lastRefreshDate ?: continue
                        val missedPeriods = BudgetCalculator.countPeriodsCompleted(refreshDate, currentPeriod, budgetPeriod)
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
                                    periodLedger.add(
                                        PeriodLedgerEntry(
                                            periodStartDate = periodDate.atStartOfDay(),
                                            appliedAmount = budgetAmount,
                                            deviceId = localDeviceId
                                        )
                                    )
                                }
                            }
                            savePeriodLedger()

                            // Update savings goals totalSavedSoFar for non-paused, non-complete items.
                            // Use the correct date for each catch-up period so periodsLeft
                            // decreases properly (instead of using today for all iterations).
                            // NO lamportClock.tick() — savings goal accrual is deterministic.
                            // Both devices compute the same value from the same data, so
                            // clock advancement would create unnecessary divergence.
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

                            // Update RE set-aside tracking for each catch-up period.
                            // NO lamportClock.tick() — set-aside accrual is deterministic.
                            // Both devices compute the same value from the same RE data.
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
                                            isAccelerated = if (re.isAccelerated) false else re.isAccelerated
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
                                                setAsideSoFar = minOf(re.setAsideSoFar + increment, re.amount)
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

            // ── Reusable state dump builder ──
            fun buildDiagDump(): String {
                val dump = StringBuilder()
                dump.appendLine("=== State Dump ${java.time.LocalDateTime.now()} ===")
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
                dump.appendLine("syncStatus: $syncStatus")
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
                val categoryMap = categories.associateBy { it.id }
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
                val activeTxns = transactions.filter { !it.deleted }
                val periodTxns = if (budgetStartDate != null) activeTxns.filter { !it.date.isBefore(budgetStartDate) } else activeTxns
                for (txn in periodTxns.sortedBy { it.date }) {
                    val budgetAccounted = if (txn.type == TransactionType.EXPENSE) isBudgetAccountedExpense(txn) else false
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
                    dump.appendLine("  ${txn.date} ${txn.type} ${txn.amount} '${txn.source}' dev=${txn.deviceId.take(8)}… ba=$budgetAccounted ${flagDesc}$linkDesc $catDesc")
                }
                // Recompute cash using the ACTUAL BudgetCalculator logic for verification
                val verifyLedger = periodLedger.toList()
                val verifyTxns = activeTransactions
                val verifyRe = activeRecurringExpenses
                val verifyIs = activeIncomeSources
                val verifyCash = BudgetCalculator.recomputeAvailableCash(
                    budgetStartDate ?: java.time.LocalDate.now(),
                    verifyLedger, verifyTxns, verifyRe, incomeMode, verifyIs
                )
                // Breakdown: sum period ledger credits
                val ledgerCredits = if (budgetStartDate != null) {
                    verifyLedger
                        .filter { !it.periodStartDate.toLocalDate().isBefore(budgetStartDate) }
                        .groupBy { it.periodStartDate.toLocalDate() }
                        .values.map { entries -> entries.maxByOrNull { it.periodStartDate } ?: entries.first() }
                        .sumOf { it.appliedAmount }
                } else 0.0
                dump.appendLine("── Cash Verification ──")
                dump.appendLine("Ledger credits (sum of ${verifyLedger.size} entries): $ledgerCredits")
                dump.appendLine("Recomputed cash (BudgetCalculator): $verifyCash")
                dump.appendLine("Stored cash (prefs): $availableCash")
                dump.appendLine("Match: ${kotlin.math.abs(verifyCash - availableCash) < 0.01}")
                dump.appendLine()
                dump.appendLine("── Period Ledger ──")
                for (entry in periodLedger) {
                    dump.appendLine("  ${entry.periodStartDate} applied=${entry.appliedAmount}")
                }
                dump.appendLine("=== End Dump ===")
                return dump.toString()
            }

            /** Write text to a file in Download/BudgeTrak/support/ via MediaStore. */
            fun writeDiagToMediaStore(fileName: String, text: String) {
                // Write directly to support dir (app-created directory, no
                // MediaStore ownership issues on Android 10+).
                try {
                    val file = java.io.File(BackupManager.getSupportDir(), fileName)
                    file.writeText(text)
                } catch (e: Exception) {
                    android.util.Log.w("DiagDump", "Direct write failed for $fileName, trying MediaStore: ${e.message}")
                    // Fallback to MediaStore for files the app didn't create directly
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

            // ── One-time CRDT state dump to Downloads (debug only) ──
            if (com.syncbudget.app.BuildConfig.DEBUG) {
                remember {
                    try {
                        val diagText = buildDiagDump()
                        writeDiagToMediaStore("sync_diag.txt", diagText)
                        // Also write device-named copy
                        val devName = sanitizeDeviceName(com.syncbudget.app.data.sync.GroupManager.getDeviceName(context))
                        if (devName.isNotEmpty()) {
                            writeDiagToMediaStore("sync_diag_${devName}.txt", diagText)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DiagDump", "Diag write failed: ${e.message}")
                    }
                    true
                }
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
                        syncStatus = "syncing"
                        try {
                            val docSync = FirestoreDocSync(context, gId, localDeviceId, key)
                            docSync.pushAllRecords(
                                transactions.toList(),
                                recurringExpenses.toList(),
                                incomeSources.toList(),
                                savingsGoals.toList(),
                                amortizationEntries.toList(),
                                categories.toList(),
                                periodLedger.toList(),
                                sharedSettings
                            )
                            syncStatus = "synced"
                            syncErrorMessage = null
                            syncProgressMessage = null
                            lastSyncTime = "just now"
                            try {
                                syncDevices = GroupManager.getDevices(gId)
                                isSyncAdmin = GroupManager.isAdmin(context)
                            } catch (_: Exception) {}
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
                        showUpdateBanner = syncErrorMessage == strings.sync.updateRequiredNotice,
                        staleDays = staleDays,
                        syncDevices = syncDevices,
                        localDeviceId = localDeviceId,
                        syncRepairAlert = syncRepairAlert,
                        onDismissRepairAlert = {
                            syncRepairAlert = false
                            prefs.edit().putBoolean("syncRepairAlert", false).apply()
                        },
                        onSyncNow = doSyncNow,
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
                                                contributionPerPeriod = newContribution,
                                            )
                                        } else {
                                            goal.copy(
                                                totalSavedSoFar = goal.totalSavedSoFar + capped,
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
                                // reflects the immediate cash outflow. Categorized as "supercharge"
                                // so they are visible via the category but hidden from the category picker.
                                val superchargeCatId = categories.find { it.tag == "supercharge" }?.id
                                val currentIds = transactions.map { it.id }.toSet()
                                for ((goalName, depositAmount) in deposits) {
                                    val txn = Transaction(
                                        id = generateTransactionId(currentIds + transactions.map { it.id }.toSet()),
                                        source = "Savings: $goalName",
                                        amount = depositAmount,
                                        date = LocalDate.now(),
                                        type = TransactionType.EXPENSE,
                                        categoryAmounts = if (superchargeCatId != null) listOf(CategoryAmount(superchargeCatId, depositAmount)) else emptyList()
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
                        onNavigateToQuickStart = {
                            quickStartStep = QuickStartStep.WELCOME
                            currentScreen = "main"
                        },
                        matchDays = matchDays,
                        onMatchDaysChange = {
                            matchDays = it; prefs.edit().putInt("matchDays", it).apply()
                            if (isSyncConfigured) {
                                sharedSettings = sharedSettings.copy(matchDays = it, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        matchPercent = matchPercent,
                        onMatchPercentChange = {
                            matchPercent = it; prefs.edit().putString("matchPercent", it.toString()).apply()
                            if (isSyncConfigured) {
                                sharedSettings = sharedSettings.copy(matchPercent = it, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        matchDollar = matchDollar,
                        onMatchDollarChange = {
                            matchDollar = it; prefs.edit().putInt("matchDollar", it).apply()
                            if (isSyncConfigured) {
                                sharedSettings = sharedSettings.copy(matchDollar = it, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        matchChars = matchChars,
                        onMatchCharsChange = {
                            matchChars = it; prefs.edit().putInt("matchChars", it).apply()
                            if (isSyncConfigured) {
                                sharedSettings = sharedSettings.copy(matchChars = it, lastChangedBy = localDeviceId)
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
                                sharedSettings = sharedSettings.copy(weekStartSunday = it, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        onCurrencyChange = {
                            currencySymbol = it
                            prefs.edit().putString("currencySymbol", it).apply()
                            if (isSyncConfigured) {
                                sharedSettings = sharedSettings.copy(currency = it, lastChangedBy = localDeviceId)
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
                        isPaidUser = isPaidUser || isSubscriber,
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
                            categories.add(cat.copy(
                                deviceId = localDeviceId,
                            ))
                            saveCategories()
                        },
                        onUpdateCategory = { updated ->
                            val idx = categories.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = categories[idx]
                                // Don't overwrite deviceId — it tracks the original creator.
                                categories[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deleted = old.deleted,
                                )
                                saveCategories()
                            }
                        },
                        onDeleteCategory = { cat ->
                            val idx = categories.indexOfFirst { it.id == cat.id }
                            if (idx >= 0) {
                                categories[idx] = categories[idx].copy(deleted = true)
                                saveCategories()
                            }
                        },
                        onToggleCharted = { cat ->
                            val idx = categories.indexOfFirst { it.id == cat.id }
                            if (idx >= 0) {
                                categories[idx] = categories[idx].copy(
                                    charted = !categories[idx].charted,
                                )
                                saveCategories()
                            }
                        },
                        onToggleWidgetVisible = { cat ->
                            val idx = categories.indexOfFirst { it.id == cat.id }
                            if (idx >= 0) {
                                categories[idx] = categories[idx].copy(
                                    widgetVisible = !categories[idx].widgetVisible,
                                )
                                saveCategories()
                            }
                        },
                        onReassignCategory = { fromId, toId ->
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
                                    )
                                }
                            }
                            saveTransactions()
                        },
                        receiptPruneAgeDays = sharedSettings.receiptPruneAgeDays,
                        onReceiptPruneChange = { days ->
                            sharedSettings = sharedSettings.copy(
                                receiptPruneAgeDays = days,
                                lastChangedBy = localDeviceId
                            )
                            SharedSettingsRepository.save(context, sharedSettings)
                        },
                        receiptCacheSize = remember(isPaidUser) {
                            com.syncbudget.app.data.sync.ReceiptManager.getTotalStorageBytes(context)
                        },
                        backupsEnabled = backupsEnabled,
                        onBackupsEnabledChange = { enabled ->
                            if (enabled) {
                                showBackupPasswordDialog = true
                            } else {
                                showDisableBackupDialog = true
                            }
                        },
                        backupFrequencyWeeks = backupFrequencyWeeks,
                        onBackupFrequencyChange = { weeks ->
                            backupFrequencyWeeks = weeks
                            backupPrefs.edit().putInt("backup_frequency_weeks", weeks).apply()
                        },
                        backupRetention = backupRetention,
                        onBackupRetentionChange = { ret ->
                            backupRetention = ret
                            backupPrefs.edit().putInt("backup_retention", ret).apply()
                        },
                        lastBackupDate = lastBackupDate,
                        nextBackupDate = com.syncbudget.app.data.BackupManager.getNextBackupDate(context),
                        onBackupNow = {
                            val pwd = com.syncbudget.app.data.BackupManager.getPassword(context)
                            if (pwd != null) {
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    val result = com.syncbudget.app.data.BackupManager.performBackup(context, pwd)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        lastBackupDate = backupPrefs.getString("last_backup_date", null)
                                    }
                                }
                            }
                        },
                        onRestoreBackup = { showRestoreDialog = true },
                        onSavePhotos = { showSavePhotosDialog = true },
                        onDumpDebug = {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val devName = com.syncbudget.app.data.sync.GroupManager.getDeviceName(context)
                                    val sanitized = sanitizeDeviceName(devName)
                                    val supportDir = BackupManager.getSupportDir()

                                    // 1. Build fresh diag dump and save locally
                                    val diagText = buildDiagDump()
                                    writeDiagToMediaStore("sync_diag.txt", diagText)
                                    if (sanitized.isNotEmpty()) {
                                        writeDiagToMediaStore("sync_diag_${sanitized}.txt", diagText)
                                    }

                                    // 2. Read sync_log and save with device name
                                    val syncLogFile = java.io.File(supportDir, "sync_log.txt")
                                    val syncLogText = if (syncLogFile.exists()) syncLogFile.readText() else ""
                                    if (sanitized.isNotEmpty() && syncLogText.isNotEmpty()) {
                                        writeDiagToMediaStore("sync_log_${sanitized}.txt", syncLogText)
                                    }

                                    // 2b. Capture logcat to file (app can read its own logs) — debug only
                                    if (com.syncbudget.app.BuildConfig.DEBUG) {
                                        try {
                                            val logcatProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "1000"))
                                            val logcatText = logcatProcess.inputStream.bufferedReader().readText()
                                            logcatProcess.waitFor()
                                            writeDiagToMediaStore("logcat_${sanitized}.txt", logcatText)
                                        } catch (e: Exception) {
                                            android.util.Log.w("DumpDebug", "Logcat capture failed: ${e.message}")
                                        }
                                    }

                                    val gId = syncGroupId
                                    if (gId != null) {
                                        // 3. Upload own files
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            toastState.show("Uploading local debug files\u2026")
                                        }
                                        // Append extra debug files to the diag upload
                                        val extraDebug = StringBuilder(diagText)
                                        for (extraName in listOf("clock_dump.txt", "fcm_debug.txt")) {
                                            val f = java.io.File(supportDir, extraName)
                                            if (f.exists()) {
                                                extraDebug.appendLine("\n=== $extraName ===")
                                                extraDebug.appendLine(f.readText())
                                            }
                                        }
                                        // Append logcat if captured
                                        try {
                                            val lp = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "500"))
                                            val lt = lp.inputStream.bufferedReader().readText()
                                            lp.waitFor()
                                            if (lt.isNotEmpty()) {
                                                extraDebug.appendLine("\n=== logcat (last 500) ===")
                                                extraDebug.appendLine(lt)
                                            }
                                        } catch (_: Exception) {}
                                        val debugKey = com.syncbudget.app.data.sync.GroupManager.getEncryptionKey(context)
                                        FirestoreService.uploadDebugFiles(gId, localDeviceId, devName, syncLogText, extraDebug.toString(), debugKey)

                                        // 4. Request all devices upload fresh files
                                        FirestoreService.requestDebugDump(gId)

                                        // 4b. Send FCM push to wake up remote devices
                                        try {
                                            val fcmTokens = FirestoreService.getFcmTokens(gId, localDeviceId)
                                            val supportDir = BackupManager.getSupportDir()
                                            val debugLog = java.io.File(supportDir, "fcm_debug.txt")
                                            debugLog.appendText("[${java.time.LocalDateTime.now()}] FCM tokens found: ${fcmTokens.size}\n")
                                            for (token in fcmTokens) {
                                                debugLog.appendText("  token: ${token.take(20)}...\n")
                                                val sent = FcmSender.sendDebugRequest(context, token)
                                                debugLog.appendText("  result: $sent, error: ${FcmSender.lastError}\n")
                                            }
                                            if (fcmTokens.isEmpty()) {
                                                debugLog.appendText("  No FCM tokens found for remote devices\n")
                                            }
                                        } catch (e: Exception) {
                                            val supportDir = BackupManager.getSupportDir()
                                            java.io.File(supportDir, "fcm_debug.txt")
                                                .appendText("[${java.time.LocalDateTime.now()}] FCM exception: ${e.javaClass.simpleName}: ${e.message}\n")
                                        }

                                        // 5. Poll for remote files (wait up to 90s for other devices)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            toastState.show("Waiting for remote device\u2026")
                                        }
                                        val requestTime = System.currentTimeMillis()
                                        var gotFreshRemote = false
                                        for (attempt in 1..18) { // 18 × 5s = 90s max
                                            kotlinx.coroutines.delay(5_000)
                                            val remoteFiles = FirestoreService.downloadDebugFiles(gId, localDeviceId, debugKey)
                                            // Check if any remote file was updated AFTER our request
                                            val fresh = remoteFiles.filter { it.updatedAt > requestTime - 5000 }
                                            if (fresh.isNotEmpty()) {
                                                for (remote in remoteFiles) {
                                                    val rName = sanitizeDeviceName(remote.deviceName)
                                                    if (remote.syncLog.isNotEmpty()) writeDiagToMediaStore("sync_log_${rName}.txt", remote.syncLog)
                                                    if (remote.syncDiag.isNotEmpty()) writeDiagToMediaStore("sync_diag_${rName}.txt", remote.syncDiag)
                                                }
                                                gotFreshRemote = true
                                                break
                                            }
                                        }
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (gotFreshRemote) {
                                                toastState.show("Debug files synced")
                                            } else {
                                                toastState.show("Local files saved. Remote device didn\u2019t respond in 90s.")
                                            }
                                        }
                                    } else {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            toastState.show("Debug files saved locally")
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("DumpDebug", "Debug sync failed: ${e.message}", e)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        toastState.show("Debug sync failed: ${e.message?.take(60)}")
                                    }
                                }
                            }
                        },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "settings_help" }
                    )
                    "transactions" -> TransactionsScreen(
                        transactions = activeTransactions,
                        currencySymbol = currencySymbol,
                        dateFormatPattern = dateFormatPattern,
                        categories = activeCategories,
                        isPaidUser = isPaidUser || isSubscriber,
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
                                // Don't overwrite deviceId — it tracks the original creator.
                                transactions[index] = updated.copy(
                                    deviceId = prev.deviceId,
                                    deleted = prev.deleted,
                                    // If user manually unlinks, clear remembered amounts (linked-in-error → full amount applies)
                                    amortizationAppliedAmount = if (prev.linkedAmortizationEntryId != null && updated.linkedAmortizationEntryId == null) 0.0 else prev.amortizationAppliedAmount,
                                    linkedRecurringExpenseAmount = if (prev.linkedRecurringExpenseId != null && updated.linkedRecurringExpenseId == null) 0.0 else prev.linkedRecurringExpenseAmount,
                                    linkedIncomeSourceAmount = if (prev.linkedIncomeSourceId != null && updated.linkedIncomeSourceId == null) 0.0 else prev.linkedIncomeSourceAmount,
                                    // Manual unlink from savings goal: clear remembered amount, restore funds to goal
                                    linkedSavingsGoalAmount = if (prev.linkedSavingsGoalId != null && updated.linkedSavingsGoalId == null) 0.0
                                        else if (updated.linkedSavingsGoalId != null && prev.linkedSavingsGoalId == null) updated.linkedSavingsGoalAmount
                                        else prev.linkedSavingsGoalAmount,
                                )
                                // Handle savings goal link/unlink effects
                                val wasLinkedToGoal = prev.linkedSavingsGoalId
                                val nowLinkedToGoal = updated.linkedSavingsGoalId
                                if (wasLinkedToGoal != null && nowLinkedToGoal == null) {
                                    // Manual unlink: restore funds to goal
                                    val gIdx = savingsGoals.indexOfFirst { it.id == wasLinkedToGoal }
                                    if (gIdx >= 0) {
                                        val g = savingsGoals[gIdx]
                                        savingsGoals[gIdx] = g.copy(
                                            totalSavedSoFar = g.totalSavedSoFar + prev.linkedSavingsGoalAmount,
                                        )
                                        saveSavingsGoals()
                                    }
                                } else if (wasLinkedToGoal == null && nowLinkedToGoal != null) {
                                    // Newly linked: deduct from goal
                                    val gIdx = savingsGoals.indexOfFirst { it.id == nowLinkedToGoal }
                                    if (gIdx >= 0) {
                                        val g = savingsGoals[gIdx]
                                        savingsGoals[gIdx] = g.copy(
                                            totalSavedSoFar = maxOf(0.0, g.totalSavedSoFar - updated.amount),
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
                                        savingsGoals[gIdx] = g.copy(
                                            totalSavedSoFar = g.totalSavedSoFar + t.linkedSavingsGoalAmount,
                                        )
                                        saveSavingsGoals()
                                    }
                                }
                                transactions[idx] = t.copy(
                                    deleted = true
                                )
                                saveTransactions()
                            }
                            recomputeCash()
                        },
                        onDeleteTransactions = { ids ->
                            var goalsChanged = false
                            transactions.forEachIndexed { index, txn ->
                                if (txn.id in ids && !txn.deleted) {
                                    // Restore savings goal funds for linked transactions
                                    if (txn.linkedSavingsGoalId != null && txn.linkedSavingsGoalAmount > 0.0) {
                                        val gIdx = savingsGoals.indexOfFirst { it.id == txn.linkedSavingsGoalId }
                                        if (gIdx >= 0) {
                                            val g = savingsGoals[gIdx]
                                            savingsGoals[gIdx] = g.copy(
                                                totalSavedSoFar = g.totalSavedSoFar + txn.linkedSavingsGoalAmount,
                                            )
                                            goalsChanged = true
                                        }
                                    }
                                    transactions[index] = txn.copy(
                                        deleted = true,
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
                                // Clear sync identity on all records
                                transactions.forEachIndexed { i, t ->
                                    transactions[i] = t.copy(deviceId = "")
                                }
                                saveTransactions()
                                categories.forEachIndexed { i, c ->
                                    categories[i] = c.copy(deviceId = "")
                                }
                                saveCategories()
                                recurringExpenses.forEachIndexed { i, r ->
                                    recurringExpenses[i] = r.copy(deviceId = "")
                                }
                                saveRecurringExpenses()
                                incomeSources.forEachIndexed { i, s ->
                                    incomeSources[i] = s.copy(deviceId = "")
                                }
                                saveIncomeSources()
                                savingsGoals.forEachIndexed { i, g ->
                                    savingsGoals[i] = g.copy(deviceId = "")
                                }
                                saveSavingsGoals()
                                amortizationEntries.forEachIndexed { i, e ->
                                    amortizationEntries[i] = e.copy(deviceId = "")
                                }
                                saveAmortizationEntries()

                                // Clear sync prefs for new group
                                context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
                                    .edit().putBoolean("pushClockFixApplied", true).apply()

                                val oldGroupId = syncGroupId
                                if (oldGroupId != null) {
                                    coroutineScope.launch {
                                        try {
                                            GroupManager.dissolveGroup(context, oldGroupId)
                                        } catch (_: Exception) {}
                                        val newGroup = GroupManager.createGroup(context)
                                        // Initialize group doc BEFORE registering device
                                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        db.collection("groups").document(newGroup.groupId)
                                            .set(mapOf("nextDeltaVersion" to 0L, "createdAt" to System.currentTimeMillis(), "lastActivity" to System.currentTimeMillis()))
                                            .await()
                                        // Register admin device
                                        FirestoreService.registerDevice(
                                            newGroup.groupId, localDeviceId,
                                            GroupManager.getDeviceName(context), isAdmin = true
                                        )
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
                                incomeSources[idx] = incomeSources[idx].copy(
                                    amount = newAmount,
                                )
                                saveIncomeSources()
                            }
                        },
                        onAddAmortization = { entry ->
                            amortizationEntries.add(entry.copy(
                                deviceId = localDeviceId,
                            ))
                            saveAmortizationEntries()
                        },
                        onDeleteAmortization = { entry ->
                            val idx = amortizationEntries.indexOfFirst { it.id == entry.id }
                            if (idx >= 0) {
                                amortizationEntries[idx] = amortizationEntries[idx].copy(
                                    deleted = true, 
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
                            savingsGoals.add(goal.copy(
                                deviceId = localDeviceId,
                            ))
                            saveSavingsGoals()
                        },
                        onUpdateGoal = { updated ->
                            val idx = savingsGoals.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = savingsGoals[idx]
                                savingsGoals[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deleted = old.deleted,
                                )
                                saveSavingsGoals()
                            }
                        },
                        onDeleteGoal = { goal ->
                            val idx = savingsGoals.indexOfFirst { it.id == goal.id }
                            if (idx >= 0) {
                                savingsGoals[idx] = savingsGoals[idx].copy(deleted = true)
                                saveSavingsGoals()
                                // Unlink any transactions linked to this goal.
                                // PRESERVE linkedSavingsGoalAmount — those expenses were
                                // already paid from savings.  Clearing it would cause
                                // availableCash to drop (double-counting the expense).
                                // Manual unlink (linked-in-error) clears amount separately.
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedSavingsGoalId == goal.id) {
                                        transactions[i] = txn.copy(
                                            linkedSavingsGoalId = null,
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
                            amortizationEntries.add(entry.copy(
                                deviceId = localDeviceId,
                            ))
                            saveAmortizationEntries()
                        },
                        onUpdateEntry = { updated ->
                            val idx = amortizationEntries.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = amortizationEntries[idx]
                                amortizationEntries[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deleted = old.deleted,
                                )
                                saveAmortizationEntries()
                            }
                        },
                        onDeleteEntry = { entry ->
                            val idx = amortizationEntries.indexOfFirst { it.id == entry.id }
                            if (idx >= 0) {
                                // Calculate how much has already been amortized
                                val today = java.time.LocalDate.now()
                                val elapsed = when (budgetPeriod) {
                                    BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(entry.startDate, today).toInt()
                                    BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(entry.startDate, today).toInt()
                                    BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(entry.startDate, today).toInt()
                                }.coerceIn(0, entry.totalPeriods)
                                val perPeriod = BudgetCalculator.roundCents(entry.amount / entry.totalPeriods.toDouble())
                                val appliedAmount = BudgetCalculator.roundCents(perPeriod * elapsed)

                                amortizationEntries[idx] = amortizationEntries[idx].copy(deleted = true)
                                saveAmortizationEntries()
                                // Unlink transactions and record the already-applied portion
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedAmortizationEntryId == entry.id) {
                                        transactions[i] = txn.copy(
                                            linkedAmortizationEntryId = null,
                                            amortizationAppliedAmount = appliedAmount,
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
                            recurringExpenses.add(expense.copy(
                                deviceId = localDeviceId,
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
                                recurringExpenses[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deleted = old.deleted,
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
                                recurringExpenses[idx] = recurringExpenses[idx].copy(deleted = true)
                                saveRecurringExpenses()
                                // Unlink any transactions linked to this expense
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedRecurringExpenseId == expense.id) {
                                        transactions[i] = txn.copy(
                                            linkedRecurringExpenseId = null,
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
                            incomeSources.add(src.copy(
                                deviceId = localDeviceId,
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
                                incomeSources[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deleted = old.deleted,
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
                                incomeSources[idx] = incomeSources[idx].copy(deleted = true)
                                saveIncomeSources()
                                // Unlink any transactions linked to this income source
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedIncomeSourceId == src.id) {
                                        transactions[i] = txn.copy(
                                            linkedIncomeSourceId = null,
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
                                sharedSettings = sharedSettings.copy(budgetPeriod = it.name, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        resetHour = resetHour,
                        onResetHourChange = {
                            resetHour = it; prefs.edit().putInt("resetHour", it).apply()
                            if (isSyncConfigured) {
                                sharedSettings = sharedSettings.copy(resetHour = it, lastChangedBy = localDeviceId)
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
                                sharedSettings = sharedSettings.copy(
                                    resetDayOfWeek = it,
                                    weekStartSunday = newWeekStart,
                                    lastChangedBy = localDeviceId
                                )
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        resetDayOfMonth = resetDayOfMonth,
                        onResetDayOfMonthChange = {
                            resetDayOfMonth = it; prefs.edit().putInt("resetDayOfMonth", it).apply()
                            if (isSyncConfigured) {
                                sharedSettings = sharedSettings.copy(resetDayOfMonth = it, lastChangedBy = localDeviceId)
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
                                    sharedSettings = sharedSettings.copy(incomeMode = "ACTUAL", lastChangedBy = localDeviceId)
                                }
                            }
                            if (isSyncConfigured) {
                                sharedSettings = sharedSettings.copy(isManualBudgetEnabled = enabled, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                        },
                        onManualBudgetAmountChange = { amount ->
                            manualBudgetAmount = amount
                            prefs.edit().putString("manualBudgetAmount", amount.toString()).apply()
                            if (isSyncConfigured) {
                                sharedSettings = sharedSettings.copy(manualBudgetAmount = amount, lastChangedBy = localDeviceId)
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
                            val entryDate = budgetStartDate!!.atStartOfDay()
                            val alreadyRecorded = periodLedger.any {
                                it.periodStartDate.toLocalDate() == budgetStartDate
                            }
                            if (!alreadyRecorded) {
                                periodLedger.add(
                                    PeriodLedgerEntry(
                                        periodStartDate = entryDate,
                                        appliedAmount = budgetAmount,
                                        deviceId = localDeviceId
                                    )
                                )
                            }
                            savePeriodLedger()
                            if (isSyncConfigured) {
                                sharedSettings = sharedSettings.copy(
                                    budgetStartDate = budgetStartDate?.toString(),
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
                                sharedSettings = sharedSettings.copy(incomeMode = modeName, lastChangedBy = localDeviceId)
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
                            sharedSettings = sharedSettings.copy(
                                familyTimezone = tz,
                                lastChangedBy = localDeviceId
                            )
                            SharedSettingsRepository.save(context, sharedSettings)
                        },
                        showAttribution = sharedSettings.showAttribution,
                        onShowAttributionChange = { enabled ->
                            sharedSettings = sharedSettings.copy(
                                showAttribution = enabled,
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
                            val json = org.json.JSONObject(roster).toString()
                            sharedSettings = sharedSettings.copy(
                                deviceRoster = json,
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
                                sharedSettings = sharedSettings.copy(
                                    deviceRoster = org.json.JSONObject(pruned).toString(),
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

                                    // Initialize group doc BEFORE registering device
                                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    db.collection("groups").document(info.groupId)
                                        .set(mapOf("nextDeltaVersion" to 0L, "createdAt" to System.currentTimeMillis(), "lastActivity" to System.currentTimeMillis()))
                                        .await()
                                    // Register this device as admin
                                    FirestoreService.registerDevice(
                                        info.groupId,
                                        localDeviceId,
                                        nickname,
                                        isAdmin = true
                                    )
                                    // Initialize SharedSettings from current app_prefs
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
                                    )
                                    SharedSettingsRepository.save(context, sharedSettings)

                                    // Stamp all existing data with deviceId so they push on first sync.
                                    // Only stamp records that have no deviceId (empty).
                                    transactions.forEachIndexed { i, t ->
                                        if (t.deviceId.isEmpty()) {
                                            transactions[i] = t.copy(deviceId = localDeviceId)
                                        }
                                    }
                                    saveTransactions()
                                    categories.forEachIndexed { i, c ->
                                        if (c.deviceId.isEmpty()) {
                                            categories[i] = c.copy(deviceId = localDeviceId)
                                        }
                                    }
                                    saveCategories()
                                    savingsGoals.forEachIndexed { i, g ->
                                        if (g.deviceId.isEmpty()) {
                                            savingsGoals[i] = g.copy(deviceId = localDeviceId)
                                        }
                                    }
                                    saveSavingsGoals()
                                    amortizationEntries.forEachIndexed { i, e ->
                                        if (e.deviceId.isEmpty()) {
                                            amortizationEntries[i] = e.copy(deviceId = localDeviceId)
                                        }
                                    }
                                    saveAmortizationEntries()
                                    recurringExpenses.forEachIndexed { i, r ->
                                        if (r.deviceId.isEmpty()) {
                                            recurringExpenses[i] = r.copy(deviceId = localDeviceId)
                                        }
                                    }
                                    saveRecurringExpenses()
                                    incomeSources.forEachIndexed { i, s ->
                                        if (s.deviceId.isEmpty()) {
                                            incomeSources[i] = s.copy(deviceId = localDeviceId)
                                        }
                                    }
                                    saveIncomeSources()

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

                                        // Stamp existing records with deviceId so they push on first sync.
                                        categories.forEachIndexed { i, c ->
                                            if (c.deviceId.isEmpty()) {
                                                categories[i] = c.copy(deviceId = localDeviceId)
                                            }
                                        }
                                        saveCategories()
                                        transactions.forEachIndexed { i, t ->
                                            if (t.deviceId.isEmpty()) {
                                                transactions[i] = t.copy(deviceId = localDeviceId)
                                            }
                                        }
                                        saveTransactions()
                                        recurringExpenses.forEachIndexed { i, r ->
                                            if (r.deviceId.isEmpty()) {
                                                recurringExpenses[i] = r.copy(deviceId = localDeviceId)
                                            }
                                        }
                                        saveRecurringExpenses()
                                        incomeSources.forEachIndexed { i, s ->
                                            if (s.deviceId.isEmpty()) {
                                                incomeSources[i] = s.copy(deviceId = localDeviceId)
                                            }
                                        }
                                        saveIncomeSources()
                                        savingsGoals.forEachIndexed { i, g ->
                                            if (g.deviceId.isEmpty()) {
                                                savingsGoals[i] = g.copy(deviceId = localDeviceId)
                                            }
                                        }
                                        saveSavingsGoals()
                                        amortizationEntries.forEachIndexed { i, e ->
                                            if (e.deviceId.isEmpty()) {
                                                amortizationEntries[i] = e.copy(deviceId = localDeviceId)
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
                                    .remove("lastSuccessfulSync")
                                    .apply()
                                isSyncConfigured = false
                                syncGroupId = null
                                isSyncAdmin = false
                                syncStatus = "off"
                                lastSyncTime = null
                                syncDevices = emptyList()
                                pendingAdminClaim = null
                                syncErrorMessage = null
                            }
                        },
                        onDissolveGroup = {
                            val gId = syncGroupId
                            if (gId != null) {
                                syncStatus = "syncing"
                                coroutineScope.launch {
                                    try {
                                        android.util.Log.d("Sync", "Dissolving group $gId")
                                        GroupManager.dissolveGroup(context, gId) { msg ->
                                            syncProgressMessage = msg
                                        }
                                        android.util.Log.d("Sync", "Group dissolved successfully")
                                        syncPrefs.edit()
                                            .remove("catIdRemap")
                                            .remove("lastSuccessfulSync")
                                            .apply()
                                        isSyncConfigured = false
                                        syncGroupId = null
                                        isSyncAdmin = false
                                        syncStatus = "off"
                                        lastSyncTime = null
                                        syncDevices = emptyList()
                                        pendingAdminClaim = null
                                        syncErrorMessage = null
                                        syncProgressMessage = null
                                    } catch (e: Exception) {
                                        android.util.Log.e("Sync", "Dissolve failed", e)
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
                                        // Push all records so the new device can bootstrap from Firestore
                                        val docSync = FirestoreDocSync(context, gId, localDeviceId, key)
                                        docSync.pushAllRecords(
                                            transactions.toList(), recurringExpenses.toList(),
                                            incomeSources.toList(), savingsGoals.toList(),
                                            amortizationEntries.toList(), categories.toList(),
                                            periodLedger.toList(), sharedSettings
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
                                    sharedSettings = sharedSettings.copy(
                                        deviceRoster = org.json.JSONObject(currentRoster as Map<*, *>).toString(),
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
                        isPaidUser = isPaidUser || isSubscriber,
                        onDismiss = { dashboardShowAddIncome = false },
                        onSave = { txn ->
                            runMatchingChain(txn)
                            dashboardShowAddIncome = false
                        },
                        onAddAmortization = { entry ->
                            amortizationEntries.add(entry.copy(
                                deviceId = localDeviceId,
                            ))
                            saveAmortizationEntries()
                        },
                        onDeleteAmortization = { entry ->
                            val idx = amortizationEntries.indexOfFirst { it.id == entry.id }
                            if (idx >= 0) {
                                amortizationEntries[idx] = amortizationEntries[idx].copy(
                                    deleted = true
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
                        isPaidUser = isPaidUser || isSubscriber,
                        onDismiss = { dashboardShowAddExpense = false },
                        onSave = { txn ->
                            runMatchingChain(txn)
                            dashboardShowAddExpense = false
                        },
                        onAddAmortization = { entry ->
                            amortizationEntries.add(entry.copy(
                                deviceId = localDeviceId,
                            ))
                            saveAmortizationEntries()
                        },
                        onDeleteAmortization = { entry ->
                            val idx = amortizationEntries.indexOfFirst { it.id == entry.id }
                            if (idx >= 0) {
                                amortizationEntries[idx] = amortizationEntries[idx].copy(
                                    deleted = true
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
                                    deleted = true
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
                                    incomeSources[idx] = incomeSources[idx].copy(
                                        amount = baseTxn.amount,
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
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedRecurringExpenseId == updated.id && !txn.deleted) {
                                        transactions[i] = txn.copy(
                                            linkedRecurringExpenseAmount = updated.amount,
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
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedIncomeSourceId == updated.id && !txn.deleted) {
                                        transactions[i] = txn.copy(
                                            linkedIncomeSourceAmount = updated.amount,
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

                // Backup password dialog
                if (showBackupPasswordDialog) {
                    var pwd by remember { mutableStateOf("") }
                    var pwdConfirm by remember { mutableStateOf("") }
                    var pwdError by remember { mutableStateOf<String?>(null) }
                    AdAwareAlertDialog(
                        onDismissRequest = { showBackupPasswordDialog = false },
                        title = { Text(strings.settings.setBackupPassword) },
                        style = DialogStyle.WARNING,
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    strings.settings.backupPasswordWarning,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                OutlinedTextField(
                                    value = pwd, onValueChange = { pwd = it; pwdError = null },
                                    label = { Text(strings.settings.passwordLabel) }, singleLine = true,
                                    visualTransformation = PasswordVisualTransformation()
                                )
                                OutlinedTextField(
                                    value = pwdConfirm, onValueChange = { pwdConfirm = it; pwdError = null },
                                    label = { Text(strings.settings.confirmPasswordLabel) }, singleLine = true,
                                    visualTransformation = PasswordVisualTransformation()
                                )
                                if (pwdError != null) {
                                    Text(pwdError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        confirmButton = {
                            DialogWarningButton(onClick = {
                                when {
                                    pwd.length < 8 -> pwdError = strings.settings.passwordTooShort
                                    pwd != pwdConfirm -> pwdError = strings.settings.passwordMismatch
                                    else -> {
                                        com.syncbudget.app.data.BackupManager.savePassword(context, pwd.toCharArray())
                                        backupsEnabled = true
                                        backupPrefs.edit().putBoolean("backups_enabled", true).apply()
                                        showBackupPasswordDialog = false
                                    }
                                }
                            }) { Text(strings.settings.enableBackups) }
                        },
                        dismissButton = {
                            DialogSecondaryButton(onClick = { showBackupPasswordDialog = false }) {
                                Text(strings.common.cancel)
                            }
                        }
                    )
                }

                // Disable backup confirmation dialog
                if (showDisableBackupDialog) {
                    var confirmDelete by remember { mutableStateOf(false) }
                    AdAwareAlertDialog(
                        onDismissRequest = { showDisableBackupDialog = false; confirmDelete = false },
                        title = { Text(strings.settings.disableBackups) },
                        style = if (confirmDelete) DialogStyle.DANGER else DialogStyle.DEFAULT,
                        text = {
                            if (!confirmDelete) {
                                Text(strings.settings.keepOrDeletePrompt)
                            } else {
                                Text(
                                    strings.settings.deleteAllConfirmMsg,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        confirmButton = {
                            if (!confirmDelete) {
                                DialogDangerButton(onClick = { confirmDelete = true }) {
                                    Text(strings.settings.deleteAllBtn)
                                }
                            } else {
                                DialogDangerButton(onClick = {
                                    com.syncbudget.app.data.BackupManager.deleteAllBackups()
                                    backupsEnabled = false
                                    backupPrefs.edit().putBoolean("backups_enabled", false).apply()
                                    showDisableBackupDialog = false; confirmDelete = false
                                }) { Text(strings.settings.confirmDeleteBtn) }
                            }
                        },
                        dismissButton = {
                            DialogSecondaryButton(onClick = {
                                if (confirmDelete) { confirmDelete = false } else {
                                    backupsEnabled = false
                                    backupPrefs.edit().putBoolean("backups_enabled", false).apply()
                                    showDisableBackupDialog = false
                                }
                            }) { Text(if (confirmDelete) strings.common.back else strings.settings.keepFilesBtn) }
                        }
                    )
                }

                // Save Photos dialog
                if (showSavePhotosDialog) {
                    AdAwareAlertDialog(
                        onDismissRequest = { showSavePhotosDialog = false },
                        title = { Text("Save Photos") },
                        style = DialogStyle.DEFAULT,
                        text = {
                            Text("Photos are already backed up in encrypted backups if Automatic Backups is enabled below. This will save unencrypted copies of all receipt photos to Download/BudgeTrak/photos/ on your device if you need them for other purposes.")
                        },
                        confirmButton = {
                            DialogPrimaryButton(onClick = {
                                showSavePhotosDialog = false
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    try {
                                        val photosDir = java.io.File(com.syncbudget.app.data.BackupManager.getBudgetrakDir(), "photos")
                                        photosDir.mkdirs()
                                        val receiptDir = java.io.File(context.filesDir, "receipts")
                                        val files = receiptDir.listFiles() ?: emptyArray()
                                        var count = 0
                                        for (f in files) {
                                            f.copyTo(java.io.File(photosDir, f.name), overwrite = true)
                                            count++
                                        }
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            toastState.show("Saved $count photos to Download/BudgeTrak/photos/")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("SavePhotos", "Failed: ${e.message}")
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            toastState.show("Failed to save photos: ${e.message}")
                                        }
                                    }
                                }
                            }) { Text(strings.common.save) }
                        },
                        dismissButton = {
                            DialogSecondaryButton(onClick = { showSavePhotosDialog = false }) {
                                Text(strings.common.cancel)
                            }
                        }
                    )
                }

                // Restore backup dialog
                if (showRestoreDialog) {
                    val availableBackups = remember { com.syncbudget.app.data.BackupManager.listAvailableBackups() }
                    var selectedBackup by remember { mutableStateOf<com.syncbudget.app.data.BackupManager.BackupEntry?>(null) }
                    var restorePassword by remember { mutableStateOf("") }
                    var restoreError by remember { mutableStateOf<String?>(null) }
                    var restoring by remember { mutableStateOf(false) }
                    val restoreScrollState = rememberScrollState()

                    AdAwareDialog(onDismissRequest = { if (!restoring) showRestoreDialog = false }) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.92f).imePadding(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            Column {
                                DialogHeader(strings.settings.restoreBackup)

                                Column(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .verticalScroll(restoreScrollState)
                                        .padding(horizontal = 20.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (availableBackups.isEmpty()) {
                                        Text(strings.settings.noBackupsFound)
                                    } else {
                                        Text(strings.settings.selectBackupPrompt, style = MaterialTheme.typography.bodyMedium)
                                        availableBackups.forEach { backup ->
                                            val sizeMb = "%.1f".format((backup.systemSizeBytes + backup.photosSizeBytes) / (1024.0 * 1024.0))
                                            val selected = selectedBackup?.date == backup.date
                                            Surface(
                                                modifier = Modifier.fillMaxWidth().clickable { selectedBackup = backup; restoreError = null },
                                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                                        else MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(8.dp),
                                                tonalElevation = if (selected) 2.dp else 0.dp
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(backup.date, style = MaterialTheme.typography.bodyLarge)
                                                    Text("${sizeMb} MB" + if (backup.photosFile != null) " (${strings.settings.withPhotos})" else " (${strings.settings.dataOnly})",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                                }
                                            }
                                        }
                                        if (selectedBackup != null) {
                                            Text(strings.settings.restoreWarning,
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                            OutlinedTextField(
                                                value = restorePassword, onValueChange = { restorePassword = it; restoreError = null },
                                                label = { Text(strings.settings.backupPasswordLabel) }, singleLine = true,
                                                visualTransformation = PasswordVisualTransformation(),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (restoreError != null) {
                                            Text(restoreError!!, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                DialogFooter {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (!restoring) {
                                            DialogSecondaryButton(onClick = { showRestoreDialog = false }) { Text(strings.common.cancel) }
                                        }
                                        if (selectedBackup != null && !restoring) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            DialogDangerButton(onClick = {
                                                if (restorePassword.isEmpty()) { restoreError = strings.settings.enterPasswordError; return@DialogDangerButton }
                                                restoring = true
                                                val backup = selectedBackup!!
                                                val pwd = restorePassword.toCharArray()
                                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                    val sysResult = com.syncbudget.app.data.BackupManager.restoreSystemBackup(context, backup.systemFile, pwd)
                                                    if (sysResult.isSuccess && backup.photosFile != null) {
                                                        val photosResult = com.syncbudget.app.data.BackupManager.restorePhotosBackup(context, backup.photosFile, pwd)
                                                        val photosRestored = photosResult.getOrNull() ?: 0
                                                        android.util.Log.d("BackupRestore", "Photos restored: $photosRestored")
                                                    }
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        restoring = false
                                                        if (sysResult.isSuccess) {
                                                            showRestoreDialog = false
                                                            this@MainActivity.recreate()
                                                        } else {
                                                            restoreError = sysResult.exceptionOrNull()?.message ?: "Restore failed"
                                                        }
                                                    }
                                                }
                                            }) { Text(strings.settings.restoreBtn) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                } // Box(weight)
              } // Column
            // Quick Start Guide overlay
            if (quickStartStep != null) {
                QuickStartOverlay(
                    step = quickStartStep!!,
                    onNext = {
                        val nextStep = when (quickStartStep) {
                            QuickStartStep.WELCOME -> QuickStartStep.BUDGET_PERIOD
                            QuickStartStep.BUDGET_PERIOD -> QuickStartStep.INCOME
                            QuickStartStep.INCOME -> QuickStartStep.EXPENSES
                            QuickStartStep.EXPENSES -> QuickStartStep.FIRST_TRANSACTION
                            QuickStartStep.FIRST_TRANSACTION -> QuickStartStep.DONE
                            QuickStartStep.DONE -> null
                            null -> null
                        }
                        quickStartStep = nextStep
                        if (nextStep == null) {
                            prefs.edit().putBoolean("quickStartCompleted", true).apply()
                        }
                    },
                    onSkip = {
                        quickStartStep = null
                        prefs.edit().putBoolean("quickStartCompleted", true).apply()
                    },
                    onNavigate = { screen -> currentScreen = screen },
                    isEnglish = appLanguage != "es",
                    isPaidUser = isPaidUser,
                    onLanguageChange = { lang ->
                        appLanguage = lang
                        prefs.edit().putString("appLanguage", lang).apply()
                    }
                )
            }
            } // SyncBudgetTheme
        }
    }
}
