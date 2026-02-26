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
            var manualBudgetAmount by remember { mutableDoubleStateOf(
                try {
                    prefs.getString("manualBudgetAmount", null)?.toDoubleOrNull()
                } catch (_: ClassCastException) { null }
                    ?: prefs.getFloat("manualBudgetAmount", 0f).toDouble()
            ) }
            var availableCash by remember { mutableDoubleStateOf(
                try {
                    prefs.getString("availableCash", null)?.toDoubleOrNull()
                } catch (_: ClassCastException) { null }
                    ?: prefs.getFloat("availableCash", 0f).toDouble()
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
                    maxOf(0.0, base - amortDeductions - savingsDeductions)
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
            // Only persists to local prefs. CRDT sync of availableCash happens
            // exclusively during budget reset to avoid LWW conflicts between devices
            // that independently compute cash from the same synced transactions.
            fun persistAvailableCash() {
                if (availableCash.isNaN() || availableCash.isInfinite()) availableCash = 0.0
                prefs.edit().putString("availableCash", availableCash.toString()).apply()
            }

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
                } catch (e: Exception) {
                    android.util.Log.w("SyncLoop", "Failed to fetch initial device list", e)
                }

                while (true) {
                    // Acquire sync lock to prevent race with SyncWorker
                    if (!SyncWorker.syncLock.tryLock()) {
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
                            existingCatIdRemap = existingRemap
                        )
                        if (result.success) {
                            // Capture pre-merge transaction IDs for availableCash adjustment
                            val premergeLocalTxnIds = transactions.map { it.id }.toSet()

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
                            }
                            result.mergedTransactions?.let { saveTransactions() }
                            result.mergedRecurringExpenses?.let { saveRecurringExpenses() }
                            result.mergedIncomeSources?.let { saveIncomeSources() }
                            result.mergedSavingsGoals?.let { saveSavingsGoals() }
                            result.mergedAmortizationEntries?.let { saveAmortizationEntries() }
                            result.mergedCategories?.let { saveCategories() }
                            // Adjust availableCash for new remote transactions (#9)
                            if (result.mergedTransactions != null && budgetStartDate != null) {
                                val newRemoteTxns = result.mergedTransactions!!.filter {
                                    it.deviceId != localDeviceId && it.id !in premergeLocalTxnIds
                                }
                                var cashChanged = false
                                for (txn in newRemoteTxns) {
                                    if (!txn.deleted && !txn.date.isBefore(budgetStartDate)) {
                                        if (txn.type == TransactionType.EXPENSE && !isBudgetAccountedExpense(txn)) {
                                            availableCash -= txn.amount
                                            cashChanged = true
                                        } else if (txn.type == TransactionType.INCOME && !txn.isBudgetIncome) {
                                            availableCash += txn.amount
                                            cashChanged = true
                                        }
                                    }
                                }
                                if (cashChanged) persistAvailableCash()
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
                                // Apply synced budgetStartDate and re-initialize budget
                                val syncedStartDate = merged.budgetStartDate?.let {
                                    try { LocalDate.parse(it) } catch (_: Exception) { null }
                                }
                                val budgetStartChanged = syncedStartDate != null && syncedStartDate != budgetStartDate
                                // Detect if admin pushed a new availableCash (reset or manual sync).
                                // Only non-admin devices adopt remote cash — admin is authoritative.
                                val lastSeenAcClock = syncPrefs.getLong("lastSeenAvailableCash_clock", 0L)
                                val cashPushedByRemote = !isSyncAdmin &&
                                    merged.availableCash_clock > lastSeenAcClock &&
                                    merged.lastChangedBy != localDeviceId
                                if (budgetStartChanged) {
                                    budgetStartDate = syncedStartDate
                                    lastRefreshDate = LocalDate.now()
                                    // Use synced availableCash from admin reset
                                    availableCash = if (merged.availableCash_clock > 0L) merged.availableCash else budgetAmount
                                    syncPrefs.edit().putLong("lastSeenAvailableCash_clock", merged.availableCash_clock).apply()
                                } else if (cashPushedByRemote) {
                                    // Admin used "Sync Cash to Admin" — adopt their value
                                    availableCash = merged.availableCash
                                    syncPrefs.edit().putLong("lastSeenAvailableCash_clock", merged.availableCash_clock).apply()
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
                                    .putFloat("matchPercent", merged.matchPercent)
                                    .putInt("matchDollar", merged.matchDollar)
                                    .putInt("matchChars", merged.matchChars)
                                if (budgetStartChanged) {
                                    prefsEditor
                                        .putString("budgetStartDate", budgetStartDate.toString())
                                        .putString("lastRefreshDate", lastRefreshDate.toString())
                                        .putString("availableCash", availableCash.toString())
                                } else if (cashPushedByRemote) {
                                    prefsEditor.putString("availableCash", availableCash.toString())
                                }
                                prefsEditor.apply()
                            }
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
                        SyncWorker.syncLock.unlock()
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

            // Helper to add a transaction with budget effects
            fun addTransactionWithBudgetEffect(txn: Transaction) {
                val clock = lamportClock.tick()
                val stamped = txn.copy(
                    deviceId = localDeviceId,
                    source_clock = clock,
                    amount_clock = clock,
                    date_clock = clock,
                    type_clock = clock,
                    categoryAmounts_clock = clock,
                    isUserCategorized_clock = clock,
                    isBudgetIncome_clock = clock
                )
                transactions.add(stamped)
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

                        // Record period ledger entry (deduplicate across devices)
                        val nowDateTime = LocalDateTime.now()
                        val alreadyRecorded = periodLedger.any {
                            it.periodStartDate.toLocalDate() == nowDateTime.toLocalDate()
                        }
                        if (!alreadyRecorded) {
                            periodLedger.add(
                                PeriodLedgerEntry(
                                    periodStartDate = nowDateTime,
                                    appliedAmount = budgetAmount,
                                    clockAtReset = lamportClock.value
                                )
                            )
                            savePeriodLedger()
                        }

                        // Update savings goals totalSavedSoFar for non-paused, non-complete items
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

                        prefs.edit()
                            .putString("availableCash", availableCash.toString())
                            .putString("lastRefreshDate", lastRefreshDate.toString())
                            .apply()

                        // Admin pushes refreshed cash to CRDT so non-admin devices
                        // can adopt it on next sync
                        if (isSyncConfigured && isSyncAdmin) {
                            val clock = lamportClock.tick()
                            sharedSettings = sharedSettings.copy(
                                availableCash = availableCash,
                                availableCash_clock = clock,
                                lastChangedBy = localDeviceId
                            )
                            SharedSettingsRepository.save(context, sharedSettings)
                        }
                    }
                }
                true // return value for remember
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
                    dump.appendLine("── Transactions (active, in current period) ──")
                    val activeTxns = transactions.filter { !it.deleted }
                    val periodTxns = if (budgetStartDate != null) activeTxns.filter { !it.date.isBefore(budgetStartDate) } else activeTxns
                    var totalExpense = 0.0
                    var totalIncome = 0.0
                    for (txn in periodTxns.sortedBy { it.date }) {
                        val budgetAccounted = if (txn.type == TransactionType.EXPENSE) isBudgetAccountedExpense(txn) else false
                        dump.appendLine("  ${txn.date} ${txn.type} ${txn.amount} '${txn.source}' dev=${txn.deviceId.take(8)}… budgetAccounted=$budgetAccounted clk=${txn.amount_clock}")
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
                        remoteCrdtCash = if (isSyncConfigured) sharedSettings.availableCash else null,
                        syncDevices = syncDevices,
                        localDeviceId = localDeviceId,
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
                                iconName_clock = clock
                            ))
                            saveCategories()
                        },
                        onUpdateCategory = { updated ->
                            val idx = categories.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = categories[idx]
                                val clock = lamportClock.tick()
                                categories[idx] = updated.copy(
                                    deviceId = localDeviceId,
                                    deleted = old.deleted,
                                    deleted_clock = old.deleted_clock,
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
                                transactions[index] = updated.copy(
                                    deviceId = localDeviceId,
                                    deleted = prev.deleted,
                                    deleted_clock = prev.deleted_clock,
                                    source_clock = if (updated.source != prev.source) clock else prev.source_clock,
                                    amount_clock = if (updated.amount != prev.amount) clock else prev.amount_clock,
                                    date_clock = if (updated.date != prev.date) clock else prev.date_clock,
                                    type_clock = if (updated.type != prev.type) clock else prev.type_clock,
                                    categoryAmounts_clock = if (updated.categoryAmounts != prev.categoryAmounts) clock else prev.categoryAmounts_clock,
                                    isUserCategorized_clock = if (updated.isUserCategorized != prev.isUserCategorized) clock else prev.isUserCategorized_clock,
                                    isBudgetIncome_clock = if (updated.isBudgetIncome != prev.isBudgetIncome) clock else prev.isBudgetIncome_clock
                                )
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
                            manualBudgetAmount = prefs.getString("manualBudgetAmount", null)?.toDoubleOrNull()
                                ?: prefs.getFloat("manualBudgetAmount", 0f).toDouble()
                            availableCash = prefs.getString("availableCash", null)?.toDoubleOrNull()
                                ?: prefs.getFloat("availableCash", 0f).toDouble()
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
                                isPaused_clock = clock
                            ))
                            saveSavingsGoals()
                        },
                        onUpdateGoal = { updated ->
                            val idx = savingsGoals.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = savingsGoals[idx]
                                val clock = lamportClock.tick()
                                savingsGoals[idx] = updated.copy(
                                    deviceId = localDeviceId,
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
                                amount_clock = clock,
                                totalPeriods_clock = clock,
                                startDate_clock = clock,
                                isPaused_clock = clock
                            ))
                            saveAmortizationEntries()
                        },
                        onUpdateEntry = { updated ->
                            val idx = amortizationEntries.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = amortizationEntries[idx]
                                val clock = lamportClock.tick()
                                amortizationEntries[idx] = updated.copy(
                                    deviceId = localDeviceId,
                                    deleted = old.deleted,
                                    deleted_clock = old.deleted_clock,
                                    source_clock = if (updated.source != old.source) clock else old.source_clock,
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
                                amount_clock = clock,
                                repeatType_clock = clock,
                                repeatInterval_clock = clock,
                                startDate_clock = clock,
                                monthDay1_clock = clock,
                                monthDay2_clock = clock
                            ))
                            saveRecurringExpenses()
                        },
                        onUpdateRecurringExpense = { updated ->
                            val idx = recurringExpenses.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = recurringExpenses[idx]
                                val clock = lamportClock.tick()
                                recurringExpenses[idx] = updated.copy(
                                    deviceId = localDeviceId,
                                    deleted = old.deleted,
                                    deleted_clock = old.deleted_clock,
                                    source_clock = if (updated.source != old.source) clock else old.source_clock,
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
                                amount_clock = clock,
                                repeatType_clock = clock,
                                repeatInterval_clock = clock,
                                startDate_clock = clock,
                                monthDay1_clock = clock,
                                monthDay2_clock = clock
                            ))
                            saveIncomeSources()
                        },
                        onUpdateIncomeSource = { updated ->
                            val idx = incomeSources.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = incomeSources[idx]
                                val clock = lamportClock.tick()
                                incomeSources[idx] = updated.copy(
                                    deviceId = localDeviceId,
                                    deleted = old.deleted,
                                    deleted_clock = old.deleted_clock,
                                    source_clock = if (updated.source != old.source) clock else old.source_clock,
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
                                sharedSettings = sharedSettings.copy(
                                    budgetStartDate = budgetStartDate?.toString(),
                                    budgetStartDate_clock = clock,
                                    availableCash = availableCash,
                                    availableCash_clock = clock,
                                    lastChangedBy = localDeviceId
                                )
                                SharedSettingsRepository.save(context, sharedSettings)
                            }
                            prefs.edit()
                                .putString("budgetStartDate", budgetStartDate.toString())
                                .putString("lastRefreshDate", lastRefreshDate.toString())
                                .putString("availableCash", availableCash.toString())
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
                                        availableCash = availableCash,
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
                                        matchChars_clock = clock,
                                        availableCash_clock = clock
                                    )
                                    SharedSettingsRepository.save(context, sharedSettings)

                                    // Stamp all existing data with sync clocks so they push on first sync
                                    val stampClock = lamportClock.tick()
                                    transactions.forEachIndexed { i, t ->
                                        if (t.source_clock == 0L) {
                                            transactions[i] = t.copy(
                                                deviceId = localDeviceId,
                                                source_clock = stampClock, amount_clock = stampClock,
                                                date_clock = stampClock, type_clock = stampClock,
                                                categoryAmounts_clock = stampClock,
                                                isUserCategorized_clock = stampClock,
                                                isBudgetIncome_clock = stampClock
                                            )
                                        }
                                    }
                                    saveTransactions()
                                    categories.forEachIndexed { i, c ->
                                        if (c.name_clock == 0L) {
                                            categories[i] = c.copy(
                                                deviceId = localDeviceId,
                                                name_clock = stampClock, iconName_clock = stampClock
                                            )
                                        }
                                    }
                                    saveCategories()
                                    savingsGoals.forEachIndexed { i, g ->
                                        if (g.name_clock == 0L) {
                                            savingsGoals[i] = g.copy(
                                                deviceId = localDeviceId,
                                                name_clock = stampClock, targetAmount_clock = stampClock,
                                                targetDate_clock = stampClock, totalSavedSoFar_clock = stampClock,
                                                contributionPerPeriod_clock = stampClock, isPaused_clock = stampClock
                                            )
                                        }
                                    }
                                    saveSavingsGoals()
                                    amortizationEntries.forEachIndexed { i, e ->
                                        if (e.source_clock == 0L) {
                                            amortizationEntries[i] = e.copy(
                                                deviceId = localDeviceId,
                                                source_clock = stampClock, amount_clock = stampClock,
                                                totalPeriods_clock = stampClock, startDate_clock = stampClock,
                                                isPaused_clock = stampClock
                                            )
                                        }
                                    }
                                    saveAmortizationEntries()
                                    recurringExpenses.forEachIndexed { i, r ->
                                        if (r.source_clock == 0L) {
                                            recurringExpenses[i] = r.copy(
                                                deviceId = localDeviceId,
                                                source_clock = stampClock, amount_clock = stampClock,
                                                repeatType_clock = stampClock, repeatInterval_clock = stampClock,
                                                startDate_clock = stampClock, monthDay1_clock = stampClock,
                                                monthDay2_clock = stampClock
                                            )
                                        }
                                    }
                                    saveRecurringExpenses()
                                    incomeSources.forEachIndexed { i, s ->
                                        if (s.source_clock == 0L) {
                                            incomeSources[i] = s.copy(
                                                deviceId = localDeviceId,
                                                source_clock = stampClock, amount_clock = stampClock,
                                                repeatType_clock = stampClock, repeatInterval_clock = stampClock,
                                                startDate_clock = stampClock, monthDay1_clock = stampClock,
                                                monthDay2_clock = stampClock
                                            )
                                        }
                                    }
                                    saveIncomeSources()

                                    syncGroupId = info.groupId
                                    isSyncAdmin = true
                                    isSyncConfigured = true
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
                                        existingCatIdRemap = existRemap
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
                                            val syncedStartDate = merged.budgetStartDate?.let {
                                                try { LocalDate.parse(it) } catch (_: Exception) { null }
                                            }
                                            val budgetStartChanged = syncedStartDate != null && syncedStartDate != budgetStartDate
                                            val lastSeenAcClock2 = syncPrefs.getLong("lastSeenAvailableCash_clock", 0L)
                                            val cashPushedByRemote2 = !isSyncAdmin &&
                                                merged.availableCash_clock > lastSeenAcClock2 &&
                                                merged.lastChangedBy != localDeviceId
                                            if (budgetStartChanged) {
                                                budgetStartDate = syncedStartDate
                                                lastRefreshDate = LocalDate.now()
                                                availableCash = if (merged.availableCash_clock > 0L) merged.availableCash else budgetAmount
                                                syncPrefs.edit().putLong("lastSeenAvailableCash_clock", merged.availableCash_clock).apply()
                                            } else if (cashPushedByRemote2) {
                                                availableCash = merged.availableCash
                                                syncPrefs.edit().putLong("lastSeenAvailableCash_clock", merged.availableCash_clock).apply()
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
                                                .putFloat("matchPercent", merged.matchPercent)
                                                .putInt("matchDollar", merged.matchDollar)
                                                .putInt("matchChars", merged.matchChars)
                                            if (budgetStartChanged || cashPushedByRemote2) {
                                                prefsEditor
                                                    .putString("budgetStartDate", budgetStartDate.toString())
                                                    .putString("lastRefreshDate", lastRefreshDate.toString())
                                                    .putString("availableCash", availableCash.toString())
                                            }
                                            prefsEditor.apply()
                                        }
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
                        onSyncCashToAdmin = {
                            // Push admin's local availableCash to SharedSettings CRDT
                            // so all devices pick it up on next sync
                            val acClock = lamportClock.tick()
                            sharedSettings = sharedSettings.copy(
                                availableCash = availableCash,
                                availableCash_clock = acClock,
                                lastChangedBy = localDeviceId
                            )
                            SharedSettingsRepository.save(context, sharedSettings)
                            android.widget.Toast.makeText(
                                context,
                                "Available cash synced: ${availableCash}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
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
                } // Box(weight)
              } // Column
              } // CompositionLocalProvider
            } // SyncBudgetTheme
        }
    }
}
