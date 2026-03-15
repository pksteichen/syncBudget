package com.syncbudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ShowChart
import com.syncbudget.app.ui.theme.AdAwareAlertDialog
import com.syncbudget.app.ui.theme.DialogStyle
import com.syncbudget.app.ui.theme.DialogPrimaryButton
import com.syncbudget.app.ui.theme.DialogSecondaryButton
import com.syncbudget.app.ui.theme.DialogDangerButton
import com.syncbudget.app.ui.theme.DialogHeader
import com.syncbudget.app.ui.theme.DialogFooter
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import com.syncbudget.app.ui.theme.AdAwareDatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.syncbudget.app.ui.theme.AdAwareDialog
import com.syncbudget.app.ui.theme.PulsingScrollArrow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SavingsSimulator
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.calculatePerPeriodDeduction
import com.syncbudget.app.data.generateSavingsGoalId
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS
import com.syncbudget.app.ui.components.formatCurrency
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalAppToast
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FutureExpendituresScreen(
    savingsGoals: List<SavingsGoal>,
    transactions: List<Transaction> = emptyList(),
    currencySymbol: String,
    budgetPeriod: BudgetPeriod,
    dateFormatPattern: String = "yyyy-MM-dd",
    recurringExpenses: List<RecurringExpense> = emptyList(),
    incomeSources: List<IncomeSource> = emptyList(),
    amortizationEntries: List<AmortizationEntry> = emptyList(),
    baseBudget: Double = 0.0,
    availableCash: Double = 0.0,
    resetDayOfWeek: Int = 7,
    resetDayOfMonth: Int = 1,
    today: LocalDate = LocalDate.now(),
    isManualOverBudget: Boolean = false,
    budgetPeriodLabel: String = "",
    onAddGoal: (SavingsGoal) -> Unit,
    onUpdateGoal: (SavingsGoal) -> Unit,
    onDeleteGoal: (SavingsGoal) -> Unit,
    isPaidUser: Boolean = false,
    isSubscriber: Boolean = false,
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {},
    onViewChart: () -> Unit = {}
) {
    val S = LocalStrings.current
    val customColors = LocalSyncBudgetColors.current
    val toastState = LocalAppToast.current
    val dateFormatter = remember(dateFormatPattern) { DateTimeFormatter.ofPattern(dateFormatPattern) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var deletingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var showSavingsWhyDialog by remember { mutableStateOf(false) }
    var savingsTextYPx by remember { mutableIntStateOf(0) }
    var linkedTransactionsGoal by remember { mutableStateOf<SavingsGoal?>(null) }

    val allPaused = savingsGoals.isNotEmpty() && savingsGoals.all { it.isPaused }
    val anyActive = savingsGoals.any { !it.isPaused }

    val periodLabel = when (budgetPeriod) {
        BudgetPeriod.DAILY -> S.common.periodDay
        BudgetPeriod.WEEKLY -> S.common.periodWeek
        BudgetPeriod.MONTHLY -> S.common.periodMonth
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.futureExpenditures.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText
                    )
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = S.common.back,
                                tint = customColors.headerText
                            )
                        }
                        if (savingsGoals.isNotEmpty()) {
                            IconButton(onClick = {
                                if (anyActive) {
                                    savingsGoals.forEach { onUpdateGoal(it.copy(isPaused = true)) }
                                } else {
                                    savingsGoals.forEach { onUpdateGoal(it.copy(isPaused = false)) }
                                }
                            }) {
                                Icon(
                                    imageVector = if (allPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = if (allPaused) S.futureExpenditures.resumeAll else S.futureExpenditures.pauseAll,
                                    tint = customColors.headerText
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onHelpClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = S.common.help,
                            tint = customColors.headerText
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = customColors.headerBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = S.futureExpenditures.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (!isManualOverBudget) {
                    val simResult = SavingsSimulator.calculateSavingsRequired(
                        incomeSources = incomeSources,
                        recurringExpenses = recurringExpenses,
                        budgetPeriod = budgetPeriod,
                        baseBudget = baseBudget,
                        amortizationEntries = amortizationEntries,
                        savingsGoals = savingsGoals,
                        availableCash = availableCash,
                        resetDayOfWeek = resetDayOfWeek,
                        resetDayOfMonth = resetDayOfMonth,
                        today = today
                    )
                    run {
                        val formattedAmount = formatCurrency(maxOf(0.0, simResult.savingsRequired), currencySymbol)
                        val periodText = budgetPeriodLabel
                        val lowPointDateStr = simResult.lowPointDate?.format(
                            DateTimeFormatter.ofPattern(dateFormatPattern)
                        ) ?: ""
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = S.futureExpenditures.savingsRequiredMessage(formattedAmount, periodText),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { savingsTextYPx = it.positionInWindow().y.toInt() }
                                        .clickable {
                                            if (lowPointDateStr.isNotEmpty()) {
                                                toastState.show(S.futureExpenditures.savingsLowPointToast(lowPointDateStr), savingsTextYPx)
                                            }
                                        }
                                )
                                Text(
                                    text = S.futureExpenditures.savingsWhyLink,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clickable { showSavingsWhyDialog = true }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                val buttonFontSize = with(LocalDensity.current) { 14.dp.toSp() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(end = 2.dp)
                        )
                        Text(
                            S.futureExpenditures.addSavingsGoal,
                            fontSize = buttonFontSize,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (isSubscriber) onViewChart()
                            else toastState.show(S.settings.subscribeToAccess)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        enabled = isSubscriber
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(end = 2.dp)
                        )
                        Text(
                            S.futureExpenditures.viewSimulationChart,
                            fontSize = buttonFontSize,
                            maxLines = 1
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            items(savingsGoals) { goal ->
                val goalReached = goal.totalSavedSoFar >= goal.targetAmount
                val progress = if (goal.targetAmount > 0) {
                    (goal.totalSavedSoFar / goal.targetAmount).toFloat().coerceIn(0f, 1f)
                } else 0f
                val deduction = calculatePerPeriodDeduction(goal, budgetPeriod)
                val contentAlpha = if (goal.isPaused) 0.5f else 1f

                @OptIn(ExperimentalFoundationApi::class)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { editingGoal = goal },
                            onLongClick = {
                                val linked = transactions.filter { it.linkedSavingsGoalId == goal.id }
                                    .sortedByDescending { it.date }
                                    .take(10)
                                if (linked.isEmpty()) {
                                    toastState.show(S.futureExpenditures.noLinkedTransactions, savingsTextYPx)
                                } else {
                                    linkedTransactionsGoal = goal
                                }
                            }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onUpdateGoal(goal.copy(isPaused = !goal.isPaused)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (goal.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (goal.isPaused) S.futureExpenditures.resume else S.futureExpenditures.pause,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha)
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha)
                        )
                        Text(
                            text = if (goal.targetDate != null) {
                                S.futureExpenditures.targetAmountBy(
                                    formatCurrency(goal.targetAmount, currencySymbol),
                                    goal.targetDate.format(dateFormatter)
                                )
                            } else {
                                S.futureExpenditures.targetLabel(
                                    formatCurrency(goal.targetAmount, currencySymbol)
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f * contentAlpha)
                        )
                        // Show payoff date for fixed-contribution goals
                        if (!goalReached && !goal.isPaused && goal.targetDate == null && goal.contributionPerPeriod > 0) {
                            val remaining = goal.targetAmount - goal.totalSavedSoFar
                            if (remaining > 0) {
                                val periodsRemaining = ceil(remaining / goal.contributionPerPeriod).toLong()
                                val today = LocalDate.now()
                                val payoffDate = when (budgetPeriod) {
                                    BudgetPeriod.DAILY -> today.plusDays(periodsRemaining)
                                    BudgetPeriod.WEEKLY -> today.plusWeeks(periodsRemaining)
                                    BudgetPeriod.MONTHLY -> today.plusMonths(periodsRemaining)
                                }
                                Text(
                                    text = S.futureExpenditures.payoffDate(payoffDate.format(dateFormatter)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f * contentAlpha)
                                )
                            }
                        }
                        if (goalReached) {
                            Text(
                                text = S.futureExpenditures.goalReached,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        } else if (goal.isPaused) {
                            Text(
                                text = S.futureExpenditures.paused,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        } else {
                            Text(
                                text = if (goal.targetDate != null) {
                                    S.futureExpenditures.contributionLabel(
                                        formatCurrency(deduction, currencySymbol),
                                        periodLabel
                                    )
                                } else {
                                    S.futureExpenditures.contributionLabel(
                                        formatCurrency(goal.contributionPerPeriod, currencySymbol),
                                        periodLabel
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(Color(0xFF4CAF50).copy(alpha = contentAlpha))
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = S.futureExpenditures.savedOf(
                                formatCurrency(goal.totalSavedSoFar, currencySymbol),
                                formatCurrency(goal.targetAmount, currencySymbol)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50).copy(alpha = contentAlpha)
                        )
                    }
                    IconButton(onClick = { deletingGoal = goal }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = S.common.delete,
                            tint = Color(0xFFF44336)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditSavingsGoalDialog(
            title = S.futureExpenditures.addSavingsGoal,
            initialName = "",
            initialTargetAmount = "",
            initialStartingSaved = "",
            initialContribution = "",
            isAddMode = true,
            budgetPeriod = budgetPeriod,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            onDismiss = { showAddDialog = false },
            onSave = { name, targetAmount, startingSaved, contribution ->
                val id = generateSavingsGoalId(savingsGoals.map { it.id }.toSet())
                onAddGoal(
                    SavingsGoal(
                        id = id,
                        name = name,
                        targetAmount = targetAmount,
                        totalSavedSoFar = startingSaved,
                        contributionPerPeriod = contribution
                    )
                )
                showAddDialog = false
            }
        )
    }

    editingGoal?.let { goal ->
        // For existing target-date goals, pre-calculate the current per-period deduction
        val currentContribution = if (goal.targetDate != null) {
            calculatePerPeriodDeduction(goal, budgetPeriod)
        } else goal.contributionPerPeriod
        AddEditSavingsGoalDialog(
            title = S.futureExpenditures.editSavingsGoal,
            initialName = goal.name,
            initialTargetAmount = "%.${CURRENCY_DECIMALS[currencySymbol] ?: 2}f".format(goal.targetAmount),
            initialStartingSaved = "",
            initialContribution = "%.${CURRENCY_DECIMALS[currencySymbol] ?: 2}f".format(currentContribution),
            isAddMode = false,
            existingSaved = goal.totalSavedSoFar,
            budgetPeriod = budgetPeriod,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            onDismiss = { editingGoal = null },
            onSave = { name, targetAmount, _, contribution ->
                onUpdateGoal(
                    goal.copy(
                        name = name,
                        targetAmount = targetAmount,
                        targetDate = null,
                        contributionPerPeriod = contribution
                    )
                )
                editingGoal = null
            }
        )
    }

    deletingGoal?.let { goal ->
        AdAwareAlertDialog(
            onDismissRequest = { deletingGoal = null },
            title = { Text(S.futureExpenditures.deleteSavingsGoal) },
            text = { Text(S.futureExpenditures.deleteGoalConfirm(goal.name)) },
            style = DialogStyle.DANGER,
            confirmButton = {
                DialogDangerButton(onClick = {
                        onDeleteGoal(goal)
                        deletingGoal = null
                    }) { Text(S.common.delete) }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { deletingGoal = null }) { Text(S.common.cancel) }
            }
        )
    }

    if (showSavingsWhyDialog) {
        val whyScrollState = rememberScrollState()
        AdAwareAlertDialog(
            onDismissRequest = { showSavingsWhyDialog = false },
            title = { Text(S.futureExpenditures.savingsWhyTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(whyScrollState)) {
                    Text(S.futureExpenditures.savingsWhyBody)
                }
            },
            confirmButton = {
                DialogPrimaryButton(onClick = { showSavingsWhyDialog = false }) { Text(S.common.ok) }
            },
            scrollState = whyScrollState
        )
    }

    linkedTransactionsGoal?.let { goal ->
        val linked = remember(goal, transactions) {
            transactions.filter { it.linkedSavingsGoalId == goal.id }
                .sortedByDescending { it.date }
                .take(10)
        }
        AdAwareAlertDialog(
            onDismissRequest = { linkedTransactionsGoal = null },
            title = { Text(goal.name) },
            text = {
                Column {
                    Text(
                        S.futureExpenditures.linkedTransactions,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    linked.forEach { tx ->
                        val txDate = tx.date.format(dateFormatter)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.source, style = MaterialTheme.typography.bodyMedium)
                                Text(txDate, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Text(
                                formatCurrency(tx.amount, currencySymbol),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                DialogSecondaryButton(onClick = { linkedTransactionsGoal = null }) { Text(S.common.close) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditSavingsGoalDialog(
    title: String,
    initialName: String,
    initialTargetAmount: String,
    initialStartingSaved: String,
    initialContribution: String,
    isAddMode: Boolean,
    existingSaved: Double = 0.0,
    budgetPeriod: BudgetPeriod,
    currencySymbol: String,
    dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double) -> Unit
) {
    val S = LocalStrings.current
    val maxDecimalPlaces = CURRENCY_DECIMALS[currencySymbol] ?: 2

    var name by remember { mutableStateOf(initialName) }
    var targetAmountText by remember { mutableStateOf(initialTargetAmount) }
    var startingSavedText by remember { mutableStateOf(initialStartingSaved) }
    var contributionText by remember { mutableStateOf(initialContribution) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }

    val targetAmount = targetAmountText.toDoubleOrNull()
    val startingSaved = startingSavedText.toDoubleOrNull() ?: 0.0
    val contribution = contributionText.toDoubleOrNull()
    val isNameValid = name.isNotBlank()
    val isTargetAmountValid = targetAmount != null && targetAmount > 0
    val isStartingSavedValid = startingSavedText.isEmpty() || (startingSaved >= 0 && (targetAmount == null || startingSaved < targetAmount))
    val isContributionValid = contribution != null && contribution > 0

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    AdAwareDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).imePadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            val dialogScrollState = rememberScrollState()
            Box {
            Column {
                DialogHeader(title)

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(dialogScrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(S.futureExpenditures.name) },
                        singleLine = true,
                        isError = showValidation && !isNameValid,
                        supportingText = if (showValidation && !isNameValid) ({
                            Text(S.futureExpenditures.requiredNameExample, color = Color(0xFFF44336))
                        }) else null,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetAmountText,
                        onValueChange = { newVal ->
                            if (newVal.isEmpty() || newVal == "." || newVal.toDoubleOrNull() != null) {
                                val dotIdx = newVal.indexOf('.')
                                val decs = if (dotIdx >= 0) newVal.length - dotIdx - 1 else 0
                                if (maxDecimalPlaces == 0 && dotIdx >= 0) { /* block */ }
                                else if (decs <= maxDecimalPlaces) { targetAmountText = newVal }
                            }
                        },
                        label = { Text(S.futureExpenditures.targetAmount) },
                        singleLine = true,
                        isError = showValidation && !isTargetAmountValid,
                        supportingText = if (showValidation && !isTargetAmountValid) ({
                            Text(S.futureExpenditures.exampleTargetAmount, color = Color(0xFFF44336))
                        }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = if (maxDecimalPlaces > 0) KeyboardType.Decimal else KeyboardType.Number),
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isAddMode) {
                        OutlinedTextField(
                            value = startingSavedText,
                            onValueChange = { newVal ->
                                if (newVal.isEmpty() || newVal == "." || newVal.toDoubleOrNull() != null) {
                                    val dotIdx = newVal.indexOf('.')
                                    val decs = if (dotIdx >= 0) newVal.length - dotIdx - 1 else 0
                                    if (maxDecimalPlaces == 0 && dotIdx >= 0) { /* block */ }
                                    else if (decs <= maxDecimalPlaces) { startingSavedText = newVal }
                                }
                            },
                            label = { Text(S.futureExpenditures.startingSavedAmount) },
                            singleLine = true,
                            isError = showValidation && !isStartingSavedValid,
                            supportingText = if (showValidation && !isStartingSavedValid) ({
                                Text(S.futureExpenditures.mustBeLessThanTarget, color = Color(0xFFF44336))
                            }) else null,
                            keyboardOptions = KeyboardOptions(keyboardType = if (maxDecimalPlaces > 0) KeyboardType.Decimal else KeyboardType.Number),
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = contributionText,
                        onValueChange = { newVal ->
                            if (newVal.isEmpty() || newVal == "." || newVal.toDoubleOrNull() != null) {
                                val dotIdx = newVal.indexOf('.')
                                val decs = if (dotIdx >= 0) newVal.length - dotIdx - 1 else 0
                                if (maxDecimalPlaces == 0 && dotIdx >= 0) { /* block */ }
                                else if (decs <= maxDecimalPlaces) { contributionText = newVal }
                            }
                        },
                        label = { Text(S.futureExpenditures.contributionPerPeriod) },
                        singleLine = true,
                        isError = showValidation && !isContributionValid,
                        supportingText = if (showValidation && !isContributionValid) ({
                            Text(S.futureExpenditures.exampleContribution, color = Color(0xFFF44336))
                        }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = if (maxDecimalPlaces > 0) KeyboardType.Decimal else KeyboardType.Number),
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Helper: calculate contribution from a target date
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(S.futureExpenditures.calculateWithTargetDate)
                    }
                }

                DialogFooter {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DialogSecondaryButton(onClick = onDismiss) { Text(S.common.cancel, maxLines = 1) }
                    Spacer(modifier = Modifier.width(8.dp))
                    DialogPrimaryButton(onClick = {
                            val isValid = isNameValid && isTargetAmountValid && isStartingSavedValid && isContributionValid
                            if (isValid) {
                                onSave(name.trim(), targetAmount!!, startingSavedText.toDoubleOrNull() ?: 0.0, contribution!!)
                            } else {
                                showValidation = true
                            }
                        }) { Text(S.common.save) }
                }
                }
            }
            PulsingScrollArrow(
                scrollState = dialogScrollState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 50.dp)
            )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        AdAwareDatePickerDialog(
            title = S.common.selectDate,
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                DialogPrimaryButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            val today = LocalDate.now()
                            if (selectedDate.isAfter(today)) {
                                val saved = if (isAddMode) (startingSavedText.toDoubleOrNull() ?: 0.0) else existingSaved
                                val remaining = (targetAmountText.toDoubleOrNull() ?: 0.0) - saved
                                if (remaining > 0) {
                                    val periods = when (budgetPeriod) {
                                        BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(today, selectedDate)
                                        BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(today, selectedDate)
                                        BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(today, selectedDate)
                                    }
                                    if (periods > 0) {
                                        val calc = remaining / periods.toDouble()
                                        contributionText = "%.${maxDecimalPlaces}f".format(
                                            com.syncbudget.app.data.BudgetCalculator.roundCents(calc)
                                        )
                                    }
                                }
                            }
                        }
                        showDatePicker = false
                    }) { Text(S.common.ok) }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { showDatePicker = false }) { Text(S.common.cancel) }
            }
        ) {
            DatePicker(state = datePickerState, title = null)
        }
    }
}
