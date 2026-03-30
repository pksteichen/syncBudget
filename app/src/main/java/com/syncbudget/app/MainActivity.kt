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
import com.syncbudget.app.data.sync.SyncMergeProcessor
import com.syncbudget.app.data.sync.SyncWriteHelper
import com.syncbudget.app.data.PeriodRefreshService
import com.syncbudget.app.data.sync.EncryptedDocSerializer
import com.syncbudget.app.data.sync.FirestoreDocService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import com.syncbudget.app.data.sync.SubscriptionReminderReceiver
import com.syncbudget.app.data.sync.SyncWorker
import com.syncbudget.app.data.sync.active
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.syncbudget.app.data.BackupManager
import com.syncbudget.app.data.DiagDumpBuilder
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
import com.syncbudget.app.ui.theme.AppToastState
import com.syncbudget.app.ui.theme.LocalAppToast
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    companion object {
        /** True when the app is visible to the user. Background workers check this to skip. */
        @Volatile var isAppActive = false
    }

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

            // Track when the last successful push or receive occurred (epoch millis).
            // Declared before save functions so they can update it on push.
            var lastSyncActivity by remember { mutableStateOf(0L) }

            // Save functions: persist to local JSON + auto-push ALL records to Firestore.
            // Save functions: persist to JSON + push only CHANGED records to Firestore.
            // Each collection keeps a snapshot (by ID) of the last-saved state.
            // On save, only records that are new or differ from the snapshot are pushed.
            val lastSavedTxns = remember { mutableMapOf<Int, Transaction>() }
            val lastSavedRe = remember { mutableMapOf<Int, RecurringExpense>() }
            val lastSavedIs = remember { mutableMapOf<Int, IncomeSource>() }
            val lastSavedSg = remember { mutableMapOf<Int, SavingsGoal>() }
            val lastSavedAe = remember { mutableMapOf<Int, AmortizationEntry>() }
            val lastSavedCat = remember { mutableMapOf<Int, Category>() }
            val lastSavedPle = remember { mutableMapOf<Int, PeriodLedgerEntry>() }

            fun saveIncomeSources(hint: List<IncomeSource>? = null) {
                val current = incomeSources.toList()
                IncomeSourceRepository.save(context, current)
                if (SyncWriteHelper.isInitialized()) {
                    if (hint != null) {
                        hint.forEach { SyncWriteHelper.pushIncomeSource(it) }
                    } else {
                        for (src in current) {
                            if (lastSavedIs[src.id] != src) SyncWriteHelper.pushIncomeSource(src)
                        }
                    }
                    current.associateByTo(lastSavedIs) { it.id }
                    if (hint == null || hint.isNotEmpty()) lastSyncActivity = System.currentTimeMillis()
                }
            }

            fun saveRecurringExpenses(hint: List<RecurringExpense>? = null) {
                val current = recurringExpenses.toList()
                RecurringExpenseRepository.save(context, current)
                if (SyncWriteHelper.isInitialized()) {
                    if (hint != null) {
                        hint.forEach { SyncWriteHelper.pushRecurringExpense(it) }
                    } else {
                        for (re in current) {
                            if (lastSavedRe[re.id] != re) SyncWriteHelper.pushRecurringExpense(re)
                        }
                    }
                    current.associateByTo(lastSavedRe) { it.id }
                    if (hint == null || hint.isNotEmpty()) lastSyncActivity = System.currentTimeMillis()
                }
            }

            fun saveAmortizationEntries(hint: List<AmortizationEntry>? = null) {
                val current = amortizationEntries.toList()
                AmortizationRepository.save(context, current)
                if (SyncWriteHelper.isInitialized()) {
                    if (hint != null) {
                        hint.forEach { SyncWriteHelper.pushAmortizationEntry(it) }
                    } else {
                        for (ae in current) {
                            if (lastSavedAe[ae.id] != ae) SyncWriteHelper.pushAmortizationEntry(ae)
                        }
                    }
                    current.associateByTo(lastSavedAe) { it.id }
                    if (hint == null || hint.isNotEmpty()) lastSyncActivity = System.currentTimeMillis()
                }
            }

            fun saveSavingsGoals(hint: List<SavingsGoal>? = null) {
                val current = savingsGoals.toList()
                SavingsGoalRepository.save(context, current)
                if (SyncWriteHelper.isInitialized()) {
                    if (hint != null) {
                        hint.forEach { SyncWriteHelper.pushSavingsGoal(it) }
                    } else {
                        for (sg in current) {
                            if (lastSavedSg[sg.id] != sg) SyncWriteHelper.pushSavingsGoal(sg)
                        }
                    }
                    current.associateByTo(lastSavedSg) { it.id }
                    if (hint == null || hint.isNotEmpty()) lastSyncActivity = System.currentTimeMillis()
                }
            }

            fun saveTransactions(hint: List<Transaction>? = null) {
                // Dedup by ID before saving
                val deduped = transactions.groupBy { it.id }
                    .values.map { group -> group.first() }
                if (deduped.size < transactions.size) {
                    android.util.Log.w("MainActivity", "Deduped ${transactions.size - deduped.size} duplicate transactions")
                    transactions.clear()
                    transactions.addAll(deduped)
                }
                val current = transactions.toList()
                TransactionRepository.save(context, current)
                if (SyncWriteHelper.isInitialized()) {
                    if (hint != null) {
                        hint.forEach { SyncWriteHelper.pushTransaction(it) }
                    } else {
                        for (txn in current) {
                            if (lastSavedTxns[txn.id] != txn) SyncWriteHelper.pushTransaction(txn)
                        }
                    }
                    current.associateByTo(lastSavedTxns) { it.id }
                    if (hint == null || hint.isNotEmpty()) lastSyncActivity = System.currentTimeMillis()
                }
            }

            fun saveCategories(hint: List<Category>? = null) {
                val current = categories.toList()
                CategoryRepository.save(context, current)
                if (SyncWriteHelper.isInitialized()) {
                    if (hint != null) {
                        hint.forEach { SyncWriteHelper.pushCategory(it) }
                    } else {
                        for (cat in current) {
                            if (lastSavedCat[cat.id] != cat) SyncWriteHelper.pushCategory(cat)
                        }
                    }
                    current.associateByTo(lastSavedCat) { it.id }
                    if (hint == null || hint.isNotEmpty()) lastSyncActivity = System.currentTimeMillis()
                }
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

            fun savePeriodLedger(hint: List<PeriodLedgerEntry>? = null) {
                val current = periodLedger.toList()
                PeriodLedgerRepository.save(context, current)
                if (SyncWriteHelper.isInitialized()) {
                    if (hint != null) {
                        hint.forEach { SyncWriteHelper.pushPeriodLedgerEntry(it) }
                    } else {
                        for (ple in current) {
                            if (lastSavedPle[ple.id] != ple) SyncWriteHelper.pushPeriodLedgerEntry(ple)
                        }
                    }
                    current.associateByTo(lastSavedPle) { it.id }
                    if (hint == null || hint.isNotEmpty()) lastSyncActivity = System.currentTimeMillis()
                }
            }

            // ── Shared Settings (for sync) ──
            var sharedSettings by remember { mutableStateOf(SharedSettingsRepository.load(context)) }

            // ── Family Sync state ──
            val syncPrefs = remember { context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE) }
            var isSyncConfigured by remember { mutableStateOf(GroupManager.isConfigured(context)) }
            var syncGroupId by remember { mutableStateOf(GroupManager.getGroupId(context)) }
            var isSyncAdmin by remember { mutableStateOf(GroupManager.isAdmin(context)) }
            var syncStatus by remember { mutableStateOf(if (GroupManager.isConfigured(context)) "synced" else "off") }
            var syncDevices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
            var generatedPairingCode by remember { mutableStateOf<String?>(null) }
            val localDeviceId = remember { SyncIdGenerator.getOrCreateDeviceId(context) }
            val coroutineScope = rememberCoroutineScope()
            var syncErrorMessage by remember { mutableStateOf<String?>(null) }
            var syncProgressMessage by remember { mutableStateOf<String?>(null) }
            var pendingAdminClaim by remember { mutableStateOf<AdminClaim?>(null) }
            var syncRepairAlert by remember { mutableStateOf(
                prefs.getBoolean("syncRepairAlert", false)
            ) }

            // Live "X ago" display computed from lastSyncActivity epoch millis
            var lastSyncTimeDisplay by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(lastSyncActivity) {
                while (true) {
                    val elapsed = if (lastSyncActivity > 0) (System.currentTimeMillis() - lastSyncActivity) / 1000 else -1L
                    lastSyncTimeDisplay = when {
                        elapsed < 0 -> null
                        elapsed < 10 -> "just now"
                        elapsed < 60 -> "${elapsed}s ago"
                        elapsed < 3600 -> "${elapsed / 60}m ago"
                        else -> "${elapsed / 3600}h ago"
                    }
                    delay(10_000)
                }
            }

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

            // Gate for sync-dependent code: true immediately for solo users,
            // set to true after initial Firestore listener snapshot for synced users.
            // Shared by migrations and period refresh to ensure lastKnownState is
            // populated before any save function fires (enables diffs, not full writes).
            var initialSyncReceived by remember { mutableStateOf(!isSyncConfigured) }

            // One-time migrations — each wrapped in try-catch so a failure
            // in one migration doesn't block subsequent migrations or crash
            // the LaunchedEffect.  Flags are set AFTER success.
            LaunchedEffect(Unit) {
                // Wait for initial listener snapshot so lastKnownState is populated.
                // lastSyncActivity updates when the listener delivers data.
                if (isSyncConfigured && !initialSyncReceived) {
                    val start = System.currentTimeMillis()
                    while (!initialSyncReceived && System.currentTimeMillis() - start < 5_000L) {
                        delay(200)
                        if (lastSyncActivity > start) {
                            initialSyncReceived = true
                        }
                    }
                    if (!initialSyncReceived) {
                        initialSyncReceived = true
                        android.util.Log.w("Migration", "Timed out waiting for initial sync — proceeding with local data")
                    }
                }
                // Purge tombstoned records (deleted=true) from local JSON.
                // These are leftover from old groups and serve no purpose locally.
                // Also strips any legacy clock fields from JSON on re-save.
                try {
                    if (!prefs.getBoolean("migration_purge_tombstones", false)) {
                        val txnBefore = transactions.size
                        val reBefore = recurringExpenses.size
                        val isBefore = incomeSources.size
                        val sgBefore = savingsGoals.size
                        val aeBefore = amortizationEntries.size
                        val catBefore = categories.size
                        transactions.removeAll { it.deleted }
                        recurringExpenses.removeAll { it.deleted }
                        incomeSources.removeAll { it.deleted }
                        savingsGoals.removeAll { it.deleted }
                        amortizationEntries.removeAll { it.deleted }
                        categories.removeAll { it.deleted }
                        val purged = (txnBefore - transactions.size) + (reBefore - recurringExpenses.size) +
                            (isBefore - incomeSources.size) + (sgBefore - savingsGoals.size) +
                            (aeBefore - amortizationEntries.size) + (catBefore - categories.size)
                        if (purged > 0) android.util.Log.i("Migration", "Purged $purged tombstoned records")
                        TransactionRepository.save(context, transactions.toList())
                        RecurringExpenseRepository.save(context, recurringExpenses.toList())
                        IncomeSourceRepository.save(context, incomeSources.toList())
                        SavingsGoalRepository.save(context, savingsGoals.toList())
                        AmortizationRepository.save(context, amortizationEntries.toList())
                        CategoryRepository.save(context, categories.toList())
                        PeriodLedgerRepository.save(context, periodLedger.toList())
                        SharedSettingsRepository.save(context, sharedSettings)
                        prefs.edit().putBoolean("migration_purge_tombstones", true).apply()
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
                                    savePeriodLedger(listOf(periodLedger[idx]))
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
                if (txn.linkedAmortizationEntryId != null) return true
                // SG-linked: only fully accounted if savings covers the entire amount
                if (txn.linkedSavingsGoalId != null || txn.linkedSavingsGoalAmount > 0.0) {
                    return txn.linkedSavingsGoalAmount >= txn.amount
                }
                return false
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
                    android.util.Log.i("SyncLifecycle", "Lifecycle event: $event")
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                        isAppActive = true
                    } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                        isAppActive = false
                    }
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        syncTrigger++
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Persistent Firestore-native sync — listener lifecycle is tied to
            // the sync group identity (syncGroupId), NOT to isSyncConfigured.
            // This prevents listener stop/restart when isSyncConfigured toggles
            // briefly (e.g. recomposition), avoiding Firestore re-delivering
            // all cached documents and the associated decryption cost.

            // Create docSync instance keyed on group identity. Survives across
            // isSyncConfigured toggles as long as the group stays the same.
            val docSync = remember(syncGroupId) {
                val gid = syncGroupId ?: return@remember null
                val key = GroupManager.getEncryptionKey(context) ?: return@remember null
                android.util.Log.i("SyncLifecycle", "remember(syncGroupId=$gid): creating NEW FirestoreDocSync")
                FirestoreDocSync(context, gid, localDeviceId, key)
            }

            // Listener lifecycle: starts when docSync is created (group joined),
            // stops only when docSync changes (group left/dissolved) or activity destroyed.
            DisposableEffect(docSync) {
                android.util.Log.i("SyncLifecycle", "DisposableEffect: docSync=${if (docSync != null) "exists" else "null"}, isListening=${docSync?.isListening}")
                if (docSync != null) {
                    SyncWriteHelper.initialize(docSync)

                    // Listener callback: update in-memory state when remote changes arrive.
                    // Events arrive batched per collection — apply all, then save once.
                    // All referenced variables are Compose mutable state holders (stable
                    // references), so this callback always reads current values.
                    docSync.onBatchChanged = { events ->
                        try {
                            // Build catIdRemap from sync prefs
                            val remapJson = syncPrefs.getString("catIdRemap", null)
                            val catIdRemap: MutableMap<Int, Int> = if (remapJson != null) {
                                try {
                                    val json = org.json.JSONObject(remapJson)
                                    json.keys().asSequence().associate { it.toInt() to json.getInt(it) }.toMutableMap()
                                } catch (_: Exception) { mutableMapOf() }
                            } else mutableMapOf()

                            // Process batch through shared merge processor
                            val result = SyncMergeProcessor.processBatch(
                                events = events,
                                currentTransactions = transactions.toList(),
                                currentRecurringExpenses = recurringExpenses.toList(),
                                currentIncomeSources = incomeSources.toList(),
                                currentSavingsGoals = savingsGoals.toList(),
                                currentAmortizationEntries = amortizationEntries.toList(),
                                currentCategories = categories.toList(),
                                currentPeriodLedger = periodLedger.toList(),
                                currentSharedSettings = sharedSettings,
                                catIdRemap = catIdRemap,
                                currentBudgetStartDate = budgetStartDate
                            )

                            // Apply merged data to in-memory Compose state
                            result.transactions?.let { list ->
                                transactions.clear(); transactions.addAll(list)
                            }
                            result.recurringExpenses?.let { list ->
                                recurringExpenses.clear(); recurringExpenses.addAll(list)
                            }
                            result.incomeSources?.let { list ->
                                incomeSources.clear(); incomeSources.addAll(list)
                            }
                            result.savingsGoals?.let { list ->
                                savingsGoals.clear(); savingsGoals.addAll(list)
                            }
                            result.amortizationEntries?.let { list ->
                                amortizationEntries.clear(); amortizationEntries.addAll(list)
                            }
                            result.categories?.let { list ->
                                categories.clear(); categories.addAll(list)
                            }
                            result.periodLedger?.let { list ->
                                periodLedger.clear(); periodLedger.addAll(list)
                            }
                            result.sharedSettings?.let { merged ->
                                sharedSettings = merged
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
                                }
                            }

                            // Apply settings prefs
                            result.settingsPrefsToApply?.let { prefsMap ->
                                val editor = prefs.edit()
                                for ((key, value) in prefsMap) {
                                    when (value) {
                                        is String -> editor.putString(key, value)
                                        is Boolean -> editor.putBoolean(key, value)
                                        is Int -> editor.putInt(key, value)
                                        else -> editor.putString(key, value.toString())
                                    }
                                }
                                editor.apply()
                            }

                            // Persist catIdRemap if modified
                            if (catIdRemap.isNotEmpty()) {
                                syncPrefs.edit().putString("catIdRemap",
                                    org.json.JSONObject(catIdRemap.mapKeys { it.key.toString() }).toString()
                                ).apply()
                            }

                            // Recompute cash (skip if only categories changed)
                            val hasNonCatChanges = events.any { it.collection != EncryptedDocSerializer.COLLECTION_CATEGORIES }
                            if (hasNonCatChanges) recomputeCash()

                            // Save changed collections to JSON on background thread
                            val txnSnap = result.transactions
                            val reSnap = result.recurringExpenses
                            val isSnap = result.incomeSources
                            val sgSnap = result.savingsGoals
                            val aeSnap = result.amortizationEntries
                            val catSnap = result.categories
                            val pleSnap = result.periodLedger
                            val ssSnap = result.sharedSettings
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                txnSnap?.let { TransactionRepository.save(context, it) }
                                reSnap?.let { RecurringExpenseRepository.save(context, it) }
                                isSnap?.let { IncomeSourceRepository.save(context, it) }
                                sgSnap?.let { SavingsGoalRepository.save(context, it) }
                                aeSnap?.let { AmortizationRepository.save(context, it) }
                                catSnap?.let { CategoryRepository.save(context, it) }
                                pleSnap?.let { PeriodLedgerRepository.save(context, it) }
                                ssSnap?.let { SharedSettingsRepository.save(context, it) }
                            }

                            // Handle side effects: push back conflicts, delete remapped categories
                            for (txn in result.conflictedTransactionsToPushBack) {
                                SyncWriteHelper.pushTransaction(txn)
                            }
                            if (result.categoriesToDeleteFromFirestore.isNotEmpty()) {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val gId = syncGroupId ?: return@launch
                                    for (catId in result.categoriesToDeleteFromFirestore) {
                                        try {
                                            FirestoreDocService.deleteDoc(gId,
                                                EncryptedDocSerializer.COLLECTION_CATEGORIES, catId.toString())
                                            android.util.Log.i("SyncDedup", "Deleted remapped category $catId from Firestore")
                                        } catch (e: Exception) {
                                            android.util.Log.w("SyncDedup", "Failed to delete remapped category: ${e.message}")
                                        }
                                    }
                                }
                            }

                            com.syncbudget.app.widget.BudgetWidgetProvider.updateAllWidgets(context)
                            lastSyncActivity = System.currentTimeMillis()
                            if (result.conflictDetected) {
                                syncRepairAlert = true
                                prefs.edit().putBoolean("syncRepairAlert", true).apply()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SyncListener", "Failed to handle batch", e)
                        }
                    }

                    // Start real-time listeners
                    try {
                        docSync.startListeners()
                        android.util.Log.i("SyncLoop", "Persistent listeners started for group ${syncGroupId}")
                    } catch (e: Exception) {
                        android.util.Log.e("SyncLoop", "Failed to start listeners", e)
                    }
                }
                onDispose {
                    android.util.Log.i("SyncLifecycle", "DisposableEffect.onDispose: stopping listeners")
                    docSync?.dispose()
                    SyncWriteHelper.dispose()
                }
            }

            // Sync setup: migrations, one-time pushes, and health check loop.
            // This LaunchedEffect restarts on isSyncConfigured changes, but
            // listeners are managed by the DisposableEffect above and persist.
            LaunchedEffect(isSyncConfigured) {
                if (!isSyncConfigured) return@LaunchedEffect
                val groupId = GroupManager.getGroupId(context) ?: return@LaunchedEffect
                val key = GroupManager.getEncryptionKey(context) ?: return@LaunchedEffect
                if (docSync == null) return@LaunchedEffect

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

                // One-time migration: re-push all records in per-field encrypted format
                if (!syncPrefs.getBoolean("migration_per_field_enc_done", false)) {
                    try {
                        syncStatus = "syncing"
                        syncProgressMessage = "Upgrading encryption..."
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
                        syncPrefs.edit().putBoolean("migration_per_field_enc_done", true).apply()
                        syncProgressMessage = null
                    } catch (e: Exception) {
                        android.util.Log.e("SyncLoop", "Per-field encryption migration failed", e)
                        syncProgressMessage = null
                    }
                }

                // Admin: clean up Firestore tombstones that all devices have seen.
                // Finds the oldest lastSeen across all devices, then deletes
                // tombstoned docs whose updatedAt is older than that minus 1 day buffer.
                if (isSyncAdmin) {
                    try {
                        val deviceRecords = FirestoreService.getDevices(groupId)
                        if (deviceRecords.size >= 2) {
                            val oldestLastSeen = deviceRecords.minOf { it.lastSeen }
                            val cutoff = oldestLastSeen - 24 * 60 * 60 * 1000L // 1-day buffer
                            if (cutoff > 0) {
                                var totalPurged = 0
                                for (collection in EncryptedDocSerializer.ALL_COLLECTIONS) {
                                    val docs = FirestoreDocService.readAllDocs(groupId, collection)
                                    for (doc in docs) {
                                        val deleted = doc.getBoolean("deleted") ?: false
                                        if (!deleted) continue
                                        val updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: continue
                                        if (updatedAt < cutoff) {
                                            FirestoreDocService.deleteDoc(groupId, collection, doc.id)
                                            totalPurged++
                                        }
                                    }
                                }
                                if (totalPurged > 0) {
                                    android.util.Log.i("SyncLoop", "Purged $totalPurged old tombstones from Firestore")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SyncLoop", "Tombstone cleanup failed: ${e.message}")
                    }

                    // Clean up orphaned Cloud Storage receipt files (no matching ledger entry)
                    try {
                        com.syncbudget.app.data.sync.ImageLedgerService.purgeOrphanedCloudFiles(groupId)
                    } catch (e: Exception) {
                        android.util.Log.w("SyncLoop", "Orphan cloud cleanup failed: ${e.message}")
                    }
                }

                syncStatus = "synced"
                lastSyncActivity = System.currentTimeMillis()
                var healthCheckCounter = 0

                // Periodic loop: push dirty changes + health checks
                // No finally block needed — listener cleanup is in DisposableEffect above.
                while (true) {
                    delay(5_000) // 5 seconds between checks
                    healthCheckCounter++

                    // Per-record pushes replaced the dirty-push-all loop.
                    // Save functions now push individual changed records via
                    // SyncWriteHelper. Clear stale dirty flag if set.
                    if (syncPrefs.getBoolean("syncDirty", false)) {
                        syncPrefs.edit().putBoolean("syncDirty", false).apply()
                    }

                    // Ensure listeners are alive — restart if they died
                    if (docSync != null && !docSync.isListening) {
                        try {
                            docSync.startListeners()
                            android.util.Log.i("SyncLoop", "Restarted dead listeners")
                        } catch (e: Exception) {
                            android.util.Log.w("SyncLoop", "Failed to restart listeners: ${e.message}")
                        }
                    }

                    // Light health check every ~60 seconds (12 * 5s):
                    // update device metadata, refresh device list, receipt sync
                    if (healthCheckCounter % 12 == 0) {
                        try {
                            syncDevices = GroupManager.getDevices(groupId)
                            // Receipt photo sync (paid users only)
                            if (isPaidUser || isSubscriber) {
                                try {
                                    val deviceRecords = FirestoreService.getDevices(groupId)
                                    val receiptSync = com.syncbudget.app.data.sync.ReceiptSyncManager(
                                        context, groupId, localDeviceId, key
                                    )
                                    val updatedTxns = receiptSync.syncReceipts(transactions.toList(), deviceRecords)
                                    if (updatedTxns != transactions.toList()) {
                                        transactions.clear()
                                        transactions.addAll(updatedTxns)
                                        TransactionRepository.save(context, updatedTxns)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("SyncLoop", "Receipt sync failed: ${e.message}")
                                }
                            }
                            isSyncAdmin = GroupManager.isAdmin(context)
                            // Update device metadata — keep appSyncVersion=2
                            // to avoid triggering "update_required" in old
                            // SyncWorker until it's fully replaced.
                            val receiptPrefs = context.getSharedPreferences("receipt_sync_prefs", Context.MODE_PRIVATE)
                            val uploadSpeed = receiptPrefs.getLong("lastUploadSpeedBps", 0L)
                            val speedMeasuredAt = receiptPrefs.getLong("lastSpeedMeasuredAt", 0L)
                            FirestoreService.updateDeviceMetadata(
                                groupId, localDeviceId,
                                syncVersion = 0L,
                                appSyncVersion = 2,
                                minSyncVersion = 2,
                                photoCapable = isPaidUser || isSubscriber,
                                uploadSpeedBps = uploadSpeed,
                                uploadSpeedMeasuredAt = speedMeasuredAt
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("SyncLoop", "Light health check failed", e)
                        }
                    }

                    // Heavy health checks every ~5 minutes (60 * 5s = 300s):
                    // dissolution, removal, subscription, group activity
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
                            // Update group activity timestamp
                            FirestoreService.updateGroupActivity(groupId)

                            // ── Integrity check: compare local counts against Firestore ──
                            // Firestore is the source of truth. Each device independently
                            // checks if its local state matches. No peer comparison needed.
                            val localCounts = mapOf(
                                EncryptedDocSerializer.COLLECTION_TRANSACTIONS to transactions.count { !it.deleted },
                                EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES to recurringExpenses.count { !it.deleted },
                                EncryptedDocSerializer.COLLECTION_INCOME_SOURCES to incomeSources.count { !it.deleted },
                                EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS to savingsGoals.count { !it.deleted },
                                EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES to amortizationEntries.count { !it.deleted },
                                EncryptedDocSerializer.COLLECTION_CATEGORIES to categories.count { !it.deleted },
                                EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER to periodLedger.size
                            )

                            // IDs of categories remapped by tag dedup (exist in Firestore
                            // but not in local list — excluded from integrity comparison)
                            val remapJson = syncPrefs.getString("catIdRemap", null)
                            val remappedCatIds = if (remapJson != null) {
                                try {
                                    val json = org.json.JSONObject(remapJson)
                                    json.keys().asSequence().map { it }.toSet()
                                } catch (_: Exception) { emptySet() }
                            } else emptySet<String>()

                            val divergent = mutableListOf<String>()
                            for ((collection, localCount) in localCounts) {
                                val firestoreDocs = FirestoreDocService.readAllDocs(groupId, collection)
                                val firestoreCount = firestoreDocs.count { doc ->
                                    val isDeleted = doc.getBoolean("deleted") == true
                                    val isRemapped = collection == EncryptedDocSerializer.COLLECTION_CATEGORIES &&
                                        doc.id in remappedCatIds
                                    !isDeleted && !isRemapped
                                }
                                if (localCount != firestoreCount) {
                                    if (localCount > firestoreCount) {
                                        // Local has records Firestore doesn't — push missing ones
                                        android.util.Log.w("Integrity",
                                            "$collection: local=$localCount firestore=$firestoreCount — pushing missing records")
                                        val firestoreIds = firestoreDocs.map { it.id }.toSet()
                                        val missingRecords = when (collection) {
                                            EncryptedDocSerializer.COLLECTION_TRANSACTIONS ->
                                                transactions.filter { !it.deleted && it.id.toString() !in firestoreIds }
                                            EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES ->
                                                recurringExpenses.filter { !it.deleted && it.id.toString() !in firestoreIds }
                                            EncryptedDocSerializer.COLLECTION_INCOME_SOURCES ->
                                                incomeSources.filter { !it.deleted && it.id.toString() !in firestoreIds }
                                            EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS ->
                                                savingsGoals.filter { !it.deleted && it.id.toString() !in firestoreIds }
                                            EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES ->
                                                amortizationEntries.filter { !it.deleted && it.id.toString() !in firestoreIds }
                                            EncryptedDocSerializer.COLLECTION_CATEGORIES ->
                                                categories.filter { !it.deleted && it.id.toString() !in firestoreIds }
                                            EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER ->
                                                periodLedger.filter { it.id.toString() !in firestoreIds }
                                            else -> emptyList()
                                        }
                                        for (record in missingRecords) {
                                            when (record) {
                                                is Transaction -> SyncWriteHelper.pushTransaction(record)
                                                is RecurringExpense -> SyncWriteHelper.pushRecurringExpense(record)
                                                is IncomeSource -> SyncWriteHelper.pushIncomeSource(record)
                                                is SavingsGoal -> SyncWriteHelper.pushSavingsGoal(record)
                                                is AmortizationEntry -> SyncWriteHelper.pushAmortizationEntry(record)
                                                is Category -> SyncWriteHelper.pushCategory(record)
                                                is PeriodLedgerEntry -> SyncWriteHelper.pushPeriodLedgerEntry(record)
                                            }
                                        }
                                        android.util.Log.i("Integrity", "Pushed ${missingRecords.size} missing records for $collection")
                                    } else {
                                        // Firestore has records we don't — reattach to receive them
                                        android.util.Log.w("Integrity",
                                            "$collection: local=$localCount firestore=$firestoreCount — reattaching")
                                        docSync?.reattachListener(collection)
                                    }
                                    divergent.add(collection)
                                }
                            }
                            if (divergent.isEmpty()) {
                                android.util.Log.i("Integrity", "All collections match Firestore")
                            } else {
                                // Recompute cash after reattach settles
                                recomputeCash()
                            }

                            // Log cash for cross-device comparison in diagnostic dumps
                            android.util.Log.i("Integrity", "Cash: $availableCash")

                            // Update device metadata (lastSeen + cash for diagnostic comparison)
                            val cashJson = org.json.JSONObject().apply {
                                put("cash", availableCash)
                            }.toString()
                            FirestoreService.updateDeviceMetadata(
                                groupId, localDeviceId,
                                syncVersion = 0L,
                                fingerprintJson = cashJson,
                                appSyncVersion = 2,
                                minSyncVersion = 2,
                                photoCapable = isPaidUser || isSubscriber
                            )

                            // Diagnostic: compare cash with other devices (log only, no repair)
                            try {
                                val allDevices = FirestoreService.getDevices(groupId)
                                for (device in allDevices) {
                                    if (device.deviceId == localDeviceId) continue
                                    val fp = device.fingerprintData ?: continue
                                    val remoteCash = org.json.JSONObject(fp).optDouble("cash", Double.NaN)
                                    if (!remoteCash.isNaN()) {
                                        val diff = kotlin.math.abs(availableCash - remoteCash)
                                        if (diff > 0.01) {
                                            android.util.Log.w("Integrity",
                                                "Cash differs from ${device.deviceId.take(8)}: local=$availableCash remote=$remoteCash (diff=${"%.2f".format(diff)})")
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        } catch (e: Exception) {
                            android.util.Log.w("SyncLoop", "Health check failed", e)
                        }
                    }
                }
            }



            // Schedule background sync when configured
            LaunchedEffect(isSyncConfigured) {
                if (isSyncConfigured) {
                    SyncWorker.schedule(context)
                    com.syncbudget.app.data.sync.PeriodRefreshWorker.schedule(context)
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
                        saveSavingsGoals(listOf(savingsGoals[gIdx]))
                    }
                }
                // Guard against duplicate IDs (e.g., double-tap or recomposition replay)
                if (transactions.none { it.id == stamped.id }) {
                    transactions.add(stamped)
                }
                saveTransactions(listOf(stamped))
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

            // Period refresh — checks every 30s while the app is open so the
            // UI updates when a period boundary passes without needing a restart.
            // Delegates to shared PeriodRefreshService (also used by PeriodRefreshWorker).
            LaunchedEffect(Unit) {
                // Wait for initial sync if needed (shared gate set by migrations block)
                while (!initialSyncReceived) { delay(200) }
                while (true) {
                    if (budgetStartDate != null && lastRefreshDate != null) {
                        val config = PeriodRefreshService.RefreshConfig(
                            budgetStartDate = budgetStartDate!!,
                            lastRefreshDate = lastRefreshDate!!,
                            budgetPeriod = budgetPeriod,
                            resetHour = resetHour,
                            resetDayOfWeek = resetDayOfWeek,
                            resetDayOfMonth = resetDayOfMonth,
                            familyTimezone = if (isSyncConfigured && sharedSettings.familyTimezone.isNotEmpty())
                                sharedSettings.familyTimezone else "",
                            localDeviceId = localDeviceId,
                            incomeMode = incomeMode,
                            isManualBudgetEnabled = isManualBudgetEnabled,
                            manualBudgetAmount = manualBudgetAmount
                        )
                        val result = PeriodRefreshService.refreshIfNeeded(context, config)
                        if (result != null) {
                            // Update in-memory Compose state from result
                            lastRefreshDate = result.newLastRefreshDate
                            for (entry in result.newLedgerEntries) {
                                if (periodLedger.none { it.id == entry.id }) periodLedger.add(entry)
                            }
                            for (sg in result.updatedSavingsGoals) {
                                val idx = savingsGoals.indexOfFirst { it.id == sg.id }
                                if (idx >= 0) savingsGoals[idx] = sg
                            }
                            for (re in result.updatedRecurringExpenses) {
                                val idx = recurringExpenses.indexOfFirst { it.id == re.id }
                                if (idx >= 0) recurringExpenses[idx] = re
                            }
                            recomputeCash()
                            // Push to Firestore via save functions (hint-based).
                            // Uses existing save functions which handle disk save
                            // (redundant but harmless), push via SyncWriteHelper,
                            // and update lastSaved* caches + lastSyncActivity.
                            if (result.newLedgerEntries.isNotEmpty()) savePeriodLedger(result.newLedgerEntries)
                            if (result.updatedSavingsGoals.isNotEmpty()) saveSavingsGoals(result.updatedSavingsGoals)
                            if (result.updatedRecurringExpenses.isNotEmpty()) saveRecurringExpenses(result.updatedRecurringExpenses)
                        }
                    }
                    delay(30_000) // Re-check every 30 seconds
                }
            }

            // ── One-time CRDT state dump to Downloads (debug only) ──
            if (com.syncbudget.app.BuildConfig.DEBUG) {
                remember {
                    try {
                        val diagText = DiagDumpBuilder.build(context)
                        DiagDumpBuilder.writeDiagToMediaStore(context, "sync_diag.txt", diagText)
                        // Also write device-named copy
                        val devName = DiagDumpBuilder.sanitizeDeviceName(com.syncbudget.app.data.sync.GroupManager.getDeviceName(context))
                        if (devName.isNotEmpty()) {
                            DiagDumpBuilder.writeDiagToMediaStore(context, "sync_diag_${devName}.txt", diagText)
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
                            // Ensure listeners are alive
                            if (docSync != null && !docSync.isListening) {
                                docSync.startListeners()
                            }
                            // Refresh device list
                            syncDevices = GroupManager.getDevices(gId)
                            isSyncAdmin = GroupManager.isAdmin(context)
                            // Trigger receipt photo sync (paid users only)
                            if (isPaidUser || isSubscriber) {
                                try {
                                    val deviceRecords = FirestoreService.getDevices(gId)
                                    val receiptSync = com.syncbudget.app.data.sync.ReceiptSyncManager(
                                        context, gId, localDeviceId, key
                                    )
                                    val updatedTxns = receiptSync.syncReceipts(transactions.toList(), deviceRecords)
                                    if (updatedTxns != transactions.toList()) {
                                        transactions.clear()
                                        transactions.addAll(updatedTxns)
                                        TransactionRepository.save(context, updatedTxns)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("SyncNow", "Receipt sync failed: ${e.message}")
                                }
                            }
                            syncStatus = "synced"
                            syncErrorMessage = null
                            lastSyncActivity = System.currentTimeMillis()
                        } catch (_: Exception) {
                            syncStatus = "error"
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
                            val changedGoals = mutableListOf<SavingsGoal>()
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
                                        changedGoals.add(updatedGoal)
                                        deposits.add(goal.name to capped)
                                    }
                                }
                            }
                            if (deposits.isNotEmpty()) {
                                saveSavingsGoals(changedGoals)
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
                    "settings" -> SettingsScreenBranch(
                        currencySymbol = currencySymbol,
                        appLanguage = appLanguage,
                        onSetAppLanguage = { appLanguage = it },
                        prefs = prefs,
                        categories = categories,
                        saveCategories = { saveCategories(it) },
                        onSetCurrentScreen = { currentScreen = it },
                        onSetQuickStartStep = { quickStartStep = it },
                        matchDays = matchDays,
                        onSetMatchDays = { matchDays = it },
                        isSyncConfigured = isSyncConfigured,
                        sharedSettings = sharedSettings,
                        onSetSharedSettings = { sharedSettings = it },
                        context = context,
                        localDeviceId = localDeviceId,
                        onSetLastSyncActivity = { lastSyncActivity = it },
                        matchPercent = matchPercent,
                        onSetMatchPercent = { matchPercent = it },
                        matchDollar = matchDollar,
                        onSetMatchDollar = { matchDollar = it },
                        matchChars = matchChars,
                        onSetMatchChars = { matchChars = it },
                        chartPalette = chartPalette,
                        onSetChartPalette = { chartPalette = it },
                        budgetPeriod = budgetPeriod,
                        weekStartSunday = weekStartSunday,
                        onSetWeekStartSunday = { weekStartSunday = it },
                        onSetCurrencySymbol = { currencySymbol = it },
                        isSyncAdmin = isSyncAdmin,
                        showDecimals = showDecimals,
                        onSetShowDecimals = { showDecimals = it },
                        dateFormatPattern = dateFormatPattern,
                        onSetDateFormatPattern = { dateFormatPattern = it },
                        isPaidUser = isPaidUser,
                        onSetIsPaidUser = { isPaidUser = it },
                        isSubscriber = isSubscriber,
                        onSetIsSubscriber = { isSubscriber = it },
                        subscriptionExpiry = subscriptionExpiry,
                        onSetSubscriptionExpiry = { subscriptionExpiry = it },
                        showWidgetLogo = showWidgetLogo,
                        onSetShowWidgetLogo = { showWidgetLogo = it },
                        activeCategories = activeCategories,
                        activeTransactions = activeTransactions,
                        transactions = transactions,
                        saveTransactions = { saveTransactions(it) },
                        backupsEnabled = backupsEnabled,
                        onSetShowBackupPasswordDialog = { showBackupPasswordDialog = it },
                        onSetShowDisableBackupDialog = { showDisableBackupDialog = it },
                        backupFrequencyWeeks = backupFrequencyWeeks,
                        onSetBackupFrequencyWeeks = { backupFrequencyWeeks = it },
                        backupPrefs = backupPrefs,
                        backupRetention = backupRetention,
                        onSetBackupRetention = { backupRetention = it },
                        lastBackupDate = lastBackupDate,
                        onSetLastBackupDate = { lastBackupDate = it },
                        onSetShowRestoreDialog = { showRestoreDialog = it },
                        onSetShowSavePhotosDialog = { showSavePhotosDialog = it },
                        coroutineScope = coroutineScope,
                        syncGroupId = syncGroupId,
                        toastState = toastState,
                    )
                    "transactions" -> TransactionsScreenBranch(
                        activeTransactions = activeTransactions,
                        currencySymbol = currencySymbol,
                        dateFormatPattern = dateFormatPattern,
                        activeCategories = activeCategories,
                        isPaidUser = isPaidUser,
                        isSubscriber = isSubscriber,
                        activeRecurringExpenses = activeRecurringExpenses,
                        activeAmortizationEntries = activeAmortizationEntries,
                        activeIncomeSources = activeIncomeSources,
                        activeSavingsGoals = activeSavingsGoals,
                        matchDays = matchDays,
                        matchPercent = matchPercent,
                        matchDollar = matchDollar,
                        matchChars = matchChars,
                        chartPalette = chartPalette,
                        sharedSettings = sharedSettings,
                        onSetSharedSettings = { sharedSettings = it },
                        isSyncConfigured = isSyncConfigured,
                        onSetIsSyncConfigured = { isSyncConfigured = it },
                        syncDevices = syncDevices,
                        onSetSyncDevices = { syncDevices = it },
                        localDeviceId = localDeviceId,
                        addTransactionWithBudgetEffect = { addTransactionWithBudgetEffect(it) },
                        transactions = transactions,
                        savingsGoals = savingsGoals,
                        saveSavingsGoals = { saveSavingsGoals(it) },
                        saveTransactions = { saveTransactions(it) },
                        recomputeCash = { recomputeCash() },
                        coroutineScope = coroutineScope,
                        context = context,
                        categories = categories,
                        recurringExpenses = recurringExpenses,
                        incomeSources = incomeSources,
                        amortizationEntries = amortizationEntries,
                        periodLedger = periodLedger,
                        prefs = prefs,
                        onSetCurrencySymbol = { currencySymbol = it },
                        onSetDigitCount = { digitCount = it },
                        onSetShowDecimals = { showDecimals = it },
                        onSetDateFormatPattern = { dateFormatPattern = it },
                        onSetChartPalette = { chartPalette = it },
                        onSetAppLanguage = { appLanguage = it },
                        onSetBudgetPeriod = { budgetPeriod = it },
                        onSetResetHour = { resetHour = it },
                        onSetResetDayOfWeek = { resetDayOfWeek = it },
                        onSetResetDayOfMonth = { resetDayOfMonth = it },
                        onSetIsManualBudgetEnabled = { isManualBudgetEnabled = it },
                        onSetManualBudgetAmount = { manualBudgetAmount = it },
                        onSetAvailableCash = { availableCash = it },
                        onSetBudgetStartDate = { budgetStartDate = it },
                        onSetLastRefreshDate = { lastRefreshDate = it },
                        onSetWeekStartSunday = { weekStartSunday = it },
                        onSetMatchDays = { matchDays = it },
                        onSetMatchPercent = { matchPercent = it },
                        onSetMatchDollar = { matchDollar = it },
                        onSetMatchChars = { matchChars = it },
                        syncGroupId = syncGroupId,
                        onSetSyncGroupId = { syncGroupId = it },
                        isSyncAdmin = isSyncAdmin,
                        onSetIsSyncAdmin = { isSyncAdmin = it },
                        syncStatus = syncStatus,
                        onSetSyncStatus = { syncStatus = it },
                        lastSyncActivity = lastSyncActivity,
                        onSetLastSyncActivity = { lastSyncActivity = it },
                        onSetGeneratedPairingCode = { generatedPairingCode = it },
                        saveCategories = { saveCategories(it) },
                        saveRecurringExpenses = { saveRecurringExpenses(it) },
                        saveIncomeSources = { saveIncomeSources(it) },
                        saveAmortizationEntries = { saveAmortizationEntries(it) },
                        budgetPeriod = budgetPeriod,
                        incomeMode = incomeMode,
                        onSetCurrentScreen = { currentScreen = it },
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
                            val added = goal.copy(
                                deviceId = localDeviceId,
                            )
                            savingsGoals.add(added)
                            saveSavingsGoals(listOf(added))
                        },
                        onUpdateGoal = { updated ->
                            val idx = savingsGoals.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = savingsGoals[idx]
                                savingsGoals[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deleted = old.deleted,
                                )
                                saveSavingsGoals(listOf(savingsGoals[idx]))
                            }
                        },
                        onDeleteGoal = { goal ->
                            val idx = savingsGoals.indexOfFirst { it.id == goal.id }
                            if (idx >= 0) {
                                savingsGoals[idx] = savingsGoals[idx].copy(deleted = true)
                                saveSavingsGoals(listOf(savingsGoals[idx]))
                                // Unlink any transactions linked to this goal.
                                // PRESERVE linkedSavingsGoalAmount — those expenses were
                                // already paid from savings.  Clearing it would cause
                                // availableCash to drop (double-counting the expense).
                                // Manual unlink (linked-in-error) clears amount separately.
                                val unlinkedTxns = mutableListOf<Transaction>()
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedSavingsGoalId == goal.id) {
                                        transactions[i] = txn.copy(
                                            linkedSavingsGoalId = null,
                                        )
                                        unlinkedTxns.add(transactions[i])
                                    }
                                }
                                saveTransactions(unlinkedTxns)
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
                            val added = entry.copy(
                                deviceId = localDeviceId,
                            )
                            amortizationEntries.add(added)
                            saveAmortizationEntries(listOf(added))
                        },
                        onUpdateEntry = { updated ->
                            val idx = amortizationEntries.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = amortizationEntries[idx]
                                amortizationEntries[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deleted = old.deleted,
                                )
                                saveAmortizationEntries(listOf(amortizationEntries[idx]))
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
                                saveAmortizationEntries(listOf(amortizationEntries[idx]))
                                // Unlink transactions and record the already-applied portion
                                val unlinkedTxns = mutableListOf<Transaction>()
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedAmortizationEntryId == entry.id) {
                                        transactions[i] = txn.copy(
                                            linkedAmortizationEntryId = null,
                                            amortizationAppliedAmount = appliedAmount,
                                        )
                                        unlinkedTxns.add(transactions[i])
                                    }
                                }
                                saveTransactions(unlinkedTxns)
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
                            val added = expense.copy(
                                deviceId = localDeviceId,
                            )
                            recurringExpenses.add(added)
                            saveRecurringExpenses(listOf(added))
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
                                saveRecurringExpenses(listOf(recurringExpenses[idx]))
                                if (hasLinkedTxns) {
                                    pendingREAmountUpdate = Pair(updated, old.amount)
                                }
                            }
                        },
                        onDeleteRecurringExpense = { expense ->
                            val idx = recurringExpenses.indexOfFirst { it.id == expense.id }
                            if (idx >= 0) {
                                recurringExpenses[idx] = recurringExpenses[idx].copy(deleted = true)
                                saveRecurringExpenses(listOf(recurringExpenses[idx]))
                                // Unlink any transactions linked to this expense
                                val unlinkedTxns = mutableListOf<Transaction>()
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedRecurringExpenseId == expense.id) {
                                        transactions[i] = txn.copy(
                                            linkedRecurringExpenseId = null,
                                        )
                                        unlinkedTxns.add(transactions[i])
                                    }
                                }
                                saveTransactions(unlinkedTxns)
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
                            val added = src.copy(deviceId = localDeviceId)
                            incomeSources.add(added)
                            saveIncomeSources(listOf(added))
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
                                saveIncomeSources(listOf(incomeSources[idx]))
                                if (hasLinkedTxns) {
                                    pendingISAmountUpdate = Pair(updated, old.amount)
                                }
                            }
                        },
                        onDeleteIncomeSource = { src ->
                            val idx = incomeSources.indexOfFirst { it.id == src.id }
                            if (idx >= 0) {
                                incomeSources[idx] = incomeSources[idx].copy(deleted = true)
                                saveIncomeSources(listOf(incomeSources[idx]))
                                val unlinkedTxns = mutableListOf<Transaction>()
                                transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedIncomeSourceId == src.id) {
                                        transactions[i] = txn.copy(linkedIncomeSourceId = null)
                                        unlinkedTxns.add(transactions[i])
                                    }
                                }
                                saveTransactions(unlinkedTxns)
                                recomputeCash()
                            }
                        },
                        budgetPeriod = budgetPeriod,
                        onBudgetPeriodChange = {
                            budgetPeriod = it; prefs.edit().putString("budgetPeriod", it.name).apply()
                            if (isSyncConfigured) {
                                val updated = sharedSettings.copy(budgetPeriod = it.name, lastChangedBy = localDeviceId)
                                sharedSettings = updated
                                SharedSettingsRepository.save(context, sharedSettings)
                                com.syncbudget.app.data.sync.SyncWriteHelper.pushSharedSettings(sharedSettings)
                                lastSyncActivity = System.currentTimeMillis()
                            }
                        },
                        resetHour = resetHour,
                        onResetHourChange = {
                            resetHour = it; prefs.edit().putInt("resetHour", it).apply()
                            if (isSyncConfigured) {
                                val updated = sharedSettings.copy(resetHour = it, lastChangedBy = localDeviceId)
                                sharedSettings = updated
                                SharedSettingsRepository.save(context, sharedSettings)
                                com.syncbudget.app.data.sync.SyncWriteHelper.pushSharedSettings(sharedSettings)
                                lastSyncActivity = System.currentTimeMillis()
                            }
                        },
                        resetDayOfWeek = resetDayOfWeek,
                        onResetDayOfWeekChange = {
                            resetDayOfWeek = it; prefs.edit().putInt("resetDayOfWeek", it).apply()
                            val newWeekStart = (it == 7)
                            if (weekStartSunday != newWeekStart) {
                                weekStartSunday = newWeekStart
                                prefs.edit().putBoolean("weekStartSunday", newWeekStart).apply()
                            }
                            if (isSyncConfigured) {
                                val updated = sharedSettings.copy(
                                    resetDayOfWeek = it,
                                    weekStartSunday = newWeekStart,
                                    lastChangedBy = localDeviceId
                                )
                                sharedSettings = updated
                                SharedSettingsRepository.save(context, sharedSettings)
                                com.syncbudget.app.data.sync.SyncWriteHelper.pushSharedSettings(sharedSettings)
                                lastSyncActivity = System.currentTimeMillis()
                            }
                        },
                        resetDayOfMonth = resetDayOfMonth,
                        onResetDayOfMonthChange = {
                            resetDayOfMonth = it; prefs.edit().putInt("resetDayOfMonth", it).apply()
                            if (isSyncConfigured) {
                                val updated = sharedSettings.copy(resetDayOfMonth = it, lastChangedBy = localDeviceId)
                                sharedSettings = updated
                                SharedSettingsRepository.save(context, sharedSettings)
                                com.syncbudget.app.data.sync.SyncWriteHelper.pushSharedSettings(sharedSettings)
                                lastSyncActivity = System.currentTimeMillis()
                            }
                        },
                        safeBudgetAmount = safeBudgetAmount,
                        isManualBudgetEnabled = isManualBudgetEnabled,
                        manualBudgetAmount = manualBudgetAmount,
                        onManualBudgetToggle = { enabled ->
                            isManualBudgetEnabled = enabled
                            prefs.edit().putBoolean("isManualBudgetEnabled", enabled).apply()
                            if (enabled && incomeMode == IncomeMode.ACTUAL_ADJUST) {
                                incomeMode = IncomeMode.ACTUAL
                                prefs.edit().putString("incomeMode", "ACTUAL").apply()
                                if (isSyncConfigured) {
                                    sharedSettings = sharedSettings.copy(incomeMode = "ACTUAL", lastChangedBy = localDeviceId)
                                }
                            }
                            if (isSyncConfigured) {
                                val updated = sharedSettings.copy(isManualBudgetEnabled = enabled, lastChangedBy = localDeviceId)
                                sharedSettings = updated
                                SharedSettingsRepository.save(context, sharedSettings)
                                com.syncbudget.app.data.sync.SyncWriteHelper.pushSharedSettings(sharedSettings)
                                lastSyncActivity = System.currentTimeMillis()
                            }
                        },
                        onManualBudgetAmountChange = { amount ->
                            manualBudgetAmount = amount
                            prefs.edit().putString("manualBudgetAmount", amount.toString()).apply()
                            if (isSyncConfigured) {
                                val updated = sharedSettings.copy(manualBudgetAmount = amount, lastChangedBy = localDeviceId)
                                sharedSettings = updated
                                SharedSettingsRepository.save(context, sharedSettings)
                                com.syncbudget.app.data.sync.SyncWriteHelper.pushSharedSettings(sharedSettings)
                                lastSyncActivity = System.currentTimeMillis()
                            }
                        },
                        budgetStartDate = budgetStartDate?.format(java.time.format.DateTimeFormatter.ofPattern(dateFormatPattern)),
                        onResetBudget = {
                            val tz = if (isSyncConfigured && sharedSettings.familyTimezone.isNotEmpty())
                                java.time.ZoneId.of(sharedSettings.familyTimezone) else null
                            budgetStartDate = BudgetCalculator.currentPeriodStart(budgetPeriod, resetDayOfWeek, resetDayOfMonth, tz, resetHour)
                            lastRefreshDate = budgetStartDate
                            val entryDate = budgetStartDate!!.atStartOfDay()
                            val alreadyRecorded = periodLedger.any {
                                it.periodStartDate.toLocalDate() == budgetStartDate
                            }
                            val newLedgerEntry = if (!alreadyRecorded) {
                                val entry = com.syncbudget.app.data.sync.PeriodLedgerEntry(
                                    periodStartDate = entryDate,
                                    appliedAmount = budgetAmount,
                                    deviceId = localDeviceId
                                )
                                periodLedger.add(entry)
                                entry
                            } else null
                            savePeriodLedger(listOfNotNull(newLedgerEntry))
                            if (isSyncConfigured) {
                                val updated = sharedSettings.copy(
                                    budgetStartDate = budgetStartDate.toString(),
                                    lastChangedBy = localDeviceId
                                )
                                sharedSettings = updated
                                SharedSettingsRepository.save(context, sharedSettings)
                                com.syncbudget.app.data.sync.SyncWriteHelper.pushSharedSettings(sharedSettings)
                                lastSyncActivity = System.currentTimeMillis()
                            }
                            recomputeCash()
                            prefs.edit()
                                .putString("budgetStartDate", budgetStartDate.toString())
                                .putString("lastRefreshDate", budgetStartDate.toString())
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
                                val updated = sharedSettings.copy(incomeMode = modeName, lastChangedBy = localDeviceId)
                                sharedSettings = updated
                                SharedSettingsRepository.save(context, sharedSettings)
                                com.syncbudget.app.data.sync.SyncWriteHelper.pushSharedSettings(sharedSettings)
                                lastSyncActivity = System.currentTimeMillis()
                            }
                            recomputeCash()
                        },
                        onBack = { currentScreen = "settings" },
                        onHelpClick = { currentScreen = "budget_config_help" }
                    )
                    "family_sync" -> FamilySyncScreenBranch(
                        isSyncConfigured = isSyncConfigured,
                        onSetIsSyncConfigured = { isSyncConfigured = it },
                        isSubscriber = isSubscriber,
                        syncGroupId = syncGroupId,
                        onSetSyncGroupId = { syncGroupId = it },
                        isSyncAdmin = isSyncAdmin,
                        onSetIsSyncAdmin = { isSyncAdmin = it },
                        localDeviceId = localDeviceId,
                        syncDevices = syncDevices,
                        onSetSyncDevices = { syncDevices = it },
                        syncStatus = syncStatus,
                        onSetSyncStatus = { syncStatus = it },
                        lastSyncTimeDisplay = lastSyncTimeDisplay,
                        sharedSettings = sharedSettings,
                        onSetSharedSettings = { sharedSettings = it },
                        context = context,
                        onSetLastSyncActivity = { lastSyncActivity = it },
                        transactions = transactions,
                        pendingAdminClaim = pendingAdminClaim,
                        onSetPendingAdminClaim = { pendingAdminClaim = it },
                        coroutineScope = coroutineScope,
                        syncErrorMessage = syncErrorMessage,
                        onSetSyncErrorMessage = { syncErrorMessage = it },
                        syncProgressMessage = syncProgressMessage,
                        onSetSyncProgressMessage = { syncProgressMessage = it },
                        currencySymbol = currencySymbol,
                        budgetPeriod = budgetPeriod,
                        budgetStartDate = budgetStartDate,
                        isManualBudgetEnabled = isManualBudgetEnabled,
                        manualBudgetAmount = manualBudgetAmount,
                        weekStartSunday = weekStartSunday,
                        resetDayOfWeek = resetDayOfWeek,
                        resetDayOfMonth = resetDayOfMonth,
                        resetHour = resetHour,
                        matchDays = matchDays,
                        matchPercent = matchPercent,
                        matchDollar = matchDollar,
                        matchChars = matchChars,
                        incomeMode = incomeMode,
                        categories = categories,
                        recurringExpenses = recurringExpenses,
                        incomeSources = incomeSources,
                        savingsGoals = savingsGoals,
                        amortizationEntries = amortizationEntries,
                        periodLedger = periodLedger,
                        saveTransactions = { saveTransactions(it) },
                        saveCategories = { saveCategories(it) },
                        saveSavingsGoals = { saveSavingsGoals(it) },
                        saveAmortizationEntries = { saveAmortizationEntries(it) },
                        saveRecurringExpenses = { saveRecurringExpenses(it) },
                        saveIncomeSources = { saveIncomeSources(it) },
                        doSyncNow = doSyncNow,
                        generatedPairingCode = generatedPairingCode,
                        onSetGeneratedPairingCode = { generatedPairingCode = it },
                        syncPrefs = syncPrefs,
                        toastState = toastState,
                        onSetCurrentScreen = { currentScreen = it },
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
                DashboardDialogs(
                    dashboardShowAddIncome = dashboardShowAddIncome,
                    dashboardShowAddExpense = dashboardShowAddExpense,
                    dashShowManualDuplicateDialog = dashShowManualDuplicateDialog,
                    dashPendingManualSave = dashPendingManualSave,
                    dashManualDuplicateMatch = dashManualDuplicateMatch,
                    dashShowRecurringDialog = dashShowRecurringDialog,
                    dashPendingRecurringTxn = dashPendingRecurringTxn,
                    dashPendingRecurringMatch = dashPendingRecurringMatch,
                    dashShowAmortizationDialog = dashShowAmortizationDialog,
                    dashPendingAmortizationTxn = dashPendingAmortizationTxn,
                    dashPendingAmortizationMatch = dashPendingAmortizationMatch,
                    dashShowBudgetIncomeDialog = dashShowBudgetIncomeDialog,
                    dashPendingBudgetIncomeTxn = dashPendingBudgetIncomeTxn,
                    dashPendingBudgetIncomeMatch = dashPendingBudgetIncomeMatch,
                    pendingREAmountUpdate = pendingREAmountUpdate,
                    pendingISAmountUpdate = pendingISAmountUpdate,
                    showBackupPasswordDialog = showBackupPasswordDialog,
                    showDisableBackupDialog = showDisableBackupDialog,
                    showRestoreDialog = showRestoreDialog,
                    showSavePhotosDialog = showSavePhotosDialog,
                    backupsEnabled = backupsEnabled,
                    strings = strings,
                    activeCategories = activeCategories,
                    existingIds = existingIds,
                    currencySymbol = currencySymbol,
                    dateFormatter = dateFormatter,
                    chartPalette = chartPalette,
                    activeRecurringExpenses = activeRecurringExpenses,
                    activeAmortizationEntries = activeAmortizationEntries,
                    activeIncomeSources = activeIncomeSources,
                    activeSavingsGoals = activeSavingsGoals,
                    activeTransactions = activeTransactions,
                    budgetPeriod = budgetPeriod,
                    isPaidUser = isPaidUser,
                    isSubscriber = isSubscriber,
                    localDeviceId = localDeviceId,
                    categoryMap = categoryMap,
                    percentTolerance = percentTolerance,
                    matchDollar = matchDollar,
                    matchChars = matchChars,
                    matchDays = matchDays,
                    categories = categories,
                    incomeMode = incomeMode,
                    context = context,
                    toastState = toastState,
                    transactions = transactions,
                    amortizationEntries = amortizationEntries,
                    incomeSources = incomeSources,
                    backupPrefs = backupPrefs,
                    onSetDashboardShowAddIncome = { dashboardShowAddIncome = it },
                    onSetDashboardShowAddExpense = { dashboardShowAddExpense = it },
                    onSetDashShowManualDuplicateDialog = { dashShowManualDuplicateDialog = it },
                    onSetDashPendingManualSave = { dashPendingManualSave = it },
                    onSetDashManualDuplicateMatch = { dashManualDuplicateMatch = it },
                    onSetDashShowRecurringDialog = { dashShowRecurringDialog = it },
                    onSetDashPendingRecurringTxn = { dashPendingRecurringTxn = it },
                    onSetDashPendingRecurringMatch = { dashPendingRecurringMatch = it },
                    onSetDashShowAmortizationDialog = { dashShowAmortizationDialog = it },
                    onSetDashPendingAmortizationTxn = { dashPendingAmortizationTxn = it },
                    onSetDashPendingAmortizationMatch = { dashPendingAmortizationMatch = it },
                    onSetDashShowBudgetIncomeDialog = { dashShowBudgetIncomeDialog = it },
                    onSetDashPendingBudgetIncomeTxn = { dashPendingBudgetIncomeTxn = it },
                    onSetDashPendingBudgetIncomeMatch = { dashPendingBudgetIncomeMatch = it },
                    onSetPendingREAmountUpdate = { pendingREAmountUpdate = it },
                    onSetPendingISAmountUpdate = { pendingISAmountUpdate = it },
                    onSetShowBackupPasswordDialog = { showBackupPasswordDialog = it },
                    onSetShowDisableBackupDialog = { showDisableBackupDialog = it },
                    onSetShowRestoreDialog = { showRestoreDialog = it },
                    onSetShowSavePhotosDialog = { showSavePhotosDialog = it },
                    onSetBackupsEnabled = { backupsEnabled = it },
                    runMatchingChain = { runMatchingChain(it) },
                    runLinkingChain = { runLinkingChain(it) },
                    addTransactionWithBudgetEffect = { addTransactionWithBudgetEffect(it) },
                    saveAmortizationEntries = { saveAmortizationEntries(it) },
                    saveTransactions = { saveTransactions(it) },
                    saveIncomeSources = { saveIncomeSources(it) },
                    recomputeCash = { recomputeCash() },
                )

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

    @Composable
    private fun DashboardDialogs(
        dashboardShowAddIncome: Boolean,
        dashboardShowAddExpense: Boolean,
        dashShowManualDuplicateDialog: Boolean,
        dashPendingManualSave: Transaction?,
        dashManualDuplicateMatch: Transaction?,
        dashShowRecurringDialog: Boolean,
        dashPendingRecurringTxn: Transaction?,
        dashPendingRecurringMatch: RecurringExpense?,
        dashShowAmortizationDialog: Boolean,
        dashPendingAmortizationTxn: Transaction?,
        dashPendingAmortizationMatch: AmortizationEntry?,
        dashShowBudgetIncomeDialog: Boolean,
        dashPendingBudgetIncomeTxn: Transaction?,
        dashPendingBudgetIncomeMatch: IncomeSource?,
        pendingREAmountUpdate: Pair<RecurringExpense, Double>?,
        pendingISAmountUpdate: Pair<IncomeSource, Double>?,
        showBackupPasswordDialog: Boolean,
        showDisableBackupDialog: Boolean,
        showRestoreDialog: Boolean,
        showSavePhotosDialog: Boolean,
        backupsEnabled: Boolean,
        strings: com.syncbudget.app.ui.strings.AppStrings,
        activeCategories: List<Category>,
        existingIds: Set<Int>,
        currencySymbol: String,
        dateFormatter: java.time.format.DateTimeFormatter,
        chartPalette: String,
        activeRecurringExpenses: List<RecurringExpense>,
        activeAmortizationEntries: List<AmortizationEntry>,
        activeIncomeSources: List<IncomeSource>,
        activeSavingsGoals: List<SavingsGoal>,
        activeTransactions: List<Transaction>,
        budgetPeriod: BudgetPeriod,
        isPaidUser: Boolean,
        isSubscriber: Boolean,
        localDeviceId: String,
        categoryMap: Map<Int, Category>,
        percentTolerance: Double,
        matchDollar: Int,
        matchChars: Int,
        matchDays: Int,
        categories: SnapshotStateList<Category>,
        incomeMode: IncomeMode,
        context: android.content.Context,
        toastState: AppToastState,
        transactions: SnapshotStateList<Transaction>,
        amortizationEntries: SnapshotStateList<AmortizationEntry>,
        incomeSources: SnapshotStateList<IncomeSource>,
        backupPrefs: SharedPreferences,
        onSetDashboardShowAddIncome: (Boolean) -> Unit,
        onSetDashboardShowAddExpense: (Boolean) -> Unit,
        onSetDashShowManualDuplicateDialog: (Boolean) -> Unit,
        onSetDashPendingManualSave: (Transaction?) -> Unit,
        onSetDashManualDuplicateMatch: (Transaction?) -> Unit,
        onSetDashShowRecurringDialog: (Boolean) -> Unit,
        onSetDashPendingRecurringTxn: (Transaction?) -> Unit,
        onSetDashPendingRecurringMatch: (RecurringExpense?) -> Unit,
        onSetDashShowAmortizationDialog: (Boolean) -> Unit,
        onSetDashPendingAmortizationTxn: (Transaction?) -> Unit,
        onSetDashPendingAmortizationMatch: (AmortizationEntry?) -> Unit,
        onSetDashShowBudgetIncomeDialog: (Boolean) -> Unit,
        onSetDashPendingBudgetIncomeTxn: (Transaction?) -> Unit,
        onSetDashPendingBudgetIncomeMatch: (IncomeSource?) -> Unit,
        onSetPendingREAmountUpdate: (Pair<RecurringExpense, Double>?) -> Unit,
        onSetPendingISAmountUpdate: (Pair<IncomeSource, Double>?) -> Unit,
        onSetShowBackupPasswordDialog: (Boolean) -> Unit,
        onSetShowDisableBackupDialog: (Boolean) -> Unit,
        onSetShowRestoreDialog: (Boolean) -> Unit,
        onSetShowSavePhotosDialog: (Boolean) -> Unit,
        onSetBackupsEnabled: (Boolean) -> Unit,
        runMatchingChain: (Transaction) -> Unit,
        runLinkingChain: (Transaction) -> Unit,
        addTransactionWithBudgetEffect: (Transaction) -> Unit,
        saveAmortizationEntries: (List<AmortizationEntry>) -> Unit,
        saveTransactions: (List<Transaction>) -> Unit,
        saveIncomeSources: (List<IncomeSource>) -> Unit,
        recomputeCash: () -> Unit,
    ) {
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
                onDismiss = { onSetDashboardShowAddIncome(false) },
                onSave = { txn ->
                    runMatchingChain(txn)
                    onSetDashboardShowAddIncome(false)
                },
                onAddAmortization = { entry ->
                    val added = entry.copy(
                        deviceId = localDeviceId,
                    )
                    amortizationEntries.add(added)
                    saveAmortizationEntries(listOf(added))
                },
                onDeleteAmortization = { entry ->
                    val idx = amortizationEntries.indexOfFirst { it.id == entry.id }
                    if (idx >= 0) {
                        amortizationEntries[idx] = amortizationEntries[idx].copy(
                            deleted = true
                        )
                        saveAmortizationEntries(listOf(amortizationEntries[idx]))
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
                onDismiss = { onSetDashboardShowAddExpense(false) },
                onSave = { txn ->
                    runMatchingChain(txn)
                    onSetDashboardShowAddExpense(false)
                },
                onAddAmortization = { entry ->
                    val added = entry.copy(
                        deviceId = localDeviceId,
                    )
                    amortizationEntries.add(added)
                    saveAmortizationEntries(listOf(added))
                },
                onDeleteAmortization = { entry ->
                    val idx = amortizationEntries.indexOfFirst { it.id == entry.id }
                    if (idx >= 0) {
                        amortizationEntries[idx] = amortizationEntries[idx].copy(
                            deleted = true
                        )
                        saveAmortizationEntries(listOf(amortizationEntries[idx]))
                    }
                }
            )
        }

        // Dashboard duplicate resolution dialog
        if (dashShowManualDuplicateDialog && dashPendingManualSave != null && dashManualDuplicateMatch != null) {
            DuplicateResolutionDialog(
                existingTransaction = dashManualDuplicateMatch,
                newTransaction = dashPendingManualSave,
                currencySymbol = currencySymbol,
                dateFormatter = dateFormatter,
                categoryMap = categoryMap,
                showIgnoreAll = false,
                onIgnore = {
                    val txn = dashPendingManualSave
                    onSetDashPendingManualSave(null)
                    onSetDashManualDuplicateMatch(null)
                    onSetDashShowManualDuplicateDialog(false)
                    runLinkingChain(txn)
                },
                onKeepNew = {
                    val dup = dashManualDuplicateMatch
                    val dupIdx = transactions.indexOfFirst { it.id == dup.id }
                    if (dupIdx >= 0) {
                        transactions[dupIdx] = transactions[dupIdx].copy(
                            deleted = true
                        )
                    }
                    saveTransactions(if (dupIdx >= 0) listOf(transactions[dupIdx]) else emptyList())
                    val txn = dashPendingManualSave
                    onSetDashPendingManualSave(null)
                    onSetDashManualDuplicateMatch(null)
                    onSetDashShowManualDuplicateDialog(false)
                    runLinkingChain(txn)
                },
                onKeepExisting = {
                    onSetDashPendingManualSave(null)
                    onSetDashManualDuplicateMatch(null)
                    onSetDashShowManualDuplicateDialog(false)
                },
                onIgnoreAll = {}
            )
        }

        // Dashboard recurring expense match dialog
        if (dashShowRecurringDialog && dashPendingRecurringTxn != null && dashPendingRecurringMatch != null) {
            val dateCloseEnough = isRecurringDateCloseEnough(dashPendingRecurringTxn.date, dashPendingRecurringMatch)
            RecurringExpenseConfirmDialog(
                transaction = dashPendingRecurringTxn,
                recurringExpense = dashPendingRecurringMatch,
                currencySymbol = currencySymbol,
                dateFormatter = dateFormatter,
                showDateAdvisory = !dateCloseEnough,
                onConfirmRecurring = {
                    val txn = dashPendingRecurringTxn
                    val updatedTxn = txn.copy(
                        linkedRecurringExpenseId = dashPendingRecurringMatch.id,
                        linkedRecurringExpenseAmount = dashPendingRecurringMatch.amount
                    )
                    addTransactionWithBudgetEffect(updatedTxn)
                    onSetDashPendingRecurringTxn(null)
                    onSetDashPendingRecurringMatch(null)
                    onSetDashShowRecurringDialog(false)
                },
                onNotRecurring = {
                    val txn = dashPendingRecurringTxn
                    onSetDashPendingRecurringTxn(null)
                    onSetDashPendingRecurringMatch(null)
                    onSetDashShowRecurringDialog(false)
                    // Continue linking chain: check amortization, then budget income
                    val amortizationMatch = findAmortizationMatch(txn, activeAmortizationEntries, percentTolerance, matchDollar, matchChars)
                    if (amortizationMatch != null) {
                        onSetDashPendingAmortizationTxn(txn)
                        onSetDashPendingAmortizationMatch(amortizationMatch)
                        onSetDashShowAmortizationDialog(true)
                    } else {
                        val budgetMatch = findBudgetIncomeMatch(txn, activeIncomeSources, matchChars, matchDays)
                        if (budgetMatch != null) {
                            onSetDashPendingBudgetIncomeTxn(txn)
                            onSetDashPendingBudgetIncomeMatch(budgetMatch)
                            onSetDashShowBudgetIncomeDialog(true)
                        } else {
                            addTransactionWithBudgetEffect(txn)
                        }
                    }
                }
            )
        }

        // Dashboard amortization match dialog
        if (dashShowAmortizationDialog && dashPendingAmortizationTxn != null && dashPendingAmortizationMatch != null) {
            AmortizationConfirmDialog(
                transaction = dashPendingAmortizationTxn,
                amortizationEntry = dashPendingAmortizationMatch,
                currencySymbol = currencySymbol,
                dateFormatter = dateFormatter,
                onConfirmAmortization = {
                    val txn = dashPendingAmortizationTxn
                    val updatedTxn = txn.copy(linkedAmortizationEntryId = dashPendingAmortizationMatch.id)
                    addTransactionWithBudgetEffect(updatedTxn)
                    onSetDashPendingAmortizationTxn(null)
                    onSetDashPendingAmortizationMatch(null)
                    onSetDashShowAmortizationDialog(false)
                },
                onNotAmortized = {
                    addTransactionWithBudgetEffect(dashPendingAmortizationTxn)
                    onSetDashPendingAmortizationTxn(null)
                    onSetDashPendingAmortizationMatch(null)
                    onSetDashShowAmortizationDialog(false)
                }
            )
        }

        // Dashboard budget income match dialog
        if (dashShowBudgetIncomeDialog && dashPendingBudgetIncomeTxn != null && dashPendingBudgetIncomeMatch != null) {
            BudgetIncomeConfirmDialog(
                transaction = dashPendingBudgetIncomeTxn,
                incomeSource = dashPendingBudgetIncomeMatch,
                currencySymbol = currencySymbol,
                dateFormatter = dateFormatter,
                onConfirmBudgetIncome = {
                    val recurringIncomeCatId = categories.find { it.tag == "recurring_income" }?.id
                    val baseTxn = dashPendingBudgetIncomeTxn
                    val txn = baseTxn.copy(
                        isBudgetIncome = true,
                        linkedIncomeSourceId = dashPendingBudgetIncomeMatch.id,
                        linkedIncomeSourceAmount = dashPendingBudgetIncomeMatch.amount,
                        categoryAmounts = if (recurringIncomeCatId != null)
                            listOf(CategoryAmount(recurringIncomeCatId, baseTxn.amount))
                        else baseTxn.categoryAmounts,
                        isUserCategorized = true
                    )
                    // ACTUAL_ADJUST: update the income source BEFORE adding txn
                    // so recomputeCash sees matching amounts (delta = 0)
                    if (incomeMode == IncomeMode.ACTUAL_ADJUST) {
                        val srcId = dashPendingBudgetIncomeMatch.id
                        val idx = incomeSources.indexOfFirst { it.id == srcId }
                        if (idx >= 0 && incomeSources[idx].amount != baseTxn.amount) {
                            incomeSources[idx] = incomeSources[idx].copy(
                                amount = baseTxn.amount,
                            )
                            saveIncomeSources(listOf(incomeSources[idx]))
                        }
                    }
                    addTransactionWithBudgetEffect(txn)
                    onSetDashPendingBudgetIncomeTxn(null)
                    onSetDashPendingBudgetIncomeMatch(null)
                    onSetDashShowBudgetIncomeDialog(false)
                },
                onNotBudgetIncome = {
                    addTransactionWithBudgetEffect(dashPendingBudgetIncomeTxn)
                    onSetDashPendingBudgetIncomeTxn(null)
                    onSetDashPendingBudgetIncomeMatch(null)
                    onSetDashShowBudgetIncomeDialog(false)
                }
            )
        }

        // Confirmation dialog: apply recurring expense amount change to past transactions?
        pendingREAmountUpdate?.let { (updated, oldAmount) ->
            AdAwareAlertDialog(
                onDismissRequest = {
                    onSetPendingREAmountUpdate(null)
                    recomputeCash()
                },
                title = { Text(strings.common.applyToPastTitle) },
                text = { Text(strings.common.applyToPastBody) },
                style = DialogStyle.WARNING,
                confirmButton = {
                    DialogWarningButton(onClick = {
                        val changedTxns = mutableListOf<Transaction>()
                        transactions.forEachIndexed { i, txn ->
                            if (txn.linkedRecurringExpenseId == updated.id && !txn.deleted) {
                                transactions[i] = txn.copy(
                                    linkedRecurringExpenseAmount = updated.amount,
                                )
                                changedTxns.add(transactions[i])
                            }
                        }
                        saveTransactions(changedTxns)
                        onSetPendingREAmountUpdate(null)
                        recomputeCash()
                    }) { Text(strings.common.applyToPastConfirm) }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = {
                        onSetPendingREAmountUpdate(null)
                        recomputeCash()
                    }) { Text(strings.common.applyToPastDeny) }
                }
            )
        }

        // Confirmation dialog: apply income source amount change to past transactions?
        pendingISAmountUpdate?.let { (updated, oldAmount) ->
            AdAwareAlertDialog(
                onDismissRequest = {
                    onSetPendingISAmountUpdate(null)
                    recomputeCash()
                },
                title = { Text(strings.common.applyToPastTitle) },
                text = { Text(strings.common.applyToPastBody) },
                style = DialogStyle.WARNING,
                confirmButton = {
                    DialogWarningButton(onClick = {
                        val changedTxns = mutableListOf<Transaction>()
                        transactions.forEachIndexed { i, txn ->
                            if (txn.linkedIncomeSourceId == updated.id && !txn.deleted) {
                                transactions[i] = txn.copy(
                                    linkedIncomeSourceAmount = updated.amount,
                                )
                                changedTxns.add(transactions[i])
                            }
                        }
                        saveTransactions(changedTxns)
                        onSetPendingISAmountUpdate(null)
                        recomputeCash()
                    }) { Text(strings.common.applyToPastConfirm) }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = {
                        onSetPendingISAmountUpdate(null)
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
                onDismissRequest = { onSetShowBackupPasswordDialog(false) },
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
                                onSetBackupsEnabled(true)
                                backupPrefs.edit().putBoolean("backups_enabled", true).apply()
                                onSetShowBackupPasswordDialog(false)
                            }
                        }
                    }) { Text(strings.settings.enableBackups) }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = { onSetShowBackupPasswordDialog(false) }) {
                        Text(strings.common.cancel)
                    }
                }
            )
        }

        // Disable backup confirmation dialog
        if (showDisableBackupDialog) {
            var confirmDelete by remember { mutableStateOf(false) }
            AdAwareAlertDialog(
                onDismissRequest = { onSetShowDisableBackupDialog(false); confirmDelete = false },
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
                            onSetBackupsEnabled(false)
                            backupPrefs.edit().putBoolean("backups_enabled", false).apply()
                            onSetShowDisableBackupDialog(false); confirmDelete = false
                        }) { Text(strings.settings.confirmDeleteBtn) }
                    }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = {
                        if (confirmDelete) { confirmDelete = false } else {
                            onSetBackupsEnabled(false)
                            backupPrefs.edit().putBoolean("backups_enabled", false).apply()
                            onSetShowDisableBackupDialog(false)
                        }
                    }) { Text(if (confirmDelete) strings.common.back else strings.settings.keepFilesBtn) }
                }
            )
        }

        // Save Photos dialog
        if (showSavePhotosDialog) {
            AdAwareAlertDialog(
                onDismissRequest = { onSetShowSavePhotosDialog(false) },
                title = { Text("Save Photos") },
                style = DialogStyle.DEFAULT,
                text = {
                    Text("Photos are already backed up in encrypted backups if Automatic Backups is enabled below. This will save unencrypted copies of all receipt photos to Download/BudgeTrak/photos/ on your device if you need them for other purposes.")
                },
                confirmButton = {
                    DialogPrimaryButton(onClick = {
                        onSetShowSavePhotosDialog(false)
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
                    DialogSecondaryButton(onClick = { onSetShowSavePhotosDialog(false) }) {
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

            AdAwareDialog(onDismissRequest = { if (!restoring) onSetShowRestoreDialog(false) }) {
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
                                    DialogSecondaryButton(onClick = { onSetShowRestoreDialog(false) }) { Text(strings.common.cancel) }
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
                                                    onSetShowRestoreDialog(false)
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
    }

    // ── Extracted screen branches to reduce ART instruction count ──

    @Composable
    private fun SettingsScreenBranch(
        currencySymbol: String,
        appLanguage: String,
        onSetAppLanguage: (String) -> Unit,
        prefs: SharedPreferences,
        categories: SnapshotStateList<Category>,
        saveCategories: (List<Category>?) -> Unit,
        onSetCurrentScreen: (String) -> Unit,
        onSetQuickStartStep: (QuickStartStep?) -> Unit,
        matchDays: Int,
        onSetMatchDays: (Int) -> Unit,
        isSyncConfigured: Boolean,
        sharedSettings: SharedSettings,
        onSetSharedSettings: (SharedSettings) -> Unit,
        context: android.content.Context,
        localDeviceId: String,
        onSetLastSyncActivity: (Long) -> Unit,
        matchPercent: Double,
        onSetMatchPercent: (Double) -> Unit,
        matchDollar: Int,
        onSetMatchDollar: (Int) -> Unit,
        matchChars: Int,
        onSetMatchChars: (Int) -> Unit,
        chartPalette: String,
        onSetChartPalette: (String) -> Unit,
        budgetPeriod: BudgetPeriod,
        weekStartSunday: Boolean,
        onSetWeekStartSunday: (Boolean) -> Unit,
        onSetCurrencySymbol: (String) -> Unit,
        isSyncAdmin: Boolean,
        showDecimals: Boolean,
        onSetShowDecimals: (Boolean) -> Unit,
        dateFormatPattern: String,
        onSetDateFormatPattern: (String) -> Unit,
        isPaidUser: Boolean,
        onSetIsPaidUser: (Boolean) -> Unit,
        isSubscriber: Boolean,
        onSetIsSubscriber: (Boolean) -> Unit,
        subscriptionExpiry: Long,
        onSetSubscriptionExpiry: (Long) -> Unit,
        showWidgetLogo: Boolean,
        onSetShowWidgetLogo: (Boolean) -> Unit,
        activeCategories: List<Category>,
        activeTransactions: List<Transaction>,
        transactions: SnapshotStateList<Transaction>,
        saveTransactions: (List<Transaction>?) -> Unit,
        backupsEnabled: Boolean,
        onSetShowBackupPasswordDialog: (Boolean) -> Unit,
        onSetShowDisableBackupDialog: (Boolean) -> Unit,
        backupFrequencyWeeks: Int,
        onSetBackupFrequencyWeeks: (Int) -> Unit,
        backupPrefs: SharedPreferences,
        backupRetention: Int,
        onSetBackupRetention: (Int) -> Unit,
        lastBackupDate: String?,
        onSetLastBackupDate: (String?) -> Unit,
        onSetShowRestoreDialog: (Boolean) -> Unit,
        onSetShowSavePhotosDialog: (Boolean) -> Unit,
        coroutineScope: CoroutineScope,
        syncGroupId: String?,
        toastState: AppToastState,
    ) {
        SettingsScreen(
            currencySymbol = currencySymbol,
            appLanguage = appLanguage,
            onLanguageChange = { lang ->
                onSetAppLanguage(lang)
                prefs.edit().putString("appLanguage", lang).apply()
                val newStrings: AppStrings = if (lang == "es") SpanishStrings else EnglishStrings
                val changedCats = mutableListOf<Category>()
                categories.forEachIndexed { idx, cat ->
                    if (cat.tag.isNotEmpty()) {
                        val allKnown = getAllKnownNamesForTag(cat.tag)
                        if (cat.name in allKnown) {
                            val newName = getDefaultCategoryName(cat.tag, newStrings)
                            if (newName != null && newName != cat.name) {
                                categories[idx] = cat.copy(name = newName)
                                changedCats.add(categories[idx])
                            }
                        }
                    }
                }
                if (changedCats.isNotEmpty()) saveCategories(changedCats)
            },
            onNavigateToBudgetConfig = { onSetCurrentScreen("budget_config") },
            onNavigateToFamilySync = { onSetCurrentScreen("family_sync") },
            onNavigateToQuickStart = {
                onSetQuickStartStep(QuickStartStep.WELCOME)
                onSetCurrentScreen("main")
            },
            matchDays = matchDays,
            onMatchDaysChange = {
                onSetMatchDays(it); prefs.edit().putInt("matchDays", it).apply()
                if (isSyncConfigured) {
                    val updated = sharedSettings.copy(matchDays = it, lastChangedBy = localDeviceId)
                    onSetSharedSettings(updated)
                    SharedSettingsRepository.save(context, updated)
                    SyncWriteHelper.pushSharedSettings(updated)
                    onSetLastSyncActivity(System.currentTimeMillis())
                }
            },
            matchPercent = matchPercent,
            onMatchPercentChange = {
                onSetMatchPercent(it); prefs.edit().putString("matchPercent", it.toString()).apply()
                if (isSyncConfigured) {
                    val updated = sharedSettings.copy(matchPercent = it, lastChangedBy = localDeviceId)
                    onSetSharedSettings(updated)
                    SharedSettingsRepository.save(context, updated)
                    SyncWriteHelper.pushSharedSettings(updated)
                    onSetLastSyncActivity(System.currentTimeMillis())
                }
            },
            matchDollar = matchDollar,
            onMatchDollarChange = {
                onSetMatchDollar(it); prefs.edit().putInt("matchDollar", it).apply()
                if (isSyncConfigured) {
                    val updated = sharedSettings.copy(matchDollar = it, lastChangedBy = localDeviceId)
                    onSetSharedSettings(updated)
                    SharedSettingsRepository.save(context, updated)
                    SyncWriteHelper.pushSharedSettings(updated)
                    onSetLastSyncActivity(System.currentTimeMillis())
                }
            },
            matchChars = matchChars,
            onMatchCharsChange = {
                onSetMatchChars(it); prefs.edit().putInt("matchChars", it).apply()
                if (isSyncConfigured) {
                    val updated = sharedSettings.copy(matchChars = it, lastChangedBy = localDeviceId)
                    onSetSharedSettings(updated)
                    SharedSettingsRepository.save(context, updated)
                    SyncWriteHelper.pushSharedSettings(updated)
                    onSetLastSyncActivity(System.currentTimeMillis())
                }
            },
            chartPalette = chartPalette,
            onChartPaletteChange = { onSetChartPalette(it); prefs.edit().putString("chartPalette", it).apply() },
            budgetPeriod = budgetPeriod.name,
            weekStartSunday = weekStartSunday,
            onWeekStartChange = {
                onSetWeekStartSunday(it); prefs.edit().putBoolean("weekStartSunday", it).apply()
                if (isSyncConfigured) {
                    val updated = sharedSettings.copy(weekStartSunday = it, lastChangedBy = localDeviceId)
                    onSetSharedSettings(updated)
                    SharedSettingsRepository.save(context, updated)
                    SyncWriteHelper.pushSharedSettings(updated)
                    onSetLastSyncActivity(System.currentTimeMillis())
                }
            },
            onCurrencyChange = {
                onSetCurrencySymbol(it)
                prefs.edit().putString("currencySymbol", it).apply()
                if (isSyncConfigured) {
                    val updated = sharedSettings.copy(currency = it, lastChangedBy = localDeviceId)
                    onSetSharedSettings(updated)
                    SharedSettingsRepository.save(context, updated)
                    SyncWriteHelper.pushSharedSettings(updated)
                    onSetLastSyncActivity(System.currentTimeMillis())
                }
            },
            isSyncConfigured = isSyncConfigured,
            isAdmin = isSyncAdmin,
            showDecimals = showDecimals,
            onDecimalsChange = {
                onSetShowDecimals(it)
                prefs.edit().putBoolean("showDecimals", it).apply()
            },
            dateFormatPattern = dateFormatPattern,
            onDateFormatChange = {
                onSetDateFormatPattern(it)
                prefs.edit().putString("dateFormatPattern", it).apply()
            },
            isPaidUser = isPaidUser || isSubscriber,
            onPaidUserChange = { newValue ->
                onSetIsPaidUser(newValue)
                prefs.edit().putBoolean("isPaidUser", newValue).apply()
                com.syncbudget.app.widget.BudgetWidgetProvider.updateAllWidgets(context)
            },
            isSubscriber = isSubscriber,
            onSubscriberChange = { newValue ->
                onSetIsSubscriber(newValue)
                prefs.edit().putBoolean("isSubscriber", newValue).apply()
            },
            subscriptionExpiry = subscriptionExpiry,
            onSubscriptionExpiryChange = { newValue ->
                onSetSubscriptionExpiry(newValue)
                prefs.edit().putLong("subscriptionExpiry", newValue).apply()
            },
            showWidgetLogo = showWidgetLogo,
            onWidgetLogoChange = { newValue ->
                onSetShowWidgetLogo(newValue)
                prefs.edit().putBoolean("showWidgetLogo", newValue).apply()
                com.syncbudget.app.widget.BudgetWidgetProvider.updateAllWidgets(context)
            },
            categories = activeCategories,
            transactions = activeTransactions,
            onAddCategory = { cat ->
                val added = cat.copy(
                    deviceId = localDeviceId,
                )
                categories.add(added)
                saveCategories(listOf(added))
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
                    saveCategories(listOf(categories[idx]))
                }
            },
            onDeleteCategory = { cat ->
                val idx = categories.indexOfFirst { it.id == cat.id }
                if (idx >= 0) {
                    categories[idx] = categories[idx].copy(deleted = true)
                    saveCategories(listOf(categories[idx]))
                }
            },
            onToggleCharted = { cat ->
                val idx = categories.indexOfFirst { it.id == cat.id }
                if (idx >= 0) {
                    categories[idx] = categories[idx].copy(
                        charted = !categories[idx].charted,
                    )
                    saveCategories(listOf(categories[idx]))
                }
            },
            onToggleWidgetVisible = { cat ->
                val idx = categories.indexOfFirst { it.id == cat.id }
                if (idx >= 0) {
                    categories[idx] = categories[idx].copy(
                        widgetVisible = !categories[idx].widgetVisible,
                    )
                    saveCategories(listOf(categories[idx]))
                }
            },
            onReassignCategory = { fromId, toId ->
                val changedTxns = mutableListOf<Transaction>()
                transactions.forEachIndexed { index, txn ->
                    val updatedAmts = txn.categoryAmounts.map { ca ->
                        if (ca.categoryId == fromId) {
                            // Check if toId already exists in this transaction
                            val existingTo = txn.categoryAmounts.find { it.categoryId == toId }
                            if (existingTo != null) ca.copy(categoryId = -1) // mark for merge
                            else ca.copy(categoryId = toId)
                        } else ca
                    }
                    // Merge amounts if both fromId and toId existed
                    val markedForMerge = updatedAmts.find { it.categoryId == -1 }
                    val finalAmounts = if (markedForMerge != null) {
                        val mergedAmount = (updatedAmts.find { it.categoryId == toId }?.amount ?: 0.0) + markedForMerge.amount
                        updatedAmts.filter { it.categoryId != -1 && it.categoryId != toId } +
                            CategoryAmount(toId, mergedAmount)
                    } else updatedAmts
                    if (finalAmounts != txn.categoryAmounts) {
                        transactions[index] = txn.copy(
                            categoryAmounts = finalAmounts,
                        )
                        changedTxns.add(transactions[index])
                    }
                }
                saveTransactions(changedTxns)
            },
            receiptPruneAgeDays = sharedSettings.receiptPruneAgeDays,
            onReceiptPruneChange = { days ->
                val updated = sharedSettings.copy(
                    receiptPruneAgeDays = days,
                    lastChangedBy = localDeviceId
                )
                onSetSharedSettings(updated)
                SharedSettingsRepository.save(context, updated)
                SyncWriteHelper.pushSharedSettings(updated)
                onSetLastSyncActivity(System.currentTimeMillis())
            },
            receiptCacheSize = remember(isPaidUser) {
                com.syncbudget.app.data.sync.ReceiptManager.getTotalStorageBytes(context)
            },
            backupsEnabled = backupsEnabled,
            onBackupsEnabledChange = { enabled ->
                if (enabled) {
                    onSetShowBackupPasswordDialog(true)
                } else {
                    onSetShowDisableBackupDialog(true)
                }
            },
            backupFrequencyWeeks = backupFrequencyWeeks,
            onBackupFrequencyChange = { weeks ->
                onSetBackupFrequencyWeeks(weeks)
                backupPrefs.edit().putInt("backup_frequency_weeks", weeks).apply()
            },
            backupRetention = backupRetention,
            onBackupRetentionChange = { ret ->
                onSetBackupRetention(ret)
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
                            onSetLastBackupDate(backupPrefs.getString("last_backup_date", null))
                        }
                    }
                }
            },
            onRestoreBackup = { onSetShowRestoreDialog(true) },
            onSavePhotos = { onSetShowSavePhotosDialog(true) },
            onDumpDebug = {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val devName = com.syncbudget.app.data.sync.GroupManager.getDeviceName(context)
                        val sanitized = DiagDumpBuilder.sanitizeDeviceName(devName)
                        val supportDir = BackupManager.getSupportDir()

                        // 1. Build fresh diag dump and save locally
                        val diagText = DiagDumpBuilder.build(context)
                        DiagDumpBuilder.writeDiagToMediaStore(context, "sync_diag.txt", diagText)
                        if (sanitized.isNotEmpty()) {
                            DiagDumpBuilder.writeDiagToMediaStore(context, "sync_diag_${sanitized}.txt", diagText)
                        }

                        // 2. Read sync_log and save with device name
                        val syncLogFile = java.io.File(supportDir, "sync_log.txt")
                        val syncLogText = if (syncLogFile.exists()) syncLogFile.readText() else ""
                        if (sanitized.isNotEmpty() && syncLogText.isNotEmpty()) {
                            DiagDumpBuilder.writeDiagToMediaStore(context, "sync_log_${sanitized}.txt", syncLogText)
                        }

                        // 2b. Capture logcat to file (app can read its own logs) — debug only
                        if (com.syncbudget.app.BuildConfig.DEBUG) {
                            try {
                                val logcatProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "1000"))
                                val logcatText = logcatProcess.inputStream.bufferedReader().readText()
                                logcatProcess.waitFor()
                                DiagDumpBuilder.writeDiagToMediaStore(context, "logcat_${sanitized}.txt", logcatText)
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
                                        val rName = DiagDumpBuilder.sanitizeDeviceName(remote.deviceName)
                                        if (remote.syncLog.isNotEmpty()) DiagDumpBuilder.writeDiagToMediaStore(context, "sync_log_${rName}.txt", remote.syncLog)
                                        if (remote.syncDiag.isNotEmpty()) DiagDumpBuilder.writeDiagToMediaStore(context, "sync_diag_${rName}.txt", remote.syncDiag)
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
            onBack = { onSetCurrentScreen("main") },
            onHelpClick = { onSetCurrentScreen("settings_help") }
        )
    }

    @Composable
    private fun TransactionsScreenBranch(
        activeTransactions: List<Transaction>,
        currencySymbol: String,
        dateFormatPattern: String,
        activeCategories: List<Category>,
        isPaidUser: Boolean,
        isSubscriber: Boolean,
        activeRecurringExpenses: List<RecurringExpense>,
        activeAmortizationEntries: List<AmortizationEntry>,
        activeIncomeSources: List<IncomeSource>,
        activeSavingsGoals: List<SavingsGoal>,
        matchDays: Int,
        matchPercent: Double,
        matchDollar: Int,
        matchChars: Int,
        chartPalette: String,
        sharedSettings: SharedSettings,
        onSetSharedSettings: (SharedSettings) -> Unit,
        isSyncConfigured: Boolean,
        onSetIsSyncConfigured: (Boolean) -> Unit,
        syncDevices: List<com.syncbudget.app.data.sync.DeviceInfo>,
        onSetSyncDevices: (List<com.syncbudget.app.data.sync.DeviceInfo>) -> Unit,
        localDeviceId: String,
        addTransactionWithBudgetEffect: (Transaction) -> Unit,
        transactions: SnapshotStateList<Transaction>,
        savingsGoals: SnapshotStateList<SavingsGoal>,
        saveSavingsGoals: (List<SavingsGoal>?) -> Unit,
        saveTransactions: (List<Transaction>?) -> Unit,
        recomputeCash: () -> Unit,
        coroutineScope: CoroutineScope,
        context: android.content.Context,
        categories: SnapshotStateList<Category>,
        recurringExpenses: SnapshotStateList<RecurringExpense>,
        incomeSources: SnapshotStateList<IncomeSource>,
        amortizationEntries: SnapshotStateList<AmortizationEntry>,
        periodLedger: SnapshotStateList<com.syncbudget.app.data.sync.PeriodLedgerEntry>,
        prefs: SharedPreferences,
        onSetCurrencySymbol: (String) -> Unit,
        onSetDigitCount: (Int) -> Unit,
        onSetShowDecimals: (Boolean) -> Unit,
        onSetDateFormatPattern: (String) -> Unit,
        onSetChartPalette: (String) -> Unit,
        onSetAppLanguage: (String) -> Unit,
        onSetBudgetPeriod: (BudgetPeriod) -> Unit,
        onSetResetHour: (Int) -> Unit,
        onSetResetDayOfWeek: (Int) -> Unit,
        onSetResetDayOfMonth: (Int) -> Unit,
        onSetIsManualBudgetEnabled: (Boolean) -> Unit,
        onSetManualBudgetAmount: (Double) -> Unit,
        onSetAvailableCash: (Double) -> Unit,
        onSetBudgetStartDate: (LocalDate?) -> Unit,
        onSetLastRefreshDate: (LocalDate?) -> Unit,
        onSetWeekStartSunday: (Boolean) -> Unit,
        onSetMatchDays: (Int) -> Unit,
        onSetMatchPercent: (Double) -> Unit,
        onSetMatchDollar: (Int) -> Unit,
        onSetMatchChars: (Int) -> Unit,
        syncGroupId: String?,
        onSetSyncGroupId: (String?) -> Unit,
        isSyncAdmin: Boolean,
        onSetIsSyncAdmin: (Boolean) -> Unit,
        syncStatus: String,
        onSetSyncStatus: (String) -> Unit,
        lastSyncActivity: Long,
        onSetLastSyncActivity: (Long) -> Unit,
        onSetGeneratedPairingCode: (String?) -> Unit,
        saveCategories: (List<Category>?) -> Unit,
        saveRecurringExpenses: (List<RecurringExpense>?) -> Unit,
        saveIncomeSources: (List<IncomeSource>?) -> Unit,
        saveAmortizationEntries: (List<AmortizationEntry>?) -> Unit,
        budgetPeriod: BudgetPeriod,
        incomeMode: IncomeMode,
        onSetCurrentScreen: (String) -> Unit,
    ) {
        TransactionsScreen(
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
                            saveSavingsGoals(listOf(savingsGoals[gIdx]))
                        }
                    } else if (wasLinkedToGoal == null && nowLinkedToGoal != null) {
                        // Newly linked: deduct from goal
                        val gIdx = savingsGoals.indexOfFirst { it.id == nowLinkedToGoal }
                        if (gIdx >= 0) {
                            val g = savingsGoals[gIdx]
                            savingsGoals[gIdx] = g.copy(
                                totalSavedSoFar = maxOf(0.0, g.totalSavedSoFar - updated.amount),
                            )
                            saveSavingsGoals(listOf(savingsGoals[gIdx]))
                        }
                    }
                    saveTransactions(listOf(transactions[index]))
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
                            saveSavingsGoals(listOf(savingsGoals[gIdx]))
                        }
                    }
                    transactions[idx] = t.copy(
                        deleted = true
                    )
                    saveTransactions(listOf(transactions[idx]))
                    // Clean up receipt photos (local + cloud)
                    val receiptIds = listOfNotNull(t.receiptId1, t.receiptId2, t.receiptId3, t.receiptId4, t.receiptId5)
                    if (receiptIds.isNotEmpty()) {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            for (rid in receiptIds) {
                                com.syncbudget.app.data.sync.ReceiptManager.deleteReceiptFull(context, rid)
                            }
                        }
                    }
                }
                recomputeCash()
            },
            onDeleteTransactions = { ids ->
                val changedGoals = mutableListOf<SavingsGoal>()
                val changedTxns = mutableListOf<Transaction>()
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
                                changedGoals.add(savingsGoals[gIdx])
                            }
                        }
                        transactions[index] = txn.copy(
                            deleted = true,
                        )
                        changedTxns.add(transactions[index])
                    }
                }
                if (changedGoals.isNotEmpty()) saveSavingsGoals(changedGoals)
                saveTransactions(changedTxns)
                recomputeCash()
                // Clean up receipt photos for all deleted transactions
                val deletedReceiptIds = ids.flatMap { id ->
                    val txn = transactions.find { it.id == id } ?: return@flatMap emptyList()
                    listOfNotNull(txn.receiptId1, txn.receiptId2, txn.receiptId3, txn.receiptId4, txn.receiptId5)
                }
                if (deletedReceiptIds.isNotEmpty()) {
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        for (rid in deletedReceiptIds) {
                            com.syncbudget.app.data.sync.ReceiptManager.deleteReceiptFull(context, rid)
                        }
                    }
                }
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

                onSetSharedSettings(SharedSettingsRepository.load(context))

                // Reload local prefs
                onSetCurrencySymbol(prefs.getString("currencySymbol", "$") ?: "$")
                onSetDigitCount(prefs.getInt("digitCount", 3))
                onSetShowDecimals(prefs.getBoolean("showDecimals", false))
                onSetDateFormatPattern(prefs.getString("dateFormatPattern", "yyyy-MM-dd") ?: "yyyy-MM-dd")
                onSetChartPalette(prefs.getString("chartPalette", "Sunset") ?: "Sunset")
                onSetAppLanguage(prefs.getString("appLanguage", "en") ?: "en")
                onSetBudgetPeriod(try { BudgetPeriod.valueOf(prefs.getString("budgetPeriod", "DAILY") ?: "DAILY") }
                                   catch (_: Exception) { BudgetPeriod.DAILY })
                onSetResetHour(prefs.getInt("resetHour", 0))
                onSetResetDayOfWeek(prefs.getInt("resetDayOfWeek", 7))
                onSetResetDayOfMonth(prefs.getInt("resetDayOfMonth", 1))
                onSetIsManualBudgetEnabled(prefs.getBoolean("isManualBudgetEnabled", false))
                onSetManualBudgetAmount(prefs.getDoubleCompat("manualBudgetAmount"))
                onSetAvailableCash(prefs.getDoubleCompat("availableCash"))
                onSetBudgetStartDate(prefs.getString("budgetStartDate", null)?.let { LocalDate.parse(it) })
                onSetLastRefreshDate(prefs.getString("lastRefreshDate", null)?.let { LocalDate.parse(it) })
                onSetWeekStartSunday(prefs.getBoolean("weekStartSunday", true))
                onSetMatchDays(prefs.getInt("matchDays", 7))
                onSetMatchPercent(prefs.getDoubleCompat("matchPercent", 1.0))
                onSetMatchDollar(prefs.getInt("matchDollar", 1))
                onSetMatchChars(prefs.getInt("matchChars", 5))

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
                    saveTransactions(null)
                    categories.forEachIndexed { i, c ->
                        categories[i] = c.copy(deviceId = "")
                    }
                    saveCategories(null)
                    recurringExpenses.forEachIndexed { i, r ->
                        recurringExpenses[i] = r.copy(deviceId = "")
                    }
                    saveRecurringExpenses(null)
                    incomeSources.forEachIndexed { i, s ->
                        incomeSources[i] = s.copy(deviceId = "")
                    }
                    saveIncomeSources(null)
                    savingsGoals.forEachIndexed { i, g ->
                        savingsGoals[i] = g.copy(deviceId = "")
                    }
                    saveSavingsGoals(null)
                    amortizationEntries.forEachIndexed { i, e ->
                        amortizationEntries[i] = e.copy(deviceId = "")
                    }
                    saveAmortizationEntries(null)

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
                                .set(mapOf("createdAt" to System.currentTimeMillis(), "lastActivity" to System.currentTimeMillis()))
                                .await()
                            // Register admin device
                            FirestoreService.registerDevice(
                                newGroup.groupId, localDeviceId,
                                GroupManager.getDeviceName(context), isAdmin = true
                            )
                            onSetIsSyncConfigured(true)
                            onSetSyncGroupId(newGroup.groupId)
                            onSetIsSyncAdmin(true)
                            onSetSyncStatus("synced")
                            onSetLastSyncActivity(0L)
                            onSetSyncDevices(emptyList())
                            onSetGeneratedPairingCode(null)
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
                    saveIncomeSources(listOf(incomeSources[idx]))
                }
            },
            onAddAmortization = { entry ->
                val added = entry.copy(
                    deviceId = localDeviceId,
                )
                amortizationEntries.add(added)
                saveAmortizationEntries(listOf(added))
            },
            onDeleteAmortization = { entry ->
                val idx = amortizationEntries.indexOfFirst { it.id == entry.id }
                if (idx >= 0) {
                    amortizationEntries[idx] = amortizationEntries[idx].copy(
                        deleted = true,
                    )
                    saveAmortizationEntries(listOf(amortizationEntries[idx]))
                }
            },
            onBack = { onSetCurrentScreen("main") },
            onHelpClick = { onSetCurrentScreen("transactions_help") }
        )
    }

    @Composable
    private fun FamilySyncScreenBranch(
        isSyncConfigured: Boolean,
        onSetIsSyncConfigured: (Boolean) -> Unit,
        isSubscriber: Boolean,
        syncGroupId: String?,
        onSetSyncGroupId: (String?) -> Unit,
        isSyncAdmin: Boolean,
        onSetIsSyncAdmin: (Boolean) -> Unit,
        localDeviceId: String,
        syncDevices: List<com.syncbudget.app.data.sync.DeviceInfo>,
        onSetSyncDevices: (List<com.syncbudget.app.data.sync.DeviceInfo>) -> Unit,
        syncStatus: String,
        onSetSyncStatus: (String) -> Unit,
        lastSyncTimeDisplay: String?,
        sharedSettings: SharedSettings,
        onSetSharedSettings: (SharedSettings) -> Unit,
        context: android.content.Context,
        onSetLastSyncActivity: (Long) -> Unit,
        transactions: SnapshotStateList<Transaction>,
        pendingAdminClaim: AdminClaim?,
        onSetPendingAdminClaim: (AdminClaim?) -> Unit,
        coroutineScope: CoroutineScope,
        syncErrorMessage: String?,
        onSetSyncErrorMessage: (String?) -> Unit,
        syncProgressMessage: String?,
        onSetSyncProgressMessage: (String?) -> Unit,
        currencySymbol: String,
        budgetPeriod: BudgetPeriod,
        budgetStartDate: LocalDate?,
        isManualBudgetEnabled: Boolean,
        manualBudgetAmount: Double,
        weekStartSunday: Boolean,
        resetDayOfWeek: Int,
        resetDayOfMonth: Int,
        resetHour: Int,
        matchDays: Int,
        matchPercent: Double,
        matchDollar: Int,
        matchChars: Int,
        incomeMode: IncomeMode,
        categories: SnapshotStateList<Category>,
        recurringExpenses: SnapshotStateList<RecurringExpense>,
        incomeSources: SnapshotStateList<IncomeSource>,
        savingsGoals: SnapshotStateList<SavingsGoal>,
        amortizationEntries: SnapshotStateList<AmortizationEntry>,
        periodLedger: SnapshotStateList<com.syncbudget.app.data.sync.PeriodLedgerEntry>,
        saveTransactions: (List<Transaction>?) -> Unit,
        saveCategories: (List<Category>?) -> Unit,
        saveSavingsGoals: (List<SavingsGoal>?) -> Unit,
        saveAmortizationEntries: (List<AmortizationEntry>?) -> Unit,
        saveRecurringExpenses: (List<RecurringExpense>?) -> Unit,
        saveIncomeSources: (List<IncomeSource>?) -> Unit,
        doSyncNow: () -> Unit,
        generatedPairingCode: String?,
        onSetGeneratedPairingCode: (String?) -> Unit,
        syncPrefs: SharedPreferences,
        toastState: AppToastState,
        onSetCurrentScreen: (String) -> Unit,
    ) {
        FamilySyncScreen(
            isConfigured = isSyncConfigured,
            isSubscriber = isSubscriber,
            groupId = syncGroupId,
            isAdmin = isSyncAdmin,
            deviceName = GroupManager.getDeviceName(context),
            localDeviceId = localDeviceId,
            devices = syncDevices,
            syncStatus = syncStatus,
            lastSyncTime = lastSyncTimeDisplay,
            familyTimezone = sharedSettings.familyTimezone,
            onTimezoneChange = { tz ->
                val updated = sharedSettings.copy(
                    familyTimezone = tz,
                    lastChangedBy = localDeviceId
                )
                onSetSharedSettings(updated)
                SharedSettingsRepository.save(context, updated)
                SyncWriteHelper.pushSharedSettings(updated)
                onSetLastSyncActivity(System.currentTimeMillis())
            },
            showAttribution = sharedSettings.showAttribution,
            onShowAttributionChange = { enabled ->
                val updated = sharedSettings.copy(
                    showAttribution = enabled,
                    lastChangedBy = localDeviceId
                )
                onSetSharedSettings(updated)
                SharedSettingsRepository.save(context, updated)
                SyncWriteHelper.pushSharedSettings(updated)
                onSetLastSyncActivity(System.currentTimeMillis())
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
                val updated = sharedSettings.copy(
                    deviceRoster = json,
                    lastChangedBy = localDeviceId
                )
                onSetSharedSettings(updated)
                SharedSettingsRepository.save(context, updated)
                SyncWriteHelper.pushSharedSettings(updated)
                onSetLastSyncActivity(System.currentTimeMillis())
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
                    val updated = sharedSettings.copy(
                        deviceRoster = org.json.JSONObject(pruned).toString(),
                        lastChangedBy = localDeviceId
                    )
                    onSetSharedSettings(updated)
                    SharedSettingsRepository.save(context, updated)
                    SyncWriteHelper.pushSharedSettings(updated)
                    onSetLastSyncActivity(System.currentTimeMillis())
                }
            },
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
                        onSetPendingAdminClaim(FirestoreService.getAdminClaim(gId))
                    } catch (_: Exception) {}
                }
            },
            onObjectClaim = {
                coroutineScope.launch {
                    try {
                        val gId = syncGroupId ?: return@launch
                        FirestoreService.addObjection(gId, localDeviceId)
                        onSetPendingAdminClaim(FirestoreService.getAdminClaim(gId))
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
                        onSetSyncGroupId(info.groupId)
                        onSetIsSyncAdmin(true)
                        onSetIsSyncConfigured(true)
                        onSetSyncStatus("syncing")

                        // Initialize group doc BEFORE registering device
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        db.collection("groups").document(info.groupId)
                            .set(mapOf("createdAt" to System.currentTimeMillis(), "lastActivity" to System.currentTimeMillis()))
                            .await()
                        // Register this device as admin
                        FirestoreService.registerDevice(
                            info.groupId,
                            localDeviceId,
                            nickname,
                            isAdmin = true
                        )
                        // Initialize SharedSettings from current app_prefs
                        val newSettings = SharedSettings(
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
                            incomeMode = incomeMode.name,
                            lastChangedBy = localDeviceId,
                        )
                        onSetSharedSettings(newSettings)
                        SharedSettingsRepository.save(context, newSettings)

                        // Stamp all existing data with deviceId so they push on first sync.
                        // Only stamp records that have no deviceId (empty).
                        transactions.forEachIndexed { i, t ->
                            if (t.deviceId.isEmpty()) {
                                transactions[i] = t.copy(deviceId = localDeviceId)
                            }
                        }
                        saveTransactions(null)
                        categories.forEachIndexed { i, c ->
                            if (c.deviceId.isEmpty()) {
                                categories[i] = c.copy(deviceId = localDeviceId)
                            }
                        }
                        saveCategories(null)
                        savingsGoals.forEachIndexed { i, g ->
                            if (g.deviceId.isEmpty()) {
                                savingsGoals[i] = g.copy(deviceId = localDeviceId)
                            }
                        }
                        saveSavingsGoals(null)
                        amortizationEntries.forEachIndexed { i, e ->
                            if (e.deviceId.isEmpty()) {
                                amortizationEntries[i] = e.copy(deviceId = localDeviceId)
                            }
                        }
                        saveAmortizationEntries(null)
                        recurringExpenses.forEachIndexed { i, r ->
                            if (r.deviceId.isEmpty()) {
                                recurringExpenses[i] = r.copy(deviceId = localDeviceId)
                            }
                        }
                        saveRecurringExpenses(null)
                        incomeSources.forEachIndexed { i, s ->
                            if (s.deviceId.isEmpty()) {
                                incomeSources[i] = s.copy(deviceId = localDeviceId)
                            }
                        }
                        saveIncomeSources(null)

                        onSetSyncStatus("synced")
                    } catch (_: Exception) {
                        onSetSyncStatus("error")
                    }
                }
            },
            onJoinGroup = { code, nickname ->
                coroutineScope.launch {
                    try {
                        GroupManager.setDeviceName(context, nickname)
                        val success = GroupManager.joinGroup(context, code)
                        if (success) {
                            // Clear local data — the group's data will arrive
                            // via Firestore listeners once they start.
                            transactions.clear()
                            recurringExpenses.clear()
                            incomeSources.clear()
                            savingsGoals.clear()
                            amortizationEntries.clear()
                            categories.clear()
                            periodLedger.clear()
                            TransactionRepository.save(context, emptyList())
                            RecurringExpenseRepository.save(context, emptyList())
                            IncomeSourceRepository.save(context, emptyList())
                            SavingsGoalRepository.save(context, emptyList())
                            AmortizationRepository.save(context, emptyList())
                            CategoryRepository.save(context, emptyList())
                            PeriodLedgerRepository.save(context, emptyList())

                            // Mark migrations as done so we don't push empty data
                            syncPrefs.edit()
                                .putBoolean("migration_native_docs_done", true)
                                .putBoolean("migration_per_field_enc_done", true)
                                .apply()

                            onSetSyncGroupId(GroupManager.getGroupId(context))
                            onSetIsSyncAdmin(false)
                            onSetIsSyncConfigured(true)
                            onSetSyncStatus("synced")
                        } else {
                            onSetSyncErrorMessage("Invalid or expired pairing code")
                        }
                    } catch (e: Exception) {
                        onSetSyncStatus("error")
                        onSetSyncErrorMessage(e.message)
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
                    onSetIsSyncConfigured(false)
                    onSetSyncGroupId(null)
                    onSetIsSyncAdmin(false)
                    onSetSyncStatus("off")
                    onSetLastSyncActivity(0L)
                    onSetSyncDevices(emptyList())
                    onSetPendingAdminClaim(null)
                    onSetSyncErrorMessage(null)
                }
            },
            onDissolveGroup = {
                val gId = syncGroupId
                if (gId != null) {
                    onSetSyncStatus("syncing")
                    coroutineScope.launch {
                        try {
                            android.util.Log.d("Sync", "Dissolving group $gId")
                            GroupManager.dissolveGroup(context, gId) { msg ->
                                onSetSyncProgressMessage(msg)
                            }
                            android.util.Log.d("Sync", "Group dissolved successfully")
                            syncPrefs.edit()
                                .remove("catIdRemap")
                                .remove("lastSuccessfulSync")
                                .apply()
                            onSetIsSyncConfigured(false)
                            onSetSyncGroupId(null)
                            onSetIsSyncAdmin(false)
                            onSetSyncStatus("off")
                            onSetLastSyncActivity(0L)
                            onSetSyncDevices(emptyList())
                            onSetPendingAdminClaim(null)
                            onSetSyncErrorMessage(null)
                            onSetSyncProgressMessage(null)
                        } catch (e: Exception) {
                            android.util.Log.e("Sync", "Dissolve failed, falling back to local leave", e)
                            // If Firestore is unreachable (group already dissolved, network down),
                            // fall back to local-only leave so user isn't stuck.
                            try {
                                GroupManager.leaveGroup(context, localOnly = true)
                            } catch (_: Exception) {}
                            syncPrefs.edit()
                                .remove("catIdRemap")
                                .remove("lastSuccessfulSync")
                                .apply()
                            onSetIsSyncConfigured(false)
                            onSetSyncGroupId(null)
                            onSetIsSyncAdmin(false)
                            onSetSyncStatus("off")
                            onSetLastSyncActivity(0L)
                            onSetSyncDevices(emptyList())
                            onSetPendingAdminClaim(null)
                            onSetSyncErrorMessage(null)
                            onSetSyncProgressMessage(null)
                            toastState.show("Group left locally (server unreachable)")
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
                            // No need to push data — the new device receives
                            // everything via Firestore listeners on join.
                            onSetGeneratedPairingCode(GroupManager.generatePairingCode(context, gId, key))
                        } catch (_: Exception) {}
                    }
                }
            },
            generatedPairingCode = generatedPairingCode,
            onDismissPairingCode = { onSetGeneratedPairingCode(null) },
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
                        onSetSyncDevices(GroupManager.getDevices(gId))
                        // Update permanent roster
                        val currentRoster = try {
                            val obj = org.json.JSONObject(sharedSettings.deviceRoster)
                            obj.keys().asSequence().associateWith { obj.getString(it) }.toMutableMap()
                        } catch (_: Exception) { mutableMapOf() }
                        currentRoster[targetDeviceId] = newName
                        val updated = sharedSettings.copy(
                            deviceRoster = org.json.JSONObject(currentRoster as Map<*, *>).toString(),
                            lastChangedBy = localDeviceId
                        )
                        onSetSharedSettings(updated)
                        SharedSettingsRepository.save(context, updated)
                        SyncWriteHelper.pushSharedSettings(updated)
                        onSetLastSyncActivity(System.currentTimeMillis())
                    } catch (_: Exception) {}
                }
            },
            onRemoveDevice = { targetDeviceId ->
                val gId = syncGroupId ?: return@FamilySyncScreen
                coroutineScope.launch {
                    try {
                        FirestoreService.removeDevice(gId, targetDeviceId)
                        onSetSyncDevices(GroupManager.getDevices(gId))
                    } catch (_: Exception) {}
                }
            },
            onHelpClick = { onSetCurrentScreen("family_sync_help") },
            onBack = {
                onSetGeneratedPairingCode(null)
                onSetCurrentScreen("settings")
            }
        )
    }

}
