package com.syncbudget.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RepeatType
import com.syncbudget.app.data.generateIncomeSourceId
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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
    onResetBudget: () -> Unit = {},
    budgetStartDate: String? = null,
    dateFormatPattern: String = "yyyy-MM-dd",
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current
    val dateFormatter = remember(dateFormatPattern) { DateTimeFormatter.ofPattern(dateFormatPattern) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<IncomeSource?>(null) }
    var deletingSource by remember { mutableStateOf<IncomeSource?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showResetBudgetConfirm by remember { mutableStateOf(false) }
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
                        text = S.budgetConfig.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = S.common.back,
                            tint = customColors.headerText
                        )
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
                            value = when (budgetPeriod) {
                                BudgetPeriod.DAILY -> S.common.budgetPeriodDaily
                                BudgetPeriod.WEEKLY -> S.common.budgetPeriodWeekly
                                BudgetPeriod.MONTHLY -> S.common.budgetPeriodMonthly
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S.budgetConfig.budgetPeriod) },
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
                                    text = {
                                        Text(when (period) {
                                            BudgetPeriod.DAILY -> S.common.budgetPeriodDaily
                                            BudgetPeriod.WEEKLY -> S.common.budgetPeriodWeekly
                                            BudgetPeriod.MONTHLY -> S.common.budgetPeriodMonthly
                                        })
                                    },
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
                        Text(S.common.reset)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                val periodLabel = when (budgetPeriod) {
                    BudgetPeriod.DAILY -> S.common.periodDay
                    BudgetPeriod.WEEKLY -> S.common.periodWeek
                    BudgetPeriod.MONTHLY -> S.common.periodMonth
                }

                Text(
                    text = S.budgetConfig.safeBudgetAmountLabel(currencySymbol, "%.2f".format(safeBudgetAmount), periodLabel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (budgetStartDate != null) {
                    Text(
                        text = S.budgetConfig.budgetTrackingSince(budgetStartDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRecalculate,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(S.budgetConfig.recalculate)
                    }
                    OutlinedButton(
                        onClick = { showResetBudgetConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(S.budgetConfig.resetBudget)
                    }
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
                        text = S.budgetConfig.manualBudgetOverride,
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
                        label = { Text(S.budgetConfig.budgetAmountPer(periodLabel)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = S.budgetConfig.manualOverrideWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Text(
                    text = S.budgetConfig.incomeSourceDescription,
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
                    Text(S.budgetConfig.addIncomeSource)
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
                    IconButton(onClick = { deletingSource = source }) {
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
        AddEditIncomeDialog(
            existingSource = null,
            dateFormatter = dateFormatter,
            onDismiss = { showAddDialog = false },
            onSave = { incomeSource ->
                val id = generateIncomeSourceId(incomeSources.map { it.id }.toSet())
                onAddIncomeSource(incomeSource.copy(id = id))
                showAddDialog = false
            }
        )
    }

    editingSource?.let { source ->
        AddEditIncomeDialog(
            existingSource = source,
            dateFormatter = dateFormatter,
            onDismiss = { editingSource = null },
            onSave = { updated ->
                onUpdateIncomeSource(updated)
                editingSource = null
            }
        )
    }

    deletingSource?.let { source ->
        AlertDialog(
            onDismissRequest = { deletingSource = null },
            title = { Text(S.budgetConfig.deleteSourceConfirmTitle(source.source)) },
            text = { Text(S.budgetConfig.deleteSourceConfirmBody) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteIncomeSource(source)
                    deletingSource = null
                }) {
                    Text(S.common.delete, color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSource = null }) { Text(S.common.cancel) }
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

    if (showResetBudgetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetBudgetConfirm = false },
            title = { Text(S.budgetConfig.resetBudgetConfirmTitle) },
            text = {
                Text(S.budgetConfig.resetBudgetConfirmBody)
            },
            confirmButton = {
                TextButton(onClick = {
                    onResetBudget()
                    showResetBudgetConfirm = false
                }) {
                    Text(S.common.reset, color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetBudgetConfirm = false }) { Text(S.common.cancel) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditIncomeDialog(
    existingSource: IncomeSource?,
    dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    onDismiss: () -> Unit,
    onSave: (IncomeSource) -> Unit
) {
    val S = LocalStrings.current
    val isEdit = existingSource != null
    val title = if (isEdit) S.budgetConfig.editIncomeSource else S.budgetConfig.addIncomeSource

    var sourceName by remember { mutableStateOf(existingSource?.source ?: "") }
    var amountText by remember {
        mutableStateOf(
            if (existingSource != null && existingSource.amount > 0.0)
                "%.2f".format(existingSource.amount)
            else ""
        )
    }
    var repeatType by remember { mutableStateOf(existingSource?.repeatType ?: RepeatType.MONTHS) }
    var intervalText by remember { mutableStateOf(existingSource?.repeatInterval?.toString() ?: "1") }
    var startDate by remember { mutableStateOf(existingSource?.startDate) }
    var monthDay1Text by remember { mutableStateOf(existingSource?.monthDay1?.toString() ?: "") }
    var monthDay2Text by remember { mutableStateOf(existingSource?.monthDay2?.toString() ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    val amount = amountText.toDoubleOrNull()
    val interval = intervalText.toIntOrNull()
    val monthDay1 = monthDay1Text.toIntOrNull()
    val monthDay2 = monthDay2Text.toIntOrNull()

    val isSourceValid = sourceName.isNotBlank()
    val isAmountValid = amount != null && amount > 0

    val isRepeatValid = when (repeatType) {
        RepeatType.DAYS -> interval != null && interval in 1..60 && startDate != null
        RepeatType.WEEKS -> interval != null && interval in 1..18 && startDate != null
        RepeatType.BI_WEEKLY -> startDate != null
        RepeatType.MONTHS -> interval != null && interval in 1..3 && monthDay1 != null && monthDay1 in 1..28
        RepeatType.BI_MONTHLY -> monthDay1 != null && monthDay1 in 1..28 && monthDay2 != null && monthDay2 in 1..28
    }

    val isValid = isSourceValid && isAmountValid && isRepeatValid

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        label = { Text(S.common.sourceName) },
                        singleLine = true,
                        isError = showValidation && !isSourceValid,
                        supportingText = if (showValidation && !isSourceValid) ({
                            Text(S.budgetConfig.requiredPaycheckExample, color = Color(0xFFF44336))
                        }) else null,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(S.common.amount) },
                        singleLine = true,
                        isError = showValidation && !isAmountValid,
                        supportingText = if (showValidation && !isAmountValid) ({
                            Text(S.budgetConfig.exampleIncomeAmount, color = Color(0xFFF44336))
                        }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()

                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = when (repeatType) {
                                RepeatType.DAYS -> S.common.repeatTypeDays
                                RepeatType.WEEKS -> S.common.repeatTypeWeeks
                                RepeatType.BI_WEEKLY -> S.common.repeatTypeBiWeekly
                                RepeatType.MONTHS -> S.common.repeatTypeMonths
                                RepeatType.BI_MONTHLY -> S.common.repeatTypeBiMonthly
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S.common.repeatType) },
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
                                    text = {
                                        Text(when (type) {
                                            RepeatType.DAYS -> S.common.repeatTypeDays
                                            RepeatType.WEEKS -> S.common.repeatTypeWeeks
                                            RepeatType.BI_WEEKLY -> S.common.repeatTypeBiWeekly
                                            RepeatType.MONTHS -> S.common.repeatTypeMonths
                                            RepeatType.BI_MONTHLY -> S.common.repeatTypeBiMonthly
                                        })
                                    },
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
                                label = { Text(S.common.everyXDays) },
                                singleLine = true,
                                isError = showValidation && (interval == null || interval !in 1..60),
                                supportingText = if (showValidation && (interval == null || interval !in 1..60)) ({
                                    Text(S.common.exampleDays, color = Color(0xFFF44336))
                                }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = if (showValidation && startDate == null)
                                    Modifier.border(1.dp, Color.Red, RoundedCornerShape(4.dp))
                                else Modifier
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (startDate != null) S.common.startDateLabel(startDate!!.format(dateFormatter)) else S.common.pickStartDate)
                                }
                            }
                            if (showValidation && startDate == null) {
                                Text(S.common.selectAStartDate, style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336))
                            }
                        }
                        RepeatType.WEEKS -> {
                            OutlinedTextField(
                                value = intervalText,
                                onValueChange = { intervalText = it },
                                label = { Text(S.common.intervalWeeks) },
                                singleLine = true,
                                isError = showValidation && (interval == null || interval !in 1..18),
                                supportingText = if (showValidation && (interval == null || interval !in 1..18)) ({
                                    Text(S.common.exampleWeeks, color = Color(0xFFF44336))
                                }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = if (showValidation && startDate == null)
                                    Modifier.border(1.dp, Color.Red, RoundedCornerShape(4.dp))
                                else Modifier
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (startDate != null) S.common.startDateLabel(startDate!!.format(dateFormatter)) else S.common.pickStartDate)
                                }
                            }
                            if (showValidation && startDate == null) {
                                Text(S.common.selectAStartDate, style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336))
                            }
                            if (startDate != null) {
                                val dayName = startDate!!.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                                Text(
                                    text = S.common.dayOfWeekLabel(dayName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                            }
                        }
                        RepeatType.BI_WEEKLY -> {
                            Box(
                                modifier = if (showValidation && startDate == null)
                                    Modifier.border(1.dp, Color.Red, RoundedCornerShape(4.dp))
                                else Modifier
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (startDate != null) S.common.startDateLabel(startDate!!.format(dateFormatter)) else S.common.pickStartDate)
                                }
                            }
                            if (showValidation && startDate == null) {
                                Text(S.common.selectAStartDate, style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336))
                            }
                            if (startDate != null) {
                                val dayName = startDate!!.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                                Text(
                                    text = S.common.dayOfWeekLabel(dayName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                            }
                        }
                        RepeatType.MONTHS -> {
                            OutlinedTextField(
                                value = intervalText,
                                onValueChange = { intervalText = it },
                                label = { Text(S.common.everyXMonths) },
                                singleLine = true,
                                isError = showValidation && (interval == null || interval !in 1..3),
                                supportingText = if (showValidation && (interval == null || interval !in 1..3)) ({
                                    Text(S.common.exampleMonths, color = Color(0xFFF44336))
                                }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = monthDay1Text,
                                onValueChange = { monthDay1Text = it },
                                label = { Text(S.common.dayOfMonth) },
                                singleLine = true,
                                isError = showValidation && (monthDay1 == null || monthDay1 !in 1..28),
                                supportingText = if (showValidation && (monthDay1 == null || monthDay1 !in 1..28)) ({
                                    Text(S.common.exampleMonthDay, color = Color(0xFFF44336))
                                }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        RepeatType.BI_MONTHLY -> {
                            OutlinedTextField(
                                value = monthDay1Text,
                                onValueChange = { monthDay1Text = it },
                                label = { Text(S.common.firstDayOfMonth) },
                                singleLine = true,
                                isError = showValidation && (monthDay1 == null || monthDay1 !in 1..28),
                                supportingText = if (showValidation && (monthDay1 == null || monthDay1 !in 1..28)) ({
                                    Text(S.common.exampleBiMonthlyDay1, color = Color(0xFFF44336))
                                }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = monthDay2Text,
                                onValueChange = { monthDay2Text = it },
                                label = { Text(S.common.secondDayOfMonth) },
                                singleLine = true,
                                isError = showValidation && (monthDay2 == null || monthDay2 !in 1..28),
                                supportingText = if (showValidation && (monthDay2 == null || monthDay2 !in 1..28)) ({
                                    Text(S.common.exampleBiMonthlyDay2, color = Color(0xFFF44336))
                                }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                                val result = when (repeatType) {
                                    RepeatType.DAYS -> IncomeSource(
                                        id = existingSource?.id ?: 0,
                                        source = sourceName.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = interval!!,
                                        startDate = startDate,
                                        monthDay1 = null,
                                        monthDay2 = null
                                    )
                                    RepeatType.WEEKS -> IncomeSource(
                                        id = existingSource?.id ?: 0,
                                        source = sourceName.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = interval!!,
                                        startDate = startDate,
                                        monthDay1 = null,
                                        monthDay2 = null
                                    )
                                    RepeatType.BI_WEEKLY -> IncomeSource(
                                        id = existingSource?.id ?: 0,
                                        source = sourceName.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = 1,
                                        startDate = startDate,
                                        monthDay1 = null,
                                        monthDay2 = null
                                    )
                                    RepeatType.MONTHS -> IncomeSource(
                                        id = existingSource?.id ?: 0,
                                        source = sourceName.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = interval!!,
                                        startDate = null,
                                        monthDay1 = monthDay1,
                                        monthDay2 = null
                                    )
                                    RepeatType.BI_MONTHLY -> IncomeSource(
                                        id = existingSource?.id ?: 0,
                                        source = sourceName.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = 1,
                                        startDate = null,
                                        monthDay1 = monthDay1,
                                        monthDay2 = monthDay2
                                    )
                                }
                                onSave(result)
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
                    Text(S.common.ok)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(S.common.cancel)
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
    val S = LocalStrings.current
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
        title = { Text(S.budgetConfig.resetSettingsTitle) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (budgetPeriod == BudgetPeriod.WEEKLY) {
                    ExposedDropdownMenuBox(
                        expanded = dayOfWeekExpanded,
                        onExpandedChange = { dayOfWeekExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedDayOfWeekName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S.budgetConfig.dayOfWeekLabel) },
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
                        label = { Text(S.budgetConfig.dayOfMonthReset) },
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
                        label = { Text(S.budgetConfig.resetHour) },
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
                Text(S.common.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S.common.cancel) }
        }
    )
}
