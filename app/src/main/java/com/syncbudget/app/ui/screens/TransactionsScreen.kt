package com.syncbudget.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.heightIn
import com.syncbudget.app.data.BudgetCalculator
import com.syncbudget.app.data.sync.ReceiptManager
import com.syncbudget.app.ui.components.SwipeablePhotoRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.unit.sp
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
import com.syncbudget.app.data.ExpenseReportGenerator
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
    PDF("PDF Expense Report")
}

private enum class ImportStage {
    FORMAT_SELECTION, PARSING, PARSE_ERROR, DUPLICATE_CHECK, COMPLETE
}

private enum class ViewFilter {
    ALL,
    EXPENSES,
    INCOME,
    RECURRING,
    EXCLUDED,
    NOT_VERIFIED,
    PHOTOS
}

private enum class SortMode {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC,
    CATEGORY
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
    var photoThumbRefreshKey by remember { mutableIntStateOf(0) }
    var viewFilter by remember { mutableStateOf(ViewFilter.ALL) }
    var sortMode by remember { mutableStateOf(SortMode.DATE_DESC) }
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
    // bankFilterOnly removed — use Not Verified filter toggle instead

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
    var processingUri by remember { mutableStateOf<Uri?>(null) }

    // Save state
    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedSaveFormat by remember { mutableStateOf(SaveFormat.CSV) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val saveScope = rememberCoroutineScope()

    // Full backup state
    var includeAllData by remember { mutableStateOf(false) }
    var pendingFullBackupContent by remember { mutableStateOf<String?>(null) }
    var showFullBackupDialog by remember { mutableStateOf(false) }

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

    // Transfer pendingUri to processingUri (outside LaunchedEffect to avoid key-change cancellation)
    if (pendingUri != null && processingUri == null) {
        processingUri = pendingUri
        pendingUri = null
    }

    // Process file when URI is set — dispatch on IO thread to avoid ANR
    LaunchedEffect(processingUri) {
        val uri = processingUri ?: return@LaunchedEffect
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
                    // Banks produce clean files — any skipped rows means wrong format.
                    // Fall back to generic parser if the specific parser had trouble.
                    if (r.skippedRows == 0 && r.transactions.isNotEmpty()) r else {
                        val fallbackStream = context.contentResolver.openInputStream(uri)
                            ?: return@withContext r
                        val fallbackReader = BufferedReader(InputStreamReader(fallbackStream))
                        val fallbackResult = parseGenericCsv(fallbackReader, existingIdSet)
                        fallbackReader.close()
                        if (fallbackResult.transactions.size > r.transactions.size) fallbackResult else r
                    }
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e  // Don't catch cancellation — let coroutine framework handle it
        } catch (e: Exception) {
            importError = "Error: ${e.message}"
            importStage = ImportStage.PARSE_ERROR
        } finally {
            processingUri = null
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
        var list = transactions.toList()
        list = when (viewFilter) {
            ViewFilter.EXPENSES -> list.filter { it.type == TransactionType.EXPENSE }
            ViewFilter.INCOME -> list.filter { it.type == TransactionType.INCOME }
            ViewFilter.RECURRING -> list.filter { it.linkedRecurringExpenseId != null || it.linkedRecurringExpenseAmount > 0.0 }
            ViewFilter.EXCLUDED -> list.filter { it.excludeFromBudget }
            ViewFilter.NOT_VERIFIED -> list.filter { !it.isUserCategorized }
            ViewFilter.PHOTOS -> list.filter { it.receiptId1 != null || it.receiptId2 != null || it.receiptId3 != null || it.receiptId4 != null || it.receiptId5 != null }
            ViewFilter.ALL -> list
        }
        if (searchActive && searchPredicate != null) {
            list = list.filter(searchPredicate!!)
        }
        if (categoryFilterId != null) {
            list = list.filter { t -> t.categoryAmounts.any { it.categoryId == categoryFilterId } }
        }
        when (sortMode) {
            SortMode.DATE_DESC -> list.sortedWith(
                compareByDescending<Transaction> { it.date }.thenBy { it.source }
            )
            SortMode.DATE_ASC -> list.sortedWith(
                compareBy<Transaction> { it.date }.thenBy { it.source }
            )
            SortMode.AMOUNT_DESC -> list.sortedByDescending { it.amount }
            SortMode.AMOUNT_ASC -> list.sortedBy { it.amount }
            SortMode.CATEGORY -> {
                // Sort by category usage frequency (least used first)
                val catCount = mutableMapOf<Int, Int>()
                for (t in transactions) {
                    for (ca in t.categoryAmounts) {
                        catCount[ca.categoryId] = (catCount[ca.categoryId] ?: 0) + 1
                    }
                }
                list.sortedBy { t ->
                    t.categoryAmounts.minOfOrNull { catCount[it.categoryId] ?: 0 } ?: 0
                }
            }
        }
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
                            ViewFilter.INCOME -> ViewFilter.RECURRING
                            ViewFilter.RECURRING -> ViewFilter.EXCLUDED
                            ViewFilter.EXCLUDED -> ViewFilter.NOT_VERIFIED
                            ViewFilter.NOT_VERIFIED -> ViewFilter.PHOTOS
                            ViewFilter.PHOTOS -> ViewFilter.ALL
                        }
                        scrollToTopTrigger++
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = when (viewFilter) {
                            ViewFilter.ALL -> S.transactions.all
                            ViewFilter.EXPENSES -> S.transactions.expensesFilter
                            ViewFilter.INCOME -> S.transactions.incomeFilter
                            ViewFilter.RECURRING -> S.transactions.recurringFilter
                            ViewFilter.EXCLUDED -> S.transactions.excludedFilter
                            ViewFilter.NOT_VERIFIED -> S.transactions.notVerifiedFilter
                            ViewFilter.PHOTOS -> S.transactions.photosFilter
                        },
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                OutlinedButton(
                    onClick = {
                        sortMode = when (sortMode) {
                            SortMode.DATE_DESC -> SortMode.DATE_ASC
                            SortMode.DATE_ASC -> SortMode.AMOUNT_DESC
                            SortMode.AMOUNT_DESC -> SortMode.AMOUNT_ASC
                            SortMode.AMOUNT_ASC -> SortMode.CATEGORY
                            SortMode.CATEGORY -> SortMode.DATE_DESC
                        }
                        scrollToTopTrigger++
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = when (sortMode) {
                            SortMode.DATE_DESC -> S.transactions.sortDateDesc
                            SortMode.DATE_ASC -> S.transactions.sortDateAsc
                            SortMode.AMOUNT_DESC -> S.transactions.sortAmountDesc
                            SortMode.AMOUNT_ASC -> S.transactions.sortAmountAsc
                            SortMode.CATEGORY -> S.transactions.sortCategory
                        },
                        fontSize = 20.sp
                    )
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
                    val txnContext = LocalContext.current
                    val photoScope = rememberCoroutineScope()
                    if (isPaidUser) {
                        val thumbnails = remember(transaction.receiptId1, transaction.receiptId2, transaction.receiptId3, transaction.receiptId4, transaction.receiptId5, photoThumbRefreshKey) {
                            listOf(transaction.receiptId1, transaction.receiptId2, transaction.receiptId3, transaction.receiptId4, transaction.receiptId5)
                                .map { id -> id?.let { ReceiptManager.loadThumbnail(txnContext, it) } }
                        }
                        SwipeablePhotoRow(
                            transactionId = transaction.id,
                            photos = thumbnails,
                            receiptIds = listOf(transaction.receiptId1, transaction.receiptId2, transaction.receiptId3, transaction.receiptId4, transaction.receiptId5),
                            onPhotosAdded = { files ->
                                photoScope.launch(Dispatchers.IO) {
                                    var txn = transaction
                                    for (file in files) {
                                        val slot = ReceiptManager.nextEmptySlot(txn) ?: break
                                        val rid = ReceiptManager.generateReceiptId()
                                        val dest = ReceiptManager.getReceiptFile(txnContext, rid)
                                        file.copyTo(dest, overwrite = true)
                                        val oldThumb = java.io.File(java.io.File(txnContext.filesDir, "receipt_thumbs"), file.name)
                                        if (oldThumb.exists()) {
                                            oldThumb.copyTo(ReceiptManager.getThumbFile(txnContext, rid), overwrite = true)
                                            oldThumb.delete()
                                        }
                                        file.delete()
                                        ReceiptManager.addToPendingQueue(txnContext, rid)
                                        txn = when (slot) {
                                            1 -> txn.copy(receiptId1 = rid)
                                            2 -> txn.copy(receiptId2 = rid)
                                            3 -> txn.copy(receiptId3 = rid)
                                            4 -> txn.copy(receiptId4 = rid)
                                            5 -> txn.copy(receiptId5 = rid)
                                            else -> txn
                                        }
                                    }
                                    if (txn !== transaction) {
                                        withContext(Dispatchers.Main) { onUpdateTransaction(txn) }
                                    }
                                }
                            },
                            onPhotoDelete = { slotIndex ->
                                val receiptIds = listOf(transaction.receiptId1, transaction.receiptId2, transaction.receiptId3, transaction.receiptId4, transaction.receiptId5)
                                val rid = receiptIds.getOrNull(slotIndex) ?: return@SwipeablePhotoRow
                                // Update transaction immediately on main thread, delete file in background
                                val updated = when (slotIndex) {
                                    0 -> transaction.copy(receiptId1 = null)
                                    1 -> transaction.copy(receiptId2 = null)
                                    2 -> transaction.copy(receiptId3 = null)
                                    3 -> transaction.copy(receiptId4 = null)
                                    4 -> transaction.copy(receiptId5 = null)
                                    else -> transaction
                                }
                                onUpdateTransaction(updated)
                                photoThumbRefreshKey++
                                photoScope.launch(Dispatchers.IO) {
                                    ReceiptManager.deleteLocalReceipt(txnContext, rid)
                                }
                            },
                            onPhotoRotated = { photoThumbRefreshKey++ },
                            onSwipeOpen = { expandedIds[transaction.id] = false },
                            enabled = !selectionMode
                        ) {
                            TransactionRow(
                                transaction = transaction, currencySymbol = currencySymbol,
                                dateFormatter = dateFormatter, categoryMap = categoryMap,
                                selectionMode = selectionMode,
                                isSelected = selectedIds[transaction.id] == true,
                                isExpanded = expandedIds[transaction.id] == true,
                                onTap = { if (selectionMode) { selectedIds[transaction.id] = !(selectedIds[transaction.id] ?: false) } else { editingTransaction = transaction } },
                                onLongPress = { if (!selectionMode) { selectionMode = true; selectedIds.clear() }; selectedIds[transaction.id] = true },
                                onToggleSelection = { checked -> selectedIds[transaction.id] = checked },
                                onToggleExpand = { expandedIds[transaction.id] = !(expandedIds[transaction.id] ?: false) },
                                onCategoryFilter = { catId -> categoryFilterId = catId; viewFilter = ViewFilter.ALL; selectionMode = false; selectedIds.clear() },
                                attributionLabel = if (showAttribution && transaction.deviceId.isNotEmpty()) { if (transaction.deviceId == localDeviceId) S.sync.you else deviceNameMap[transaction.deviceId] ?: transaction.deviceId.take(8) } else null,
                                isLinkedRecurring = isLinkedRecurring, isLinkedAmortization = isLinkedAmortization,
                                isAmortComplete = isAmortComplete, isLinkedIncome = isLinkedIncome,
                                linkedRecurringAmount = linkedRecurringAmount, linkedAmortizationApplied = linkedAmortizationApplied,
                                linkedIncomeAmount = linkedIncomeAmount, incomeMode = incomeMode,
                                isLinkedSavingsGoal = isLinkedSavingsGoal,
                                hasPhotos = transaction.receiptId1 != null || transaction.receiptId2 != null || transaction.receiptId3 != null || transaction.receiptId4 != null || transaction.receiptId5 != null,
                                onEffectTap = { effectExplanationTransaction = transaction }
                            )
                        }
                    } else {
                        TransactionRow(
                            transaction = transaction, currencySymbol = currencySymbol,
                            dateFormatter = dateFormatter, categoryMap = categoryMap,
                            selectionMode = selectionMode,
                            isSelected = selectedIds[transaction.id] == true,
                            isExpanded = expandedIds[transaction.id] == true,
                            onTap = { if (selectionMode) { selectedIds[transaction.id] = !(selectedIds[transaction.id] ?: false) } else { editingTransaction = transaction } },
                            onLongPress = { if (!selectionMode) { selectionMode = true; selectedIds.clear() }; selectedIds[transaction.id] = true },
                            onToggleSelection = { checked -> selectedIds[transaction.id] = checked },
                            onToggleExpand = { expandedIds[transaction.id] = !(expandedIds[transaction.id] ?: false) },
                            onCategoryFilter = { catId -> categoryFilterId = catId; viewFilter = ViewFilter.ALL; selectionMode = false; selectedIds.clear() },
                            attributionLabel = if (showAttribution && transaction.deviceId.isNotEmpty()) { if (transaction.deviceId == localDeviceId) S.sync.you else deviceNameMap[transaction.deviceId] ?: transaction.deviceId.take(8) } else null,
                            isLinkedRecurring = isLinkedRecurring, isLinkedAmortization = isLinkedAmortization,
                            isAmortComplete = isAmortComplete, isLinkedIncome = isLinkedIncome,
                            linkedRecurringAmount = linkedRecurringAmount, linkedAmortizationApplied = linkedAmortizationApplied,
                            linkedIncomeAmount = linkedIncomeAmount, incomeMode = incomeMode,
                            isLinkedSavingsGoal = isLinkedSavingsGoal,
                            hasPhotos = transaction.receiptId1 != null || transaction.receiptId2 != null || transaction.receiptId3 != null || transaction.receiptId4 != null || transaction.receiptId5 != null,
                            onEffectTap = { effectExplanationTransaction = transaction }
                        )
                    }
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
            isPaidUser = isPaidUser,
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
            isPaidUser = isPaidUser,
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
            isPaidUser = isPaidUser,
            onDismiss = { editingTransaction = null },
            onUpdatePhoto = { updated ->
                // Update transaction (photo add/delete) without closing dialog
                onUpdateTransaction(updated)
                editingTransaction = updated
                photoThumbRefreshKey++
            },
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
            }
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
                    searchPredicate = { t -> !t.date.isBefore(start) && !t.date.isAfter(endDate) }
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
            scrollable = false,  // content has LazyColumn
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
                                        saveError = null
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedSaveFormat == format,
                                    onClick = {
                                        selectedSaveFormat = format
                                        saveError = null
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(when (format) {
                                    SaveFormat.CSV -> S.transactions.csv
                                    SaveFormat.XLS -> S.transactions.xls
                                    SaveFormat.PDF -> format.label
                                }, style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        val transactionsToSave = if (selectionMode && selectedIds.any { it.value })
                            transactions.filter { selectedIds[it.id] == true } else transactions
                        Text(S.transactions.selectedCount(transactionsToSave.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

                    }

                    DialogFooter {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DialogSecondaryButton(onClick = {
                                showSaveDialog = false
                                saveError = null
                            }) { Text(S.common.cancel) }
                            Spacer(modifier = Modifier.width(8.dp))
                            DialogPrimaryButton(onClick = {
                                when (selectedSaveFormat) {
                                    SaveFormat.CSV -> {
                                        showSaveDialog = false
                                        csvSaveLauncher.launch("budgetrak_transactions.csv")
                                    }
                                    SaveFormat.XLS -> {
                                        showSaveDialog = false
                                        xlsSaveLauncher.launch("budgetrak_transactions.xlsx")
                                    }
                                    SaveFormat.PDF -> {
                                        showSaveDialog = false
                                        val toSave = if (selectionMode && selectedIds.any { it.value }) {
                                            transactions.filter { selectedIds[it.id] == true }
                                        } else { transactions }
                                        saveScope.launch(Dispatchers.IO) {
                                            try {
                                                val files = ExpenseReportGenerator.generateReports(context, toSave, categories, currencySymbol)
                                                withContext(Dispatchers.Main) {
                                                    toastState.show("${files.size} expense report(s) saved to Download/BudgeTrak/PDF", durationMs = 7500L)
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    toastState.show("PDF generation failed: ${e.message}")
                                                }
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
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedBankFormat == format,
                                    onClick = {
                                        selectedBankFormat = format
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(when (format) {
                                    BankFormat.GENERIC_CSV -> S.transactions.formatGenericCsv
                                    BankFormat.US_BANK -> S.transactions.formatUsBank
                                    BankFormat.SECURESYNC_CSV -> S.transactions.formatBudgeTrakCsv
                                }, style = MaterialTheme.typography.bodyLarge)
                            }
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
                            }) { Text(S.common.cancel) }
                            Spacer(modifier = Modifier.width(8.dp))
                            DialogPrimaryButton(
                                onClick = {
                                    showImportFormatDialog = false
                                    filePickerLauncher.launch(arrayOf("text/*", "*/*"))
                                }
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
    hasPhotos: Boolean = false,
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
                .heightIn(min = 56.dp)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                )
                .padding(horizontal = 12.dp, vertical = if (useExpandedLayout) 10.dp else 8.dp),
            verticalArrangement = Arrangement.Center
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (hasPhotos) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        if (isLinkedRecurring) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (isLinkedAmortization) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = if (isAmortComplete) Color(0xFF4CAF50) else Color(0xFFF44336),
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (isLinkedIncome) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalance,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (isLinkedSavingsGoal) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_coins),
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (transaction.excludeFromBudget) {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = null,
                                tint = Color(0xFF9E9E9E),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(3.dp))
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
    isPaidUser: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onUpdatePhoto: ((Transaction) -> Unit)? = null,  // update transaction without closing dialog
    onDelete: (() -> Unit)? = null,
    onAddAmortization: ((AmortizationEntry) -> Unit)? = null,
    onDeleteAmortization: ((AmortizationEntry) -> Unit)? = null
) {
    val S = LocalStrings.current
    val maxDecimals = CURRENCY_DECIMALS[currencySymbol] ?: 2
    val context = LocalContext.current
    val toastState = LocalAppToast.current
    val isEdit = editTransaction != null
    val isSupercharge = editTransaction?.categoryAmounts?.any { ca ->
        categories.find { it.id == ca.categoryId }?.tag == "supercharge"
    } ?: false
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

    // Track photos added during this dialog session (for add mode orphan cleanup)
    val addedPhotoIds = remember { mutableStateListOf<String>() }
    var addModeReceiptId1 by remember { mutableStateOf<String?>(null) }
    var addModeReceiptId2 by remember { mutableStateOf<String?>(null) }
    var addModeReceiptId3 by remember { mutableStateOf<String?>(null) }
    var addModeReceiptId4 by remember { mutableStateOf<String?>(null) }
    var addModeReceiptId5 by remember { mutableStateOf<String?>(null) }

    // Dialog camera state (for header camera icon)
    var dialogTempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val dialogPhotoScope = rememberCoroutineScope()
    val dialogCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && dialogTempPhotoUri != null) {
            dialogPhotoScope.launch(Dispatchers.IO) {
                val rid = ReceiptManager.processAndSaveFromCamera(context, dialogTempPhotoUri!!)
                if (rid != null) {
                    ReceiptManager.addToPendingQueue(context, rid)
                    withContext(Dispatchers.Main) {
                        if (isEdit && editTransaction != null) {
                            val slot = ReceiptManager.nextEmptySlot(editTransaction)
                            if (slot != null) {
                                val updated = when (slot) {
                                    1 -> editTransaction.copy(receiptId1 = rid)
                                    2 -> editTransaction.copy(receiptId2 = rid)
                                    3 -> editTransaction.copy(receiptId3 = rid)
                                    4 -> editTransaction.copy(receiptId4 = rid)
                                    5 -> editTransaction.copy(receiptId5 = rid)
                                    else -> editTransaction
                                }
                                onUpdatePhoto?.invoke(updated)
                            }
                        } else {
                            // Add mode — track locally
                            when {
                                addModeReceiptId1 == null -> addModeReceiptId1 = rid
                                addModeReceiptId2 == null -> addModeReceiptId2 = rid
                                addModeReceiptId3 == null -> addModeReceiptId3 = rid
                                addModeReceiptId4 == null -> addModeReceiptId4 = rid
                                addModeReceiptId5 == null -> addModeReceiptId5 = rid
                            }
                            addedPhotoIds.add(rid)
                        }
                    }
                }
            }
        }
    }
    val dialogGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        val remaining = if (isEdit && editTransaction != null) {
            5 - listOfNotNull(editTransaction.receiptId1, editTransaction.receiptId2, editTransaction.receiptId3, editTransaction.receiptId4, editTransaction.receiptId5).size
        } else {
            5 - listOfNotNull(addModeReceiptId1, addModeReceiptId2, addModeReceiptId3, addModeReceiptId4, addModeReceiptId5).size
        }
        val toProcess = uris.take(remaining)
        if (toProcess.isNotEmpty()) {
            dialogPhotoScope.launch(Dispatchers.IO) {
                for (uri in toProcess) {
                    val rid = ReceiptManager.processAndSavePhoto(context, uri) ?: continue
                    ReceiptManager.addToPendingQueue(context, rid)
                    withContext(Dispatchers.Main) {
                        if (isEdit && editTransaction != null) {
                            val slot = ReceiptManager.nextEmptySlot(editTransaction)
                            if (slot != null) {
                                val updated = when (slot) {
                                    1 -> editTransaction.copy(receiptId1 = rid)
                                    2 -> editTransaction.copy(receiptId2 = rid)
                                    3 -> editTransaction.copy(receiptId3 = rid)
                                    4 -> editTransaction.copy(receiptId4 = rid)
                                    5 -> editTransaction.copy(receiptId5 = rid)
                                    else -> editTransaction
                                }
                                onUpdatePhoto?.invoke(updated)
                            }
                        } else {
                            when {
                                addModeReceiptId1 == null -> addModeReceiptId1 = rid
                                addModeReceiptId2 == null -> addModeReceiptId2 = rid
                                addModeReceiptId3 == null -> addModeReceiptId3 = rid
                                addModeReceiptId4 == null -> addModeReceiptId4 = rid
                                addModeReceiptId5 == null -> addModeReceiptId5 = rid
                            }
                            addedPhotoIds.add(rid)
                        }
                    }
                }
            }
        }
    }

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

    var showDiscardConfirm by remember { mutableStateOf(false) }

    val hasUnsavedContent = !isEdit && (
        source.isNotBlank() || description.isNotBlank() || addedPhotoIds.isNotEmpty() ||
        selectedCategoryIds.any { it.value }
    )

    val dismissWithCleanup: () -> Unit = {
        if (hasUnsavedContent) {
            showDiscardConfirm = true
        } else {
            // Clean up add-mode photos
            if (!isEdit && addedPhotoIds.isNotEmpty()) {
                for (rid in addedPhotoIds) {
                    ReceiptManager.deleteLocalReceipt(context, rid)
                    ReceiptManager.removeFromPendingQueue(context, rid)
                }
            }
            provisionalAmortizationEntry?.let { entry ->
                onDeleteAmortization?.invoke(entry)
            }
            onDismiss()
        }
    }

    if (showDiscardConfirm) {
        AdAwareAlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard Changes?") },  // TODO: i18n
            style = DialogStyle.WARNING,
            text = { Text("You have unsaved changes. Discard them?") },  // TODO: i18n
            confirmButton = {
                DialogWarningButton(onClick = {
                    showDiscardConfirm = false
                    // Clean up add-mode photos
                    for (rid in addedPhotoIds) {
                        ReceiptManager.deleteLocalReceipt(context, rid)
                        ReceiptManager.removeFromPendingQueue(context, rid)
                    }
                    provisionalAmortizationEntry?.let { entry ->
                        onDeleteAmortization?.invoke(entry)
                    }
                    onDismiss()
                }) { Text("Discard") }  // TODO: i18n
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep Editing")  // TODO: i18n
                }
            }
        )
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
                // Title bar with optional camera icon
                val headerBg = dialogHeaderColor()
                val headerTxt = dialogHeaderTextColor()
                var showDialogCameraPicker by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = headerTxt)
                    if (!isPaidUser) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Photos (paid feature)",
                            tint = headerTxt.copy(alpha = 0.3f),
                            modifier = Modifier.align(Alignment.CenterEnd).size(20.dp)
                                .clickable { toastState.show(S.settings.upgradeForPhotos) }
                        )
                    }
                    if (isPaidUser) {
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Add photo",
                                tint = headerTxt,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { showDialogCameraPicker = true }
                            )
                            DropdownMenu(
                                expanded = showDialogCameraPicker,
                                onDismissRequest = { showDialogCameraPicker = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Camera") },
                                    leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
                                    onClick = {
                                        showDialogCameraPicker = false
                                        val file = java.io.File(context.cacheDir, "receipt_dialog_${java.util.UUID.randomUUID()}.jpg")
                                        dialogTempPhotoUri = androidx.core.content.FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", file
                                        )
                                        dialogCameraLauncher.launch(dialogTempPhotoUri!!)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Gallery") },
                                    leadingIcon = { Icon(Icons.Filled.Collections, null) },
                                    onClick = {
                                        showDialogCameraPicker = false
                                        dialogGalleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                )
                            }
                        }
                    }
                }

                // Photo thumbnail bar (paid users, add or edit mode)
                if (isPaidUser) {
                    val dialogReceiptIds = if (isEdit && editTransaction != null) {
                        listOf(editTransaction.receiptId1, editTransaction.receiptId2, editTransaction.receiptId3, editTransaction.receiptId4, editTransaction.receiptId5)
                    } else {
                        listOf(addModeReceiptId1, addModeReceiptId2, addModeReceiptId3, addModeReceiptId4, addModeReceiptId5)
                    }
                    val hasPhotos = dialogReceiptIds.any { it != null }
                    if (hasPhotos) {
                        var dialogThumbRefresh by remember { mutableIntStateOf(0) }
                        val dialogThumbnails = remember(dialogReceiptIds[0], dialogReceiptIds[1], dialogReceiptIds[2], dialogReceiptIds[3], dialogReceiptIds[4], dialogThumbRefresh) {
                            dialogReceiptIds.map { id -> id?.let { ReceiptManager.loadThumbnail(context, it) } }
                        }
                        var dialogFullScreenSlot by remember { mutableIntStateOf(-1) }
                        var dialogDeleteConfirmSlot by remember { mutableIntStateOf(-1) }
                        val dialogPhotoFrameSize = 48.dp

                        // Delete confirmation
                        if (dialogDeleteConfirmSlot >= 0) {
                            AdAwareAlertDialog(
                                onDismissRequest = { dialogDeleteConfirmSlot = -1 },
                                title = { Text(S.settings.deletePhotoTitle) },
                                style = DialogStyle.DANGER,
                                text = { Text(S.settings.deletePhotoConfirm) },
                                confirmButton = {
                                    DialogDangerButton(onClick = {
                                        val rid = dialogReceiptIds.getOrNull(dialogDeleteConfirmSlot)
                                        if (rid != null) {
                                            if (isEdit && editTransaction != null) {
                                                val updated = when (dialogDeleteConfirmSlot) {
                                                    0 -> editTransaction.copy(receiptId1 = null)
                                                    1 -> editTransaction.copy(receiptId2 = null)
                                                    2 -> editTransaction.copy(receiptId3 = null)
                                                    3 -> editTransaction.copy(receiptId4 = null)
                                                    4 -> editTransaction.copy(receiptId5 = null)
                                                    else -> editTransaction
                                                }
                                                onUpdatePhoto?.invoke(updated)
                                            } else {
                                                // Add mode — update local state
                                                when (dialogDeleteConfirmSlot) {
                                                    0 -> addModeReceiptId1 = null
                                                    1 -> addModeReceiptId2 = null
                                                    2 -> addModeReceiptId3 = null
                                                    3 -> addModeReceiptId4 = null
                                                    4 -> addModeReceiptId5 = null
                                                }
                                                addedPhotoIds.remove(rid)
                                            }
                                            dialogThumbRefresh++
                                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                                ReceiptManager.deleteLocalReceipt(context, rid)
                                                ReceiptManager.removeFromPendingQueue(context, rid)
                                            }
                                        }
                                        dialogDeleteConfirmSlot = -1
                                    }) { Text(S.common.delete) }
                                },
                                dismissButton = {
                                    DialogSecondaryButton(onClick = { dialogDeleteConfirmSlot = -1 }) { Text(S.common.cancel) }
                                }
                            )
                        }

                        // Full-screen viewer
                        if (dialogFullScreenSlot >= 0) {
                            val viewRid = dialogReceiptIds.getOrNull(dialogFullScreenSlot)
                            val viewBmp = remember(viewRid) {
                                viewRid?.let { ReceiptManager.loadFullImage(context, it) }
                                    ?: dialogThumbnails.getOrNull(dialogFullScreenSlot)
                            }
                            if (viewBmp != null) {
                                com.syncbudget.app.ui.components.FullScreenPhotoViewer(
                                    bitmap = viewBmp,
                                    receiptId = viewRid,
                                    onDismiss = { dialogFullScreenSlot = -1 },
                                    onDelete = {
                                        val slot = dialogFullScreenSlot
                                        dialogFullScreenSlot = -1
                                        dialogDeleteConfirmSlot = slot
                                    },
                                    onRotated = { dialogThumbRefresh++ }
                                )
                            } else {
                                dialogFullScreenSlot = -1
                            }
                        }

                        // Thumbnail row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 5) {
                                val thumb = dialogThumbnails.getOrNull(i)
                                val rid = dialogReceiptIds.getOrNull(i)
                                if (thumb == null && rid == null) continue
                                Box(
                                    modifier = Modifier
                                        .size(dialogPhotoFrameSize)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (thumb != null) Color.Transparent else MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .then(
                                            if (thumb != null) {
                                                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                                                Modifier.combinedClickable(
                                                    onClick = { dialogFullScreenSlot = i },
                                                    onLongClick = { dialogDeleteConfirmSlot = i }
                                                )
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = "Receipt photo ${i + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.CameraAlt, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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
                            enabled = !isSupercharge,
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
                    // Hidden entirely for supercharge transactions (linking is locked)
                    if (isSupercharge) {
                        // No linking UI for supercharge transactions
                    } else if (isExpense) {
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
                    // Hidden for supercharge transactions (category is locked)
                    if (categories.isNotEmpty() && linkedIncomeId == null && !isSupercharge) {
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
                        if (!isSupercharge) {
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
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        DialogSecondaryButton(onClick = dismissWithCleanup) { Text(S.common.cancel) }
                        Spacer(modifier = Modifier.width(8.dp))
                        DialogPrimaryButton(
                            onClick = {
                                if (source.isBlank() || selectedCats.isEmpty()) { showValidation = true; return@DialogPrimaryButton }
                                val type = if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME
                                var catAmounts: List<CategoryAmount>
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
                                        var catSum = catAmounts.sumOf { it.amount }
                                        // Nudge the largest category to fix rounding drift
                                        // from pie chart splits (up to 1 cent per category)
                                        if (total != null && abs(catSum - total) in 0.005..0.10 && catAmounts.isNotEmpty()) {
                                            val maxIdx = catAmounts.indices.maxByOrNull { catAmounts[it].amount } ?: 0
                                            val nudged = catAmounts[maxIdx].copy(
                                                amount = BudgetCalculator.roundCents(catAmounts[maxIdx].amount + (total - catSum))
                                            )
                                            catAmounts = catAmounts.toMutableList().also { it[maxIdx] = nudged }
                                            catSum = catAmounts.sumOf { it.amount }
                                        }
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
                                    excludeFromBudget = excludeFromBudget,
                                    receiptId1 = addModeReceiptId1,
                                    receiptId2 = addModeReceiptId2,
                                    receiptId3 = addModeReceiptId3,
                                    receiptId4 = addModeReceiptId4,
                                    receiptId5 = addModeReceiptId5
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
            scrollable = false,  // content has its own verticalScroll
            scrollState = catPickerScrollState,  // for PulsingScrollArrow
            text = {
                Column(
                    modifier = Modifier.verticalScroll(catPickerScrollState),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.filter { it.tag != "supercharge" }.forEach { cat ->
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
                                    } else if (selectedCats.size >= 7) {
                                        // Max 7 categories per transaction
                                        toastState.show(S.transactions.maxCategoriesReached)
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
            scrollable = false,  // content has LazyColumn
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
            scrollable = false,
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
            scrollable = false,
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
            scrollable = false,
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
            scrollable = false,
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
