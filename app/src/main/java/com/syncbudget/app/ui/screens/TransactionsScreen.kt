package com.syncbudget.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syncbudget.app.data.BankFormat
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CryptoHelper
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionType
import com.syncbudget.app.data.autoCategorize
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.findAmortizationMatch
import com.syncbudget.app.data.findBudgetIncomeMatch
import com.syncbudget.app.data.findDuplicate
import com.syncbudget.app.data.findRecurringExpenseMatch
import com.syncbudget.app.data.generateTransactionId
import com.syncbudget.app.data.getCategoryIcon
import com.syncbudget.app.data.isRecurringDateCloseEnough
import com.syncbudget.app.data.parseSyncBudgetCsv
import com.syncbudget.app.data.parseUsBank
import com.syncbudget.app.data.serializeTransactionsCsv
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS
import com.syncbudget.app.ui.components.PieChartEditor
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class SaveFormat(val label: String) {
    CSV("CSV"),
    ENCRYPTED("Encrypted")
}

private enum class ImportStage {
    FORMAT_SELECTION, PARSING, PARSE_ERROR, DUPLICATE_CHECK, COMPLETE
}

private enum class ViewFilter(val label: String) {
    EXPENSES("Expenses"),
    INCOME("Income"),
    ALL("All")
}

private fun isValidAmountInput(text: String, maxDecimals: Int): Boolean {
    if (text.isEmpty()) return true
    if (maxDecimals == 0) return text.all { it.isDigit() }
    if (text.count { it == '.' } > 1) return false
    val dotIndex = text.indexOf('.')
    if (dotIndex >= 0 && text.length - dotIndex - 1 > maxDecimals) return false
    if (text == ".") return true
    val testStr = if (text.endsWith(".")) "${text}0" else text
    return testStr.toDoubleOrNull() != null
}

private fun isValidPercentInput(text: String): Boolean {
    if (text.isEmpty()) return true
    if (!text.all { it.isDigit() }) return false
    val value = text.toIntOrNull() ?: return false
    return value in 0..100
}

private fun formatAmount(value: Double, decimals: Int): String {
    return "%.${decimals}f".format(value)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    transactions: List<Transaction>,
    currencySymbol: String,
    dateFormatPattern: String = "yyyy-MM-dd",
    categories: List<Category>,
    isPaidUser: Boolean = false,
    recurringExpenses: List<RecurringExpense> = emptyList(),
    amortizationEntries: List<AmortizationEntry> = emptyList(),
    incomeSources: List<IncomeSource> = emptyList(),
    onAddTransaction: (Transaction) -> Unit,
    onUpdateTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onDeleteTransactions: (Set<Int>) -> Unit,
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current
    val dateFormatter = remember(dateFormatPattern) {
        DateTimeFormatter.ofPattern(dateFormatPattern)
    }

    var viewFilter by remember { mutableStateOf(ViewFilter.ALL) }
    var showAddIncome by remember { mutableStateOf(false) }
    var showAddExpense by remember { mutableStateOf(false) }
    var showSearchMenu by remember { mutableStateOf(false) }

    // Search state
    var searchActive by remember { mutableStateOf(false) }
    var searchPredicate by remember { mutableStateOf<((Transaction) -> Boolean)?>(null) }

    // Category filter state
    var categoryFilterId by remember { mutableStateOf<Int?>(null) }

    // Search dialog states
    var showTextSearch by remember { mutableStateOf(false) }
    var showAmountSearch by remember { mutableStateOf(false) }
    var showDateSearchStart by remember { mutableStateOf(false) }
    var showDateSearchEnd by remember { mutableStateOf(false) }
    var dateSearchStart by remember { mutableStateOf<LocalDate?>(null) }

    // Edit state
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Multi-select state
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateMapOf<Int, Boolean>() }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkCategoryChange by remember { mutableStateOf(false) }
    var showBulkMerchantEdit by remember { mutableStateOf(false) }

    // Expanded multi-category rows
    val expandedIds = remember { mutableStateMapOf<Int, Boolean>() }

    // Manual duplicate check state
    var pendingManualSave by remember { mutableStateOf<Transaction?>(null) }
    var manualDuplicateMatch by remember { mutableStateOf<Transaction?>(null) }
    var showManualDuplicateDialog by remember { mutableStateOf(false) }
    var pendingManualIsEdit by remember { mutableStateOf(false) }

    // Recurring expense match state
    var pendingRecurringTxn by remember { mutableStateOf<Transaction?>(null) }
    var pendingRecurringMatch by remember { mutableStateOf<RecurringExpense?>(null) }
    var pendingRecurringIsEdit by remember { mutableStateOf(false) }
    var showRecurringDialog by remember { mutableStateOf(false) }
    var currentImportRecurring by remember { mutableStateOf<RecurringExpense?>(null) }

    // Amortization match state
    var pendingAmortizationTxn by remember { mutableStateOf<Transaction?>(null) }
    var pendingAmortizationMatch by remember { mutableStateOf<AmortizationEntry?>(null) }
    var pendingAmortizationIsEdit by remember { mutableStateOf(false) }
    var showAmortizationDialog by remember { mutableStateOf(false) }
    var currentImportAmortization by remember { mutableStateOf<AmortizationEntry?>(null) }

    // Budget income match state
    var pendingBudgetIncomeTxn by remember { mutableStateOf<Transaction?>(null) }
    var pendingBudgetIncomeMatch by remember { mutableStateOf<IncomeSource?>(null) }
    var pendingBudgetIncomeIsEdit by remember { mutableStateOf(false) }
    var showBudgetIncomeDialog by remember { mutableStateOf(false) }

    // CSV Import state
    val context = LocalContext.current
    var showImportFormatDialog by remember { mutableStateOf(false) }
    var selectedBankFormat by remember { mutableStateOf(BankFormat.US_BANK) }
    var importStage by remember { mutableStateOf<ImportStage?>(null) }
    val parsedTransactions = remember { mutableStateListOf<Transaction>() }
    val importApproved = remember { mutableStateListOf<Transaction>() }
    var importIndex by remember { mutableIntStateOf(0) }
    var ignoreAllDuplicates by remember { mutableStateOf(false) }
    var currentImportDup by remember { mutableStateOf<Transaction?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    // Save state
    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedSaveFormat by remember { mutableStateOf(SaveFormat.CSV) }
    var savePassword by remember { mutableStateOf("") }
    var savePasswordConfirm by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Encrypted load password
    var encryptedLoadPassword by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        pendingUri = uri
    }

    val csvSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val csvContent = serializeTransactionsCsv(transactions)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(csvContent.toByteArray())
            }
            Toast.makeText(context, "Saved ${transactions.size} transactions", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val encryptedSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val csvContent = serializeTransactionsCsv(transactions)
            val encrypted = CryptoHelper.encrypt(
                csvContent.toByteArray(),
                savePassword.toCharArray()
            )
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(encrypted)
            }
            savePassword = ""
            savePasswordConfirm = ""
            Toast.makeText(context, "Encrypted save: ${transactions.size} transactions", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Encrypted save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Process file when URI is set — dispatch on selected format
    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        pendingUri = null
        importStage = ImportStage.PARSING

        try {
            val existingIdSet = transactions.map { it.id }.toSet()

            val result = when (selectedBankFormat) {
                BankFormat.US_BANK -> {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        importError = "Could not open file"
                        importStage = ImportStage.PARSE_ERROR
                        return@LaunchedEffect
                    }
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val r = parseUsBank(reader, existingIdSet)
                    reader.close()
                    r
                }
                BankFormat.SECURESYNC_CSV -> {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        importError = "Could not open file"
                        importStage = ImportStage.PARSE_ERROR
                        return@LaunchedEffect
                    }
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val r = parseSyncBudgetCsv(reader, existingIdSet)
                    reader.close()
                    r
                }
                BankFormat.SECURESYNC_ENCRYPTED -> {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        importError = "Could not open file"
                        importStage = ImportStage.PARSE_ERROR
                        return@LaunchedEffect
                    }
                    try {
                        val encryptedBytes = inputStream.readBytes()
                        inputStream.close()
                        val decryptedBytes = CryptoHelper.decrypt(
                            encryptedBytes,
                            encryptedLoadPassword.toCharArray()
                        )
                        encryptedLoadPassword = ""
                        val reader = BufferedReader(
                            InputStreamReader(decryptedBytes.inputStream())
                        )
                        val r = parseSyncBudgetCsv(reader, existingIdSet)
                        reader.close()
                        r
                    } catch (e: javax.crypto.AEADBadTagException) {
                        encryptedLoadPassword = ""
                        importError = "Wrong password or corrupted file"
                        importStage = ImportStage.PARSE_ERROR
                        return@LaunchedEffect
                    } catch (e: javax.crypto.BadPaddingException) {
                        encryptedLoadPassword = ""
                        importError = "Wrong password or corrupted file"
                        importStage = ImportStage.PARSE_ERROR
                        return@LaunchedEffect
                    }
                }
            }

            parsedTransactions.clear()
            parsedTransactions.addAll(result.transactions)

            if (result.error != null && result.transactions.isEmpty()) {
                importError = result.error
                importStage = ImportStage.PARSE_ERROR
            } else if (result.error != null) {
                importError = result.error
                importStage = ImportStage.PARSE_ERROR
            } else {
                // Auto-categorize only for bank imports (they lack categories)
                val processed = if (selectedBankFormat == BankFormat.US_BANK) {
                    parsedTransactions.map { txn -> autoCategorize(txn, transactions, categories) }
                } else {
                    parsedTransactions.toList()
                }
                parsedTransactions.clear()
                parsedTransactions.addAll(processed)

                importApproved.clear()
                importIndex = 0
                ignoreAllDuplicates = false
                importStage = ImportStage.DUPLICATE_CHECK
            }
        } catch (e: Exception) {
            importError = "Error: ${e.message}"
            importStage = ImportStage.PARSE_ERROR
        }
    }

    // Duplicate check loop
    LaunchedEffect(importStage, importIndex, ignoreAllDuplicates) {
        if (importStage != ImportStage.DUPLICATE_CHECK) return@LaunchedEffect
        if (currentImportDup != null || currentImportRecurring != null || currentImportAmortization != null) return@LaunchedEffect
        if (importIndex >= parsedTransactions.size) {
            // All done — add approved transactions
            importApproved.forEach { txn -> onAddTransaction(txn) }
            val count = importApproved.size
            importApproved.clear()
            parsedTransactions.clear()
            importStage = ImportStage.COMPLETE
            Toast.makeText(context, "Imported $count transactions", Toast.LENGTH_SHORT).show()
            importStage = null
            return@LaunchedEffect
        }

        val txn = parsedTransactions[importIndex]
        if (ignoreAllDuplicates) {
            // Still check recurring/amortization even when ignoring duplicates
            val recurringMatch = findRecurringExpenseMatch(txn, recurringExpenses)
            if (recurringMatch != null) {
                currentImportRecurring = recurringMatch
            } else {
                val amortizationMatch = findAmortizationMatch(txn, amortizationEntries)
                if (amortizationMatch != null) {
                    currentImportAmortization = amortizationMatch
                } else {
                    importApproved.add(txn)
                    importIndex++
                }
            }
            return@LaunchedEffect
        }

        val dup = findDuplicate(txn, transactions + importApproved)
        if (dup == null) {
            val recurringMatch = findRecurringExpenseMatch(txn, recurringExpenses)
            if (recurringMatch != null) {
                currentImportRecurring = recurringMatch
            } else {
                val amortizationMatch = findAmortizationMatch(txn, amortizationEntries)
                if (amortizationMatch != null) {
                    currentImportAmortization = amortizationMatch
                } else {
                    importApproved.add(txn)
                    importIndex++
                }
            }
        } else {
            currentImportDup = dup
        }
    }

    // Filter and sort transactions (no remember — SnapshotStateList mutations trigger recomposition)
    val filteredTransactions = run {
        var list = transactions.toList()
        list = when (viewFilter) {
            ViewFilter.EXPENSES -> list.filter { it.type == TransactionType.EXPENSE }
            ViewFilter.INCOME -> list.filter { it.type == TransactionType.INCOME }
            ViewFilter.ALL -> list
        }
        if (searchActive && searchPredicate != null) {
            list = list.filter(searchPredicate!!)
        }
        if (categoryFilterId != null) {
            list = list.filter { t -> t.categoryAmounts.any { it.categoryId == categoryFilterId } }
        }
        list.sortedWith(
            compareByDescending<Transaction> { it.date }
                .thenBy { it.source }
                .thenBy { it.amount }
        )
    }

    val allSelected = filteredTransactions.isNotEmpty() &&
            filteredTransactions.all { selectedIds[it.id] == true }

    val categoryMap = categories.associateBy { it.id }
    val existingIds = transactions.map { it.id }.toSet()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Transactions",
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selectedIds.clear()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = customColors.headerText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { if (isPaidUser) showSaveDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Save",
                            tint = if (isPaidUser) customColors.headerText
                                   else customColors.headerText.copy(alpha = 0.35f)
                        )
                    }
                    IconButton(onClick = { if (isPaidUser) showImportFormatDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoveToInbox,
                            contentDescription = "Load",
                            tint = if (isPaidUser) customColors.headerText
                                   else customColors.headerText.copy(alpha = 0.35f)
                        )
                    }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        viewFilter = when (viewFilter) {
                            ViewFilter.ALL -> ViewFilter.EXPENSES
                            ViewFilter.EXPENSES -> ViewFilter.INCOME
                            ViewFilter.INCOME -> ViewFilter.ALL
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    )
                ) {
                    Text(viewFilter.label)
                }

                IconButton(
                    onClick = { showAddIncome = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Income",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { showAddExpense = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Add Expense",
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showSearchMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSearchMenu,
                        onDismissRequest = { showSearchMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Date Search") },
                            onClick = {
                                showSearchMenu = false
                                showDateSearchStart = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Text Search") },
                            onClick = {
                                showSearchMenu = false
                                showTextSearch = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Amount Search") },
                            onClick = {
                                showSearchMenu = false
                                showAmountSearch = true
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Search results bar
            if (searchActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .clickable {
                            searchActive = false
                            searchPredicate = null
                            selectionMode = false
                            selectedIds.clear()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Search Results \u2014 Tap to Clear",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Category view bar
            if (categoryFilterId != null) {
                val filterCatName = categoryMap[categoryFilterId]?.name ?: "Unknown"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .clickable {
                            categoryFilterId = null
                            selectionMode = false
                            selectedIds.clear()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$filterCatName \u2014 Tap to Clear",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Select-all bar (in selection mode)
            if (selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            if (checked) {
                                filteredTransactions.forEach { selectedIds[it.id] = true }
                            } else {
                                selectedIds.clear()
                            }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                    Text(
                        text = "Select All",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (selectedIds.any { it.value }) {
                            showBulkCategoryChange = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Category,
                            contentDescription = "Change category",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = {
                        if (selectedIds.any { it.value }) {
                            showBulkMerchantEdit = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit merchant",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = {
                        if (selectedIds.any { it.value }) {
                            showBulkDeleteConfirm = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete selected",
                            tint = Color(0xFFF44336)
                        )
                    }
                    IconButton(onClick = {
                        selectionMode = false
                        selectedIds.clear()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancel selection",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Transaction list
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredTransactions, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        currencySymbol = currencySymbol,
                        dateFormatter = dateFormatter,
                        categoryMap = categoryMap,
                        selectionMode = selectionMode,
                        isSelected = selectedIds[transaction.id] == true,
                        isExpanded = expandedIds[transaction.id] == true,
                        onTap = {
                            if (selectionMode) {
                                selectedIds[transaction.id] =
                                    !(selectedIds[transaction.id] ?: false)
                            } else {
                                editingTransaction = transaction
                            }
                        },
                        onLongPress = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds.clear()
                            }
                            selectedIds[transaction.id] = true
                        },
                        onToggleSelection = { checked ->
                            selectedIds[transaction.id] = checked
                        },
                        onToggleExpand = {
                            expandedIds[transaction.id] =
                                !(expandedIds[transaction.id] ?: false)
                        },
                        onCategoryFilter = { catId ->
                            categoryFilterId = catId
                            viewFilter = ViewFilter.ALL
                            selectionMode = false
                            selectedIds.clear()
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                    )
                }
            }
        }
    }

    // Add Income dialog
    if (showAddIncome) {
        TransactionDialog(
            title = "Add New Income Transaction",
            sourceLabel = "Source",
            categories = categories,
            existingIds = existingIds,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            onDismiss = { showAddIncome = false },
            onSave = { txn ->
                val dup = findDuplicate(txn, transactions)
                if (dup != null) {
                    pendingManualSave = txn
                    manualDuplicateMatch = dup
                    pendingManualIsEdit = false
                    showManualDuplicateDialog = true
                } else {
                    val recurringMatch = findRecurringExpenseMatch(txn, recurringExpenses)
                    if (recurringMatch != null) {
                        pendingRecurringTxn = txn
                        pendingRecurringMatch = recurringMatch
                        pendingRecurringIsEdit = false
                        showRecurringDialog = true
                    } else {
                        val amortizationMatch = findAmortizationMatch(txn, amortizationEntries)
                        if (amortizationMatch != null) {
                            pendingAmortizationTxn = txn
                            pendingAmortizationMatch = amortizationMatch
                            pendingAmortizationIsEdit = false
                            showAmortizationDialog = true
                        } else {
                            val budgetMatch = findBudgetIncomeMatch(txn, incomeSources)
                            if (budgetMatch != null) {
                                pendingBudgetIncomeTxn = txn
                                pendingBudgetIncomeMatch = budgetMatch
                                pendingBudgetIncomeIsEdit = false
                                showBudgetIncomeDialog = true
                            } else {
                                onAddTransaction(txn)
                            }
                        }
                    }
                }
                showAddIncome = false
            }
        )
    }

    // Add Expense dialog
    if (showAddExpense) {
        TransactionDialog(
            title = "Add New Expense Transaction",
            sourceLabel = "Merchant",
            categories = categories,
            existingIds = existingIds,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            isExpense = true,
            onDismiss = { showAddExpense = false },
            onSave = { txn ->
                val dup = findDuplicate(txn, transactions)
                if (dup != null) {
                    pendingManualSave = txn
                    manualDuplicateMatch = dup
                    pendingManualIsEdit = false
                    showManualDuplicateDialog = true
                } else {
                    val recurringMatch = findRecurringExpenseMatch(txn, recurringExpenses)
                    if (recurringMatch != null) {
                        pendingRecurringTxn = txn
                        pendingRecurringMatch = recurringMatch
                        pendingRecurringIsEdit = false
                        showRecurringDialog = true
                    } else {
                        val amortizationMatch = findAmortizationMatch(txn, amortizationEntries)
                        if (amortizationMatch != null) {
                            pendingAmortizationTxn = txn
                            pendingAmortizationMatch = amortizationMatch
                            pendingAmortizationIsEdit = false
                            showAmortizationDialog = true
                        } else {
                            onAddTransaction(txn)
                        }
                    }
                }
                showAddExpense = false
            }
        )
    }

    // Edit dialog
    editingTransaction?.let { txn ->
        TransactionDialog(
            title = if (txn.type == TransactionType.EXPENSE) "Edit Expense" else "Edit Income",
            sourceLabel = if (txn.type == TransactionType.EXPENSE) "Merchant" else "Source",
            categories = categories,
            existingIds = existingIds,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            isExpense = txn.type == TransactionType.EXPENSE,
            editTransaction = txn,
            onDismiss = { editingTransaction = null },
            onSave = { updated ->
                val dup = findDuplicate(updated, transactions.filter { it.id != updated.id })
                if (dup != null) {
                    pendingManualSave = updated
                    manualDuplicateMatch = dup
                    pendingManualIsEdit = true
                    showManualDuplicateDialog = true
                } else {
                    val recurringMatch = findRecurringExpenseMatch(updated, recurringExpenses)
                    if (recurringMatch != null) {
                        pendingRecurringTxn = updated
                        pendingRecurringMatch = recurringMatch
                        pendingRecurringIsEdit = true
                        showRecurringDialog = true
                    } else {
                        val amortizationMatch = findAmortizationMatch(updated, amortizationEntries)
                        if (amortizationMatch != null) {
                            pendingAmortizationTxn = updated
                            pendingAmortizationMatch = amortizationMatch
                            pendingAmortizationIsEdit = true
                            showAmortizationDialog = true
                        } else {
                            val budgetMatch = findBudgetIncomeMatch(updated, incomeSources)
                            if (budgetMatch != null) {
                                pendingBudgetIncomeTxn = updated
                                pendingBudgetIncomeMatch = budgetMatch
                                pendingBudgetIncomeIsEdit = true
                                showBudgetIncomeDialog = true
                            } else {
                                onUpdateTransaction(updated)
                            }
                        }
                    }
                }
                editingTransaction = null
            },
            onDelete = { onDeleteTransaction(txn); editingTransaction = null }
        )
    }

    // Text search dialog
    if (showTextSearch) {
        TextSearchDialog(
            onDismiss = { showTextSearch = false },
            onSearch = { query ->
                searchPredicate = { t -> t.source.contains(query, ignoreCase = true) }
                searchActive = true
                viewFilter = ViewFilter.ALL
                showTextSearch = false
            }
        )
    }

    // Amount search dialog
    if (showAmountSearch) {
        AmountSearchDialog(
            onDismiss = { showAmountSearch = false },
            onSearch = { min, max ->
                searchPredicate = { t -> t.amount in min..max }
                searchActive = true
                viewFilter = ViewFilter.ALL
                showAmountSearch = false
            }
        )
    }

    // Date search - start
    if (showDateSearchStart) {
        SearchDatePickerDialog(
            title = "Select Start Date",
            onDismiss = { showDateSearchStart = false },
            onDateSelected = { date ->
                dateSearchStart = date
                showDateSearchStart = false
                showDateSearchEnd = true
            }
        )
    }

    // Date search - end
    if (showDateSearchEnd) {
        SearchDatePickerDialog(
            title = "Select End Date",
            onDismiss = { showDateSearchEnd = false; dateSearchStart = null },
            onDateSelected = { endDate ->
                val start = dateSearchStart
                if (start != null) {
                    searchPredicate = { t -> !t.date.isBefore(start) && !t.date.isAfter(endDate) }
                    searchActive = true
                viewFilter = ViewFilter.ALL
                }
                showDateSearchEnd = false
                dateSearchStart = null
            }
        )
    }

    // Bulk delete confirmation
    if (showBulkDeleteConfirm) {
        val count = selectedIds.count { it.value }
        val isAllWithoutSearch = allSelected && !searchActive && categoryFilterId == null
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = {
                Text(
                    if (isAllWithoutSearch) "WARNING" else "Delete Transactions?"
                )
            },
            text = {
                Text(
                    if (isAllWithoutSearch)
                        "This will permanently delete ALL selected transactions in the current view. This action cannot be undone."
                    else
                        "Delete $count selected transaction${if (count != 1) "s" else ""}?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val idsToDelete = selectedIds.filter { it.value }.keys
                    onDeleteTransactions(idsToDelete)
                    selectedIds.clear()
                    selectionMode = false
                    showBulkDeleteConfirm = false
                }) {
                    Text("Delete", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk category change dialog
    if (showBulkCategoryChange) {
        var bulkSelectedCatId by remember { mutableStateOf<Int?>(null) }
        AlertDialog(
            onDismissRequest = {
                showBulkCategoryChange = false
            },
            title = { Text("Change Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val count = selectedIds.count { it.value }
                    Text(
                        text = "Set category for $count transaction${if (count != 1) "s" else ""}:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(categories) { cat ->
                            val isTarget = bulkSelectedCatId == cat.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable { bulkSelectedCatId = cat.id }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getCategoryIcon(cat.iconName),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val catId = bulkSelectedCatId ?: return@TextButton
                        val idsToChange = selectedIds.filter { it.value }.keys
                        transactions.filter { it.id in idsToChange }.forEach { txn ->
                            onUpdateTransaction(
                                txn.copy(
                                    categoryAmounts = listOf(CategoryAmount(catId, txn.amount)),
                                    isUserCategorized = true
                                )
                            )
                        }
                        selectedIds.clear()
                        selectionMode = false
                        showBulkCategoryChange = false
                    },
                    enabled = bulkSelectedCatId != null
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkCategoryChange = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk merchant/source edit dialog
    if (showBulkMerchantEdit) {
        var newMerchant by remember { mutableStateOf("") }
        val count = selectedIds.count { it.value }
        AlertDialog(
            onDismissRequest = { showBulkMerchantEdit = false },
            title = { Text("Edit Merchant/Source") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Set merchant/source for $count transaction${if (count != 1) "s" else ""}:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = newMerchant,
                        onValueChange = { newMerchant = it },
                        label = { Text("Merchant/Source") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newMerchant.isNotBlank()) {
                            val idsToChange = selectedIds.filter { it.value }.keys
                            transactions.filter { it.id in idsToChange }.forEach { txn ->
                                onUpdateTransaction(txn.copy(source = newMerchant.trim()))
                            }
                            selectedIds.clear()
                            selectionMode = false
                            showBulkMerchantEdit = false
                        }
                    },
                    enabled = newMerchant.isNotBlank()
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkMerchantEdit = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Save dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = {
                showSaveDialog = false
                savePassword = ""
                savePasswordConfirm = ""
                saveError = null
            },
            title = { Text("Save Transactions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Format:", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaveFormat.entries.forEach { format ->
                            OutlinedButton(
                                onClick = {
                                    selectedSaveFormat = format
                                    savePassword = ""
                                    savePasswordConfirm = ""
                                    saveError = null
                                },
                                colors = if (selectedSaveFormat == format)
                                    ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    )
                                else ButtonDefaults.outlinedButtonColors()
                            ) {
                                if (format == SaveFormat.ENCRYPTED) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(format.label)
                            }
                        }
                    }

                    Text("${transactions.size} transactions will be saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

                    if (selectedSaveFormat == SaveFormat.ENCRYPTED) {
                        val pwFieldColors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        OutlinedTextField(
                            value = savePassword,
                            onValueChange = { savePassword = it; saveError = null },
                            label = { Text("Password (min 8 chars)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = pwFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = savePasswordConfirm,
                            onValueChange = { savePasswordConfirm = it; saveError = null },
                            label = { Text("Confirm Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = pwFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (saveError != null) {
                            Text(
                                text = saveError!!,
                                color = Color(0xFFF44336),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (selectedSaveFormat) {
                        SaveFormat.CSV -> {
                            showSaveDialog = false
                            csvSaveLauncher.launch("syncbudget_transactions.csv")
                        }
                        SaveFormat.ENCRYPTED -> {
                            when {
                                savePassword.length < 8 -> {
                                    saveError = "Password must be at least 8 characters"
                                }
                                savePassword != savePasswordConfirm -> {
                                    saveError = "Passwords do not match"
                                }
                                else -> {
                                    showSaveDialog = false
                                    encryptedSaveLauncher.launch("syncbudget_transactions.enc")
                                }
                            }
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    savePassword = ""
                    savePasswordConfirm = ""
                    saveError = null
                }) { Text("Cancel") }
            }
        )
    }

    // Import / Load format selection dialog
    if (showImportFormatDialog) {
        var formatDropdownExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                showImportFormatDialog = false
                encryptedLoadPassword = ""
            },
            title = { Text("Import / Load") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Format:", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Box {
                        OutlinedButton(onClick = { formatDropdownExpanded = true }) {
                            Text(selectedBankFormat.displayName)
                        }
                        DropdownMenu(
                            expanded = formatDropdownExpanded,
                            onDismissRequest = { formatDropdownExpanded = false }
                        ) {
                            BankFormat.entries.forEach { format ->
                                DropdownMenuItem(
                                    text = { Text(format.displayName) },
                                    onClick = {
                                        selectedBankFormat = format
                                        formatDropdownExpanded = false
                                        encryptedLoadPassword = ""
                                    }
                                )
                            }
                        }
                    }

                    if (selectedBankFormat == BankFormat.SECURESYNC_ENCRYPTED) {
                        OutlinedTextField(
                            value = encryptedLoadPassword,
                            onValueChange = { encryptedLoadPassword = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                val canProceed = when (selectedBankFormat) {
                    BankFormat.SECURESYNC_ENCRYPTED -> encryptedLoadPassword.length >= 8
                    else -> true
                }
                TextButton(
                    onClick = {
                        showImportFormatDialog = false
                        filePickerLauncher.launch(arrayOf("text/*", "*/*"))
                    },
                    enabled = canProceed
                ) { Text("Select File") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportFormatDialog = false
                    encryptedLoadPassword = ""
                }) { Text("Cancel") }
            }
        )
    }

    // Parse error dialog
    if (importStage == ImportStage.PARSE_ERROR) {
        AlertDialog(
            onDismissRequest = {
                importStage = null
                parsedTransactions.clear()
                importError = null
            },
            title = { Text("Parse Error") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(importError ?: "Unknown error")
                    if (parsedTransactions.isNotEmpty()) {
                        Text("${parsedTransactions.size} transactions parsed before error.")
                    }
                }
            },
            confirmButton = {
                if (parsedTransactions.isNotEmpty()) {
                    TextButton(onClick = {
                        val categorized = parsedTransactions.map { txn ->
                            autoCategorize(txn, transactions, categories)
                        }
                        parsedTransactions.clear()
                        parsedTransactions.addAll(categorized)
                        importApproved.clear()
                        importIndex = 0
                        ignoreAllDuplicates = false
                        importError = null
                        importStage = ImportStage.DUPLICATE_CHECK
                    }) { Text("Keep") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    parsedTransactions.clear()
                    importError = null
                    importStage = null
                }) { Text("Delete") }
            }
        )
    }

    // Import duplicate resolution dialog
    if (importStage == ImportStage.DUPLICATE_CHECK && currentImportDup != null && importIndex < parsedTransactions.size) {
        val newTxn = parsedTransactions[importIndex]
        val existingDup = currentImportDup!!
        DuplicateResolutionDialog(
            existingTransaction = existingDup,
            newTransaction = newTxn,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            categoryMap = categoryMap,
            showIgnoreAll = true,
            onIgnore = {
                importApproved.add(newTxn)
                currentImportDup = null
                importIndex++
            },
            onKeepNew = {
                onDeleteTransaction(existingDup)
                importApproved.add(newTxn)
                currentImportDup = null
                importIndex++
            },
            onKeepExisting = {
                currentImportDup = null
                importIndex++
            },
            onIgnoreAll = {
                importApproved.add(newTxn)
                currentImportDup = null
                ignoreAllDuplicates = true
                importIndex++
            }
        )
    }

    // Manual duplicate resolution dialog
    if (showManualDuplicateDialog && pendingManualSave != null && manualDuplicateMatch != null) {
        DuplicateResolutionDialog(
            existingTransaction = manualDuplicateMatch!!,
            newTransaction = pendingManualSave!!,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            categoryMap = categoryMap,
            showIgnoreAll = false,
            onIgnore = {
                if (pendingManualIsEdit) onUpdateTransaction(pendingManualSave!!)
                else onAddTransaction(pendingManualSave!!)
                pendingManualSave = null
                manualDuplicateMatch = null
                showManualDuplicateDialog = false
            },
            onKeepNew = {
                onDeleteTransaction(manualDuplicateMatch!!)
                if (pendingManualIsEdit) onUpdateTransaction(pendingManualSave!!)
                else onAddTransaction(pendingManualSave!!)
                pendingManualSave = null
                manualDuplicateMatch = null
                showManualDuplicateDialog = false
            },
            onKeepExisting = {
                pendingManualSave = null
                manualDuplicateMatch = null
                showManualDuplicateDialog = false
            },
            onIgnoreAll = {}
        )
    }

    // Import recurring expense match dialog
    if (importStage == ImportStage.DUPLICATE_CHECK && currentImportRecurring != null && importIndex < parsedTransactions.size) {
        val importTxn = parsedTransactions[importIndex]
        val recurringMatch = currentImportRecurring!!
        val recurringCategoryId = categories.find { it.name == "Recurring" }?.id
        val dateCloseEnough = isRecurringDateCloseEnough(importTxn.date, recurringMatch)
        RecurringExpenseConfirmDialog(
            transaction = importTxn,
            recurringExpense = recurringMatch,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            showDateAdvisory = !dateCloseEnough,
            onConfirmRecurring = {
                val updatedTxn = if (recurringCategoryId != null) {
                    importTxn.copy(
                        categoryAmounts = listOf(CategoryAmount(recurringCategoryId, importTxn.amount)),
                        isUserCategorized = true
                    )
                } else importTxn
                importApproved.add(updatedTxn)
                currentImportRecurring = null
                importIndex++
            },
            onNotRecurring = {
                importApproved.add(importTxn)
                currentImportRecurring = null
                importIndex++
            }
        )
    }

    // Manual recurring expense match dialog
    if (showRecurringDialog && pendingRecurringTxn != null && pendingRecurringMatch != null) {
        val recurringCategoryId = categories.find { it.name == "Recurring" }?.id
        val dateCloseEnough = isRecurringDateCloseEnough(pendingRecurringTxn!!.date, pendingRecurringMatch!!)
        RecurringExpenseConfirmDialog(
            transaction = pendingRecurringTxn!!,
            recurringExpense = pendingRecurringMatch!!,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            showDateAdvisory = !dateCloseEnough,
            onConfirmRecurring = {
                val txn = pendingRecurringTxn!!
                val updatedTxn = if (recurringCategoryId != null) {
                    txn.copy(
                        categoryAmounts = listOf(CategoryAmount(recurringCategoryId, txn.amount)),
                        isUserCategorized = true
                    )
                } else txn
                if (pendingRecurringIsEdit) onUpdateTransaction(updatedTxn)
                else onAddTransaction(updatedTxn)
                pendingRecurringTxn = null
                pendingRecurringMatch = null
                showRecurringDialog = false
            },
            onNotRecurring = {
                val txn = pendingRecurringTxn!!
                if (pendingRecurringIsEdit) onUpdateTransaction(txn)
                else onAddTransaction(txn)
                pendingRecurringTxn = null
                pendingRecurringMatch = null
                showRecurringDialog = false
            }
        )
    }

    // Import amortization match dialog
    if (importStage == ImportStage.DUPLICATE_CHECK && currentImportAmortization != null && importIndex < parsedTransactions.size) {
        val importTxn = parsedTransactions[importIndex]
        val amortizationMatch = currentImportAmortization!!
        val amortizationCategoryId = categories.find { it.name == "Amortization" }?.id
        AmortizationConfirmDialog(
            transaction = importTxn,
            amortizationEntry = amortizationMatch,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            onConfirmAmortization = {
                val updatedTxn = if (amortizationCategoryId != null) {
                    importTxn.copy(
                        categoryAmounts = listOf(CategoryAmount(amortizationCategoryId, importTxn.amount)),
                        isUserCategorized = true
                    )
                } else importTxn
                importApproved.add(updatedTxn)
                currentImportAmortization = null
                importIndex++
            },
            onNotAmortized = {
                importApproved.add(importTxn)
                currentImportAmortization = null
                importIndex++
            }
        )
    }

    // Manual amortization match dialog
    if (showAmortizationDialog && pendingAmortizationTxn != null && pendingAmortizationMatch != null) {
        val amortizationCategoryId = categories.find { it.name == "Amortization" }?.id
        AmortizationConfirmDialog(
            transaction = pendingAmortizationTxn!!,
            amortizationEntry = pendingAmortizationMatch!!,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            onConfirmAmortization = {
                val txn = pendingAmortizationTxn!!
                val updatedTxn = if (amortizationCategoryId != null) {
                    txn.copy(
                        categoryAmounts = listOf(CategoryAmount(amortizationCategoryId, txn.amount)),
                        isUserCategorized = true
                    )
                } else txn
                if (pendingAmortizationIsEdit) onUpdateTransaction(updatedTxn)
                else onAddTransaction(updatedTxn)
                pendingAmortizationTxn = null
                pendingAmortizationMatch = null
                showAmortizationDialog = false
            },
            onNotAmortized = {
                val txn = pendingAmortizationTxn!!
                if (pendingAmortizationIsEdit) onUpdateTransaction(txn)
                else onAddTransaction(txn)
                pendingAmortizationTxn = null
                pendingAmortizationMatch = null
                showAmortizationDialog = false
            }
        )
    }

    // Budget income confirm dialog
    if (showBudgetIncomeDialog && pendingBudgetIncomeTxn != null && pendingBudgetIncomeMatch != null) {
        BudgetIncomeConfirmDialog(
            transaction = pendingBudgetIncomeTxn!!,
            incomeSource = pendingBudgetIncomeMatch!!,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            onConfirmBudgetIncome = {
                val txn = pendingBudgetIncomeTxn!!.copy(isBudgetIncome = true)
                if (pendingBudgetIncomeIsEdit) onUpdateTransaction(txn)
                else onAddTransaction(txn)
                pendingBudgetIncomeTxn = null
                pendingBudgetIncomeMatch = null
                showBudgetIncomeDialog = false
            },
            onNotBudgetIncome = {
                val txn = pendingBudgetIncomeTxn!!
                if (pendingBudgetIncomeIsEdit) onUpdateTransaction(txn)
                else onAddTransaction(txn)
                pendingBudgetIncomeTxn = null
                pendingBudgetIncomeMatch = null
                showBudgetIncomeDialog = false
            }
        )
    }
}

@Composable
private fun BudgetIncomeConfirmDialog(
    transaction: Transaction,
    incomeSource: IncomeSource,
    currencySymbol: String,
    dateFormatter: DateTimeFormatter,
    onConfirmBudgetIncome: () -> Unit,
    onNotBudgetIncome: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotBudgetIncome,
        title = { Text("Budget Income Detected") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transaction:", fontWeight = FontWeight.SemiBold)
                Text("${transaction.source} — $currencySymbol${formatAmount(transaction.amount, 2)}")
                Text(transaction.date.format(dateFormatter))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Matched Income Source:", fontWeight = FontWeight.SemiBold)
                Text("${incomeSource.source} — $currencySymbol${formatAmount(incomeSource.amount, 2)}")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "This appears to be expected income from your budget configuration. " +
                    "Budget income is already included in your budget calculation and will not increase your available cash.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmBudgetIncome) {
                Text("Yes, Budget Income")
            }
        },
        dismissButton = {
            TextButton(onClick = onNotBudgetIncome) {
                Text("No, Extra Income")
            }
        }
    )
}

@Composable
private fun AmortizationConfirmDialog(
    transaction: Transaction,
    amortizationEntry: AmortizationEntry,
    currencySymbol: String,
    dateFormatter: DateTimeFormatter,
    onConfirmAmortization: () -> Unit,
    onNotAmortized: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotAmortized,
        title = { Text("Amortization Transaction Detected") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transaction:", fontWeight = FontWeight.SemiBold)
                Text("${transaction.source} — $currencySymbol${formatAmount(transaction.amount, 2)}")
                Text(transaction.date.format(dateFormatter))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Matched Amortization Entry:", fontWeight = FontWeight.SemiBold)
                Text("${amortizationEntry.source} — $currencySymbol${formatAmount(amortizationEntry.amount, 2)}")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Is this an amortized expense that is configured on the Amortization page and should be spread across multiple budget periods?",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmAmortization) {
                Text("Yes, Amortized")
            }
        },
        dismissButton = {
            TextButton(onClick = onNotAmortized) {
                Text("No, Not Amortized")
            }
        }
    )
}

@Composable
private fun RecurringExpenseConfirmDialog(
    transaction: Transaction,
    recurringExpense: RecurringExpense,
    currencySymbol: String,
    dateFormatter: DateTimeFormatter,
    showDateAdvisory: Boolean,
    onConfirmRecurring: () -> Unit,
    onNotRecurring: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotRecurring,
        title = { Text("Recurring Transaction Detected") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transaction:", fontWeight = FontWeight.SemiBold)
                Text("${transaction.source} — $currencySymbol${formatAmount(transaction.amount, 2)}")
                Text(transaction.date.format(dateFormatter))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Matched Recurring Expense:", fontWeight = FontWeight.SemiBold)
                Text("${recurringExpense.source} — $currencySymbol${formatAmount(recurringExpense.amount, 2)}")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Is this a recurring transaction that is configured on the Recurring Expenses page and should not count against your budget?",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (showDateAdvisory) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Note: This transaction's date differs from the expected schedule by more than 2 days. Consider updating your recurring expense configuration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmRecurring) {
                Text("Yes, Recurring")
            }
        },
        dismissButton = {
            TextButton(onClick = onNotRecurring) {
                Text("No, Not Recurring")
            }
        }
    )
}

@Composable
private fun DuplicateResolutionDialog(
    existingTransaction: Transaction,
    newTransaction: Transaction,
    currencySymbol: String,
    dateFormatter: DateTimeFormatter,
    categoryMap: Map<Int, Category>,
    showIgnoreAll: Boolean,
    onIgnore: () -> Unit,
    onKeepNew: () -> Unit,
    onKeepExisting: () -> Unit,
    onIgnoreAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepExisting,
        title = { Text("Possible Duplicate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Existing:", fontWeight = FontWeight.SemiBold)
                Text(
                    "${existingTransaction.date.format(dateFormatter)}  ${existingTransaction.source}  $currencySymbol${"%.2f".format(existingTransaction.amount)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (existingTransaction.categoryAmounts.isNotEmpty()) {
                    val catNames = existingTransaction.categoryAmounts.mapNotNull { ca ->
                        categoryMap[ca.categoryId]?.name
                    }.joinToString(", ")
                    Text(catNames, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("New:", fontWeight = FontWeight.SemiBold)
                Text(
                    "${newTransaction.date.format(dateFormatter)}  ${newTransaction.source}  $currencySymbol${"%.2f".format(newTransaction.amount)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (newTransaction.categoryAmounts.isNotEmpty()) {
                    val catNames = newTransaction.categoryAmounts.mapNotNull { ca ->
                        categoryMap[ca.categoryId]?.name
                    }.joinToString(", ")
                    Text(catNames, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onIgnore) { Text("Ignore") }
                    TextButton(onClick = onKeepNew) { Text("Keep New") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onKeepExisting) { Text("Keep Existing") }
                    if (showIgnoreAll) {
                        TextButton(onClick = onIgnoreAll) { Text("Ignore All") }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    transaction: Transaction,
    currencySymbol: String,
    dateFormatter: DateTimeFormatter,
    categoryMap: Map<Int, Category>,
    selectionMode: Boolean,
    isSelected: Boolean,
    isExpanded: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelection: (Boolean) -> Unit,
    onToggleExpand: () -> Unit,
    onCategoryFilter: (Int) -> Unit = {}
) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    val amountColor = if (isExpense) Color(0xFFF44336) else Color(0xFF4CAF50)
    val amountPrefix = if (isExpense) "-" else ""
    val formattedAmount = "$amountPrefix$currencySymbol${"%.2f".format(transaction.amount)}"

    val hasMultipleCategories = transaction.categoryAmounts.size > 1
    val singleCategory = if (transaction.categoryAmounts.size == 1)
        categoryMap[transaction.categoryAmounts[0].categoryId] else null
    val customColors = LocalSyncBudgetColors.current
    val categoryIconTint = if (transaction.isUserCategorized) customColors.headerBackground
        else MaterialTheme.colorScheme.onBackground

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            if (transaction.categoryAmounts.isNotEmpty()) {
                if (hasMultipleCategories) {
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Categories",
                            tint = categoryIconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else if (singleCategory != null) {
                    Icon(
                        imageVector = getCategoryIcon(singleCategory.iconName),
                        contentDescription = singleCategory.name,
                        tint = categoryIconTint,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { onCategoryFilter(singleCategory.id) }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = transaction.date.format(dateFormatter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = transaction.source,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
                textAlign = TextAlign.End
            )

            if (selectionMode) {
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onToggleSelection,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                )
            }
        }

        // Inline category breakdown
        if (hasMultipleCategories && isExpanded) {
            transaction.categoryAmounts.forEach { ca ->
                val cat = categoryMap[ca.categoryId]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (cat != null) {
                        Icon(
                            imageVector = getCategoryIcon(cat.iconName),
                            contentDescription = cat.name,
                            tint = if (transaction.isUserCategorized) customColors.headerBackground.copy(alpha = 0.7f)
                                   else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onCategoryFilter(cat.id) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = cat.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = "Unknown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "$currencySymbol${"%.2f".format(ca.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDialog(
    title: String,
    sourceLabel: String,
    categories: List<Category>,
    existingIds: Set<Int>,
    currencySymbol: String = "$",
    dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    isExpense: Boolean = false,
    editTransaction: Transaction? = null,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val maxDecimals = CURRENCY_DECIMALS[currencySymbol] ?: 2
    val context = LocalContext.current
    val isEdit = editTransaction != null
    var selectedDate by remember {
        mutableStateOf(editTransaction?.date ?: LocalDate.now())
    }
    var source by remember { mutableStateOf(editTransaction?.source ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Category selection
    val selectedCategoryIds = remember {
        mutableStateMapOf<Int, Boolean>().apply {
            editTransaction?.categoryAmounts?.forEach { put(it.categoryId, true) }
        }
    }
    val selectedCats = categories.filter { selectedCategoryIds[it.id] == true }

    // Amount fields
    var usePercentage by remember { mutableStateOf(false) }
    var showPieChart by remember { mutableStateOf(false) }
    var totalAmountText by remember {
        mutableStateOf(
            if (editTransaction != null && editTransaction.categoryAmounts.size > 1)
                formatAmount(editTransaction.amount, maxDecimals)
            else ""
        )
    }
    var lastEditedCatId by remember { mutableStateOf<Int?>(null) }

    // Move value dialog state (category deselection)
    var showMoveValueDialog by remember { mutableStateOf(false) }
    var pendingDeselect by remember { mutableStateOf<Category?>(null) }
    var pendingDeselectValue by remember { mutableStateOf("") }
    var moveTargetCatId by remember { mutableStateOf<Int?>(null) }

    // Sum mismatch dialog state
    var showSumMismatchDialog by remember { mutableStateOf(false) }
    var adjustTargetId by remember { mutableStateOf<String?>(null) }

    var singleAmountText by remember {
        mutableStateOf(
            if (editTransaction != null && editTransaction.categoryAmounts.size <= 1)
                formatAmount(editTransaction.amount, maxDecimals)
            else ""
        )
    }
    val categoryAmountTexts: SnapshotStateMap<Int, String> = remember {
        mutableStateMapOf<Int, String>().apply {
            editTransaction?.categoryAmounts?.forEach {
                put(it.categoryId, formatAmount(it.amount, maxDecimals))
            }
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    // Category picker dialog state
    var showCategoryPicker by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { focusManager.clearFocus() }
            ) {
                // Title bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isEdit && onDelete != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFF44336)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Auto-dismiss pie chart when < 2 categories
                    LaunchedEffect(selectedCats.size) {
                        if (selectedCats.size < 2) showPieChart = false
                    }

                    // Date field
                    OutlinedTextField(
                        value = selectedDate.format(dateFormatter),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = "Select date"
                                )
                            }
                        },
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Source/Merchant field
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        label = { Text(sourceLabel) },
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category selector — button that opens picker dialog
                    if (categories.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { showCategoryPicker = true }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (selectedCats.isEmpty()) {
                                Icon(
                                    imageVector = Icons.Filled.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Select categories...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            } else {
                                selectedCats.forEach { cat ->
                                    Icon(
                                        imageVector = getCategoryIcon(cat.iconName),
                                        contentDescription = cat.name,
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                // Amount field(s)
                if (selectedCats.size <= 1) {
                    // Validation: clear invalid input after 1 second
                    LaunchedEffect(singleAmountText) {
                        if (singleAmountText.isNotEmpty() && !isValidAmountInput(singleAmountText, maxDecimals)) {
                            delay(1000L)
                            singleAmountText = ""
                        }
                    }
                    OutlinedTextField(
                        value = singleAmountText,
                        onValueChange = { singleAmountText = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (maxDecimals > 0) KeyboardType.Decimal else KeyboardType.Number
                        ),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Multi-category mode

                    // Entry mode icons: Pie chart | Calculator (amounts) | % (percentage)
                    val totalFilled = totalAmountText.toDoubleOrNull()?.let { it > 0 } == true
                    val modeIconSize = 36.dp
                    val activeColor = MaterialTheme.colorScheme.primary
                    val inactiveColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pie chart mode
                        IconButton(onClick = {
                            if (!totalFilled) {
                                Toast.makeText(context, "Enter a total to enable this mode.", Toast.LENGTH_SHORT).show()
                            } else {
                                showPieChart = !showPieChart
                                if (showPieChart) usePercentage = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.PieChart,
                                contentDescription = "Pie chart mode",
                                tint = if (showPieChart) activeColor else inactiveColor,
                                modifier = Modifier.size(modeIconSize)
                            )
                        }

                        // Calculator (amounts) mode
                        IconButton(onClick = {
                            if (showPieChart || usePercentage) {
                                showPieChart = false
                                if (usePercentage) {
                                    // Convert percentages to amounts
                                    val total = totalAmountText.toDoubleOrNull()
                                    if (total != null && total > 0) {
                                        selectedCats.forEach { cat ->
                                            val pct = (categoryAmountTexts[cat.id] ?: "").toIntOrNull()
                                            if (pct != null) {
                                                categoryAmountTexts[cat.id] = formatAmount(total * pct / 100.0, maxDecimals)
                                            } else {
                                                categoryAmountTexts[cat.id] = ""
                                            }
                                        }
                                    } else {
                                        selectedCats.forEach { cat -> categoryAmountTexts[cat.id] = "" }
                                    }
                                    usePercentage = false
                                    lastEditedCatId = null
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Calculate,
                                contentDescription = "Amount mode",
                                tint = if (!showPieChart && !usePercentage) activeColor else inactiveColor,
                                modifier = Modifier.size(modeIconSize)
                            )
                        }

                        // Percentage mode
                        IconButton(onClick = {
                            if (!totalFilled) {
                                Toast.makeText(context, "Enter a total to enable this mode.", Toast.LENGTH_SHORT).show()
                            } else if (!usePercentage) {
                                showPieChart = false
                                // Convert amounts to percentages
                                val total = totalAmountText.toDoubleOrNull()
                                if (total != null && total > 0) {
                                    selectedCats.forEach { cat ->
                                        val amt = (categoryAmountTexts[cat.id] ?: "").toDoubleOrNull()
                                        if (amt != null) {
                                            categoryAmountTexts[cat.id] = (amt / total * 100).roundToInt().toString()
                                        } else {
                                            categoryAmountTexts[cat.id] = ""
                                        }
                                    }
                                } else {
                                    selectedCats.forEach { cat -> categoryAmountTexts[cat.id] = "" }
                                }
                                usePercentage = true
                                lastEditedCatId = null
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Percent,
                                contentDescription = "Percentage mode",
                                tint = if (usePercentage) activeColor else inactiveColor,
                                modifier = Modifier.size(modeIconSize)
                            )
                        }
                    }

                    // Total field (editable)
                    LaunchedEffect(totalAmountText) {
                        if (totalAmountText.isNotEmpty() && !isValidAmountInput(totalAmountText, maxDecimals)) {
                            delay(1000L)
                            totalAmountText = ""
                        }
                    }
                    OutlinedTextField(
                        value = totalAmountText,
                        onValueChange = { newVal ->
                            totalAmountText = newVal
                            // In amount mode: auto-fill last empty category if total is valid
                            if (!usePercentage && isValidAmountInput(newVal, maxDecimals)) {
                                val total = newVal.toDoubleOrNull()
                                if (total != null && total > 0) {
                                    val empty = selectedCats.filter {
                                        (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() == null
                                    }
                                    if (empty.size == 1) {
                                        val filledSum = selectedCats.filter { it.id != empty[0].id }.sumOf {
                                            (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() ?: 0.0
                                        }
                                        val remaining = total - filledSum
                                        if (remaining >= 0) {
                                            categoryAmountTexts[empty[0].id] = formatAmount(remaining, maxDecimals)
                                        }
                                    }
                                }
                            }
                        },
                        label = { Text("Total") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (maxDecimals > 0) KeyboardType.Decimal else KeyboardType.Number
                        ),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Pie chart mode or per-category text fields
                    if (showPieChart) {
                        val pieTotal = totalAmountText.toDoubleOrNull() ?: 0.0
                        val currentAmounts = selectedCats.associate { cat ->
                            cat.id to ((categoryAmountTexts[cat.id] ?: "").toDoubleOrNull() ?: 0.0)
                        }
                        PieChartEditor(
                            categories = selectedCats,
                            totalAmount = pieTotal,
                            maxDecimals = maxDecimals,
                            currencySymbol = currencySymbol,
                            categoryAmounts = currentAmounts,
                            onAmountsChanged = { newAmounts ->
                                newAmounts.forEach { (catId, amount) ->
                                    categoryAmountTexts[catId] = formatAmount(amount, maxDecimals)
                                }
                            }
                        )
                    } else {
                        // Per-category fields
                        selectedCats.forEach { cat ->
                            val catText = categoryAmountTexts[cat.id] ?: ""

                            // Validation: clear invalid input after 1 second
                            LaunchedEffect(cat.id, catText, usePercentage) {
                                if (catText.isNotEmpty()) {
                                    val valid = if (usePercentage) isValidPercentInput(catText)
                                        else isValidAmountInput(catText, maxDecimals)
                                    if (!valid) {
                                        delay(1000L)
                                        categoryAmountTexts[cat.id] = ""
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = catText,
                                onValueChange = { newVal ->
                                    categoryAmountTexts[cat.id] = newVal
                                    lastEditedCatId = cat.id

                                    if (usePercentage) {
                                        // Auto-fill last empty percentage
                                        if (isValidPercentInput(newVal) && newVal.isNotEmpty()) {
                                            val empty = selectedCats.filter {
                                                val t = categoryAmountTexts[it.id] ?: ""
                                                t.toIntOrNull()?.let { v -> v in 0..100 } != true
                                            }
                                            if (empty.size == 1) {
                                                val filledSum = selectedCats
                                                    .filter { it.id != empty[0].id }
                                                    .sumOf { (categoryAmountTexts[it.id] ?: "").toIntOrNull() ?: 0 }
                                                val remaining = 100 - filledSum
                                                if (remaining in 0..100) {
                                                    categoryAmountTexts[empty[0].id] = remaining.toString()
                                                }
                                            }
                                        }
                                    } else {
                                        // Amount mode auto-fill
                                        if (isValidAmountInput(newVal, maxDecimals) && newVal.isNotEmpty()) {
                                            val allFilled = selectedCats.all {
                                                (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() != null
                                            }
                                            if (allFilled && totalAmountText.isBlank()) {
                                                val sum = selectedCats.sumOf {
                                                    (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() ?: 0.0
                                                }
                                                if (sum > 0) totalAmountText = formatAmount(sum, maxDecimals)
                                            }
                                            if (!allFilled) {
                                                val total = totalAmountText.toDoubleOrNull()
                                                val empty = selectedCats.filter {
                                                    (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() == null
                                                }
                                                if (total != null && total > 0 && empty.size == 1) {
                                                    val filledSum = selectedCats
                                                        .filter { it.id != empty[0].id }
                                                        .sumOf { (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() ?: 0.0 }
                                                    val remaining = total - filledSum
                                                    if (remaining >= 0) {
                                                        categoryAmountTexts[empty[0].id] = formatAmount(remaining, maxDecimals)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                label = { Text(cat.name) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (usePercentage) KeyboardType.Number
                                        else (if (maxDecimals > 0) KeyboardType.Decimal else KeyboardType.Number)
                                ),
                                suffix = if (usePercentage) ({ Text("%") }) else null,
                                colors = textFieldColors,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Debounced proportional adjustment for percentage mode
                        if (usePercentage && lastEditedCatId != null) {
                            LaunchedEffect(lastEditedCatId, categoryAmountTexts.toMap()) {
                                val editedId = lastEditedCatId ?: return@LaunchedEffect
                                val allValid = selectedCats.all {
                                    val t = categoryAmountTexts[it.id] ?: ""
                                    t.toIntOrNull()?.let { v -> v in 0..100 } == true
                                }
                                if (allValid) {
                                    val sum = selectedCats.sumOf { (categoryAmountTexts[it.id] ?: "").toInt() }
                                    if (sum != 100) {
                                        delay(500L)
                                        val editedPct = (categoryAmountTexts[editedId] ?: "").toIntOrNull()
                                            ?: return@LaunchedEffect
                                        val otherCats = selectedCats.filter { it.id != editedId }
                                        val remaining = 100 - editedPct
                                        val otherSum = otherCats.sumOf {
                                            (categoryAmountTexts[it.id] ?: "").toInt()
                                        }
                                        if (remaining >= 0 && otherSum > 0) {
                                            val scaled = otherCats.map { c ->
                                                val oldPct = (categoryAmountTexts[c.id] ?: "").toInt()
                                                c.id to (oldPct.toDouble() / otherSum * remaining)
                                            }
                                            val rounded = scaled.map { (id, v) -> id to v.roundToInt() }
                                            val roundedSum = rounded.sumOf { it.second }
                                            val diff = remaining - roundedSum
                                            val adjusted = rounded.toMutableList()
                                            if (diff != 0 && adjusted.isNotEmpty()) {
                                                val maxIdx = adjusted.indices.maxByOrNull {
                                                    adjusted[it].second
                                                } ?: 0
                                                adjusted[maxIdx] = adjusted[maxIdx].let { (id, v) ->
                                                    id to (v + diff)
                                                }
                                            }
                                            adjusted.forEach { (id, v) ->
                                                categoryAmountTexts[id] = v.coerceIn(0, 100).toString()
                                            }
                                        } else if (remaining >= 0 && otherSum == 0) {
                                            val each = remaining / otherCats.size
                                            val extra = remaining % otherCats.size
                                            otherCats.forEachIndexed { i, c ->
                                                categoryAmountTexts[c.id] =
                                                    (each + if (i < extra) 1 else 0).toString()
                                            }
                                        }
                                        lastEditedCatId = null
                                    }
                                }
                            }
                        }
                    }
                }

                } // End scrollable content Column

                Spacer(modifier = Modifier.height(16.dp))

                // Button row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (source.isBlank()) return@TextButton
                            val type = if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME
                            val catAmounts: List<CategoryAmount>
                            val totalAmount: Double

                            if (selectedCats.size <= 1) {
                                val amt = singleAmountText.toDoubleOrNull() ?: return@TextButton
                                if (amt <= 0) return@TextButton
                                totalAmount = amt
                                catAmounts = if (selectedCats.size == 1) {
                                    listOf(CategoryAmount(selectedCats[0].id, amt))
                                } else emptyList()
                            } else {
                                if (usePercentage) {
                                    val total = totalAmountText.toDoubleOrNull() ?: return@TextButton
                                    if (total <= 0) return@TextButton
                                    catAmounts = selectedCats.mapNotNull { cat ->
                                        val pct = (categoryAmountTexts[cat.id] ?: "").toIntOrNull()
                                        if (pct != null && pct > 0) CategoryAmount(cat.id, total * pct / 100.0)
                                        else null
                                    }
                                    if (catAmounts.isEmpty()) return@TextButton
                                    totalAmount = total
                                } else {
                                    val total = totalAmountText.toDoubleOrNull()
                                    catAmounts = selectedCats.mapNotNull { cat ->
                                        val amt = (categoryAmountTexts[cat.id] ?: "").toDoubleOrNull()
                                        if (amt != null && amt > 0) CategoryAmount(cat.id, amt) else null
                                    }
                                    if (catAmounts.isEmpty()) return@TextButton
                                    val catSum = catAmounts.sumOf { it.amount }
                                    if (total != null && abs(catSum - total) > 0.005) {
                                        showSumMismatchDialog = true
                                        adjustTargetId = null
                                        return@TextButton
                                    }
                                    totalAmount = total ?: catSum
                                }
                            }

                            // Deselect zero-value categories (e.g. from pie chart)
                            if (showPieChart) {
                                selectedCats.forEach { cat ->
                                    val amt = (categoryAmountTexts[cat.id] ?: "").toDoubleOrNull() ?: 0.0
                                    if (amt <= 0.005) selectedCategoryIds[cat.id] = false
                                }
                            }

                            val id = editTransaction?.id
                                ?: generateTransactionId(existingIds)
                            onSave(
                                Transaction(
                                    id = id,
                                    type = type,
                                    date = selectedDate,
                                    source = source.trim(),
                                    categoryAmounts = catAmounts,
                                    amount = totalAmount
                                )
                            )
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    // Category picker dialog
    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Select Categories") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategoryIds[cat.id] == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    if (isSelected) {
                                        // Deselecting — check if move value dialog needed
                                        val catValue = categoryAmountTexts[cat.id] ?: ""
                                        val hasValue = if (usePercentage) catValue.toIntOrNull() != null
                                            else catValue.toDoubleOrNull() != null
                                        val otherSelected = selectedCats.filter { it.id != cat.id }
                                        val totalFilled = totalAmountText.toDoubleOrNull() != null
                                        val othersFilled = otherSelected.all {
                                            val t = categoryAmountTexts[it.id] ?: ""
                                            if (usePercentage) t.toIntOrNull() != null
                                            else t.toDoubleOrNull() != null
                                        }
                                        if (hasValue && totalFilled && othersFilled && otherSelected.isNotEmpty()) {
                                            pendingDeselect = cat
                                            pendingDeselectValue = catValue
                                            moveTargetCatId = null
                                            showMoveValueDialog = true
                                            showCategoryPicker = false
                                        } else {
                                            selectedCategoryIds[cat.id] = false
                                        }
                                    } else {
                                        selectedCategoryIds[cat.id] = true
                                        if (cat.id !in categoryAmountTexts) {
                                            categoryAmountTexts[cat.id] = ""
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(cat.iconName),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Deselect",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
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

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete", color = Color(0xFFF44336)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Move value dialog — shown when deselecting a category with a filled value
    if (showMoveValueDialog && pendingDeselect != null) {
        val deselectedCat = pendingDeselect!!
        val valueLabel = if (usePercentage) "$pendingDeselectValue%"
            else "$currencySymbol$pendingDeselectValue"

        AlertDialog(
            onDismissRequest = {
                showMoveValueDialog = false
                pendingDeselect = null
                moveTargetCatId = null
            },
            title = { Text("Move Category Value") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Where would you like to place $valueLabel from ${deselectedCat.name}?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(
                            categories.filter { it.id != deselectedCat.id }
                        ) { targetCat ->
                            val isTarget = moveTargetCatId == targetCat.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable { moveTargetCatId = targetCat.id }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getCategoryIcon(targetCat.iconName),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = targetCat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = moveTargetCatId ?: return@TextButton
                        val isTargetSelected = selectedCategoryIds[targetId] == true
                        if (isTargetSelected) {
                            // Sum with existing value
                            if (usePercentage) {
                                val existing = (categoryAmountTexts[targetId] ?: "").toIntOrNull() ?: 0
                                val moving = pendingDeselectValue.toIntOrNull() ?: 0
                                categoryAmountTexts[targetId] = (existing + moving).toString()
                            } else {
                                val existing = (categoryAmountTexts[targetId] ?: "").toDoubleOrNull() ?: 0.0
                                val moving = pendingDeselectValue.toDoubleOrNull() ?: 0.0
                                categoryAmountTexts[targetId] = formatAmount(existing + moving, maxDecimals)
                            }
                        } else {
                            // Select new category and set its value
                            selectedCategoryIds[targetId] = true
                            categoryAmountTexts[targetId] = pendingDeselectValue
                        }
                        // Deselect the original
                        selectedCategoryIds[deselectedCat.id] = false

                        // Handle transition to single-category mode
                        val newSelected = categories.filter { selectedCategoryIds[it.id] == true }
                        if (newSelected.size == 1) {
                            singleAmountText = totalAmountText.ifBlank {
                                categoryAmountTexts[newSelected[0].id] ?: ""
                            }
                        }

                        showMoveValueDialog = false
                        pendingDeselect = null
                        moveTargetCatId = null
                    },
                    enabled = moveTargetCatId != null
                ) {
                    Text("Move to")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMoveValueDialog = false
                    pendingDeselect = null
                    moveTargetCatId = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sum mismatch dialog — shown when saving with category sum != total in amount mode
    if (showSumMismatchDialog) {
        val mismatchCatSum = selectedCats.sumOf {
            (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() ?: 0.0
        }
        val mismatchTotal = totalAmountText.toDoubleOrNull() ?: 0.0

        AlertDialog(
            onDismissRequest = { showSumMismatchDialog = false; adjustTargetId = null },
            title = { Text("Sum Mismatch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Category amounts total $currencySymbol${formatAmount(mismatchCatSum, maxDecimals)}" +
                            " but Total is $currencySymbol${formatAmount(mismatchTotal, maxDecimals)}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Select field to adjust:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    // "Total" option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (adjustTargetId == "total") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable { adjustTargetId = "total" }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Category options
                    selectedCats.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (adjustTargetId == cat.id.toString())
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable { adjustTargetId = cat.id.toString() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(cat.iconName),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = adjustTargetId ?: return@TextButton
                        if (targetId == "total") {
                            totalAmountText = formatAmount(mismatchCatSum, maxDecimals)
                            showSumMismatchDialog = false
                            adjustTargetId = null
                        } else {
                            val catId = targetId.toIntOrNull() ?: return@TextButton
                            val otherSum = selectedCats.filter { it.id != catId }.sumOf {
                                (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() ?: 0.0
                            }
                            val newAmount = mismatchTotal - otherSum
                            if (newAmount < 0) {
                                Toast.makeText(context, "Unable to Fix", Toast.LENGTH_SHORT).show()
                            } else {
                                categoryAmountTexts[catId] = formatAmount(newAmount, maxDecimals)
                                showSumMismatchDialog = false
                                adjustTargetId = null
                            }
                        }
                    },
                    enabled = adjustTargetId != null
                ) {
                    Text("Auto-Adjust")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSumMismatchDialog = false
                    adjustTargetId = null
                }) {
                    Text("I'll adjust them")
                }
            }
        )
    }
}

@Composable
private fun TextSearchDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text Search") },
        text = {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search terms") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (query.isNotBlank()) onSearch(query.trim())
            }) { Text("Search") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AmountSearchDialog(
    onDismiss: () -> Unit,
    onSearch: (Double, Double) -> Unit
) {
    var minText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }
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
        title = { Text("Amount Search") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    label = { Text("Min Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    label = { Text("Max Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val min = minText.toDoubleOrNull() ?: 0.0
                val max = maxText.toDoubleOrNull() ?: Double.MAX_VALUE
                onSearch(min, max)
            }) { Text("Search") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDatePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis)
                        .atZone(ZoneId.of("UTC"))
                        .toLocalDate()
                    onDateSelected(date)
                }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
            )
            DatePicker(state = datePickerState)
        }
    }
}
