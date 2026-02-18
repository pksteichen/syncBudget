package com.syncbudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.FutureExpenditure
import com.syncbudget.app.data.generateFutureExpenditureId
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private fun calculatePerPeriodDeduction(
    expenditure: FutureExpenditure,
    budgetPeriod: BudgetPeriod
): Double {
    val remaining = expenditure.amount - expenditure.totalSavedSoFar
    if (remaining <= 0) return 0.0
    val today = LocalDate.now()
    if (!today.isBefore(expenditure.targetDate)) return remaining
    val periods = when (budgetPeriod) {
        BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(today, expenditure.targetDate)
        BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(today, expenditure.targetDate)
        BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(today, expenditure.targetDate)
    }
    if (periods <= 0) return remaining
    return remaining / periods.toDouble()
}

private fun periodSingularLabel(budgetPeriod: BudgetPeriod): String = when (budgetPeriod) {
    BudgetPeriod.DAILY -> "day"
    BudgetPeriod.WEEKLY -> "week"
    BudgetPeriod.MONTHLY -> "month"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FutureExpendituresScreen(
    futureExpenditures: List<FutureExpenditure>,
    currencySymbol: String,
    budgetPeriod: BudgetPeriod,
    isManualBudgetEnabled: Boolean = false,
    onAddExpenditure: (FutureExpenditure) -> Unit,
    onUpdateExpenditure: (FutureExpenditure) -> Unit,
    onDeleteExpenditure: (FutureExpenditure) -> Unit,
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpenditure by remember { mutableStateOf<FutureExpenditure?>(null) }
    var deletingExpenditure by remember { mutableStateOf<FutureExpenditure?>(null) }

    val allPaused = futureExpenditures.isNotEmpty() && futureExpenditures.all { it.isPaused }
    val anyActive = futureExpenditures.any { !it.isPaused }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Future Large Expenditures",
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
                        if (futureExpenditures.isNotEmpty()) {
                            IconButton(onClick = {
                                if (anyActive) {
                                    futureExpenditures.forEach { onUpdateExpenditure(it.copy(isPaused = true)) }
                                } else {
                                    futureExpenditures.forEach { onUpdateExpenditure(it.copy(isPaused = false)) }
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
                    text = "Plan for large future expenses such as new tires, a new car, or home repairs. " +
                            "Enter the expected amount and target date. Your budget will be reduced each " +
                            "period to save enough by the target date. Use the pause button to temporarily " +
                            "stop deductions for an item.",
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
                    Text("Add Future Expenditure")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            items(futureExpenditures) { expenditure ->
                val fullySaved = expenditure.totalSavedSoFar >= expenditure.amount
                val deduction = calculatePerPeriodDeduction(expenditure, budgetPeriod)
                val label = periodSingularLabel(budgetPeriod)
                val contentAlpha = if (expenditure.isPaused) 0.5f else 1f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingExpenditure = expenditure }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onUpdateExpenditure(expenditure.copy(isPaused = !expenditure.isPaused)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (expenditure.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (expenditure.isPaused) "Resume" else "Pause",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha)
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            text = expenditure.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha)
                        )
                        Text(
                            text = "$currencySymbol${"%.2f".format(expenditure.amount)} by ${expenditure.targetDate}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f * contentAlpha)
                        )
                        if (fullySaved) {
                            Text(
                                text = "Fully Saved!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        } else if (expenditure.isPaused) {
                            Text(
                                text = "Paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        } else {
                            Text(
                                text = "Budget reduction: $currencySymbol${"%.2f".format(deduction)}/$label",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = "Total Saved So Far: $currencySymbol${"%.2f".format(expenditure.totalSavedSoFar)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50).copy(alpha = contentAlpha)
                        )
                    }
                    IconButton(onClick = { deletingExpenditure = expenditure }) {
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
        AddEditFutureExpenditureDialog(
            title = "Add Future Expenditure",
            initialDescription = "",
            initialAmount = "",
            initialTargetDate = null,
            onDismiss = { showAddDialog = false },
            onSave = { description, amount, targetDate ->
                val id = generateFutureExpenditureId(futureExpenditures.map { it.id }.toSet())
                onAddExpenditure(FutureExpenditure(id = id, description = description, amount = amount, targetDate = targetDate))
                showAddDialog = false
            }
        )
    }

    editingExpenditure?.let { expenditure ->
        AddEditFutureExpenditureDialog(
            title = "Edit Future Expenditure",
            initialDescription = expenditure.description,
            initialAmount = "%.2f".format(expenditure.amount),
            initialTargetDate = expenditure.targetDate,
            onDismiss = { editingExpenditure = null },
            onSave = { description, amount, targetDate ->
                onUpdateExpenditure(expenditure.copy(description = description, amount = amount, targetDate = targetDate))
                editingExpenditure = null
            }
        )
    }

    deletingExpenditure?.let { expenditure ->
        AlertDialog(
            onDismissRequest = { deletingExpenditure = null },
            title = { Text("Delete Expenditure?") },
            text = { Text("Delete \"${expenditure.description}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteExpenditure(expenditure)
                    deletingExpenditure = null
                }) {
                    Text("Delete", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingExpenditure = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditFutureExpenditureDialog(
    title: String,
    initialDescription: String,
    initialAmount: String,
    initialTargetDate: LocalDate?,
    onDismiss: () -> Unit,
    onSave: (String, Double, LocalDate) -> Unit
) {
    var description by remember { mutableStateOf(initialDescription) }
    var amountText by remember { mutableStateOf(initialAmount) }
    var targetDate by remember { mutableStateOf(initialTargetDate) }
    var showDatePicker by remember { mutableStateOf(false) }

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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { newVal ->
                        if (newVal.isEmpty() || newVal.toDoubleOrNull() != null || newVal == ".") {
                            amountText = newVal
                        }
                    },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (description.isNotBlank() && amount != null && amount > 0 && targetDate != null) {
                        onSave(description.trim(), amount, targetDate!!)
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
