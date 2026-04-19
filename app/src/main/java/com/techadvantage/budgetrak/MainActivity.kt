package com.techadvantage.budgetrak

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.material3.OutlinedButton
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.techadvantage.budgetrak.data.AmortizationEntry
import com.techadvantage.budgetrak.data.BudgetCalculator
import com.techadvantage.budgetrak.data.BudgetPeriod
import com.techadvantage.budgetrak.data.IncomeMode
import com.techadvantage.budgetrak.data.SavingsSimulator
import com.techadvantage.budgetrak.data.Category
import com.techadvantage.budgetrak.data.CategoryAmount
import com.techadvantage.budgetrak.data.CategoryRepository
import com.techadvantage.budgetrak.data.getDoubleCompat
import com.techadvantage.budgetrak.data.getAllKnownNamesForTag
import com.techadvantage.budgetrak.data.getDefaultCategoryName
import com.techadvantage.budgetrak.data.AmortizationRepository
import com.techadvantage.budgetrak.data.SavingsGoal
import com.techadvantage.budgetrak.data.SavingsGoalRepository
import com.techadvantage.budgetrak.data.SuperchargeMode
import kotlin.math.ceil
import com.techadvantage.budgetrak.data.IncomeSource
import com.techadvantage.budgetrak.data.IncomeSourceRepository
import com.techadvantage.budgetrak.data.RecurringExpense
import com.techadvantage.budgetrak.data.RecurringExpenseRepository
import com.techadvantage.budgetrak.data.Transaction
import com.techadvantage.budgetrak.data.TransactionRepository
import com.techadvantage.budgetrak.data.TransactionType
import com.techadvantage.budgetrak.data.generateTransactionId
import com.techadvantage.budgetrak.data.SharedSettings
import com.techadvantage.budgetrak.data.SharedSettingsRepository
import com.techadvantage.budgetrak.data.sync.DeviceInfo
import com.techadvantage.budgetrak.data.sync.FcmSender
import com.techadvantage.budgetrak.data.sync.FirestoreService
import com.techadvantage.budgetrak.data.sync.GroupManager
import com.techadvantage.budgetrak.data.sync.PeriodLedgerEntry
import com.techadvantage.budgetrak.data.sync.PeriodLedgerRepository
import com.techadvantage.budgetrak.data.sync.SyncIdGenerator
import com.techadvantage.budgetrak.data.sync.SyncWriteHelper
import com.techadvantage.budgetrak.data.PeriodRefreshService
import com.techadvantage.budgetrak.data.CryptoHelper
import com.techadvantage.budgetrak.data.sync.FirestoreDocSync
import com.techadvantage.budgetrak.data.sync.ImageLedgerService
import com.techadvantage.budgetrak.data.sync.EncryptedDocSerializer
import com.techadvantage.budgetrak.data.sync.FirestoreDocService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.techadvantage.budgetrak.data.BackupManager
import com.techadvantage.budgetrak.data.DiagDumpBuilder
import com.techadvantage.budgetrak.data.FullBackupSerializer
import com.techadvantage.budgetrak.data.findAmortizationMatches
import com.techadvantage.budgetrak.data.findBudgetIncomeMatches
import com.techadvantage.budgetrak.data.isRecurringDateCloseEnough
import com.techadvantage.budgetrak.sound.FlipSoundPlayer
import com.techadvantage.budgetrak.ui.screens.AmortizationConfirmDialog
import com.techadvantage.budgetrak.ui.screens.AmortizationHelpScreen
import com.techadvantage.budgetrak.ui.screens.AmortizationScreen
import com.techadvantage.budgetrak.ui.screens.BudgetConfigHelpScreen
import com.techadvantage.budgetrak.ui.screens.BudgetConfigScreen
import com.techadvantage.budgetrak.ui.screens.BudgetIncomeConfirmDialog
import com.techadvantage.budgetrak.ui.screens.DashboardHelpScreen
import com.techadvantage.budgetrak.ui.screens.DuplicateResolutionDialog
import com.techadvantage.budgetrak.data.sync.AdminClaim
import com.techadvantage.budgetrak.ui.screens.SyncHelpScreen
import com.techadvantage.budgetrak.ui.screens.SyncScreen
import com.techadvantage.budgetrak.ui.screens.SavingsGoalsHelpScreen
import com.techadvantage.budgetrak.ui.screens.SavingsGoalsScreen
import com.techadvantage.budgetrak.ui.screens.QuickStartOverlay
import com.techadvantage.budgetrak.ui.screens.QuickStartStep
import com.techadvantage.budgetrak.ui.screens.MainScreen
import com.techadvantage.budgetrak.ui.screens.RecurringExpenseConfirmDialog
import com.techadvantage.budgetrak.ui.screens.RecurringExpensesHelpScreen
import com.techadvantage.budgetrak.ui.screens.RecurringExpensesScreen
import com.techadvantage.budgetrak.ui.screens.SettingsHelpScreen
import com.techadvantage.budgetrak.ui.screens.BudgetCalendarScreen
import com.techadvantage.budgetrak.ui.screens.BudgetCalendarHelpScreen
import com.techadvantage.budgetrak.ui.screens.SimulationGraphHelpScreen
import com.techadvantage.budgetrak.ui.screens.SimulationGraphScreen
import com.techadvantage.budgetrak.ui.screens.SettingsScreen
import com.techadvantage.budgetrak.ui.screens.TransactionDialog
import com.techadvantage.budgetrak.ui.screens.TransactionsHelpScreen
import com.techadvantage.budgetrak.ui.screens.TransactionsScreen
import com.techadvantage.budgetrak.ui.strings.AppStrings
import com.techadvantage.budgetrak.ui.strings.EnglishStrings
import com.techadvantage.budgetrak.ui.strings.SpanishStrings
import com.techadvantage.budgetrak.ui.theme.AdAwareAlertDialog
import com.techadvantage.budgetrak.ui.theme.AdAwareDialog
import com.techadvantage.budgetrak.ui.theme.DialogDangerButton
import com.techadvantage.budgetrak.ui.theme.DialogPrimaryButton
import com.techadvantage.budgetrak.ui.theme.DialogFooter
import com.techadvantage.budgetrak.ui.theme.DialogHeader
import com.techadvantage.budgetrak.ui.theme.DialogStyle
import com.techadvantage.budgetrak.ui.theme.DialogWarningButton
import com.techadvantage.budgetrak.ui.theme.DialogSecondaryButton
import com.techadvantage.budgetrak.ui.theme.SyncBudgetTheme
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
import androidx.compose.foundation.layout.size
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
import com.techadvantage.budgetrak.ui.theme.AppToastState
import com.techadvantage.budgetrak.ui.theme.LocalAppToast
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    companion object {
        /** True when the app is visible to the user. Background workers check this to skip. */
        @Volatile var isAppActive = false

        /**
         * Extracts image URIs from an ACTION_SEND or ACTION_SEND_MULTIPLE intent.
         * Returns empty list if the intent isn't a relevant share.
         */
        internal fun extractSharedImageUris(intent: android.content.Intent?): List<android.net.Uri> {
            if (intent == null) return emptyList()
            val type = intent.type ?: return emptyList()
            if (!type.startsWith("image/")) return emptyList()
            return when (intent.action) {
                android.content.Intent.ACTION_SEND -> {
                    val u = if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM) as? android.net.Uri
                    }
                    listOfNotNull(u)
                }
                android.content.Intent.ACTION_SEND_MULTIPLE -> {
                    val list = if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
                    }
                    list ?: emptyList()
                }
                else -> emptyList()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uris = extractSharedImageUris(intent)
        if (uris.isEmpty()) return
        val vm = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
        vm.handleSharedImages(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crash logger — file output in debug builds only (Crashlytics handles production)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (BuildConfig.DEBUG) {
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
                    val crashFile = java.io.File(dir, "crash_log.txt")
                    if (crashFile.exists() && crashFile.length() > 100_000) crashFile.writeText("")
                    crashFile.appendText(sb.toString())
                } catch (_: Exception) {}
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        isAppActive = true
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()

            // Sound player (needs Activity lifecycle)
            val soundPlayer = remember { FlipSoundPlayer(this@MainActivity) }
            DisposableEffect(Unit) {
                onDispose { soundPlayer.release() }
            }

            // Widget intent + share-intent (check once, forward to VM)
            androidx.compose.runtime.LaunchedEffect(Unit) {
                vm.handleWidgetIntent(intent?.action)
                vm.handleSharedImages(extractSharedImageUris(intent))
            }

            // ── Loading gate: block all UI until data is ready ──
            if (!vm.dataLoaded) {
                LoadingScreen(vm.loadProgress)
                return@setContent
            }

            // Lifecycle observer — registered AFTER data loads so the initial
            // ON_RESUME during Activity creation is missed (we don't need it).
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    android.util.Log.i("Lifecycle", "Event: $event, dataLoaded=${vm.dataLoaded}, txns=${vm.transactions.size}")
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_START) isAppActive = true
                    else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) isAppActive = false
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.onResume()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val adBannerHeight = if (!vm.isPaidUser) 50.dp else 0.dp
            SyncBudgetTheme(strings = vm.strings, adBannerHeight = adBannerHeight) {
              val toastState = LocalAppToast.current
              androidx.compose.runtime.CompositionLocalProvider(
                  com.techadvantage.budgetrak.ui.theme.LocalShareBlockingDialogRegistrar provides { open: Boolean ->
                      if (open) vm.shareBlockingDialogCount++ else vm.shareBlockingDialogCount--
                  }
              ) {
              // Archive toast
              LaunchedEffect(vm.archiveToastMessage) {
                  vm.archiveToastMessage?.let { msg ->
                      toastState.show(msg)
                      vm.archiveToastMessage = null
                  }
              }
              // Consume shared-image intent(s) once data is loaded. Keyed on list size
              // so subsequent shares (cleared → refilled) retrigger. The VM's route
              // logic picks the right target based on whether a dialog is open.
              val pendingShareCount = vm.pendingSharedImageUris.size
              LaunchedEffect(pendingShareCount) {
                  if (pendingShareCount > 0) {
                      vm.consumePendingSharedImages(canAttachPhotos = vm.isPaidUser || vm.isSubscriber)
                  }
              }
              // Free-user gate: when a shared image is discarded, show a 5s upgrade toast.
              LaunchedEffect(vm.sharedPhotoBlockedToastPending) {
                  if (vm.sharedPhotoBlockedToastPending) {
                      toastState.show(vm.strings.settings.sharedPhotoNeedsUpgrade, durationMs = 5000L)
                      vm.sharedPhotoBlockedToastPending = false
                  }
              }
              // Share-while-other-dialog-open: drop URIs + ask user to close the dialog.
              LaunchedEffect(vm.shareBlockedByDialogToastPending) {
                  if (vm.shareBlockedByDialogToastPending) {
                      toastState.show(vm.strings.settings.shareBlockedByOpenDialog, durationMs = 5000L)
                      vm.shareBlockedByDialogToastPending = false
                  }
              }
              // Share-overflow: dialog absorbed as many URIs as fit; the rest were dropped.
              LaunchedEffect(vm.shareOverflowToastPending) {
                  if (vm.shareOverflowToastPending) {
                      toastState.show(vm.strings.settings.shareOverflowDiscarded, durationMs = 5000L)
                      vm.shareOverflowToastPending = false
                  }
              }
              Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                // Ad banner placeholder (320x50 standard banner)
                if (!vm.isPaidUser) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(Color.Black)
                            .border(1.dp, Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(vm.strings.dashboard.adPlaceholder, color = Color.Gray, fontSize = 12.sp)
                    }
                }

                // Screen content
                Box(modifier = Modifier.weight(1f)) {
                if (vm.currentScreen == "main") {
                    BackHandler { moveTaskToBack(true) }
                } else {
                    BackHandler {
                        vm.currentScreen = when (vm.currentScreen) {
                            "settings_help" -> "settings"
                            "transactions_help" -> "transactions"
                            "savings_goals_help" -> "savings_goals"
                            "amortization_help" -> "amortization"
                            "recurring_expenses_help" -> "recurring_expenses"
                            "budget_config_help" -> "budget_config"
                            "simulation_graph_help" -> "simulation_graph"
                            "simulation_graph" -> "savings_goals"
                            "budget_config" -> "settings"
                            "budget_calendar_help" -> "budget_calendar"
                            "sync" -> "settings"
                            "sync_help" -> "sync"
                            else -> "main"
                        }
                    }
                }

                // Sync eviction popup — shown once when dashboard is displayed
                if (vm.currentScreen == "main" && vm.syncEvictionMessage != null) {
                    AdAwareAlertDialog(
                        onDismissRequest = { vm.syncEvictionMessage = null },
                        title = { DialogHeader("Sync") },
                        text = { Text(vm.syncEvictionMessage ?: "") },
                        confirmButton = {
                            DialogPrimaryButton(onClick = { vm.syncEvictionMessage = null }) {
                                Text(vm.strings.common.ok)
                            }
                        }
                    )
                }

                // Admin claim vote popup — shown on dashboard when another device claims admin
                if (vm.currentScreen == "main" && vm.adminClaimVoteNeeded != null) {
                    val claim = vm.adminClaimVoteNeeded!!
                    AdAwareAlertDialog(
                        onDismissRequest = { vm.adminClaimVoteNeeded = null },
                        title = { DialogHeader("Sync") },
                        text = { Text(vm.strings.sync.claimVotePrompt(claim.claimantName)) },
                        confirmButton = {
                            DialogPrimaryButton(onClick = { vm.voteOnAdminClaim("accept") }) {
                                Text(vm.strings.sync.claimAccept)
                            }
                        },
                        dismissButton = {
                            DialogDangerButton(onClick = { vm.voteOnAdminClaim("reject") }) {
                                Text(vm.strings.sync.claimReject)
                            }
                        }
                    )
                }

                // Admin claim result popup — shown on dashboard for claimant
                if (vm.currentScreen == "main" && vm.adminClaimMessage != null) {
                    AdAwareAlertDialog(
                        onDismissRequest = { vm.adminClaimMessage = null },
                        title = { DialogHeader("Sync") },
                        text = { Text(vm.adminClaimMessage ?: "") },
                        confirmButton = {
                            DialogPrimaryButton(onClick = { vm.adminClaimMessage = null }) {
                                Text(vm.strings.common.ok)
                            }
                        }
                    )
                }

                when (vm.currentScreen) {
                    "main" -> MainScreen(
                        soundPlayer = soundPlayer,
                        currencySymbol = vm.currencySymbol,
                        digitCount = vm.digitCount,
                        showDecimals = vm.showDecimals,
                        availableCash = vm.availableCash,
                        budgetAmount = vm.budgetAmount,
                        budgetStartDate = vm.budgetStartDate?.toString(),
                        budgetPeriodLabel = when (vm.budgetPeriod) {
                            BudgetPeriod.DAILY -> vm.strings.common.periodDay
                            BudgetPeriod.WEEKLY -> vm.strings.common.periodWeek
                            BudgetPeriod.MONTHLY -> vm.strings.common.periodMonth
                        },
                        savingsGoals = vm.activeSavingsGoals,
                        transactions = vm.activeTransactions,
                        categories = vm.activeCategories,
                        onSettingsClick = { vm.currentScreen = "settings" },
                        onNavigate = { vm.currentScreen = it },
                        onAddIncome = { vm.dashboardShowAddIncome = true },
                        onAddExpense = { vm.dashboardShowAddExpense = true },
                        weekStartDay = if (vm.weekStartSunday) java.time.DayOfWeek.SUNDAY else java.time.DayOfWeek.MONDAY,
                        chartPalette = vm.chartPalette,
                        dateFormatPattern = vm.dateFormatPattern,
                        budgetPeriod = vm.budgetPeriod,
                        syncStatus = vm.syncStatus,
                        syncDevices = vm.syncDevices,
                        localDeviceId = vm.localDeviceId,
                        syncRepairAlert = vm.syncRepairAlert,
                        onDismissRepairAlert = {
                            vm.syncRepairAlert = false
                            vm.prefs.edit().putBoolean("syncRepairAlert", false).apply()
                        },
                        onSyncNow = { vm.doSyncNow() },
                        onSupercharge = { allocations, modes ->
                            val deposits = mutableListOf<Pair<String, Double>>() // goalName to capped amount
                            val changedGoals = mutableListOf<SavingsGoal>()
                            for ((goalId, amount) in allocations) {
                                val idx = vm.savingsGoals.indexOfFirst { it.id == goalId }
                                if (idx >= 0) {
                                    val goal = vm.savingsGoals[idx]
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
                                        vm.savingsGoals[idx] = updatedGoal
                                        changedGoals.add(updatedGoal)
                                        deposits.add(goal.name to capped)
                                    }
                                }
                            }
                            if (deposits.isNotEmpty()) {
                                vm.saveSavingsGoals(changedGoals)
                                val superchargeCatId = vm.categories.find { it.tag == "supercharge" }?.id
                                val currentIds = vm.transactions.map { it.id }.toSet()
                                for ((goalName, depositAmount) in deposits) {
                                    val txn = Transaction(
                                        id = generateTransactionId(currentIds + vm.transactions.map { it.id }.toSet()),
                                        source = "Savings: $goalName",
                                        amount = depositAmount,
                                        date = LocalDate.now(),
                                        type = TransactionType.EXPENSE,
                                        categoryAmounts = if (superchargeCatId != null) listOf(CategoryAmount(superchargeCatId, depositAmount)) else emptyList()
                                    )
                                    vm.addTransactionWithBudgetEffect(txn)
                                }
                            }
                        }
                    )
                    "settings" -> SettingsScreenBranch(vm, toastState)
                    "transactions" -> TransactionsScreenBranch(vm, toastState)
                    "savings_goals" -> SavingsGoalsScreen(
                        isPaidUser = vm.isPaidUser,
                        isSubscriber = vm.isSubscriber,
                        savingsGoals = vm.activeSavingsGoals,
                        transactions = vm.activeTransactions,
                        currencySymbol = vm.currencySymbol,
                        budgetPeriod = vm.budgetPeriod,
                        dateFormatPattern = vm.dateFormatPattern,
                        recurringExpenses = vm.activeRecurringExpenses,
                        incomeSources = vm.activeIncomeSources,
                        amortizationEntries = vm.activeAmortizationEntries,
                        baseBudget = if (vm.isManualBudgetEnabled) vm.manualBudgetAmount else vm.safeBudgetAmount,
                        availableCash = vm.simAvailableCash,
                        resetDayOfWeek = vm.resetDayOfWeek,
                        resetDayOfMonth = vm.resetDayOfMonth,
                        today = vm.budgetToday,
                        isManualOverBudget = vm.isManualBudgetEnabled && vm.manualBudgetAmount > vm.safeBudgetAmount,
                        budgetPeriodLabel = when (vm.budgetPeriod) {
                            BudgetPeriod.DAILY -> vm.strings.savingsGoals.savingsPeriodDaily
                            BudgetPeriod.WEEKLY -> vm.strings.savingsGoals.savingsPeriodWeekly
                            BudgetPeriod.MONTHLY -> vm.strings.savingsGoals.savingsPeriodMonthly
                        },
                        onAddGoal = { goal ->
                            val added = goal.copy(deviceId = vm.localDeviceId)
                            vm.savingsGoals.add(added)
                            vm.saveSavingsGoals(listOf(added))
                        },
                        onUpdateGoal = { updated ->
                            val idx = vm.savingsGoals.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = vm.savingsGoals[idx]
                                vm.savingsGoals[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deleted = old.deleted,
                                )
                                vm.saveSavingsGoals(listOf(vm.savingsGoals[idx]))
                            }
                        },
                        onDeleteGoal = { goal ->
                            val idx = vm.savingsGoals.indexOfFirst { it.id == goal.id }
                            if (idx >= 0) {
                                vm.savingsGoals[idx] = vm.savingsGoals[idx].copy(deleted = true)
                                vm.saveSavingsGoals(listOf(vm.savingsGoals[idx]))
                                val unlinkedTxns = mutableListOf<Transaction>()
                                vm.transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedSavingsGoalId == goal.id) {
                                        vm.transactions[i] = txn.copy(linkedSavingsGoalId = null)
                                        unlinkedTxns.add(vm.transactions[i])
                                    }
                                }
                                vm.saveTransactions(unlinkedTxns)
                                vm.recomputeCash()
                            }
                        },
                        onBack = { vm.currentScreen = "main" },
                        onHelpClick = { vm.currentScreen = "savings_goals_help" },
                        onViewChart = { vm.currentScreen = "simulation_graph" },
                        autoCapitalize = vm.autoCapitalize
                    )
                    "simulation_graph" -> SimulationGraphScreen(
                        incomeSources = vm.activeIncomeSources,
                        recurringExpenses = vm.activeRecurringExpenses,
                        budgetPeriod = vm.budgetPeriod,
                        baseBudget = if (vm.isManualBudgetEnabled) vm.manualBudgetAmount else vm.safeBudgetAmount,
                        amortizationEntries = vm.activeAmortizationEntries,
                        savingsGoals = vm.activeSavingsGoals,
                        availableCash = vm.simAvailableCash,
                        resetDayOfWeek = vm.resetDayOfWeek,
                        resetDayOfMonth = vm.resetDayOfMonth,
                        currencySymbol = vm.currencySymbol,
                        today = vm.budgetToday,
                        onBack = { vm.currentScreen = "savings_goals" },
                        onHelpClick = { vm.currentScreen = "simulation_graph_help" }
                    )
                    "amortization" -> AmortizationScreen(
                        amortizationEntries = vm.activeAmortizationEntries,
                        currencySymbol = vm.currencySymbol,
                        budgetPeriod = vm.budgetPeriod,
                        dateFormatPattern = vm.dateFormatPattern,
                        transactions = vm.activeTransactions,
                        onAddEntry = { entry ->
                            val added = entry.copy(deviceId = vm.localDeviceId)
                            vm.amortizationEntries.add(added)
                            vm.saveAmortizationEntries(listOf(added))
                        },
                        onUpdateEntry = { updated ->
                            val idx = vm.amortizationEntries.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = vm.amortizationEntries[idx]
                                vm.amortizationEntries[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deleted = old.deleted,
                                )
                                vm.saveAmortizationEntries(listOf(vm.amortizationEntries[idx]))
                            }
                        },
                        onDeleteEntry = { entry ->
                            val idx = vm.amortizationEntries.indexOfFirst { it.id == entry.id }
                            if (idx >= 0) {
                                val elapsed = BudgetCalculator.countElapsedPeriods(
                                    entry.startDate, vm.budgetToday, vm.budgetPeriod, vm.resetDayOfWeek
                                ).coerceIn(0, entry.totalPeriods)
                                val appliedAmount = BudgetCalculator.roundCents(
                                    entry.amount * elapsed.toDouble() / entry.totalPeriods
                                )

                                vm.amortizationEntries[idx] = vm.amortizationEntries[idx].copy(deleted = true)
                                vm.saveAmortizationEntries(listOf(vm.amortizationEntries[idx]))
                                val unlinkedTxns = mutableListOf<Transaction>()
                                vm.transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedAmortizationEntryId == entry.id) {
                                        vm.transactions[i] = txn.copy(
                                            linkedAmortizationEntryId = null,
                                            amortizationAppliedAmount = appliedAmount,
                                        )
                                        unlinkedTxns.add(vm.transactions[i])
                                    }
                                }
                                vm.saveTransactions(unlinkedTxns)
                                vm.recomputeCash()
                            }
                        },
                        onBack = { vm.currentScreen = "main" },
                        onHelpClick = { vm.currentScreen = "amortization_help" },
                        autoCapitalize = vm.autoCapitalize
                    )
                    "recurring_expenses" -> RecurringExpensesScreen(
                        recurringExpenses = vm.activeRecurringExpenses,
                        transactions = vm.activeTransactions,
                        currencySymbol = vm.currencySymbol,
                        dateFormatPattern = vm.dateFormatPattern,
                        onAddRecurringExpense = { expense ->
                            val added = expense.copy(deviceId = vm.localDeviceId)
                            vm.recurringExpenses.add(added)
                            vm.saveRecurringExpenses(listOf(added))
                        },
                        onUpdateRecurringExpense = { updated ->
                            val idx = vm.recurringExpenses.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = vm.recurringExpenses[idx]
                                val amountChanged = updated.amount != old.amount
                                val hasLinkedTxns = amountChanged && vm.transactions.any {
                                    it.linkedRecurringExpenseId == updated.id && !it.deleted
                                }
                                vm.recurringExpenses[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deleted = old.deleted,
                                )
                                vm.saveRecurringExpenses(listOf(vm.recurringExpenses[idx]))
                                if (hasLinkedTxns) {
                                    vm.pendingREAmountUpdate = Pair(updated, old.amount)
                                }
                            }
                        },
                        onDeleteRecurringExpense = { expense ->
                            val idx = vm.recurringExpenses.indexOfFirst { it.id == expense.id }
                            if (idx >= 0) {
                                vm.recurringExpenses[idx] = vm.recurringExpenses[idx].copy(deleted = true)
                                vm.saveRecurringExpenses(listOf(vm.recurringExpenses[idx]))
                                val unlinkedTxns = mutableListOf<Transaction>()
                                vm.transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedRecurringExpenseId == expense.id) {
                                        vm.transactions[i] = txn.copy(linkedRecurringExpenseId = null)
                                        unlinkedTxns.add(vm.transactions[i])
                                    }
                                }
                                vm.saveTransactions(unlinkedTxns)
                                vm.recomputeCash()
                            }
                        },
                        onBack = { vm.currentScreen = "main" },
                        onHelpClick = { vm.currentScreen = "recurring_expenses_help" },
                        autoCapitalize = vm.autoCapitalize
                    )
                    "budget_config" -> BudgetConfigScreen(
                        incomeSources = vm.activeIncomeSources,
                        currencySymbol = vm.currencySymbol,
                        dateFormatPattern = vm.dateFormatPattern,
                        onAddIncomeSource = { src ->
                            val added = src.copy(deviceId = vm.localDeviceId)
                            vm.incomeSources.add(added)
                            vm.saveIncomeSources(listOf(added))
                        },
                        onUpdateIncomeSource = { updated ->
                            val idx = vm.incomeSources.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                val old = vm.incomeSources[idx]
                                val amountChanged = updated.amount != old.amount
                                val hasLinkedTxns = amountChanged && vm.transactions.any {
                                    it.linkedIncomeSourceId == updated.id && !it.deleted
                                }
                                vm.incomeSources[idx] = updated.copy(
                                    deviceId = old.deviceId,
                                    deleted = old.deleted,
                                )
                                vm.saveIncomeSources(listOf(vm.incomeSources[idx]))
                                if (hasLinkedTxns) {
                                    vm.pendingISAmountUpdate = Pair(updated, old.amount)
                                }
                            }
                        },
                        onDeleteIncomeSource = { src ->
                            val idx = vm.incomeSources.indexOfFirst { it.id == src.id }
                            if (idx >= 0) {
                                vm.incomeSources[idx] = vm.incomeSources[idx].copy(deleted = true)
                                vm.saveIncomeSources(listOf(vm.incomeSources[idx]))
                                val unlinkedTxns = mutableListOf<Transaction>()
                                vm.transactions.forEachIndexed { i, txn ->
                                    if (txn.linkedIncomeSourceId == src.id) {
                                        vm.transactions[i] = txn.copy(linkedIncomeSourceId = null)
                                        unlinkedTxns.add(vm.transactions[i])
                                    }
                                }
                                vm.saveTransactions(unlinkedTxns)
                                vm.recomputeCash()
                            }
                        },
                        budgetPeriod = vm.budgetPeriod,
                        onBudgetPeriodChange = {
                            vm.budgetPeriod = it; vm.prefs.edit().putString("budgetPeriod", it.name).apply()
                            if (vm.isSyncConfigured) {
                                vm.sharedSettings = vm.sharedSettings.copy(budgetPeriod = it.name, lastChangedBy = vm.localDeviceId)
                                vm.saveSharedSettings()
                            }
                        },
                        resetHour = vm.resetHour,
                        onResetHourChange = {
                            vm.resetHour = it; vm.prefs.edit().putInt("resetHour", it).apply()
                            if (vm.isSyncConfigured) {
                                vm.sharedSettings = vm.sharedSettings.copy(resetHour = it, lastChangedBy = vm.localDeviceId)
                                vm.saveSharedSettings()
                            }
                        },
                        resetDayOfWeek = vm.resetDayOfWeek,
                        onResetDayOfWeekChange = {
                            vm.resetDayOfWeek = it; vm.prefs.edit().putInt("resetDayOfWeek", it).apply()
                            val newWeekStart = (it == 7)
                            if (vm.weekStartSunday != newWeekStart) {
                                vm.weekStartSunday = newWeekStart
                                vm.prefs.edit().putBoolean("weekStartSunday", newWeekStart).apply()
                            }
                            if (vm.isSyncConfigured) {
                                vm.sharedSettings = vm.sharedSettings.copy(
                                    resetDayOfWeek = it,
                                    weekStartSunday = newWeekStart,
                                    lastChangedBy = vm.localDeviceId
                                )
                                vm.saveSharedSettings()
                            }
                        },
                        resetDayOfMonth = vm.resetDayOfMonth,
                        onResetDayOfMonthChange = {
                            vm.resetDayOfMonth = it; vm.prefs.edit().putInt("resetDayOfMonth", it).apply()
                            if (vm.isSyncConfigured) {
                                vm.sharedSettings = vm.sharedSettings.copy(resetDayOfMonth = it, lastChangedBy = vm.localDeviceId)
                                vm.saveSharedSettings()
                            }
                        },
                        safeBudgetAmount = vm.safeBudgetAmount,
                        isManualBudgetEnabled = vm.isManualBudgetEnabled,
                        manualBudgetAmount = vm.manualBudgetAmount,
                        onManualBudgetToggle = { enabled ->
                            vm.isManualBudgetEnabled = enabled
                            vm.prefs.edit().putBoolean("isManualBudgetEnabled", enabled).apply()
                            if (enabled && vm.incomeMode == IncomeMode.ACTUAL_ADJUST) {
                                vm.incomeMode = IncomeMode.ACTUAL
                                vm.prefs.edit().putString("incomeMode", "ACTUAL").apply()
                                if (vm.isSyncConfigured) {
                                    vm.sharedSettings = vm.sharedSettings.copy(incomeMode = "ACTUAL", lastChangedBy = vm.localDeviceId)
                                }
                            }
                            if (vm.isSyncConfigured) {
                                vm.sharedSettings = vm.sharedSettings.copy(isManualBudgetEnabled = enabled, lastChangedBy = vm.localDeviceId)
                                vm.saveSharedSettings()
                            }
                        },
                        onManualBudgetAmountChange = { amount ->
                            vm.manualBudgetAmount = amount
                            vm.prefs.edit().putString("manualBudgetAmount", amount.toString()).apply()
                            if (vm.isSyncConfigured) {
                                vm.sharedSettings = vm.sharedSettings.copy(manualBudgetAmount = amount, lastChangedBy = vm.localDeviceId)
                                vm.saveSharedSettings()
                            }
                        },
                        budgetStartDate = vm.budgetStartDate?.format(java.time.format.DateTimeFormatter.ofPattern(vm.dateFormatPattern)),
                        onResetBudget = {
                            val tz = if (vm.isSyncConfigured && vm.sharedSettings.familyTimezone.isNotEmpty())
                                java.time.ZoneId.of(vm.sharedSettings.familyTimezone) else null
                            vm.budgetStartDate = BudgetCalculator.currentPeriodStart(vm.budgetPeriod, vm.resetDayOfWeek, vm.resetDayOfMonth, tz, vm.resetHour)
                            vm.lastRefreshDate = vm.budgetStartDate
                            val entryDate = vm.budgetStartDate!!.atStartOfDay()
                            val alreadyRecorded = vm.periodLedger.any {
                                it.periodStartDate.toLocalDate() == vm.budgetStartDate
                            }
                            val newLedgerEntry = if (!alreadyRecorded) {
                                val entry = PeriodLedgerEntry(
                                    periodStartDate = entryDate,
                                    appliedAmount = vm.budgetAmount,
                                    deviceId = vm.localDeviceId
                                )
                                vm.periodLedger.add(entry)
                                entry
                            } else null
                            vm.savePeriodLedger(listOfNotNull(newLedgerEntry))
                            if (vm.isSyncConfigured) {
                                vm.sharedSettings = vm.sharedSettings.copy(
                                    budgetStartDate = vm.budgetStartDate.toString(),
                                    lastChangedBy = vm.localDeviceId
                                )
                                vm.saveSharedSettings()
                            }
                            vm.recomputeCash()
                            vm.prefs.edit()
                                .putString("budgetStartDate", vm.budgetStartDate.toString())
                                .putString("lastRefreshDate", vm.budgetStartDate.toString())
                                .apply()
                        },
                        isSyncConfigured = vm.isSyncConfigured,
                        isAdmin = vm.isSyncAdmin,
                        incomeMode = vm.incomeMode.name,
                        onIncomeModeChange = { modeName ->
                            val mode = try { IncomeMode.valueOf(modeName) } catch (_: Exception) { IncomeMode.FIXED }
                            vm.incomeMode = mode
                            vm.prefs.edit().putString("incomeMode", modeName).apply()
                            if (vm.isSyncConfigured) {
                                vm.sharedSettings = vm.sharedSettings.copy(incomeMode = modeName, lastChangedBy = vm.localDeviceId)
                                vm.saveSharedSettings()
                            }
                            vm.recomputeCash()
                        },
                        onBack = { vm.currentScreen = "settings" },
                        onHelpClick = { vm.currentScreen = "budget_config_help" },
                        autoCapitalize = vm.autoCapitalize
                    )
                    "sync" -> SyncScreenBranch(vm, toastState)
                    "dashboard_help" -> DashboardHelpScreen(
                        onBack = { vm.currentScreen = "main" }
                    )
                    "settings_help" -> SettingsHelpScreen(
                        onBack = { vm.currentScreen = "settings" }
                    )
                    "transactions_help" -> TransactionsHelpScreen(
                        onBack = { vm.currentScreen = "transactions" },
                        scrollTarget = vm.transactionsHelpScrollTo,
                        onScrollTargetConsumed = { vm.transactionsHelpScrollTo = null }
                    )
                    "savings_goals_help" -> SavingsGoalsHelpScreen(
                        onBack = { vm.currentScreen = "savings_goals" }
                    )
                    "amortization_help" -> AmortizationHelpScreen(
                        onBack = { vm.currentScreen = "amortization" }
                    )
                    "recurring_expenses_help" -> RecurringExpensesHelpScreen(
                        onBack = { vm.currentScreen = "recurring_expenses" }
                    )
                    "budget_config_help" -> BudgetConfigHelpScreen(
                        onBack = { vm.currentScreen = "budget_config" }
                    )
                    "sync_help" -> SyncHelpScreen(
                        onBack = { vm.currentScreen = "sync" }
                    )
                    "simulation_graph_help" -> SimulationGraphHelpScreen(
                        onBack = { vm.currentScreen = "simulation_graph" }
                    )
                    "budget_calendar" -> BudgetCalendarScreen(
                        recurringExpenses = vm.activeRecurringExpenses,
                        incomeSources = vm.activeIncomeSources,
                        currencySymbol = vm.currencySymbol,
                        weekStartSunday = vm.weekStartSunday,
                        budgetPeriod = vm.budgetPeriod,
                        resetDayOfWeek = vm.resetDayOfWeek,
                        resetDayOfMonth = vm.resetDayOfMonth,
                        onBack = { vm.currentScreen = "main" },
                        onHelpClick = { vm.currentScreen = "budget_calendar_help" }
                    )
                    "budget_calendar_help" -> BudgetCalendarHelpScreen(
                        onBack = { vm.currentScreen = "budget_calendar" }
                    )
                }

                // Dashboard quick-add dialogs (rendered over any screen)
                DashboardDialogs(vm, vm.strings, toastState)

                } // Box(weight)
              } // Column
            // Quick Start Guide overlay
            if (vm.quickStartStep != null) {
                QuickStartOverlay(
                    step = vm.quickStartStep!!,
                    onNext = {
                        val nextStep = when (vm.quickStartStep) {
                            QuickStartStep.WELCOME -> QuickStartStep.BUDGET_PERIOD
                            QuickStartStep.BUDGET_PERIOD -> QuickStartStep.INCOME
                            QuickStartStep.INCOME -> QuickStartStep.EXPENSES
                            QuickStartStep.EXPENSES -> QuickStartStep.FIRST_TRANSACTION
                            QuickStartStep.FIRST_TRANSACTION -> QuickStartStep.DONE
                            QuickStartStep.DONE -> null
                            null -> null
                        }
                        vm.quickStartStep = nextStep
                        if (nextStep == null) {
                            vm.prefs.edit().putBoolean("quickStartCompleted", true).apply()
                        }
                    },
                    onSkip = {
                        vm.quickStartStep = null
                        vm.prefs.edit().putBoolean("quickStartCompleted", true).apply()
                    },
                    onNavigate = { screen -> vm.currentScreen = screen },
                    isEnglish = vm.appLanguage != "es",
                    isPaidUser = vm.isPaidUser,
                    onLanguageChange = { lang ->
                        vm.appLanguage = lang
                        vm.prefs.edit().putString("appLanguage", lang).apply()
                    }
                )
            }
              } // CompositionLocalProvider(LocalShareBlockingDialogRegistrar)
            } // SyncBudgetTheme
        }
    }

    @Composable
    private fun DashboardDialogs(
        vm: MainViewModel,
        strings: AppStrings,
        toastState: AppToastState,
    ) {
        if (vm.dashboardShowAddIncome) {
            TransactionDialog(
                title = strings.common.addNewIncomeTransaction,
                sourceLabel = strings.common.sourceLabel,
                categories = vm.activeCategories,
                existingIds = vm.existingIds,
                currencySymbol = vm.currencySymbol,
                dateFormatter = vm.dateFormatter,
                chartPalette = vm.chartPalette,
                recurringExpenses = vm.activeRecurringExpenses,
                amortizationEntries = vm.activeAmortizationEntries,
                incomeSources = vm.activeIncomeSources,
                savingsGoals = vm.activeSavingsGoals,
                pastSources = vm.activeTransactions.groupingBy { it.source }.eachCount().entries.sortedByDescending { it.value }.map { it.key },
                allTransactions = vm.activeTransactions,
                matchChars = vm.matchChars,
                budgetPeriod = vm.budgetPeriod,
                isPaidUser = vm.isPaidUser || vm.isSubscriber,
                isSubscriber = vm.isSubscriber,
                ocrState = vm.ocrState,
                onRunOcr = { rid, preSelected -> vm.runOcrOnSlot1(rid, preSelected) },
                onClearOcrState = { vm.clearOcrState() },
                onShowPreselectHelp = {
                    vm.transactionsHelpScrollTo = "preselected_cats"
                    vm.currentScreen = "transactions_help"
                },
                onDismiss = {
                    vm.dashboardShowAddIncome = false
                    vm.clearOcrState()
                },
                onSave = { txn ->
                    vm.runMatchingChain(txn)
                    vm.dashboardShowAddIncome = false
                    vm.clearOcrState()
                },
                onAddAmortization = { entry ->
                    val added = entry.copy(deviceId = vm.localDeviceId)
                    vm.amortizationEntries.add(added)
                    vm.saveAmortizationEntries(listOf(added))
                },
                onDeleteAmortization = { entry ->
                    val idx = vm.amortizationEntries.indexOfFirst { it.id == entry.id }
                    if (idx >= 0) {
                        vm.amortizationEntries[idx] = vm.amortizationEntries[idx].copy(deleted = true)
                        vm.saveAmortizationEntries(listOf(vm.amortizationEntries[idx]))
                    }
                },
                autoCapitalize = vm.autoCapitalize,
                onOpenStateChange = { open -> if (open) vm.transactionDialogOpenCount++ else vm.transactionDialogOpenCount-- },
                pendingSharedImageUris = vm.pendingSharedImageUris,
                onConsumeSharedImageUris = { vm.pendingSharedImageUris.clear() },
                onShareOverflow = { vm.shareOverflowToastPending = true }
            )
        }

        if (vm.dashboardShowAddExpense) {
            TransactionDialog(
                title = strings.common.addNewExpenseTransaction,
                sourceLabel = strings.common.merchantLabel,
                categories = vm.activeCategories,
                existingIds = vm.existingIds,
                currencySymbol = vm.currencySymbol,
                dateFormatter = vm.dateFormatter,
                isExpense = true,
                chartPalette = vm.chartPalette,
                recurringExpenses = vm.activeRecurringExpenses,
                amortizationEntries = vm.activeAmortizationEntries,
                incomeSources = vm.activeIncomeSources,
                savingsGoals = vm.activeSavingsGoals,
                pastSources = vm.activeTransactions.groupingBy { it.source }.eachCount().entries.sortedByDescending { it.value }.map { it.key },
                allTransactions = vm.activeTransactions,
                matchChars = vm.matchChars,
                budgetPeriod = vm.budgetPeriod,
                isPaidUser = vm.isPaidUser || vm.isSubscriber,
                isSubscriber = vm.isSubscriber,
                initialReceiptId1 = vm.pendingSharedReceiptId,
                ocrState = vm.ocrState,
                onRunOcr = { rid, preSelected -> vm.runOcrOnSlot1(rid, preSelected) },
                onClearOcrState = { vm.clearOcrState() },
                onShowPreselectHelp = {
                    vm.transactionsHelpScrollTo = "preselected_cats"
                    vm.currentScreen = "transactions_help"
                },
                onDismiss = {
                    vm.dashboardShowAddExpense = false
                    vm.pendingSharedReceiptId = null
                    vm.clearOcrState()
                },
                onSave = { txn ->
                    vm.runMatchingChain(txn)
                    vm.dashboardShowAddExpense = false
                    vm.pendingSharedReceiptId = null
                    vm.clearOcrState()
                },
                onAddAmortization = { entry ->
                    val added = entry.copy(deviceId = vm.localDeviceId)
                    vm.amortizationEntries.add(added)
                    vm.saveAmortizationEntries(listOf(added))
                },
                onDeleteAmortization = { entry ->
                    val idx = vm.amortizationEntries.indexOfFirst { it.id == entry.id }
                    if (idx >= 0) {
                        vm.amortizationEntries[idx] = vm.amortizationEntries[idx].copy(deleted = true)
                        vm.saveAmortizationEntries(listOf(vm.amortizationEntries[idx]))
                    }
                },
                autoCapitalize = vm.autoCapitalize,
                onOpenStateChange = { open -> if (open) vm.transactionDialogOpenCount++ else vm.transactionDialogOpenCount-- },
                pendingSharedImageUris = vm.pendingSharedImageUris,
                onConsumeSharedImageUris = { vm.pendingSharedImageUris.clear() },
                onShareOverflow = { vm.shareOverflowToastPending = true }
            )
        }

        // Dashboard duplicate resolution dialog
        if (vm.dashShowManualDuplicateDialog && vm.dashPendingManualSave != null && vm.dashManualDuplicateMatches.isNotEmpty()) {
            DuplicateResolutionDialog(
                existingTransactions = vm.dashManualDuplicateMatches,
                newTransaction = vm.dashPendingManualSave!!,
                currencySymbol = vm.currencySymbol,
                dateFormatter = vm.dateFormatter,
                categoryMap = vm.categoryMap,
                showIgnoreAll = false,
                onIgnore = {
                    val txn = vm.dashPendingManualSave!!
                    vm.dashPendingManualSave = null
                    vm.dashManualDuplicateMatches = emptyList()
                    vm.dashShowManualDuplicateDialog = false
                    vm.runLinkingChain(txn)
                },
                onKeepNew = { selectedExisting ->
                    val dupIdx = vm.transactions.indexOfFirst { it.id == selectedExisting.id }
                    if (dupIdx >= 0) {
                        vm.transactions[dupIdx] = vm.transactions[dupIdx].copy(deleted = true)
                    }
                    vm.saveTransactions(if (dupIdx >= 0) listOf(vm.transactions[dupIdx]) else emptyList())
                    val txn = vm.dashPendingManualSave!!
                    vm.dashPendingManualSave = null
                    vm.dashManualDuplicateMatches = emptyList()
                    vm.dashShowManualDuplicateDialog = false
                    vm.runLinkingChain(txn)
                },
                onKeepExisting = {
                    vm.dashPendingManualSave = null
                    vm.dashManualDuplicateMatches = emptyList()
                    vm.dashShowManualDuplicateDialog = false
                },
                onIgnoreAll = {}
            )
        }

        // Dashboard recurring expense match dialog
        if (vm.dashShowRecurringDialog && vm.dashPendingRecurringTxn != null && vm.dashPendingRecurringMatches.isNotEmpty()) {
            RecurringExpenseConfirmDialog(
                transaction = vm.dashPendingRecurringTxn!!,
                recurringExpenses = vm.dashPendingRecurringMatches,
                currencySymbol = vm.currencySymbol,
                dateFormatter = vm.dateFormatter,
                onConfirmRecurring = { selectedRE ->
                    val txn = vm.dashPendingRecurringTxn!!
                    val updatedTxn = txn.copy(
                        linkedRecurringExpenseId = selectedRE.id,
                        linkedRecurringExpenseAmount = selectedRE.amount
                    )
                    vm.addTransactionWithBudgetEffect(updatedTxn)
                    vm.dashPendingRecurringTxn = null
                    vm.dashPendingRecurringMatches = emptyList()
                    vm.dashShowRecurringDialog = false
                },
                onNotRecurring = {
                    val txn = vm.dashPendingRecurringTxn!!
                    vm.dashPendingRecurringTxn = null
                    vm.dashPendingRecurringMatches = emptyList()
                    vm.dashShowRecurringDialog = false
                    // Continue expense chain: check amortization only
                    val amortizationMatches = findAmortizationMatches(txn, vm.activeAmortizationEntries, vm.percentTolerance, vm.matchDollar, vm.matchChars)
                    if (amortizationMatches.isNotEmpty()) {
                        vm.dashPendingAmortizationTxn = txn
                        vm.dashPendingAmortizationMatches = amortizationMatches
                        vm.dashShowAmortizationDialog = true
                    } else {
                        vm.addTransactionWithBudgetEffect(txn)
                    }
                }
            )
        }

        // Dashboard amortization match dialog
        if (vm.dashShowAmortizationDialog && vm.dashPendingAmortizationTxn != null && vm.dashPendingAmortizationMatches.isNotEmpty()) {
            AmortizationConfirmDialog(
                transaction = vm.dashPendingAmortizationTxn!!,
                amortizationEntries = vm.dashPendingAmortizationMatches,
                currencySymbol = vm.currencySymbol,
                dateFormatter = vm.dateFormatter,
                onConfirmAmortization = { selectedEntry ->
                    val txn = vm.dashPendingAmortizationTxn!!
                    val updatedTxn = txn.copy(linkedAmortizationEntryId = selectedEntry.id)
                    vm.addTransactionWithBudgetEffect(updatedTxn)
                    vm.dashPendingAmortizationTxn = null
                    vm.dashPendingAmortizationMatches = emptyList()
                    vm.dashShowAmortizationDialog = false
                },
                onNotAmortized = {
                    vm.addTransactionWithBudgetEffect(vm.dashPendingAmortizationTxn!!)
                    vm.dashPendingAmortizationTxn = null
                    vm.dashPendingAmortizationMatches = emptyList()
                    vm.dashShowAmortizationDialog = false
                }
            )
        }

        // Dashboard budget income match dialog
        if (vm.dashShowBudgetIncomeDialog && vm.dashPendingBudgetIncomeTxn != null && vm.dashPendingBudgetIncomeMatches.isNotEmpty()) {
            BudgetIncomeConfirmDialog(
                transaction = vm.dashPendingBudgetIncomeTxn!!,
                incomeSources = vm.dashPendingBudgetIncomeMatches,
                currencySymbol = vm.currencySymbol,
                dateFormatter = vm.dateFormatter,
                onConfirmBudgetIncome = { selectedIS ->
                    val recurringIncomeCatId = vm.categories.find { it.tag == "recurring_income" }?.id
                    val baseTxn = vm.dashPendingBudgetIncomeTxn!!
                    val txn = baseTxn.copy(
                        isBudgetIncome = true,
                        linkedIncomeSourceId = selectedIS.id,
                        linkedIncomeSourceAmount = selectedIS.amount,
                        categoryAmounts = if (recurringIncomeCatId != null)
                            listOf(CategoryAmount(recurringIncomeCatId, baseTxn.amount))
                        else baseTxn.categoryAmounts,
                        isUserCategorized = true
                    )
                    // ACTUAL_ADJUST: update the income source BEFORE adding txn
                    if (vm.incomeMode == IncomeMode.ACTUAL_ADJUST) {
                        val srcId = selectedIS.id
                        val idx = vm.incomeSources.indexOfFirst { it.id == srcId }
                        if (idx >= 0 && vm.incomeSources[idx].amount != baseTxn.amount) {
                            vm.incomeSources[idx] = vm.incomeSources[idx].copy(amount = baseTxn.amount)
                            vm.saveIncomeSources(listOf(vm.incomeSources[idx]))
                        }
                    }
                    vm.addTransactionWithBudgetEffect(txn)
                    vm.dashPendingBudgetIncomeTxn = null
                    vm.dashPendingBudgetIncomeMatches = emptyList()
                    vm.dashShowBudgetIncomeDialog = false
                },
                onNotBudgetIncome = {
                    vm.addTransactionWithBudgetEffect(vm.dashPendingBudgetIncomeTxn!!)
                    vm.dashPendingBudgetIncomeTxn = null
                    vm.dashPendingBudgetIncomeMatches = emptyList()
                    vm.dashShowBudgetIncomeDialog = false
                }
            )
        }

        // Confirmation dialog: apply recurring expense amount change to past transactions?
        vm.pendingREAmountUpdate?.let { (updated, oldAmount) ->
            AdAwareAlertDialog(
                onDismissRequest = {
                    vm.pendingREAmountUpdate = null
                    vm.recomputeCash()
                },
                title = { Text(strings.common.applyToPastTitle) },
                text = { Text(strings.common.applyToPastBody) },
                style = DialogStyle.WARNING,
                confirmButton = {
                    DialogWarningButton(onClick = {
                        val changedTxns = mutableListOf<Transaction>()
                        vm.transactions.forEachIndexed { i, txn ->
                            if (txn.linkedRecurringExpenseId == updated.id && !txn.deleted) {
                                vm.transactions[i] = txn.copy(linkedRecurringExpenseAmount = updated.amount)
                                changedTxns.add(vm.transactions[i])
                            }
                        }
                        vm.saveTransactions(changedTxns)
                        vm.pendingREAmountUpdate = null
                        vm.recomputeCash()
                    }) { Text(strings.common.applyToPastConfirm) }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = {
                        vm.pendingREAmountUpdate = null
                        vm.recomputeCash()
                    }) { Text(strings.common.applyToPastDeny) }
                }
            )
        }

        // Confirmation dialog: apply income source amount change to past transactions?
        vm.pendingISAmountUpdate?.let { (updated, oldAmount) ->
            AdAwareAlertDialog(
                onDismissRequest = {
                    vm.pendingISAmountUpdate = null
                    vm.recomputeCash()
                },
                title = { Text(strings.common.applyToPastTitle) },
                text = { Text(strings.common.applyToPastBody) },
                style = DialogStyle.WARNING,
                confirmButton = {
                    DialogWarningButton(onClick = {
                        val changedTxns = mutableListOf<Transaction>()
                        vm.transactions.forEachIndexed { i, txn ->
                            if (txn.linkedIncomeSourceId == updated.id && !txn.deleted) {
                                vm.transactions[i] = txn.copy(linkedIncomeSourceAmount = updated.amount)
                                changedTxns.add(vm.transactions[i])
                            }
                        }
                        vm.saveTransactions(changedTxns)
                        vm.pendingISAmountUpdate = null
                        vm.recomputeCash()
                    }) { Text(strings.common.applyToPastConfirm) }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = {
                        vm.pendingISAmountUpdate = null
                        vm.recomputeCash()
                    }) { Text(strings.common.applyToPastDeny) }
                }
            )
        }

        // Backup password dialog
        if (vm.showBackupPasswordDialog) {
            var pwd by remember { mutableStateOf("") }
            var pwdConfirm by remember { mutableStateOf("") }
            var pwdError by remember { mutableStateOf<String?>(null) }
            val context = androidx.compose.ui.platform.LocalContext.current
            AdAwareAlertDialog(
                onDismissRequest = { vm.showBackupPasswordDialog = false },
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
                                BackupManager.savePassword(context, pwd.toCharArray())
                                vm.backupsEnabled = true
                                vm.backupPrefs.edit().putBoolean("backups_enabled", true).apply()
                                vm.showBackupPasswordDialog = false
                                val savedPwd = pwd.toCharArray()
                                vm.launchIO {
                                    val result = BackupManager.performBackup(context, savedPwd)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        if (result.isSuccess) toastState.show("Backup created")
                                        else toastState.show("Backup failed — please try again", durationMs = 7000L)
                                    }
                                }
                            }
                        }
                    }) { Text(strings.settings.enableBackups) }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = { vm.showBackupPasswordDialog = false }) {
                        Text(strings.common.cancel)
                    }
                }
            )
        }

        // Disable backup confirmation dialog
        if (vm.showDisableBackupDialog) {
            var confirmDelete by remember { mutableStateOf(false) }
            AdAwareAlertDialog(
                onDismissRequest = { vm.showDisableBackupDialog = false; confirmDelete = false },
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
                            BackupManager.deleteAllBackups()
                            vm.backupsEnabled = false
                            vm.backupPrefs.edit().putBoolean("backups_enabled", false).apply()
                            vm.showDisableBackupDialog = false; confirmDelete = false
                        }) { Text(strings.settings.confirmDeleteBtn) }
                    }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = {
                        if (confirmDelete) { confirmDelete = false } else {
                            vm.backupsEnabled = false
                            vm.backupPrefs.edit().putBoolean("backups_enabled", false).apply()
                            vm.showDisableBackupDialog = false
                        }
                    }) { Text(if (confirmDelete) strings.common.back else strings.settings.keepFilesBtn) }
                }
            )
        }

        // Save Photos dialog
        if (vm.showSavePhotosDialog) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AdAwareAlertDialog(
                onDismissRequest = { vm.showSavePhotosDialog = false },
                title = { Text("Save Photos") },
                style = DialogStyle.DEFAULT,
                text = {
                    Text("Photos are already backed up in encrypted backups if Automatic Backups is enabled below. This will save unencrypted copies of all receipt photos to Download/BudgeTrak/photos/ on your device if you need them for other purposes. Each save creates a new timestamped folder.")
                },
                confirmButton = {
                    DialogPrimaryButton(onClick = {
                        vm.showSavePhotosDialog = false
                        vm.launchIO {
                            // Each run writes to a new timestamped subfolder. Avoids
                            // Android scoped-storage overwrite failures when stale files
                            // from a previous session/version can't be deleted by the
                            // current app context (Kotlin copyTo throws "Tried to
                            // overwrite the destination, but failed to delete it.").
                            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.US).format(java.util.Date())
                            val photosDir = java.io.File(BackupManager.getBudgetrakDir(), "photos/$stamp")
                            try {
                                photosDir.mkdirs()
                                val activeIds = com.techadvantage.budgetrak.data.sync.ReceiptManager
                                    .collectAllReceiptIds(vm.transactions.filter { !it.deleted })
                                val receiptDir = java.io.File(context.filesDir, "receipts")
                                val allFiles = receiptDir.listFiles() ?: emptyArray()
                                // Only export files referenced by a live transaction — skip
                                // orphans from legacy bugs (e.g. pre-fix multi-photo imports)
                                // and files belonging to deleted transactions awaiting purge.
                                val files = allFiles.filter { it.nameWithoutExtension in activeIds }
                                var count = 0
                                val failed = mutableListOf<String>()
                                for (f in files) {
                                    try {
                                        // Stream-based copy — truncates target on open, no
                                        // pre-delete required. Falls back to writing a
                                        // uniquely-suffixed filename only if this still fails.
                                        val target = java.io.File(photosDir, f.name)
                                        f.inputStream().use { input ->
                                            java.io.FileOutputStream(target).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        count++
                                    } catch (inner: Exception) {
                                        android.util.Log.w("SavePhotos", "Skip ${f.name}: ${inner.message}")
                                        failed += f.name
                                    }
                                }
                                // Sweep orphans now that referenced files are safely exported.
                                // Use all transactions (incl. soft-deleted) so a deletion that
                                // hasn't propagated via sync doesn't take its receipts with it.
                                val keepIds = com.techadvantage.budgetrak.data.sync.ReceiptManager
                                    .collectAllReceiptIds(vm.transactions.toList())
                                com.techadvantage.budgetrak.data.sync.ReceiptManager
                                    .cleanOrphans(context, keepIds)
                                vm.receiptStorageRevision++ // trigger Settings cache-size refresh
                                val msg = if (failed.isEmpty()) {
                                    "Saved $count photos to Download/BudgeTrak/photos/$stamp/"
                                } else {
                                    "Saved $count photos (${failed.size} skipped) to Download/BudgeTrak/photos/$stamp/"
                                }
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { toastState.show(msg) }
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
                    DialogSecondaryButton(onClick = { vm.showSavePhotosDialog = false }) {
                        Text(strings.common.cancel)
                    }
                }
            )
        }

        // Restore backup dialog
        if (vm.showRestoreDialog) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val autoBackups = remember { BackupManager.listAvailableBackups() }
            var browsedBackups by remember { mutableStateOf<List<BackupManager.BackupEntry>>(emptyList()) }
            var selectedBackup by remember { mutableStateOf<BackupManager.BackupEntry?>(null) }
            var restorePassword by remember { mutableStateOf("") }
            var restoreError by remember { mutableStateOf<String?>(null) }
            var restoring by remember { mutableStateOf(false) }
            val restoreScrollState = rememberScrollState()

            val dirPickerLauncher = rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                    val docDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                    val allFiles = docDir?.listFiles() ?: emptyArray()
                    val systemFiles = allFiles.filter {
                        it.name?.startsWith("backup_") == true && it.name?.endsWith("_system.enc") == true
                    }
                    browsedBackups = systemFiles.mapNotNull { sysDoc ->
                        val name = sysDoc.name ?: return@mapNotNull null
                        val date = name.removePrefix("backup_").removeSuffix("_system.enc")
                        val photosDoc = allFiles.firstOrNull { it.name == "backup_${date}_photos.enc" }
                        try {
                            val sysCacheFile = java.io.File(context.cacheDir, name)
                            context.contentResolver.openInputStream(sysDoc.uri)?.use { input ->
                                sysCacheFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            var photosCacheFile: java.io.File? = null
                            if (photosDoc != null) {
                                photosCacheFile = java.io.File(context.cacheDir, photosDoc.name!!)
                                context.contentResolver.openInputStream(photosDoc.uri)?.use { input ->
                                    photosCacheFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                            BackupManager.BackupEntry(
                                date = date,
                                systemFile = sysCacheFile,
                                photosFile = photosCacheFile,
                                systemSizeBytes = sysDoc.length(),
                                photosSizeBytes = photosDoc?.length() ?: 0L
                            )
                        } catch (_: Exception) { null }
                    }.sortedByDescending { it.date }
                }
            }

            val availableBackups = if (browsedBackups.isNotEmpty()) browsedBackups else autoBackups

            AdAwareDialog(onDismissRequest = { if (!restoring) vm.showRestoreDialog = false }) {
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
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val initialUri = android.provider.DocumentsContract.buildDocumentUri(
                                            "com.android.externalstorage.documents",
                                            "primary:Download/BudgeTrak/backups"
                                        )
                                        dirPickerLauncher.launch(initialUri)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(strings.settings.browseForBackup) }
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
                                    DialogSecondaryButton(onClick = { vm.showRestoreDialog = false }) { Text(strings.common.cancel) }
                                }
                                if (selectedBackup != null && !restoring) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    DialogDangerButton(onClick = {
                                        if (restorePassword.isEmpty()) { restoreError = strings.settings.enterPasswordError; return@DialogDangerButton }
                                        restoring = true
                                        val backup = selectedBackup!!
                                        val pwd = restorePassword.toCharArray()
                                        vm.launchIO {
                                            val sysResult = BackupManager.restoreSystemBackup(context, backup.systemFile, pwd)
                                            if (sysResult.isSuccess && backup.photosFile != null) {
                                                val photosResult = BackupManager.restorePhotosBackup(context, backup.photosFile, pwd)
                                                val photosRestored = photosResult.getOrNull() ?: 0
                                                android.util.Log.d("BackupRestore", "Photos restored: $photosRestored")
                                            }
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                restoring = false
                                                if (sysResult.isSuccess) {
                                                    vm.showRestoreDialog = false
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

    // ── Extracted screen branches ──

    @Composable
    private fun SettingsScreenBranch(
        vm: MainViewModel,
        toastState: AppToastState,
    ) {
        val coroutineScope = rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        SettingsScreen(
            currencySymbol = vm.currencySymbol,
            appLanguage = vm.appLanguage,
            onLanguageChange = { lang ->
                vm.appLanguage = lang
                vm.prefs.edit().putString("appLanguage", lang).apply()
                val newStrings: AppStrings = if (lang == "es") SpanishStrings else EnglishStrings
                val changedCats = mutableListOf<Category>()
                vm.categories.forEachIndexed { idx, cat ->
                    if (cat.tag.isNotEmpty()) {
                        val allKnown = getAllKnownNamesForTag(cat.tag)
                        if (cat.name in allKnown) {
                            val newName = getDefaultCategoryName(cat.tag, newStrings)
                            if (newName != null && newName != cat.name) {
                                vm.categories[idx] = cat.copy(name = newName)
                                changedCats.add(vm.categories[idx])
                            }
                        }
                    }
                }
                if (changedCats.isNotEmpty()) vm.saveCategories(changedCats)
            },
            onNavigateToBudgetConfig = { vm.currentScreen = "budget_config" },
            onNavigateToSync = { vm.currentScreen = "sync" },
            onNavigateToQuickStart = {
                vm.quickStartStep = QuickStartStep.WELCOME
                vm.currentScreen = "main"
            },
            matchDays = vm.matchDays,
            onMatchDaysChange = {
                vm.matchDays = it; vm.prefs.edit().putInt("matchDays", it).apply()
                if (vm.isSyncConfigured) {
                    vm.sharedSettings = vm.sharedSettings.copy(matchDays = it, lastChangedBy = vm.localDeviceId)
                    vm.saveSharedSettings()
                }
            },
            matchPercent = vm.matchPercent,
            onMatchPercentChange = {
                vm.matchPercent = it; vm.prefs.edit().putString("matchPercent", it.toString()).apply()
                if (vm.isSyncConfigured) {
                    vm.sharedSettings = vm.sharedSettings.copy(matchPercent = it, lastChangedBy = vm.localDeviceId)
                    vm.saveSharedSettings()
                }
            },
            matchDollar = vm.matchDollar,
            onMatchDollarChange = {
                vm.matchDollar = it; vm.prefs.edit().putInt("matchDollar", it).apply()
                if (vm.isSyncConfigured) {
                    vm.sharedSettings = vm.sharedSettings.copy(matchDollar = it, lastChangedBy = vm.localDeviceId)
                    vm.saveSharedSettings()
                }
            },
            matchChars = vm.matchChars,
            onMatchCharsChange = {
                vm.matchChars = it; vm.prefs.edit().putInt("matchChars", it).apply()
                if (vm.isSyncConfigured) {
                    vm.sharedSettings = vm.sharedSettings.copy(matchChars = it, lastChangedBy = vm.localDeviceId)
                    vm.saveSharedSettings()
                }
            },
            chartPalette = vm.chartPalette,
            onChartPaletteChange = { vm.chartPalette = it; vm.prefs.edit().putString("chartPalette", it).apply() },
            budgetPeriod = vm.budgetPeriod.name,
            weekStartSunday = vm.weekStartSunday,
            onWeekStartChange = {
                vm.weekStartSunday = it; vm.prefs.edit().putBoolean("weekStartSunday", it).apply()
                if (vm.isSyncConfigured) {
                    vm.sharedSettings = vm.sharedSettings.copy(weekStartSunday = it, lastChangedBy = vm.localDeviceId)
                    vm.saveSharedSettings()
                }
            },
            onCurrencyChange = {
                vm.currencySymbol = it
                vm.prefs.edit().putString("currencySymbol", it).apply()
                if (vm.isSyncConfigured) {
                    vm.sharedSettings = vm.sharedSettings.copy(currency = it, lastChangedBy = vm.localDeviceId)
                    vm.saveSharedSettings()
                }
            },
            isSyncConfigured = vm.isSyncConfigured,
            isAdmin = vm.isSyncAdmin,
            showDecimals = vm.showDecimals,
            onDecimalsChange = {
                vm.showDecimals = it
                vm.prefs.edit().putBoolean("showDecimals", it).apply()
            },
            dateFormatPattern = vm.dateFormatPattern,
            onDateFormatChange = {
                vm.dateFormatPattern = it
                vm.prefs.edit().putString("dateFormatPattern", it).apply()
            },
            isPaidUser = vm.isPaidUser || vm.isSubscriber,
            onPaidUserChange = { newValue ->
                vm.isPaidUser = newValue
                vm.prefs.edit().putBoolean("isPaidUser", newValue).apply()
                com.techadvantage.budgetrak.widget.BudgetWidgetProvider.updateAllWidgets(context)
            },
            isSubscriber = vm.isSubscriber,
            onSubscriberChange = { newValue ->
                vm.isSubscriber = newValue
                vm.prefs.edit().putBoolean("isSubscriber", newValue).apply()
            },
            subscriptionExpiry = vm.subscriptionExpiry,
            onSubscriptionExpiryChange = { newValue ->
                vm.subscriptionExpiry = newValue
                vm.prefs.edit().putLong("subscriptionExpiry", newValue).apply()
            },
            showWidgetLogo = vm.showWidgetLogo,
            onWidgetLogoChange = { newValue ->
                vm.showWidgetLogo = newValue
                vm.prefs.edit().putBoolean("showWidgetLogo", newValue).apply()
                com.techadvantage.budgetrak.widget.BudgetWidgetProvider.updateAllWidgets(context)
            },
            autoCapitalize = vm.autoCapitalize,
            onAutoCapitalizeChange = { newValue ->
                vm.autoCapitalize = newValue
                vm.prefs.edit().putBoolean("autoCapitalize", newValue).apply()
            },
            aiCsvCategorizeEnabled = vm.aiCsvCategorizeEnabled,
            onAiCsvCategorizeChange = { newValue ->
                vm.aiCsvCategorizeEnabled = newValue
                vm.prefs.edit().putBoolean("aiCsvCategorizeEnabled", newValue).apply()
            },
            crashlyticsEnabled = vm.crashlyticsEnabled,
            onCrashlyticsEnabledChange = { newValue ->
                vm.crashlyticsEnabled = newValue
                vm.prefs.edit().putBoolean("crashlyticsEnabled", newValue).apply()
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                        .setCrashlyticsCollectionEnabled(newValue)
                } catch (_: Exception) {}
            },
            categories = vm.activeCategories,
            transactions = vm.activeTransactions,
            onAddCategory = { cat ->
                val added = cat.copy(deviceId = vm.localDeviceId)
                vm.categories.add(added)
                vm.saveCategories(listOf(added))
            },
            onUpdateCategory = { updated ->
                val idx = vm.categories.indexOfFirst { it.id == updated.id }
                if (idx >= 0) {
                    val old = vm.categories[idx]
                    vm.categories[idx] = updated.copy(
                        deviceId = old.deviceId,
                        deleted = old.deleted,
                    )
                    vm.saveCategories(listOf(vm.categories[idx]))
                }
            },
            onDeleteCategory = { cat ->
                val idx = vm.categories.indexOfFirst { it.id == cat.id }
                if (idx >= 0) {
                    vm.categories[idx] = vm.categories[idx].copy(deleted = true)
                    vm.saveCategories(listOf(vm.categories[idx]))
                }
            },
            onToggleCharted = { cat ->
                val idx = vm.categories.indexOfFirst { it.id == cat.id }
                if (idx >= 0) {
                    vm.categories[idx] = vm.categories[idx].copy(charted = !vm.categories[idx].charted)
                    vm.saveCategories(listOf(vm.categories[idx]))
                }
            },
            onToggleWidgetVisible = { cat ->
                val idx = vm.categories.indexOfFirst { it.id == cat.id }
                if (idx >= 0) {
                    vm.categories[idx] = vm.categories[idx].copy(widgetVisible = !vm.categories[idx].widgetVisible)
                    vm.saveCategories(listOf(vm.categories[idx]))
                }
            },
            onReassignCategory = { fromId, toId ->
                val changedTxns = mutableListOf<Transaction>()
                vm.transactions.forEachIndexed { index, txn ->
                    val updatedAmts = txn.categoryAmounts.map { ca ->
                        if (ca.categoryId == fromId) {
                            val existingTo = txn.categoryAmounts.find { it.categoryId == toId }
                            if (existingTo != null) ca.copy(categoryId = -1)
                            else ca.copy(categoryId = toId)
                        } else ca
                    }
                    val markedForMerge = updatedAmts.find { it.categoryId == -1 }
                    val finalAmounts = if (markedForMerge != null) {
                        val mergedAmount = (updatedAmts.find { it.categoryId == toId }?.amount ?: 0.0) + markedForMerge.amount
                        updatedAmts.filter { it.categoryId != -1 && it.categoryId != toId } +
                            CategoryAmount(toId, mergedAmount)
                    } else updatedAmts
                    if (finalAmounts != txn.categoryAmounts) {
                        vm.transactions[index] = txn.copy(categoryAmounts = finalAmounts)
                        changedTxns.add(vm.transactions[index])
                    }
                }
                vm.saveTransactions(changedTxns)
            },
            receiptPruneAgeDays = vm.sharedSettings.receiptPruneAgeDays,
            onReceiptPruneChange = { days ->
                vm.sharedSettings = vm.sharedSettings.copy(
                    receiptPruneAgeDays = days,
                    lastChangedBy = vm.localDeviceId
                )
                vm.saveSharedSettings()
            },
            receiptCacheSize = remember(vm.isPaidUser, vm.receiptStorageRevision) {
                com.techadvantage.budgetrak.data.sync.ReceiptManager.getTotalStorageBytes(context)
            },
            backupsEnabled = vm.backupsEnabled,
            onBackupsEnabledChange = { enabled ->
                if (enabled) {
                    vm.showBackupPasswordDialog = true
                } else {
                    vm.showDisableBackupDialog = true
                }
            },
            backupFrequencyWeeks = vm.backupFrequencyWeeks,
            onBackupFrequencyChange = { weeks ->
                vm.backupFrequencyWeeks = weeks
                vm.backupPrefs.edit().putInt("backup_frequency_weeks", weeks).apply()
            },
            backupRetention = vm.backupRetention,
            onBackupRetentionChange = { ret ->
                vm.backupRetention = ret
                vm.backupPrefs.edit().putInt("backup_retention", ret).apply()
            },
            lastBackupDate = vm.lastBackupDate,
            nextBackupDate = BackupManager.getNextBackupDate(context),
            onBackupNow = {
                val pwd = BackupManager.getPassword(context)
                if (pwd != null) {
                    vm.launchIO {
                        val result = BackupManager.performBackup(context, pwd)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            vm.lastBackupDate = vm.backupPrefs.getString("last_backup_date", null)
                            if (result.isSuccess) toastState.show("Backup created")
                            else toastState.show("Backup failed — please try again", durationMs = 7000L)
                        }
                    }
                }
            },
            onRestoreBackup = { vm.showRestoreDialog = true },
            onSavePhotos = { vm.showSavePhotosDialog = true },
            onDumpDebug = {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val devName = GroupManager.getDeviceName(context)
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

                        // 2b. Capture logcat to file (debug only)
                        if (BuildConfig.DEBUG) {
                            try {
                                val logcatProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "1000"))
                                val logcatText = logcatProcess.inputStream.bufferedReader().readText()
                                logcatProcess.waitFor()
                                DiagDumpBuilder.writeDiagToMediaStore(context, "logcat_${sanitized}.txt", logcatText)
                            } catch (e: Exception) {
                                android.util.Log.w("DumpDebug", "Logcat capture failed: ${e.message}")
                            }
                        }

                        val gId = vm.syncGroupId
                        if (gId != null) {
                            // 3. Upload own files
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                toastState.show("Uploading local debug files\u2026")
                            }
                            val extraDebug = StringBuilder(diagText)
                            for (extraName in listOf("clock_dump.txt", "fcm_debug.txt")) {
                                val f = java.io.File(supportDir, extraName)
                                if (f.exists()) {
                                    extraDebug.appendLine("\n=== $extraName ===")
                                    extraDebug.appendLine(f.readText())
                                }
                            }
                            try {
                                val lp = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "500"))
                                val lt = lp.inputStream.bufferedReader().readText()
                                lp.waitFor()
                                if (lt.isNotEmpty()) {
                                    extraDebug.appendLine("\n=== logcat (last 500) ===")
                                    extraDebug.appendLine(lt)
                                }
                            } catch (_: Exception) {}
                            val debugKey = GroupManager.getEncryptionKey(context)
                            FirestoreService.uploadDebugFiles(gId, vm.localDeviceId, devName, syncLogText, extraDebug.toString(), debugKey)

                            // 4. Request all devices upload fresh files
                            FirestoreService.requestDebugDump(gId)

                            // 4b. Send FCM push to wake up remote devices
                            try {
                                val fcmTokens = FirestoreService.getFcmTokens(gId, vm.localDeviceId)
                                val debugLog = java.io.File(supportDir, "fcm_debug.txt")
                                if (debugLog.exists() && debugLog.length() > 50_000) debugLog.writeText("")
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
                                java.io.File(supportDir, "fcm_debug.txt")
                                    .appendText("[${java.time.LocalDateTime.now()}] FCM exception: ${e.javaClass.simpleName}: ${e.message}\n")
                            }

                            // 5. Poll for remote files (wait up to 90s for other devices)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                toastState.show("Waiting for remote device\u2026")
                            }
                            val requestTime = System.currentTimeMillis()
                            var gotFreshRemote = false
                            for (attempt in 1..18) { // 18 x 5s = 90s max
                                kotlinx.coroutines.delay(5_000)
                                val remoteFiles = FirestoreService.downloadDebugFiles(gId, vm.localDeviceId, debugKey)
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
            onBack = { vm.currentScreen = "main" },
            onHelpClick = { vm.currentScreen = "settings_help" },
            activeTransactionCount = vm.activeTransactions.size,
            archiveThreshold = vm.archiveThreshold,
            onArchiveThresholdChange = { threshold ->
                vm.archiveThreshold = threshold
                vm.sharedSettings = vm.sharedSettings.copy(archiveThreshold = threshold)
                vm.saveSharedSettings()
            },
            lastArchiveDate = vm.lastArchiveDateDisplay,
            lastArchiveCount = vm.lastArchiveCountDisplay,
            totalArchivedCount = vm.totalArchivedCountDisplay
        )
    }

    @Composable
    private fun TransactionsScreenBranch(
        vm: MainViewModel,
        toastState: AppToastState,
    ) {
        val coroutineScope = rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        TransactionsScreen(
            transactions = vm.activeTransactions,
            currencySymbol = vm.currencySymbol,
            dateFormatPattern = vm.dateFormatPattern,
            categories = vm.activeCategories,
            isPaidUser = vm.isPaidUser || vm.isSubscriber,
            isSubscriber = vm.isSubscriber,
            aiCsvCategorizeEnabled = vm.aiCsvCategorizeEnabled,
            isNetworkAvailable = vm.isNetworkAvailable,
            recurringExpenses = vm.activeRecurringExpenses,
            amortizationEntries = vm.activeAmortizationEntries,
            incomeSources = vm.activeIncomeSources,
            savingsGoals = vm.activeSavingsGoals,
            matchDays = vm.matchDays,
            matchPercent = vm.matchPercent,
            matchDollar = vm.matchDollar,
            matchChars = vm.matchChars,
            chartPalette = vm.chartPalette,
            showAttribution = vm.showAttribution && vm.isSyncConfigured,
            deviceNameMap = run {
                val roster = try {
                    val obj = org.json.JSONObject(vm.sharedSettings.deviceRoster)
                    obj.keys().asSequence().associateWith { obj.getString(it) }
                } catch (_: Exception) { emptyMap() }
                roster + vm.syncDevices.associate { it.deviceId to it.deviceName.ifEmpty { it.deviceId.take(8) } }
            },
            localDeviceId = vm.localDeviceId,
            onAddTransaction = { txn ->
                vm.addTransactionWithBudgetEffect(txn)
            },
            onUpdateTransaction = { updated ->
                val index = vm.transactions.indexOfFirst { it.id == updated.id }
                if (index >= 0) {
                    val prev = vm.transactions[index]
                    vm.transactions[index] = updated.copy(
                        deviceId = prev.deviceId,
                        deleted = prev.deleted,
                        amortizationAppliedAmount = if (prev.linkedAmortizationEntryId != null && updated.linkedAmortizationEntryId == null) 0.0 else prev.amortizationAppliedAmount,
                        linkedRecurringExpenseAmount = if (prev.linkedRecurringExpenseId != null && updated.linkedRecurringExpenseId == null) 0.0 else prev.linkedRecurringExpenseAmount,
                        linkedIncomeSourceAmount = if (prev.linkedIncomeSourceId != null && updated.linkedIncomeSourceId == null) 0.0 else prev.linkedIncomeSourceAmount,
                        linkedSavingsGoalAmount = if (prev.linkedSavingsGoalId != null && updated.linkedSavingsGoalId == null) 0.0
                            else if (updated.linkedSavingsGoalId != null && prev.linkedSavingsGoalId == null) updated.linkedSavingsGoalAmount
                            else prev.linkedSavingsGoalAmount,
                    )
                    // Handle savings goal link/unlink effects
                    val wasLinkedToGoal = prev.linkedSavingsGoalId
                    val nowLinkedToGoal = updated.linkedSavingsGoalId
                    if (wasLinkedToGoal != null && nowLinkedToGoal == null) {
                        val gIdx = vm.savingsGoals.indexOfFirst { it.id == wasLinkedToGoal }
                        if (gIdx >= 0) {
                            val g = vm.savingsGoals[gIdx]
                            vm.savingsGoals[gIdx] = g.copy(totalSavedSoFar = g.totalSavedSoFar + prev.linkedSavingsGoalAmount)
                            vm.saveSavingsGoals(listOf(vm.savingsGoals[gIdx]))
                        }
                    } else if (wasLinkedToGoal == null && nowLinkedToGoal != null) {
                        val gIdx = vm.savingsGoals.indexOfFirst { it.id == nowLinkedToGoal }
                        if (gIdx >= 0) {
                            val g = vm.savingsGoals[gIdx]
                            vm.savingsGoals[gIdx] = g.copy(totalSavedSoFar = maxOf(0.0, g.totalSavedSoFar - updated.amount))
                            vm.saveSavingsGoals(listOf(vm.savingsGoals[gIdx]))
                        }
                    }
                    vm.saveTransactions(listOf(vm.transactions[index]))
                }
                vm.recomputeCash()
            },
            onDeleteTransaction = { txn ->
                val idx = vm.transactions.indexOfFirst { it.id == txn.id }
                if (idx >= 0) {
                    val t = vm.transactions[idx]
                    if (t.linkedSavingsGoalId != null && t.linkedSavingsGoalAmount > 0.0) {
                        val gIdx = vm.savingsGoals.indexOfFirst { it.id == t.linkedSavingsGoalId }
                        if (gIdx >= 0) {
                            val g = vm.savingsGoals[gIdx]
                            vm.savingsGoals[gIdx] = g.copy(totalSavedSoFar = g.totalSavedSoFar + t.linkedSavingsGoalAmount)
                            vm.saveSavingsGoals(listOf(vm.savingsGoals[gIdx]))
                        }
                    }
                    vm.transactions[idx] = t.copy(deleted = true)
                    vm.saveTransactions(listOf(vm.transactions[idx]))
                    val receiptIds = listOfNotNull(t.receiptId1, t.receiptId2, t.receiptId3, t.receiptId4, t.receiptId5)
                    if (receiptIds.isNotEmpty()) {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            for (rid in receiptIds) {
                                com.techadvantage.budgetrak.data.sync.ReceiptManager.deleteReceiptFull(context, rid)
                            }
                        }
                    }
                }
                vm.recomputeCash()
            },
            onDeleteTransactions = { ids ->
                val changedGoals = mutableListOf<SavingsGoal>()
                val changedTxns = mutableListOf<Transaction>()
                vm.transactions.forEachIndexed { index, txn ->
                    if (txn.id in ids && !txn.deleted) {
                        if (txn.linkedSavingsGoalId != null && txn.linkedSavingsGoalAmount > 0.0) {
                            val gIdx = vm.savingsGoals.indexOfFirst { it.id == txn.linkedSavingsGoalId }
                            if (gIdx >= 0) {
                                val g = vm.savingsGoals[gIdx]
                                vm.savingsGoals[gIdx] = g.copy(totalSavedSoFar = g.totalSavedSoFar + txn.linkedSavingsGoalAmount)
                                changedGoals.add(vm.savingsGoals[gIdx])
                            }
                        }
                        vm.transactions[index] = txn.copy(deleted = true)
                        changedTxns.add(vm.transactions[index])
                    }
                }
                if (changedGoals.isNotEmpty()) vm.saveSavingsGoals(changedGoals)
                vm.saveTransactions(changedTxns)
                vm.recomputeCash()
                val deletedReceiptIds = ids.flatMap { id ->
                    val txn = vm.transactions.find { it.id == id } ?: return@flatMap emptyList()
                    listOfNotNull(txn.receiptId1, txn.receiptId2, txn.receiptId3, txn.receiptId4, txn.receiptId5)
                }
                if (deletedReceiptIds.isNotEmpty()) {
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        for (rid in deletedReceiptIds) {
                            com.techadvantage.budgetrak.data.sync.ReceiptManager.deleteReceiptFull(context, rid)
                        }
                    }
                }
            },
            onSerializeFullBackup = {
                FullBackupSerializer.serialize(context)
            },
            onLoadFullBackup = { jsonContent ->
                FullBackupSerializer.restoreFullState(context, jsonContent)
                vm.reloadAllFromDisk()

                // Handle SYNC: dissolve old group, create new one.
                if (vm.isSyncConfigured) {
                    vm.transactions.forEachIndexed { i, t ->
                        vm.transactions[i] = t.copy(deviceId = "")
                    }
                    vm.saveTransactions(null)
                    vm.categories.forEachIndexed { i, c ->
                        vm.categories[i] = c.copy(deviceId = "")
                    }
                    vm.saveCategories(null)
                    vm.recurringExpenses.forEachIndexed { i, r ->
                        vm.recurringExpenses[i] = r.copy(deviceId = "")
                    }
                    vm.saveRecurringExpenses(null)
                    vm.incomeSources.forEachIndexed { i, s ->
                        vm.incomeSources[i] = s.copy(deviceId = "")
                    }
                    vm.saveIncomeSources(null)
                    vm.savingsGoals.forEachIndexed { i, g ->
                        vm.savingsGoals[i] = g.copy(deviceId = "")
                    }
                    vm.saveSavingsGoals(null)
                    vm.amortizationEntries.forEachIndexed { i, e ->
                        vm.amortizationEntries[i] = e.copy(deviceId = "")
                    }
                    vm.saveAmortizationEntries(null)

                    context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
                        .edit().putBoolean("pushClockFixApplied", true).apply()

                    val oldGroupId = vm.syncGroupId
                    if (oldGroupId != null) {
                        coroutineScope.launch {
                            try {
                                GroupManager.dissolveGroup(context, oldGroupId)
                            } catch (_: Exception) {}
                            val newGroup = GroupManager.createGroup(context)
                            // Register membership FIRST (self-registration allowed by rules)
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                                FirestoreService.registerMembership(newGroup.groupId, uid, vm.localDeviceId)
                            }
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            db.collection("groups").document(newGroup.groupId)
                                .set(mapOf("createdAt" to System.currentTimeMillis(), "lastActivity" to System.currentTimeMillis()))
                                .await()
                            FirestoreService.registerDevice(
                                newGroup.groupId, vm.localDeviceId,
                                GroupManager.getDeviceName(context), isAdmin = true
                            )
                            vm.isSyncConfigured = true
                            vm.syncGroupId = newGroup.groupId
                            vm.isSyncAdmin = true
                            vm.lastSyncActivity = 0L
                            vm.syncDevices = emptyList()
                            vm.generatedPairingCode = null
                            vm.syncEvictionMessage = null
                            vm.configureSyncGroup()
                            com.techadvantage.budgetrak.data.sync.BackgroundSyncWorker.schedule(context)
                        }
                    }
                }
            },
            isSyncConfigured = vm.isSyncConfigured,
            isSyncAdmin = vm.isSyncAdmin,
            budgetPeriod = vm.budgetPeriod,
            incomeMode = vm.incomeMode,
            onAdjustIncomeAmount = { srcId, newAmount ->
                val idx = vm.incomeSources.indexOfFirst { it.id == srcId }
                if (idx >= 0 && vm.incomeSources[idx].amount != newAmount) {
                    vm.incomeSources[idx] = vm.incomeSources[idx].copy(amount = newAmount)
                    vm.saveIncomeSources(listOf(vm.incomeSources[idx]))
                }
            },
            onAddAmortization = { entry ->
                val added = entry.copy(deviceId = vm.localDeviceId)
                vm.amortizationEntries.add(added)
                vm.saveAmortizationEntries(listOf(added))
            },
            onDeleteAmortization = { entry ->
                val idx = vm.amortizationEntries.indexOfFirst { it.id == entry.id }
                if (idx >= 0) {
                    vm.amortizationEntries[idx] = vm.amortizationEntries[idx].copy(deleted = true)
                    vm.saveAmortizationEntries(listOf(vm.amortizationEntries[idx]))
                }
            },
            onBack = { vm.currentScreen = "main" },
            onHelpClick = { vm.currentScreen = "transactions_help" },
            archivedTransactions = vm.loadedArchivedTransactions,
            onRequestArchived = { vm.loadArchivedTransactionsAsync() },
            archiveCutoffDate = vm.archiveCutoffDate,
            onUpdateArchivedTransaction = { vm.updateArchivedTransaction(it) },
            autoCapitalize = vm.autoCapitalize,
            ocrState = vm.ocrState,
            onRunOcr = { rid, preSelected -> vm.runOcrOnSlot1(rid, preSelected) },
            onClearOcrState = { vm.clearOcrState() },
            onShowPreselectHelp = {
                vm.transactionsHelpScrollTo = "preselected_cats"
                vm.currentScreen = "transactions_help"
            },
            onDialogOpenStateChange = { open -> if (open) vm.transactionDialogOpenCount++ else vm.transactionDialogOpenCount-- },
            pendingSharedImageUris = vm.pendingSharedImageUris,
            onConsumeSharedImageUris = { vm.pendingSharedImageUris.clear() },
            onShareOverflow = { vm.shareOverflowToastPending = true },
            onCsvImportInProgressChange = { vm.csvImportInProgress = it }
        )
    }

    @Composable
    private fun SyncScreenBranch(
        vm: MainViewModel,
        toastState: AppToastState,
    ) {
        val coroutineScope = rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        SyncScreen(
            isConfigured = vm.isSyncConfigured,
            isSubscriber = vm.isSubscriber,
            groupId = vm.syncGroupId,
            isAdmin = vm.isSyncAdmin,
            deviceName = GroupManager.getDeviceName(context),
            localDeviceId = vm.localDeviceId,
            devices = vm.syncDevices,
            syncStatus = vm.syncStatus,
            lastSyncTime = vm.lastSyncTimeDisplay,
            groupTimezone = vm.sharedSettings.familyTimezone,
            onTimezoneChange = { tz ->
                vm.sharedSettings = vm.sharedSettings.copy(familyTimezone = tz, lastChangedBy = vm.localDeviceId)
                vm.saveSharedSettings()
            },
            showAttribution = vm.showAttribution,
            onShowAttributionChange = { enabled ->
                vm.showAttribution = enabled
                vm.prefs.edit().putBoolean("showAttribution", enabled).apply()
            },
            orphanedDeviceIds = remember(vm.transactions.toList(), vm.syncDevices, vm.localDeviceId, vm.sharedSettings.deviceRoster) {
                val roster = try {
                    val obj = org.json.JSONObject(vm.sharedSettings.deviceRoster)
                    obj.keys().asSequence().toSet()
                } catch (_: Exception) { emptySet() }
                val knownIds = vm.syncDevices.map { it.deviceId }.toSet() + vm.localDeviceId + roster
                vm.transactions.toList()
                    .map { it.deviceId }
                    .filter { it.isNotEmpty() && it !in knownIds }
                    .toSet()
            },
            deviceRoster = remember(vm.sharedSettings.deviceRoster) {
                try {
                    val obj = org.json.JSONObject(vm.sharedSettings.deviceRoster)
                    obj.keys().asSequence().associateWith { obj.getString(it) }
                } catch (_: Exception) { emptyMap() }
            },
            onSaveDeviceRoster = { roster ->
                val json = org.json.JSONObject(roster).toString()
                vm.sharedSettings = vm.sharedSettings.copy(deviceRoster = json, lastChangedBy = vm.localDeviceId)
                vm.saveSharedSettings()
            },
            onPurgeStaleRoster = {
                val txnDeviceIds = vm.transactions.toList()
                    .map { it.deviceId }
                    .filter { it.isNotEmpty() }
                    .toSet()
                val currentIds = vm.syncDevices.map { it.deviceId }.toSet()
                val currentRoster = try {
                    val obj = org.json.JSONObject(vm.sharedSettings.deviceRoster)
                    obj.keys().asSequence().associateWith { obj.getString(it) }
                } catch (_: Exception) { emptyMap() }
                val pruned = currentRoster.filterKeys { it in txnDeviceIds || it in currentIds }
                if (pruned.size < currentRoster.size) {
                    vm.sharedSettings = vm.sharedSettings.copy(
                        deviceRoster = org.json.JSONObject(pruned).toString(),
                        lastChangedBy = vm.localDeviceId
                    )
                    vm.saveSharedSettings()
                }
            },
            pendingAdminClaim = vm.pendingAdminClaim,
            onClaimAdmin = {
                coroutineScope.launch {
                    try {
                        val gId = vm.syncGroupId ?: return@launch
                        val now = System.currentTimeMillis()
                        val claim = AdminClaim(
                            claimantDeviceId = vm.localDeviceId,
                            claimantName = GroupManager.getDeviceName(context),
                            claimedAt = now,
                            expiresAt = now + 24 * 60 * 60 * 1000L
                        )
                        FirestoreService.createAdminClaim(gId, claim)
                        vm.pendingAdminClaim = FirestoreService.getAdminClaim(gId)
                    } catch (_: Exception) {}
                }
            },
            onAcceptClaim = { vm.voteOnAdminClaim("accept") },
            onRejectClaim = { vm.voteOnAdminClaim("reject") },
            syncErrorMessage = vm.syncErrorMessage,
            syncProgressMessage = vm.syncProgressMessage,
            onCreateGroup = { nickname ->
                coroutineScope.launch {
                    try {
                        GroupManager.setDeviceName(context, nickname)
                        val info = GroupManager.createGroup(context)

                        vm.syncGroupId = info.groupId
                        vm.isSyncAdmin = true
                        vm.isSyncConfigured = true
                        vm.syncStatus = "syncing"

                        // Register membership FIRST (self-registration allowed by rules)
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                            FirestoreService.registerMembership(info.groupId, uid, vm.localDeviceId)
                        }
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        db.collection("groups").document(info.groupId)
                            .set(mapOf("createdAt" to System.currentTimeMillis(), "lastActivity" to System.currentTimeMillis()))
                            .await()
                        FirestoreService.registerDevice(
                            info.groupId, vm.localDeviceId, nickname, isAdmin = true
                        )
                        val newSettings = SharedSettings(
                            currency = vm.currencySymbol,
                            budgetPeriod = vm.budgetPeriod.name,
                            budgetStartDate = vm.budgetStartDate?.toString(),
                            isManualBudgetEnabled = vm.isManualBudgetEnabled,
                            manualBudgetAmount = vm.manualBudgetAmount,
                            weekStartSunday = vm.weekStartSunday,
                            resetDayOfWeek = vm.resetDayOfWeek,
                            resetDayOfMonth = vm.resetDayOfMonth,
                            resetHour = vm.resetHour,
                            familyTimezone = java.util.TimeZone.getDefault().id,
                            matchDays = vm.matchDays,
                            matchPercent = vm.matchPercent,
                            matchDollar = vm.matchDollar,
                            matchChars = vm.matchChars,
                            incomeMode = vm.incomeMode.name,
                            lastChangedBy = vm.localDeviceId,
                        )
                        vm.sharedSettings = newSettings
                        SharedSettingsRepository.save(context, newSettings)

                        // Stamp all existing data with deviceId
                        vm.transactions.forEachIndexed { i, t ->
                            if (t.deviceId.isEmpty()) vm.transactions[i] = t.copy(deviceId = vm.localDeviceId)
                        }
                        vm.saveTransactions(null)
                        vm.categories.forEachIndexed { i, c ->
                            if (c.deviceId.isEmpty()) vm.categories[i] = c.copy(deviceId = vm.localDeviceId)
                        }
                        vm.saveCategories(null)
                        vm.savingsGoals.forEachIndexed { i, g ->
                            if (g.deviceId.isEmpty()) vm.savingsGoals[i] = g.copy(deviceId = vm.localDeviceId)
                        }
                        vm.saveSavingsGoals(null)
                        vm.amortizationEntries.forEachIndexed { i, e ->
                            if (e.deviceId.isEmpty()) vm.amortizationEntries[i] = e.copy(deviceId = vm.localDeviceId)
                        }
                        vm.saveAmortizationEntries(null)
                        vm.recurringExpenses.forEachIndexed { i, r ->
                            if (r.deviceId.isEmpty()) vm.recurringExpenses[i] = r.copy(deviceId = vm.localDeviceId)
                        }
                        vm.saveRecurringExpenses(null)
                        vm.incomeSources.forEachIndexed { i, s ->
                            if (s.deviceId.isEmpty()) vm.incomeSources[i] = s.copy(deviceId = vm.localDeviceId)
                        }
                        vm.saveIncomeSources(null)

                        vm.syncEvictionMessage = null
                        vm.configureSyncGroup()
                        com.techadvantage.budgetrak.data.sync.BackgroundSyncWorker.schedule(context)
                    } catch (_: Exception) {
                        vm.syncStatus = "error"
                    }
                }
            },
            onJoinGroup = { code, nickname ->
                coroutineScope.launch {
                    try {
                        GroupManager.setDeviceName(context, nickname)
                        vm.syncProgressMessage = "Joining group..."
                        val success = GroupManager.joinGroup(context, code)
                        if (success) {
                            vm.transactions.clear()
                            vm.recurringExpenses.clear()
                            vm.incomeSources.clear()
                            vm.savingsGoals.clear()
                            vm.amortizationEntries.clear()
                            vm.categories.clear()
                            vm.periodLedger.clear()
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                TransactionRepository.save(context, emptyList())
                                RecurringExpenseRepository.save(context, emptyList())
                                IncomeSourceRepository.save(context, emptyList())
                                SavingsGoalRepository.save(context, emptyList())
                                AmortizationRepository.save(context, emptyList())
                                CategoryRepository.save(context, emptyList())
                                PeriodLedgerRepository.save(context, emptyList())
                            }

                            val gId = GroupManager.getGroupId(context)
                            val key = GroupManager.getEncryptionKey(context)

                            // Try to download join snapshot
                            if (gId != null && key != null) {
                                vm.syncProgressMessage = "Downloading group data..."
                                val encryptedSnapshot = ImageLedgerService.downloadJoinSnapshot(gId)
                                if (encryptedSnapshot != null) {
                                    vm.syncProgressMessage = "Installing data..."
                                    val decrypted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        CryptoHelper.decryptWithKey(encryptedSnapshot, key)
                                    }
                                    val json = org.json.JSONObject(String(decrypted))

                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        FullBackupSerializer.restoreFromSnapshot(context, json)
                                    }

                                    // Reload into ViewModel
                                    vm.transactions.addAll(TransactionRepository.load(context))
                                    vm.recurringExpenses.addAll(RecurringExpenseRepository.load(context))
                                    vm.incomeSources.addAll(IncomeSourceRepository.load(context))
                                    vm.savingsGoals.addAll(SavingsGoalRepository.load(context))
                                    vm.amortizationEntries.addAll(AmortizationRepository.load(context))
                                    vm.categories.addAll(CategoryRepository.load(context))
                                    vm.periodLedger.addAll(PeriodLedgerRepository.load(context))

                                    // Set sync cursors so filtered listeners start from snapshot
                                    val snapshotTs = json.optLong("snapshotTimestamp", 0L)
                                    if (snapshotTs > 0) {
                                        FirestoreDocSync.setCursorsFromTimestamp(context, snapshotTs)
                                    }
                                }
                            }

                            vm.syncPrefs.edit()
                                .putBoolean("migration_native_docs_done", true)
                                .putBoolean("migration_per_field_enc_done", true)
                                .apply()

                            vm.syncGroupId = GroupManager.getGroupId(context)
                            vm.isSyncAdmin = false
                            vm.isSyncConfigured = true
                            vm.syncProgressMessage = null
                            vm.syncEvictionMessage = null // clear any pending eviction popup
                            vm.configureSyncGroup()
                            com.techadvantage.budgetrak.data.sync.BackgroundSyncWorker.schedule(context)
                        } else {
                            vm.syncProgressMessage = null
                            vm.syncErrorMessage = "Invalid or expired pairing code"
                        }
                    } catch (e: Exception) {
                        vm.syncProgressMessage = null
                        vm.syncStatus = "error"
                        vm.syncErrorMessage = e.message
                    }
                }
            },
            onLeaveGroup = {
                coroutineScope.launch {
                    // Dispose listeners BEFORE leaveGroup to prevent onDisconnect re-writing RTDB
                    vm.disposeSyncListeners()
                    GroupManager.leaveGroup(context)
                    vm.resetSyncState()
                }
            },
            onDissolveGroup = {
                val gId = vm.syncGroupId
                if (gId != null) {
                    vm.syncStatus = "syncing"
                    coroutineScope.launch {
                        try {
                            android.util.Log.d("Sync", "Dissolving group $gId")
                            vm.disposeSyncListeners()
                            GroupManager.dissolveGroup(context, gId) { msg ->
                                vm.syncProgressMessage = msg
                            }
                            android.util.Log.d("Sync", "Group dissolved successfully")
                            vm.resetSyncState()
                            vm.syncEvictionMessage = null // admin initiated — no popup needed
                        } catch (e: Exception) {
                            android.util.Log.e("Sync", "Dissolve failed, falling back to local leave", e)
                            try {
                                GroupManager.leaveGroup(context, localOnly = true)
                            } catch (_: Exception) {}
                            vm.resetSyncState()
                            toastState.show("Group left locally (server unreachable)")
                        }
                    }
                }
            },
            onSyncNow = { vm.doSyncNow() },
            onGeneratePairingCode = {
                val gId = vm.syncGroupId
                val key = GroupManager.getEncryptionKey(context)
                if (gId != null && key != null) {
                    coroutineScope.launch {
                        try {
                            // Build join snapshot if stale or missing (7-day TTL)
                            val snapshotAge = FirestoreService.getJoinSnapshotAge(gId)
                            if (snapshotAge > 7 * 24 * 60 * 60 * 1000L) {
                                vm.syncProgressMessage = "Preparing sync data..."
                                val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    FullBackupSerializer.serialize(context, mode = "joinSnapshot")
                                }
                                val encrypted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    CryptoHelper.encryptWithKey(json.toByteArray(), key)
                                }
                                val uploaded = ImageLedgerService.uploadJoinSnapshot(gId, encrypted)
                                if (uploaded) {
                                    FirestoreService.setJoinSnapshotTimestamp(gId)
                                }
                                vm.syncProgressMessage = null
                            }
                            vm.generatedPairingCode = GroupManager.generatePairingCode(context, gId, key)
                        } catch (e: Exception) {
                            vm.syncProgressMessage = null
                            vm.syncErrorMessage = "Failed to prepare pairing: ${e.message}"
                        }
                    }
                }
            },
            generatedPairingCode = vm.generatedPairingCode,
            onDismissPairingCode = { vm.generatedPairingCode = null },
            onRenameDevice = { targetDeviceId, newName ->
                val gId = vm.syncGroupId ?: return@SyncScreen
                coroutineScope.launch {
                    try {
                        FirestoreService.updateDeviceName(gId, targetDeviceId, newName)
                        if (targetDeviceId == vm.localDeviceId) {
                            GroupManager.setDeviceName(context, newName)
                        }
                        vm.syncDevices = GroupManager.getDevices(gId)
                        val currentRoster = try {
                            val obj = org.json.JSONObject(vm.sharedSettings.deviceRoster)
                            obj.keys().asSequence().associateWith { obj.getString(it) }.toMutableMap()
                        } catch (_: Exception) { mutableMapOf() }
                        currentRoster[targetDeviceId] = newName
                        vm.sharedSettings = vm.sharedSettings.copy(
                            deviceRoster = org.json.JSONObject(currentRoster as Map<*, *>).toString(),
                            lastChangedBy = vm.localDeviceId
                        )
                        vm.saveSharedSettings()
                    } catch (_: Exception) {}
                }
            },
            onRemoveDevice = { targetDeviceId ->
                val gId = vm.syncGroupId ?: return@SyncScreen
                coroutineScope.launch {
                    try {
                        FirestoreService.removeDevice(gId, targetDeviceId)
                        com.techadvantage.budgetrak.data.sync.RealtimePresenceService.deletePresenceNode(gId, targetDeviceId)
                        vm.syncDevices = GroupManager.getDevices(gId)
                    } catch (_: Exception) {}
                }
            },
            onHelpClick = { vm.currentScreen = "sync_help" },
            onBack = {
                vm.generatedPairingCode = null
                vm.currentScreen = "settings"
            }
        )
    }
}

@Composable
private fun LoadingScreen(progress: Float) {
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (darkTheme) Color(0xFF2A3A2F) else Color(0xFFBDD5CC)
    val textColor = if (darkTheme) Color(0xFFE8D5A0) else Color(0xFF2E5C80)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(192.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "BudgeTrak",
                fontSize = 28.sp,
                color = textColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(200.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = textColor,
                trackColor = textColor.copy(alpha = 0.2f)
            )
        }
    }
}
