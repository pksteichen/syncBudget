package com.syncbudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RepeatType
import com.syncbudget.app.data.generateIncomeSourceId
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val REPEAT_TYPE_LABELS = mapOf(
    RepeatType.DAYS to "Every X Days",
    RepeatType.WEEKS to "Every X Weeks",
    RepeatType.BI_WEEKLY to "Every 2 Weeks",
    RepeatType.MONTHS to "Every X Months",
    RepeatType.BI_MONTHLY to "Twice per Month"
)

private val BUDGET_PERIOD_LABELS = mapOf(
    BudgetPeriod.DAILY to "Daily",
    BudgetPeriod.WEEKLY to "Weekly",
    BudgetPeriod.MONTHLY to "Monthly"
)

private val HOUR_LABELS = (0..23).map { hour ->
    when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}

private val DAY_OF_WEEK_ORDER = listOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetConfigScreen(
    incomeSources: List<IncomeSource>,
    currencySymbol: String,
    onAddIncomeSource: (IncomeSource) -> Unit,
    onUpdateIncomeSource: (IncomeSource) -> Unit,
    onDeleteIncomeSource: (IncomeSource) -> Unit,
    budgetPeriod: BudgetPeriod,
    onBudgetPeriodChange: (BudgetPeriod) -> Unit,
    resetHour: Int,
    onResetHourChange: (Int) -> Unit,
    resetDayOfWeek: Int,
    onResetDayOfWeekChange: (Int) -> Unit,
    resetDayOfMonth: Int,
    onResetDayOfMonthChange: (Int) -> Unit,
    safeBudgetAmount: Double = 0.0,
    isManualBudgetEnabled: Boolean = false,
    manualBudgetAmount: Double = 0.0,
    onManualBudgetToggle: (Boolean) -> Unit = {},
    onManualBudgetAmountChange: (Double) -> Unit = {},
    onRecalculate: () -> Unit = {},
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<IncomeSource?>(null) }
    var deletingSource by remember { mutableStateOf<IncomeSource?>(null) }
    var repeatSettingsSource by remember { mutableStateOf<IncomeSource?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var periodExpanded by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Budget Configuration",
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = periodExpanded,
                        onExpandedChange = { periodExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = BUDGET_PERIOD_LABELS[budgetPeriod] ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Budget Period") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodExpanded) },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = periodExpanded,
                            onDismissRequest = { periodExpanded = false }
                        ) {
                            BudgetPeriod.entries.forEach { period ->
                                DropdownMenuItem(
                                    text = { Text(BUDGET_PERIOD_LABELS[period] ?: period.name) },
                                    onClick = {
                                        onBudgetPeriodChange(period)
                                        periodExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(onClick = { showResetDialog = true }) {
                        Text("Reset")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                val periodLabel = when (budgetPeriod) {
                    BudgetPeriod.DAILY -> "day"
                    BudgetPeriod.WEEKLY -> "week"
                    BudgetPeriod.MONTHLY -> "month"
                }

                Text(
                    text = "Safe Budget Amount: $currencySymbol${"%.2f".format(safeBudgetAmount)}/$periodLabel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRecalculate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Recalculate")
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isManualBudgetEnabled,
                        onCheckedChange = onManualBudgetToggle
                    )
                    Text(
                        text = "Manual Budget Override",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (isManualBudgetEnabled) {
                    var manualAmountText by remember(manualBudgetAmount) {
                        mutableStateOf(if (manualBudgetAmount == 0.0) "" else "%.2f".format(manualBudgetAmount))
                    }
                    OutlinedTextField(
                        value = manualAmountText,
                        onValueChange = { text ->
                            manualAmountText = text
                            text.toDoubleOrNull()?.let { onManualBudgetAmountChange(it) }
                        },
                        label = { Text("Budget Amount per $periodLabel") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Manual override disables Amortization and FLE deductions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Text(
                    text = "Add sources of consistent income that you can rely on for budgeting. If your pay varies (large check, small check), you can make more than one entry for a source.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Add Income Source")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            items(incomeSources) { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingSource = source }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.source,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$currencySymbol${"%.2f".format(source.amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = { repeatSettingsSource = source }) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = "Repeat Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { deletingSource = source }) {
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
        AddEditIncomeDialog(
            title = "Add Income Source",
            initialSource = "",
            initialAmount = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, amount ->
                val id = generateIncomeSourceId(incomeSources.map { it.id }.toSet())
                onAddIncomeSource(IncomeSource(id = id, source = name, amount = amount))
                showAddDialog = false
            }
        )
    }

    editingSource?.let { source ->
        AddEditIncomeDialog(
            title = "Edit Income Source",
            initialSource = source.source,
            initialAmount = source.amount.toString(),
            onDismiss = { editingSource = null },
            onSave = { name, amount ->
                onUpdateIncomeSource(source.copy(source = name, amount = amount))
                editingSource = null
            }
        )
    }

    deletingSource?.let { source ->
        AlertDialog(
            onDismissRequest = { deletingSource = null },
            title = { Text("Delete ${source.source}?") },
            text = { Text("This income source will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteIncomeSource(source)
                    deletingSource = null
                }) {
                    Text("Delete", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSource = null }) { Text("Cancel") }
            }
        )
    }

    repeatSettingsSource?.let { source ->
        RepeatSettingsDialog(
            source = source,
            onDismiss = { repeatSettingsSource = null },
            onSave = { updated ->
                onUpdateIncomeSource(updated)
                repeatSettingsSource = null
            }
        )
    }

    if (showResetDialog) {
        BudgetResetDialog(
            budgetPeriod = budgetPeriod,
            resetHour = resetHour,
            resetDayOfWeek = resetDayOfWeek,
            resetDayOfMonth = resetDayOfMonth,
            onDismiss = { showResetDialog = false },
            onSave = { hour, dayOfWeek, dayOfMonth ->
                onResetHourChange(hour)
                onResetDayOfWeekChange(dayOfWeek)
                onResetDayOfMonthChange(dayOfMonth)
                showResetDialog = false
            }
        )
    }
}

@Composable
private fun AddEditIncomeDialog(
    title: String,
    initialSource: String,
    initialAmount: String,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var sourceName by remember { mutableStateOf(initialSource) }
    var amountText by remember { mutableStateOf(if (initialAmount == "0.0") "" else initialAmount) }

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
                    value = sourceName,
                    onValueChange = { sourceName = it },
                    label = { Text("Source Name") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (sourceName.isNotBlank() && amount != null && amount > 0) {
                        onSave(sourceName.trim(), amount)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatSettingsDialog(
    source: IncomeSource,
    onDismiss: () -> Unit,
    onSave: (IncomeSource) -> Unit
) {
    var repeatType by remember { mutableStateOf(source.repeatType) }
    var intervalText by remember { mutableStateOf(source.repeatInterval.toString()) }
    var startDate by remember { mutableStateOf(source.startDate) }
    var monthDay1Text by remember { mutableStateOf(source.monthDay1?.toString() ?: "") }
    var monthDay2Text by remember { mutableStateOf(source.monthDay2?.toString() ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    val interval = intervalText.toIntOrNull()
    val monthDay1 = monthDay1Text.toIntOrNull()
    val monthDay2 = monthDay2Text.toIntOrNull()

    val isValid = when (repeatType) {
        RepeatType.DAYS -> interval != null && interval in 1..60 && startDate != null
        RepeatType.WEEKS -> interval != null && interval in 1..18 && startDate != null
        RepeatType.BI_WEEKLY -> startDate != null
        RepeatType.MONTHS -> interval != null && interval in 1..3 && monthDay1 != null && monthDay1 in 1..28
        RepeatType.BI_MONTHLY -> monthDay1 != null && monthDay1 in 1..28 && monthDay2 != null && monthDay2 in 1..28
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = REPEAT_TYPE_LABELS[repeatType] ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repeat Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        RepeatType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(REPEAT_TYPE_LABELS[type] ?: type.name) },
                                onClick = {
                                    repeatType = type
                                    typeExpanded = false
                                    when (type) {
                                        RepeatType.DAYS -> { intervalText = "1"; monthDay1Text = ""; monthDay2Text = "" }
                                        RepeatType.WEEKS -> { intervalText = "1"; monthDay1Text = ""; monthDay2Text = "" }
                                        RepeatType.BI_WEEKLY -> { monthDay1Text = ""; monthDay2Text = "" }
                                        RepeatType.MONTHS -> { intervalText = "1"; startDate = null; monthDay2Text = "" }
                                        RepeatType.BI_MONTHLY -> { intervalText = "1"; startDate = null }
                                    }
                                }
                            )
                        }
                    }
                }

                when (repeatType) {
                    RepeatType.DAYS -> {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it },
                            label = { Text("Every X Days (1-60)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (startDate != null) "Start Date: $startDate" else "Pick Start Date")
                        }
                    }
                    RepeatType.WEEKS -> {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it },
                            label = { Text("Interval (1-18)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (startDate != null) "Start Date: $startDate" else "Pick Start Date")
                        }
                        if (startDate != null) {
                            val dayName = startDate!!.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                            Text(
                                text = "Day of week: $dayName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                    RepeatType.BI_WEEKLY -> {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (startDate != null) "Start Date: $startDate" else "Pick Start Date")
                        }
                        if (startDate != null) {
                            val dayName = startDate!!.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                            Text(
                                text = "Day of week: $dayName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                    RepeatType.MONTHS -> {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it },
                            label = { Text("Every X Months (1-3)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = monthDay1Text,
                            onValueChange = { monthDay1Text = it },
                            label = { Text("Day of Month (1-28)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RepeatType.BI_MONTHLY -> {
                        OutlinedTextField(
                            value = monthDay1Text,
                            onValueChange = { monthDay1Text = it },
                            label = { Text("First Day of Month (1-28)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = monthDay2Text,
                            onValueChange = { monthDay2Text = it },
                            label = { Text("Second Day of Month (1-28)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) {
                        val updated = when (repeatType) {
                            RepeatType.DAYS -> source.copy(
                                repeatType = repeatType,
                                repeatInterval = interval!!,
                                startDate = startDate,
                                monthDay1 = null,
                                monthDay2 = null
                            )
                            RepeatType.WEEKS -> source.copy(
                                repeatType = repeatType,
                                repeatInterval = interval!!,
                                startDate = startDate,
                                monthDay1 = null,
                                monthDay2 = null
                            )
                            RepeatType.BI_WEEKLY -> source.copy(
                                repeatType = repeatType,
                                repeatInterval = 1,
                                startDate = startDate,
                                monthDay1 = null,
                                monthDay2 = null
                            )
                            RepeatType.MONTHS -> source.copy(
                                repeatType = repeatType,
                                repeatInterval = interval!!,
                                startDate = null,
                                monthDay1 = monthDay1,
                                monthDay2 = null
                            )
                            RepeatType.BI_MONTHLY -> source.copy(
                                repeatType = repeatType,
                                repeatInterval = 1,
                                startDate = null,
                                monthDay1 = monthDay1,
                                monthDay2 = monthDay2
                            )
                        }
                        onSave(updated)
                    }
                },
                enabled = isValid
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
            initialSelectedDateMillis = startDate?.let {
                it.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        startDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetResetDialog(
    budgetPeriod: BudgetPeriod,
    resetHour: Int,
    resetDayOfWeek: Int,
    resetDayOfMonth: Int,
    onDismiss: () -> Unit,
    onSave: (hour: Int, dayOfWeek: Int, dayOfMonth: Int) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(resetHour) }
    var selectedDayOfWeek by remember { mutableIntStateOf(resetDayOfWeek) }
    var dayOfMonthText by remember { mutableStateOf(resetDayOfMonth.toString()) }
    var hourExpanded by remember { mutableStateOf(false) }
    var dayOfWeekExpanded by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    val dayOfMonth = dayOfMonthText.toIntOrNull()
    val isValid = when (budgetPeriod) {
        BudgetPeriod.DAILY -> true
        BudgetPeriod.WEEKLY -> true
        BudgetPeriod.MONTHLY -> dayOfMonth != null && dayOfMonth in 1..28
    }

    val selectedDayOfWeekName = DayOfWeek.of(selectedDayOfWeek)
        .getDisplayName(TextStyle.FULL, Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget Reset Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (budgetPeriod == BudgetPeriod.WEEKLY) {
                    ExposedDropdownMenuBox(
                        expanded = dayOfWeekExpanded,
                        onExpandedChange = { dayOfWeekExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedDayOfWeekName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Day of Week") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayOfWeekExpanded) },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = dayOfWeekExpanded,
                            onDismissRequest = { dayOfWeekExpanded = false }
                        ) {
                            DAY_OF_WEEK_ORDER.forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                                    onClick = {
                                        selectedDayOfWeek = day.value
                                        dayOfWeekExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (budgetPeriod == BudgetPeriod.MONTHLY) {
                    OutlinedTextField(
                        value = dayOfMonthText,
                        onValueChange = { dayOfMonthText = it },
                        label = { Text("Day of Month (1-28)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = hourExpanded,
                    onExpandedChange = { hourExpanded = it }
                ) {
                    OutlinedTextField(
                        value = HOUR_LABELS[selectedHour],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Reset Hour") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hourExpanded) },
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = hourExpanded,
                        onDismissRequest = { hourExpanded = false }
                    ) {
                        HOUR_LABELS.forEachIndexed { index, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedHour = index
                                    hourExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) {
                        onSave(selectedHour, selectedDayOfWeek, dayOfMonth ?: resetDayOfMonth)
                    }
                },
                enabled = isValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
