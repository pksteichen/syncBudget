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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
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
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.RepeatType
import com.syncbudget.app.data.generateRecurringExpenseId
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpensesScreen(
    recurringExpenses: List<RecurringExpense>,
    currencySymbol: String,
    onAddRecurringExpense: (RecurringExpense) -> Unit,
    onUpdateRecurringExpense: (RecurringExpense) -> Unit,
    onDeleteRecurringExpense: (RecurringExpense) -> Unit,
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<RecurringExpense?>(null) }
    var deletingExpense by remember { mutableStateOf<RecurringExpense?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Recurring Expenses",
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
                Text(
                    text = "Enter recurring expenses like rent, insurance, subscriptions, and loan payments. " +
                        "These will be used to calculate your cash flow and to identify bank transactions that " +
                        "should not count against your budget because they are already factored in. " +
                        "Use descriptive terms in Source Name \u2014 they will be matched against bank transaction " +
                        "merchant names to automatically identify recurring transactions.",
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
                    Text("Add Recurring Expense")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            items(recurringExpenses) { expense ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingExpense = expense }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = expense.source,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$currencySymbol${"%.2f".format(expense.amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = { editingExpense = expense }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { deletingExpense = expense }) {
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
        AddEditExpenseDialog(
            existingExpense = null,
            onDismiss = { showAddDialog = false },
            onSave = { expense ->
                val id = generateRecurringExpenseId(recurringExpenses.map { it.id }.toSet())
                onAddRecurringExpense(expense.copy(id = id))
                showAddDialog = false
            }
        )
    }

    editingExpense?.let { expense ->
        AddEditExpenseDialog(
            existingExpense = expense,
            onDismiss = { editingExpense = null },
            onSave = { updated ->
                onUpdateRecurringExpense(updated)
                editingExpense = null
            }
        )
    }

    deletingExpense?.let { expense ->
        AlertDialog(
            onDismissRequest = { deletingExpense = null },
            title = { Text("Delete ${expense.source}?") },
            text = { Text("This recurring expense will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecurringExpense(expense)
                    deletingExpense = null
                }) {
                    Text("Delete", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingExpense = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditExpenseDialog(
    existingExpense: RecurringExpense?,
    onDismiss: () -> Unit,
    onSave: (RecurringExpense) -> Unit
) {
    val isEdit = existingExpense != null
    val title = if (isEdit) "Edit Recurring Expense" else "Add Recurring Expense"

    var sourceName by remember { mutableStateOf(existingExpense?.source ?: "") }
    var amountText by remember {
        mutableStateOf(
            if (existingExpense != null && existingExpense.amount > 0.0)
                existingExpense.amount.toString()
            else ""
        )
    }
    var repeatType by remember { mutableStateOf(existingExpense?.repeatType ?: RepeatType.MONTHS) }
    var intervalText by remember { mutableStateOf(existingExpense?.repeatInterval?.toString() ?: "1") }
    var startDate by remember { mutableStateOf(existingExpense?.startDate) }
    var monthDay1Text by remember { mutableStateOf(existingExpense?.monthDay1?.toString() ?: "") }
    var monthDay2Text by remember { mutableStateOf(existingExpense?.monthDay2?.toString() ?: "") }
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
                        label = { Text("Source Name") },
                        singleLine = true,
                        isError = showValidation && !isSourceValid,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        isError = showValidation && !isAmountValid,
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
                                isError = showValidation && (interval == null || interval !in 1..60),
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
                                    Text(if (startDate != null) "Start Date: $startDate" else "Pick Start Date")
                                }
                            }
                        }
                        RepeatType.WEEKS -> {
                            OutlinedTextField(
                                value = intervalText,
                                onValueChange = { intervalText = it },
                                label = { Text("Interval (1-18)") },
                                singleLine = true,
                                isError = showValidation && (interval == null || interval !in 1..18),
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
                                    Text(if (startDate != null) "Start Date: $startDate" else "Pick Start Date")
                                }
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
                            Box(
                                modifier = if (showValidation && startDate == null)
                                    Modifier.border(1.dp, Color.Red, RoundedCornerShape(4.dp))
                                else Modifier
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (startDate != null) "Start Date: $startDate" else "Pick Start Date")
                                }
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
                                isError = showValidation && (interval == null || interval !in 1..3),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = monthDay1Text,
                                onValueChange = { monthDay1Text = it },
                                label = { Text("Day of Month (1-28)") },
                                singleLine = true,
                                isError = showValidation && (monthDay1 == null || monthDay1 !in 1..28),
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
                                isError = showValidation && (monthDay1 == null || monthDay1 !in 1..28),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = monthDay2Text,
                                onValueChange = { monthDay2Text = it },
                                label = { Text("Second Day of Month (1-28)") },
                                singleLine = true,
                                isError = showValidation && (monthDay2 == null || monthDay2 !in 1..28),
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
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            if (isValid) {
                                val result = when (repeatType) {
                                    RepeatType.DAYS -> RecurringExpense(
                                        id = existingExpense?.id ?: 0,
                                        source = sourceName.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = interval!!,
                                        startDate = startDate,
                                        monthDay1 = null,
                                        monthDay2 = null
                                    )
                                    RepeatType.WEEKS -> RecurringExpense(
                                        id = existingExpense?.id ?: 0,
                                        source = sourceName.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = interval!!,
                                        startDate = startDate,
                                        monthDay1 = null,
                                        monthDay2 = null
                                    )
                                    RepeatType.BI_WEEKLY -> RecurringExpense(
                                        id = existingExpense?.id ?: 0,
                                        source = sourceName.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = 1,
                                        startDate = startDate,
                                        monthDay1 = null,
                                        monthDay2 = null
                                    )
                                    RepeatType.MONTHS -> RecurringExpense(
                                        id = existingExpense?.id ?: 0,
                                        source = sourceName.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = interval!!,
                                        startDate = null,
                                        monthDay1 = monthDay1,
                                        monthDay2 = null
                                    )
                                    RepeatType.BI_MONTHLY -> RecurringExpense(
                                        id = existingExpense?.id ?: 0,
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
                        Text("Save")
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
