package com.techadvantage.budgetrak

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
import com.techadvantage.budgetrak.data.AmortizationEntry
import com.techadvantage.budgetrak.data.AmortizationRepository
import com.techadvantage.budgetrak.data.BackupManager
import com.techadvantage.budgetrak.data.BudgetCalculator
import com.techadvantage.budgetrak.data.BudgetPeriod
import com.techadvantage.budgetrak.data.Category
import com.techadvantage.budgetrak.data.CategoryAmount
import com.techadvantage.budgetrak.data.CategoryRepository
import com.techadvantage.budgetrak.data.DEFAULT_CATEGORY_DEFS
import com.techadvantage.budgetrak.data.DiagDumpBuilder
import com.techadvantage.budgetrak.data.FullBackupSerializer
import com.techadvantage.budgetrak.data.IncomeMode
import com.techadvantage.budgetrak.data.IncomeSource
import com.techadvantage.budgetrak.data.IncomeSourceRepository
import com.techadvantage.budgetrak.data.PeriodRefreshService
import com.techadvantage.budgetrak.data.RecurringExpense
import com.techadvantage.budgetrak.data.RecurringExpenseRepository
import com.techadvantage.budgetrak.data.RepeatType
import com.techadvantage.budgetrak.data.SavingsGoal
import com.techadvantage.budgetrak.data.SavingsGoalRepository
import com.techadvantage.budgetrak.data.SavingsSimulator
import com.techadvantage.budgetrak.data.SharedSettings
import com.techadvantage.budgetrak.data.SharedSettingsRepository
import com.techadvantage.budgetrak.data.Transaction
import com.techadvantage.budgetrak.data.TransactionRepository
import com.techadvantage.budgetrak.data.TransactionType
import com.techadvantage.budgetrak.data.findAmortizationMatches
import com.techadvantage.budgetrak.data.findBudgetIncomeMatches
import com.techadvantage.budgetrak.data.findDuplicates
import com.techadvantage.budgetrak.data.findRecurringExpenseMatches
import com.techadvantage.budgetrak.data.ocr.OcrState
import com.techadvantage.budgetrak.data.ocr.ReceiptOcrService
import com.techadvantage.budgetrak.data.generateTransactionId
import com.techadvantage.budgetrak.data.getDefaultCategoryName
import com.techadvantage.budgetrak.data.getDoubleCompat
import com.techadvantage.budgetrak.data.isRecurringDateCloseEnough
import com.techadvantage.budgetrak.data.sync.DeviceInfo
import com.techadvantage.budgetrak.data.sync.EncryptedDocSerializer
import com.techadvantage.budgetrak.data.sync.FcmSender
import com.techadvantage.budgetrak.data.sync.FirestoreDocService
import com.techadvantage.budgetrak.data.sync.FirestoreDocSync
import com.techadvantage.budgetrak.data.sync.FirestoreService
import com.techadvantage.budgetrak.data.sync.GroupManager
import com.techadvantage.budgetrak.data.sync.PeriodLedgerEntry
import com.techadvantage.budgetrak.data.sync.PeriodLedgerRepository
import com.techadvantage.budgetrak.data.sync.SyncIdGenerator
import com.techadvantage.budgetrak.data.sync.SyncMergeProcessor
import com.techadvantage.budgetrak.data.sync.SyncWriteHelper
import com.techadvantage.budgetrak.data.sync.active
import com.techadvantage.budgetrak.ui.screens.QuickStartStep
import com.techadvantage.budgetrak.ui.strings.AppStrings
import com.techadvantage.budgetrak.ui.strings.EnglishStrings
import com.techadvantage.budgetrak.ui.strings.SpanishStrings
import com.techadvantage.budgetrak.data.sync.AdminClaim
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    // One-shot scroll-target hint consumed by TransactionsHelpScreen on next
    // composition so deep-links (e.g. "explain preselected cats") can jump
    // the user to a specific subsection instead of the top of the help.
    var transactionsHelpScrollTo by mutableStateOf<String?>(null)

    // Help overlay shown ON TOP of an open transaction dialog (preselect-help
    // banner tap). Renders the TransactionsHelpScreen as a fullscreen overlay
    // so the dialog stays mounted underneath and the user's in-progress
    // entries/photos survive the help round-trip.
    var transactionsHelpOverlayShowing by mutableStateOf(false)

    // ── Dashboard quick-add dialog state ──
    var dashboardShowAddIncome by mutableStateOf(false)
    var dashboardShowAddExpense by mutableStateOf(false)

    // ── Share-intent image (receipt shared from another app) ──
    // Set by MainActivity on ACTION_SEND / ACTION_SEND_MULTIPLE; consumed by a
    // LaunchedEffect gated on dataLoaded. List so multiple shared images go into
    // multiple photo slots on the resulting transaction dialog.
    val pendingSharedImageUris = mutableStateListOf<android.net.Uri>()
    // After processing the first URI, holds the receiptId so a fresh TransactionDialog
    // can seed it into slot 1 via initialReceiptId1. Any additional shared URIs are
    // absorbed post-open by TransactionDialog's LaunchedEffect.
    var pendingSharedReceiptId by mutableStateOf<String?>(null)
    // Signals MainActivity to show a 5s "photos require paid/subscriber" toast when a
    // free user shares an image (dialog still opens, but the photo is discarded).
    var sharedPhotoBlockedToastPending by mutableStateOf(false)
    // Signals MainActivity to show a toast when a share arrives while a non-transaction
    // dialog or off-dashboard screen is active — URIs are dropped; user asked to retry.
    var shareBlockedByDialogToastPending by mutableStateOf(false)
    // Signals MainActivity to show a toast when more photos were shared than fit
    // in the remaining receipt slots — overflow is discarded.
    var shareOverflowToastPending by mutableStateOf(false)
    // TransactionDialog increments/decrements this via DisposableEffect so share-
    // handling code can route shared images into an open dialog instead of dropping
    // them or opening a competing second dialog.
    var transactionDialogOpenCount by mutableIntStateOf(0)
    val transactionDialogOpen: Boolean get() = transactionDialogOpenCount > 0
    // Set by TransactionsScreen when the sequential CSV import is in its user-
    // driven duplicate-check phase. Interrupting that phase would lose the user's
    // progress through the match queue, so shares arriving mid-import are blocked.
    // Other CSV stages (format select, parsing, complete) are safe to navigate away
    // from — they don't carry multi-step user state.
    var csvImportInProgress by mutableStateOf(false)
    // ⚠️  SCOPE: share-intent blocking ONLY. Do not read this counter for
    // other purposes (e.g. suppressing back-navigation, deferring background
    // work). The AdAware dialog wrappers increment this on every dialog —
    // including brief pickers and confirmations — and any other consumer
    // would block on those spuriously. If a new mechanism needs a similar
    // signal, add a separately-named counter with its own CompositionLocal.
    //
    // Each dialog increments on compose and decrements on dispose via the
    // LocalShareBlockingDialogRegistrar CompositionLocal. The share handler
    // treats any non-zero value as "don't wipe the user's in-progress edit."
    var shareBlockingDialogCount by mutableIntStateOf(0)

    // ── AI OCR ──
    var ocrState by mutableStateOf<OcrState>(OcrState.Idle)

    // Dashboard matching state
    var dashPendingManualSave by mutableStateOf<Transaction?>(null)
    var dashManualDuplicateMatches by mutableStateOf<List<Transaction>>(emptyList())
    var dashShowManualDuplicateDialog by mutableStateOf(false)

    var dashPendingRecurringTxn by mutableStateOf<Transaction?>(null)
    var dashPendingRecurringMatches by mutableStateOf<List<RecurringExpense>>(emptyList())
    var dashShowRecurringDialog by mutableStateOf(false)

    var dashPendingAmortizationTxn by mutableStateOf<Transaction?>(null)
    var dashPendingAmortizationMatches by mutableStateOf<List<AmortizationEntry>>(emptyList())
    var dashShowAmortizationDialog by mutableStateOf(false)

    var dashPendingBudgetIncomeTxn by mutableStateOf<Transaction?>(null)
    var dashPendingBudgetIncomeMatches by mutableStateOf<List<IncomeSource>>(emptyList())
    var dashShowBudgetIncomeDialog by mutableStateOf(false)

    // Pending amount-change confirmations (apply to past transactions?)
    var pendingREAmountUpdate by mutableStateOf<Pair<RecurringExpense, Double>?>(null) // (updated, oldAmount)
    var pendingISAmountUpdate by mutableStateOf<Pair<IncomeSource, Double>?>(null) // (updated, oldAmount)

    // ── App Config ──
    var currencySymbol by mutableStateOf(prefs.getString("currencySymbol", "$") ?: "$")
    var digitCount by mutableIntStateOf(prefs.getInt("digitCount", 3))
    var showDecimals by mutableStateOf(prefs.getBoolean("showDecimals", false))
    var showAttribution by mutableStateOf(prefs.getBoolean("showAttribution", false))
    var dateFormatPattern by mutableStateOf(prefs.getString("dateFormatPattern", "yyyy-MM-dd") ?: "yyyy-MM-dd")
    var isPaidUser by mutableStateOf(prefs.getBoolean("isPaidUser", false))
    var isSubscriber by mutableStateOf(prefs.getBoolean("isSubscriber", false))
    var quickStartStep by mutableStateOf<QuickStartStep?>(null)
    var subscriptionExpiry by mutableStateOf(
        prefs.getLong("subscriptionExpiry", System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
    )
    var showWidgetLogo by mutableStateOf(prefs.getBoolean("showWidgetLogo", true))
    var autoCapitalize by mutableStateOf(prefs.getBoolean("autoCapitalize", true))
    var aiCsvCategorizeEnabled by mutableStateOf(prefs.getBoolean("aiCsvCategorizeEnabled", false))
    var crashlyticsEnabled by mutableStateOf(prefs.getBoolean("crashlyticsEnabled", true))

    // ── Backup State ──
    var backupsEnabled by mutableStateOf(backupPrefs.getBoolean("backups_enabled", false))
    var backupFrequencyWeeks by mutableIntStateOf(backupPrefs.getInt("backup_frequency_weeks", 1))
    var backupRetention by mutableIntStateOf(backupPrefs.getInt("backup_retention", 10))
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

    // Bumped whenever receipt storage changes in a way the UI should re-measure
    // (Save Photos sweep, explicit cleanup, etc). Used as a remember() key.
    var receiptStorageRevision by mutableIntStateOf(0)

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
    var archiveThreshold by mutableIntStateOf(
        SharedSettingsRepository.load(context).archiveThreshold
    )
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
        if (com.techadvantage.budgetrak.data.sync.SyncWriteHelper.isInitialized()) {
            com.techadvantage.budgetrak.data.sync.SyncWriteHelper.pushTransaction(updated)
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
                    // Restore last-known lastSeen + online so the roster doesn't
                    // show stale "13 days ago" (Firestore registerDevice value)
                    // during the seconds–minutes window between process start
                    // and the RTDB presence listener delivering its initial
                    // snapshot. Default 0L/false for backward-compat with
                    // pre-2026-04-25 cache JSON.
                    lastSeen = obj.optLong("lastSeen", 0L),
                    online = obj.optBoolean("online", false)
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
                put("lastSeen", d.lastSeen)
                put("online", d.online)
            })
        }
        syncPrefs.edit().putString("cachedDeviceRoster", arr.toString()).apply()
    }

    var syncRepairAlert by mutableStateOf(prefs.getBoolean("syncRepairAlert", false))
    var lastSyncTimeDisplay by mutableStateOf<String?>(null)
    private var imageLedgerListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var deviceDocListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var adminClaimListener: com.google.firebase.firestore.ListenerRegistration? = null
    var syncEvictionMessage by mutableStateOf<String?>(null)
    var adminClaimMessage by mutableStateOf<String?>(null)  // dashboard popup for vote or result
    var adminClaimVoteNeeded by mutableStateOf<AdminClaim?>(null)  // non-null = show vote dialog

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
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var lastPresenceRecords: List<com.techadvantage.budgetrak.data.sync.PresenceRecord>? = null
    private var isRefreshingDeviceList = false
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
        val tz = if (isSyncConfigured && sharedSettings.familyTimezone.isNotEmpty())
            java.time.ZoneId.of(sharedSettings.familyTimezone) else java.time.ZoneId.systemDefault()
        val now = java.time.Instant.now().atZone(tz)
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
            activeAmortizationEntries, budgetPeriod, budgetToday, resetDayOfWeek
        )
        val savingsDeductions = BudgetCalculator.activeSavingsGoalDeductions(
            activeSavingsGoals, budgetPeriod, budgetToday, resetDayOfWeek
        )
        val acceleratedDeductions = BudgetCalculator.acceleratedREExtraDeductions(
            activeRecurringExpenses, budgetPeriod, budgetToday, resetDayOfWeek
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

    /** Generic save: persist to disk, diff against cache, batch push to Firestore. */
    private fun <T : Any> saveCollection(
        current: List<T>,
        saveToDisk: (Context, List<T>) -> Unit,
        cache: MutableMap<Int, T>,
        getId: (T) -> Int,
        hint: List<T>?
    ) {
        saveToDisk(context, current)
        if (SyncWriteHelper.isInitialized()) {
            val toPush = hint ?: current.filter { cache[getId(it)] != it }
            if (toPush.isNotEmpty()) SyncWriteHelper.pushBatch(toPush)
            current.associateByTo(cache) { getId(it) }
            if (hint == null || hint.isNotEmpty()) lastSyncActivity = System.currentTimeMillis()
        }
    }

    fun saveIncomeSources(hint: List<IncomeSource>? = null) =
        saveCollection(incomeSources.toList(), IncomeSourceRepository::save, lastSavedIs, { it.id }, hint)

    fun saveRecurringExpenses(hint: List<RecurringExpense>? = null) =
        saveCollection(recurringExpenses.toList(), RecurringExpenseRepository::save, lastSavedRe, { it.id }, hint)

    fun saveAmortizationEntries(hint: List<AmortizationEntry>? = null) =
        saveCollection(amortizationEntries.toList(), AmortizationRepository::save, lastSavedAe, { it.id }, hint)

    fun saveSavingsGoals(hint: List<SavingsGoal>? = null) =
        saveCollection(savingsGoals.toList(), SavingsGoalRepository::save, lastSavedSg, { it.id }, hint)

    fun saveTransactions(hint: List<Transaction>? = null) {
        // Dedup by ID before saving
        val deduped = transactions.groupBy { it.id }.values.map { it.first() }
        if (deduped.size < transactions.size) {
            android.util.Log.w("MainViewModel", "Deduped ${transactions.size - deduped.size} duplicate transactions")
            transactions.clear()
            transactions.addAll(deduped)
        }
        // Queue newly-attached receipts for cloud upload. Doing this here (rather
        // than at photo-capture) guarantees the queue only ever contains IDs
        // claimed by a persisted transaction — dialog-cancels, failed slot
        // assignments, and any future ordering bugs can't leak orphans into the
        // queue. Filter to files still on disk so re-saves of already-uploaded
        // (and pruned) receipts don't re-enqueue.
        val previousIds = com.techadvantage.budgetrak.data.sync.ReceiptManager
            .collectAllReceiptIds(lastSavedTxns.values.toList())
        val currentIds = com.techadvantage.budgetrak.data.sync.ReceiptManager
            .collectAllReceiptIds(transactions)
        val newlyAttached = currentIds - previousIds
        if (newlyAttached.isNotEmpty()) {
            var anyQueued = false
            for (rid in newlyAttached) {
                if (com.techadvantage.budgetrak.data.sync.ReceiptManager.hasLocalFile(context, rid)) {
                    com.techadvantage.budgetrak.data.sync.ReceiptManager.addToPendingQueue(context, rid)
                    anyQueued = true
                }
            }
            // Kick the upload drainer so the photo reaches Cloud Storage without
            // waiting for the next manual Sync-Now or BG Tier 3.
            if (anyQueued) kickUploadDrainer()
        }
        saveCollection(transactions.toList(), TransactionRepository::save, lastSavedTxns, { it.id }, hint)
    }

    fun saveCategories(hint: List<Category>? = null) =
        saveCollection(categories.toList(), CategoryRepository::save, lastSavedCat, { it.id }, hint)

    fun savePeriodLedger(hint: List<PeriodLedgerEntry>? = null) =
        saveCollection(periodLedger.toList(), PeriodLedgerRepository::save, lastSavedPle, { it.id }, hint)

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
        com.techadvantage.budgetrak.widget.BudgetWidgetProvider.updateAllWidgets(context)
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
    // ═══════════════════════════════════════════════════════════════════
    // PERIODIC MAINTENANCE (called from onResume, 24h gate)
    // ═══════════════════════════════════════════════════════════════════

    private suspend fun runPeriodicMaintenance() {
        android.util.Log.i("Maintenance", "Running periodic maintenance")

        // ── Daily health beacon ──
        // Migrated 2026-04-22 from a Crashlytics non-fatal to a Firebase
        // Analytics event (health_beacon). Analytics is the right channel for
        // per-period heartbeat telemetry — it doesn't pollute the crash
        // dashboard and has no per-session rate limit. Crashlytics diag keys
        // are still updated below so they attach to any real crash that does
        // happen. Skipped entirely when user has opted out.
        if (isSyncConfigured && crashlyticsEnabled) {
            val maintenanceCashDigest = availableCash.toString().hashCode().toString(16)
            BudgeTrakApplication.updateDiagKeys(mapOf(
                "cashDigest" to maintenanceCashDigest,
                "listenerStatus" to if (docSync?.isListening == true) "healthy" else "dead",
                "lastRefreshDate" to (lastRefreshDate?.toString() ?: "null"),
                "activeDevices" to syncDevices.size.toString(),
                "txnCount" to transactions.count { !it.deleted }.toString(),
                "reCount" to recurringExpenses.count { !it.deleted }.toString(),
                "plCount" to periodLedger.size.toString()
            ))
            BudgeTrakApplication.syncEvent("Daily health: listeners=${if (docSync?.isListening == true) "up" else "down"} txn=${transactions.count { !it.deleted }} devices=${syncDevices.size}")
            com.techadvantage.budgetrak.data.telemetry.AnalyticsEvents.logHealthBeacon(
                context = context,
                listenerUp = docSync?.isListening == true,
                activeDevices = syncDevices.size,
                txnCount = transactions.count { !it.deleted },
                reCount = recurringExpenses.count { !it.deleted },
                plCount = periodLedger.size,
            )
        }

        // ── Backup check ──
        try {
            if (BackupManager.isBackupDue(context)) {
                val pwd = BackupManager.getPassword(context)
                if (pwd != null) {
                    withContext(Dispatchers.IO) { BackupManager.performBackup(context, pwd) }
                    lastBackupDate = backupPrefs.getString("last_backup_date", null)
                    android.util.Log.i("Maintenance", "Auto-backup completed")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("Maintenance", "Backup failed: ${e.message}")
        }

        // ── Integrity check + consistency check (sync users only) ──
        if (isSyncConfigured && syncGroupId != null) {
            runIntegrityCheck()
            recomputeCash()
            runConsistencyCheck()
        }

        // ── Receipt orphan cleanup + pruning (snapshot to avoid index corruption) ──
        try {
            val txnSnapshot = transactions.toList()
            val mutations = mutableMapOf<Int, Transaction>() // id -> updated txn
            // Clean orphaned receiptIds (transaction references a file that doesn't exist)
            if (!isSyncConfigured) {
                val receiptDir = java.io.File(context.filesDir, "receipts")
                for (txn in txnSnapshot) {
                    var t = txn
                    var changed = false
                    fun fileExists(rid: String?) = rid != null && java.io.File(receiptDir, "$rid.jpg").exists()
                    if (t.receiptId1 != null && !fileExists(t.receiptId1)) { t = t.copy(receiptId1 = null); changed = true }
                    if (t.receiptId2 != null && !fileExists(t.receiptId2)) { t = t.copy(receiptId2 = null); changed = true }
                    if (t.receiptId3 != null && !fileExists(t.receiptId3)) { t = t.copy(receiptId3 = null); changed = true }
                    if (t.receiptId4 != null && !fileExists(t.receiptId4)) { t = t.copy(receiptId4 = null); changed = true }
                    if (t.receiptId5 != null && !fileExists(t.receiptId5)) { t = t.copy(receiptId5 = null); changed = true }
                    if (changed) mutations[txn.id] = t
                }
            }
            // Receipt local storage pruning
            val pruneAge = sharedSettings.receiptPruneAgeDays
            if (pruneAge != null) {
                val pruneDate = java.time.LocalDate.now().minusDays(pruneAge.toLong())
                for (txn in txnSnapshot) {
                    if (txn.date.isBefore(pruneDate)) {
                        val ids = com.techadvantage.budgetrak.data.sync.ReceiptManager.getReceiptIds(txn)
                        if (ids.isNotEmpty()) {
                            for (rid in ids) com.techadvantage.budgetrak.data.sync.ReceiptManager.deleteLocalReceipt(context, rid)
                            mutations[txn.id] = txn.copy(
                                receiptId1 = null, receiptId2 = null, receiptId3 = null,
                                receiptId4 = null, receiptId5 = null
                            )
                        }
                    }
                }
            }
            // Apply mutations atomically
            if (mutations.isNotEmpty()) {
                for ((id, updated) in mutations) {
                    val idx = transactions.indexOfFirst { it.id == id }
                    if (idx >= 0) transactions[idx] = updated
                }
                saveTransactions()
                android.util.Log.i("Maintenance", "Receipt cleanup: ${mutations.size} transactions updated")
            }
            // Clean orphaned files (on disk but not referenced by any transaction)
            val allReceiptIds = com.techadvantage.budgetrak.data.sync.ReceiptManager.collectAllReceiptIds(transactions)
            com.techadvantage.budgetrak.data.sync.ReceiptManager.cleanOrphans(context, allReceiptIds)
        } catch (e: Exception) {
            android.util.Log.w("Maintenance", "Receipt cleanup failed: ${e.message}")
        }

        // ── Solo user: purge tombstoned records (synced users need them for propagation) ──
        if (!isSyncConfigured) {
            var purged = 0
            purged += transactions.count { it.deleted }.also { if (it > 0) transactions.removeAll { t -> t.deleted } }
            purged += recurringExpenses.count { it.deleted }.also { if (it > 0) recurringExpenses.removeAll { r -> r.deleted } }
            purged += incomeSources.count { it.deleted }.also { if (it > 0) incomeSources.removeAll { s -> s.deleted } }
            purged += savingsGoals.count { it.deleted }.also { if (it > 0) savingsGoals.removeAll { s -> s.deleted } }
            purged += amortizationEntries.count { it.deleted }.also { if (it > 0) amortizationEntries.removeAll { a -> a.deleted } }
            purged += categories.count { it.deleted }.also { if (it > 0) categories.removeAll { c -> c.deleted } }
            if (purged > 0) {
                saveTransactions(); saveRecurringExpenses(); saveIncomeSources()
                saveSavingsGoals(); saveAmortizationEntries(); saveCategories()
                android.util.Log.i("Maintenance", "Purged $purged tombstoned records (solo user)")
            }
        }

        // ── Admin: tombstone + cloud orphan cleanup (30-day sub-gate) ──
        if (isSyncAdmin && isSyncConfigured) {
            val groupId = syncGroupId ?: return
            val lastCleanup = syncPrefs.getLong("lastAdminCleanup", 0L)
            val cleanupInterval = 30L * 24 * 60 * 60 * 1000
            if (System.currentTimeMillis() - lastCleanup > cleanupInterval) {
                try {
                    val devices = syncDevices
                    if (devices.size >= 2) {
                        val oldestLastSeen = devices.minOf { it.lastSeen }
                        val cutoff = oldestLastSeen - 24 * 60 * 60 * 1000L
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
                            if (totalPurged > 0) android.util.Log.i("Maintenance", "Purged $totalPurged old tombstones")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("Maintenance", "Tombstone cleanup failed: ${e.message}")
                }
                try {
                    com.techadvantage.budgetrak.data.sync.ImageLedgerService.purgeOrphanedCloudFiles(groupId)
                } catch (e: Exception) {
                    android.util.Log.w("Maintenance", "Orphan cloud cleanup failed: ${e.message}")
                }
                syncPrefs.edit().putLong("lastAdminCleanup", System.currentTimeMillis()).apply()
            }
        }

        android.util.Log.i("Maintenance", "Periodic maintenance complete")
    }

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

    // ── CONSISTENCY CHECK (Layer 1: counts vs Firestore, Layer 2: cashHash majority vote) ──

    private suspend fun runConsistencyCheck() {
        val groupId = syncGroupId ?: return
        if (!isSyncConfigured) return

        try {
            val cursorPrefs = context.getSharedPreferences("sync_cursor", android.content.Context.MODE_PRIVATE)

            // ── Layer 1: Count-based check against Firestore server ──
            val localCounts = mapOf(
                EncryptedDocSerializer.COLLECTION_TRANSACTIONS to transactions.count { !it.deleted },
                EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES to recurringExpenses.count { !it.deleted },
                EncryptedDocSerializer.COLLECTION_INCOME_SOURCES to incomeSources.count { !it.deleted },
                EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS to savingsGoals.count { !it.deleted },
                EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES to amortizationEntries.count { !it.deleted },
                EncryptedDocSerializer.COLLECTION_CATEGORIES to categories.count { !it.deleted },
                EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER to periodLedger.size
            )

            var countMismatches = 0
            for ((collection, localCount) in localCounts) {
                val serverCount = FirestoreDocService.countActiveDocs(groupId, collection)
                if (serverCount < 0) continue // query failed, skip
                if (serverCount.toInt() != localCount) {
                    countMismatches++
                    android.util.Log.w("Consistency",
                        "Count mismatch: $collection local=$localCount server=$serverCount — clearing cursor")
                    BudgeTrakApplication.recordNonFatal(
                        "CONSISTENCY_COUNT_MISMATCH",
                        "$collection local=$localCount server=$serverCount group=$groupId",
                        Exception("Count mismatch")
                    )
                    // Clear cursor so next sync does full re-read for this collection
                    cursorPrefs.edit()
                        .remove("cursor_${collection}_seconds")
                        .remove("cursor_${collection}_nanos")
                        .apply()
                }
            }
            if (countMismatches > 0) {
                android.util.Log.w("Consistency", "$countMismatches collections have count mismatches — cursors cleared")
            } else {
                android.util.Log.i("Consistency", "Layer 1 pass: all counts match Firestore")
            }

            // ── Layer 2: cashHash checkpoint + majority vote ──
            // Use hash digest, not raw cash value, to protect user financial privacy
            val cashHash = availableCash.toString().hashCode().toString(16)
            val now = System.currentTimeMillis()

            // Write our checkpoint to group doc
            val checkpointData = mapOf(
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "counts" to localCounts.mapValues { it.value },
                "cashHash" to cashHash
            )
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("groups").document(groupId)
                    .update("deviceChecksums.$localDeviceId", checkpointData)
            } catch (_: Exception) {
                // Field might not exist yet — use set with merge
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("groups").document(groupId)
                        .set(mapOf("deviceChecksums" to mapOf(localDeviceId to checkpointData)),
                            com.google.firebase.firestore.SetOptions.merge())
                } catch (_: Exception) {}
            }

            // Read all device checkpoints
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val groupDoc = db.collection("groups").document(groupId).get().await()
                @Suppress("UNCHECKED_CAST")
                val checksums = groupDoc.get("deviceChecksums") as? Map<String, Map<String, Any>> ?: return

                // Filter to devices with recent RTDB lastSeen AND recent checkpoint
                val convergenceWindowMs = 30 * 60 * 1000L
                val recentDevices = mutableMapOf<String, String>() // deviceId -> cashHash
                for ((devId, data) in checksums) {
                    if (devId == localDeviceId) {
                        recentDevices[devId] = cashHash
                        continue
                    }
                    val ts = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: continue
                    if (now - ts > convergenceWindowMs) continue
                    // Check RTDB lastSeen
                    val rtdbDevice = syncDevices.find { it.deviceId == devId }
                    if (rtdbDevice == null || now - rtdbDevice.lastSeen > convergenceWindowMs) continue
                    val otherHash = data["cashHash"] as? String ?: continue
                    recentDevices[devId] = otherHash
                }

                if (recentDevices.size < 2) {
                    android.util.Log.i("Consistency", "Layer 2: <2 comparable devices, skipping cashHash check")
                    return
                }

                // Majority vote
                val hashGroups = recentDevices.entries.groupBy { it.value }
                val majorityHash = hashGroups.maxByOrNull { it.value.size }?.key
                val myHash = cashHash

                if (myHash == majorityHash) {
                    android.util.Log.i("Consistency",
                        "Layer 2 pass: cashHash=$myHash matches majority (${recentDevices.size} devices)")
                    // Clear any pending mismatch flag
                    prefs.edit().remove("checksumMismatchAt").apply()
                } else {
                    val majorityCount = hashGroups[majorityHash]?.size ?: 0
                    android.util.Log.w("Consistency",
                        "Layer 2 mismatch: my=$myHash majority=$majorityHash ($majorityCount/${recentDevices.size} devices)")

                    val previousMismatch = prefs.getLong("checksumMismatchAt", 0L)
                    if (previousMismatch == 0L) {
                        // First detection — set flag for recheck in 1 hour
                        prefs.edit().putLong("checksumMismatchAt", now).apply()
                        android.util.Log.w("Consistency", "Mismatch flagged for 1-hour recheck")
                    } else if (now - previousMismatch > 60 * 60 * 1000L) {
                        // Confirmed after 1+ hour — recovery
                        android.util.Log.w("Consistency", "Confirmed mismatch — triggering full re-read recovery")
                        BudgeTrakApplication.recordNonFatal(
                            "CONSISTENCY_CASH_MISMATCH",
                            "devices=${recentDevices.size} majorityAgree=$majorityCount",
                            Exception("Cash mismatch confirmed — triggering full re-read")
                        )
                        // Clear all cursors — next sync does full re-read
                        cursorPrefs.edit().clear().apply()
                        prefs.edit().remove("checksumMismatchAt").apply()
                    }
                    // else: within 1-hour confirmation window, wait
                }
            } catch (e: Exception) {
                android.util.Log.w("Consistency", "Layer 2 check failed: ${e.message}")
            }
        } catch (e: Exception) {
            android.util.Log.w("Consistency", "Consistency check failed: ${e.message}")
        }
    }

    /**
     * Lightweight recheck for confirmed cashHash mismatches.
     * Called from onResume and BackgroundSyncWorker when checksumMismatchAt is set.
     */
    suspend fun recheckConsistency() {
        val mismatchAt = prefs.getLong("checksumMismatchAt", 0L)
        if (mismatchAt == 0L) return
        if (System.currentTimeMillis() - mismatchAt < 60 * 60 * 1000L) return // wait 1 hour
        if (!isSyncConfigured || syncGroupId == null) {
            prefs.edit().remove("checksumMismatchAt").apply()
            return
        }
        android.util.Log.i("Consistency", "Running mismatch recheck")
        recomputeCash()
        runConsistencyCheck()
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
        // Guard against duplicate IDs (e.g., double-tap or recomposition replay).
        // All side effects of "adding a transaction" — savings-goal deduction,
        // local list mutation, disk write, Firestore push — are gated together
        // so a re-entry is a complete no-op. Pre-2026-04-27 the SG deduction and
        // the Firestore push happened outside the guard, which could
        // double-deduct on double-tap and leak "saved on Firestore but missing
        // locally" state when stamped collided with an existing local row.
        if (transactions.none { it.id == stamped.id }) {
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
            transactions.add(stamped)
            saveTransactions(listOf(stamped))
        }
        recomputeCash()
        if (archiveThreshold > 0 && activeTransactions.size > archiveThreshold) {
            checkAndTriggerArchive()
        }
    }

    // ── Matching + linking chain design note ──────────────────────────────
    //
    // The deterministic match-FINDERS (findDuplicates, findRecurringExpenseMatches,
    // findAmortizationMatches, findBudgetIncomeMatches) live in DuplicateDetector.kt
    // and are shared across all entry points — that's the layer of consolidation that
    // makes sense.
    //
    // What is intentionally NOT consolidated: the orchestration (this function +
    // runMatchingChain) is the dashboard's wiring. TransactionsScreen, CSV-import,
    // edit dialogs, and WidgetTransactionActivity each have their own parallel
    // orchestration because each has different post-match side effects:
    //   - dashboard  → uses VM's dashPending* state + addTransactionWithBudgetEffect
    //   - screen add/edit → uses local pendingManual* + addAndScroll + isEdit flag
    //   - screen CSV import → sequential flow, importIndex / importApproved
    //   - widget → runs in a separate Activity, no VM, reads from *Repository.active
    //
    // A 2026-04-03 cleanup (commit a394a3a) made all 5 entry points agree on the
    // same TYPE-based order (income → budget income; expense → RE → amortization).
    // A 2026-04-13 analysis considered extracting a single shared chain function
    // with callbacks and concluded it would require VM to know about screen-local
    // state, which is architecturally worse. See
    // memory/project_prelaunch_todo.md "Completed + documented elsewhere".
    //
    // If you change the type-based order or add a new match type, update all 5
    // entry points to match. Grep for findDuplicates / findRecurringExpenseMatches
    // to find them.

    // Linking chain: recurring/amortization/income match (no duplicate check).
    // Runs search on background thread to avoid blocking UI.
    fun runLinkingChain(txn: Transaction) {
        val alreadyLinked = txn.linkedRecurringExpenseId != null || txn.linkedAmortizationEntryId != null || txn.linkedIncomeSourceId != null || txn.linkedSavingsGoalId != null
        if (alreadyLinked) {
            addTransactionWithBudgetEffect(txn)
            return
        }
        val pTol = percentTolerance; val dTol = matchDollar; val mChars = matchChars; val dWin = matchDays
        if (txn.type == TransactionType.INCOME) {
            // Income: check budget income match only
            val is_ = activeIncomeSources.toList()
            viewModelScope.launch(Dispatchers.Default) {
                val budgetMatches = findBudgetIncomeMatches(txn, is_, mChars, dWin)
                withContext(Dispatchers.Main) {
                    if (budgetMatches.isNotEmpty()) {
                        dashPendingBudgetIncomeTxn = txn
                        dashPendingBudgetIncomeMatches = budgetMatches
                        dashShowBudgetIncomeDialog = true
                    } else {
                        addTransactionWithBudgetEffect(txn)
                    }
                }
            }
        } else {
            // Expense: check RE match → amortization match
            val re = activeRecurringExpenses.toList()
            val ae = activeAmortizationEntries.toList()
            viewModelScope.launch(Dispatchers.Default) {
                val recurringMatches = findRecurringExpenseMatches(txn, re, pTol, dTol, mChars, dWin)
                if (recurringMatches.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        dashPendingRecurringTxn = txn
                        dashPendingRecurringMatches = recurringMatches
                        dashShowRecurringDialog = true
                    }
                    return@launch
                }
                val amortizationMatches = findAmortizationMatches(txn, ae, pTol, dTol, mChars)
                withContext(Dispatchers.Main) {
                    if (amortizationMatches.isNotEmpty()) {
                        dashPendingAmortizationTxn = txn
                        dashPendingAmortizationMatches = amortizationMatches
                        dashShowAmortizationDialog = true
                    } else {
                        addTransactionWithBudgetEffect(txn)
                    }
                }
            }
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
            val dups = findDuplicates(txn, txns, pTol, dTol, dWin, mChars)
            withContext(Dispatchers.Main) {
                if (dups.isNotEmpty()) {
                    dashPendingManualSave = txn
                    dashManualDuplicateMatches = dups
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
        // Set Crashlytics context for sync diagnostics
        try {
            val c = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            c.setCustomKey("isSyncConfigured", isSyncConfigured)
            c.setCustomKey("isSyncAdmin", isSyncAdmin)
            c.setCustomKey("syncGroupId", syncGroupId ?: "none")
        } catch (_: Exception) {}

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
                    lastPresenceRecords?.let { mergePresenceIntoRoster(it) }
                    cacheDeviceRoster(syncDevices)
                    android.util.Log.i("SyncLoop", "Device list refreshed after listener recovery")
                } catch (e: Exception) {
                    android.util.Log.w("SyncLoop", "Device list refresh after recovery failed: ${e.message}")
                }
                try {
                    val deviceName = GroupManager.getDeviceName(context)
                    com.techadvantage.budgetrak.data.sync.RealtimePresenceService.setupPresence(
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

        // Launch sync setup coroutine (attaches device doc listener after registration)
        startSyncSetup()

        // Set up RTDB presence (foreground only)
        try {
            val deviceName = GroupManager.getDeviceName(context)
            val receiptPrefs = context.getSharedPreferences("receipt_sync_prefs", Context.MODE_PRIVATE)
            val uploadSpeed = receiptPrefs.getLong("lastUploadSpeedBps", 0L)
            val speedMeasuredAt = receiptPrefs.getLong("lastSpeedMeasuredAt", 0L)
            com.techadvantage.budgetrak.data.sync.RealtimePresenceService.setupPresence(
                gid, localDeviceId, deviceName,
                photoCapable = isPaidUser || isSubscriber,
                uploadSpeedBps = uploadSpeed,
                uploadSpeedMeasuredAt = speedMeasuredAt
            )
            com.techadvantage.budgetrak.data.sync.RealtimePresenceService.listenToGroupPresence(gid) { presenceRecords ->
                lastPresenceRecords = presenceRecords
                mergePresenceIntoRoster(presenceRecords)
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
        com.techadvantage.budgetrak.data.sync.RealtimePresenceService.cleanup()
        imageLedgerListener?.remove(); imageLedgerListener = null
        deviceDocListener?.remove(); deviceDocListener = null
        adminClaimListener?.remove(); adminClaimListener = null
    }

    /**
     * Evict this device from sync — called when the device doc is removed or
     * marked removed=true. Disables all sync immediately; app continues as solo.
     * No local data is deleted.
     */
    private fun evictFromSync(reason: String) {
        if (!isSyncConfigured) return // already evicted or never synced
        val gid = syncGroupId
        android.util.Log.w("SyncEviction", "Evicted: $reason (group=$gid)")
        BudgeTrakApplication.tokenLog("EVICTED: $reason (group=$gid) caller=${Thread.currentThread().stackTrace.getOrNull(3)?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" } ?: "unknown"}")

        // Clean up RTDB presence + membership
        if (gid != null) {
            try { com.techadvantage.budgetrak.data.sync.RealtimePresenceService.deletePresenceNode(gid, localDeviceId) } catch (_: Exception) {}
            try {
                val authUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                if (authUid != null) viewModelScope.launch {
                    try { FirestoreService.removeMembership(gid, authUid) } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        // Dispose all sync resources
        deviceDocListener?.remove(); deviceDocListener = null
        adminClaimListener?.remove(); adminClaimListener = null
        docSync?.dispose(); docSync = null
        SyncWriteHelper.dispose()
        com.techadvantage.budgetrak.data.sync.RealtimePresenceService.cleanup()
        imageLedgerListener?.remove(); imageLedgerListener = null

        // Stop any in-flight receipt sync coroutines — their captured groupId
        // and encryption key are about to become invalid.
        cancelReceiptSyncJobs()

        // Clear sync state — app now functions as solo device
        viewModelScope.launch { GroupManager.leaveGroup(context, localOnly = true) }
        isSyncConfigured = false
        syncGroupId = null
        isSyncAdmin = false
        syncStatus = "off"
        syncDevices = emptyList()
        syncPrefs.edit().remove("cachedDeviceRoster").apply()
        initialSyncReceived = true // unblock any waiting coroutines
        showAttribution = false
        prefs.edit().putBoolean("showAttribution", false).apply()

        // Set eviction message for dashboard popup
        syncEvictionMessage = reason

        // If currently on sync screen, stay — it shows join/create when not configured
        // (no navigation needed)
    }

    /** Handle admin claim document changes. */
    private fun onAdminClaimChanged(claim: AdminClaim?) {
        if (claim == null || claim.status != "pending") {
            // Claim resolved or deleted — check if we're the claimant and show result
            if (claim != null && claim.claimantDeviceId == localDeviceId) {
                adminClaimMessage = when (claim.status) {
                    "approved" -> strings.sync.claimApproved
                    "rejected" -> strings.sync.claimRejected
                    else -> null
                }
            }
            pendingAdminClaim = if (claim?.status == "pending") claim else null
            adminClaimVoteNeeded = null
            return
        }
        pendingAdminClaim = claim
        // If we're NOT the claimant and haven't voted yet, prompt for vote
        if (claim.claimantDeviceId != localDeviceId && !claim.votes.containsKey(localDeviceId)) {
            adminClaimVoteNeeded = claim
        }
        // Only the claimant's device resolves — prevents race between multiple devices
        if (claim.claimantDeviceId == localDeviceId) {
            // Auto-resolve if expired
            if (System.currentTimeMillis() > claim.expiresAt) {
                resolveExpiredClaim(claim)
                return
            }
            // Check if all non-claimant devices in roster have voted → resolve immediately
            val otherDeviceIds = syncDevices
                .map { it.deviceId }
                .filter { it != claim.claimantDeviceId }
                .toSet()
            if (otherDeviceIds.isNotEmpty() && otherDeviceIds.all { claim.votes.containsKey(it) }) {
                resolveExpiredClaim(claim)
            }
        }
    }

    /** Resolve an expired admin claim. Any reject = rejected. All accepts (ignoring non-voters) = approved. */
    private fun resolveExpiredClaim(claim: AdminClaim) {
        val gid = syncGroupId ?: return
        viewModelScope.launch {
            try {
                val hasReject = claim.votes.values.any { it == "reject" }
                val newStatus = if (hasReject) "rejected" else "approved"
                FirestoreService.resolveAdminClaim(gid, newStatus)
                if (newStatus == "approved") {
                    val oldAdmin = syncDevices.find { it.isAdmin }?.deviceId
                    if (oldAdmin != null) {
                        // transferAdmin also deletes the claim doc
                        FirestoreService.transferAdmin(gid, oldAdmin, claim.claimantDeviceId)
                    } else {
                        FirestoreService.deleteAdminClaim(gid)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AdminClaim", "Resolve failed: ${e.message}")
            }
        }
    }

    /** Cast a vote on the pending admin claim. */
    fun voteOnAdminClaim(vote: String) {
        val gid = syncGroupId ?: return
        adminClaimVoteNeeded = null
        viewModelScope.launch {
            try {
                FirestoreService.castVote(gid, localDeviceId, vote)
                // Listener (adminClaimListener) will update pendingAdminClaim via onAdminClaimChanged
            } catch (e: Exception) {
                android.util.Log.w("AdminClaim", "Vote failed: ${e.message}")
            }
        }
    }

    /** Handle isAdmin change from device doc listener. */
    private fun onAdminStatusChanged(newIsAdmin: Boolean) {
        if (newIsAdmin != isSyncAdmin) {
            android.util.Log.i("SyncAdmin", "Admin status changed: $isSyncAdmin → $newIsAdmin")
            isSyncAdmin = newIsAdmin
            GroupManager.setAdmin(context, newIsAdmin)
            // Refresh device list to update roster
            val gid = syncGroupId ?: return
            viewModelScope.launch {
                try {
                    syncDevices = GroupManager.getDevices(gid)
                    lastPresenceRecords?.let { mergePresenceIntoRoster(it) }
                } catch (_: Exception) {}
            }
        }
    }

    /** Launch a coroutine on IO, scoped to ViewModel lifecycle. */
    fun launchIO(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        viewModelScope.launch(Dispatchers.IO, block = block)
    }

    /** Reset all sync-related state to "off" (used by leave, dissolve, evict). */
    fun resetSyncState() {
        // Stop any in-flight receipt sync coroutines — their captured groupId
        // and encryption key are about to become invalid.
        cancelReceiptSyncJobs()
        syncPrefs.edit().remove("catIdRemap").remove("lastSuccessfulSync").apply()
        isSyncConfigured = false
        syncGroupId = null
        isSyncAdmin = false
        syncStatus = "off"
        lastSyncActivity = 0L
        syncDevices = emptyList()
        pendingAdminClaim = null
        syncErrorMessage = null
        syncProgressMessage = null
        showAttribution = false
        prefs.edit().putBoolean("showAttribution", false).apply()
    }

    /** Merge RTDB presence records into the current syncDevices roster. */
    private fun mergePresenceIntoRoster(presenceRecords: List<com.techadvantage.budgetrak.data.sync.PresenceRecord>) {
        val currentDevices = syncDevices
        val rtdbIds = presenceRecords.map { it.deviceId }.toSet()
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
            } else device.copy(online = false) // not in RTDB = offline
        }
        val existingIds = updatedDevices.map { it.deviceId }.toSet()
        val newDevices = presenceRecords
            .filter { it.deviceId !in existingIds }
            .map { DeviceInfo(it.deviceId, it.deviceName, isAdmin = false, lastSeen = it.lastSeen,
                online = it.online, photoCapable = it.photoCapable,
                uploadSpeedBps = it.uploadSpeedBps, uploadSpeedMeasuredAt = it.uploadSpeedMeasuredAt) }
        syncDevices = updatedDevices + newDevices
        cacheDeviceRoster(syncDevices)

        // If a device disappeared from RTDB (left/removed), refresh Firestore device list
        val missingFromRtdb = currentDevices.filter { it.deviceId !in rtdbIds && it.deviceId != localDeviceId }
        if (missingFromRtdb.isNotEmpty() && !isRefreshingDeviceList) {
            val gid = syncGroupId ?: return
            isRefreshingDeviceList = true
            viewModelScope.launch {
                try {
                    syncDevices = GroupManager.getDevices(gid)
                    lastPresenceRecords?.let { mergePresenceIntoRoster(it) }
                    cacheDeviceRoster(syncDevices)
                } catch (_: Exception) {}
                isRefreshingDeviceList = false
            }
        }
    }

    /**
     * The onBatchChanged callback: update in-memory state when remote changes arrive.
     */
    private suspend fun onBatchChanged(events: List<com.techadvantage.budgetrak.data.sync.DataChangeEvent>) {
        if (!isSyncConfigured) return // evicted — skip merge
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
                archiveThreshold = merged.archiveThreshold
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

            // Save changed collections to JSON — awaited so cursor advances only after persist
            val txnSnap = result.transactions
            val reSnap = result.recurringExpenses
            val isSnap = result.incomeSources
            val sgSnap = result.savingsGoals
            val aeSnap = result.amortizationEntries
            val catSnap = result.categories
            val pleSnap = result.periodLedger
            val ssSnap = result.sharedSettings
            withContext(Dispatchers.IO) {
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

            // Check for missing receipt photos from newly-arrived transactions.
            // Fast path: download in parallel. On any miss, hand off to the
            // foreground retry coroutine (Layer 2) which applies backoff +
            // the 3-retry-then-recovery-request escalation in ReceiptSyncManager.
            if ((isPaidUser || isSubscriber) && isSyncConfigured) {
                val updatedTxns = result.transactions
                if (updatedTxns != null) {
                    val missingReceiptIds = com.techadvantage.budgetrak.data.sync.ReceiptManager.collectAllReceiptIds(updatedTxns)
                        .filter { !com.techadvantage.budgetrak.data.sync.ReceiptManager.hasLocalFile(context, it) }
                    if (missingReceiptIds.isNotEmpty()) {
                        val currentGroupId = syncGroupId ?: return
                        val currentKey = GroupManager.getEncryptionKey(context) ?: return
                        val photoCapableDeviceIds = syncDevices
                            .filter { it.photoCapable }
                            .map { it.deviceId }
                            .toSet()
                        val receiptSync = com.techadvantage.budgetrak.data.sync.ReceiptSyncManager(
                            context, currentGroupId, localDeviceId, currentKey
                        ) { msg -> BudgeTrakApplication.syncEvent("ReceiptSync(onBatch): $msg") }
                        viewModelScope.launch {
                            missingReceiptIds.chunked(5).forEach { chunk ->
                                kotlinx.coroutines.coroutineScope {
                                    for (receiptId in chunk) {
                                        launch {
                                            try {
                                                val ok = receiptSync.downloadReceiptWithRetry(
                                                    receiptId, photoCapableDeviceIds
                                                )
                                                if (!ok) kickFgDownloadRetry(receiptId)
                                            } catch (e: Exception) {
                                                android.util.Log.w("ReceiptSync", "Download exception $receiptId: ${e.message}")
                                                kickFgDownloadRetry(receiptId)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            com.techadvantage.budgetrak.widget.BudgetWidgetProvider.updateAllWidgets(context)
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
                lastPresenceRecords?.let { mergePresenceIntoRoster(it) } // re-apply RTDB data
                cacheDeviceRoster(syncDevices)
            } catch (e: Exception) {
                android.util.Log.w("SyncLoop", "Failed to fetch initial device list, retrying in 10s", e)
                kotlinx.coroutines.delay(10_000)
                try {
                    syncDevices = GroupManager.getDevices(groupId)
                    lastPresenceRecords?.let { mergePresenceIntoRoster(it) }
                    cacheDeviceRoster(syncDevices)
                } catch (e2: Exception) {
                    android.util.Log.w("SyncLoop", "Device list retry also failed — will recover via listener callback", e2)
                }
            }

            // Early dissolution/removal check BEFORE writing to Firestore
            // (prevents recreating docs in a dead group)
            try {
                val groupHealth = FirestoreService.getGroupHealthStatus(groupId)
                if (groupHealth.isDissolved) {
                    BudgeTrakApplication.tokenLog("Evicting (path=earlyHealthCheck.dissolved)")
                    evictFromSync(strings.sync.evictionDissolved)
                    return@launch
                }
                if (FirestoreService.isDeviceRemoved(groupId, localDeviceId)) {
                    BudgeTrakApplication.tokenLog("Evicting (path=earlyHealthCheck.removed)")
                    evictFromSync(strings.sync.evictionRemoved)
                    return@launch
                }
            } catch (e: Exception) {
                android.util.Log.w("SyncLoop", "Early health check failed: ${e.message}")
                // Continue — will check again after initial sync
            }

            // Write device capabilities once on foreground launch
            try {
                FirestoreService.updateDeviceMetadata(
                    groupId, localDeviceId,
                    deviceName = GroupManager.getDeviceName(context),
                    syncVersion = 0L,
                    appSyncVersion = 2,
                    minSyncVersion = 2,
                    photoCapable = isPaidUser || isSubscriber
                )
                BudgeTrakApplication.tokenLog("Device metadata written to group=$groupId device=$localDeviceId")
            } catch (e: Exception) {
                BudgeTrakApplication.tokenLog("Device metadata write FAILED: ${e.message} group=$groupId")
                android.util.Log.w("SyncLoop", "Device metadata write failed: ${e.message}")
            }

            // Listen to own device doc for removal/dissolution + admin status changes
            deviceDocListener?.remove()
            deviceDocListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("groups").document(groupId)
                .collection("devices").document(localDeviceId)
                .addSnapshotListener { snapshot, err ->
                    if (err != null) {
                        val msg = err.message ?: ""
                        BudgeTrakApplication.tokenLog("Device doc listener error: $msg")
                        if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                            // Token may have expired — refresh and re-attach
                            viewModelScope.launch {
                                try {
                                    com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                                        .getAppCheckToken(true).await()
                                    BudgeTrakApplication.tokenLog("Device doc listener: token refreshed after PERMISSION_DENIED")
                                } catch (e: Exception) {
                                    BudgeTrakApplication.tokenLog("Device doc listener: token refresh failed: ${e.message}")
                                }
                            }
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot == null) {
                        return@addSnapshotListener
                    } else if (!snapshot.exists()) {
                        // Only trust "not exists" from server — cache miss is not dissolution
                        if (!snapshot.metadata.isFromCache) {
                            BudgeTrakApplication.tokenLog("Device doc deleted (server-confirmed) — evicting (path=deviceDocListener.notExists)")
                            evictFromSync(strings.sync.evictionDissolved)
                        } else {
                            BudgeTrakApplication.tokenLog("Device doc not in cache (fromCache=true) — ignoring, not evicting")
                        }
                    } else if (snapshot.getBoolean("removed") == true) {
                        BudgeTrakApplication.tokenLog("Device doc marked removed=true — evicting (path=deviceDocListener.removed)")
                        evictFromSync(strings.sync.evictionRemoved)
                    } else {
                        // Detect admin status changes (from admin claim transfer)
                        val newIsAdmin = snapshot.getBoolean("isAdmin") ?: false
                        onAdminStatusChanged(newIsAdmin)
                    }
                }

            // Listen to admin claim doc for vote requests and results
            adminClaimListener?.remove()
            adminClaimListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("groups").document(groupId)
                .collection("adminClaim").document("current")
                .addSnapshotListener { snapshot, err ->
                    if (err != null) return@addSnapshotListener
                    if (snapshot == null || !snapshot.exists()) {
                        onAdminClaimChanged(null)
                    } else {
                        val claim = AdminClaim(
                            claimantDeviceId = snapshot.getString("claimantDeviceId") ?: "",
                            claimantName = snapshot.getString("claimantName") ?: "",
                            claimedAt = snapshot.getLong("claimedAt") ?: 0L,
                            expiresAt = snapshot.getLong("expiresAt") ?: 0L,
                            votes = (snapshot.get("votes") as? Map<*, *>)?.mapNotNull { (k, v) ->
                                if (k is String && v is String) k to v else null
                            }?.toMap() ?: emptyMap(),
                            status = snapshot.getString("status") ?: "pending"
                        )
                        onAdminClaimChanged(claim)
                    }
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
                                    uploadedAt > 0L && !com.techadvantage.budgetrak.data.sync.ReceiptManager.hasLocalFile(context, receiptId) -> {
                                        val data = com.techadvantage.budgetrak.data.sync.ImageLedgerService.downloadFromCloud(currentGroupId, receiptId)
                                        if (data != null) {
                                            com.techadvantage.budgetrak.data.sync.ReceiptManager.decryptAndSave(context, receiptId, data, currentKey)
                                            com.techadvantage.budgetrak.data.sync.ImageLedgerService.markPossession(currentGroupId, receiptId, localDeviceId)
                                        }
                                    }
                                    // File uploaded and we have it — mark possession + prune check
                                    uploadedAt > 0L && com.techadvantage.budgetrak.data.sync.ReceiptManager.hasLocalFile(context, receiptId) -> {
                                        if (!possessions.containsKey(localDeviceId)) {
                                            com.techadvantage.budgetrak.data.sync.ImageLedgerService.markPossession(currentGroupId, receiptId, localDeviceId)
                                        }
                                        val photoCapableIds = syncDevices.filter { /* need device records */ true }.map { it.deviceId }.toSet()
                                        // Note: prune check needs photo-capable device IDs from Firestore device records
                                        // This is done when full receipt sync runs (background worker or manual sync)
                                    }
                                    // Recovery request — we have the file locally, re-upload it
                                    uploadedAt == 0L && com.techadvantage.budgetrak.data.sync.ReceiptManager.hasLocalFile(context, receiptId) -> {
                                        val encrypted = com.techadvantage.budgetrak.data.sync.ReceiptManager.encryptForUpload(context, receiptId, currentKey)
                                        if (encrypted != null) {
                                            val uploaded = com.techadvantage.budgetrak.data.sync.ImageLedgerService.uploadToCloud(currentGroupId, receiptId, encrypted)
                                            if (uploaded) {
                                                com.techadvantage.budgetrak.data.sync.ImageLedgerService.markReuploadComplete(currentGroupId, receiptId, localDeviceId)
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
                    BudgeTrakApplication.tokenLog("Evicting (path=postSyncHealthCheck.dissolved)")
                    evictFromSync(strings.sync.evictionDissolved)
                    return@launch
                }

                // Check device removal (separate doc)
                if (FirestoreService.isDeviceRemoved(groupId, localDeviceId)) {
                    BudgeTrakApplication.tokenLog("Evicting (path=postSyncHealthCheck.removed)")
                    evictFromSync(strings.sync.evictionRemoved)
                    return@launch
                }

                // Subscription expiry check (from same group doc read)
                val expiry = groupHealth.subscriptionExpiry
                if (expiry > 0L) {
                    val gracePeriodMs = 7L * 24 * 60 * 60 * 1000
                    val elapsed = System.currentTimeMillis() - expiry
                    if (elapsed > gracePeriodMs) {
                        // Grace period over — admin dissolves, non-admin evicts locally
                        BudgeTrakApplication.tokenLog("Evicting (path=postSyncHealthCheck.subscriptionExpiry, elapsed=${elapsed/1000}s)")
                        if (isSyncAdmin && !groupHealth.isDissolved) {
                            try { disposeSyncListeners() } catch (_: Exception) {}
                            GroupManager.dissolveGroup(context, groupId)
                        }
                        evictFromSync(strings.sync.evictionDissolved)
                        return@launch
                    } else if (elapsed > 0) {
                        // During grace period — dashboard popup once per day
                        val lastWarning = prefs.getLong("lastSubscriptionWarning", 0L)
                        if (System.currentTimeMillis() - lastWarning > 24 * 60 * 60 * 1000L) {
                            prefs.edit().putLong("lastSubscriptionWarning", System.currentTimeMillis()).apply()
                            val daysLeft = ((gracePeriodMs - elapsed) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(1)
                            syncEvictionMessage = strings.sync.subscriptionGraceWarning(daysLeft)
                        }
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
                        val receiptSync = com.techadvantage.budgetrak.data.sync.ReceiptSyncManager(
                            context, gId, localDeviceId, key
                        ) { msg -> BudgeTrakApplication.syncEvent("ReceiptSync(SyncNow): $msg") }
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
    // RECEIPT PHOTO PIPELINE — FOREGROUND DRAINERS
    // ═══════════════════════════════════════════════════════════════════

    // Layer 0 — Continuous upload drainer. Runs on viewModelScope whenever the
    // pending-upload queue is non-empty. Kicked by saveTransactions after it
    // adds newly-attached receipts to the queue, and on VM init if the queue
    // was persisted with items (crash recovery). Exits when the queue drains;
    // on upload failure, backs off (30s → 60s → 2m → 5m → 10m cap) before
    // retrying the same queue.
    private var uploadDrainerJob: kotlinx.coroutines.Job? = null

    fun kickUploadDrainer() {
        if (!(isPaidUser || isSubscriber)) return
        if (!isSyncConfigured) return
        if (uploadDrainerJob?.isActive == true) return
        val gId = syncGroupId ?: return
        val key = GroupManager.getEncryptionKey(context) ?: return

        uploadDrainerJob = viewModelScope.launch(Dispatchers.IO) {
            var backoffMs = 30_000L
            val receiptSync = com.techadvantage.budgetrak.data.sync.ReceiptSyncManager(
                context, gId, localDeviceId, key
            ) { msg -> BudgeTrakApplication.syncEvent("ReceiptSync(UploadDrainer): $msg") }
            try {
                while (isActive) {
                    val pendingBefore = com.techadvantage.budgetrak.data.sync.ReceiptManager
                        .loadPendingUploads(context).size
                    if (pendingBefore == 0) break

                    val uploaded = receiptSync.processPendingUploads()
                    val pendingAfter = com.techadvantage.budgetrak.data.sync.ReceiptManager
                        .loadPendingUploads(context).size

                    if (pendingAfter == 0) break
                    if (uploaded == 0) {
                        // Made no forward progress — back off before retrying
                        android.util.Log.i("UploadDrainer", "No progress, sleeping ${backoffMs / 1000}s")
                        kotlinx.coroutines.delay(backoffMs)
                        backoffMs = minOf(backoffMs * 2, 10 * 60_000L)
                    } else {
                        backoffMs = 30_000L
                    }
                }
                android.util.Log.i("UploadDrainer", "Queue drained, exiting")
            } catch (e: Exception) {
                android.util.Log.w("UploadDrainer", "Exception: ${e.message}")
            }
        }
    }

    // Layer 2 — Foreground download retry. onBatchChanged's fast-path tries
    // once; anything that misses gets handed here. The coroutine drains the
    // in-memory set with exponential backoff, using ReceiptSyncManager's
    // downloadReceiptWithRetry (which applies the 3-retry-then-recovery-request
    // escalation internally). Exits when the set is empty.
    private val fgDownloadRetryQueue = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var fgDownloadRetryJob: kotlinx.coroutines.Job? = null

    /**
     * Whether any foreground receipt-sync coroutine is currently running.
     * Used by `BackgroundSyncWorker` Tier 2 to skip its own receipt sync
     * when the foreground already has it covered — avoids two `syncReceipts()`
     * runs racing on the same ledger / pending queue / download paths.
     */
    fun isReceiptSyncActive(): Boolean =
        uploadDrainerJob?.isActive == true || fgDownloadRetryJob?.isActive == true

    /**
     * Cancel Layer 0 (upload drainer) and Layer 2 (foreground download retry)
     * and clear the retry queue. Called when the device leaves/changes its
     * group, dissolves the group, is evicted, or loses its paid/subscriber
     * status — any transition that invalidates the captured `syncGroupId` or
     * `encryptionKey` that the running coroutines are mid-flight against.
     */
    fun cancelReceiptSyncJobs() {
        uploadDrainerJob?.cancel()
        uploadDrainerJob = null
        fgDownloadRetryJob?.cancel()
        fgDownloadRetryJob = null
        fgDownloadRetryQueue.clear()
    }

    fun kickFgDownloadRetry(receiptId: String) {
        if (!(isPaidUser || isSubscriber)) return
        if (!isSyncConfigured) return
        fgDownloadRetryQueue.add(receiptId)
        if (fgDownloadRetryJob?.isActive == true) return
        val gId = syncGroupId ?: return
        val key = GroupManager.getEncryptionKey(context) ?: return

        fgDownloadRetryJob = viewModelScope.launch(Dispatchers.IO) {
            val backoffs = longArrayOf(30_000L, 60_000L, 2 * 60_000L, 5 * 60_000L, 10 * 60_000L)
            var step = 0
            val receiptSync = com.techadvantage.budgetrak.data.sync.ReceiptSyncManager(
                context, gId, localDeviceId, key
            ) { msg -> BudgeTrakApplication.syncEvent("ReceiptSync(FgRetry): $msg") }
            try {
                while (isActive) {
                    kotlinx.coroutines.delay(backoffs[step.coerceAtMost(backoffs.size - 1)])
                    val snapshot = synchronized(fgDownloadRetryQueue) { fgDownloadRetryQueue.toList() }
                    if (snapshot.isEmpty()) break
                    val photoCapableDeviceIds = syncDevices
                        .filter { it.photoCapable }
                        .map { it.deviceId }
                        .toSet()
                    // Re-filter: drop ids we've since obtained (another path recovered them),
                    // or that are no longer referenced in any transaction.
                    val referenced = com.techadvantage.budgetrak.data.sync.ReceiptManager
                        .collectAllReceiptIds(transactions.toList())
                    val toTry = snapshot.filter { rid ->
                        rid in referenced && !com.techadvantage.budgetrak.data.sync.ReceiptManager
                            .hasLocalFile(context, rid)
                    }
                    fgDownloadRetryQueue.retainAll(toTry.toSet())
                    if (toTry.isEmpty()) break

                    var anySuccess = false
                    for (chunk in toTry.chunked(5)) {
                        kotlinx.coroutines.coroutineScope {
                            for (rid in chunk) {
                                launch {
                                    val ok = try {
                                        receiptSync.downloadReceiptWithRetry(rid, photoCapableDeviceIds)
                                    } catch (e: Exception) {
                                        android.util.Log.w("FgDownloadRetry", "Exception $rid: ${e.message}")
                                        false
                                    }
                                    if (ok) {
                                        fgDownloadRetryQueue.remove(rid)
                                        anySuccess = true
                                    }
                                }
                            }
                        }
                    }
                    step = if (anySuccess) 0 else (step + 1)
                }
                android.util.Log.i("FgDownloadRetry", "Retry queue drained, exiting")
            } catch (e: Exception) {
                android.util.Log.w("FgDownloadRetry", "Exception: ${e.message}")
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
        // Reload transactions from disk on resume to pick up widget-added entries.
        //
        // Add-only merge: pull in any IDs that are on disk but not in memory.
        // Never clear+addAll from disk — the disk read is async and races against
        // (a) in-flight saves on Main and (b) the sync merge's deferred
        // withContext(Dispatchers.IO) write in onBatchChanged. Both can leave
        // memory ahead of disk briefly; a wholesale replace would clobber the
        // newer in-memory state and silently revert just-saved transactions.
        // The widget's only disk effect is adding new transactions (and the
        // rare Keep-New tombstone, which sync resolves separately), so an
        // additive merge covers the original intent without the data-loss risk.
        viewModelScope.launch {
            val diskTransactions = withContext(Dispatchers.IO) { TransactionRepository.load(context) }
            val memoryIds = transactions.map { it.id }.toSet()
            val diskOnly = diskTransactions.filter { it.id !in memoryIds }
            if (diskOnly.isNotEmpty()) {
                android.util.Log.i("OnResume", "Adding ${diskOnly.size} disk-only transactions to memory (likely widget adds)")
                transactions.addAll(diskOnly)
                recomputeCash()
            } else {
                android.util.Log.i("OnResume", "No disk-only transactions, no merge needed")
            }
        }

        // Force App Check token refresh on resume — prevents PERMISSION_DENIED from stale token
        if (isSyncConfigured) {
            try {
                com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                    .getAppCheckToken(false) // false = use cached if valid, force refresh if expired
                    .addOnSuccessListener { /* token refreshed, listeners will reconnect */ }
            } catch (_: Exception) {}
        }

        // Refresh RTDB presence on resume — ensures we show online after Doze/network loss
        if (isSyncConfigured && syncGroupId != null) {
            try {
                val gid = syncGroupId!!
                val deviceName = GroupManager.getDeviceName(context)
                val receiptPrefs = context.getSharedPreferences("receipt_sync_prefs", Context.MODE_PRIVATE)
                com.techadvantage.budgetrak.data.sync.RealtimePresenceService.setupPresence(
                    gid, localDeviceId, deviceName,
                    photoCapable = isPaidUser || isSubscriber,
                    uploadSpeedBps = receiptPrefs.getLong("lastUploadSpeedBps", 0L),
                    uploadSpeedMeasuredAt = receiptPrefs.getLong("lastSpeedMeasuredAt", 0L)
                )
            } catch (e: Exception) {
                android.util.Log.w("OnResume", "Presence refresh failed: ${e.message}")
            }
        }

        // Periodic maintenance (time-gated) — keeps long-lived ViewModels healthy
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("lastMaintenanceCheck", 0L) > 24 * 60 * 60 * 1000L) {
            prefs.edit().putLong("lastMaintenanceCheck", now).apply()
            viewModelScope.launch { runPeriodicMaintenance() }
        } else if (prefs.getLong("checksumMismatchAt", 0L) > 0L) {
            // Mismatch pending — recheck on every resume until resolved
            viewModelScope.launch { recheckConsistency() }
        }

        // Update Crashlytics diagnostic keys (attached to every future crash/non-fatal).
        // Use cash hash (not actual value) to protect user financial privacy.
        // Skipped entirely when user has opted out of crash reporting.
        if (crashlyticsEnabled) {
            val cashDigest = availableCash.toString().hashCode().toString(16)
            BudgeTrakApplication.updateDiagKeys(mapOf(
                "cashDigest" to cashDigest,
                "listenerStatus" to if (docSync?.isListening == true) "healthy" else if (isSyncConfigured) "dead" else "off",
                "lastRefreshDate" to (lastRefreshDate?.toString() ?: "null"),
                "activeDevices" to syncDevices.size.toString(),
                "txnCount" to transactions.count { !it.deleted }.toString(),
                "reCount" to recurringExpenses.count { !it.deleted }.toString(),
                "plCount" to periodLedger.size.toString()
            ))
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WIDGET INTENT HANDLER
    // ═══════════════════════════════════════════════════════════════════

    fun handleWidgetIntent(action: String?) {
        when (action) {
            com.techadvantage.budgetrak.widget.BudgetWidgetProvider.ACTION_ADD_INCOME -> dashboardShowAddIncome = true
            com.techadvantage.budgetrak.widget.BudgetWidgetProvider.ACTION_ADD_EXPENSE -> dashboardShowAddExpense = true
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHARE-INTENT (ACTION_SEND image) HANDLER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called by MainActivity when the app receives shared images via ACTION_SEND
     * or ACTION_SEND_MULTIPLE. Appends to the pending list; the LaunchedEffect
     * in MainActivity consumes the list once dataLoaded is true.
     */
    fun handleSharedImages(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        pendingSharedImageUris.addAll(uris)
    }

    /**
     * True when any dialog or user-driven multi-step process would lose unsaved
     * state if interrupted — EXCLUDING the TransactionDialog (which we route
     * shared images INTO).
     *
     * Being on a non-dashboard screen (Transactions list, Settings, Help pages,
     * etc.) by itself is NOT a blocker — those are navigable views with no
     * mid-flow state. `consumePendingSharedImages` navigates to the dashboard
     * before opening the Add Expense dialog when needed.
     *
     * Fatally-interruptible conditions:
     *   - Any dashboard-level matching dialog (duplicate / recurring / amortization
     *     / budget income) — the user is confirming a match and losing the choice
     *     dumps them into a weird re-prompt state.
     *   - Backup / restore password prompts + "save photos?" confirmation.
     *   - RE/IS amount-change confirmations (mid-flow user decisions).
     *   - CSV import's sequential duplicate-check phase (losing the queue progress
     *     is the classic fatal-interrupt case users flag).
     */
    fun anyNonTransactionDialogOpen(): Boolean {
        return dashShowManualDuplicateDialog ||
            dashShowRecurringDialog ||
            dashShowAmortizationDialog ||
            dashShowBudgetIncomeDialog ||
            showBackupPasswordDialog ||
            showDisableBackupDialog ||
            showRestoreDialog ||
            showSavePhotosDialog ||
            pendingREAmountUpdate != null ||
            pendingISAmountUpdate != null ||
            csvImportInProgress ||
            shareBlockingDialogCount > 0
    }

    /**
     * Consume queued shared image URIs. Routes based on current dialog state.
     * Gate order matters — see below.
     *
     *   Gate 1: TransactionDialog already open → leave URIs in the list; the
     *     dialog's LaunchedEffect absorbs up to the remaining empty slots,
     *     discards overflow, and fires shareOverflowToastPending if any
     *     dropped. Checked FIRST because AdAwareDialog auto-registers the
     *     TransactionDialog itself in shareBlockingDialogCount; if Gate 2
     *     ran first, multi-share would bounce its second+ image after
     *     opening the dialog with the first.
     *   Gate 2: some OTHER dialog open (RE/IS/SG/AE/Category edit, backup
     *     password, restore, save photos, amount-update prompt) or CSV
     *     import's duplicate-check phase → discard URIs and toast. Protects
     *     in-flight user edits on non-dashboard screens.
     *   Gate 3: nothing blocking → process the FIRST URI to a receiptId
     *     (slot-1 seed via pendingSharedReceiptId + initialReceiptId1),
     *     open the Add Expense dialog, and leave the remainder in
     *     pendingSharedImageUris for the dialog's absorber to fill slots
     *     2-5. Works whether the user was on the dashboard or elsewhere.
     *
     * Free users: dialog still opens (so they can record the transaction), but
     * all photos are discarded and a 5s upgrade toast is shown.
     *
     * Runs in viewModelScope so state writes (recomposition → LaunchedEffect
     * cancellation) don't kill the photo-processing work mid-flight.
     */
    fun consumePendingSharedImages(canAttachPhotos: Boolean) {
        if (pendingSharedImageUris.isEmpty()) return

        // Gate 1: TransactionDialog already open → absorb regardless of what
        // else is open. Must run BEFORE the generic block-gate because the
        // AdAwareDialog wrapper auto-registers TransactionDialog itself in
        // shareBlockingDialogCount — checking the block-gate first would bounce
        // the second image of a multi-share that opened the dialog with the
        // first image.
        if (transactionDialogOpen) {
            if (!canAttachPhotos) {
                pendingSharedImageUris.clear()
                sharedPhotoBlockedToastPending = true
            }
            // else: the open dialog's LaunchedEffect picks up pendingSharedImageUris.
            return
        }

        // Gate 2: some OTHER dialog open or CSV-import mid-flow → drop + toast.
        if (anyNonTransactionDialogOpen()) {
            pendingSharedImageUris.clear()
            shareBlockedByDialogToastPending = true
            return
        }

        // Gate 3: no dialog open — open a fresh Add Expense dialog.
        val uris = pendingSharedImageUris.toList()
        pendingSharedImageUris.clear()

        viewModelScope.launch {
            currentScreen = "main"
            if (!canAttachPhotos) {
                dashboardShowAddExpense = true
                sharedPhotoBlockedToastPending = true
                return@launch
            }
            // Process the FIRST uri now so TransactionDialog can seed slot 1
            // via initialReceiptId1 on its first composition. Remaining URIs
            // are handed to the dialog via pendingSharedImageUris for async
            // absorption into slots 2-5.
            val first = uris.first()
            val receiptId = withContext(Dispatchers.IO) {
                com.techadvantage.budgetrak.data.sync.ReceiptManager.processAndSavePhoto(context, first)
            }
            if (receiptId != null) {
                // Queue-for-upload happens at dialog-save time (saveTransactions);
                // if the user cancels, the receipt becomes an orphan that
                // background cleanup will prune.
                pendingSharedReceiptId = receiptId
            }
            if (uris.size > 1) {
                // Re-queue slots 2..N for post-open absorption.
                pendingSharedImageUris.addAll(uris.drop(1))
            }
            dashboardShowAddExpense = true
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // AI OCR (subscriber-only, explicit tap trigger, reads slot-1 photo)
    // ═══════════════════════════════════════════════════════════════════

    fun runOcrOnSlot1(receiptId: String, preSelectedCategoryIds: Set<Int> = emptySet()) {
        if (ocrState is OcrState.Loading) return
        // Fail fast when offline. Gemini calls don't have a fast-fail path —
        // they wait the full SDK timeout (~30-60 s) before erroring. The
        // dialog's OcrState.Failed handler picks the offline-specific toast
        // when message == "OFFLINE".
        if (!isNetworkAvailable) {
            ocrState = OcrState.Failed("OFFLINE")
            return
        }
        ocrState = OcrState.Loading
        // ReceiptOcrService routes internally by preSelectedCategoryIds.size:
        //   0-1 preselected → single-call Lite (1 API call, cheapest path)
        //   2+ preselected  → 3-call Lite pipeline (header + items+cats + prices + reconcile)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ReceiptOcrService.extractFromReceipt(
                    context, receiptId, categories.toList(), preSelectedCategoryIds
                )
            }
            ocrState = result.fold(
                onSuccess = { OcrState.Success(it) },
                onFailure = { OcrState.Failed(it.message ?: "OCR failed") }
            )
        }
    }

    fun clearOcrState() {
        ocrState = OcrState.Idle
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

            networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    isNetworkAvailable = true
                    // Refresh App Check token on network recovery (may have expired during outage)
                    if (isSyncConfigured) {
                        try { com.google.firebase.appcheck.FirebaseAppCheck.getInstance().getAppCheckToken(false) } catch (_: Exception) {}
                    }
                    if (isSyncConfigured && syncStatus == "offline") {
                        syncStatus = if (docSync?.isListening == true) "synced" else "error"
                    }
                    // Resume queued receipt uploads. The drainer's exponential
                    // backoff loop may currently be sleeping (up to 10 min)
                    // after no-progress cycles during the outage; cancel +
                    // restart so uploads resume immediately rather than
                    // waiting out the backoff.
                    val pending = com.techadvantage.budgetrak.data.sync.ReceiptManager
                        .loadPendingUploads(context).size
                    if (pending > 0 && (isPaidUser || isSubscriber) && isSyncConfigured) {
                        uploadDrainerJob?.cancel()
                        uploadDrainerJob = null
                        kickUploadDrainer()
                    }
                }
                override fun onLost(network: android.net.Network) {
                    isNetworkAvailable = false
                    if (isSyncConfigured) syncStatus = "offline"
                }
            }
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
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
                do { id = (1..Int.MAX_VALUE).random() } while (id in usedIds)
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
                // Crash-recovery: if the pending upload queue survived from a
                // prior session, drain it now.
                kickUploadDrainer()
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
        fun formatElapsed(syncTime: Long): String? {
            val elapsed = if (syncTime > 0) (System.currentTimeMillis() - syncTime) / 1000 else -1L
            return when {
                elapsed < 0 -> null
                elapsed < 10 -> "just now"
                elapsed < 60 -> "${elapsed}s ago"
                elapsed < 3600 -> "${elapsed / 60}m ago"
                else -> "${elapsed / 3600}h ago"
            }
        }
        viewModelScope.launch {
            snapshotFlow { lastSyncActivity }.collect { lastSyncTimeDisplay = formatElapsed(it) }
        }
        viewModelScope.launch {
            while (true) { delay(10_000); lastSyncTimeDisplay = formatElapsed(lastSyncActivity) }
        }

        // ── Sync group configuration (waits for data) ──
        viewModelScope.launch {
            snapshotFlow { dataLoaded }.first { it }
            if (isSyncConfigured && syncGroupId != null) {
                configureSyncGroup()
            }
            if (isSyncConfigured) {
                com.techadvantage.budgetrak.data.sync.BackgroundSyncWorker.schedule(context)
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
        }

        // ── Quick start auto-launch (one-time) ──
        viewModelScope.launch {
            snapshotFlow { dataLoaded }.first { it }
            if (incomeSources.isEmpty() && !isSyncConfigured &&
                !prefs.getBoolean("quickStartCompleted", false)) {
                quickStartStep = QuickStartStep.WELCOME
            }
        }

        // ── Period refresh (sleeps until next boundary, rechecks on wake) ──
        viewModelScope.launch {
            snapshotFlow { dataLoaded }.first { it }
            // Wait for initial sync (max 60s to prevent indefinite block)
            val syncWaitStart = System.currentTimeMillis()
            while (!initialSyncReceived && System.currentTimeMillis() - syncWaitStart < 60_000) { delay(200) }
            android.util.Log.i("PeriodRefresh", "Starting loop: initialSyncReceived=$initialSyncReceived bsd=$budgetStartDate lrd=$lastRefreshDate")
            while (true) {
                val bsd = budgetStartDate
                val lrd = lastRefreshDate
                if (bsd != null && lrd != null) {
                    val config = PeriodRefreshService.RefreshConfig(
                        budgetStartDate = bsd,
                        lastRefreshDate = lrd,
                        budgetPeriod = budgetPeriod,
                        resetHour = resetHour,
                        resetDayOfWeek = resetDayOfWeek,
                        resetDayOfMonth = resetDayOfMonth,
                        familyTimezone = if (isSyncConfigured && sharedSettings.familyTimezone.isNotEmpty())
                            sharedSettings.familyTimezone else "",
                        localDeviceId = localDeviceId,
                        incomeMode = incomeMode,
                        isManualBudgetEnabled = isManualBudgetEnabled,
                        manualBudgetAmount = manualBudgetAmount,
                        carryForwardBalance = carryForwardBalance,
                        archiveCutoffDate = archiveCutoffDate
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
                // Sleep until next period boundary + 60s buffer, minimum 60s
                val tz = if (isSyncConfigured && sharedSettings.familyTimezone.isNotEmpty())
                    java.time.ZoneId.of(sharedSettings.familyTimezone) else java.time.ZoneId.systemDefault()
                val currentStart = BudgetCalculator.currentPeriodStart(
                    budgetPeriod, resetDayOfWeek, resetDayOfMonth, tz, resetHour
                )
                val nextBoundary = when (budgetPeriod) {
                    BudgetPeriod.DAILY -> currentStart.plusDays(1).atTime(resetHour, 0)
                    BudgetPeriod.WEEKLY -> currentStart.plusWeeks(1).atStartOfDay()
                    BudgetPeriod.MONTHLY -> {
                        val nextMonth = currentStart.plusMonths(1)
                        val clampedDay = resetDayOfMonth.coerceAtMost(nextMonth.lengthOfMonth())
                        nextMonth.withDayOfMonth(clampedDay).atStartOfDay()
                    }
                }
                val nowMs = System.currentTimeMillis()
                val boundaryMs = nextBoundary.atZone(tz).toInstant().toEpochMilli()
                val sleepMs = (boundaryMs - nowMs + 60_000).coerceIn(60_000, 15 * 60_000L)
                android.util.Log.i("PeriodRefresh", "Next boundary: $nextBoundary, sleeping ${sleepMs / 1000}s")
                delay(sleepMs)
            }
        }

        // ── App Check token keep-alive (prevents PERMISSION_DENIED on long-lived listeners) ──
        if (isSyncConfigured) {
            viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    delay(45 * 60 * 1000L) // check every 45 min
                    // Skip when offline — App Check refresh requires network.
                    // The networkCallback.onAvailable refreshes on recovery, so
                    // we don't need to re-check until the next 45-min cycle.
                    if (!isNetworkAvailable) continue
                    try {
                        val token = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                            com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                                .getAppCheckToken(false).await()
                        }
                        if (token != null) {
                            val remainingMs = token.expireTimeMillis - System.currentTimeMillis()
                            // Threshold matches Worker Tier 2/3 (16 min) so the VM
                            // keep-alive doesn't fire redundantly alongside the
                            // periodic Worker (which runs every 15 min and catches
                            // the boundary first). VM remains as the safety net
                            // for cases where Worker is silenced for >45 min by
                            // deep Doze and FCM heartbeats are also dropped.
                            if (remainingMs < 16 * 60 * 1000L) {
                                val newToken = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                                    com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                                        .getAppCheckToken(true).await()
                                }
                                if (newToken != null) {
                                    val newRemaining = (newToken.expireTimeMillis - System.currentTimeMillis()) / 1000
                                    BudgeTrakApplication.tokenLog("Proactive token refresh (ViewModel): was ${remainingMs/1000}s from expiry, new expires in ${newRemaining}s")
                                } else {
                                    BudgeTrakApplication.recordNonFatal(
                                        "TOKEN_REFRESH_TIMEOUT",
                                        "ViewModel proactive refresh hung for 15s, remaining was ${remainingMs/1000}s")
                                }
                            }
                        }
                    } catch (_: Exception) {}
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
        com.techadvantage.budgetrak.data.sync.RealtimePresenceService.cleanup()
        imageLedgerListener?.remove()
        deviceDocListener?.remove()
        adminClaimListener?.remove()
        try {
            networkCallback?.let {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                cm.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
    }
}
