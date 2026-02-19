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
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
        return goal.contributionPerPeriod
    }
}

private fun periodSingularLabel(budgetPeriod: BudgetPeriod): String = when (budgetPeriod) {
    BudgetPeriod.DAILY -> "day"
    BudgetPeriod.WEEKLY -> "week"
    BudgetPeriod.MONTHLY -> "month"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FutureExpendituresScreen(
    savingsGoals: List<SavingsGoal>,
    currencySymbol: String,
    budgetPeriod: BudgetPeriod,
    isManualBudgetEnabled: Boolean = false,
    onAddGoal: (SavingsGoal) -> Unit,
    onUpdateGoal: (SavingsGoal) -> Unit,
    onDeleteGoal: (SavingsGoal) -> Unit,
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var deletingGoal by remember { mutableStateOf<SavingsGoal?>(null) }

    val allPaused = savingsGoals.isNotEmpty() && savingsGoals.all { it.isPaused }
    val anyActive = savingsGoals.any { !it.isPaused }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Savings Goals",
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText
                    )
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
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
                                    contentDescription = if (allPaused) "Resume All" else "Pause All",
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
                            contentDescription = "Help",
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
                    text = "Save for future goals by setting a target amount and either a target date " +
                            "(the app calculates per-period deductions) or a fixed contribution per period. " +
                            "Your budget is reduced each period to build savings automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (isManualBudgetEnabled) {
                    Text(
                        text = "Budget deductions are disabled. Manual budget override is active in Settings > Budget Configuration.",
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
                    Text("Add Savings Goal")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            items(savingsGoals) { goal ->
                val goalReached = goal.totalSavedSoFar >= goal.targetAmount
                val progress = if (goal.targetAmount > 0) {
                    (goal.totalSavedSoFar / goal.targetAmount).toFloat().coerceIn(0f, 1f)
                } else 0f
                val deduction = calculatePerPeriodDeduction(goal, budgetPeriod)
                val label = periodSingularLabel(budgetPeriod)
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
                            contentDescription = if (goal.isPaused) "Resume" else "Pause",
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
                                "$currencySymbol${"%.2f".format(goal.targetAmount)} by ${goal.targetDate}"
                            } else {
                                "Target: $currencySymbol${"%.2f".format(goal.targetAmount)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f * contentAlpha)
                        )
                        if (goalReached) {
                            Text(
                                text = "Goal reached!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        } else if (goal.isPaused) {
                            Text(
                                text = "Paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        } else {
                            Text(
                                text = if (goal.targetDate != null) {
                                    "Budget reduction: $currencySymbol${"%.2f".format(deduction)}/$label"
                                } else {
                                    "Contribution: $currencySymbol${"%.2f".format(goal.contributionPerPeriod)}/$label"
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
                            text = "Saved: $currencySymbol${"%.2f".format(goal.totalSavedSoFar)} of $currencySymbol${"%.2f".format(goal.targetAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50).copy(alpha = contentAlpha)
                        )
                    }
                    IconButton(onClick = { deletingGoal = goal }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFF44336)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditSavingsGoalDialog(
            title = "Add Savings Goal",
            initialName = "",
            initialTargetAmount = "",
            initialStartingSaved = "",
            initialTargetDate = null,
            initialContribution = "",
            initialIsTargetDate = true,
            isAddMode = true,
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
            title = "Edit Savings Goal",
            initialName = goal.name,
            initialTargetAmount = "%.2f".format(goal.targetAmount),
            initialStartingSaved = "",
            initialTargetDate = goal.targetDate,
            initialContribution = if (goal.targetDate == null) "%.2f".format(goal.contributionPerPeriod) else "",
            initialIsTargetDate = goal.targetDate != null,
            isAddMode = false,
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
            title = { Text("Delete Savings Goal?") },
            text = { Text("Delete \"${goal.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGoal(goal)
                    deletingGoal = null
                }) {
                    Text("Delete", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingGoal = null }) { Text("Cancel") }
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
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, LocalDate?, Double) -> Unit
) {
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
                    label = { Text("Name") },
                    singleLine = true,
                    isError = showValidation && !isNameValid,
                    supportingText = if (showValidation && !isNameValid) ({
                        Text("Required, e.g. New Car", color = Color(0xFFF44336))
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
                    label = { Text("Target Amount") },
                    singleLine = true,
                    isError = showValidation && !isTargetAmountValid,
                    supportingText = if (showValidation && !isTargetAmountValid) ({
                        Text("e.g. 5000.00", color = Color(0xFFF44336))
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
                        label = { Text("Starting Saved Amount (optional)") },
                        singleLine = true,
                        isError = showValidation && !isStartingSavedValid,
                        supportingText = if (showValidation && !isStartingSavedValid) ({
                            Text("Must be less than target", color = Color(0xFFF44336))
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
                        label = { Text("Target Date") }
                    )
                    FilterChip(
                        selected = !isTargetDateType,
                        onClick = { isTargetDateType = false },
                        label = { Text("Fixed Contribution") }
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
                                if (targetDate != null) "Target Date: $targetDate"
                                else "Select Target Date"
                            )
                        }
                    }
                    if (showValidation && !isTargetDateValid) {
                        Text(
                            text = "Select a future date",
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
                        label = { Text("Contribution per Period") },
                        singleLine = true,
                        isError = showValidation && !isContributionValid,
                        supportingText = if (showValidation && !isContributionValid) ({
                            Text("e.g. 100.00", color = Color(0xFFF44336))
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
