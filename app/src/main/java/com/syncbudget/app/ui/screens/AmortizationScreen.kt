package com.syncbudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.generateAmortizationEntryId
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private fun calculateElapsedPeriods(
    startDate: LocalDate,
    budgetPeriod: BudgetPeriod,
    totalPeriods: Int
): Int {
    val today = LocalDate.now()
    if (today.isBefore(startDate)) return 0
    val elapsed = when (budgetPeriod) {
        BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(startDate, today).toInt()
        BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(startDate, today).toInt()
        BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(startDate, today).toInt()
    }
    return minOf(elapsed, totalPeriods)
}

private fun periodLabel(budgetPeriod: BudgetPeriod): String = when (budgetPeriod) {
    BudgetPeriod.DAILY -> "days"
    BudgetPeriod.WEEKLY -> "weeks"
    BudgetPeriod.MONTHLY -> "months"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmortizationScreen(
    amortizationEntries: List<AmortizationEntry>,
    currencySymbol: String,
    budgetPeriod: BudgetPeriod,
    isManualBudgetEnabled: Boolean = false,
    onAddEntry: (AmortizationEntry) -> Unit,
    onUpdateEntry: (AmortizationEntry) -> Unit,
    onDeleteEntry: (AmortizationEntry) -> Unit,
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<AmortizationEntry?>(null) }
    var deletingEntry by remember { mutableStateOf<AmortizationEntry?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Amortization",
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = customColors.headerText
                        )
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
                    text = "Amortization lets you spread a large one-time expense across multiple budget periods. " +
                            "Instead of the full amount hitting your budget at once, the cost is divided evenly " +
                            "across the number of periods you choose. Use descriptive source names \u2014 they will " +
                            "be matched against bank transaction merchant names to automatically identify " +
                            "amortized transactions.",
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
                    Text("Add Amortization Entry")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            items(amortizationEntries) { entry ->
                val elapsed = calculateElapsedPeriods(entry.startDate, budgetPeriod, entry.totalPeriods)
                val isCompleted = elapsed >= entry.totalPeriods
                val perPeriod = entry.amount / entry.totalPeriods
                val label = periodLabel(budgetPeriod)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingEntry = entry }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.source,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$currencySymbol${"%.2f".format(entry.amount)} total \u2022 $currencySymbol${"%.2f".format(perPeriod)}/${label.dropLast(1)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (isCompleted) "Completed"
                                   else "$elapsed of ${entry.totalPeriods} $label complete",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCompleted) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = { deletingEntry = entry }) {
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
        AddEditAmortizationDialog(
            title = "Add Amortization Entry",
            initialSource = "",
            initialAmount = "",
            initialTotalPeriods = "",
            initialStartDate = null,
            budgetPeriod = budgetPeriod,
            onDismiss = { showAddDialog = false },
            onSave = { source, amount, totalPeriods, startDate ->
                val id = generateAmortizationEntryId(amortizationEntries.map { it.id }.toSet())
                onAddEntry(AmortizationEntry(id = id, source = source, amount = amount, totalPeriods = totalPeriods, startDate = startDate))
                showAddDialog = false
            }
        )
    }

    editingEntry?.let { entry ->
        AddEditAmortizationDialog(
            title = "Edit Amortization Entry",
            initialSource = entry.source,
            initialAmount = "%.2f".format(entry.amount),
            initialTotalPeriods = entry.totalPeriods.toString(),
            initialStartDate = entry.startDate,
            budgetPeriod = budgetPeriod,
            onDismiss = { editingEntry = null },
            onSave = { source, amount, totalPeriods, startDate ->
                onUpdateEntry(entry.copy(source = source, amount = amount, totalPeriods = totalPeriods, startDate = startDate))
                editingEntry = null
            }
        )
    }

    deletingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text("Delete Entry?") },
            text = { Text("Delete \"${entry.source}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteEntry(entry)
                    deletingEntry = null
                }) {
                    Text("Delete", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditAmortizationDialog(
    title: String,
    initialSource: String,
    initialAmount: String,
    initialTotalPeriods: String,
    initialStartDate: LocalDate?,
    budgetPeriod: BudgetPeriod,
    onDismiss: () -> Unit,
    onSave: (String, Double, Int, LocalDate) -> Unit
) {
    var source by remember { mutableStateOf(initialSource) }
    var amountText by remember { mutableStateOf(initialAmount) }
    var periodsText by remember { mutableStateOf(initialTotalPeriods) }
    var startDate by remember { mutableStateOf(initialStartDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull()
    val periods = periodsText.toIntOrNull()
    val isSourceValid = source.isNotBlank()
    val isAmountValid = amount != null && amount > 0
    val isPeriodsValid = periods != null && periods > 0
    val isDateValid = startDate != null
    val isValid = isSourceValid && isAmountValid && isPeriodsValid && isDateValid

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
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("Source Name") },
                    singleLine = true,
                    isError = showValidation && !isSourceValid,
                    supportingText = if (showValidation && !isSourceValid) ({
                        Text("Required, e.g. New Laptop", color = Color(0xFFF44336))
                    }) else null,
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
                    label = { Text("Total Amount") },
                    singleLine = true,
                    isError = showValidation && !isAmountValid,
                    supportingText = if (showValidation && !isAmountValid) ({
                        Text("e.g. 1200.00", color = Color(0xFFF44336))
                    }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = periodsText,
                    onValueChange = { newVal ->
                        if (newVal.isEmpty() || newVal.all { it.isDigit() }) {
                            periodsText = newVal
                        }
                    },
                    label = { Text("Budget Periods (${periodLabel(budgetPeriod)})") },
                    singleLine = true,
                    isError = showValidation && !isPeriodsValid,
                    supportingText = if (showValidation && !isPeriodsValid) ({
                        Text("e.g. 12", color = Color(0xFFF44336))
                    }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (startDate != null) "Start Date: ${startDate}"
                        else "Select Start Date"
                    )
                }
                if (showValidation && !isDateValid) {
                    Text(
                        text = "Select a start date",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF44336)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) {
                        onSave(source.trim(), amount!!, periods!!, startDate!!)
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
            initialSelectedDateMillis = startDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        startDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
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
