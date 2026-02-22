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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
            var matchPercent by remember { mutableFloatStateOf(prefs.getFloat("matchPercent", 1.0f)) }
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
            var manualBudgetAmount by remember { mutableDoubleStateOf(prefs.getFloat("manualBudgetAmount", 0f).toDouble()) }
            var availableCash by remember { mutableDoubleStateOf(prefs.getFloat("availableCash", 0f).toDouble()) }
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
                    // Backward compat: check if a category with a matching name exists but no tag
                    val allNames = getAllKnownNamesForTag(def.tag)
                    val byName = loaded.indexOfFirst { it.tag.isEmpty() && it.name in allNames }
                    if (byName >= 0) {
                        loaded[byName] = loaded[byName].copy(tag = def.tag)
                        changed = true
                    } else {
                        val usedIds = loaded.map { it.id }.toSet()
                        var id: Int
                        do { id = (0..65535).random() } while (id in usedIds)
                        val name = getDefaultCategoryName(def.tag, strings) ?: def.tag
                        loaded.add(Category(id = id, name = name, iconName = def.iconName, tag = def.tag))
                        changed = true
                    }
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

            fun persistAvailableCash() {
                prefs.edit().putFloat("availableCash", availableCash.toFloat()).apply()
            }

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
                    if (isManualBudgetEnabled) {
                        manualBudgetAmount
                    } else {
                        val amortDeductions = BudgetCalculator.activeAmortizationDeductions(
                            amortizationEntries.toList().active, budgetPeriod
                        )
                        val savingsDeductions = BudgetCalculator.activeSavingsGoalDeductions(
                            savingsGoals.toList().active, budgetPeriod
                        )
                        maxOf(0.0, safeBudgetAmount - amortDeductions - savingsDeductions)
                    }
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

            // Foreground flag
            DisposableEffect(Unit) {
                syncPrefs.edit().putBoolean("isInForeground", true).apply()
                onDispose {
                    syncPrefs.edit().putBoolean("isInForeground", false).apply()
                }
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
                } catch (_: Exception) {}

                while (true) {
                    try {
                        syncStatus = "syncing"
                        val result = engine.sync(
                            transactions.toList(),
                            recurringExpenses.toList(),
                            incomeSources.toList(),
                            savingsGoals.toList(),
                            amortizationEntries.toList(),
                            categories.toList(),
                            sharedSettings
                        )
                        if (result.success) {
                            result.mergedTransactions?.let { merged ->
                                transactions.clear()
                                transactions.addAll(merged)
                                saveTransactions()
                            }
                            result.mergedRecurringExpenses?.let { merged ->
                                recurringExpenses.clear()
                                recurringExpenses.addAll(merged)
                                saveRecurringExpenses()
                            }
                            result.mergedIncomeSources?.let { merged ->
                                incomeSources.clear()
                                incomeSources.addAll(merged)
                                saveIncomeSources()
                            }
                            result.mergedSavingsGoals?.let { merged ->
                                savingsGoals.clear()
                                savingsGoals.addAll(merged)
                                saveSavingsGoals()
                            }
                            result.mergedAmortizationEntries?.let { merged ->
                                amortizationEntries.clear()
                                amortizationEntries.addAll(merged)
                                saveAmortizationEntries()
                            }
                            result.mergedCategories?.let { merged ->
                                categories.clear()
                                categories.addAll(merged)
                                saveCategories()
                            }
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
                                // Also write to app_prefs for backward compat
                                prefs.edit()
                                    .putString("currencySymbol", merged.currency)
                                    .putString("budgetPeriod", merged.budgetPeriod)
                                    .putInt("resetHour", merged.resetHour)
                                    .putInt("resetDayOfWeek", merged.resetDayOfWeek)
                                    .putInt("resetDayOfMonth", merged.resetDayOfMonth)
                                    .putBoolean("isManualBudgetEnabled", merged.isManualBudgetEnabled)
                                    .putFloat("manualBudgetAmount", merged.manualBudgetAmount.toFloat())
                                    .putBoolean("weekStartSunday", merged.weekStartSunday)
                                    .putInt("matchDays", merged.matchDays)
                                    .putFloat("matchPercent", merged.matchPercent)
                                    .putInt("matchDollar", merged.matchDollar)
                                    .putInt("matchChars", merged.matchChars)
                                    .apply()
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
                            } catch (_: Exception) {}
                        } else {
                            syncStatus = "error"
                            syncErrorMessage = result.error
                            pendingAdminClaim = result.pendingAdminClaim
                            // Handle auto-leave on removal
                            if (result.error == "removed_from_group" || result.error == "group_deleted") {
                                GroupManager.leaveGroup(context)
                                isSyncConfigured = false
                                syncGroupId = null
                                isSyncAdmin = false
                                syncStatus = "off"
                                lastSyncTime = null
                                syncDevices = emptyList()
                                pendingAdminClaim = null
                                return@LaunchedEffect
                            }
                        }
                    } catch (_: Exception) {
                        syncStatus = "error"
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
            val percentTolerance = matchPercent / 100f

            // Check if an expense transaction is already accounted for in the budget
            // (recurring expenses and amortization are built into the safe budget amount)
            fun isBudgetAccountedExpense(txn: Transaction): Boolean {
                if (txn.type != TransactionType.EXPENSE) return false
                val recurringCatId = categories.find { it.tag == "recurring" }?.id
                val amortCatId = categories.find { it.tag == "amortization" }?.id
                return txn.categoryAmounts.any {
                    it.categoryId == recurringCatId || it.categoryId == amortCatId
                }
            }

            // Helper to add a transaction with budget effects
            fun addTransactionWithBudgetEffect(txn: Transaction) {
                transactions.add(txn)
                saveTransactions()
                if (budgetStartDate != null && !txn.date.isBefore(budgetStartDate)) {
                    if (txn.type == TransactionType.EXPENSE && !isBudgetAccountedExpense(txn)) {
                        availableCash -= txn.amount
                    } else if (txn.type == TransactionType.INCOME && !txn.isBudgetIncome) {
                        availableCash += txn.amount
                    }
                    persistAvailableCash()
                }
            }

            // Matching chain for dashboard-added transactions
            fun runMatchingChain(txn: Transaction) {
                val activeTransactions = transactions.toList().active
                val activeRecurring = recurringExpenses.toList().active
                val activeAmort = amortizationEntries.toList().active
                val activeIncome = incomeSources.toList().active
                val dup = findDuplicate(txn, activeTransactions, percentTolerance, matchDollar, matchDays, matchChars)
                if (dup != null) {
                    dashPendingManualSave = txn
                    dashManualDuplicateMatch = dup
                    dashShowManualDuplicateDialog = true
                } else {
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
                }
            }

            val dateFormatter = remember(dateFormatPattern) {
                DateTimeFormatter.ofPattern(dateFormatPattern)
            }
            val existingIds = transactions.map { it.id }.toSet()
            val categoryMap = categories.associateBy { it.id }

            // Period refresh on app open
            remember {
                if (budgetStartDate != null && lastRefreshDate != null) {
                    val today = LocalDate.now()
                    val missedPeriods = BudgetCalculator.countPeriodsCompleted(lastRefreshDate!!, today, budgetPeriod)
                    if (missedPeriods > 0) {
                        availableCash += budgetAmount * missedPeriods
                        lastRefreshDate = today

                        // Record period ledger entry
                        periodLedger.add(
                            PeriodLedgerEntry(
                                periodStartDate = LocalDateTime.now(),
                                appliedAmount = budgetAmount,
                                clockAtReset = lamportClock.value
                            )
                        )
                        savePeriodLedger()

                        // Update savings goals totalSavedSoFar for non-paused, non-complete items
                        if (!isManualBudgetEnabled) {
                            for (period in 0 until missedPeriods) {
                                savingsGoals.forEachIndexed { idx, goal ->
                                    if (!goal.isPaused && !goal.deleted) {
                                        val remaining = goal.targetAmount - goal.totalSavedSoFar
                                        if (remaining > 0) {
                                            if (goal.targetDate != null) {
                                                if (LocalDate.now().isBefore(goal.targetDate)) {
                                                    val periods = when (budgetPeriod) {
                                                        BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(LocalDate.now(), goal.targetDate)
                                                        BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(LocalDate.now(), goal.targetDate)
                                                        BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(LocalDate.now(), goal.targetDate)
                                                    }
                                                    if (periods > 0) {
                                                        val deduction = minOf(remaining / periods.toDouble(), remaining)
                                                        savingsGoals[idx] = goal.copy(
                                                            totalSavedSoFar = goal.totalSavedSoFar + deduction
                                                        )
                                                    }
                                                }
                                            } else {
                                                val contribution = minOf(
                                                    goal.contributionPerPeriod,
                                                    remaining
                                                )
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
                        }

                        prefs.edit()
                            .putFloat("availableCash", availableCash.toFloat())
                            .putString("lastRefreshDate", lastRefreshDate.toString())
                            .apply()
                    }
                }
                true // return value for remember
            }

            SyncBudgetTheme(strings = strings) {
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
                        onSupercharge = { allocations, modes ->
                            var totalDeducted = 0.0
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
                                            // Target-date goal, Achieve Sooner: move target date earlier
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
                                            // Fixed-contribution goal, Reduce: lower contribution rate
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
                                        totalDeducted += capped
                                    }
                                }
                            }
                            if (totalDeducted > 0) {
                                saveSavingsGoals()
                                availableCash -= totalDeducted
                                persistAvailableCash()
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
                            matchPercent = it; prefs.edit().putFloat("matchPercent", it).apply()
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
                            categories.add(cat)
                            saveCategories()
                        },
                        onUpdateCategory = { updated ->
                            val idx = categories.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                categories[idx] = updated
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
                                    transactions[index] = txn.copy(categoryAmounts = finalAmounts)
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
                                transactions[index] = updated
                                saveTransactions()
                            }
                            if (budgetStartDate != null && old != null) {
                                // Reverse old effect
                                if (!old.date.isBefore(budgetStartDate)) {
                                    if (old.type == TransactionType.EXPENSE && !isBudgetAccountedExpense(old)) availableCash += old.amount
                                    else if (old.type == TransactionType.INCOME && !old.isBudgetIncome) availableCash -= old.amount
                                }
                                // Apply new effect
                                if (!updated.date.isBefore(budgetStartDate)) {
                                    if (updated.type == TransactionType.EXPENSE && !isBudgetAccountedExpense(updated)) availableCash -= updated.amount
                                    else if (updated.type == TransactionType.INCOME && !updated.isBudgetIncome) availableCash += updated.amount
                                }
                                persistAvailableCash()
                            }
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
                            if (budgetStartDate != null && !txn.date.isBefore(budgetStartDate)) {
                                if (txn.type == TransactionType.EXPENSE && !isBudgetAccountedExpense(txn)) {
                                    availableCash += txn.amount
                                } else if (txn.type == TransactionType.INCOME && !txn.isBudgetIncome) {
                                    availableCash -= txn.amount
                                }
                                persistAvailableCash()
                            }
                        },
                        onDeleteTransactions = { ids ->
                            val deletedTxns = transactions.filter { it.id in ids && !it.deleted }
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
                            if (budgetStartDate != null) {
                                for (txn in deletedTxns) {
                                    if (!txn.date.isBefore(budgetStartDate)) {
                                        if (txn.type == TransactionType.EXPENSE && !isBudgetAccountedExpense(txn)) {
                                            availableCash += txn.amount
                                        } else if (txn.type == TransactionType.INCOME && !txn.isBudgetIncome) {
                                            availableCash -= txn.amount
                                        }
                                    }
                                }
                                persistAvailableCash()
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
                            manualBudgetAmount = prefs.getFloat("manualBudgetAmount", 0f).toDouble()
                            availableCash = prefs.getFloat("availableCash", 0f).toDouble()
                            budgetStartDate = prefs.getString("budgetStartDate", null)?.let { LocalDate.parse(it) }
                            lastRefreshDate = prefs.getString("lastRefreshDate", null)?.let { LocalDate.parse(it) }
                            weekStartSunday = prefs.getBoolean("weekStartSunday", true)
                            matchDays = prefs.getInt("matchDays", 7)
                            matchPercent = prefs.getFloat("matchPercent", 1.0f)
                            matchDollar = prefs.getInt("matchDollar", 1)
                            matchChars = prefs.getInt("matchChars", 5)

                            // Handle family sync: dissolve old group, create new one
                            if (isSyncConfigured) {
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
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "transactions_help" }
                    )
                    "future_expenditures" -> FutureExpendituresScreen(
                        savingsGoals = savingsGoals.toList().active,
                        currencySymbol = currencySymbol,
                        budgetPeriod = budgetPeriod,
                        isManualBudgetEnabled = isManualBudgetEnabled,
                        dateFormatPattern = dateFormatPattern,
                        onAddGoal = { savingsGoals.add(it); saveSavingsGoals() },
                        onUpdateGoal = { updated ->
                            val idx = savingsGoals.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) { savingsGoals[idx] = updated; saveSavingsGoals() }
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
                        isManualBudgetEnabled = isManualBudgetEnabled,
                        dateFormatPattern = dateFormatPattern,
                        onAddEntry = { amortizationEntries.add(it); saveAmortizationEntries() },
                        onUpdateEntry = { updated ->
                            val idx = amortizationEntries.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) { amortizationEntries[idx] = updated; saveAmortizationEntries() }
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
                        onAddRecurringExpense = { recurringExpenses.add(it); saveRecurringExpenses() },
                        onUpdateRecurringExpense = { updated ->
                            val idx = recurringExpenses.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) { recurringExpenses[idx] = updated; saveRecurringExpenses() }
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
                        onAddIncomeSource = { incomeSources.add(it); saveIncomeSources() },
                        onUpdateIncomeSource = { updated ->
                            val idx = incomeSources.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                incomeSources[idx] = updated
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
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(resetDayOfWeek = it, resetDayOfWeek_clock = clock, lastChangedBy = localDeviceId)
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
                            prefs.edit().putFloat("manualBudgetAmount", amount.toFloat()).apply()
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
                            lastRefreshDate = LocalDate.now()
                            availableCash = budgetAmount
                            // Record period ledger entry
                            periodLedger.add(
                                PeriodLedgerEntry(
                                    periodStartDate = LocalDateTime.now(),
                                    appliedAmount = budgetAmount,
                                    clockAtReset = lamportClock.value
                                )
                            )
                            savePeriodLedger()
                            if (isSyncConfigured) {
                                val clock = lamportClock.tick()
                                sharedSettings = sharedSettings.copy(budgetStartDate = budgetStartDate?.toString(), budgetStartDate_clock = clock, lastChangedBy = localDeviceId)
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                            prefs.edit()
                                .putString("budgetStartDate", budgetStartDate.toString())
                                .putString("lastRefreshDate", lastRefreshDate.toString())
                                .putFloat("availableCash", availableCash.toFloat())
                                .apply()
                        },
                        onRecalculate = {
                            if (budgetStartDate == null || availableCash == 0.0) {
                                val tz = if (isSyncConfigured && sharedSettings.familyTimezone.isNotEmpty())
                                    ZoneId.of(sharedSettings.familyTimezone) else null
                                budgetStartDate = BudgetCalculator.currentPeriodStart(budgetPeriod, resetDayOfWeek, resetDayOfMonth, tz)
                                lastRefreshDate = LocalDate.now()
                                availableCash = budgetAmount
                                prefs.edit()
                                    .putString("budgetStartDate", budgetStartDate.toString())
                                    .putString("lastRefreshDate", lastRefreshDate.toString())
                                    .putFloat("availableCash", availableCash.toFloat())
                                    .apply()
                            }
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
                        onCreateGroup = {
                            coroutineScope.launch {
                                try {
                                    val info = GroupManager.createGroup(context)
                                    // Register this device as admin
                                    FirestoreService.registerDevice(
                                        info.groupId,
                                        localDeviceId,
                                        GroupManager.getDeviceName(context),
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
                                    syncGroupId = info.groupId
                                    isSyncAdmin = true
                                    isSyncConfigured = true
                                    syncStatus = "synced"
                                } catch (_: Exception) {
                                    syncStatus = "error"
                                }
                            }
                        },
                        onJoinGroup = { code ->
                            coroutineScope.launch {
                                try {
                                    val success = GroupManager.joinGroup(context, code)
                                    if (success) {
                                        syncGroupId = GroupManager.getGroupId(context)
                                        isSyncAdmin = false
                                        isSyncConfigured = true
                                        syncStatus = "synced"
                                    }
                                } catch (_: Exception) {
                                    syncStatus = "error"
                                }
                            }
                        },
                        onLeaveGroup = {
                            GroupManager.leaveGroup(context)
                            isSyncConfigured = false
                            syncGroupId = null
                            isSyncAdmin = false
                            syncStatus = "off"
                            lastSyncTime = null
                            syncDevices = emptyList()
                        },
                        onDissolveGroup = {
                            val gId = syncGroupId
                            if (gId != null) {
                                coroutineScope.launch {
                                    try {
                                        GroupManager.dissolveGroup(context, gId)
                                        isSyncConfigured = false
                                        syncGroupId = null
                                        isSyncAdmin = false
                                        syncStatus = "off"
                                        lastSyncTime = null
                                        syncDevices = emptyList()
                                    } catch (_: Exception) {}
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
                                    val result = engine.sync(
                                        transactions.toList(),
                                        recurringExpenses.toList(),
                                        incomeSources.toList(),
                                        savingsGoals.toList(),
                                        amortizationEntries.toList(),
                                        categories.toList(),
                                        sharedSettings
                                    )
                                    if (result.success) {
                                        result.mergedTransactions?.let { transactions.clear(); transactions.addAll(it); saveTransactions() }
                                        result.mergedRecurringExpenses?.let { recurringExpenses.clear(); recurringExpenses.addAll(it); saveRecurringExpenses() }
                                        result.mergedIncomeSources?.let { incomeSources.clear(); incomeSources.addAll(it); saveIncomeSources() }
                                        result.mergedSavingsGoals?.let { savingsGoals.clear(); savingsGoals.addAll(it); saveSavingsGoals() }
                                        result.mergedAmortizationEntries?.let { amortizationEntries.clear(); amortizationEntries.addAll(it); saveAmortizationEntries() }
                                        result.mergedCategories?.let { categories.clear(); categories.addAll(it); saveCategories() }
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
                                            prefs.edit()
                                                .putString("currencySymbol", merged.currency)
                                                .putString("budgetPeriod", merged.budgetPeriod)
                                                .putInt("resetHour", merged.resetHour)
                                                .putInt("resetDayOfWeek", merged.resetDayOfWeek)
                                                .putInt("resetDayOfMonth", merged.resetDayOfMonth)
                                                .putBoolean("isManualBudgetEnabled", merged.isManualBudgetEnabled)
                                                .putFloat("manualBudgetAmount", merged.manualBudgetAmount.toFloat())
                                                .putBoolean("weekStartSunday", merged.weekStartSunday)
                                                .putInt("matchDays", merged.matchDays)
                                                .putFloat("matchPercent", merged.matchPercent)
                                                .putInt("matchDollar", merged.matchDollar)
                                                .putInt("matchChars", merged.matchChars)
                                                .apply()
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
                            if (budgetStartDate != null && !dup.date.isBefore(budgetStartDate)) {
                                if (dup.type == TransactionType.EXPENSE && !isBudgetAccountedExpense(dup)) availableCash += dup.amount
                                else if (dup.type == TransactionType.INCOME && !dup.isBudgetIncome) availableCash -= dup.amount
                                persistAvailableCash()
                            }
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
                    val recurringCategoryId = categories.find { it.tag == "recurring" }?.id
                    val dateCloseEnough = isRecurringDateCloseEnough(dashPendingRecurringTxn!!.date, dashPendingRecurringMatch!!)
                    RecurringExpenseConfirmDialog(
                        transaction = dashPendingRecurringTxn!!,
                        recurringExpense = dashPendingRecurringMatch!!,
                        currencySymbol = currencySymbol,
                        dateFormatter = dateFormatter,
                        showDateAdvisory = !dateCloseEnough,
                        onConfirmRecurring = {
                            val txn = dashPendingRecurringTxn!!
                            val updatedTxn = if (recurringCategoryId != null) {
                                txn.copy(
                                    categoryAmounts = listOf(CategoryAmount(recurringCategoryId, txn.amount)),
                                    isUserCategorized = true
                                )
                            } else txn
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
                    val amortizationCategoryId = categories.find { it.tag == "amortization" }?.id
                    AmortizationConfirmDialog(
                        transaction = dashPendingAmortizationTxn!!,
                        amortizationEntry = dashPendingAmortizationMatch!!,
                        currencySymbol = currencySymbol,
                        dateFormatter = dateFormatter,
                        onConfirmAmortization = {
                            val txn = dashPendingAmortizationTxn!!
                            val updatedTxn = if (amortizationCategoryId != null) {
                                txn.copy(
                                    categoryAmounts = listOf(CategoryAmount(amortizationCategoryId, txn.amount)),
                                    isUserCategorized = true
                                )
                            } else txn
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
            }
        }
    }
}
