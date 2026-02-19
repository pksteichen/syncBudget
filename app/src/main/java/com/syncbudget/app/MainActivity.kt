package com.syncbudget.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.BudgetCalculator
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.CategoryRepository
import com.syncbudget.app.data.AmortizationRepository
import com.syncbudget.app.data.SavingsGoalRepository
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.IncomeSourceRepository
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.RecurringExpenseRepository
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionRepository
import com.syncbudget.app.data.TransactionType
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
import com.syncbudget.app.ui.theme.SyncBudgetTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val soundPlayer = remember { FlipSoundPlayer(this@MainActivity) }

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
            var budgetPeriod by remember {
                mutableStateOf(
                    try { BudgetPeriod.valueOf(prefs.getString("budgetPeriod", "MONTHLY") ?: "MONTHLY") }
                    catch (_: Exception) { BudgetPeriod.MONTHLY }
                )
            }
            var resetHour by remember { mutableIntStateOf(prefs.getInt("resetHour", 0)) }
            var resetDayOfWeek by remember { mutableIntStateOf(prefs.getInt("resetDayOfWeek", 7)) }
            var resetDayOfMonth by remember { mutableIntStateOf(prefs.getInt("resetDayOfMonth", 1)) }

            // Budget state
            var safeBudgetAmount by remember { mutableDoubleStateOf(prefs.getFloat("safeBudgetAmount", 0f).toDouble()) }
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
                if (loaded.none { it.name == "Other" }) {
                    val usedIds = loaded.map { it.id }.toSet()
                    var id: Int
                    do { id = (0..65535).random() } while (id in usedIds)
                    val otherCat = Category(id, "Other", "Balloon")
                    loaded.add(otherCat)
                    CategoryRepository.save(context, loaded)
                }
                if (loaded.none { it.name == "Recurring" }) {
                    val usedIds = loaded.map { it.id }.toSet()
                    var id: Int
                    do { id = (0..65535).random() } while (id in usedIds)
                    loaded.add(Category(id, "Recurring", "Sync"))
                    CategoryRepository.save(context, loaded)
                }
                if (loaded.none { it.name == "Amortization" }) {
                    val usedIds = loaded.map { it.id }.toSet()
                    var id: Int
                    do { id = (0..65535).random() } while (id in usedIds)
                    loaded.add(Category(id, "Amortization", "Schedule"))
                    CategoryRepository.save(context, loaded)
                }
                if (loaded.none { it.name == "Recurring Income" }) {
                    val usedIds = loaded.map { it.id }.toSet()
                    var id: Int
                    do { id = (0..65535).random() } while (id in usedIds)
                    loaded.add(Category(id, "Recurring Income", "Payments"))
                    CategoryRepository.save(context, loaded)
                }
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

            fun recalculateBudget() {
                safeBudgetAmount = BudgetCalculator.calculateSafeBudgetAmount(
                    incomeSources, recurringExpenses, budgetPeriod
                )
                prefs.edit().putFloat("safeBudgetAmount", safeBudgetAmount.toFloat()).apply()
            }

            // Derived budgetAmount
            val budgetAmount = if (isManualBudgetEnabled) {
                manualBudgetAmount
            } else {
                val amortDeductions = BudgetCalculator.activeAmortizationDeductions(amortizationEntries, budgetPeriod)
                val savingsDeductions = BudgetCalculator.activeSavingsGoalDeductions(savingsGoals, budgetPeriod)
                maxOf(0.0, safeBudgetAmount - amortDeductions - savingsDeductions)
            }

            // Percent tolerance for matching
            val percentTolerance = matchPercent / 100f

            // Check if an expense transaction is already accounted for in the budget
            // (recurring expenses and amortization are built into the safe budget amount)
            fun isBudgetAccountedExpense(txn: Transaction): Boolean {
                if (txn.type != TransactionType.EXPENSE) return false
                val recurringCatId = categories.find { it.name == "Recurring" }?.id
                val amortCatId = categories.find { it.name == "Amortization" }?.id
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
                val dup = findDuplicate(txn, transactions, percentTolerance, matchDollar, matchDays, matchChars)
                if (dup != null) {
                    dashPendingManualSave = txn
                    dashManualDuplicateMatch = dup
                    dashShowManualDuplicateDialog = true
                } else {
                    val recurringMatch = findRecurringExpenseMatch(txn, recurringExpenses, percentTolerance, matchDollar, matchChars)
                    if (recurringMatch != null) {
                        dashPendingRecurringTxn = txn
                        dashPendingRecurringMatch = recurringMatch
                        dashShowRecurringDialog = true
                    } else {
                        val amortizationMatch = findAmortizationMatch(txn, amortizationEntries, percentTolerance, matchDollar, matchChars)
                        if (amortizationMatch != null) {
                            dashPendingAmortizationTxn = txn
                            dashPendingAmortizationMatch = amortizationMatch
                            dashShowAmortizationDialog = true
                        } else {
                            val budgetMatch = findBudgetIncomeMatch(txn, incomeSources, matchChars)
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
                        // Compute budget amount at time of refresh (using current state)
                        val currentBudgetAmount = if (isManualBudgetEnabled) {
                            manualBudgetAmount
                        } else {
                            val amortDed = BudgetCalculator.activeAmortizationDeductions(amortizationEntries, budgetPeriod)
                            val savDed = BudgetCalculator.activeSavingsGoalDeductions(savingsGoals, budgetPeriod)
                            maxOf(0.0, safeBudgetAmount - amortDed - savDed)
                        }
                        availableCash += currentBudgetAmount * missedPeriods
                        lastRefreshDate = today

                        // Update savings goals totalSavedSoFar for non-paused, non-complete items
                        if (!isManualBudgetEnabled) {
                            for (period in 0 until missedPeriods) {
                                savingsGoals.forEachIndexed { idx, goal ->
                                    if (!goal.isPaused) {
                                        val remaining = goal.targetAmount - goal.totalSavedSoFar
                                        if (remaining > 0) {
                                            if (goal.targetDate != null) {
                                                // Target-date type: recalculate dynamically
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
                                                // Fixed contribution type: add contributionPerPeriod capped at target
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

            SyncBudgetTheme {
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
                            BudgetPeriod.DAILY -> "day"
                            BudgetPeriod.WEEKLY -> "week"
                            BudgetPeriod.MONTHLY -> "month"
                        },
                        savingsGoals = savingsGoals,
                        transactions = transactions,
                        categories = categories,
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
                        onSupercharge = { allocations ->
                            var totalDeducted = 0.0
                            for ((goalId, amount) in allocations) {
                                val idx = savingsGoals.indexOfFirst { it.id == goalId }
                                if (idx >= 0) {
                                    val goal = savingsGoals[idx]
                                    val remaining = goal.targetAmount - goal.totalSavedSoFar
                                    val capped = minOf(amount, remaining)
                                    if (capped > 0) {
                                        savingsGoals[idx] = goal.copy(
                                            totalSavedSoFar = goal.totalSavedSoFar + capped
                                        )
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
                        onNavigateToBudgetConfig = { currentScreen = "budget_config" },
                        matchDays = matchDays,
                        onMatchDaysChange = { matchDays = it; prefs.edit().putInt("matchDays", it).apply() },
                        matchPercent = matchPercent,
                        onMatchPercentChange = { matchPercent = it; prefs.edit().putFloat("matchPercent", it).apply() },
                        matchDollar = matchDollar,
                        onMatchDollarChange = { matchDollar = it; prefs.edit().putInt("matchDollar", it).apply() },
                        matchChars = matchChars,
                        onMatchCharsChange = { matchChars = it; prefs.edit().putInt("matchChars", it).apply() },
                        chartPalette = chartPalette,
                        onChartPaletteChange = { chartPalette = it; prefs.edit().putString("chartPalette", it).apply() },
                        weekStartSunday = weekStartSunday,
                        onWeekStartChange = { weekStartSunday = it; prefs.edit().putBoolean("weekStartSunday", it).apply() },
                        onCurrencyChange = {
                            currencySymbol = it
                            prefs.edit().putString("currencySymbol", it).apply()
                        },
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
                        categories = categories,
                        transactions = transactions,
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
                            categories.removeAll { it.id == cat.id }
                            saveCategories()
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
                        transactions = transactions,
                        currencySymbol = currencySymbol,
                        dateFormatPattern = dateFormatPattern,
                        categories = categories,
                        isPaidUser = isPaidUser,
                        recurringExpenses = recurringExpenses,
                        amortizationEntries = amortizationEntries,
                        incomeSources = incomeSources,
                        matchDays = matchDays,
                        matchPercent = matchPercent,
                        matchDollar = matchDollar,
                        matchChars = matchChars,
                        chartPalette = chartPalette,
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
                            transactions.removeAll { it.id == txn.id }
                            saveTransactions()
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
                            val deletedTxns = transactions.filter { it.id in ids }
                            transactions.removeAll { it.id in ids }
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
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "transactions_help" }
                    )
                    "future_expenditures" -> FutureExpendituresScreen(
                        savingsGoals = savingsGoals,
                        currencySymbol = currencySymbol,
                        budgetPeriod = budgetPeriod,
                        isManualBudgetEnabled = isManualBudgetEnabled,
                        onAddGoal = { savingsGoals.add(it); saveSavingsGoals() },
                        onUpdateGoal = { updated ->
                            val idx = savingsGoals.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) { savingsGoals[idx] = updated; saveSavingsGoals() }
                        },
                        onDeleteGoal = { savingsGoals.removeAll { s -> s.id == it.id }; saveSavingsGoals() },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "future_expenditures_help" }
                    )
                    "amortization" -> AmortizationScreen(
                        amortizationEntries = amortizationEntries,
                        currencySymbol = currencySymbol,
                        budgetPeriod = budgetPeriod,
                        isManualBudgetEnabled = isManualBudgetEnabled,
                        onAddEntry = { amortizationEntries.add(it); saveAmortizationEntries() },
                        onUpdateEntry = { updated ->
                            val idx = amortizationEntries.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) { amortizationEntries[idx] = updated; saveAmortizationEntries() }
                        },
                        onDeleteEntry = { amortizationEntries.removeAll { s -> s.id == it.id }; saveAmortizationEntries() },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "amortization_help" }
                    )
                    "recurring_expenses" -> RecurringExpensesScreen(
                        recurringExpenses = recurringExpenses,
                        currencySymbol = currencySymbol,
                        onAddRecurringExpense = { recurringExpenses.add(it); saveRecurringExpenses(); recalculateBudget() },
                        onUpdateRecurringExpense = { updated ->
                            val idx = recurringExpenses.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) { recurringExpenses[idx] = updated; saveRecurringExpenses(); recalculateBudget() }
                        },
                        onDeleteRecurringExpense = { recurringExpenses.removeAll { s -> s.id == it.id }; saveRecurringExpenses(); recalculateBudget() },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "recurring_expenses_help" }
                    )
                    "budget_config" -> BudgetConfigScreen(
                        incomeSources = incomeSources,
                        currencySymbol = currencySymbol,
                        onAddIncomeSource = { incomeSources.add(it); saveIncomeSources(); recalculateBudget() },
                        onUpdateIncomeSource = { updated ->
                            val idx = incomeSources.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                incomeSources[idx] = updated
                                saveIncomeSources()
                                recalculateBudget()
                            }
                        },
                        onDeleteIncomeSource = { incomeSources.removeAll { s -> s.id == it.id }; saveIncomeSources(); recalculateBudget() },
                        budgetPeriod = budgetPeriod,
                        onBudgetPeriodChange = { budgetPeriod = it; prefs.edit().putString("budgetPeriod", it.name).apply() },
                        resetHour = resetHour,
                        onResetHourChange = { resetHour = it; prefs.edit().putInt("resetHour", it).apply() },
                        resetDayOfWeek = resetDayOfWeek,
                        onResetDayOfWeekChange = { resetDayOfWeek = it; prefs.edit().putInt("resetDayOfWeek", it).apply() },
                        resetDayOfMonth = resetDayOfMonth,
                        onResetDayOfMonthChange = { resetDayOfMonth = it; prefs.edit().putInt("resetDayOfMonth", it).apply() },
                        safeBudgetAmount = safeBudgetAmount,
                        isManualBudgetEnabled = isManualBudgetEnabled,
                        manualBudgetAmount = manualBudgetAmount,
                        onManualBudgetToggle = { enabled ->
                            isManualBudgetEnabled = enabled
                            prefs.edit().putBoolean("isManualBudgetEnabled", enabled).apply()
                        },
                        onManualBudgetAmountChange = { amount ->
                            manualBudgetAmount = amount
                            prefs.edit().putFloat("manualBudgetAmount", amount.toFloat()).apply()
                        },
                        budgetStartDate = budgetStartDate?.toString(),
                        onResetBudget = {
                            safeBudgetAmount = BudgetCalculator.calculateSafeBudgetAmount(
                                incomeSources, recurringExpenses, budgetPeriod
                            )
                            budgetStartDate = BudgetCalculator.currentPeriodStart(budgetPeriod, resetDayOfWeek, resetDayOfMonth)
                            lastRefreshDate = LocalDate.now()
                            val newBudgetAmount = if (isManualBudgetEnabled) {
                                manualBudgetAmount
                            } else {
                                val amortDed = BudgetCalculator.activeAmortizationDeductions(amortizationEntries, budgetPeriod)
                                val savDed = BudgetCalculator.activeSavingsGoalDeductions(savingsGoals, budgetPeriod)
                                maxOf(0.0, safeBudgetAmount - amortDed - savDed)
                            }
                            availableCash = newBudgetAmount
                            prefs.edit()
                                .putFloat("safeBudgetAmount", safeBudgetAmount.toFloat())
                                .putString("budgetStartDate", budgetStartDate.toString())
                                .putString("lastRefreshDate", lastRefreshDate.toString())
                                .putFloat("availableCash", availableCash.toFloat())
                                .apply()
                        },
                        onRecalculate = {
                            safeBudgetAmount = BudgetCalculator.calculateSafeBudgetAmount(
                                incomeSources, recurringExpenses, budgetPeriod
                            )
                            prefs.edit().putFloat("safeBudgetAmount", safeBudgetAmount.toFloat()).apply()

                            // Recompute budgetAmount with new safeBudgetAmount
                            val newBudgetAmount = if (isManualBudgetEnabled) {
                                manualBudgetAmount
                            } else {
                                val amortDed = BudgetCalculator.activeAmortizationDeductions(amortizationEntries, budgetPeriod)
                                val savDed = BudgetCalculator.activeSavingsGoalDeductions(savingsGoals, budgetPeriod)
                                maxOf(0.0, safeBudgetAmount - amortDed - savDed)
                            }

                            if (budgetStartDate == null || availableCash == 0.0) {
                                // First-time setup or reinitialize from buggy state
                                budgetStartDate = BudgetCalculator.currentPeriodStart(budgetPeriod, resetDayOfWeek, resetDayOfMonth)
                                lastRefreshDate = LocalDate.now()
                                availableCash = newBudgetAmount
                                prefs.edit()
                                    .putString("budgetStartDate", budgetStartDate.toString())
                                    .putString("lastRefreshDate", lastRefreshDate.toString())
                                    .putFloat("availableCash", availableCash.toFloat())
                                    .apply()
                            }
                        },
                        onBack = { currentScreen = "settings" },
                        onHelpClick = { currentScreen = "budget_config_help" }
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
                }

                // Dashboard quick-add dialogs (rendered over any screen)
                if (dashboardShowAddIncome) {
                    TransactionDialog(
                        title = "Add New Income Transaction",
                        sourceLabel = "Source",
                        categories = categories,
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
                        title = "Add New Expense Transaction",
                        sourceLabel = "Merchant",
                        categories = categories,
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
                            transactions.removeAll { it.id == dup.id }
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
                    val recurringCategoryId = categories.find { it.name == "Recurring" }?.id
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
                    val amortizationCategoryId = categories.find { it.name == "Amortization" }?.id
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
                            val recurringIncomeCatId = categories.find { it.name == "Recurring Income" }?.id
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
