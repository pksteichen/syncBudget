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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.syncbudget.app.data.BudgetCalculator
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.CategoryRepository
import com.syncbudget.app.data.AmortizationRepository
import com.syncbudget.app.data.FutureExpenditureRepository
import com.syncbudget.app.data.IncomeSourceRepository
import com.syncbudget.app.data.RecurringExpenseRepository
import com.syncbudget.app.data.TransactionRepository
import com.syncbudget.app.data.TransactionType
import com.syncbudget.app.sound.FlipSoundPlayer
import com.syncbudget.app.ui.screens.AmortizationHelpScreen
import com.syncbudget.app.ui.screens.AmortizationScreen
import com.syncbudget.app.ui.screens.BudgetConfigHelpScreen
import com.syncbudget.app.ui.screens.BudgetConfigScreen
import com.syncbudget.app.ui.screens.DashboardHelpScreen
import com.syncbudget.app.ui.screens.FutureExpendituresHelpScreen
import com.syncbudget.app.ui.screens.FutureExpendituresScreen
import com.syncbudget.app.ui.screens.MainScreen
import com.syncbudget.app.ui.screens.RecurringExpensesHelpScreen
import com.syncbudget.app.ui.screens.RecurringExpensesScreen
import com.syncbudget.app.ui.screens.SavingsHelpScreen
import com.syncbudget.app.ui.screens.SavingsScreen
import com.syncbudget.app.ui.screens.SettingsHelpScreen
import com.syncbudget.app.ui.screens.SettingsScreen
import com.syncbudget.app.ui.screens.TransactionsHelpScreen
import com.syncbudget.app.ui.screens.TransactionsScreen
import com.syncbudget.app.ui.theme.SyncBudgetTheme
import java.time.LocalDate
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

            val context = this@MainActivity
            val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            var currencySymbol by remember { mutableStateOf(prefs.getString("currencySymbol", "$") ?: "$") }
            var digitCount by remember { mutableIntStateOf(prefs.getInt("digitCount", 3)) }
            var showDecimals by remember { mutableStateOf(prefs.getBoolean("showDecimals", false)) }
            var dateFormatPattern by remember { mutableStateOf(prefs.getString("dateFormatPattern", "yyyy-MM-dd") ?: "yyyy-MM-dd") }
            var isPaidUser by remember { mutableStateOf(prefs.getBoolean("isPaidUser", false)) }
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

            val futureExpenditures = remember {
                mutableStateListOf(*FutureExpenditureRepository.load(context).toTypedArray())
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

            fun saveFutureExpenditures() {
                FutureExpenditureRepository.save(context, futureExpenditures.toList())
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

            // Derived budgetAmount
            val budgetAmount = if (isManualBudgetEnabled) {
                manualBudgetAmount
            } else {
                val amortDeductions = BudgetCalculator.activeAmortizationDeductions(amortizationEntries, budgetPeriod)
                val fleDeductions = BudgetCalculator.activeFLEDeductions(futureExpenditures, budgetPeriod)
                maxOf(0.0, safeBudgetAmount - amortDeductions - fleDeductions)
            }

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
                            val fleDed = BudgetCalculator.activeFLEDeductions(futureExpenditures, budgetPeriod)
                            maxOf(0.0, safeBudgetAmount - amortDed - fleDed)
                        }
                        availableCash += currentBudgetAmount * missedPeriods
                        lastRefreshDate = today

                        // Update FLE totalSavedSoFar for non-paused, non-complete items
                        if (!isManualBudgetEnabled) {
                            for (period in 0 until missedPeriods) {
                                futureExpenditures.forEachIndexed { idx, exp ->
                                    if (!exp.isPaused) {
                                        val remaining = exp.amount - exp.totalSavedSoFar
                                        if (remaining > 0 && LocalDate.now().isBefore(exp.targetDate)) {
                                            val periods = when (budgetPeriod) {
                                                BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(LocalDate.now(), exp.targetDate)
                                                BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(LocalDate.now(), exp.targetDate)
                                                BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(LocalDate.now(), exp.targetDate)
                                            }
                                            if (periods > 0) {
                                                val deduction = minOf(remaining / periods.toDouble(), remaining)
                                                futureExpenditures[idx] = exp.copy(
                                                    totalSavedSoFar = exp.totalSavedSoFar + deduction
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            saveFutureExpenditures()
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
                            "savings_help" -> "savings"
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
                        onSettingsClick = { currentScreen = "settings" },
                        onNavigate = { currentScreen = it }
                    )
                    "settings" -> SettingsScreen(
                        currencySymbol = currencySymbol,
                        onNavigateToBudgetConfig = { currentScreen = "budget_config" },
                        onCurrencyChange = {
                            currencySymbol = it
                            prefs.edit().putString("currencySymbol", it).apply()
                        },
                        digitCount = digitCount,
                        onDigitCountChange = {
                            digitCount = it
                            prefs.edit().putInt("digitCount", it).apply()
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
                        onAddTransaction = { txn ->
                            transactions.add(txn)
                            saveTransactions()
                            if (budgetStartDate != null && !txn.date.isBefore(budgetStartDate)) {
                                if (txn.type == TransactionType.EXPENSE) {
                                    availableCash -= txn.amount
                                } else if (txn.type == TransactionType.INCOME && !txn.isBudgetIncome) {
                                    availableCash += txn.amount
                                }
                                persistAvailableCash()
                            }
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
                                    if (old.type == TransactionType.EXPENSE) availableCash += old.amount
                                    else if (old.type == TransactionType.INCOME && !old.isBudgetIncome) availableCash -= old.amount
                                }
                                // Apply new effect
                                if (!updated.date.isBefore(budgetStartDate)) {
                                    if (updated.type == TransactionType.EXPENSE) availableCash -= updated.amount
                                    else if (updated.type == TransactionType.INCOME && !updated.isBudgetIncome) availableCash += updated.amount
                                }
                                persistAvailableCash()
                            }
                        },
                        onDeleteTransaction = { txn ->
                            transactions.removeAll { it.id == txn.id }
                            saveTransactions()
                            if (budgetStartDate != null && !txn.date.isBefore(budgetStartDate)) {
                                if (txn.type == TransactionType.EXPENSE) {
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
                                        if (txn.type == TransactionType.EXPENSE) {
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
                        futureExpenditures = futureExpenditures,
                        currencySymbol = currencySymbol,
                        budgetPeriod = budgetPeriod,
                        isManualBudgetEnabled = isManualBudgetEnabled,
                        onAddExpenditure = { futureExpenditures.add(it); saveFutureExpenditures() },
                        onUpdateExpenditure = { updated ->
                            val idx = futureExpenditures.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) { futureExpenditures[idx] = updated; saveFutureExpenditures() }
                        },
                        onDeleteExpenditure = { futureExpenditures.removeAll { s -> s.id == it.id }; saveFutureExpenditures() },
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
                        onAddRecurringExpense = { recurringExpenses.add(it); saveRecurringExpenses() },
                        onUpdateRecurringExpense = { updated ->
                            val idx = recurringExpenses.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) { recurringExpenses[idx] = updated; saveRecurringExpenses() }
                        },
                        onDeleteRecurringExpense = { recurringExpenses.removeAll { s -> s.id == it.id }; saveRecurringExpenses() },
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "recurring_expenses_help" }
                    )
                    "savings" -> SavingsScreen(
                        onBack = { currentScreen = "main" },
                        onHelpClick = { currentScreen = "savings_help" }
                    )
                    "budget_config" -> BudgetConfigScreen(
                        incomeSources = incomeSources,
                        currencySymbol = currencySymbol,
                        onAddIncomeSource = { incomeSources.add(it); saveIncomeSources() },
                        onUpdateIncomeSource = { updated ->
                            val idx = incomeSources.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                incomeSources[idx] = updated
                                saveIncomeSources()
                            }
                        },
                        onDeleteIncomeSource = { incomeSources.removeAll { s -> s.id == it.id }; saveIncomeSources() },
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
                        onRecalculate = {
                            safeBudgetAmount = BudgetCalculator.calculateSafeBudgetAmount(
                                incomeSources, recurringExpenses, budgetPeriod
                            )
                            prefs.edit().putFloat("safeBudgetAmount", safeBudgetAmount.toFloat()).apply()
                            if (budgetStartDate == null) {
                                // First-time setup
                                budgetStartDate = BudgetCalculator.currentPeriodStart(budgetPeriod, resetDayOfWeek, resetDayOfMonth)
                                lastRefreshDate = LocalDate.now()
                                // Recompute budgetAmount with new safeBudgetAmount
                                val newBudgetAmount = if (isManualBudgetEnabled) {
                                    manualBudgetAmount
                                } else {
                                    val amortDed = BudgetCalculator.activeAmortizationDeductions(amortizationEntries, budgetPeriod)
                                    val fleDed = BudgetCalculator.activeFLEDeductions(futureExpenditures, budgetPeriod)
                                    maxOf(0.0, safeBudgetAmount - amortDed - fleDed)
                                }
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
                    "savings_help" -> SavingsHelpScreen(
                        onBack = { currentScreen = "savings" }
                    )
                    "budget_config_help" -> BudgetConfigHelpScreen(
                        onBack = { currentScreen = "budget_config" }
                    )
                }
            }
        }
    }
}
