package com.syncbudget.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import com.syncbudget.app.ui.theme.AdAwareAlertDialog
import com.syncbudget.app.ui.theme.DialogStyle
import com.syncbudget.app.ui.theme.DialogPrimaryButton
import com.syncbudget.app.ui.theme.DialogSecondaryButton
import com.syncbudget.app.ui.theme.DialogDangerButton
import com.syncbudget.app.ui.theme.DialogHeader
import com.syncbudget.app.ui.theme.DialogFooter
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncbudget.app.ui.theme.AdAwareDialog
import com.syncbudget.app.data.BudgetCalculator
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsSimulator
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.RepeatType
import com.syncbudget.app.data.generateRecurringExpenseId
import com.syncbudget.app.ui.components.formatCurrency
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import com.syncbudget.app.ui.theme.PulsingScrollArrow
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpensesScreen(
    recurringExpenses: List<RecurringExpense>,
    currencySymbol: String,
    dateFormatPattern: String = "yyyy-MM-dd",
    onAddRecurringExpense: (RecurringExpense) -> Unit,
    onUpdateRecurringExpense: (RecurringExpense) -> Unit,
    onDeleteRecurringExpense: (RecurringExpense) -> Unit,
    transactions: List<Transaction> = emptyList(),
    incomeSources: List<IncomeSource> = emptyList(),
    budgetPeriod: BudgetPeriod = BudgetPeriod.DAILY,
    budgetAmount: Double = 0.0,
    availableCash: Double = 0.0,
    resetDayOfWeek: Int = 7,
    resetDayOfMonth: Int = 1,
    isManualOverBudget: Boolean = false,
    budgetPeriodLabel: String = "",
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val S = LocalStrings.current
    val customColors = LocalSyncBudgetColors.current
    val context = LocalContext.current
    val dateFormatter = remember(dateFormatPattern) { DateTimeFormatter.ofPattern(dateFormatPattern) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<RecurringExpense?>(null) }
    var deletingExpense by remember { mutableStateOf<RecurringExpense?>(null) }
    var linkedTransactionsExpense by remember { mutableStateOf<RecurringExpense?>(null) }
    var showSavingsWhyDialog by remember { mutableStateOf(false) }
    val appPrefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var sortByAlpha by remember { mutableStateOf(appPrefs.getBoolean("recurringExpenseSortAlpha", false)) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.recurringExpenses.title,
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
                Text(
                    text = S.recurringExpenses.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                if (recurringExpenses.isNotEmpty() && !isManualOverBudget) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val simResult = remember(
                        recurringExpenses, incomeSources, budgetPeriod,
                        budgetAmount, availableCash, resetDayOfWeek, resetDayOfMonth
                    ) {
                        SavingsSimulator.calculateSavingsRequired(
                            incomeSources = incomeSources,
                            recurringExpenses = recurringExpenses,
                            budgetPeriod = budgetPeriod,
                            budgetAmount = budgetAmount,
                            availableCash = availableCash,
                            resetDayOfWeek = resetDayOfWeek,
                            resetDayOfMonth = resetDayOfMonth
                        )
                    }
                    if (simResult.savingsRequired > 0.0) {
                    val formattedAmount = formatCurrency(simResult.savingsRequired, currencySymbol)
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
                                text = S.recurringExpenses.savingsRequiredMessage(formattedAmount, periodText),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (lowPointDateStr.isNotEmpty()) {
                                            Toast.makeText(
                                                context,
                                                S.recurringExpenses.savingsLowPointToast(lowPointDateStr),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                            )
                            Text(
                                text = S.recurringExpenses.savingsWhyLink,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF4CAF50),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable { showSavingsWhyDialog = true }
                            )
                        }
                    }
                    } // end if savingsRequired > 0
                }
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
                    Text(S.recurringExpenses.addExpense)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Group expenses into Monthly, Annual, Other
            val today = LocalDate.now()
            val monthly = recurringExpenses.filter {
                it.repeatType == RepeatType.MONTHS && it.repeatInterval == 1
            }
            val annual = recurringExpenses.filter {
                it.repeatType == RepeatType.ANNUAL
            }
            val other = recurringExpenses.filter {
                it !in monthly && it !in annual
            }

            // Helper to compute next occurrence date
            fun nextDate(expense: RecurringExpense): LocalDate? {
                return BudgetCalculator.generateOccurrences(
                    expense.repeatType, expense.repeatInterval,
                    expense.startDate, expense.monthDay1, expense.monthDay2,
                    today, today.plusDays(730)
                ).firstOrNull()
            }

            // Helper to describe the period for "Other" expenses
            fun periodLabel(expense: RecurringExpense): String = when (expense.repeatType) {
                RepeatType.DAYS -> S.recurringExpenses.everyNDays(expense.repeatInterval)
                RepeatType.WEEKS -> S.recurringExpenses.everyNWeeks(expense.repeatInterval)
                RepeatType.BI_WEEKLY -> S.recurringExpenses.everyTwoWeeks
                RepeatType.BI_MONTHLY -> S.recurringExpenses.twicePerMonth
                RepeatType.MONTHS -> S.recurringExpenses.everyNMonths(expense.repeatInterval)
                else -> ""
            }

            // Sort each group
            fun sorted(list: List<RecurringExpense>): List<RecurringExpense> =
                if (sortByAlpha) list.sortedBy { it.source.lowercase() }
                else list.sortedByDescending { it.amount }

            val sortedMonthly = sorted(monthly)
            val sortedAnnual = sorted(annual)
            val sortedOther = sorted(other)

            val sortButtonLabel = if (sortByAlpha) "A" else currencySymbol

            // --- Monthly section ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(customColors.headerBackground, RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sortButtonLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = customColors.headerText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                customColors.headerText.copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { sortByAlpha = !sortByAlpha; appPrefs.edit().putBoolean("recurringExpenseSortAlpha", sortByAlpha).apply() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    Text(
                        text = S.recurringExpenses.monthlyExpenses,
                        style = MaterialTheme.typography.titleSmall,
                        color = customColors.headerText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                }
            }
            if (sortedMonthly.isEmpty()) {
                item {
                    Text(
                        text = S.recurringExpenses.noMonthlyExpenses,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(sortedMonthly) { expense ->
                    val next = nextDate(expense)
                    ExpenseRow(expense, next, null, currencySymbol, dateFormatter, S,
                        onEdit = { editingExpense = expense },
                        onDelete = { deletingExpense = expense },
                        onLongPress = {
                            val linked = transactions.filter { it.linkedRecurringExpenseId == expense.id }
                                .sortedByDescending { it.date }
                                .take(10)
                            if (linked.isEmpty()) {
                                Toast.makeText(context, S.recurringExpenses.noLinkedTransactions, Toast.LENGTH_SHORT).show()
                            } else {
                                linkedTransactionsExpense = expense
                            }
                        })
                }
            }

            // --- Annual section ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(customColors.headerBackground, RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sortButtonLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = customColors.headerText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                customColors.headerText.copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { sortByAlpha = !sortByAlpha; appPrefs.edit().putBoolean("recurringExpenseSortAlpha", sortByAlpha).apply() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    Text(
                        text = S.recurringExpenses.annualExpenses,
                        style = MaterialTheme.typography.titleSmall,
                        color = customColors.headerText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                }
            }
            if (sortedAnnual.isEmpty()) {
                item {
                    Text(
                        text = S.recurringExpenses.noAnnualExpenses,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(sortedAnnual) { expense ->
                    val next = nextDate(expense)
                    ExpenseRow(expense, next, null, currencySymbol, dateFormatter, S,
                        onEdit = { editingExpense = expense },
                        onDelete = { deletingExpense = expense },
                        onLongPress = {
                            val linked = transactions.filter { it.linkedRecurringExpenseId == expense.id }
                                .sortedByDescending { it.date }
                                .take(10)
                            if (linked.isEmpty()) {
                                Toast.makeText(context, S.recurringExpenses.noLinkedTransactions, Toast.LENGTH_SHORT).show()
                            } else {
                                linkedTransactionsExpense = expense
                            }
                        })
                }
            }

            // --- Other section ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(customColors.headerBackground, RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sortButtonLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = customColors.headerText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                customColors.headerText.copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { sortByAlpha = !sortByAlpha; appPrefs.edit().putBoolean("recurringExpenseSortAlpha", sortByAlpha).apply() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    Text(
                        text = S.recurringExpenses.otherExpenses,
                        style = MaterialTheme.typography.titleSmall,
                        color = customColors.headerText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                }
            }
            if (sortedOther.isEmpty()) {
                item {
                    Text(
                        text = S.recurringExpenses.noOtherExpenses,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(sortedOther) { expense ->
                    val next = nextDate(expense)
                    ExpenseRow(expense, next, periodLabel(expense), currencySymbol, dateFormatter, S,
                        onEdit = { editingExpense = expense },
                        onDelete = { deletingExpense = expense },
                        onLongPress = {
                            val linked = transactions.filter { it.linkedRecurringExpenseId == expense.id }
                                .sortedByDescending { it.date }
                                .take(10)
                            if (linked.isEmpty()) {
                                Toast.makeText(context, S.recurringExpenses.noLinkedTransactions, Toast.LENGTH_SHORT).show()
                            } else {
                                linkedTransactionsExpense = expense
                            }
                        })
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditExpenseDialog(
            existingExpense = null,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
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
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            onDismiss = { editingExpense = null },
            onSave = { updated ->
                onUpdateRecurringExpense(updated)
                editingExpense = null
            }
        )
    }

    deletingExpense?.let { expense ->
        AdAwareAlertDialog(
            onDismissRequest = { deletingExpense = null },
            title = { Text(S.recurringExpenses.deleteExpenseTitle(expense.source)) },
            text = { Text(S.recurringExpenses.deleteExpenseBody) },
            style = DialogStyle.DANGER,
            confirmButton = {
                DialogDangerButton(onClick = {
                        onDeleteRecurringExpense(expense)
                        deletingExpense = null
                    }) { Text(S.common.delete) }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { deletingExpense = null }) { Text(S.common.cancel) }
            }
        )
    }

    linkedTransactionsExpense?.let { expense ->
        val linked = remember(expense, transactions) {
            transactions.filter { it.linkedRecurringExpenseId == expense.id }
                .sortedByDescending { it.date }
                .take(10)
        }
        AdAwareAlertDialog(
            onDismissRequest = { linkedTransactionsExpense = null },
            title = { Text(expense.source) },
            text = {
                Column {
                    Text(
                        S.recurringExpenses.linkedTransactions,
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
                DialogSecondaryButton(onClick = { linkedTransactionsExpense = null }) { Text(S.common.close) }
            }
        )
    }

    if (showSavingsWhyDialog) {
        val whyScrollState = rememberScrollState()
        AdAwareAlertDialog(
            onDismissRequest = { showSavingsWhyDialog = false },
            title = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        S.recurringExpenses.savingsWhyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(whyScrollState)) {
                    Text(S.recurringExpenses.savingsWhyBody)
                }
            },
            confirmButton = {
                DialogPrimaryButton(onClick = { showSavingsWhyDialog = false }) { Text(S.common.ok) }
            },
            scrollState = whyScrollState
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpenseRow(
    expense: RecurringExpense,
    nextDate: LocalDate?,
    periodDescription: String?,
    currencySymbol: String,
    dateFormatter: DateTimeFormatter,
    S: com.syncbudget.app.ui.strings.AppStrings,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onLongPress)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = expense.source,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (expense.description.isNotBlank()) {
                Text(
                    expense.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val amountStr = formatCurrency(expense.amount, currencySymbol)
            val nextStr = nextDate?.format(dateFormatter) ?: "—"
            Text(
                text = S.recurringExpenses.nextOn(amountStr, nextStr),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            if (periodDescription != null) {
                Text(
                    text = periodDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
        IconButton(onClick = { onDelete() }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = S.common.delete,
                tint = Color(0xFFF44336)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditExpenseDialog(
    existingExpense: RecurringExpense?,
    currencySymbol: String,
    dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    onDismiss: () -> Unit,
    onSave: (RecurringExpense) -> Unit
) {
    val S = LocalStrings.current
    val context = LocalContext.current
    val isEdit = existingExpense != null
    val title = if (isEdit) S.recurringExpenses.editExpense else S.recurringExpenses.addExpense
    val maxDecimalPlaces = CURRENCY_DECIMALS[currencySymbol] ?: 2

    var sourceName by remember { mutableStateOf(existingExpense?.source ?: "") }
    var description by remember { mutableStateOf(existingExpense?.description ?: "") }
    var amountText by remember {
        mutableStateOf(
            if (existingExpense != null && existingExpense.amount > 0.0)
                "%.${CURRENCY_DECIMALS[currencySymbol] ?: 2}f".format(existingExpense.amount)
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
        RepeatType.DAYS -> interval != null && interval in 1..365 && startDate != null
        RepeatType.WEEKS -> interval != null && interval in 1..52 && startDate != null
        RepeatType.BI_WEEKLY -> startDate != null
        RepeatType.MONTHS -> interval != null && interval in 1..12 && startDate != null
        RepeatType.BI_MONTHLY -> monthDay1 != null && monthDay1 in 1..28 && monthDay2 != null && monthDay2 in 1..28
        RepeatType.ANNUAL -> startDate != null
    }

    val isValid = isSourceValid && isAmountValid && isRepeatValid

    fun getRepeatTypeLabel(type: RepeatType): String = when (type) {
        RepeatType.DAYS -> S.common.repeatTypeDays
        RepeatType.WEEKS -> S.common.repeatTypeWeeks
        RepeatType.BI_WEEKLY -> S.common.repeatTypeBiWeekly
        RepeatType.MONTHS -> S.common.repeatTypeMonths
        RepeatType.BI_MONTHLY -> S.common.repeatTypeBiMonthly
        RepeatType.ANNUAL -> S.common.repeatTypeAnnual
    }

    AdAwareDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .imePadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            val dialogScrollState = rememberScrollState()
            Box {
            Column {
                DialogHeader(title)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(dialogScrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        label = { Text(S.recurringExpenses.descriptionLabel) },
                        singleLine = true,
                        isError = showValidation && !isSourceValid,
                        supportingText = if (showValidation && !isSourceValid) ({
                            Text(S.recurringExpenses.requiredNetflixExample, color = Color(0xFFF44336))
                        }) else null,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(S.common.descriptionFieldLabel) },
                        colors = textFieldColors,
                        singleLine = true,
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
                        label = { Text(S.common.amount) },
                        singleLine = true,
                        isError = showValidation && !isAmountValid,
                        supportingText = if (showValidation && !isAmountValid) ({
                            Text(S.recurringExpenses.exampleAmount, color = Color(0xFFF44336))
                        }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = if (maxDecimalPlaces > 0) KeyboardType.Decimal else KeyboardType.Number),
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()

                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = getRepeatTypeLabel(repeatType),
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
                            RepeatType.entries.filter { it != RepeatType.BI_WEEKLY }.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(getRepeatTypeLabel(type)) },
                                    onClick = {
                                        repeatType = type
                                        typeExpanded = false
                                        when (type) {
                                            RepeatType.DAYS -> { intervalText = "1"; monthDay1Text = ""; monthDay2Text = "" }
                                            RepeatType.WEEKS -> { intervalText = "1"; monthDay1Text = ""; monthDay2Text = "" }
                                            RepeatType.BI_WEEKLY -> { monthDay1Text = ""; monthDay2Text = "" }
                                            RepeatType.MONTHS -> { intervalText = "1"; monthDay1Text = ""; monthDay2Text = "" }
                                            RepeatType.BI_MONTHLY -> { intervalText = "1"; startDate = null }
                                            RepeatType.ANNUAL -> { monthDay1Text = ""; monthDay2Text = "" }
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
                                onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) intervalText = it },
                                label = { Text(S.common.everyXDays) },
                                singleLine = true,
                                isError = showValidation && (interval == null || interval !in 1..365),
                                supportingText = if (showValidation && (interval == null || interval !in 1..365)) ({
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
                                onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) intervalText = it },
                                label = { Text(S.common.intervalWeeks) },
                                singleLine = true,
                                isError = showValidation && (interval == null || interval !in 1..52),
                                supportingText = if (showValidation && (interval == null || interval !in 1..52)) ({
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
                                onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) intervalText = it },
                                label = { Text(S.common.everyXMonths) },
                                singleLine = true,
                                isError = showValidation && (interval == null || interval !in 1..12),
                                supportingText = if (showValidation && (interval == null || interval !in 1..12)) ({
                                    Text(S.common.exampleMonths, color = Color(0xFFF44336))
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
                                Text(
                                    text = S.common.dayOfMonth + ": " + startDate!!.dayOfMonth,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                            }
                        }
                        RepeatType.BI_MONTHLY -> {
                            OutlinedTextField(
                                value = monthDay1Text,
                                onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) monthDay1Text = it },
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
                                onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) monthDay2Text = it },
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
                        RepeatType.ANNUAL -> {
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
                    }
                }

                DialogFooter {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    DialogSecondaryButton(onClick = onDismiss) { Text(S.common.cancel) }
                    Spacer(modifier = Modifier.width(8.dp))
                    DialogPrimaryButton(
                        onClick = {
                            if (isValid) {
                                val result = when (repeatType) {
                                    RepeatType.DAYS -> RecurringExpense(
                                        id = existingExpense?.id ?: 0,
                                        source = sourceName.trim(),
                                        description = description.trim(),
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
                                        description = description.trim(),
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
                                        description = description.trim(),
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
                                        description = description.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = interval!!,
                                        startDate = startDate,
                                        monthDay1 = startDate!!.dayOfMonth,
                                        monthDay2 = null
                                    )
                                    RepeatType.BI_MONTHLY -> RecurringExpense(
                                        id = existingExpense?.id ?: 0,
                                        source = sourceName.trim(),
                                        description = description.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = 1,
                                        startDate = null,
                                        monthDay1 = monthDay1,
                                        monthDay2 = monthDay2
                                    )
                                    RepeatType.ANNUAL -> RecurringExpense(
                                        id = existingExpense?.id ?: 0,
                                        source = sourceName.trim(),
                                        description = description.trim(),
                                        amount = amount!!,
                                        repeatType = repeatType,
                                        repeatInterval = 1,
                                        startDate = startDate,
                                        monthDay1 = null,
                                        monthDay2 = null
                                    )
                                }
                                onSave(result)
                            } else {
                                showValidation = true
                            }
                        }
                    ) { Text(S.common.save) }
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
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate?.let {
                it.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                DialogPrimaryButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            val monthInterval = intervalText.toIntOrNull() ?: 1
                            if (repeatType == RepeatType.MONTHS && monthInterval != 12 && selected.dayOfMonth > 28) {
                                Toast.makeText(context, S.common.dateDayTooHigh, Toast.LENGTH_SHORT).show()
                            } else {
                                startDate = selected
                                showDatePicker = false
                            }
                        }
                    }) { Text(S.common.ok) }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { showDatePicker = false }) { Text(S.common.cancel) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
