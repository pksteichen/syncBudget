package com.syncbudget.app.ui.screens

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalance
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.res.painterResource
import com.syncbudget.app.R
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.ThumbUpAlt
import com.syncbudget.app.ui.theme.AdAwareAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import com.syncbudget.app.ui.theme.AdAwareDatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import com.syncbudget.app.ui.theme.AdAwareDialog
import com.syncbudget.app.ui.theme.DialogStyle
import com.syncbudget.app.ui.theme.DialogPrimaryButton
import com.syncbudget.app.ui.theme.DialogSecondaryButton
import com.syncbudget.app.ui.theme.DialogDangerButton
import com.syncbudget.app.ui.theme.DialogWarningButton
import com.syncbudget.app.ui.theme.DialogHeader
import com.syncbudget.app.ui.theme.DialogFooter
import com.syncbudget.app.ui.theme.dialogHeaderColor
import com.syncbudget.app.ui.theme.dialogHeaderTextColor
import com.syncbudget.app.ui.theme.dialogFooterColor
import com.syncbudget.app.ui.theme.dialogSectionLabelColor
import com.syncbudget.app.ui.theme.LocalAppToast
import com.syncbudget.app.ui.theme.PulsingScrollArrow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncbudget.app.data.BankFormat
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.IncomeMode
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CryptoHelper
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.generateAmortizationEntryId
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionType
import com.syncbudget.app.data.autoCategorize
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.filterAlreadyLoadedDays
import com.syncbudget.app.data.findAmortizationMatch
import com.syncbudget.app.data.findBudgetIncomeMatch
import com.syncbudget.app.data.findDuplicate
import com.syncbudget.app.data.findRecurringExpenseMatch
import com.syncbudget.app.data.generateTransactionId
import com.syncbudget.app.data.getCategoryIcon
import com.syncbudget.app.data.isRecurringDateCloseEnough
import com.syncbudget.app.data.parseSyncBudgetCsv
import com.syncbudget.app.data.parseGenericCsv
import com.syncbudget.app.data.parseUsBank
import com.syncbudget.app.data.FullBackupSerializer
import com.syncbudget.app.data.serializeTransactionsCsv
import com.syncbudget.app.data.serializeTransactionsXlsx
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS
import com.syncbudget.app.ui.components.CURRENCY_SUFFIX_SYMBOLS
import com.syncbudget.app.ui.components.PieChartEditor
import com.syncbudget.app.ui.components.formatCurrency
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SaveFormat(val label: String) {
    CSV("CSV"),
    XLS("Excel (.xlsx)"),
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

fun formatAmount(value: Double, decimals: Int): String {
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
    isSubscriber: Boolean = false,
    recurringExpenses: List<RecurringExpense> = emptyList(),
    amortizationEntries: List<AmortizationEntry> = emptyList(),
    incomeSources: List<IncomeSource> = emptyList(),
    savingsGoals: List<SavingsGoal> = emptyList(),
    matchDays: Int = 7,
    matchPercent: Double = 1.0,
    matchDollar: Int = 1,
    matchChars: Int = 5,
    onAddTransaction: (Transaction) -> Unit,
    onUpdateTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onDeleteTransactions: (Set<Int>) -> Unit,
    chartPalette: String = "Bright",
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {},
    showAttribution: Boolean = false,
    deviceNameMap: Map<String, String> = emptyMap(),
    localDeviceId: String = "",
    onSerializeFullBackup: () -> String = { "" },
    onLoadFullBackup: (String) -> Unit = {},
    isSyncConfigured: Boolean = false,
    isSyncAdmin: Boolean = false,
    budgetPeriod: BudgetPeriod = BudgetPeriod.DAILY,
    incomeMode: IncomeMode = IncomeMode.FIXED,
    onAdjustIncomeAmount: (incomeSourceId: Int, newAmount: Double) -> Unit = { _, _ -> },
    onAddAmortization: ((AmortizationEntry) -> Unit)? = null,
    onDeleteAmortization: ((AmortizationEntry) -> Unit)? = null
) {
    val S = LocalStrings.current
    val customColors = LocalSyncBudgetColors.current
    val dateFormatter = remember(dateFormatPattern) {
        DateTimeFormatter.ofPattern(dateFormatPattern)
    }

    // Convert user-facing percent (e.g. 1.0 = 1%) to fraction (0.01)
    val percentTolerance = matchPercent / 100.0

    val pastSources = remember(transactions) {
        transactions.groupingBy { it.source }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
    }

    val toastState = LocalAppToast.current
    var viewFilter by remember { mutableStateOf(ViewFilter.ALL) }
    var showAddIncome by remember { mutableStateOf(false) }
    var showAddExpense by remember { mutableStateOf(false) }
    var showSearchMenu by remember { mutableStateOf(false) }

    // Search state
    var searchActive by remember { mutableStateOf(false) }
    var searchPredicate by remember { mutableStateOf<((Transaction) -> Boolean)?>(null) }

    // Category filter state
    var categoryFilterId by remember { mutableStateOf<Int?>(null) }

    // Effect explanation popup state
    var effectExplanationTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Search dialog states
    var showTextSearch by remember { mutableStateOf(false) }
    var showAmountSearch by remember { mutableStateOf(false) }
    var showDateSearchStart by remember { mutableStateOf(false) }
    var showDateSearchEnd by remember { mutableStateOf(false) }
    var dateSearchStart by remember { mutableStateOf<LocalDate?>(null) }
    var bankFilterOnly by remember { mutableStateOf(false) }

    // Edit state
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Multi-select state
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateMapOf<Int, Boolean>() }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkCategoryChange by remember { mutableStateOf(false) }
    var showBulkMerchantEdit by remember { mutableStateOf(false) }
    var showBulkVerify by remember { mutableStateOf(false) }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds.clear()
    }

    // Expanded multi-category rows
    val expandedIds = remember { mutableStateMapOf<Int, Boolean>() }

    // Auto-scroll to top when user adds transaction or loads backup
    val listState = rememberLazyListState()
    var scrollToTopTrigger by remember { mutableIntStateOf(0) }
    val addAndScroll: (Transaction) -> Unit = { txn ->
        onAddTransaction(txn)
        scrollToTopTrigger++
    }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

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
    var currentImportBudgetIncome by remember { mutableStateOf<IncomeSource?>(null) }

    // CSV Import state
    val context = LocalContext.current
    var showImportFormatDialog by remember { mutableStateOf(false) }
    var selectedBankFormat by remember { mutableStateOf(BankFormat.GENERIC_CSV) }
    var importStage by remember { mutableStateOf<ImportStage?>(null) }
    val parsedTransactions = remember { mutableStateListOf<Transaction>() }
    var totalFileTransactions by remember { mutableIntStateOf(0) }
    val importApproved = remember { mutableStateListOf<Transaction>() }
    var importIndex by remember { mutableIntStateOf(0) }
    var ignoreAllDuplicates by remember { mutableStateOf(false) }
    var skipDupForIndex by remember { mutableIntStateOf(-1) }  // skip dup check for this index (Keep Both)
    var currentImportDup by remember { mutableStateOf<Transaction?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importSkippedRows by remember { mutableIntStateOf(0) }
    var importTotalDataRows by remember { mutableIntStateOf(0) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    // Save state
    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedSaveFormat by remember { mutableStateOf(SaveFormat.CSV) }
    var savePassword by remember { mutableStateOf("") }
    var savePasswordConfirm by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Full backup state
    var includeAllData by remember { mutableStateOf(false) }
    var pendingFullBackupContent by remember { mutableStateOf<String?>(null) }
    var showFullBackupDialog by remember { mutableStateOf(false) }

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
            val toSave = if (selectionMode && selectedIds.any { it.value }) {
                transactions.filter { selectedIds[it.id] == true }
            } else { transactions }
            val csvContent = serializeTransactionsCsv(toSave)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(csvContent.toByteArray())
            }
            toastState.show(S.transactions.savedSuccessfully(toSave.size))
        } catch (e: Exception) {
            toastState.show("Save failed: ${e.message}")
        }
    }

    val xlsSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val toSave = if (selectionMode && selectedIds.any { it.value }) {
                transactions.filter { selectedIds[it.id] == true }
            } else { transactions }
            val xlsxBytes = serializeTransactionsXlsx(toSave, categories, currencySymbol)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(xlsxBytes)
            }
            toastState.show(S.transactions.savedSuccessfully(toSave.size))
        } catch (e: Exception) {
            toastState.show("Save failed: ${e.message}")
        }
    }

    val encryptedSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val contentToEncrypt = if (includeAllData) {
                onSerializeFullBackup()
            } else {
                val toSave = if (selectionMode && selectedIds.any { it.value }) {
                    transactions.filter { selectedIds[it.id] == true }
                } else { transactions }
                serializeTransactionsCsv(toSave)
            }
            val encrypted = CryptoHelper.encrypt(
                contentToEncrypt.toByteArray(),
                savePassword.toCharArray()
            )
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(encrypted)
            }
            savePassword = ""
            savePasswordConfirm = ""
            val msg = if (includeAllData) S.transactions.fullBackupSaved
                      else S.transactions.savedSuccessfully(transactions.size)
            toastState.show(msg)
            includeAllData = false
        } catch (e: Exception) {
            toastState.show("Encrypted save failed: ${e.message}")
        }
    }

    val jsonSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val content = onSerializeFullBackup()
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray())
            }
            toastState.show(S.transactions.fullBackupSaved)
            includeAllData = false
        } catch (e: Exception) {
            toastState.show("Save failed: ${e.message}")
        }
    }

    // Process file when URI is set — dispatch on IO thread to avoid ANR
    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        pendingUri = null
        importStage = ImportStage.PARSING

        try {
            val existingIdSet = transactions.map { it.id }.toSet()

            // Run file I/O and parsing on IO dispatcher to avoid ANR
            val result = withContext(Dispatchers.IO) {
                when (selectedBankFormat) {
                BankFormat.GENERIC_CSV -> {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext null
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val r = parseGenericCsv(reader, existingIdSet)
                    reader.close()
                    r
                }
                BankFormat.US_BANK -> {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext null
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val r = parseUsBank(reader, existingIdSet)
                    reader.close()
                    r
                }
                BankFormat.SECURESYNC_CSV -> {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext null
                    val textContent = inputStream.bufferedReader().use { it.readText() }
                    if (FullBackupSerializer.isFullBackup(textContent)) {
                        pendingFullBackupContent = textContent
                        showFullBackupDialog = true
                        importStage = null
                        return@withContext null
                    }
                    val reader = BufferedReader(textContent.reader())
                    val r = parseSyncBudgetCsv(reader, existingIdSet)
                    reader.close()
                    r
                }
                BankFormat.SECURESYNC_ENCRYPTED -> {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext null
                    try {
                        val encryptedBytes = inputStream.readBytes()
                        inputStream.close()
                        val decryptedBytes = CryptoHelper.decrypt(
                            encryptedBytes,
                            encryptedLoadPassword.toCharArray()
                        )
                        encryptedLoadPassword = ""
                        val textContent = String(decryptedBytes)
                        if (FullBackupSerializer.isFullBackup(textContent)) {
                            pendingFullBackupContent = textContent
                            showFullBackupDialog = true
                            importStage = null
                            return@withContext null
                        }
                        val reader = BufferedReader(textContent.reader())
                        val r = parseSyncBudgetCsv(reader, existingIdSet)
                        reader.close()
                        r
                    } catch (e: javax.crypto.AEADBadTagException) {
                        encryptedLoadPassword = ""
                        importError = "Wrong password or corrupted file"
                        importStage = ImportStage.PARSE_ERROR
                        return@withContext null
                    } catch (e: javax.crypto.BadPaddingException) {
                        encryptedLoadPassword = ""
                        importError = "Wrong password or corrupted file"
                        importStage = ImportStage.PARSE_ERROR
                        return@withContext null
                    }
                }
            } }  // close when + withContext

            if (result == null) {
                // Early return from withContext (file open failed, backup detected, etc.)
                if (importStage == ImportStage.PARSING) {
                    importError = "Could not open file"
                    importStage = ImportStage.PARSE_ERROR
                }
                return@LaunchedEffect
            }
            parsedTransactions.clear()
            parsedTransactions.addAll(result.transactions)
            importSkippedRows = result.skippedRows
            importTotalDataRows = result.totalDataRows

            if (result.error != null && result.transactions.isEmpty()) {
                importError = result.error
                importStage = ImportStage.PARSE_ERROR
            } else if (result.error != null) {
                importError = result.error
                importStage = ImportStage.PARSE_ERROR
            } else {
                // Auto-categorize only for bank imports (they lack categories)
                val processed = if (selectedBankFormat == BankFormat.US_BANK || selectedBankFormat == BankFormat.GENERIC_CSV) {
                    parsedTransactions.map { txn -> autoCategorize(txn, transactions, categories) }
                } else {
                    parsedTransactions.toList()
                }
                parsedTransactions.clear()
                parsedTransactions.addAll(processed)

                // Pre-filter days that are already fully loaded (date+amount multiset match)
                totalFileTransactions = parsedTransactions.size
                val filtered = filterAlreadyLoadedDays(parsedTransactions.toList(), transactions)
                parsedTransactions.clear()
                parsedTransactions.addAll(filtered)

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
    LaunchedEffect(importStage, importIndex, ignoreAllDuplicates, currentImportDup, currentImportRecurring, currentImportAmortization, currentImportBudgetIncome) {
        if (importStage != ImportStage.DUPLICATE_CHECK) return@LaunchedEffect
        if (currentImportDup != null || currentImportRecurring != null || currentImportAmortization != null || currentImportBudgetIncome != null) return@LaunchedEffect
        if (importIndex >= parsedTransactions.size) {
            // All done — add approved transactions
            importApproved.forEach { txn -> onAddTransaction(txn) }
            scrollToTopTrigger++
            val count = importApproved.size
            importApproved.clear()
            parsedTransactions.clear()
            importStage = ImportStage.COMPLETE
            val base = if (count == 0 && totalFileTransactions > 0) {
                S.transactions.allSkipped(totalFileTransactions)
            } else {
                S.transactions.loadedSuccessfully(count, totalFileTransactions)
            }
            val message = if (importSkippedRows > 0 && importTotalDataRows > 0) {
                "$base\n${S.transactions.rowsSkippedWarning(importSkippedRows, importTotalDataRows)}"
            } else base
            toastState.show(message)
            importStage = null
            return@LaunchedEffect
        }

        val txn = parsedTransactions[importIndex]

        // Check for duplicate (skip if ignoreAll or this specific index was cleared)
        val skipDup = ignoreAllDuplicates || skipDupForIndex == importIndex
        if (skipDupForIndex == importIndex) skipDupForIndex = -1  // one-shot

        if (!skipDup) {
            val dup = findDuplicate(txn, transactions, percentTolerance, matchDollar, matchDays, matchChars)
            if (dup != null) {
                currentImportDup = dup
                return@LaunchedEffect
            }
        }

        // Linking chain: recurring → amortization → budget income
        val recurringMatch = findRecurringExpenseMatch(txn, recurringExpenses, percentTolerance, matchDollar, matchChars, matchDays)
        if (recurringMatch != null) {
            currentImportRecurring = recurringMatch
        } else {
            val amortizationMatch = findAmortizationMatch(txn, amortizationEntries, percentTolerance, matchDollar, matchChars)
            if (amortizationMatch != null) {
                currentImportAmortization = amortizationMatch
            } else {
                val budgetIncomeMatch = findBudgetIncomeMatch(txn, incomeSources, matchChars, matchDays)
                if (budgetIncomeMatch != null) {
                    currentImportBudgetIncome = budgetIncomeMatch
                } else {
                    importApproved.add(txn)
                    importIndex++
                }
            }
        }
    }

    // Filter and sort transactions (no remember — SnapshotStateList mutations trigger recomposition)
    val filteredTransactions = run {
        var list = transactions.filter { !it.source.startsWith("Savings: ") }
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
                        text = S.transactions.title,
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
                            contentDescription = S.common.back,
                            tint = customColors.headerText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isSubscriber) showSaveDialog = true
                        else toastState.show(S.settings.subscribeToAccess)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = S.transactions.save,
                            tint = if (isSubscriber) customColors.headerText
                                   else customColors.headerText.copy(alpha = 0.35f)
                        )
                    }
                    IconButton(onClick = {
                        if (isSubscriber) showImportFormatDialog = true
                        else toastState.show(S.settings.subscribeToAccess)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.MoveToInbox,
                            contentDescription = S.transactions.load,
                            tint = if (isSubscriber) customColors.headerText
                                   else customColors.headerText.copy(alpha = 0.35f)
                        )
                    }
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
                    Text(when (viewFilter) {
                        ViewFilter.ALL -> S.transactions.all
                        ViewFilter.EXPENSES -> S.transactions.expensesFilter
                        ViewFilter.INCOME -> S.transactions.incomeFilter
                    })
                }

                IconButton(
                    onClick = { showAddIncome = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = S.transactions.addIncome,
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
                        contentDescription = S.transactions.addExpense,
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
                            contentDescription = S.transactions.search,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSearchMenu,
                        onDismissRequest = { showSearchMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(S.transactions.dateSearch) },
                            onClick = {
                                showSearchMenu = false
                                bankFilterOnly = false
                                showDateSearchStart = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(S.transactions.textSearch) },
                            onClick = {
                                showSearchMenu = false
                                showTextSearch = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(S.transactions.amountSearch) },
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
                        text = "${S.transactions.searchResults} \u2014 ${S.transactions.tapToClearSearch}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Category view bar
            if (categoryFilterId != null) {
                val filterCatName = categoryMap[categoryFilterId]?.name ?: S.transactions.unknown
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
                        text = "${S.transactions.filterByCategory(filterCatName)} \u2014 ${S.transactions.tapToClearFilter}",
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
                        text = S.transactions.selectAll,
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
                            contentDescription = S.transactions.changeCategory,
                            tint = customColors.headerBackground
                        )
                    }
                    IconButton(onClick = {
                        if (selectedIds.any { it.value }) {
                            showBulkMerchantEdit = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = S.transactions.editMerchant,
                            tint = customColors.headerBackground
                        )
                    }
                    IconButton(onClick = {
                        if (selectedIds.any { it.value }) {
                            showBulkVerify = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.ThumbUpAlt,
                            contentDescription = S.transactions.verifiedToast,
                            tint = Color(0xFF2E7D32)
                        )
                    }
                    IconButton(onClick = {
                        if (selectedIds.any { it.value }) {
                            showBulkDeleteConfirm = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = S.transactions.deleteSelected,
                            tint = Color(0xFFF44336)
                        )
                    }
                    IconButton(onClick = {
                        selectionMode = false
                        selectedIds.clear()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = S.common.close,
                            tint = customColors.headerBackground
                        )
                    }
                }
            }

            // Transaction list
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(filteredTransactions, key = { it.id }) { transaction ->
                    val isLinkedRecurring = transaction.linkedRecurringExpenseId != null
                    val linkedRecurringAmount = if (isLinkedRecurring) {
                        if (transaction.linkedRecurringExpenseAmount > 0.0) transaction.linkedRecurringExpenseAmount
                        else recurringExpenses.find { it.id == transaction.linkedRecurringExpenseId }?.amount
                    } else null
                    val isLinkedIncome = transaction.linkedIncomeSourceId != null
                    val linkedIncomeAmount = if (isLinkedIncome) {
                        if (transaction.linkedIncomeSourceAmount > 0.0) transaction.linkedIncomeSourceAmount
                        else incomeSources.find { it.id == transaction.linkedIncomeSourceId }?.amount
                    } else null
                    val isLinkedSavingsGoal = transaction.linkedSavingsGoalId != null || transaction.linkedSavingsGoalAmount > 0.0
                    val isLinkedAmortization = transaction.linkedAmortizationEntryId != null
                    val amortEntry = if (isLinkedAmortization)
                        amortizationEntries.find { it.id == transaction.linkedAmortizationEntryId }
                    else null
                    val amortElapsed = if (amortEntry != null) {
                        val today = LocalDate.now()
                        when (budgetPeriod) {
                            BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(amortEntry.startDate, today).toInt()
                            BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(amortEntry.startDate, today).toInt()
                            BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(amortEntry.startDate, today).toInt()
                        }
                    } else 0
                    val isAmortComplete = amortEntry != null && amortElapsed >= amortEntry.totalPeriods
                    val linkedAmortizationApplied = if (amortEntry != null) {
                        val periods = minOf(amortElapsed, amortEntry.totalPeriods)
                        (amortEntry.amount / amortEntry.totalPeriods) * periods
                    } else null
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
                        },
                        attributionLabel = if (showAttribution && transaction.deviceId.isNotEmpty()) {
                            if (transaction.deviceId == localDeviceId) S.sync.you
                            else deviceNameMap[transaction.deviceId] ?: transaction.deviceId.take(8)
                        } else null,
                        isLinkedRecurring = isLinkedRecurring,
                        isLinkedAmortization = isLinkedAmortization,
                        isAmortComplete = isAmortComplete,
                        isLinkedIncome = isLinkedIncome,
                        linkedRecurringAmount = linkedRecurringAmount,
                        linkedAmortizationApplied = linkedAmortizationApplied,
                        linkedIncomeAmount = linkedIncomeAmount,
                        incomeMode = incomeMode,
                        isLinkedSavingsGoal = isLinkedSavingsGoal,
                        onEffectTap = { effectExplanationTransaction = transaction }
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
            title = S.common.addNewIncomeTransaction,
            sourceLabel = S.common.sourceLabel,
            categories = categories,
            existingIds = existingIds,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            chartPalette = chartPalette,
            recurringExpenses = recurringExpenses,
            amortizationEntries = amortizationEntries,
            incomeSources = incomeSources,
            savingsGoals = savingsGoals,
            pastSources = pastSources,
            budgetPeriod = budgetPeriod,
            onDismiss = { showAddIncome = false },
            onSave = { txn ->
                val alreadyLinked = txn.linkedRecurringExpenseId != null || txn.linkedAmortizationEntryId != null || txn.linkedIncomeSourceId != null || txn.linkedSavingsGoalId != null
                val dup = findDuplicate(txn, transactions, percentTolerance, matchDollar, matchDays, matchChars)
                if (dup != null) {
                    pendingManualSave = txn
                    manualDuplicateMatch = dup
                    pendingManualIsEdit = false
                    showManualDuplicateDialog = true
                } else if (alreadyLinked) {
                    addAndScroll(txn)
                } else {
                    val recurringMatch = findRecurringExpenseMatch(txn, recurringExpenses, percentTolerance, matchDollar, matchChars, matchDays)
                    if (recurringMatch != null) {
                        pendingRecurringTxn = txn
                        pendingRecurringMatch = recurringMatch
                        pendingRecurringIsEdit = false
                        showRecurringDialog = true
                    } else {
                        val amortizationMatch = findAmortizationMatch(txn, amortizationEntries, percentTolerance, matchDollar, matchChars)
                        if (amortizationMatch != null) {
                            pendingAmortizationTxn = txn
                            pendingAmortizationMatch = amortizationMatch
                            pendingAmortizationIsEdit = false
                            showAmortizationDialog = true
                        } else {
                            val budgetMatch = findBudgetIncomeMatch(txn, incomeSources, matchChars, matchDays)
                            if (budgetMatch != null) {
                                pendingBudgetIncomeTxn = txn
                                pendingBudgetIncomeMatch = budgetMatch
                                pendingBudgetIncomeIsEdit = false
                                showBudgetIncomeDialog = true
                            } else {
                                addAndScroll(txn)
                            }
                        }
                    }
                }
                showAddIncome = false
            },
            onAddAmortization = onAddAmortization,
            onDeleteAmortization = onDeleteAmortization
        )
    }

    // Add Expense dialog
    if (showAddExpense) {
        TransactionDialog(
            title = S.common.addNewExpenseTransaction,
            sourceLabel = S.common.merchantLabel,
            categories = categories,
            existingIds = existingIds,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            isExpense = true,
            chartPalette = chartPalette,
            recurringExpenses = recurringExpenses,
            amortizationEntries = amortizationEntries,
            incomeSources = incomeSources,
            savingsGoals = savingsGoals,
            pastSources = pastSources,
            budgetPeriod = budgetPeriod,
            onDismiss = { showAddExpense = false },
            onSave = { txn ->
                val alreadyLinked = txn.linkedRecurringExpenseId != null || txn.linkedAmortizationEntryId != null || txn.linkedIncomeSourceId != null || txn.linkedSavingsGoalId != null
                val dup = findDuplicate(txn, transactions, percentTolerance, matchDollar, matchDays, matchChars)
                if (dup != null) {
                    pendingManualSave = txn
                    manualDuplicateMatch = dup
                    pendingManualIsEdit = false
                    showManualDuplicateDialog = true
                } else if (alreadyLinked) {
                    addAndScroll(txn)
                } else {
                    val recurringMatch = findRecurringExpenseMatch(txn, recurringExpenses, percentTolerance, matchDollar, matchChars, matchDays)
                    if (recurringMatch != null) {
                        pendingRecurringTxn = txn
                        pendingRecurringMatch = recurringMatch
                        pendingRecurringIsEdit = false
                        showRecurringDialog = true
                    } else {
                        val amortizationMatch = findAmortizationMatch(txn, amortizationEntries, percentTolerance, matchDollar, matchChars)
                        if (amortizationMatch != null) {
                            pendingAmortizationTxn = txn
                            pendingAmortizationMatch = amortizationMatch
                            pendingAmortizationIsEdit = false
                            showAmortizationDialog = true
                        } else {
                            addAndScroll(txn)
                        }
                    }
                }
                showAddExpense = false
            },
            onAddAmortization = onAddAmortization,
            onDeleteAmortization = onDeleteAmortization
        )
    }

    // Edit dialog
    editingTransaction?.let { txn ->
        TransactionDialog(
            title = S.transactions.editTransaction,
            sourceLabel = if (txn.type == TransactionType.EXPENSE) S.common.merchantLabel else S.common.sourceLabel,
            categories = categories,
            existingIds = existingIds,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            isExpense = txn.type == TransactionType.EXPENSE,
            editTransaction = txn,
            chartPalette = chartPalette,
            recurringExpenses = recurringExpenses,
            amortizationEntries = amortizationEntries,
            incomeSources = incomeSources,
            savingsGoals = savingsGoals,
            pastSources = pastSources,
            budgetPeriod = budgetPeriod,
            onDismiss = { editingTransaction = null },
            onSave = { updated ->
                // Only run duplicate/matching checks if merchant, date, or amount changed
                val orig = txn
                val coreChanged = updated.source != orig.source || updated.date != orig.date || updated.amount != orig.amount
                if (!coreChanged) {
                    onUpdateTransaction(updated)
                } else {
                    val alreadyLinked = updated.linkedRecurringExpenseId != null || updated.linkedAmortizationEntryId != null || updated.linkedIncomeSourceId != null || updated.linkedSavingsGoalId != null
                    val dup = findDuplicate(updated, transactions.filter { it.id != updated.id }, percentTolerance, matchDollar, matchDays, matchChars)
                    if (dup != null) {
                        pendingManualSave = updated
                        manualDuplicateMatch = dup
                        pendingManualIsEdit = true
                        showManualDuplicateDialog = true
                    } else if (alreadyLinked) {
                        onUpdateTransaction(updated)
                    } else {
                        val recurringMatch = findRecurringExpenseMatch(updated, recurringExpenses, percentTolerance, matchDollar, matchChars, matchDays)
                        if (recurringMatch != null) {
                            pendingRecurringTxn = updated
                            pendingRecurringMatch = recurringMatch
                            pendingRecurringIsEdit = true
                            showRecurringDialog = true
                        } else {
                            val amortizationMatch = findAmortizationMatch(updated, amortizationEntries, percentTolerance, matchDollar, matchChars)
                            if (amortizationMatch != null) {
                                pendingAmortizationTxn = updated
                                pendingAmortizationMatch = amortizationMatch
                                pendingAmortizationIsEdit = true
                                showAmortizationDialog = true
                            } else {
                                val budgetMatch = findBudgetIncomeMatch(updated, incomeSources, matchChars, matchDays)
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
                }
                editingTransaction = null
            },
            onDelete = { onDeleteTransaction(txn); editingTransaction = null },
            onAddAmortization = onAddAmortization,
            onDeleteAmortization = onDeleteAmortization
        )
    }

    // Text search dialog
    if (showTextSearch) {
        TextSearchDialog(
            onDismiss = { showTextSearch = false },
            onSearch = { query ->
                searchPredicate = { t -> t.source.contains(query, ignoreCase = true) || t.description.contains(query, ignoreCase = true) }
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
            title = S.transactions.dateRangeSearch,
            headline = S.transactions.startDate,
            confirmLabel = S.common.next,
            onDismiss = { showDateSearchStart = false },
            onDateSelected = { date ->
                dateSearchStart = date
                showDateSearchStart = false
                showDateSearchEnd = true
            },
            bankFilterChecked = bankFilterOnly,
            onBankFilterChanged = { bankFilterOnly = it },
            showBankFilter = isSubscriber
        )
    }

    // Date search - end
    if (showDateSearchEnd) {
        SearchDatePickerDialog(
            title = S.transactions.dateRangeSearch,
            headline = S.transactions.endDate,
            confirmLabel = S.transactions.search,
            onDismiss = { showDateSearchEnd = false; dateSearchStart = null },
            onDateSelected = { endDate ->
                val start = dateSearchStart
                if (start != null) {
                    val bankOnly = bankFilterOnly
                    searchPredicate = if (bankOnly) {
                        { t -> !t.date.isBefore(start) && !t.date.isAfter(endDate) && !t.isUserCategorized }
                    } else {
                        { t -> !t.date.isBefore(start) && !t.date.isAfter(endDate) }
                    }
                    searchActive = true
                viewFilter = ViewFilter.ALL
                }
                showDateSearchEnd = false
                dateSearchStart = null
            },
            onBack = {
                showDateSearchEnd = false
                showDateSearchStart = true
            },
            bankFilterChecked = bankFilterOnly,
            onBankFilterChanged = { bankFilterOnly = it },
            showBankFilter = isSubscriber
        )
    }

    // Bulk delete confirmation
    if (showBulkDeleteConfirm) {
        val count = selectedIds.count { it.value }
        val isAllWithoutSearch = allSelected && !searchActive && categoryFilterId == null
        AdAwareAlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            style = DialogStyle.DANGER,
            title = {
                Text(
                    if (isAllWithoutSearch) "WARNING" else "${S.common.delete}?"
                )
            },
            text = {
                Text(
                    if (isAllWithoutSearch)
                        "This will permanently delete ALL selected transactions in the current view. This action cannot be undone."
                    else
                        S.transactions.selectedCount(count)
                )
            },
            confirmButton = {
                DialogDangerButton(onClick = {
                    val idsToDelete = selectedIds.filter { it.value }.keys
                    onDeleteTransactions(idsToDelete)
                    selectedIds.clear()
                    selectionMode = false
                    showBulkDeleteConfirm = false
                }) {
                    Text(S.common.delete)
                }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // Bulk verify/unverify dialog
    if (showBulkVerify) {
        val count = selectedIds.count { it.value }
        MatchDialogCard(
            title = S.transactions.bulkVerifyTitle,
            onDismiss = { showBulkVerify = false },
            buttons = {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DialogSecondaryButton(
                        onClick = { showBulkVerify = false }
                    ) { Text(S.common.cancel, maxLines = 1) }
                    DialogPrimaryButton(
                        onClick = {
                            val idsToChange = selectedIds.filter { it.value }.keys
                            transactions.filter { it.id in idsToChange }.forEach { txn ->
                                onUpdateTransaction(txn.copy(isUserCategorized = true))
                            }
                            selectedIds.clear()
                            selectionMode = false
                            showBulkVerify = false
                        }
                    ) { Text(S.transactions.markVerified, maxLines = 1) }
                    DialogPrimaryButton(
                        onClick = {
                            val idsToChange = selectedIds.filter { it.value }.keys
                            transactions.filter { it.id in idsToChange }.forEach { txn ->
                                onUpdateTransaction(txn.copy(isUserCategorized = false))
                            }
                            selectedIds.clear()
                            selectionMode = false
                            showBulkVerify = false
                        }
                    ) { Text(S.transactions.markUnverified, maxLines = 1) }
                }
            }
        ) {
            Text(S.transactions.bulkVerifyMessage(count))
        }
    }

    // Bulk category change dialog
    if (showBulkCategoryChange) {
        var bulkSelectedCatId by remember { mutableStateOf<Int?>(null) }
        AdAwareAlertDialog(
            onDismissRequest = {
                showBulkCategoryChange = false
            },
            title = { Text(S.transactions.changeCategory) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val count = selectedIds.count { it.value }
                    Text(
                        text = S.transactions.selectedCount(count),
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
                DialogPrimaryButton(
                    onClick = {
                        val catId = bulkSelectedCatId ?: return@DialogPrimaryButton
                        val idsToChange = selectedIds.filter { it.value }.keys
                        transactions.filter { it.id in idsToChange }.forEach { txn ->
                            onUpdateTransaction(
                                txn.copy(
                                    categoryAmounts = listOf(CategoryAmount(catId, txn.amount))
                                )
                            )
                        }
                        selectedIds.clear()
                        selectionMode = false
                        showBulkCategoryChange = false
                    },
                    enabled = bulkSelectedCatId != null
                ) {
                    Text(S.common.save)
                }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { showBulkCategoryChange = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // Bulk merchant/source edit dialog
    if (showBulkMerchantEdit) {
        var newMerchant by remember { mutableStateOf("") }
        val count = selectedIds.count { it.value }
        AdAwareDialog(
            onDismissRequest = { showBulkMerchantEdit = false },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).imePadding(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column {
                    DialogHeader(S.transactions.editMerchant)
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            text = S.transactions.selectedCount(count),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newMerchant,
                            onValueChange = { newMerchant = it },
                            label = { Text(S.transactions.newMerchantName) },
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
                    DialogFooter {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DialogSecondaryButton(onClick = { showBulkMerchantEdit = false }) {
                                Text(S.common.cancel, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            DialogPrimaryButton(
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
                                Text(S.common.save)
                            }
                        }
                    }
                }
            }
        }
    }

    // Save dialog
    if (showSaveDialog) {
        AdAwareDialog(
            onDismissRequest = {
                showSaveDialog = false
                savePassword = ""
                savePasswordConfirm = ""
                saveError = null
            },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).imePadding(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                val saveScrollState = rememberScrollState()
                Box {
                Column {
                    DialogHeader(S.transactions.saveTransactions)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(saveScrollState)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(S.transactions.format, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        SaveFormat.entries.forEach { format ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedSaveFormat = format
                                        if (format == SaveFormat.XLS) includeAllData = false
                                        savePassword = ""
                                        savePasswordConfirm = ""
                                        saveError = null
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedSaveFormat == format,
                                    onClick = {
                                        selectedSaveFormat = format
                                        if (format == SaveFormat.XLS) includeAllData = false
                                        savePassword = ""
                                        savePasswordConfirm = ""
                                        saveError = null
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (format == SaveFormat.ENCRYPTED) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(when (format) {
                                    SaveFormat.CSV -> S.transactions.csv
                                    SaveFormat.XLS -> S.transactions.xls
                                    SaveFormat.ENCRYPTED -> S.transactions.encrypted
                                }, style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        val backupEnabled = selectedSaveFormat != SaveFormat.XLS
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (backupEnabled) Modifier.clickable { includeAllData = !includeAllData } else Modifier)
                                .let { if (!backupEnabled) it.alpha(0.4f) else it }
                        ) {
                            Checkbox(
                                checked = includeAllData,
                                onCheckedChange = if (backupEnabled) ({ includeAllData = it }) else null,
                                enabled = backupEnabled
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(S.transactions.includeAllData, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (includeAllData) {
                            Text(S.transactions.fullBackupNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        } else {
                            val transactionsToSave = if (selectionMode && selectedIds.any { it.value })
                                transactions.filter { selectedIds[it.id] == true } else transactions
                            Text(S.transactions.selectedCount(transactionsToSave.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        }

                        if (selectedSaveFormat == SaveFormat.ENCRYPTED) {
                            val pwFieldColors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            if (saveError != null) {
                                Text(
                                    text = saveError!!,
                                    color = Color(0xFFF44336),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            OutlinedTextField(
                                value = savePassword,
                                onValueChange = { savePassword = it; saveError = null },
                                label = { Text(S.transactions.passwordMinLength) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = pwFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = savePasswordConfirm,
                                onValueChange = { savePasswordConfirm = it; saveError = null },
                                label = { Text(S.transactions.confirmPassword) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = pwFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    DialogFooter {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DialogSecondaryButton(onClick = {
                                showSaveDialog = false
                                savePassword = ""
                                savePasswordConfirm = ""
                                saveError = null
                            }) { Text(S.common.cancel) }
                            Spacer(modifier = Modifier.width(8.dp))
                            DialogPrimaryButton(onClick = {
                                when {
                                    includeAllData && selectedSaveFormat == SaveFormat.CSV -> {
                                        showSaveDialog = false
                                        jsonSaveLauncher.launch("budgetrak_backup.json")
                                    }
                                    includeAllData && selectedSaveFormat == SaveFormat.ENCRYPTED -> {
                                        when {
                                            savePassword.length < 8 -> {
                                                saveError = S.transactions.passwordMinLength
                                            }
                                            savePassword != savePasswordConfirm -> {
                                                saveError = S.transactions.passwordsMustMatch
                                            }
                                            else -> {
                                                showSaveDialog = false
                                                encryptedSaveLauncher.launch("budgetrak_backup.enc")
                                            }
                                        }
                                    }
                                    selectedSaveFormat == SaveFormat.CSV -> {
                                        showSaveDialog = false
                                        csvSaveLauncher.launch("budgetrak_transactions.csv")
                                    }
                                    selectedSaveFormat == SaveFormat.XLS -> {
                                        showSaveDialog = false
                                        xlsSaveLauncher.launch("budgetrak_transactions.xlsx")
                                    }
                                    selectedSaveFormat == SaveFormat.ENCRYPTED -> {
                                        when {
                                            savePassword.length < 8 -> {
                                                saveError = S.transactions.passwordMinLength
                                            }
                                            savePassword != savePasswordConfirm -> {
                                                saveError = S.transactions.passwordsMustMatch
                                            }
                                            else -> {
                                                showSaveDialog = false
                                                encryptedSaveLauncher.launch("budgetrak_transactions.enc")
                                            }
                                        }
                                    }
                                }
                            }) { Text(S.common.save) }
                        }
                    }
                }
                PulsingScrollArrow(
                    scrollState = saveScrollState,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 50.dp)
                )
                }
            }
        }
    }

    // Full backup load confirmation dialog
    if (showFullBackupDialog && pendingFullBackupContent != null) {
        AdAwareAlertDialog(
            onDismissRequest = {
                showFullBackupDialog = false
                pendingFullBackupContent = null
            },
            style = DialogStyle.WARNING,
            title = { Text(S.transactions.fullBackupDetected) },
            text = {
                Column {
                    Text(S.transactions.fullBackupBody)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        S.transactions.fullRestoreWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (isSyncConfigured) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (isSyncAdmin) S.transactions.fullBackupSyncWarning
                            else S.transactions.fullBackupNonAdminBlock,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (isSyncConfigured && !isSyncAdmin) {
                    Column {
                        DialogWarningButton(onClick = {}, enabled = false) {
                            Text(S.transactions.loadAllDataOverwrite)
                        }
                        Text(
                            S.transactions.fullBackupNonAdminBlock,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                } else {
                    var restoreBtnYPx by remember { mutableIntStateOf(0) }
                    DialogWarningButton(
                        onClick = {
                            onLoadFullBackup(pendingFullBackupContent!!)
                            scrollToTopTrigger++
                            showFullBackupDialog = false
                            pendingFullBackupContent = null
                            toastState.show(S.transactions.fullBackupRestored, restoreBtnYPx)
                        },
                        modifier = Modifier.onGloballyPositioned { restoreBtnYPx = it.positionInWindow().y.toInt() }
                    ) { Text(S.transactions.loadAllDataOverwrite) }
                }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = {
                    val existingIdSet = transactions.map { it.id }.toSet()
                    val result = FullBackupSerializer.extractTransactions(
                        pendingFullBackupContent!!, existingIdSet
                    )
                    parsedTransactions.clear()
                    parsedTransactions.addAll(result.transactions)
                    showFullBackupDialog = false
                    pendingFullBackupContent = null
                    if (result.error != null && result.transactions.isEmpty()) {
                        importError = result.error
                        importStage = ImportStage.PARSE_ERROR
                    } else {
                        totalFileTransactions = parsedTransactions.size
                        val filtered = filterAlreadyLoadedDays(parsedTransactions.toList(), transactions)
                        parsedTransactions.clear()
                        parsedTransactions.addAll(filtered)
                        importApproved.clear()
                        importIndex = 0
                        ignoreAllDuplicates = false
                        importStage = ImportStage.DUPLICATE_CHECK
                    }
                }) { Text(S.transactions.loadTransactionsOnly) }
            }
        )
    }

    // Import / Load format selection dialog
    if (showImportFormatDialog) {
        AdAwareDialog(
            onDismissRequest = {
                showImportFormatDialog = false
                encryptedLoadPassword = ""
            },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).imePadding(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                val loadScrollState = rememberScrollState()
                Box {
                Column {
                    DialogHeader(S.transactions.loadTransactions)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(loadScrollState)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(S.transactions.format, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        BankFormat.entries.forEach { format ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedBankFormat = format
                                        encryptedLoadPassword = ""
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedBankFormat == format,
                                    onClick = {
                                        selectedBankFormat = format
                                        encryptedLoadPassword = ""
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(when (format) {
                                    BankFormat.GENERIC_CSV -> S.transactions.formatGenericCsv
                                    BankFormat.US_BANK -> S.transactions.formatUsBank
                                    BankFormat.SECURESYNC_CSV -> S.transactions.formatBudgeTrakCsv
                                    BankFormat.SECURESYNC_ENCRYPTED -> S.transactions.formatBudgeTrakEncrypted
                                }, style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        if (selectedBankFormat == BankFormat.SECURESYNC_ENCRYPTED) {
                            OutlinedTextField(
                                value = encryptedLoadPassword,
                                onValueChange = { encryptedLoadPassword = it },
                                label = { Text(S.transactions.password) },
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

                    DialogFooter {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DialogSecondaryButton(onClick = {
                                showImportFormatDialog = false
                                encryptedLoadPassword = ""
                            }) { Text(S.common.cancel) }
                            Spacer(modifier = Modifier.width(8.dp))
                            val canProceed = when (selectedBankFormat) {
                                BankFormat.SECURESYNC_ENCRYPTED -> encryptedLoadPassword.length >= 8
                                else -> true
                            }
                            DialogPrimaryButton(
                                onClick = {
                                    showImportFormatDialog = false
                                    filePickerLauncher.launch(arrayOf("text/*", "*/*"))
                                },
                                enabled = canProceed
                            ) { Text(S.transactions.selectFile) }
                        }
                    }
                }
                PulsingScrollArrow(
                    scrollState = loadScrollState,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 50.dp)
                )
                }
            }
        }
    }

    // Parse error dialog
    if (importStage == ImportStage.PARSE_ERROR) {
        AdAwareAlertDialog(
            onDismissRequest = {
                importStage = null
                parsedTransactions.clear()
                importError = null
            },
            title = { Text(S.transactions.parseError) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(importError ?: S.transactions.unknownError)
                    if (parsedTransactions.isNotEmpty()) {
                        Text(S.transactions.parsedBeforeError(parsedTransactions.size))
                    }
                    if (importSkippedRows > 0 && importTotalDataRows > 0) {
                        Text(S.transactions.rowsSkippedWarning(importSkippedRows, importTotalDataRows))
                    }
                }
            },
            style = DialogStyle.WARNING,
            confirmButton = {
                if (parsedTransactions.isNotEmpty()) {
                    DialogPrimaryButton(onClick = {
                        val categorized = parsedTransactions.map { txn ->
                            autoCategorize(txn, transactions, categories)
                        }
                        parsedTransactions.clear()
                        parsedTransactions.addAll(categorized)
                        totalFileTransactions = parsedTransactions.size
                        val filtered = filterAlreadyLoadedDays(parsedTransactions.toList(), transactions)
                        parsedTransactions.clear()
                        parsedTransactions.addAll(filtered)
                        importApproved.clear()
                        importIndex = 0
                        ignoreAllDuplicates = false
                        importError = null
                        importStage = ImportStage.DUPLICATE_CHECK
                    }) { Text(S.transactions.keep) }
                }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = {
                    parsedTransactions.clear()
                    importError = null
                    importStage = null
                }) { Text(S.common.delete) }
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
                // Keep Both — skip dup check, continue to linking chain
                skipDupForIndex = importIndex
                currentImportDup = null
            },
            onKeepNew = {
                onDeleteTransaction(existingDup)
                // Skip dup check on re-run, continue to linking chain
                skipDupForIndex = importIndex
                currentImportDup = null
            },
            onKeepExisting = {
                currentImportDup = null
                importIndex++
            },
            onIgnoreAll = {
                // Skip dup check on re-run, continue to linking chain
                skipDupForIndex = importIndex
                currentImportDup = null
                ignoreAllDuplicates = true
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
                // Keep Both — continue to linking chain
                val txn = pendingManualSave!!
                val isEdit = pendingManualIsEdit
                pendingManualSave = null
                manualDuplicateMatch = null
                showManualDuplicateDialog = false
                val alreadyLinked = txn.linkedRecurringExpenseId != null || txn.linkedAmortizationEntryId != null || txn.linkedIncomeSourceId != null || txn.linkedSavingsGoalId != null
                if (!alreadyLinked) {
                    val recurringMatch = findRecurringExpenseMatch(txn, recurringExpenses, percentTolerance, matchDollar, matchChars, matchDays)
                    if (recurringMatch != null) {
                        pendingRecurringTxn = txn
                        pendingRecurringMatch = recurringMatch
                        pendingRecurringIsEdit = isEdit
                        showRecurringDialog = true
                    } else {
                        val amortizationMatch = findAmortizationMatch(txn, amortizationEntries, percentTolerance, matchDollar, matchChars)
                        if (amortizationMatch != null) {
                            pendingAmortizationTxn = txn
                            pendingAmortizationMatch = amortizationMatch
                            pendingAmortizationIsEdit = isEdit
                            showAmortizationDialog = true
                        } else {
                            val budgetMatch = findBudgetIncomeMatch(txn, incomeSources, matchChars, matchDays)
                            if (budgetMatch != null) {
                                pendingBudgetIncomeTxn = txn
                                pendingBudgetIncomeMatch = budgetMatch
                                pendingBudgetIncomeIsEdit = isEdit
                                showBudgetIncomeDialog = true
                            } else {
                                if (isEdit) onUpdateTransaction(txn) else addAndScroll(txn)
                            }
                        }
                    }
                } else {
                    if (isEdit) onUpdateTransaction(txn) else addAndScroll(txn)
                }
            },
            onKeepNew = {
                onDeleteTransaction(manualDuplicateMatch!!)
                // Continue to linking chain after deleting existing
                val txn = pendingManualSave!!
                val isEdit = pendingManualIsEdit
                pendingManualSave = null
                manualDuplicateMatch = null
                showManualDuplicateDialog = false
                val alreadyLinked = txn.linkedRecurringExpenseId != null || txn.linkedAmortizationEntryId != null || txn.linkedIncomeSourceId != null || txn.linkedSavingsGoalId != null
                if (!alreadyLinked) {
                    val recurringMatch = findRecurringExpenseMatch(txn, recurringExpenses, percentTolerance, matchDollar, matchChars, matchDays)
                    if (recurringMatch != null) {
                        pendingRecurringTxn = txn
                        pendingRecurringMatch = recurringMatch
                        pendingRecurringIsEdit = isEdit
                        showRecurringDialog = true
                    } else {
                        val amortizationMatch = findAmortizationMatch(txn, amortizationEntries, percentTolerance, matchDollar, matchChars)
                        if (amortizationMatch != null) {
                            pendingAmortizationTxn = txn
                            pendingAmortizationMatch = amortizationMatch
                            pendingAmortizationIsEdit = isEdit
                            showAmortizationDialog = true
                        } else {
                            val budgetMatch = findBudgetIncomeMatch(txn, incomeSources, matchChars, matchDays)
                            if (budgetMatch != null) {
                                pendingBudgetIncomeTxn = txn
                                pendingBudgetIncomeMatch = budgetMatch
                                pendingBudgetIncomeIsEdit = isEdit
                                showBudgetIncomeDialog = true
                            } else {
                                if (isEdit) onUpdateTransaction(txn) else addAndScroll(txn)
                            }
                        }
                    }
                } else {
                    if (isEdit) onUpdateTransaction(txn) else addAndScroll(txn)
                }
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
        val dateCloseEnough = isRecurringDateCloseEnough(importTxn.date, recurringMatch)
        RecurringExpenseConfirmDialog(
            transaction = importTxn,
            recurringExpense = recurringMatch,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            showDateAdvisory = !dateCloseEnough,
            onConfirmRecurring = {
                val updatedTxn = importTxn.copy(
                    linkedRecurringExpenseId = recurringMatch.id,
                    linkedRecurringExpenseAmount = recurringMatch.amount
                )
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
        val dateCloseEnough = isRecurringDateCloseEnough(pendingRecurringTxn!!.date, pendingRecurringMatch!!)
        RecurringExpenseConfirmDialog(
            transaction = pendingRecurringTxn!!,
            recurringExpense = pendingRecurringMatch!!,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            showDateAdvisory = !dateCloseEnough,
            onConfirmRecurring = {
                val txn = pendingRecurringTxn!!
                val updatedTxn = txn.copy(
                    linkedRecurringExpenseId = pendingRecurringMatch!!.id,
                    linkedRecurringExpenseAmount = pendingRecurringMatch!!.amount
                )
                if (pendingRecurringIsEdit) onUpdateTransaction(updatedTxn)
                else addAndScroll(updatedTxn)
                pendingRecurringTxn = null
                pendingRecurringMatch = null
                showRecurringDialog = false
            },
            onNotRecurring = {
                val txn = pendingRecurringTxn!!
                if (pendingRecurringIsEdit) onUpdateTransaction(txn)
                else addAndScroll(txn)
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
        AmortizationConfirmDialog(
            transaction = importTxn,
            amortizationEntry = amortizationMatch,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            onConfirmAmortization = {
                val updatedTxn = importTxn.copy(linkedAmortizationEntryId = amortizationMatch.id)
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

    // Import budget income match dialog
    if (importStage == ImportStage.DUPLICATE_CHECK && currentImportBudgetIncome != null && importIndex < parsedTransactions.size) {
        val importTxn = parsedTransactions[importIndex]
        val incomeMatch = currentImportBudgetIncome!!
        BudgetIncomeConfirmDialog(
            transaction = importTxn,
            incomeSource = incomeMatch,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            onConfirmBudgetIncome = {
                val recurringIncomeCatId = categories.find { it.tag == "recurring_income" }?.id
                val updatedTxn = importTxn.copy(
                    isBudgetIncome = true,
                    linkedIncomeSourceId = incomeMatch.id,
                    linkedIncomeSourceAmount = incomeMatch.amount,
                    categoryAmounts = if (recurringIncomeCatId != null)
                        listOf(CategoryAmount(recurringIncomeCatId, importTxn.amount))
                    else importTxn.categoryAmounts
                )
                if (incomeMode == IncomeMode.ACTUAL_ADJUST) {
                    onAdjustIncomeAmount(incomeMatch.id, importTxn.amount)
                }
                importApproved.add(updatedTxn)
                currentImportBudgetIncome = null
                importIndex++
            },
            onNotBudgetIncome = {
                importApproved.add(importTxn)
                currentImportBudgetIncome = null
                importIndex++
            }
        )
    }

    // Manual amortization match dialog
    if (showAmortizationDialog && pendingAmortizationTxn != null && pendingAmortizationMatch != null) {
        AmortizationConfirmDialog(
            transaction = pendingAmortizationTxn!!,
            amortizationEntry = pendingAmortizationMatch!!,
            currencySymbol = currencySymbol,
            dateFormatter = dateFormatter,
            onConfirmAmortization = {
                val txn = pendingAmortizationTxn!!
                val updatedTxn = txn.copy(linkedAmortizationEntryId = pendingAmortizationMatch!!.id)
                if (pendingAmortizationIsEdit) onUpdateTransaction(updatedTxn)
                else addAndScroll(updatedTxn)
                pendingAmortizationTxn = null
                pendingAmortizationMatch = null
                showAmortizationDialog = false
            },
            onNotAmortized = {
                val txn = pendingAmortizationTxn!!
                if (pendingAmortizationIsEdit) onUpdateTransaction(txn)
                else addAndScroll(txn)
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
                val recurringIncomeCatId = categories.find { it.tag == "recurring_income" }?.id
                val baseTxn = pendingBudgetIncomeTxn!!
                val txn = baseTxn.copy(
                    isBudgetIncome = true,
                    linkedIncomeSourceId = pendingBudgetIncomeMatch!!.id,
                    linkedIncomeSourceAmount = pendingBudgetIncomeMatch!!.amount,
                    categoryAmounts = if (recurringIncomeCatId != null)
                        listOf(CategoryAmount(recurringIncomeCatId, baseTxn.amount))
                    else baseTxn.categoryAmounts,
                    isUserCategorized = true
                )
                // ACTUAL_ADJUST: update source BEFORE adding txn so cash delta = 0
                if (incomeMode == IncomeMode.ACTUAL_ADJUST) {
                    onAdjustIncomeAmount(pendingBudgetIncomeMatch!!.id, baseTxn.amount)
                }
                if (pendingBudgetIncomeIsEdit) onUpdateTransaction(txn)
                else addAndScroll(txn)
                pendingBudgetIncomeTxn = null
                pendingBudgetIncomeMatch = null
                showBudgetIncomeDialog = false
            },
            onNotBudgetIncome = {
                val txn = pendingBudgetIncomeTxn!!
                if (pendingBudgetIncomeIsEdit) onUpdateTransaction(txn)
                else addAndScroll(txn)
                pendingBudgetIncomeTxn = null
                pendingBudgetIncomeMatch = null
                showBudgetIncomeDialog = false
            }
        )
    }

    // Effect explanation popup
    effectExplanationTransaction?.let { txn ->
        val isExpense = txn.type == TransactionType.EXPENSE
        val fc = { amt: Double -> formatCurrency(amt, currencySymbol) }

        val title: String
        val body: String

        if (txn.linkedRecurringExpenseId != null) {
            val reAmt = if (txn.linkedRecurringExpenseAmount > 0.0) txn.linkedRecurringExpenseAmount
                else recurringExpenses.find { it.id == txn.linkedRecurringExpenseId }?.amount ?: 0.0
            val reName = recurringExpenses.find { it.id == txn.linkedRecurringExpenseId }?.source ?: txn.source
            val diff = reAmt - txn.amount
            title = S.transactions.effectTitleRecurring
            body = if (kotlin.math.abs(diff) < 0.005) {
                S.transactions.effectRecurringMatch(fc(txn.amount), reName, fc(reAmt))
            } else if (diff > 0) {
                S.transactions.effectRecurringUnder(fc(txn.amount), reName, fc(reAmt), fc(diff))
            } else {
                S.transactions.effectRecurringOver(fc(txn.amount), reName, fc(reAmt), fc(kotlin.math.abs(diff)))
            }
        } else if (txn.linkedAmortizationEntryId != null) {
            val ae = amortizationEntries.find { it.id == txn.linkedAmortizationEntryId }
            val aeName = ae?.source ?: txn.source
            val aeTotal = ae?.amount ?: txn.amount
            val aePeriods = ae?.totalPeriods ?: 1
            val perPeriod = aeTotal / aePeriods
            val elapsed = if (ae != null) {
                val today = LocalDate.now()
                when (budgetPeriod) {
                    BudgetPeriod.DAILY -> java.time.temporal.ChronoUnit.DAYS.between(ae.startDate, today).toInt()
                    BudgetPeriod.WEEKLY -> java.time.temporal.ChronoUnit.WEEKS.between(ae.startDate, today).toInt()
                    BudgetPeriod.MONTHLY -> java.time.temporal.ChronoUnit.MONTHS.between(ae.startDate, today).toInt()
                }.coerceIn(0, aePeriods)
            } else 0
            val isComplete = elapsed >= aePeriods
            val periodLabel = when (budgetPeriod) {
                BudgetPeriod.DAILY -> S.common.periodDay
                BudgetPeriod.WEEKLY -> S.common.periodWeek
                BudgetPeriod.MONTHLY -> S.common.periodMonth
            }
            title = S.transactions.effectTitleAmortization
            body = if (isComplete) {
                S.transactions.effectAmortizationComplete(fc(txn.amount), aeName, fc(aeTotal), aePeriods.toString(), periodLabel)
            } else {
                S.transactions.effectAmortizationActive(fc(txn.amount), aeName, fc(aeTotal), fc(perPeriod), periodLabel, elapsed.toString(), aePeriods.toString())
            }
        } else if (txn.linkedIncomeSourceId != null) {
            val srcAmt = if (txn.linkedIncomeSourceAmount > 0.0) txn.linkedIncomeSourceAmount
                else incomeSources.find { it.id == txn.linkedIncomeSourceId }?.amount ?: 0.0
            val srcName = incomeSources.find { it.id == txn.linkedIncomeSourceId }?.source ?: txn.source
            title = S.transactions.effectTitleIncome
            body = when (incomeMode) {
                IncomeMode.FIXED -> S.transactions.effectIncomeFixed(fc(txn.amount), srcName, fc(srcAmt))
                IncomeMode.ACTUAL -> {
                    val diff = txn.amount - srcAmt
                    if (kotlin.math.abs(diff) < 0.005) {
                        S.transactions.effectIncomeActualMatch(fc(txn.amount), srcName, fc(srcAmt))
                    } else if (diff > 0) {
                        S.transactions.effectIncomeActualOver(fc(txn.amount), srcName, fc(srcAmt), fc(diff))
                    } else {
                        S.transactions.effectIncomeActualUnder(fc(txn.amount), srcName, fc(srcAmt), fc(kotlin.math.abs(diff)))
                    }
                }
                IncomeMode.ACTUAL_ADJUST -> S.transactions.effectIncomeActualAdjust(fc(txn.amount), srcName)
            }
        } else if (txn.linkedSavingsGoalId != null || txn.linkedSavingsGoalAmount > 0.0) {
            val goalName = savingsGoals.find { it.id == txn.linkedSavingsGoalId }?.name ?: txn.source
            title = S.transactions.effectTitleSavingsGoal
            body = S.transactions.effectSavingsGoal(fc(txn.amount), goalName)
        } else if (txn.excludeFromBudget) {
            title = S.transactions.effectTitleExcluded
            body = S.transactions.effectExcluded(fc(txn.amount))
        } else {
            title = ""
            body = ""
        }

        if (title.isNotEmpty()) {
            AdAwareAlertDialog(
                onDismissRequest = { effectExplanationTransaction = null },
                title = { Text(title) },
                text = { Text(body) },
                confirmButton = {
                    DialogSecondaryButton(onClick = { effectExplanationTransaction = null }) {
                        Text(S.common.close)
                    }
                }
            )
        }
    }

}

@Composable
fun MatchDialogCard(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val headerColor = dialogHeaderColor()
    val headerTextColor = dialogHeaderTextColor()
    val footerColor = dialogFooterColor()

    AdAwareDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            headerColor,
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = headerTextColor
                    )
                }

                // Body
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    content = content
                )

                // Footer
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            footerColor,
                            RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    buttons()
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = dialogSectionLabelColor(),
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun TransactionCard(
    source: String,
    amount: String,
    date: String,
    extra: String? = null,
    extraColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(source, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(amount, fontWeight = FontWeight.SemiBold)
            }
            Text(date, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            if (extra != null) {
                Text(extra, style = MaterialTheme.typography.bodySmall, color = extraColor)
            }
        }
    }
}

@Composable
fun BudgetIncomeConfirmDialog(
    transaction: Transaction,
    incomeSource: IncomeSource,
    currencySymbol: String,
    dateFormatter: DateTimeFormatter,
    onConfirmBudgetIncome: () -> Unit,
    onNotBudgetIncome: () -> Unit
) {
    val S = LocalStrings.current
    MatchDialogCard(
        title = S.transactions.budgetIncomeMatchTitle(transaction.source),
        onDismiss = onNotBudgetIncome,
        buttons = {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DialogSecondaryButton(onClick = onNotBudgetIncome) { Text(S.transactions.noExtraIncome, maxLines = 1) }
                Spacer(modifier = Modifier.width(8.dp))
                DialogPrimaryButton(onClick = onConfirmBudgetIncome) { Text(S.transactions.yesBudgetIncome, maxLines = 1) }
            }
        }
    ) {
        SectionLabel(S.transactions.transactionLabel)
        TransactionCard(
            source = transaction.source,
            amount = formatCurrency(transaction.amount, currencySymbol),
            date = transaction.date.format(dateFormatter)
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel(S.transactions.incomeSourceLabel)
        TransactionCard(
            source = incomeSource.source,
            amount = formatCurrency(incomeSource.amount, currencySymbol),
            date = ""
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            S.transactions.budgetIncomeMatchBody(transaction.source, incomeSource.source),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun AmortizationConfirmDialog(
    transaction: Transaction,
    amortizationEntry: AmortizationEntry,
    currencySymbol: String,
    dateFormatter: DateTimeFormatter,
    onConfirmAmortization: () -> Unit,
    onNotAmortized: () -> Unit
) {
    val S = LocalStrings.current
    MatchDialogCard(
        title = S.transactions.amortizationMatchTitle(transaction.source),
        onDismiss = onNotAmortized,
        buttons = {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DialogSecondaryButton(onClick = onNotAmortized) { Text(S.transactions.noRegularAmort, maxLines = 1) }
                Spacer(modifier = Modifier.width(8.dp))
                DialogPrimaryButton(onClick = onConfirmAmortization) { Text(S.transactions.yesAmortization, maxLines = 1) }
            }
        }
    ) {
        SectionLabel(S.transactions.transactionLabel)
        TransactionCard(
            source = transaction.source,
            amount = formatCurrency(transaction.amount, currencySymbol),
            date = transaction.date.format(dateFormatter)
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel(S.transactions.amortizationEntryLabel)
        TransactionCard(
            source = amortizationEntry.source,
            amount = formatCurrency(amortizationEntry.amount, currencySymbol),
            date = ""
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            S.transactions.amortizationMatchBody(transaction.source, amortizationEntry.source),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun RecurringExpenseConfirmDialog(
    transaction: Transaction,
    recurringExpense: RecurringExpense,
    currencySymbol: String,
    dateFormatter: DateTimeFormatter,
    showDateAdvisory: Boolean,
    onConfirmRecurring: () -> Unit,
    onNotRecurring: () -> Unit
) {
    val S = LocalStrings.current
    MatchDialogCard(
        title = S.transactions.recurringMatchTitle(transaction.source),
        onDismiss = onNotRecurring,
        buttons = {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DialogSecondaryButton(onClick = onNotRecurring) { Text(S.transactions.noRegularExpense, maxLines = 1) }
                Spacer(modifier = Modifier.width(8.dp))
                DialogPrimaryButton(onClick = onConfirmRecurring) { Text(S.transactions.yesRecurring, maxLines = 1) }
            }
        }
    ) {
        SectionLabel(S.transactions.transactionLabel)
        TransactionCard(
            source = transaction.source,
            amount = formatCurrency(transaction.amount, currencySymbol),
            date = transaction.date.format(dateFormatter)
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel(S.transactions.recurringExpenseLabel)
        TransactionCard(
            source = recurringExpense.source,
            amount = formatCurrency(recurringExpense.amount, currencySymbol),
            date = ""
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            S.transactions.recurringMatchBody(transaction.source, recurringExpense.source),
            style = MaterialTheme.typography.bodyMedium
        )
        if (showDateAdvisory) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                S.transactions.dateAdvisory,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF9800)
            )
        }
    }
}

@Composable
fun DuplicateResolutionDialog(
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
    val S = LocalStrings.current
    MatchDialogCard(
        title = S.transactions.duplicateDetected,
        onDismiss = onKeepExisting,
        buttons = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Build label/action list: 3 buttons normally, 4 during import
                val labels = mutableListOf(S.transactions.keepExisting, S.transactions.keepNew, S.transactions.keepBoth)
                val actions = mutableListOf(onKeepExisting, onKeepNew, onIgnore)
                if (showIgnoreAll) {
                    labels.add(S.transactions.ignoreAll)
                    actions.add(onIgnoreAll)
                }

                labels.forEachIndexed { idx, label ->
                    DialogPrimaryButton(
                        onClick = actions[idx],
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) {
        SectionLabel(S.transactions.duplicateExisting)
        TransactionCard(
            source = existingTransaction.source,
            amount = formatCurrency(existingTransaction.amount, currencySymbol),
            date = existingTransaction.date.format(dateFormatter),
            extra = if (existingTransaction.categoryAmounts.isNotEmpty()) {
                existingTransaction.categoryAmounts.mapNotNull { ca ->
                    categoryMap[ca.categoryId]?.name
                }.joinToString(", ")
            } else null
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel(S.transactions.duplicateNew)
        TransactionCard(
            source = newTransaction.source,
            amount = formatCurrency(newTransaction.amount, currencySymbol),
            date = newTransaction.date.format(dateFormatter),
            extra = if (newTransaction.categoryAmounts.isNotEmpty()) {
                newTransaction.categoryAmounts.mapNotNull { ca ->
                    categoryMap[ca.categoryId]?.name
                }.joinToString(", ")
            } else null
        )
    }
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
    onCategoryFilter: (Int) -> Unit = {},
    attributionLabel: String? = null,
    isLinkedRecurring: Boolean = false,
    isLinkedAmortization: Boolean = false,
    isAmortComplete: Boolean = false,
    isLinkedIncome: Boolean = false,
    isLinkedSavingsGoal: Boolean = false,
    linkedRecurringAmount: Double? = null,
    linkedAmortizationApplied: Double? = null,
    linkedIncomeAmount: Double? = null,
    incomeMode: IncomeMode = IncomeMode.FIXED,
    onEffectTap: (() -> Unit)? = null
) {
    val S = LocalStrings.current
    val isExpense = transaction.type == TransactionType.EXPENSE

    // Determine if this linked transaction has a budget effect to show
    val showEffect: Boolean
    val effectAmount: Double
    val effectColor: Color
    val effectPrefix: String
    if (isLinkedRecurring && linkedRecurringAmount != null) {
        val diff = linkedRecurringAmount - transaction.amount
        showEffect = true
        effectAmount = kotlin.math.abs(diff)
        effectColor = if (diff >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
        effectPrefix = if (diff >= 0) "+" else "-"
    } else if (isLinkedIncome && linkedIncomeAmount != null &&
               incomeMode == IncomeMode.ACTUAL) {
        val diff = transaction.amount - linkedIncomeAmount
        showEffect = true
        effectAmount = kotlin.math.abs(diff)
        effectColor = if (diff >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
        effectPrefix = if (diff >= 0) "+" else "-"
    } else if (isLinkedAmortization && linkedAmortizationApplied != null) {
        showEffect = true
        effectAmount = linkedAmortizationApplied
        effectColor = if (isExpense) Color(0xFFF44336) else Color(0xFF4CAF50)
        effectPrefix = if (isExpense) "-" else ""
    } else if (isLinkedSavingsGoal) {
        showEffect = true
        effectAmount = 0.0
        effectColor = Color(0xFFFF9800)
        effectPrefix = ""
    } else if (transaction.excludeFromBudget) {
        showEffect = true
        effectAmount = 0.0
        effectColor = Color(0xFF9E9E9E)
        effectPrefix = ""
    } else {
        showEffect = false
        effectAmount = 0.0
        effectColor = Color.Unspecified
        effectPrefix = ""
    }

    // Actual transaction amount (always shown)
    val actualPrefix = if (isExpense) "-" else ""
    val formattedActualAmount = "$actualPrefix${formatCurrency(transaction.amount, currencySymbol)}"
    val formattedEffect = if (showEffect) "$effectPrefix${formatCurrency(effectAmount, currencySymbol)}" else ""

    // For non-linked transactions, use colored amount as before
    val isLinked = isLinkedRecurring || isLinkedAmortization || isLinkedIncome || isLinkedSavingsGoal
    val actualAmountColor = if (isLinked || transaction.excludeFromBudget) MaterialTheme.colorScheme.onBackground
        else if (isExpense) Color(0xFFF44336) else Color(0xFF4CAF50)

    val hasMultipleCategories = transaction.categoryAmounts.size > 1
    val singleCategory = if (transaction.categoryAmounts.size == 1)
        categoryMap[transaction.categoryAmounts[0].categoryId] else null
    val customColors = LocalSyncBudgetColors.current
    val categoryIconTint = if (transaction.isUserCategorized) customColors.userCategoryIconTint
        else MaterialTheme.colorScheme.onBackground

    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val useExpandedLayout = fontScale > 1.1f

    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                )
                .padding(horizontal = 12.dp, vertical = if (useExpandedLayout) 10.dp else 12.dp)
        ) {
            // Line 1: icon + source + amount (+ checkbox if expanded layout)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Category icon
                if (transaction.categoryAmounts.isNotEmpty()) {
                    if (hasMultipleCategories) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = S.transactions.category,
                            tint = categoryIconTint,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { onToggleExpand() }
                        )
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

                if (!useExpandedLayout) {
                    // Normal layout: date (+ attribution below) on left
                    Column {
                        Text(
                            text = transaction.date.format(dateFormatter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (attributionLabel != null) {
                            Text(
                                text = attributionLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.source,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = if (useExpandedLayout) 2 else 1,
                        modifier = if (!useExpandedLayout) Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 3000
                        ) else Modifier
                    )
                    if (transaction.description.isNotBlank()) {
                        Text(
                            text = transaction.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 3000
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (onEffectTap != null && (isLinked || transaction.excludeFromBudget))
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onEffectTap() }
                            .padding(4.dp)
                    else Modifier
                ) {
                    if (isLinkedRecurring) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    } else if (isLinkedAmortization) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = if (isAmortComplete) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    } else if (isLinkedIncome) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalance,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    } else if (isLinkedSavingsGoal) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_coins),
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    } else if (transaction.excludeFromBudget) {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = null,
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formattedActualAmount,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = actualAmountColor,
                            textAlign = TextAlign.End
                        )
                        if ((isLinked || transaction.excludeFromBudget) && showEffect) {
                            Text(
                                text = formattedEffect,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = effectColor,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }

                if (selectionMode && !useExpandedLayout) {
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

            // Line 2 (expanded layout only): date + attribution + checkbox
            if (useExpandedLayout) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (transaction.categoryAmounts.isNotEmpty()) 30.dp else 0.dp,
                            top = 2.dp
                        )
                ) {
                    Column {
                        Text(
                            text = transaction.date.format(dateFormatter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        if (attributionLabel != null) {
                            Text(
                                text = attributionLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (selectionMode) {
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
                            tint = if (transaction.isUserCategorized) customColors.userCategoryIconTint.copy(alpha = 0.7f)
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
                            text = S.transactions.unknown,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = formatCurrency(ca.amount, currencySymbol),
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
fun TransactionDialog(
    title: String,
    sourceLabel: String,
    categories: List<Category>,
    existingIds: Set<Int>,
    currencySymbol: String = "$",
    dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    isExpense: Boolean = false,
    editTransaction: Transaction? = null,
    chartPalette: String = "Bright",
    recurringExpenses: List<RecurringExpense> = emptyList(),
    amortizationEntries: List<AmortizationEntry> = emptyList(),
    incomeSources: List<IncomeSource> = emptyList(),
    savingsGoals: List<SavingsGoal> = emptyList(),
    pastSources: List<String> = emptyList(),
    budgetPeriod: BudgetPeriod = BudgetPeriod.DAILY,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (() -> Unit)? = null,
    onAddAmortization: ((AmortizationEntry) -> Unit)? = null,
    onDeleteAmortization: ((AmortizationEntry) -> Unit)? = null
) {
    val S = LocalStrings.current
    val maxDecimals = CURRENCY_DECIMALS[currencySymbol] ?: 2
    val context = LocalContext.current
    val toastState = LocalAppToast.current
    val isEdit = editTransaction != null
    var selectedDate by remember {
        mutableStateOf(editTransaction?.date ?: LocalDate.now())
    }
    var source by remember { mutableStateOf(editTransaction?.source ?: "") }
    var description by remember { mutableStateOf(editTransaction?.description ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(editTransaction?.isUserCategorized ?: true) }
    var excludeFromBudget by remember { mutableStateOf(editTransaction?.excludeFromBudget ?: false) }

    // Linked entry state
    var linkedRecurringId by remember { mutableStateOf(editTransaction?.linkedRecurringExpenseId) }
    var linkedAmortizationId by remember { mutableStateOf(editTransaction?.linkedAmortizationEntryId) }
    var linkedIncomeId by remember { mutableStateOf(editTransaction?.linkedIncomeSourceId) }
    var linkedSavingsGoalId by remember { mutableStateOf(editTransaction?.linkedSavingsGoalId) }
    var showLinkRecurringPicker by remember { mutableStateOf(false) }
    var showLinkAmortizationPicker by remember { mutableStateOf(false) }
    var showLinkIncomePicker by remember { mutableStateOf(false) }
    var showLinkSavingsGoalPicker by remember { mutableStateOf(false) }
    var showLinkMismatchDialog by remember { mutableStateOf(false) }
    var pendingLinkEntry by remember { mutableStateOf<Any?>(null) }
    var showCreateAmortizationDialog by remember { mutableStateOf(false) }
    var provisionalAmortizationEntry by remember { mutableStateOf<AmortizationEntry?>(null) }

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
    var userOwnedFields by remember {
        mutableStateOf(buildSet {
            if (editTransaction != null && editTransaction.categoryAmounts.size > 1) {
                add("total")
                editTransaction.categoryAmounts.forEach { add(it.categoryId.toString()) }
            }
        })
    }
    var autoFilledField by remember { mutableStateOf<String?>(null) }

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

    fun recomputeAutoFill(skipField: String? = null) {
        if (usePercentage) return

        val totalOwned = "total" in userOwnedFields
        val nonOwnedCats = selectedCats.filter { it.id.toString() !in userOwnedFields }

        // Determine new auto-fill target
        val newTarget: String? = when {
            !totalOwned -> "total"
            nonOwnedCats.size == 1 -> nonOwnedCats[0].id.toString()
            else -> null
        }

        // Skip filling into the field the user is actively clearing
        val effectiveTarget = if (newTarget == skipField) null else newTarget

        // Clear old auto-fill if target changed
        val oldTarget = autoFilledField
        if (oldTarget != null && oldTarget != effectiveTarget) {
            if (oldTarget == "total") {
                if ("total" !in userOwnedFields) totalAmountText = ""
            } else {
                val catId = oldTarget.toIntOrNull()
                if (catId != null && catId.toString() !in userOwnedFields) {
                    categoryAmountTexts[catId] = ""
                }
            }
        }

        // Compute new auto-fill
        when (effectiveTarget) {
            "total" -> {
                val sum = selectedCats.sumOf {
                    (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() ?: 0.0
                }
                val hasAnyValue = selectedCats.any {
                    (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() != null
                }
                totalAmountText = if (hasAnyValue) formatAmount(sum, maxDecimals) else ""
                autoFilledField = "total"
            }
            null -> {
                autoFilledField = null
            }
            else -> {
                val targetCatId = effectiveTarget.toInt()
                val total = totalAmountText.toDoubleOrNull()
                val otherSum = selectedCats
                    .filter { it.id != targetCatId }
                    .sumOf { (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() ?: 0.0 }
                if (total != null && total - otherSum >= 0) {
                    categoryAmountTexts[targetCatId] = formatAmount(total - otherSum, maxDecimals)
                } else {
                    categoryAmountTexts[targetCatId] = ""
                }
                autoFilledField = effectiveTarget
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
    var pickerHasOpened by remember { mutableStateOf(false) }
    if (showCategoryPicker) pickerHasOpened = true

    // Scroll state for auto-scrolling to mode buttons after category selection
    val scrollState = rememberScrollState()
    var modeButtonsOffset by remember { mutableIntStateOf(0) }
    var modeButtonsWindowY by remember { mutableIntStateOf(0) }

    // Currency prefix/suffix
    val isCurrencySuffix = currencySymbol in CURRENCY_SUFFIX_SYMBOLS

    val focusManager = LocalFocusManager.current
    val scrollScope = rememberCoroutineScope()

    val dismissWithCleanup: () -> Unit = {
        provisionalAmortizationEntry?.let { entry ->
            onDeleteAmortization?.invoke(entry)
        }
        onDismiss()
    }

    AdAwareDialog(
        onDismissRequest = dismissWithCleanup,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .imePadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Box {
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { focusManager.clearFocus() }
            ) {
                // Title bar
                DialogHeader(title = title)

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                        .pointerInput(focusManager) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Auto-dismiss pie chart when < 2 categories
                    LaunchedEffect(selectedCats.size) {
                        if (selectedCats.size < 2) showPieChart = false
                    }

                    // Auto-scroll to mode buttons after category picker closes
                    LaunchedEffect(showCategoryPicker) {
                        if (!showCategoryPicker && pickerHasOpened && selectedCats.size > 1) {
                            delay(150)
                            if (modeButtonsOffset > 0) {
                                scrollState.animateScrollTo(modeButtonsOffset)
                            }
                        }
                    }

                    // Auto-scroll down when pie chart is toggled on
                    LaunchedEffect(showPieChart) {
                        if (showPieChart) {
                            delay(500L)
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }

                    // Date field
                    OutlinedTextField(
                        value = selectedDate.format(dateFormatter),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(S.transactions.date) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = S.transactions.date
                                )
                            }
                        },
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Source/Merchant field with autocomplete
                    var sourceHasFocus by remember { mutableStateOf(false) }
                    val sourceSuggestions = remember(source, pastSources, sourceHasFocus) {
                        if (!sourceHasFocus || source.length < 2) emptyList()
                        else {
                            val query = source.lowercase()
                            pastSources.filter { it.lowercase().contains(query) && it != source }
                                .take(3)
                        }
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = source,
                            onValueChange = { source = it },
                            label = { Text(sourceLabel) },
                            isError = showValidation && source.isBlank(),
                            supportingText = if (showValidation && source.isBlank()) ({
                                Text(S.transactions.requiredMerchantExample, color = Color(0xFFF44336))
                            }) else null,
                            colors = textFieldColors,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { sourceHasFocus = it.isFocused }
                        )
                        if (sourceSuggestions.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                                tonalElevation = 3.dp,
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    sourceSuggestions.forEach { suggestion ->
                                        Text(
                                            text = suggestion,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    source = suggestion
                                                    sourceHasFocus = false
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Description field
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(S.common.descriptionFieldLabel) },
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Link to recurring/amortization/income entry
                    if (isExpense) {
                        if (linkedRecurringId != null) {
                            val linkedName = recurringExpenses.find { it.id == linkedRecurringId }?.source ?: "?"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.Sync, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(S.transactions.linkedToRecurring(linkedName), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { linkedRecurringId = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else if (linkedAmortizationId != null) {
                            val linkedName = amortizationEntries.find { it.id == linkedAmortizationId }?.source ?: "?"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFF44336), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(S.transactions.linkedToAmortization(linkedName), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { linkedAmortizationId = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else if (linkedSavingsGoalId != null) {
                            val linkedName = savingsGoals.find { it.id == linkedSavingsGoalId }?.name ?: "?"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFFF9800), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_coins), contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(S.transactions.linkedToSavingsGoal(linkedName), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { linkedSavingsGoalId = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                val linkPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                val fixedFontSize = with(LocalDensity.current) { 13.dp.toSp() }
                                val fixedIconSize = 18.dp / LocalDensity.current.fontScale
                                if (recurringExpenses.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showLinkRecurringPicker = true },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = linkPadding
                                    ) {
                                        Text(S.transactions.linkToRecurring, fontSize = fixedFontSize, maxLines = 1)
                                        Spacer(Modifier.width(2.dp))
                                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(fixedIconSize))
                                    }
                                }
                                if (amortizationEntries.isNotEmpty() || onAddAmortization != null) {
                                    OutlinedButton(
                                        onClick = {
                                            if (amortizationEntries.isNotEmpty()) {
                                                showLinkAmortizationPicker = true
                                            } else {
                                                showCreateAmortizationDialog = true
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = linkPadding
                                    ) {
                                        Text(S.transactions.linkToAmortization, fontSize = fixedFontSize, maxLines = 1)
                                        Spacer(Modifier.width(2.dp))
                                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(fixedIconSize))
                                    }
                                }
                                if (savingsGoals.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showLinkSavingsGoalPicker = true },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = linkPadding
                                    ) {
                                        Text(S.transactions.linkToSavingsGoal, fontSize = fixedFontSize, maxLines = 1)
                                        Spacer(Modifier.width(2.dp))
                                        Icon(painter = painterResource(id = R.drawable.ic_coins), contentDescription = null, modifier = Modifier.size(fixedIconSize))
                                    }
                                }
                            }
                        }
                    } else {
                        // Income transaction linking
                        if (linkedIncomeId != null) {
                            val linkedName = incomeSources.find { it.id == linkedIncomeId }?.source ?: "?"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF2196F3), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(S.transactions.linkedToIncome(linkedName), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { linkedIncomeId = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else if (incomeSources.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showLinkIncomePicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(S.transactions.linkToIncome)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Category selector — button that opens picker dialog
                    // Hidden when linked to income (auto-set to recurring_income)
                    if (categories.isNotEmpty() && linkedIncomeId == null) {
                        val categoryError = showValidation && selectedCats.isEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    if (categoryError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    focusManager.clearFocus()
                                    showCategoryPicker = true
                                }
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
                                    text = "${S.transactions.category}...",
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
                    val singleAmountInvalid = showValidation && (singleAmountText.toDoubleOrNull()?.let { it <= 0 } != false)
                    OutlinedTextField(
                        value = singleAmountText,
                        onValueChange = { if (isValidAmountInput(it, maxDecimals)) singleAmountText = it },
                        label = { Text(S.transactions.amount) },
                        isError = singleAmountInvalid,
                        supportingText = if (singleAmountInvalid) ({
                            Text(S.transactions.amountExample, color = Color(0xFFF44336))
                        }) else null,
                        prefix = if (!isCurrencySuffix) ({ Text(currencySymbol) }) else null,
                        suffix = if (isCurrencySuffix) ({ Text(currencySymbol) }) else null,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                modeButtonsOffset = coords.positionInParent().y.toInt()
                                modeButtonsWindowY = coords.positionInWindow().y.toInt()
                            },
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pie chart mode
                        IconButton(onClick = {
                            if (!totalFilled) {
                                toastState.show("Enter a total to enable this mode.", modeButtonsWindowY)
                            } else {
                                showPieChart = !showPieChart
                                if (showPieChart) usePercentage = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.PieChart,
                                contentDescription = S.transactions.pieChart,
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
                                contentDescription = S.transactions.calculator,
                                tint = if (!showPieChart && !usePercentage) activeColor else inactiveColor,
                                modifier = Modifier.size(modeIconSize)
                            )
                        }

                        // Percentage mode
                        IconButton(onClick = {
                            if (!totalFilled) {
                                toastState.show("Enter a total to enable this mode.", modeButtonsWindowY)
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
                                contentDescription = S.transactions.percentage,
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
                            userOwnedFields = userOwnedFields - "total"
                            recomputeAutoFill()
                        }
                    }
                    OutlinedTextField(
                        value = totalAmountText,
                        onValueChange = { newVal ->
                            if (isValidAmountInput(newVal, maxDecimals)) {
                                totalAmountText = newVal
                                if (!usePercentage) {
                                    if (newVal.isNotEmpty()) {
                                        userOwnedFields = userOwnedFields + "total"
                                        recomputeAutoFill()
                                    } else {
                                        userOwnedFields = userOwnedFields - "total"
                                        recomputeAutoFill(skipField = "total")
                                    }
                                }
                            }
                        },
                        label = { Text(S.transactions.total) },
                        prefix = if (!isCurrencySuffix) ({ Text(currencySymbol) }) else null,
                        suffix = if (isCurrencySuffix) ({ Text(currencySymbol) }) else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (maxDecimals > 0) KeyboardType.Decimal else KeyboardType.Number
                        ),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                if (state.isFocused && modeButtonsOffset > 0) {
                                    scrollScope.launch {
                                        delay(500L)
                                        scrollState.animateScrollTo(modeButtonsOffset)
                                    }
                                }
                            }
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
                            },
                            chartPalette = chartPalette
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
                                        userOwnedFields = userOwnedFields - cat.id.toString()
                                        recomputeAutoFill()
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = catText,
                                onValueChange = { newVal ->
                                    val validInput = if (usePercentage) isValidPercentInput(newVal)
                                        else isValidAmountInput(newVal, maxDecimals)
                                    if (validInput) {
                                        categoryAmountTexts[cat.id] = newVal

                                        if (usePercentage) {
                                            if (newVal.isNotEmpty()) {
                                                lastEditedCatId = cat.id
                                                userOwnedFields = userOwnedFields + cat.id.toString()
                                                // Auto-fill last empty percentage
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
                                            } else {
                                                userOwnedFields = userOwnedFields - cat.id.toString()
                                                // Kill debounce so it doesn't overwrite cleared values
                                                lastEditedCatId = null
                                                // Clear non-user-owned cats (auto-filled values)
                                                selectedCats.forEach { c ->
                                                    if (c.id.toString() !in userOwnedFields) {
                                                        categoryAmountTexts[c.id] = ""
                                                    }
                                                }
                                            }
                                        } else {
                                            // Amount mode
                                            if (newVal.isNotEmpty()) {
                                                // Claiming the auto-filled field: release total
                                                if (autoFilledField == cat.id.toString()) {
                                                    userOwnedFields = userOwnedFields - "total"
                                                }
                                                userOwnedFields = userOwnedFields + cat.id.toString()
                                                recomputeAutoFill()
                                            } else {
                                                userOwnedFields = userOwnedFields - cat.id.toString()
                                                recomputeAutoFill(skipField = cat.id.toString())
                                            }
                                        }
                                    }
                                },
                                label = { Text(cat.name) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (usePercentage) KeyboardType.Number
                                        else (if (maxDecimals > 0) KeyboardType.Decimal else KeyboardType.Number)
                                ),
                                prefix = if (!usePercentage && !isCurrencySuffix) ({ Text(currencySymbol) }) else null,
                                suffix = if (usePercentage) ({ Text("%") })
                                    else if (isCurrencySuffix) ({ Text(currencySymbol) }) else null,
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
                                        // Abort if editing was cancelled or category was cleared
                                        if (lastEditedCatId == null) return@LaunchedEffect
                                        if (editedId.toString() !in userOwnedFields) return@LaunchedEffect
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
                                                // Debounce overwrote user's value — no longer user-owned
                                                userOwnedFields = userOwnedFields - id.toString()
                                            }
                                        } else if (remaining >= 0 && otherSum == 0) {
                                            val each = remaining / otherCats.size
                                            val extra = remaining % otherCats.size
                                            otherCats.forEachIndexed { i, c ->
                                                categoryAmountTexts[c.id] =
                                                    (each + if (i < extra) 1 else 0).toString()
                                                userOwnedFields = userOwnedFields - c.id.toString()
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

                DialogFooter {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isEdit && onDelete != null) {
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = S.common.delete,
                                    tint = Color(0xFFF44336)
                                )
                            }
                        }
                        var thumbYPx by remember { mutableIntStateOf(0) }
                        IconButton(
                            onClick = {
                                verified = !verified
                                val msg = if (verified) S.transactions.verifiedToast else S.transactions.unverifiedToast
                                toastState.show(msg, thumbYPx)
                            },
                            modifier = Modifier.onGloballyPositioned { coords ->
                                thumbYPx = coords.positionInWindow().y.toInt()
                            }
                        ) {
                            Icon(
                                imageVector = if (verified) Icons.Filled.ThumbUpAlt else Icons.Filled.QuestionMark,
                                contentDescription = S.transactions.verifiedToast,
                                tint = if (verified) Color(0xFF2E7D32) else Color(0xFFF44336)
                            )
                        }
                        var excludeYPx by remember { mutableIntStateOf(0) }
                        IconButton(
                            onClick = {
                                excludeFromBudget = !excludeFromBudget
                                val msg = if (excludeFromBudget) S.transactions.excludedToast else S.transactions.includedToast
                                toastState.show(msg, excludeYPx)
                            },
                            modifier = Modifier.onGloballyPositioned { coords ->
                                excludeYPx = coords.positionInWindow().y.toInt()
                            }
                        ) {
                            Icon(
                                imageVector = if (excludeFromBudget) Icons.Filled.Block else Icons.Filled.Check,
                                contentDescription = S.transactions.excludedToast,
                                tint = if (excludeFromBudget) Color(0xFFF44336) else Color(0xFF2E7D32)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        DialogSecondaryButton(onClick = dismissWithCleanup) { Text(S.common.cancel) }
                        Spacer(modifier = Modifier.width(8.dp))
                        DialogPrimaryButton(
                            onClick = {
                                if (source.isBlank() || selectedCats.isEmpty()) { showValidation = true; return@DialogPrimaryButton }
                                val type = if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME
                                val catAmounts: List<CategoryAmount>
                                val totalAmount: Double

                                if (selectedCats.size == 1) {
                                    val amt = singleAmountText.toDoubleOrNull()
                                    if (amt == null || amt <= 0) { showValidation = true; return@DialogPrimaryButton }
                                    totalAmount = amt
                                    catAmounts = listOf(CategoryAmount(selectedCats[0].id, amt))
                                } else {
                                    if (usePercentage) {
                                        val total = totalAmountText.toDoubleOrNull() ?: return@DialogPrimaryButton
                                        if (total <= 0) return@DialogPrimaryButton
                                        catAmounts = selectedCats.mapNotNull { cat ->
                                            val pct = (categoryAmountTexts[cat.id] ?: "").toIntOrNull()
                                            if (pct != null && pct > 0) CategoryAmount(cat.id, total * pct / 100.0)
                                            else null
                                        }
                                        if (catAmounts.isEmpty()) return@DialogPrimaryButton
                                        totalAmount = total
                                    } else {
                                        val total = totalAmountText.toDoubleOrNull()
                                        catAmounts = selectedCats.mapNotNull { cat ->
                                            val amt = (categoryAmountTexts[cat.id] ?: "").toDoubleOrNull()
                                            if (amt != null && amt > 0) CategoryAmount(cat.id, amt) else null
                                        }
                                        if (catAmounts.isEmpty()) return@DialogPrimaryButton
                                        val catSum = catAmounts.sumOf { it.amount }
                                        if (total != null && abs(catSum - total) > 0.005) {
                                            showSumMismatchDialog = true
                                            adjustTargetId = null
                                            return@DialogPrimaryButton
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
                            // Look up remembered amounts for newly linked entries
                            val reAmount = linkedRecurringId?.let { reId ->
                                recurringExpenses.find { it.id == reId }?.amount ?: 0.0
                            } ?: 0.0
                            val isAmount = linkedIncomeId?.let { isId ->
                                incomeSources.find { it.id == isId }?.amount ?: 0.0
                            } ?: 0.0

                            // Compute savings goal remembered amount
                            val sgAmount = if (linkedSavingsGoalId != null) totalAmount else 0.0

                            val txn = if (editTransaction != null) {
                                // Preserve fields not editable in this dialog
                                editTransaction.copy(
                                    type = type,
                                    date = selectedDate,
                                    source = source.trim(),
                                    description = description.trim(),
                                    categoryAmounts = catAmounts,
                                    amount = totalAmount,
                                    linkedRecurringExpenseId = linkedRecurringId,
                                    linkedAmortizationEntryId = linkedAmortizationId,
                                    linkedIncomeSourceId = linkedIncomeId,
                                    linkedSavingsGoalId = linkedSavingsGoalId,
                                    linkedRecurringExpenseAmount = if (linkedRecurringId != null) reAmount else editTransaction.linkedRecurringExpenseAmount,
                                    linkedIncomeSourceAmount = if (linkedIncomeId != null) isAmount else editTransaction.linkedIncomeSourceAmount,
                                    linkedSavingsGoalAmount = if (linkedSavingsGoalId != null) sgAmount else editTransaction.linkedSavingsGoalAmount,
                                    isUserCategorized = verified,
                                    excludeFromBudget = excludeFromBudget
                                )
                            } else {
                                Transaction(
                                    id = id,
                                    type = type,
                                    date = selectedDate,
                                    source = source.trim(),
                                    description = description.trim(),
                                    categoryAmounts = catAmounts,
                                    amount = totalAmount,
                                    linkedRecurringExpenseId = linkedRecurringId,
                                    linkedAmortizationEntryId = linkedAmortizationId,
                                    linkedIncomeSourceId = linkedIncomeId,
                                    linkedSavingsGoalId = linkedSavingsGoalId,
                                    linkedRecurringExpenseAmount = reAmount,
                                    linkedIncomeSourceAmount = isAmount,
                                    linkedSavingsGoalAmount = sgAmount,
                                    excludeFromBudget = excludeFromBudget
                                )
                            }
                            onSave(txn)
                        }
                    ) {
                        Text(S.common.save)
                    }
                    }
                }
            }
            PulsingScrollArrow(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 50.dp)
            )
            }
        }
    }

    // Category picker dialog
    if (showCategoryPicker) {
        val catPickerScrollState = rememberScrollState()
        AdAwareAlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text(S.transactions.category) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(catPickerScrollState),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                                        // Transition from 1→2 categories: carry single amount to total
                                        if (selectedCats.size == 1 && singleAmountText.isNotBlank()) {
                                            totalAmountText = singleAmountText
                                            userOwnedFields = userOwnedFields + "total"
                                            // Clear the first category's amount so it doesn't duplicate the total
                                            categoryAmountTexts[selectedCats[0].id] = ""
                                        }
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
                                    contentDescription = S.common.close,
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                DialogPrimaryButton(onClick = { showCategoryPicker = false }) {
                    Text(S.common.ok)
                }
            },
            scrollState = catPickerScrollState
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
        )
        AdAwareDatePickerDialog(
            title = S.common.selectDate,
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                DialogPrimaryButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(S.common.ok) }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { showDatePicker = false }) { Text(S.common.cancel) }
            }
        ) {
            DatePicker(state = datePickerState, title = null)
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AdAwareAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            style = DialogStyle.DANGER,
            title = { Text("${S.common.delete}?") },
            text = null,
            confirmButton = {
                DialogDangerButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text(S.common.delete) }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { showDeleteConfirm = false }) { Text(S.common.cancel) }
            }
        )
    }

    // Move value dialog — shown when deselecting a category with a filled value
    if (showMoveValueDialog && pendingDeselect != null) {
        val deselectedCat = pendingDeselect!!
        val valueLabel = if (usePercentage) "$pendingDeselectValue%"
            else if (currencySymbol in CURRENCY_SUFFIX_SYMBOLS) "$pendingDeselectValue $currencySymbol"
            else "$currencySymbol$pendingDeselectValue"

        AdAwareAlertDialog(
            onDismissRequest = {
                showMoveValueDialog = false
                pendingDeselect = null
                moveTargetCatId = null
            },
            title = { Text(S.transactions.moveCategoryValue) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = S.transactions.moveCategoryBody(valueLabel, deselectedCat.name),
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
                DialogPrimaryButton(
                    onClick = {
                        val targetId = moveTargetCatId ?: return@DialogPrimaryButton
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
                    Text(S.common.save)
                }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = {
                    showMoveValueDialog = false
                    pendingDeselect = null
                    moveTargetCatId = null
                }) {
                    Text(S.common.cancel)
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

        AdAwareAlertDialog(
            onDismissRequest = { showSumMismatchDialog = false; adjustTargetId = null },
            title = { Text(S.transactions.sumMismatch) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = S.transactions.sumMismatchBody(
                            "$currencySymbol${formatAmount(mismatchCatSum, maxDecimals)}",
                            "$currencySymbol${formatAmount(mismatchTotal, maxDecimals)}"
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = S.transactions.selectFieldToAdjust,
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
                            S.transactions.total,
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
                var fixBtnYPx by remember { mutableIntStateOf(0) }
                DialogPrimaryButton(
                    onClick = {
                        val targetId = adjustTargetId ?: return@DialogPrimaryButton
                        if (targetId == "total") {
                            totalAmountText = formatAmount(mismatchCatSum, maxDecimals)
                            showSumMismatchDialog = false
                            adjustTargetId = null
                        } else {
                            val catId = targetId.toIntOrNull() ?: return@DialogPrimaryButton
                            val otherSum = selectedCats.filter { it.id != catId }.sumOf {
                                (categoryAmountTexts[it.id] ?: "").toDoubleOrNull() ?: 0.0
                            }
                            val newAmount = mismatchTotal - otherSum
                            if (newAmount < 0) {
                                toastState.show("Unable to Fix", fixBtnYPx)
                            } else {
                                categoryAmountTexts[catId] = formatAmount(newAmount, maxDecimals)
                                showSumMismatchDialog = false
                                adjustTargetId = null
                            }
                        }
                    },
                    modifier = Modifier.onGloballyPositioned { fixBtnYPx = it.positionInWindow().y.toInt() },
                    enabled = adjustTargetId != null
                ) {
                    Text(S.common.save)
                }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = {
                    showSumMismatchDialog = false
                    adjustTargetId = null
                }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // Link to recurring expense picker dialog
    if (showLinkRecurringPicker) {
        AdAwareAlertDialog(
            onDismissRequest = { showLinkRecurringPicker = false },
            title = { Text(S.transactions.linkToRecurring) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    recurringExpenses.sortedByDescending { it.amount }.forEach { re ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val txnAmt = totalAmountText.toDoubleOrNull() ?: 0.0
                                    if (txnAmt > 0 && kotlin.math.abs(txnAmt - re.amount) > 0.01) {
                                        pendingLinkEntry = re
                                        showLinkRecurringPicker = false
                                        showLinkMismatchDialog = true
                                    } else {
                                        linkedRecurringId = re.id
                                        linkedAmortizationId = null
                                        showLinkRecurringPicker = false
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(re.source, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(formatCurrency(re.amount, currencySymbol), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                DialogSecondaryButton(onClick = { showLinkRecurringPicker = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // Link to amortization entry picker dialog
    if (showLinkAmortizationPicker) {
        AdAwareAlertDialog(
            onDismissRequest = { showLinkAmortizationPicker = false },
            title = { Text(S.transactions.linkToAmortization) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    amortizationEntries.sortedByDescending { it.amount }.forEach { ae ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val txnAmt = totalAmountText.toDoubleOrNull() ?: 0.0
                                    if (txnAmt > 0 && kotlin.math.abs(txnAmt - ae.amount) > 0.01) {
                                        pendingLinkEntry = ae
                                        showLinkAmortizationPicker = false
                                        showLinkMismatchDialog = true
                                    } else {
                                        linkedAmortizationId = ae.id
                                        linkedRecurringId = null
                                        showLinkAmortizationPicker = false
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ae.source, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(formatCurrency(ae.amount, currencySymbol), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                if (onAddAmortization != null) {
                    DialogPrimaryButton(onClick = {
                        showLinkAmortizationPicker = false
                        showCreateAmortizationDialog = true
                    }) {
                        Text(S.transactions.createNewAmortization)
                    }
                }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { showLinkAmortizationPicker = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    if (showCreateAmortizationDialog && onAddAmortization != null) {
        val txnAmount = (singleAmountText.toDoubleOrNull()
            ?: totalAmountText.toDoubleOrNull()
            ?: 0.0)
        val maxDec = CURRENCY_DECIMALS[currencySymbol] ?: 2
        AddEditAmortizationDialog(
            title = S.transactions.createNewAmortization,
            initialSource = source,
            initialDescription = description,
            initialAmount = if (txnAmount > 0) "%.${maxDec}f".format(txnAmount) else "",
            initialTotalPeriods = "",
            initialStartDate = LocalDate.now(),
            currencySymbol = currencySymbol,
            budgetPeriod = budgetPeriod,
            dateFormatter = dateFormatter,
            onDismiss = { showCreateAmortizationDialog = false },
            onSave = { aSource, aDescription, aAmount, aTotalPeriods, aStartDate ->
                val newId = generateAmortizationEntryId(
                    amortizationEntries.map { it.id }.toSet()
                )
                val newEntry = AmortizationEntry(
                    id = newId,
                    source = aSource,
                    description = aDescription,
                    amount = aAmount,
                    totalPeriods = aTotalPeriods,
                    startDate = aStartDate
                )
                onAddAmortization(newEntry)
                provisionalAmortizationEntry = newEntry
                linkedAmortizationId = newId
                linkedRecurringId = null
                linkedIncomeId = null
                linkedSavingsGoalId = null
                showCreateAmortizationDialog = false
            }
        )
    }

    if (showLinkIncomePicker) {
        AdAwareAlertDialog(
            onDismissRequest = { showLinkIncomePicker = false },
            title = { Text(S.transactions.linkToIncome) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    incomeSources.sortedByDescending { it.amount }.forEach { src ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    linkedIncomeId = src.id
                                    // Auto-set recurring_income category
                                    val recurringIncomeCatId = categories.find { it.tag == "recurring_income" }?.id
                                    if (recurringIncomeCatId != null) {
                                        selectedCategoryIds.clear()
                                        selectedCategoryIds[recurringIncomeCatId] = true
                                    }
                                    showLinkIncomePicker = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(src.source, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(formatCurrency(src.amount, currencySymbol), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                DialogSecondaryButton(onClick = { showLinkIncomePicker = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // Link to savings goal picker dialog
    if (showLinkSavingsGoalPicker) {
        AdAwareAlertDialog(
            onDismissRequest = { showLinkSavingsGoalPicker = false },
            title = { Text(S.transactions.linkToSavingsGoal) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // Only fixed-contribution goals can be linked to transactions.
                    // Target-date goals are excluded because withdrawals would cause
                    // unpredictable per-period deduction spikes near the deadline.
                    savingsGoals.filter { it.targetDate == null }.sortedBy { it.name }.forEach { goal ->
                        val remaining = goal.targetAmount - goal.totalSavedSoFar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    linkedSavingsGoalId = goal.id
                                    linkedRecurringId = null
                                    linkedAmortizationId = null
                                    showLinkSavingsGoalPicker = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_coins), contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(goal.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${formatCurrency(goal.totalSavedSoFar, currencySymbol)} / ${formatCurrency(goal.targetAmount, currencySymbol)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                DialogSecondaryButton(onClick = { showLinkSavingsGoalPicker = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // Amount mismatch dialog when linking
    if (showLinkMismatchDialog && pendingLinkEntry != null) {
        val entryAmount = when (val e = pendingLinkEntry) {
            is RecurringExpense -> e.amount
            is AmortizationEntry -> e.amount
            else -> 0.0
        }
        val txnAmt = totalAmountText.toDoubleOrNull() ?: 0.0
        AdAwareAlertDialog(
            onDismissRequest = {
                showLinkMismatchDialog = false
                pendingLinkEntry = null
            },
            title = { Text(S.transactions.linkMismatchTitle) },
            text = {
                Text(S.transactions.linkMismatchBody(
                    formatCurrency(txnAmt, currencySymbol),
                    formatCurrency(entryAmount, currencySymbol)
                ))
            },
            style = DialogStyle.WARNING,
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DialogWarningButton(onClick = {
                        when (val e = pendingLinkEntry) {
                            is RecurringExpense -> {
                                linkedRecurringId = e.id
                                linkedAmortizationId = null
                            }
                            is AmortizationEntry -> {
                                linkedAmortizationId = e.id
                                linkedRecurringId = null
                            }
                        }
                        showLinkMismatchDialog = false
                        pendingLinkEntry = null
                    }) {
                        Text(S.transactions.linkAnyway)
                    }
                    DialogPrimaryButton(onClick = {
                        when (val e = pendingLinkEntry) {
                            is RecurringExpense -> {
                                linkedRecurringId = e.id
                                linkedAmortizationId = null
                                totalAmountText = e.amount.toBigDecimal().stripTrailingZeros().toPlainString()
                            }
                            is AmortizationEntry -> {
                                linkedAmortizationId = e.id
                                linkedRecurringId = null
                                totalAmountText = e.amount.toBigDecimal().stripTrailingZeros().toPlainString()
                            }
                        }
                        showLinkMismatchDialog = false
                        pendingLinkEntry = null
                    }) {
                        Text(S.transactions.updateTransactionAmount)
                    }
                }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = {
                    showLinkMismatchDialog = false
                    pendingLinkEntry = null
                }) {
                    Text(S.common.cancel)
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
    val S = LocalStrings.current
    var query by remember { mutableStateOf("") }

    AdAwareDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).imePadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column {
                DialogHeader(title = S.transactions.textSearch)
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(S.transactions.searchText) },
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
                DialogFooter {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DialogSecondaryButton(onClick = onDismiss) { Text(S.common.cancel, maxLines = 1) }
                        Spacer(modifier = Modifier.width(8.dp))
                        DialogPrimaryButton(onClick = {
                            if (query.isNotBlank()) onSearch(query.trim())
                        }) { Text(S.transactions.search, maxLines = 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountSearchDialog(
    onDismiss: () -> Unit,
    onSearch: (Double, Double) -> Unit
) {
    val S = LocalStrings.current
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

    AdAwareDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).imePadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column {
                DialogHeader(title = S.transactions.amountSearch)
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text(S.transactions.minAmount) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text(S.transactions.maxAmount) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DialogFooter {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DialogSecondaryButton(onClick = onDismiss) { Text(S.common.cancel, maxLines = 1) }
                        Spacer(modifier = Modifier.width(8.dp))
                        DialogPrimaryButton(onClick = {
                            val min = minText.toDoubleOrNull() ?: 0.0
                            val max = maxText.toDoubleOrNull() ?: Double.MAX_VALUE
                            onSearch(min, max)
                        }) { Text(S.transactions.search, maxLines = 1) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDatePickerDialog(
    title: String,
    headline: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onBack: (() -> Unit)? = null,
    bankFilterChecked: Boolean = false,
    onBankFilterChanged: (Boolean) -> Unit = {},
    showBankFilter: Boolean = false
) {
    val S = LocalStrings.current
    val datePickerState = rememberDatePickerState()

    AdAwareDatePickerDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogPrimaryButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis)
                        .atZone(ZoneId.of("UTC"))
                        .toLocalDate()
                    onDateSelected(date)
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DialogSecondaryButton(onClick = onDismiss) { Text(S.common.cancel, maxLines = 1) }
                if (onBack != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    DialogSecondaryButton(onClick = onBack) { Text(S.common.back, maxLines = 1) }
                }
            }
        }
    ) {
        Column {
            if (showBankFilter) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 24.dp, bottom = 4.dp)
                        .clickable { onBankFilterChanged(!bankFilterChecked) }
                ) {
                    Checkbox(
                        checked = bankFilterChecked,
                        onCheckedChange = onBankFilterChanged,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = S.transactions.unmodifiedBankTransactions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            DatePicker(
                state = datePickerState,
                title = null,
                headline = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    Text(
                        text = if (selectedMillis != null) {
                            val date = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
                        } else {
                            headline
                        },
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            )
        }
    }
}
