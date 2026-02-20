package com.syncbudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.generateSavingsGoalId
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

private fun calculatePerPeriodDeduction(
    goal: SavingsGoal,
    budgetPeriod: BudgetPeriod
): Double {
    val remaining = goal.targetAmount - goal.totalSavedSoFar
    if (remaining <= 0) return 0.0
    if (goal.targetDate != null) {
        val today = LocalDate.now()
        if (!today.isBefore(goal.targetDate)) return remaining
        val periods = when (budgetPeriod) {
            BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(today, goal.targetDate)
            BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(today, goal.targetDate)
            BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(today, goal.targetDate)
        }
        if (periods <= 0) return remaining
        return remaining / periods.toDouble()
    } else {
        return minOf(goal.contributionPerPeriod, remaining)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FutureExpendituresScreen(
    savingsGoals: List<SavingsGoal>,
    currencySymbol: String,
    budgetPeriod: BudgetPeriod,
    isManualBudgetEnabled: Boolean = false,
    dateFormatPattern: String = "yyyy-MM-dd",
    onAddGoal: (SavingsGoal) -> Unit,
    onUpdateGoal: (SavingsGoal) -> Unit,
    onDeleteGoal: (SavingsGoal) -> Unit,
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val S = LocalStrings.current
    val customColors = LocalSyncBudgetColors.current
    val dateFormatter = remember(dateFormatPattern) { DateTimeFormatter.ofPattern(dateFormatPattern) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var deletingGoal by remember { mutableStateOf<SavingsGoal?>(null) }

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
                if (isManualBudgetEnabled) {
                    Text(
                        text = S.futureExpenditures.manualOverrideWarning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFF44336),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(S.futureExpenditures.addSavingsGoal)
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingGoal = goal }
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
                                    "$currencySymbol${"%.2f".format(goal.targetAmount)}",
                                    goal.targetDate.format(dateFormatter)
                                )
                            } else {
                                S.futureExpenditures.targetLabel(
                                    "$currencySymbol${"%.2f".format(goal.targetAmount)}"
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
                                    S.futureExpenditures.budgetReduction(
                                        "$currencySymbol${"%.2f".format(deduction)}",
                                        periodLabel
                                    )
                                } else {
                                    S.futureExpenditures.contributionLabel(
                                        "$currencySymbol${"%.2f".format(goal.contributionPerPeriod)}",
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
                                "$currencySymbol${"%.2f".format(goal.totalSavedSoFar)}",
                                "$currencySymbol${"%.2f".format(goal.targetAmount)}"
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
            initialTargetDate = null,
            initialContribution = "",
            initialIsTargetDate = true,
            isAddMode = true,
            dateFormatter = dateFormatter,
            onDismiss = { showAddDialog = false },
            onSave = { name, targetAmount, startingSaved, targetDate, contribution ->
                val id = generateSavingsGoalId(savingsGoals.map { it.id }.toSet())
                onAddGoal(
                    SavingsGoal(
                        id = id,
                        name = name,
                        targetAmount = targetAmount,
                        targetDate = targetDate,
                        totalSavedSoFar = startingSaved,
                        contributionPerPeriod = contribution
                    )
                )
                showAddDialog = false
            }
        )
    }

    editingGoal?.let { goal ->
        AddEditSavingsGoalDialog(
            title = S.futureExpenditures.editSavingsGoal,
            initialName = goal.name,
            initialTargetAmount = "%.2f".format(goal.targetAmount),
            initialStartingSaved = "",
            initialTargetDate = goal.targetDate,
            initialContribution = if (goal.targetDate == null) "%.2f".format(goal.contributionPerPeriod) else "",
            initialIsTargetDate = goal.targetDate != null,
            isAddMode = false,
            dateFormatter = dateFormatter,
            onDismiss = { editingGoal = null },
            onSave = { name, targetAmount, _, targetDate, contribution ->
                onUpdateGoal(
                    goal.copy(
                        name = name,
                        targetAmount = targetAmount,
                        targetDate = targetDate,
                        contributionPerPeriod = contribution
                    )
                )
                editingGoal = null
            }
        )
    }

    deletingGoal?.let { goal ->
        AlertDialog(
            onDismissRequest = { deletingGoal = null },
            title = { Text(S.futureExpenditures.deleteSavingsGoal) },
            text = { Text(S.futureExpenditures.deleteGoalConfirm(goal.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGoal(goal)
                    deletingGoal = null
                }) {
                    Text(S.common.delete, color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingGoal = null }) { Text(S.common.cancel) }
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
    initialTargetDate: LocalDate?,
    initialContribution: String,
    initialIsTargetDate: Boolean,
    isAddMode: Boolean,
    dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, LocalDate?, Double) -> Unit
) {
    val S = LocalStrings.current

    var name by remember { mutableStateOf(initialName) }
    var targetAmountText by remember { mutableStateOf(initialTargetAmount) }
    var startingSavedText by remember { mutableStateOf(initialStartingSaved) }
    var isTargetDateType by remember { mutableStateOf(initialIsTargetDate) }
    var targetDate by remember { mutableStateOf(initialTargetDate) }
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
    val isTargetDateValid = targetDate != null && targetDate!!.isAfter(LocalDate.now())

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
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
                        if (newVal.isEmpty() || newVal.toDoubleOrNull() != null || newVal == ".") {
                            targetAmountText = newVal
                        }
                    },
                    label = { Text(S.futureExpenditures.targetAmount) },
                    singleLine = true,
                    isError = showValidation && !isTargetAmountValid,
                    supportingText = if (showValidation && !isTargetAmountValid) ({
                        Text(S.futureExpenditures.exampleTargetAmount, color = Color(0xFFF44336))
                    }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isAddMode) {
                    OutlinedTextField(
                        value = startingSavedText,
                        onValueChange = { newVal ->
                            if (newVal.isEmpty() || newVal.toDoubleOrNull() != null || newVal == ".") {
                                startingSavedText = newVal
                            }
                        },
                        label = { Text(S.futureExpenditures.startingSavedAmount) },
                        singleLine = true,
                        isError = showValidation && !isStartingSavedValid,
                        supportingText = if (showValidation && !isStartingSavedValid) ({
                            Text(S.futureExpenditures.mustBeLessThanTarget, color = Color(0xFFF44336))
                        }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Goal type toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = isTargetDateType,
                        onClick = { isTargetDateType = true },
                        label = { Text(S.futureExpenditures.targetDate) }
                    )
                    FilterChip(
                        selected = !isTargetDateType,
                        onClick = { isTargetDateType = false },
                        label = { Text(S.futureExpenditures.fixedContribution) }
                    )
                }

                if (isTargetDateType) {
                    Box(
                        modifier = if (showValidation && !isTargetDateValid)
                            Modifier.border(1.dp, Color(0xFFF44336), RoundedCornerShape(4.dp))
                        else Modifier
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (targetDate != null) S.futureExpenditures.targetDateLabel(targetDate!!.format(dateFormatter))
                                else S.futureExpenditures.selectTargetDate
                            )
                        }
                    }
                    if (showValidation && !isTargetDateValid) {
                        Text(
                            text = S.futureExpenditures.selectAFutureDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF44336)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = contributionText,
                        onValueChange = { newVal ->
                            if (newVal.isEmpty() || newVal.toDoubleOrNull() != null || newVal == ".") {
                                contributionText = newVal
                            }
                        },
                        label = { Text(S.futureExpenditures.contributionPerPeriod) },
                        singleLine = true,
                        isError = showValidation && !isContributionValid,
                        supportingText = if (showValidation && !isContributionValid) ({
                            Text(S.futureExpenditures.exampleContribution, color = Color(0xFFF44336))
                        }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val isTypeValid = if (isTargetDateType) isTargetDateValid else isContributionValid
                    val isValid = isNameValid && isTargetAmountValid && isStartingSavedValid && isTypeValid
                    if (isValid) {
                        val amount = targetAmount!!
                        val saved = startingSavedText.toDoubleOrNull() ?: 0.0
                        if (isTargetDateType) {
                            onSave(name.trim(), amount, saved, targetDate, 0.0)
                        } else {
                            onSave(name.trim(), amount, saved, null, contribution!!)
                        }
                    } else {
                        showValidation = true
                    }
                }
            ) {
                Text(S.common.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S.common.cancel) }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        targetDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(S.common.ok) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(S.common.cancel) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
