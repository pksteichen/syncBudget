package com.syncbudget.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.AmortizationRepository
import com.syncbudget.app.data.BackupManager
import com.syncbudget.app.data.BudgetCalculator
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.CategoryRepository
import com.syncbudget.app.data.DEFAULT_CATEGORY_DEFS
import com.syncbudget.app.data.DiagDumpBuilder
import com.syncbudget.app.data.FullBackupSerializer
import com.syncbudget.app.data.IncomeMode
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.IncomeSourceRepository
import com.syncbudget.app.data.PeriodRefreshService
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.RecurringExpenseRepository
import com.syncbudget.app.data.RepeatType
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SavingsGoalRepository
import com.syncbudget.app.data.SavingsSimulator
import com.syncbudget.app.data.SharedSettings
import com.syncbudget.app.data.SharedSettingsRepository
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionRepository
import com.syncbudget.app.data.TransactionType
import com.syncbudget.app.data.findAmortizationMatch
import com.syncbudget.app.data.findBudgetIncomeMatch
import com.syncbudget.app.data.findDuplicate
import com.syncbudget.app.data.findRecurringExpenseMatch
import com.syncbudget.app.data.generateTransactionId
import com.syncbudget.app.data.getDefaultCategoryName
import com.syncbudget.app.data.getDoubleCompat
import com.syncbudget.app.data.isRecurringDateCloseEnough
import com.syncbudget.app.data.sync.DeviceInfo
import com.syncbudget.app.data.sync.EncryptedDocSerializer
import com.syncbudget.app.data.sync.FcmSender
import com.syncbudget.app.data.sync.FirestoreDocService
import com.syncbudget.app.data.sync.FirestoreDocSync
import com.syncbudget.app.data.sync.FirestoreService
import com.syncbudget.app.data.sync.GroupManager
import com.syncbudget.app.data.sync.PeriodLedgerEntry
import com.syncbudget.app.data.sync.PeriodLedgerRepository
import com.syncbudget.app.data.sync.SyncIdGenerator
import com.syncbudget.app.data.sync.SyncMergeProcessor
import com.syncbudget.app.data.sync.SyncWriteHelper
import com.syncbudget.app.data.sync.active
import com.syncbudget.app.ui.screens.QuickStartStep
import com.syncbudget.app.ui.strings.AppStrings
import com.syncbudget.app.ui.strings.EnglishStrings
import com.syncbudget.app.ui.strings.SpanishStrings
import com.syncbudget.app.data.sync.AdminClaim
import com.syncbudget.app.data.sync.SubscriptionReminderReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        @Volatile
        var instance: java.lang.ref.WeakReference<MainViewModel>? = null
            private set
    }

    private val context: Context = application.applicationContext
    val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val syncPrefs: SharedPreferences = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
    val backupPrefs: SharedPreferences = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    // ── Firebase Auth ──
    var firebaseAuthReady by mutableStateOf(
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
    )

    // ── Navigation ──
    var currentScreen by mutableStateOf("main")

    // ── Dashboard quick-add dialog state ──
    var dashboardShowAddIncome by mutableStateOf(false)
    var dashboardShowAddExpense by mutableStateOf(false)

    // Dashboard matching state
    var dashPendingManualSave by mutableStateOf<Transaction?>(null)
    var dashManualDuplicateMatch by mutableStateOf<Transaction?>(null)
    var dashShowManualDuplicateDialog by mutableStateOf(false)

    var dashPendingRecurringTxn by mutableStateOf<Transaction?>(null)
    var dashPendingRecurringMatch by mutableStateOf<RecurringExpense?>(null)
    var dashShowRecurringDialog by mutableStateOf(false)

    var dashPendingAmortizationTxn by mutableStateOf<Transaction?>(null)
    var dashPendingAmortizationMatch by mutableStateOf<AmortizationEntry?>(null)
    var dashShowAmortizationDialog by mutableStateOf(false)

    var dashPendingBudgetIncomeTxn by mutableStateOf<Transaction?>(null)
    var dashPendingBudgetIncomeMatch by mutableStateOf<IncomeSource?>(null)
    var dashShowBudgetIncomeDialog by mutableStateOf(false)

    // Pending amount-change confirmations (apply to past transactions?)
    var pendingREAmountUpdate by mutableStateOf<Pair<RecurringExpense, Double>?>(null) // (updated, oldAmount)
    var pendingISAmountUpdate by mutableStateOf<Pair<IncomeSource, Double>?>(null) // (updated, oldAmount)

    // ── App Config ──
    var currencySymbol by mutableStateOf(prefs.getString("currencySymbol", "$") ?: "$")
    var digitCount by mutableIntStateOf(prefs.getInt("digitCount", 3))
    var showDecimals by mutableStateOf(prefs.getBoolean("showDecimals", false))
    var dateFormatPattern by mutableStateOf(prefs.getString("dateFormatPattern", "yyyy-MM-dd") ?: "yyyy-MM-dd")
    var isPaidUser by mutableStateOf(prefs.getBoolean("isPaidUser", false))
    var isSubscriber by mutableStateOf(prefs.getBoolean("isSubscriber", false))
    var quickStartStep by mutableStateOf<QuickStartStep?>(null)
    var subscriptionExpiry by mutableStateOf(
        prefs.getLong("subscriptionExpiry", System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
    )
    var showWidgetLogo by mutableStateOf(prefs.getBoolean("showWidgetLogo", true))

    // ── Backup State ──
    var backupsEnabled by mutableStateOf(backupPrefs.getBoolean("backups_enabled", false))
    var backupFrequencyWeeks by mutableIntStateOf(backupPrefs.getInt("backup_frequency_weeks", 1))
    var backupRetention by mutableIntStateOf(backupPrefs.getInt("backup_retention", 1))
    var lastBackupDate by mutableStateOf<String?>(backupPrefs.getString("last_backup_date", null))
    var showBackupPasswordDialog by mutableStateOf(false)
    var showDisableBackupDialog by mutableStateOf(false)
    var showRestoreDialog by mutableStateOf(false)
    var showSavePhotosDialog by mutableStateOf(false)

    // ── Matching Config ──
    var matchDays by mutableIntStateOf(prefs.getInt("matchDays", 7))
    var matchPercent by mutableDoubleStateOf(prefs.getDoubleCompat("matchPercent", 1.0))
    var matchDollar by mutableIntStateOf(prefs.getInt("matchDollar", 1))
    var matchChars by mutableIntStateOf(prefs.getInt("matchChars", 5))
    var weekStartSunday by mutableStateOf(prefs.getBoolean("weekStartSunday", true))
    var chartPalette by mutableStateOf(prefs.getString("chartPalette", "Sunset") ?: "Sunset")

    // ── Language / Strings ──
    // Default to device language if we support it, otherwise English
    private val deviceLang = java.util.Locale.getDefault().language
    private val defaultLang = if (deviceLang == "es") "es" else "en"
    var appLanguage by mutableStateOf(prefs.getString("appLanguage", null) ?: defaultLang)
    val strings: AppStrings get() = if (appLanguage == "es") SpanishStrings else EnglishStrings

    // ── Budget State ──
    var budgetPeriod by mutableStateOf(
        try { BudgetPeriod.valueOf(prefs.getString("budgetPeriod", "DAILY") ?: "DAILY") }
        catch (_: Exception) { BudgetPeriod.DAILY }
    )
    var resetHour by mutableIntStateOf(prefs.getInt("resetHour", 0))
    var resetDayOfWeek by mutableIntStateOf(prefs.getInt("resetDayOfWeek", 7))
    var resetDayOfMonth by mutableIntStateOf(prefs.getInt("resetDayOfMonth", 1))
    var isManualBudgetEnabled by mutableStateOf(prefs.getBoolean("isManualBudgetEnabled", false))
    var manualBudgetAmount by mutableDoubleStateOf(prefs.getDoubleCompat("manualBudgetAmount"))
    var incomeMode by mutableStateOf(
        try { IncomeMode.valueOf(prefs.getString("incomeMode", null) ?: "FIXED") } catch (_: Exception) { IncomeMode.FIXED }
    )
    var availableCash by mutableDoubleStateOf(prefs.getDoubleCompat("availableCash"))
    var budgetStartDate by mutableStateOf<LocalDate?>(
        prefs.getString("budgetStartDate", null)?.let { LocalDate.parse(it) }
    )
    var lastRefreshDate by mutableStateOf<LocalDate?>(
        prefs.getString("lastRefreshDate", null)?.let { LocalDate.parse(it) }
    )

    // ── Data Lists ──
    val transactions = mutableStateListOf<Transaction>()
    val categories = mutableStateListOf<Category>()
    val incomeSources = mutableStateListOf<IncomeSource>()
    val recurringExpenses = mutableStateListOf<RecurringExpense>()
    val amortizationEntries = mutableStateListOf<AmortizationEntry>()
    val savingsGoals = mutableStateListOf<SavingsGoal>()
    val periodLedger = mutableStateListOf<PeriodLedgerEntry>()

    // ── Sync State ──
    var lastSyncActivity by mutableStateOf(0L)
    var isSyncConfigured by mutableStateOf(GroupManager.isConfigured(context))
    var syncGroupId by mutableStateOf(GroupManager.getGroupId(context))
    var isSyncAdmin by mutableStateOf(GroupManager.isAdmin(context))
    var syncStatus by mutableStateOf(if (GroupManager.isConfigured(context)) "synced" else "off")
    var isNetworkAvailable by mutableStateOf(true)
    var syncDevices by mutableStateOf(loadCachedDevices())
    var generatedPairingCode by mutableStateOf<String?>(null)
    val localDeviceId: String = SyncIdGenerator.getOrCreateDeviceId(context)
    var syncErrorMessage by mutableStateOf<String?>(null)
    var syncProgressMessage by mutableStateOf<String?>(null)
    var pendingAdminClaim by mutableStateOf<AdminClaim?>(null)
    var dataLoaded by mutableStateOf(false)
        private set
    var loadProgress by mutableFloatStateOf(0f)
        private set

    // ── Archive State ──
    var archiveThreshold by mutableIntStateOf(prefs.getInt("archiveThreshold", 10_000))
    var archiveToastMessage by mutableStateOf<String?>(null)
    var loadedArchivedTransactions by mutableStateOf<List<Transaction>>(emptyList())
        private set

    val archiveCutoffDate: java.time.LocalDate? get() = sharedSettings.archiveCutoffDate?.let {
        try { java.time.LocalDate.parse(it) } catch (_: Exception) { null }
    }
    val carryForwardBalance: Double get() = sharedSettings.carryForwardBalance

    val lastArchiveDateDisplay: String? get() = try {
        sharedSettings.lastArchiveInfo?.let { org.json.JSONObject(it) }?.optString("date")
    } catch (_: Exception) { null }

    val lastArchiveCountDisplay: Int get() = try {
        sharedSettings.lastArchiveInfo?.let { org.json.JSONObject(it) }?.optInt("count", 0) ?: 0
    } catch (_: Exception) { 0 }

    val totalArchivedCountDisplay: Int get() = try {
        sharedSettings.lastArchiveInfo?.let { org.json.JSONObject(it) }?.optInt("totalArchived", 0) ?: 0
    } catch (_: Exception) { 0 }

    fun loadArchivedTransactionsAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            val archived = TransactionRepository.loadArchive(context)
            withContext(Dispatchers.Main) {
                loadedArchivedTransactions = archived.filter { !it.deleted }
            }
        }
    }

    fun updateArchivedTransaction(updated: Transaction) {
        val idx = loadedArchivedTransactions.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            loadedArchivedTransactions = loadedArchivedTransactions.toMutableList().also { it[idx] = updated }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val archive = TransactionRepository.loadArchive(context).toMutableList()
            val archIdx = archive.indexOfFirst { it.id == updated.id }
            if (archIdx >= 0) { archive[archIdx] = updated; TransactionRepository.saveArchive(context, archive) }
        }
        if (com.syncbudget.app.data.sync.SyncWriteHelper.isInitialized()) {
            com.syncbudget.app.data.sync.SyncWriteHelper.pushTransaction(updated)
        }
    }

    private fun loadCachedDevices(): List<DeviceInfo> {
        val json = syncPrefs.getString("cachedDeviceRoster", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DeviceInfo(
                    deviceId = obj.getString("id"),
                    deviceName = obj.optString("name", ""),
                    isAdmin = obj.optBoolean("admin", false),
                    lastSeen = 0L
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun cacheDeviceRoster(devices: List<DeviceInfo>) {
        if (devices.isEmpty() || devices.all { it.deviceName.isEmpty() }) return
        val arr = org.json.JSONArray()
        for (d in devices) {
            arr.put(org.json.JSONObject().apply {
                put("id", d.deviceId)
                put("name", d.deviceName)
                put("admin", d.isAdmin)
            })
        }
        syncPrefs.edit().putString("cachedDeviceRoster", arr.toString()).apply()
    }

    var syncRepairAlert by mutableStateOf(prefs.getBoolean("syncRepairAlert", false))
    var lastSyncTimeDisplay by mutableStateOf<String?>(null)
    private var imageLedgerListener: com.google.firebase.firestore.ListenerRegistration? = null

    // Gate for sync-dependent code: true immediately for solo users,
    // set to true after initial Firestore listener snapshot for synced users.
    var initialSyncReceived by mutableStateOf(!isSyncConfigured)

    // ── Shared Settings ──
    var sharedSettings by mutableStateOf(SharedSettingsRepository.load(context))

    // ── Sync trigger for resume ──
    var syncTrigger by mutableIntStateOf(0)
    var lastManualSyncTime by mutableStateOf(0L)

    // ── docSync ──
    var docSync: FirestoreDocSync? = null
        private set

    // ── Save Caches (private) ──
    private val lastSavedTxns = mutableMapOf<Int, Transaction>()
    private val lastSavedRe = mutableMapOf<Int, RecurringExpense>()
    private val lastSavedIs = mutableMapOf<Int, IncomeSource>()
    private val lastSavedSg = mutableMapOf<Int, SavingsGoal>()
    private val lastSavedAe = mutableMapOf<Int, AmortizationEntry>()
    private val lastSavedCat = mutableMapOf<Int, Category>()
    private val lastSavedPle = mutableMapOf<Int, PeriodLedgerEntry>()

    // ── Derived State ──

    // Cached active lists — filters deleted AND skeleton records
    val activeTransactions: List<Transaction> by derivedStateOf { transactions.toList().active }
    val activeRecurringExpenses: List<RecurringExpense> by derivedStateOf { recurringExpenses.toList().active }
    val activeIncomeSources: List<IncomeSource> by derivedStateOf { incomeSources.toList().active }
    val activeAmortizationEntries: List<AmortizationEntry> by derivedStateOf { amortizationEntries.toList().active }
    val activeSavingsGoals: List<SavingsGoal> by derivedStateOf { savingsGoals.toList().active }
    val activeCategories: List<Category> by derivedStateOf { categories.toList().active }

    // Budget "today" respects resetHour in DAILY mode: before resetHour
    // we're still in yesterday's period. WEEKLY/MONTHLY reset at midnight.
    val budgetToday by derivedStateOf {
        val now = java.time.LocalDateTime.now()
        if (budgetPeriod == BudgetPeriod.DAILY && resetHour > 0 && now.hour < resetHour)
            now.toLocalDate().minusDays(1)
        else
            now.toLocalDate()
    }

    // Derived safeBudgetAmount — auto-recalculates when income/expenses change
    val safeBudgetAmount by derivedStateOf {
        BudgetCalculator.calculateSafeBudgetAmount(
            activeIncomeSources,
            activeRecurringExpenses,
            budgetPeriod,
            budgetToday
        )
    }

    // Derived budgetAmount
    val budgetAmount by derivedStateOf {
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

    // Simulation-adjusted available cash
    val simAvailableCash by derivedStateOf {
        if (!dataLoaded) {
            android.util.Log.w("SimCash", "derivedStateOf: dataLoaded=false, returning availableCash=$availableCash")
            return@derivedStateOf availableCash
        }
        val bsd = budgetStartDate
        if (bsd == null) {
            android.util.Log.w("SimCash", "derivedStateOf: bsd=null, returning availableCash=$availableCash")
            return@derivedStateOf availableCash
        }
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
        val txnCount = activeTransactions.size
        val ledgerCount = adjustedLedger.size
        val result = BudgetCalculator.recomputeAvailableCash(
            bsd, adjustedLedger,
            activeTransactions, activeRecurringExpenses,
            incomeMode, activeIncomeSources,
            carryForwardBalance, archiveCutoffDate
        )
        android.util.Log.i("SimCash", "derivedStateOf: bsd=$bsd txns=$txnCount ledger=$ledgerCount cfb=$carryForwardBalance acd=$archiveCutoffDate → result=$result (availableCash=$availableCash)")
        result
    }

    // Percent tolerance for matching
    val percentTolerance by derivedStateOf { matchPercent / 100.0 }

    val existingIds by derivedStateOf { transactions.map { it.id }.toSet() }
    val categoryMap by derivedStateOf { categories.associateBy { it.id } }

    val dateFormatter by derivedStateOf {
        DateTimeFormatter.ofPattern(dateFormatPattern)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SAVE FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════

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
            android.util.Log.w("MainViewModel", "Deduped ${transactions.size - deduped.size} duplicate transactions")
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

    fun saveSharedSettings() {
        SharedSettingsRepository.save(context, sharedSettings)
        SyncWriteHelper.pushSharedSettings(sharedSettings)
        lastSyncActivity = System.currentTimeMillis()
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUDGET LOGIC
    // ═══════════════════════════════════════════════════════════════════

    // availableCash may go negative (= overspent). Guard against NaN/Infinity.
    fun persistAvailableCash() {
        if (availableCash.isNaN() || availableCash.isInfinite()) availableCash = 0.0
        availableCash = BudgetCalculator.roundCents(availableCash)
        android.util.Log.i("PersistCash", "Persisting availableCash=$availableCash")
        prefs.edit().putString("availableCash", availableCash.toString()).apply()
        com.syncbudget.app.widget.BudgetWidgetProvider.updateAllWidgets(context)
    }

    // ── Archive: move pre-cutoff transactions to archive file ──
    private fun applyArchiveCutoff(cutoff: java.time.LocalDate) {
        val toArchive = transactions.filter { !it.deleted && it.date.isBefore(cutoff) }
        if (toArchive.isEmpty()) return
        val archiveIds = toArchive.map { it.id }.toSet()
        viewModelScope.launch(Dispatchers.IO) {
            val existing = TransactionRepository.loadArchive(context)
            val existingIds = existing.map { it.id }.toSet()
            val newEntries = toArchive.filter { it.id !in existingIds }
            if (newEntries.isNotEmpty()) {
                TransactionRepository.saveArchive(context, existing + newEntries)
            }
        }
        transactions.removeAll { it.id in archiveIds }
        saveTransactions()
        android.util.Log.i("Archive", "Applied archive cutoff $cutoff: moved ${toArchive.size} transactions")
    }

    // ── Archive Trigger ──
    fun checkAndTriggerArchive() {
        if (archiveThreshold <= 0) return
        val count = activeTransactions.size
        if (count <= archiveThreshold) return
        if (!initialSyncReceived && isSyncConfigured) return

        val bsd = budgetStartDate ?: return
        val sorted = activeTransactions.sortedBy { it.date }
        val archiveCount = (archiveThreshold * 0.25).toInt()
        val toArchive = sorted.take(archiveCount)
        val cutoffDate = toArchive.last().date.plusDays(1)

        // Compute carry-forward balance for the archived subset
        val newCfb = BudgetCalculator.recomputeAvailableCash(
            budgetStartDate = bsd,
            periodLedgerEntries = periodLedger.toList().filter {
                val d = it.periodStartDate.toLocalDate()
                !d.isBefore(archiveCutoffDate ?: bsd) && d.isBefore(cutoffDate)
            },
            activeTransactions = toArchive.filter {
                !it.date.isBefore(archiveCutoffDate ?: bsd) && it.date.isBefore(cutoffDate)
            },
            activeRecurringExpenses = activeRecurringExpenses,
            incomeMode = incomeMode,
            activeIncomeSources = activeIncomeSources,
            carryForwardBalance = carryForwardBalance
        )

        // Build archive info
        val existingTotal = try {
            sharedSettings.lastArchiveInfo?.let { org.json.JSONObject(it) }?.optInt("totalArchived", 0) ?: 0
        } catch (_: Exception) { 0 }
        val totalArchived = existingTotal + archiveCount
        val archiveInfoJson = org.json.JSONObject().apply {
            put("date", java.time.LocalDate.now().toString())
            put("count", archiveCount)
            put("totalArchived", totalArchived)
        }.toString()

        // Append to archive file
        val archiveIds = toArchive.map { it.id }.toSet()
        viewModelScope.launch(Dispatchers.IO) {
            val existing = TransactionRepository.loadArchive(context)
            val existingIds = existing.map { it.id }.toSet()
            val newEntries = toArchive.filter { it.id !in existingIds }
            TransactionRepository.saveArchive(context, existing + newEntries)
        }

        // Remove from active list
        transactions.removeAll { it.id in archiveIds }

        // Update SharedSettings
        sharedSettings = sharedSettings.copy(
            archiveCutoffDate = cutoffDate.toString(),
            carryForwardBalance = newCfb,
            lastArchiveInfo = archiveInfoJson,
            lastChangedBy = localDeviceId
        )
        saveSharedSettings()
        saveTransactions()
        recomputeCash()

        archiveToastMessage = "Archived $archiveCount old transactions to keep the app fast"
        android.util.Log.i("Archive", "Archived $archiveCount transactions, cutoff=$cutoffDate, cfb=$newCfb")
    }

    // Deterministic cash recomputation from synced data.
    // All devices with the same synced data compute the same result.
    // Runs on background thread to avoid blocking UI.
    private suspend fun runIntegrityCheck() {
        val groupId = syncGroupId ?: return
        try {
            val collections = mapOf(
                EncryptedDocSerializer.COLLECTION_TRANSACTIONS to
                    transactions.filter { !it.deleted }.map { it.id.toString() }.toSet(),
                EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES to
                    recurringExpenses.filter { !it.deleted }.map { it.id.toString() }.toSet(),
                EncryptedDocSerializer.COLLECTION_INCOME_SOURCES to
                    incomeSources.filter { !it.deleted }.map { it.id.toString() }.toSet(),
                EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS to
                    savingsGoals.filter { !it.deleted }.map { it.id.toString() }.toSet(),
                EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES to
                    amortizationEntries.filter { !it.deleted }.map { it.id.toString() }.toSet(),
                EncryptedDocSerializer.COLLECTION_CATEGORIES to
                    categories.filter { !it.deleted }.map { it.id.toString() }.toSet(),
                EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER to
                    periodLedger.map { it.id.toString() }.toSet()
            )
            for ((collection, localIds) in collections) {
                val cacheIds = FirestoreDocService.readDocIdsFromCache(groupId, collection)
                if (cacheIds.isEmpty()) continue
                val missingFromFirestore = localIds - cacheIds
                if (missingFromFirestore.isNotEmpty()) {
                    android.util.Log.w("Integrity",
                        "$collection: ${missingFromFirestore.size} local records missing from Firestore — pushing")
                    for (id in missingFromFirestore) {
                        when (collection) {
                            EncryptedDocSerializer.COLLECTION_TRANSACTIONS ->
                                transactions.find { it.id.toString() == id }?.let { SyncWriteHelper.pushTransaction(it) }
                            EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES ->
                                recurringExpenses.find { it.id.toString() == id }?.let { SyncWriteHelper.pushRecurringExpense(it) }
                            EncryptedDocSerializer.COLLECTION_INCOME_SOURCES ->
                                incomeSources.find { it.id.toString() == id }?.let { SyncWriteHelper.pushIncomeSource(it) }
                            EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS ->
                                savingsGoals.find { it.id.toString() == id }?.let { SyncWriteHelper.pushSavingsGoal(it) }
                            EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES ->
                                amortizationEntries.find { it.id.toString() == id }?.let { SyncWriteHelper.pushAmortizationEntry(it) }
                            EncryptedDocSerializer.COLLECTION_CATEGORIES ->
                                categories.find { it.id.toString() == id }?.let { SyncWriteHelper.pushCategory(it) }
                            EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER ->
                                periodLedger.find { it.id.toString() == id }?.let { SyncWriteHelper.pushPeriodLedgerEntry(it) }
                        }
                    }
                }
            }
            android.util.Log.i("Integrity", "Cache-based integrity check complete")
        } catch (e: Exception) {
            android.util.Log.w("Integrity", "Cache integrity check failed: ${e.message}")
        }
    }

    fun recomputeCash() {
        val bsd = budgetStartDate ?: run {
            android.util.Log.w("RecomputeCash", "SKIPPED: budgetStartDate is null")
            return
        }
        val ledger = periodLedger.toList()
        val txns = activeTransactions
        val re = activeRecurringExpenses
        val mode = incomeMode
        val is_ = activeIncomeSources
        val cfb = carryForwardBalance
        val acd = archiveCutoffDate
        val caller = Thread.currentThread().stackTrace.getOrNull(3)?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" } ?: "unknown"
        android.util.Log.i("RecomputeCash", "CALLED from $caller: bsd=$bsd txns=${txns.size} ledger=${ledger.size} cfb=$cfb acd=$acd")
        val cash = BudgetCalculator.recomputeAvailableCash(
            bsd, ledger, txns, re, mode, is_, cfb, acd
        )
        android.util.Log.i("RecomputeCash", "RESULT: $cash (was $availableCash) txns=${txns.size} ledger=${ledger.size}")
        availableCash = cash
        persistAvailableCash()
    }

    // Check if an expense transaction is fully accounted for in the budget
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
    fun recurringLinkCashEffect(txn: Transaction): Double? {
        if (txn.type != TransactionType.EXPENSE || txn.linkedRecurringExpenseId == null) return null
        val rememberedAmount = if (txn.linkedRecurringExpenseAmount > 0.0) txn.linkedRecurringExpenseAmount
            else recurringExpenses.find { it.id == txn.linkedRecurringExpenseId }?.amount ?: return null
        return rememberedAmount - txn.amount
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRANSACTION HANDLING
    // ═══════════════════════════════════════════════════════════════════

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
        if (archiveThreshold > 0 && activeTransactions.size > archiveThreshold) {
            checkAndTriggerArchive()
        }
    }

    // Linking chain: recurring/amortization/income match (no duplicate check).
    // Runs search on background thread to avoid blocking UI.
    fun runLinkingChain(txn: Transaction) {
        val alreadyLinked = txn.linkedRecurringExpenseId != null || txn.linkedAmortizationEntryId != null || txn.linkedIncomeSourceId != null || txn.linkedSavingsGoalId != null
        if (!alreadyLinked) {
            val re = activeRecurringExpenses.toList()
            val ae = activeAmortizationEntries.toList()
            val is_ = activeIncomeSources.toList()
            val pTol = percentTolerance; val dTol = matchDollar; val mChars = matchChars; val dWin = matchDays
            viewModelScope.launch(Dispatchers.Default) {
                val recurringMatch = findRecurringExpenseMatch(txn, re, pTol, dTol, mChars, dWin)
                if (recurringMatch != null) {
                    withContext(Dispatchers.Main) {
                        dashPendingRecurringTxn = txn
                        dashPendingRecurringMatch = recurringMatch
                        dashShowRecurringDialog = true
                    }
                    return@launch
                }
                val amortizationMatch = findAmortizationMatch(txn, ae, pTol, dTol, mChars)
                if (amortizationMatch != null) {
                    withContext(Dispatchers.Main) {
                        dashPendingAmortizationTxn = txn
                        dashPendingAmortizationMatch = amortizationMatch
                        dashShowAmortizationDialog = true
                    }
                    return@launch
                }
                val budgetMatch = findBudgetIncomeMatch(txn, is_, mChars, dWin)
                withContext(Dispatchers.Main) {
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

    // Full matching chain: duplicate check first, then linking.
    // Runs search on background thread to avoid blocking UI.
    fun runMatchingChain(txn: Transaction) {
        val txns = activeTransactions.toList()
        val pTol = percentTolerance
        val dTol = matchDollar
        val dWin = matchDays
        val mChars = matchChars
        viewModelScope.launch(Dispatchers.Default) {
            val dup = findDuplicate(txn, txns, pTol, dTol, dWin, mChars)
            withContext(Dispatchers.Main) {
                if (dup != null) {
                    dashPendingManualSave = txn
                    dashManualDuplicateMatch = dup
                    dashShowManualDuplicateDialog = true
                } else {
                    runLinkingChain(txn)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SYNC LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Configures the sync group: creates/disposes docSync, starts listeners,
     * and launches the sync setup coroutine. Called from init (if sync configured)
     * and whenever syncGroupId changes.
     */
    fun configureSyncGroup() {
        // Dispose old docSync if any
        docSync?.dispose()
        SyncWriteHelper.dispose()
        docSync = null

        val gid = syncGroupId ?: return
        val key = GroupManager.getEncryptionKey(context) ?: return
        android.util.Log.i("SyncLifecycle", "configureSyncGroup(syncGroupId=$gid): creating NEW FirestoreDocSync")
        val newDocSync = FirestoreDocSync(context, gid, localDeviceId, key)
        docSync = newDocSync

        SyncWriteHelper.initialize(newDocSync)

        // Set onBatchChanged callback
        newDocSync.onBatchChanged = { events ->
            onBatchChanged(events)
        }

        // Re-fetch device list and RTDB presence after listener recovery
        newDocSync.onListenerRecovered = {
            viewModelScope.launch {
                try {
                    syncDevices = GroupManager.getDevices(gid)
                    cacheDeviceRoster(syncDevices)
                    android.util.Log.i("SyncLoop", "Device list refreshed after listener recovery")
                } catch (e: Exception) {
                    android.util.Log.w("SyncLoop", "Device list refresh after recovery failed: ${e.message}")
                }
                try {
                    val deviceName = GroupManager.getDeviceName(context)
                    com.syncbudget.app.data.sync.RealtimePresenceService.setupPresence(
                        gid, localDeviceId, deviceName,
                        photoCapable = isPaidUser || isSubscriber
                    )
                } catch (e: Exception) {
                    android.util.Log.w("SyncLoop", "RTDB presence re-setup after recovery failed: ${e.message}")
                }
            }
        }

        // Start real-time listeners
        try {
            newDocSync.startListeners()
            android.util.Log.i("SyncLoop", "Persistent listeners started for group $gid")
        } catch (e: Exception) {
            android.util.Log.e("SyncLoop", "Failed to start listeners", e)
        }

        // Launch sync setup coroutine
        startSyncSetup()

        // Set up RTDB presence (foreground only)
        try {
            val deviceName = GroupManager.getDeviceName(context)
            val receiptPrefs = context.getSharedPreferences("receipt_sync_prefs", Context.MODE_PRIVATE)
            val uploadSpeed = receiptPrefs.getLong("lastUploadSpeedBps", 0L)
            val speedMeasuredAt = receiptPrefs.getLong("lastSpeedMeasuredAt", 0L)
            com.syncbudget.app.data.sync.RealtimePresenceService.setupPresence(
                gid, localDeviceId, deviceName,
                photoCapable = isPaidUser || isSubscriber,
                uploadSpeedBps = uploadSpeed,
                uploadSpeedMeasuredAt = speedMeasuredAt
            )
            com.syncbudget.app.data.sync.RealtimePresenceService.listenToGroupPresence(gid) { presenceRecords ->
                // Merge RTDB presence with existing syncDevices metadata (admin status)
                val currentDevices = syncDevices
                val updatedDevices = currentDevices.map { device ->
                    val presence = presenceRecords.find { it.deviceId == device.deviceId }
                    if (presence != null) {
                        device.copy(
                            online = presence.online,
                            lastSeen = maxOf(device.lastSeen, presence.lastSeen),
                            deviceName = if (presence.deviceName.isNotEmpty()) presence.deviceName else device.deviceName,
                            photoCapable = presence.photoCapable,
                            uploadSpeedBps = presence.uploadSpeedBps,
                            uploadSpeedMeasuredAt = presence.uploadSpeedMeasuredAt
                        )
                    } else device
                }
                // Add devices in RTDB not yet in Firestore device list
                val existingIds = updatedDevices.map { it.deviceId }.toSet()
                val newDevices = presenceRecords
                    .filter { it.deviceId !in existingIds }
                    .map { DeviceInfo(it.deviceId, it.deviceName, isAdmin = false, lastSeen = it.lastSeen,
                        online = it.online, photoCapable = it.photoCapable,
                        uploadSpeedBps = it.uploadSpeedBps, uploadSpeedMeasuredAt = it.uploadSpeedMeasuredAt) }
                syncDevices = updatedDevices + newDevices
                cacheDeviceRoster(syncDevices)
            }
        } catch (e: Exception) {
            android.util.Log.w("SyncLoop", "RTDB presence setup failed (may not be configured): ${e.message}")
        }
    }

    /**
     * Disposes sync listeners without clearing docSync reference.
     */
    fun disposeSyncListeners() {
        docSync?.dispose()
        SyncWriteHelper.dispose()
        com.syncbudget.app.data.sync.RealtimePresenceService.cleanup()
        imageLedgerListener?.remove()
        imageLedgerListener = null
    }

    /**
     * The onBatchChanged callback: update in-memory state when remote changes arrive.
     */
    private fun onBatchChanged(events: List<com.syncbudget.app.data.sync.DataChangeEvent>) {
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
                currentBudgetStartDate = budgetStartDate,
                archiveCutoffDate = archiveCutoffDate
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

            // Handle incoming archiveCutoffDate from another device
            result.sharedSettings?.let { merged ->
                val newCutoff = merged.archiveCutoffDate?.let {
                    try { java.time.LocalDate.parse(it) } catch (_: Exception) { null }
                }
                if (newCutoff != null) {
                    val oldCutoff = archiveCutoffDate
                    if (oldCutoff == null || newCutoff.isAfter(oldCutoff)) {
                        applyArchiveCutoff(newCutoff)
                    }
                }
            }

            // Check if archive trigger needed after receiving new transactions
            if (result.transactions != null && archiveThreshold > 0 && activeTransactions.size > archiveThreshold) {
                checkAndTriggerArchive()
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

            // Save archived incoming transactions to archive file
            val archivedSnap = result.archivedIncoming
            if (archivedSnap.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    val existing = TransactionRepository.loadArchive(context)
                    val existingIds = existing.map { it.id }.toSet()
                    val newEntries = archivedSnap.filter { it.id !in existingIds }
                    if (newEntries.isNotEmpty()) {
                        TransactionRepository.saveArchive(context, existing + newEntries)
                    }
                }
            }

            // Save changed collections to JSON on background thread
            val txnSnap = result.transactions
            val reSnap = result.recurringExpenses
            val isSnap = result.incomeSources
            val sgSnap = result.savingsGoals
            val aeSnap = result.amortizationEntries
            val catSnap = result.categories
            val pleSnap = result.periodLedger
            val ssSnap = result.sharedSettings
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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

            // Check for missing receipt photos from newly-arrived transactions
            if ((isPaidUser || isSubscriber) && isSyncConfigured) {
                val updatedTxns = result.transactions
                if (updatedTxns != null) {
                    val missingReceiptIds = com.syncbudget.app.data.sync.ReceiptManager.collectAllReceiptIds(updatedTxns)
                        .filter { !com.syncbudget.app.data.sync.ReceiptManager.hasLocalFile(context, it) }
                    if (missingReceiptIds.isNotEmpty()) {
                        val currentGroupId = syncGroupId ?: return
                        val currentKey = GroupManager.getEncryptionKey(context) ?: return
                        viewModelScope.launch {
                            // Download up to 5 receipts in parallel
                            missingReceiptIds.chunked(5).forEach { chunk ->
                                kotlinx.coroutines.coroutineScope {
                                    for (receiptId in chunk) {
                                        launch {
                                            try {
                                                val data = com.syncbudget.app.data.sync.ImageLedgerService.downloadFromCloud(currentGroupId, receiptId)
                                                if (data != null) {
                                                    com.syncbudget.app.data.sync.ReceiptManager.decryptAndSave(context, receiptId, data, currentKey)
                                                    com.syncbudget.app.data.sync.ImageLedgerService.markPossession(currentGroupId, receiptId, localDeviceId)
                                                    android.util.Log.i("ReceiptSync", "Downloaded receipt $receiptId on transaction arrival")
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.w("ReceiptSync", "Failed to download receipt $receiptId: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            }
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

    /**
     * Sync setup: migrations, one-time pushes, and health check loop.
     */
    private fun startSyncSetup() {
        viewModelScope.launch {
            if (!isSyncConfigured) return@launch
            val groupId = GroupManager.getGroupId(context) ?: return@launch
            val key = GroupManager.getEncryptionKey(context) ?: return@launch
            val ds = docSync ?: return@launch

            // Initial device list fetch (retry once on failure)
            try {
                syncDevices = GroupManager.getDevices(groupId)
                cacheDeviceRoster(syncDevices)
            } catch (e: Exception) {
                android.util.Log.w("SyncLoop", "Failed to fetch initial device list, retrying in 10s", e)
                kotlinx.coroutines.delay(10_000)
                try {
                    syncDevices = GroupManager.getDevices(groupId)
                    cacheDeviceRoster(syncDevices)
                } catch (e2: Exception) {
                    android.util.Log.w("SyncLoop", "Device list retry also failed — will recover via listener callback", e2)
                }
            }

            // Write device capabilities once on foreground launch
            try {
                FirestoreService.updateDeviceMetadata(
                    groupId, localDeviceId,
                    syncVersion = 0L,
                    appSyncVersion = 2,
                    minSyncVersion = 2,
                    photoCapable = isPaidUser || isSubscriber
                )
            } catch (e: Exception) {
                android.util.Log.w("SyncLoop", "Device metadata write failed: ${e.message}")
            }

            // Register FCM token for push notifications (always fetch fresh — token
            // can become stale after app update without uninstall)
            try {
                val fcmPrefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                val fcmToken = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                    .token.await()
                if (fcmToken != null) {
                    fcmPrefs.edit().putString("fcm_token", fcmToken).apply()
                    FirestoreService.storeFcmToken(groupId, localDeviceId, fcmToken)
                    fcmPrefs.edit().putBoolean("token_needs_upload", false).apply()
                }
            } catch (e: Exception) {
                android.util.Log.w("FCM", "Token registration failed: ${e.message}")
            }

            // One-time cleanup: remove skeleton categories (empty name)
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
                    ds.pushAllRecords(
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
                    ds.pushAllRecords(
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

            // Admin: tombstone + orphan cleanup (skip if ran within last 30 days)
            if (isSyncAdmin) {
                val lastCleanup = syncPrefs.getLong("lastAdminCleanup", 0L)
                val cleanupInterval = 30L * 24 * 60 * 60 * 1000 // 30 days
                if (System.currentTimeMillis() - lastCleanup > cleanupInterval) {
                    // Clean up Firestore tombstones that all devices have seen.
                    // Uses RTDB-maintained syncDevices for lastSeen (1-day buffer for safety).
                    try {
                        val devices = syncDevices
                        if (devices.size >= 2) {
                            val oldestLastSeen = devices.minOf { it.lastSeen }
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

                    syncPrefs.edit().putLong("lastAdminCleanup", System.currentTimeMillis()).apply()
                }
            }

            // Image ledger listener — replaces receipt sync polling in health check
            if (isPaidUser || isSubscriber) {
                val ledgerRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("groups").document(groupId).collection("imageLedger")
                imageLedgerListener = ledgerRef.addSnapshotListener { snapshot, err ->
                    if (err != null || snapshot == null) return@addSnapshotListener
                    val currentGroupId = groupId
                    val currentKey = key
                    viewModelScope.launch {
                        try {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document
                                val receiptId = doc.getString("receiptId") ?: doc.id
                                val uploadedAt = doc.getLong("uploadedAt") ?: 0L
                                @Suppress("UNCHECKED_CAST")
                                val possessions = doc.get("possessions") as? Map<String, Any> ?: emptyMap()

                                when {
                                    // File uploaded but we don't have it — download
                                    uploadedAt > 0L && !com.syncbudget.app.data.sync.ReceiptManager.hasLocalFile(context, receiptId) -> {
                                        val data = com.syncbudget.app.data.sync.ImageLedgerService.downloadFromCloud(currentGroupId, receiptId)
                                        if (data != null) {
                                            com.syncbudget.app.data.sync.ReceiptManager.decryptAndSave(context, receiptId, data, currentKey)
                                            com.syncbudget.app.data.sync.ImageLedgerService.markPossession(currentGroupId, receiptId, localDeviceId)
                                        }
                                    }
                                    // File uploaded and we have it — mark possession + prune check
                                    uploadedAt > 0L && com.syncbudget.app.data.sync.ReceiptManager.hasLocalFile(context, receiptId) -> {
                                        if (!possessions.containsKey(localDeviceId)) {
                                            com.syncbudget.app.data.sync.ImageLedgerService.markPossession(currentGroupId, receiptId, localDeviceId)
                                        }
                                        val photoCapableIds = syncDevices.filter { /* need device records */ true }.map { it.deviceId }.toSet()
                                        // Note: prune check needs photo-capable device IDs from Firestore device records
                                        // This is done when full receipt sync runs (background worker or manual sync)
                                    }
                                    // Recovery request — we have the file locally, re-upload it
                                    uploadedAt == 0L && com.syncbudget.app.data.sync.ReceiptManager.hasLocalFile(context, receiptId) -> {
                                        val encrypted = com.syncbudget.app.data.sync.ReceiptManager.encryptForUpload(context, receiptId, currentKey)
                                        if (encrypted != null) {
                                            val uploaded = com.syncbudget.app.data.sync.ImageLedgerService.uploadToCloud(currentGroupId, receiptId, encrypted)
                                            if (uploaded) {
                                                com.syncbudget.app.data.sync.ImageLedgerService.markReuploadComplete(currentGroupId, receiptId)
                                                android.util.Log.i("ReceiptLedger", "Re-uploaded $receiptId for recovery")
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("ReceiptLedger", "Ledger listener processing failed: ${e.message}")
                        }
                    }
                }
            }

            syncStatus = if (isNetworkAvailable) "synced" else "offline"
            lastSyncActivity = System.currentTimeMillis()

            // ── One-time startup health check ──
            // Wait for all collection listeners to deliver (up to 30s for large datasets)
            val allSynced = docSync?.awaitInitialSync(30_000) ?: false
            if (!allSynced) {
                android.util.Log.w("SyncLoop", "Initial sync timed out after 30s — proceeding with available data")
            }

            try {
                // Single group doc read for dissolution + subscription expiry
                val groupHealth = FirestoreService.getGroupHealthStatus(groupId)

                // Check group dissolution (from single group doc read)
                if (groupHealth.isDissolved) {
                    GroupManager.leaveGroup(context, localOnly = true)
                    isSyncConfigured = false
                    syncGroupId = null
                    isSyncAdmin = false
                    syncStatus = "off"
                    syncDevices = emptyList(); syncPrefs.edit().remove("cachedDeviceRoster").apply()
                    return@launch
                }

                // Check device removal (separate doc)
                if (FirestoreService.isDeviceRemoved(groupId, localDeviceId)) {
                    GroupManager.leaveGroup(context, localOnly = true)
                    isSyncConfigured = false
                    syncGroupId = null
                    isSyncAdmin = false
                    syncStatus = "off"
                    syncDevices = emptyList(); syncPrefs.edit().remove("cachedDeviceRoster").apply()
                    return@launch
                }

                // Subscription expiry check (from same group doc read)
                val expiry = groupHealth.subscriptionExpiry
                if (expiry > 0L) {
                    val gracePeriodMs = 7L * 24 * 60 * 60 * 1000
                    val elapsed = System.currentTimeMillis() - expiry
                    if (elapsed > gracePeriodMs) {
                        if (!groupHealth.isDissolved) {
                            GroupManager.dissolveGroup(context, groupId)
                        }
                        isSyncConfigured = false
                        syncGroupId = null
                        isSyncAdmin = false
                        syncStatus = "off"
                        syncDevices = emptyList(); syncPrefs.edit().remove("cachedDeviceRoster").apply()
                        return@launch
                    } else if (elapsed > 0) {
                        syncErrorMessage = strings.sync.subscriptionExpiredNotice
                        SubscriptionReminderReceiver.scheduleNextReminder(context)
                    } else {
                        SubscriptionReminderReceiver.cancelReminder(context)
                    }
                }

                // Admin: post subscription expiry
                if (isSyncAdmin && isSubscriber) {
                    FirestoreService.updateSubscriptionExpiry(groupId, subscriptionExpiry)
                }

                // Update group activity timestamp (keeps group alive for TTL)
                FirestoreService.updateGroupActivity(groupId)

                // Clear stale dirty flag
                if (syncPrefs.getBoolean("syncDirty", false)) {
                    syncPrefs.edit().putBoolean("syncDirty", false).apply()
                }

                runIntegrityCheck()
                recomputeCash()
                android.util.Log.i("Integrity", "Startup cash verification: $availableCash")

                // Post-initial-sync: sort pre-cutoff transactions into archive
                // Handles new device (full Firestore read) and SharedSettings arriving
                // after transactions in the same initial sync batch
                val cutoff = archiveCutoffDate
                if (cutoff != null) {
                    applyArchiveCutoff(cutoff)
                }
            } catch (e: Exception) {
                android.util.Log.w("SyncLoop", "Startup health check failed", e)
            }
        }
    }

    /**
     * Manual sync-now action.
     */
    fun doSyncNow() {
        viewModelScope.launch {
            val gId = GroupManager.getGroupId(context) ?: return@launch
            val key = GroupManager.getEncryptionKey(context) ?: return@launch
            syncStatus = "syncing"
            try {
                // Ensure listeners are alive
                val ds = docSync
                if (ds != null && !ds.isListening) {
                    ds.startListeners()
                }
                // Trigger receipt photo sync (paid users only)
                isSyncAdmin = GroupManager.isAdmin(context)
                if (isPaidUser || isSubscriber) {
                    try {
                        val receiptSync = com.syncbudget.app.data.sync.ReceiptSyncManager(
                            context, gId, localDeviceId, key
                        )
                        val updatedTxns = receiptSync.syncReceipts(transactions.toList(), syncDevices)
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

    // ═══════════════════════════════════════════════════════════════════
    // RESUME HANDLER
    // ═══════════════════════════════════════════════════════════════════

    fun onResume() {
        if (!dataLoaded) return  // Initial async load in progress
        syncTrigger++
        android.util.Log.i("OnResume", "onResume called: dataLoaded=$dataLoaded txns=${transactions.size} pl=${periodLedger.size} availableCash=$availableCash")
        // Reload transactions from disk on resume to pick up widget-added entries
        val diskTransactions = TransactionRepository.load(context)
        if (diskTransactions.size != transactions.size ||
            diskTransactions.map { it.id }.toSet() != transactions.map { it.id }.toSet()) {
            android.util.Log.i("OnResume", "Transaction mismatch: memory=${transactions.size} disk=${diskTransactions.size}, reloading")
            transactions.clear()
            transactions.addAll(diskTransactions)
            recomputeCash()
        } else {
            android.util.Log.i("OnResume", "Transactions match disk, no reload needed")
        }

        // Time-gated maintenance (once per 24h) — keeps long-lived ViewModels healthy
        val now = System.currentTimeMillis()
        val lastMaintenance = prefs.getLong("lastMaintenanceCheck", 0L)
        if (now - lastMaintenance > 24 * 60 * 60 * 1000L) {
            prefs.edit().putLong("lastMaintenanceCheck", now).apply()
            viewModelScope.launch {
                // Backup check
                if (BackupManager.isBackupDue(context)) {
                    val pwd = BackupManager.getPassword(context)
                    if (pwd != null) {
                        withContext(Dispatchers.IO) { BackupManager.performBackup(context, pwd) }
                        lastBackupDate = backupPrefs.getString("last_backup_date", null)
                    }
                }
                // Integrity check
                if (isSyncConfigured && syncGroupId != null) {
                    runIntegrityCheck()
                    recomputeCash()
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WIDGET INTENT HANDLER
    // ═══════════════════════════════════════════════════════════════════

    fun handleWidgetIntent(action: String?) {
        when (action) {
            com.syncbudget.app.widget.BudgetWidgetProvider.ACTION_ADD_INCOME -> dashboardShowAddIncome = true
            com.syncbudget.app.widget.BudgetWidgetProvider.ACTION_ADD_EXPENSE -> dashboardShowAddExpense = true
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FULL STATE RELOAD (for backup restore)
    // ═══════════════════════════════════════════════════════════════════

    fun reloadAllFromDisk() {
        // Reload all data lists
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
    }

    // ═══════════════════════════════════════════════════════════════════
    // INIT — Data Loading + Background Loops
    // ═══════════════════════════════════════════════════════════════════

    init {
        instance = java.lang.ref.WeakReference(this)

        // App Check is initialized in BudgeTrakApplication.onCreate() so it
        // runs even when the process is started by WorkManager without an Activity.

        // ── Network connectivity monitor ──
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            // Set initial state
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            isNetworkAvailable = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            // Set initial sync status based on actual network state
            if (!isNetworkAvailable && isSyncConfigured) syncStatus = "offline"

            connectivityManager.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    isNetworkAvailable = true
                    // Restore sync status if it was showing offline
                    if (isSyncConfigured && syncStatus == "offline") {
                        syncStatus = if (docSync?.isListening == true) "synced" else "error"
                    }
                }
                override fun onLost(network: android.net.Network) {
                    isNetworkAvailable = false
                    if (isSyncConfigured) syncStatus = "offline"
                }
            })
        } catch (e: Exception) {
            android.util.Log.w("Network", "Connectivity monitor failed: ${e.message}")
        }

        // ── Load data from repositories (async on IO, learned-timing progress) ──
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.i("DataLoad", "START: loading repos on ${Thread.currentThread().name}")

            // Read stored segment times (EMA-smoothed from previous launches)
            // Defaults based on measured times: txns ~50ms, cats ~10ms, others ~5ms each
            val defaultTimes = floatArrayOf(50f, 10f, 5f, 5f, 5f, 5f, 5f)
            val hasStoredTimes = prefs.contains("loadSegTime_0")
            val storedTimes = FloatArray(7) { prefs.getFloat("loadSegTime_$it", defaultTimes[it]) }
            val segTimes = storedTimes.clone()
            val total = segTimes.sum().coerceAtLeast(1f)
            val boundaries = FloatArray(8)
            var cum = 0f
            for (i in segTimes.indices) { boundaries[i] = cum / total; cum += segTimes[i] }
            boundaries[7] = 1f

            val actualTimes = FloatArray(7)
            val loadStartNanos = System.nanoTime()
            var segIdx = 0
            var segStartNanos = loadStartNanos

            // Ticker: interpolate progress within current segment at ~60fps
            val ticker = launch {
                while (true) {
                    val i = segIdx
                    val elapsed = (System.nanoTime() - segStartNanos) / 1_000_000f
                    val fraction = (elapsed / segTimes[i].coerceAtLeast(1f)).coerceAtMost(1f)
                    loadProgress = boundaries[i] + (boundaries[i + 1] - boundaries[i]) * fraction
                    delay(16)
                }
            }

            // Segment 0: Transactions
            val loadedTxns = TransactionRepository.load(context)
            actualTimes[0] = (System.nanoTime() - segStartNanos) / 1_000_000f
            android.util.Log.i("DataLoad", "transactions: ${loadedTxns.size}")

            // Segment 1: Categories
            segIdx = 1; segStartNanos = System.nanoTime()
            val loadedCats = CategoryRepository.load(context).toMutableList()
            var catsChanged = false
            for (def in DEFAULT_CATEGORY_DEFS) {
                val byTag = loadedCats.indexOfFirst { it.tag == def.tag }
                if (byTag >= 0) continue
                val usedIds = loadedCats.map { it.id }.toSet()
                var id: Int
                do { id = (0..65535).random() } while (id in usedIds)
                val name = getDefaultCategoryName(def.tag, strings) ?: def.tag
                val devId = SyncIdGenerator.getOrCreateDeviceId(context)
                loadedCats.add(Category(id = id, name = name, iconName = def.iconName, tag = def.tag,
                    charted = def.charted, widgetVisible = def.widgetVisible,
                    deviceId = devId))
                catsChanged = true
            }
            if (catsChanged) CategoryRepository.save(context, loadedCats)
            actualTimes[1] = (System.nanoTime() - segStartNanos) / 1_000_000f

            // Segment 2: Income Sources
            segIdx = 2; segStartNanos = System.nanoTime()
            val loadedIs = IncomeSourceRepository.load(context)
            actualTimes[2] = (System.nanoTime() - segStartNanos) / 1_000_000f

            // Segment 3: Recurring Expenses
            segIdx = 3; segStartNanos = System.nanoTime()
            val loadedRe = RecurringExpenseRepository.load(context)
            actualTimes[3] = (System.nanoTime() - segStartNanos) / 1_000_000f

            // Segment 4: Amortization Entries
            segIdx = 4; segStartNanos = System.nanoTime()
            val loadedAe = AmortizationRepository.load(context)
            actualTimes[4] = (System.nanoTime() - segStartNanos) / 1_000_000f

            // Segment 5: Savings Goals
            segIdx = 5; segStartNanos = System.nanoTime()
            val loadedSg = SavingsGoalRepository.load(context)
            actualTimes[5] = (System.nanoTime() - segStartNanos) / 1_000_000f

            // Segment 6: Period Ledger
            segIdx = 6; segStartNanos = System.nanoTime()
            val loadedPl = PeriodLedgerRepository.load(context)
            actualTimes[6] = (System.nanoTime() - segStartNanos) / 1_000_000f

            // Minimum display time so the loading screen doesn't just flash
            val totalMs = (System.nanoTime() - loadStartNanos) / 1_000_000L
            val minDisplayMs = 500L
            if (totalMs < minDisplayMs) delay(minDisplayMs - totalMs)

            ticker.cancel()
            loadProgress = 1f

            // Update stored times: first run saves actual directly, subsequent uses EMA
            prefs.edit().apply {
                for (i in 0 until 7) {
                    val updated = if (hasStoredTimes) (4 * storedTimes[i] + actualTimes[i]) / 5f else actualTimes[i]
                    putFloat("loadSegTime_$i", updated)
                }
                apply()
            }
            android.util.Log.i("DataLoad", "segment ms: ${actualTimes.map { "%.1f".format(it) }}")

            withContext(Dispatchers.Main) {
                transactions.addAll(loadedTxns)
                categories.addAll(loadedCats)
                incomeSources.addAll(loadedIs)
                recurringExpenses.addAll(loadedRe)
                amortizationEntries.addAll(loadedAe)
                savingsGoals.addAll(loadedSg)
                periodLedger.addAll(loadedPl)
                android.util.Log.i("DataLoad", "DONE: txns=${transactions.size} cats=${categories.size} re=${recurringExpenses.size} is=${incomeSources.size} ae=${amortizationEntries.size} sg=${savingsGoals.size} pl=${periodLedger.size}")
                dataLoaded = true
                android.util.Log.i("DataLoad", "dataLoaded=true, availableCash=$availableCash")
            }
        }

        // ── Firebase anonymous auth ──
        viewModelScope.launch {
            if (!firebaseAuthReady) {
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance()
                        .signInAnonymously()
                        .await()
                    firebaseAuthReady = true
                } catch (e: Exception) {
                    android.util.Log.w("Auth", "Anonymous sign-in failed: ${e.message}")
                }
            }
        }

        // ── Sync time display ──
        // Immediately update when lastSyncActivity changes (mirrors old LaunchedEffect(lastSyncActivity))
        viewModelScope.launch {
            snapshotFlow { lastSyncActivity }.collect { syncTime ->
                val elapsed = if (syncTime > 0) (System.currentTimeMillis() - syncTime) / 1000 else -1L
                lastSyncTimeDisplay = when {
                    elapsed < 0 -> null
                    elapsed < 10 -> "just now"
                    elapsed < 60 -> "${elapsed}s ago"
                    elapsed < 3600 -> "${elapsed / 60}m ago"
                    else -> "${elapsed / 3600}h ago"
                }
            }
        }
        // Also refresh every 10s so elapsed time ticks forward ("5s ago" → "15s ago" → etc.)
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                val elapsed = if (lastSyncActivity > 0) (System.currentTimeMillis() - lastSyncActivity) / 1000 else -1L
                lastSyncTimeDisplay = when {
                    elapsed < 0 -> null
                    elapsed < 10 -> "just now"
                    elapsed < 60 -> "${elapsed}s ago"
                    elapsed < 3600 -> "${elapsed / 60}m ago"
                    else -> "${elapsed / 3600}h ago"
                }
            }
        }

        // ── Sync group configuration (waits for data) ──
        viewModelScope.launch {
            snapshotFlow { dataLoaded }.first { it }
            if (isSyncConfigured && syncGroupId != null) {
                configureSyncGroup()
            }
            if (isSyncConfigured) {
                com.syncbudget.app.data.sync.BackgroundSyncWorker.schedule(context)
            }
        }

        // ── One-time migrations ──
        viewModelScope.launch {
            snapshotFlow { dataLoaded }.first { it }
            // Wait for all listeners to deliver before running migrations.
            if (isSyncConfigured && !initialSyncReceived) {
                val synced = docSync?.awaitInitialSync(30_000) ?: false
                initialSyncReceived = true
                if (!synced) {
                    android.util.Log.w("Migration", "Initial sync timed out — proceeding with available data")
                }
            }
            // Purge tombstoned records (deleted=true) from local JSON.
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

            // One-time dedup
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

            // One-time migration: assign "supercharge" category to existing savings goal deposit transactions
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

            // Ensure BudgeTrak directory tree exists
            BackupManager.getBudgetrakDir()
            BackupManager.getSupportDir()
            BackupManager.getBackupDir()

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

        // ── Quick start auto-launch (one-time) ──
        viewModelScope.launch {
            snapshotFlow { dataLoaded }.first { it }
            if (incomeSources.isEmpty() && !isSyncConfigured &&
                !prefs.getBoolean("quickStartCompleted", false)) {
                quickStartStep = QuickStartStep.WELCOME
            }
        }

        // ── One-time simulation trace on startup ──
        viewModelScope.launch {
            snapshotFlow { dataLoaded }.first { it }
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

        // ── Period refresh loop (every 30s, waits for initialSyncReceived) ──
        viewModelScope.launch {
            snapshotFlow { dataLoaded }.first { it }
            // Wait for initial sync if needed
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
                        // Push to Firestore via save functions (hint-based)
                        if (result.newLedgerEntries.isNotEmpty()) savePeriodLedger(result.newLedgerEntries)
                        if (result.updatedSavingsGoals.isNotEmpty()) saveSavingsGoals(result.updatedSavingsGoals)
                        if (result.updatedRecurringExpenses.isNotEmpty()) saveRecurringExpenses(result.updatedRecurringExpenses)
                    }
                }
                delay(30_000) // Re-check every 30 seconds
            }
        }

        // ── Debug dump (one-time, debug builds only) ──
        if (BuildConfig.DEBUG) {
            viewModelScope.launch {
                snapshotFlow { dataLoaded }.first { it }
                try {
                    val diagText = DiagDumpBuilder.build(context, simAvailableCash = simAvailableCash)
                    DiagDumpBuilder.writeDiagToMediaStore(context, "sync_diag.txt", diagText)
                    val devName = DiagDumpBuilder.sanitizeDeviceName(GroupManager.getDeviceName(context))
                    if (devName.isNotEmpty()) {
                        DiagDumpBuilder.writeDiagToMediaStore(context, "sync_diag_${devName}.txt", diagText)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DiagDump", "Diag write failed: ${e.message}")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════

    override fun onCleared() {
        instance = null
        docSync?.dispose()
        SyncWriteHelper.dispose()
        com.syncbudget.app.data.sync.RealtimePresenceService.cleanup()
        imageLedgerListener?.remove()
    }
}
