package com.syncbudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.generateAmortizationEntryId
import com.syncbudget.app.ui.components.formatCurrency
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmortizationScreen(
    amortizationEntries: List<AmortizationEntry>,
    currencySymbol: String,
    budgetPeriod: BudgetPeriod,
    dateFormatPattern: String = "yyyy-MM-dd",
    onAddEntry: (AmortizationEntry) -> Unit,
    onUpdateEntry: (AmortizationEntry) -> Unit,
    onDeleteEntry: (AmortizationEntry) -> Unit,
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current
    val dateFormatter = remember(dateFormatPattern) { DateTimeFormatter.ofPattern(dateFormatPattern) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<AmortizationEntry?>(null) }
    var deletingEntry by remember { mutableStateOf<AmortizationEntry?>(null) }

    val allPaused = amortizationEntries.isNotEmpty() && amortizationEntries.all { it.isPaused }
    val anyActive = amortizationEntries.any { !it.isPaused }

    val periodLabelPlural = when (budgetPeriod) {
        BudgetPeriod.DAILY -> S.common.periodDays
        BudgetPeriod.WEEKLY -> S.common.periodWeeks
        BudgetPeriod.MONTHLY -> S.common.periodMonths
    }
    val periodLabelSingular = when (budgetPeriod) {
        BudgetPeriod.DAILY -> S.common.periodDay
        BudgetPeriod.WEEKLY -> S.common.periodWeek
        BudgetPeriod.MONTHLY -> S.common.periodMonth
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.amortization.title,
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
                        if (amortizationEntries.isNotEmpty()) {
                            IconButton(onClick = {
                                if (anyActive) {
                                    amortizationEntries.forEach { onUpdateEntry(it.copy(isPaused = true)) }
                                } else {
                                    amortizationEntries.forEach { onUpdateEntry(it.copy(isPaused = false)) }
                                }
                            }) {
                                Icon(
                                    imageVector = if (allPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = if (allPaused) S.amortization.resumeAll else S.amortization.pauseAll,
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
                    text = S.amortization.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(S.amortization.addEntry)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            items(amortizationEntries) { entry ->
                val elapsed = calculateElapsedPeriods(entry.startDate, budgetPeriod, entry.totalPeriods)
                val isCompleted = elapsed >= entry.totalPeriods
                val perPeriod = entry.amount / entry.totalPeriods
                val progress = if (entry.totalPeriods > 0) {
                    (elapsed.toFloat() / entry.totalPeriods).coerceIn(0f, 1f)
                } else 0f
                val amountPaid = perPeriod * elapsed
                val contentAlpha = if (entry.isPaused) 0.5f else if (isCompleted) 0.6f else 1f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingEntry = entry }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onUpdateEntry(entry.copy(isPaused = !entry.isPaused)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (entry.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (entry.isPaused) S.amortization.resume else S.amortization.pause,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha)
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            text = entry.source,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha)
                        )
                        Text(
                            text = S.amortization.totalPerPeriod(
                                currencySymbol,
                                "%.${CURRENCY_DECIMALS[currencySymbol] ?: 2}f".format(entry.amount),
                                "%.${CURRENCY_DECIMALS[currencySymbol] ?: 2}f".format(perPeriod),
                                periodLabelSingular
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f * contentAlpha)
                        )
                        if (isCompleted) {
                            Text(
                                text = S.amortization.completed,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        } else if (entry.isPaused) {
                            Text(
                                text = S.amortization.paused,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        } else {
                            Text(
                                text = S.amortization.xOfYComplete(elapsed, entry.totalPeriods, periodLabelPlural),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
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
                            text = "${formatCurrency(amountPaid, currencySymbol)} / ${formatCurrency(entry.amount, currencySymbol)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50).copy(alpha = contentAlpha)
                        )
                    }
                    IconButton(onClick = { deletingEntry = entry }) {
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
        AddEditAmortizationDialog(
            title = S.amortization.addEntry,
            initialSource = "",
            initialAmount = "",
            initialTotalPeriods = "",
            initialStartDate = null,
            currencySymbol = currencySymbol,
            budgetPeriod = budgetPeriod,
            dateFormatter = dateFormatter,
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
            title = S.amortization.editEntry,
            initialSource = entry.source,
            initialAmount = "%.${CURRENCY_DECIMALS[currencySymbol] ?: 2}f".format(entry.amount),
            initialTotalPeriods = entry.totalPeriods.toString(),
            initialStartDate = entry.startDate,
            currencySymbol = currencySymbol,
            budgetPeriod = budgetPeriod,
            dateFormatter = dateFormatter,
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
            title = { Text(S.amortization.deleteEntryTitle) },
            text = { Text(S.amortization.deleteEntryConfirm(entry.source)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteEntry(entry)
                    deletingEntry = null
                }) {
                    Text(S.common.delete, color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) { Text(S.common.cancel) }
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
    currencySymbol: String,
    budgetPeriod: BudgetPeriod,
    dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    onDismiss: () -> Unit,
    onSave: (String, Double, Int, LocalDate) -> Unit
) {
    val S = LocalStrings.current

    var source by remember { mutableStateOf(initialSource) }
    var amountText by remember { mutableStateOf(initialAmount) }
    var periodsText by remember { mutableStateOf(initialTotalPeriods) }
    var startDate by remember { mutableStateOf(initialStartDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }
    val maxDecimalPlaces = CURRENCY_DECIMALS[currencySymbol] ?: 2

    val amount = amountText.toDoubleOrNull()
    val periods = periodsText.toIntOrNull()
    val isSourceValid = source.isNotBlank()
    val isAmountValid = amount != null && amount > 0
    val isPeriodsValid = periods != null && periods > 0
    val isDateValid = startDate != null
    val isValid = isSourceValid && isAmountValid && isPeriodsValid && isDateValid

    val periodLabelPlural = when (budgetPeriod) {
        BudgetPeriod.DAILY -> S.common.periodDays
        BudgetPeriod.WEEKLY -> S.common.periodWeeks
        BudgetPeriod.MONTHLY -> S.common.periodMonths
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        label = { Text(S.amortization.sourceName) },
                        singleLine = true,
                        isError = showValidation && !isSourceValid,
                        supportingText = if (showValidation && !isSourceValid) ({
                            Text(S.amortization.requiredLaptopExample, color = Color(0xFFF44336))
                        }) else null,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { newVal ->
                            if (newVal.isEmpty() || newVal == "." || newVal.toDoubleOrNull() != null) {
                                val dotIdx = newVal.indexOf('.')
                                val decs = if (dotIdx >= 0) newVal.length - dotIdx - 1 else 0
                                if (maxDecimalPlaces == 0 && dotIdx >= 0) { /* block */ }
                                else if (decs <= maxDecimalPlaces) { amountText = newVal }
                            }
                        },
                        label = { Text(S.amortization.totalAmount) },
                        singleLine = true,
                        isError = showValidation && !isAmountValid,
                        supportingText = if (showValidation && !isAmountValid) ({
                            Text(S.amortization.exampleTotalAmount, color = Color(0xFFF44336))
                        }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = if (maxDecimalPlaces > 0) KeyboardType.Decimal else KeyboardType.Number),
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
                        label = { Text(S.amortization.budgetPeriods(periodLabelPlural)) },
                        singleLine = true,
                        isError = showValidation && !isPeriodsValid,
                        supportingText = if (showValidation && !isPeriodsValid) ({
                            Text(S.amortization.examplePeriods, color = Color(0xFFF44336))
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
                            if (startDate != null) S.amortization.startDateLabel(startDate!!.format(dateFormatter))
                            else S.amortization.selectStartDate
                        )
                    }
                    if (showValidation && !isDateValid) {
                        Text(
                            text = S.amortization.selectAStartDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF44336)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(S.common.cancel) }
                    TextButton(
                        onClick = {
                            if (isValid) {
                                onSave(source.trim(), amount!!, periods!!, startDate!!)
                            } else {
                                showValidation = true
                            }
                        }
                    ) {
                        Text(S.common.save)
                    }
                }
            }
        }
    }

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
