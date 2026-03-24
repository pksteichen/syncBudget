package com.syncbudget.app.widget

import android.content.Context
import android.os.Bundle
import com.syncbudget.app.data.sync.SyncWriteHelper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.AmortizationRepository
import com.syncbudget.app.data.BudgetCalculator
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.CategoryRepository
import com.syncbudget.app.data.IncomeMode
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.IncomeSourceRepository
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.RecurringExpenseRepository
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionRepository
import com.syncbudget.app.data.TransactionType
import com.syncbudget.app.data.findAmortizationMatch
import com.syncbudget.app.data.findBudgetIncomeMatch
import com.syncbudget.app.data.findDuplicate
import com.syncbudget.app.data.findRecurringExpenseMatch
import com.syncbudget.app.data.generateTransactionId
import com.syncbudget.app.data.getCategoryIcon
import com.syncbudget.app.data.sync.SyncIdGenerator
import com.syncbudget.app.data.sync.active
import com.syncbudget.app.data.getDoubleCompat
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS
import com.syncbudget.app.ui.strings.AppStrings
import com.syncbudget.app.ui.strings.EnglishStrings
import com.syncbudget.app.ui.strings.SpanishStrings
import com.syncbudget.app.ui.strings.WidgetTransactionStrings
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class WidgetTransactionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crash logger — writes stack trace to BudgeTrak/support/crash_log.txt
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sb = StringBuilder()
                sb.appendLine("=== WidgetCrash ${java.time.LocalDateTime.now()} ===")
                sb.appendLine("Thread: ${thread.name}")
                sb.appendLine("Android: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
                sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                sb.appendLine()
                var t: Throwable? = throwable
                while (t != null) {
                    sb.appendLine("${t.javaClass.name}: ${t.message}")
                    for (el in t.stackTrace) sb.appendLine("  at $el")
                    t = t.cause
                    if (t != null) sb.appendLine("Caused by:")
                }
                val dir = com.syncbudget.app.data.BackupManager.getSupportDir()
                java.io.File(dir, "crash_log.txt").appendText(sb.toString())
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        setFinishOnTouchOutside(false)

        val isExpense = intent?.action == BudgetWidgetProvider.ACTION_ADD_EXPENSE

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val context = this@WidgetTransactionActivity
                val focusManager = LocalFocusManager.current
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val currencySymbol = prefs.getString("currencySymbol", "$") ?: "$"
                val maxDecimals = CURRENCY_DECIMALS[currencySymbol] ?: 2
                val appLanguage = prefs.getString("appLanguage", "en") ?: "en"
                val S: AppStrings = if (appLanguage == "es") SpanishStrings else EnglishStrings
                val W = S.widgetTransaction
                val dateFormatPattern = prefs.getString("dateFormatPattern", "yyyy-MM-dd") ?: "yyyy-MM-dd"
                val dateFormatter = remember(dateFormatPattern) {
                    DateTimeFormatter.ofPattern(dateFormatPattern)
                }

                val categories = remember { CategoryRepository.load(context).active.filter { it.widgetVisible } }
                val pastSources = remember { TransactionRepository.load(context).active.groupingBy { it.source }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
                val selectedCategoryIds = remember { mutableStateMapOf<Int, Boolean>() }
                val categoryAmounts = remember { mutableStateMapOf<Int, String>() }
                var amount by remember { mutableStateOf("") }
                var source by remember { mutableStateOf("") }
                var description by remember { mutableStateOf("") }

                val isPaidUser = prefs.getBoolean("isPaidUser", false)
                val today = LocalDate.now().toString()
                val widgetTxDate = prefs.getString("widgetTxDate", "") ?: ""
                val widgetTxCount = if (widgetTxDate == today) prefs.getInt("widgetTxCount", 0) else 0
                val atDailyLimit = !isPaidUser && widgetTxCount >= 1

                // Matching settings
                val matchDays = prefs.getInt("matchDays", 7)
                val matchPercent = prefs.getDoubleCompat("matchPercent", 1.0)
                val matchDollar = prefs.getInt("matchDollar", 1)
                val matchChars = prefs.getInt("matchChars", 5)
                val percentTolerance = matchPercent / 100.0

                // Matching dialog state
                var pendingTxn by remember { mutableStateOf<Transaction?>(null) }
                var pendingCatAmounts by remember { mutableStateOf<List<CategoryAmount>>(emptyList()) }
                var duplicateMatch by remember { mutableStateOf<Transaction?>(null) }
                var recurringMatch by remember { mutableStateOf<RecurringExpense?>(null) }
                var amortizationMatch by remember { mutableStateOf<AmortizationEntry?>(null) }
                var budgetIncomeMatch by remember { mutableStateOf<IncomeSource?>(null) }

                val headerBg = if (isExpense) Color(0xFFB71C1C) else Color(0xFF1B5E20)
                val headerText = Color.White
                val title = if (isExpense) W.quickExpense else W.quickIncome

                val selectedIds = selectedCategoryIds.filter { it.value }.keys.toList()
                val parsedAmount = amount.toDoubleOrNull()
                val totalCatAmounts = selectedIds.sumOf { id ->
                    categoryAmounts[id]?.toDoubleOrNull() ?: 0.0
                }
                val remaining = (parsedAmount ?: 0.0) - totalCatAmounts
                val isSingleCategory = selectedIds.size == 1
                val canSave = parsedAmount != null && parsedAmount >= 0 && source.isNotBlank() &&
                    selectedIds.isNotEmpty() && (isSingleCategory || abs(remaining) < 0.005)

                // Check for recurring/amortization/income linking (no duplicate check)
                fun runLinkingChain(txn: Transaction, catAmounts: List<CategoryAmount>) {
                    val alreadyLinked = txn.linkedRecurringExpenseId != null ||
                        txn.linkedAmortizationEntryId != null ||
                        txn.linkedIncomeSourceId != null
                    if (!alreadyLinked) {
                        val activeRecurring = RecurringExpenseRepository.load(context).active
                        val reMatch = findRecurringExpenseMatch(txn, activeRecurring, percentTolerance, matchDollar, matchChars, matchDays)
                        if (reMatch != null) {
                            pendingTxn = txn
                            pendingCatAmounts = catAmounts
                            recurringMatch = reMatch
                            return
                        }
                        val activeAmort = AmortizationRepository.load(context).active
                        val amMatch = findAmortizationMatch(txn, activeAmort, percentTolerance, matchDollar, matchChars)
                        if (amMatch != null) {
                            pendingTxn = txn
                            pendingCatAmounts = catAmounts
                            amortizationMatch = amMatch
                            return
                        }
                        val activeIncome = IncomeSourceRepository.load(context).active
                        val incMatch = findBudgetIncomeMatch(txn, activeIncome, matchChars, matchDays)
                        if (incMatch != null) {
                            pendingTxn = txn
                            pendingCatAmounts = catAmounts
                            budgetIncomeMatch = incMatch
                            return
                        }
                    }
                    // No matches — save directly
                    saveTransaction(context, txn)
                    prefs.edit()
                        .putString("widgetTxDate", today)
                        .putInt("widgetTxCount", widgetTxCount + 1)
                        .apply()
                    finish()
                }

                // Full matching chain: duplicate check first, then linking
                fun runMatchingChain(txn: Transaction, catAmounts: List<CategoryAmount>) {
                    val existingTransactions = TransactionRepository.load(context).active
                    val dup = findDuplicate(txn, existingTransactions, percentTolerance, matchDollar, matchDays, matchChars)
                    if (dup != null) {
                        pendingTxn = txn
                        pendingCatAmounts = catAmounts
                        duplicateMatch = dup
                        return
                    }
                    runLinkingChain(txn, catAmounts)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { focusManager.clearFocus() }
                    ) {
                        // Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(headerBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                color = headerText
                            )
                        }

                        // Fixed amount field + remaining message
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { newVal ->
                                    val filtered = newVal.filter { it.isDigit() || it == '.' }
                                    val parts = filtered.split(".")
                                    if (parts.size <= 2) {
                                        if (parts.size == 2 && parts[1].length > maxDecimals) return@OutlinedTextField
                                        amount = filtered
                                    }
                                },
                                label = { Text(W.amountLabel(currencySymbol)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Remaining message when multiple categories selected
                            if (selectedIds.size > 1 && parsedAmount != null && parsedAmount!! > 0) {
                                val remainingColor = when {
                                    abs(remaining) < 0.005 -> Color(0xFF4CAF50)
                                    remaining < 0 -> Color(0xFFEF5350)
                                    else -> Color(0xFFFFB74D)
                                }
                                Text(
                                    text = W.remaining(currencySymbol, "%.${maxDecimals}f".format(remaining)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = remainingColor,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }

                        // Scrollable body: merchant, description, category amount fields
                        val bodyScrollState = rememberScrollState()
                        LaunchedEffect(selectedIds.size) {
                            if (selectedIds.size > 1) {
                                bodyScrollState.animateScrollTo(bodyScrollState.maxValue)
                            }
                        }
                        val canScrollUp by remember { derivedStateOf { bodyScrollState.canScrollBackward } }
                        val canScrollDown by remember { derivedStateOf { bodyScrollState.canScrollForward } }

                        Box(modifier = Modifier.heightIn(max = 156.dp)) {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(bodyScrollState)
                                    .padding(horizontal = 20.dp)
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Source / Merchant with autocomplete
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
                                        onValueChange = { source = it.take(50) },
                                        label = { Text(if (isExpense) W.merchantService else W.source) },
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

                                // Description
                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it.take(100) },
                                    label = { Text(W.descriptionOptional) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Category amount fields (when >1 category selected)
                                if (selectedIds.size > 1) {
                                    selectedIds.forEach { catId ->
                                        val cat = categories.find { it.id == catId } ?: return@forEach
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = getCategoryIcon(cat.iconName),
                                                contentDescription = cat.name,
                                                modifier = Modifier.size(24.dp),
                                                tint = headerBg
                                            )
                                            OutlinedTextField(
                                                value = categoryAmounts[catId] ?: "",
                                                onValueChange = { newVal ->
                                                    val filtered = newVal.filter { it.isDigit() || it == '.' }
                                                    val parts = filtered.split(".")
                                                    if (parts.size <= 2) {
                                                        if (parts.size == 2 && parts[1].length > maxDecimals) return@OutlinedTextField
                                                        categoryAmounts[catId] = filtered
                                                    }
                                                },
                                                label = { Text(cat.name) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Pulsing up arrow (top-left)
                            if (canScrollUp) {
                                val transition = rememberInfiniteTransition(label = "upArrow")
                                val offsetY by transition.animateFloat(
                                    initialValue = 0f, targetValue = -4f,
                                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                                    label = "upBounce"
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 2.dp)
                                        .offset(y = offsetY.dp)
                                        .size(20.dp)
                                )
                            }

                            // Pulsing down arrow (bottom-left)
                            if (canScrollDown) {
                                val transition = rememberInfiniteTransition(label = "downArrow")
                                val offsetY by transition.animateFloat(
                                    initialValue = 0f, targetValue = 4f,
                                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                                    label = "downBounce"
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 2.dp)
                                        .offset(y = offsetY.dp)
                                        .size(20.dp)
                                )
                            }
                        }

                        // Footer
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (atDailyLimit) {
                                // Replace category picker with limit message
                                Text(
                                    W.freeVersionLimit,
                                    color = Color(0xFFFF9800),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                // Category icon strip
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    categories.filter { it.tag != "supercharge" }.forEach { cat ->
                                        val isSelected = selectedCategoryIds[cat.id] == true
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .then(
                                                    if (isSelected) Modifier.background(headerBg, CircleShape)
                                                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                )
                                                .clickable {
                                                    if (isSelected) {
                                                        selectedCategoryIds.remove(cat.id)
                                                        categoryAmounts.remove(cat.id)
                                                    } else {
                                                        selectedCategoryIds[cat.id] = true
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getCategoryIcon(cat.iconName),
                                                contentDescription = cat.name,
                                                modifier = Modifier.size(20.dp),
                                                tint = if (isSelected) Color.White
                                                       else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(9.dp))

                            // Buttons
                            OutlinedButton(onClick = { finish() }) {
                                Text(W.cancel)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val pa = parsedAmount ?: return@Button
                                    val catAmounts = if (isSingleCategory) {
                                        listOf(CategoryAmount(selectedIds.first(), pa))
                                    } else {
                                        selectedIds.mapNotNull { id ->
                                            val catAmt = categoryAmounts[id]?.toDoubleOrNull() ?: return@mapNotNull null
                                            if (catAmt > 0) CategoryAmount(id, catAmt) else null
                                        }
                                    }
                                    val deviceId = SyncIdGenerator.getOrCreateDeviceId(context)
                                    val transactions = TransactionRepository.load(context)
                                    val existingIds = transactions.map { it.id }.toSet()
                                    val txn = Transaction(
                                        id = generateTransactionId(existingIds),
                                        type = if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                                        date = LocalDate.now(),
                                        source = source.trim(),
                                        description = description.trim(),
                                        amount = pa,
                                        categoryAmounts = catAmounts,
                                        isUserCategorized = catAmounts.isNotEmpty(),
                                        deviceId = deviceId
                                    )
                                    runMatchingChain(txn, catAmounts)
                                },
                                enabled = canSave && !atDailyLimit,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = headerBg,
                                    disabledContainerColor = headerBg.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(W.save, color = if (canSave) Color.White else Color.White.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                // --- Styled dialog helpers (dark-theme hardcoded) ---
                val dialogHeaderColor = Color(0xFF1B5E20)
                val dialogHeaderTextColor = Color(0xFFE8F5E9)
                val dialogFooterColor = Color(0xFF1A3A1A)
                val dialogSectionLabelColor = Color(0xFF81C784)
                val duplicateHeaderColor = Color(0xFFB71C1C)
                val duplicateHeaderTextColor = Color.White
                val dialogShape = RoundedCornerShape(16.dp)

                // Duplicate match dialog (3 options: Keep Old, Keep New, Keep Both)
                if (duplicateMatch != null && pendingTxn != null) {
                    val dup = duplicateMatch!!
                    val txn = pendingTxn!!
                    Dialog(
                        onDismissRequest = {
                            duplicateMatch = null
                            pendingTxn = null
                        }
                    ) {
                        Surface(shape = dialogShape, color = MaterialTheme.colorScheme.surface) {
                            Column {
                                // Header
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(duplicateHeaderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(W.duplicateTitle, color = duplicateHeaderTextColor, style = MaterialTheme.typography.titleMedium)
                                }
                                // Body
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(W.duplicateExisting, color = dialogSectionLabelColor, style = MaterialTheme.typography.labelMedium)
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF424242))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(dup.source, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "$currencySymbol${"%.${maxDecimals}f".format(dup.amount)}  •  ${dup.date.format(dateFormatter)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.padding(top = 12.dp))
                                    Text(W.duplicateNew, color = dialogSectionLabelColor, style = MaterialTheme.typography.labelMedium)
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF424242))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(txn.source, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "$currencySymbol${"%.${maxDecimals}f".format(txn.amount)}  •  ${txn.date.format(dateFormatter)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                // Footer
                                HorizontalDivider(color = Color(0xFF424242))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(dialogFooterColor, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                ) {
                                    // Keep Old — discard new, finish
                                    Button(
                                        onClick = {
                                            duplicateMatch = null
                                            pendingTxn = null
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 5.dp)
                                    ) { Text(W.duplicateKeepOld.replace(" ", "\n"), color = Color.White, textAlign = TextAlign.Center) }
                                    // Keep New — delete old, continue to linking
                                    Button(
                                        onClick = {
                                            val existing = TransactionRepository.load(context).toMutableList()
                                            val idx = existing.indexOfFirst { it.id == dup.id }
                                            if (idx >= 0) {
                                                existing[idx] = existing[idx].copy(deleted = true)
                                                TransactionRepository.save(context, existing)
                                            }
                                            val t = pendingTxn!!
                                            val c = pendingCatAmounts
                                            duplicateMatch = null
                                            runLinkingChain(t, c)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 5.dp)
                                    ) { Text(W.duplicateKeepNew.replace(" ", "\n"), color = Color.White, textAlign = TextAlign.Center) }
                                    // Keep Both — continue to linking
                                    Button(
                                        onClick = {
                                            val t = pendingTxn!!
                                            val c = pendingCatAmounts
                                            duplicateMatch = null
                                            runLinkingChain(t, c)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 5.dp)
                                    ) { Text(W.duplicateKeepBoth.replace(" ", "\n"), color = Color.White, textAlign = TextAlign.Center) }
                                }
                            }
                        }
                    }
                }

                // Recurring expense match dialog
                if (recurringMatch != null && pendingTxn != null) {
                    val re = recurringMatch!!
                    Dialog(
                        onDismissRequest = {
                            recurringMatch = null
                            saveTransaction(context, pendingTxn!!)
                            prefs.edit()
                                .putString("widgetTxDate", today)
                                .putInt("widgetTxCount", widgetTxCount + 1)
                                .apply()
                            pendingTxn = null
                            finish()
                        }
                    ) {
                        Surface(shape = dialogShape, color = MaterialTheme.colorScheme.surface) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(dialogHeaderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(W.recurringTitle, color = dialogHeaderTextColor, style = MaterialTheme.typography.titleMedium)
                                }
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(W.recurringBody(re.source), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.padding(top = 4.dp))
                                    Text(
                                        "$currencySymbol${"%.${maxDecimals}f".format(re.amount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider(color = Color(0xFF424242))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(dialogFooterColor, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                ) {
                                    Button(
                                        onClick = {
                                            saveTransaction(context, pendingTxn!!)
                                            prefs.edit()
                                                .putString("widgetTxDate", today)
                                                .putInt("widgetTxCount", widgetTxCount + 1)
                                                .apply()
                                            recurringMatch = null
                                            pendingTxn = null
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3A))
                                    ) { Text(W.recurringNoLink, color = Color(0xFFCCCCCC)) }
                                    Button(
                                        onClick = {
                                            saveTransaction(context, pendingTxn!!.copy(
                                                linkedRecurringExpenseId = re.id,
                                                linkedRecurringExpenseAmount = re.amount
                                            ))
                                            prefs.edit()
                                                .putString("widgetTxDate", today)
                                                .putInt("widgetTxCount", widgetTxCount + 1)
                                                .apply()
                                            recurringMatch = null
                                            pendingTxn = null
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                                    ) { Text(W.recurringLink, color = Color.White) }
                                }
                            }
                        }
                    }
                }

                // Amortization match dialog
                if (amortizationMatch != null && pendingTxn != null) {
                    val am = amortizationMatch!!
                    Dialog(
                        onDismissRequest = {
                            amortizationMatch = null
                            saveTransaction(context, pendingTxn!!)
                            prefs.edit()
                                .putString("widgetTxDate", today)
                                .putInt("widgetTxCount", widgetTxCount + 1)
                                .apply()
                            pendingTxn = null
                            finish()
                        }
                    ) {
                        Surface(shape = dialogShape, color = MaterialTheme.colorScheme.surface) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(dialogHeaderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(W.amortizationTitle, color = dialogHeaderTextColor, style = MaterialTheme.typography.titleMedium)
                                }
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(W.amortizationBody(am.source), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.padding(top = 4.dp))
                                    Text(
                                        "$currencySymbol${"%.${maxDecimals}f".format(am.amount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider(color = Color(0xFF424242))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(dialogFooterColor, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                ) {
                                    Button(
                                        onClick = {
                                            saveTransaction(context, pendingTxn!!)
                                            prefs.edit()
                                                .putString("widgetTxDate", today)
                                                .putInt("widgetTxCount", widgetTxCount + 1)
                                                .apply()
                                            amortizationMatch = null
                                            pendingTxn = null
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3A))
                                    ) { Text(W.amortizationNoLink, color = Color(0xFFCCCCCC)) }
                                    Button(
                                        onClick = {
                                            saveTransaction(context, pendingTxn!!.copy(
                                                linkedAmortizationEntryId = am.id
                                            ))
                                            prefs.edit()
                                                .putString("widgetTxDate", today)
                                                .putInt("widgetTxCount", widgetTxCount + 1)
                                                .apply()
                                            amortizationMatch = null
                                            pendingTxn = null
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                                    ) { Text(W.amortizationLink, color = Color.White) }
                                }
                            }
                        }
                    }
                }

                // Budget income match dialog
                if (budgetIncomeMatch != null && pendingTxn != null) {
                    val inc = budgetIncomeMatch!!
                    Dialog(
                        onDismissRequest = {
                            budgetIncomeMatch = null
                            saveTransaction(context, pendingTxn!!)
                            prefs.edit()
                                .putString("widgetTxDate", today)
                                .putInt("widgetTxCount", widgetTxCount + 1)
                                .apply()
                            pendingTxn = null
                            finish()
                        }
                    ) {
                        Surface(shape = dialogShape, color = MaterialTheme.colorScheme.surface) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(dialogHeaderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(W.budgetIncomeTitle, color = dialogHeaderTextColor, style = MaterialTheme.typography.titleMedium)
                                }
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(W.budgetIncomeBody(inc.source), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.padding(top = 4.dp))
                                    Text(
                                        "$currencySymbol${"%.${maxDecimals}f".format(inc.amount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider(color = Color(0xFF424242))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(dialogFooterColor, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                ) {
                                    Button(
                                        onClick = {
                                            saveTransaction(context, pendingTxn!!)
                                            prefs.edit()
                                                .putString("widgetTxDate", today)
                                                .putInt("widgetTxCount", widgetTxCount + 1)
                                                .apply()
                                            budgetIncomeMatch = null
                                            pendingTxn = null
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3A))
                                    ) { Text(W.budgetIncomeNoLink, color = Color(0xFFCCCCCC)) }
                                    Button(
                                        onClick = {
                                            val recurringIncomeCatId = CategoryRepository.load(context)
                                                .active.find { it.tag == "recurring_income" }?.id
                                            val baseTxn = pendingTxn!!
                                            val linked = baseTxn.copy(
                                                isBudgetIncome = true,
                                                linkedIncomeSourceId = inc.id,
                                                linkedIncomeSourceAmount = inc.amount,
                                                categoryAmounts = if (recurringIncomeCatId != null)
                                                    listOf(CategoryAmount(recurringIncomeCatId, baseTxn.amount))
                                                else baseTxn.categoryAmounts,
                                                isUserCategorized = true
                                            )
                                            saveTransaction(context, linked)
                                            prefs.edit()
                                                .putString("widgetTxDate", today)
                                                .putInt("widgetTxCount", widgetTxCount + 1)
                                                .apply()
                                            budgetIncomeMatch = null
                                            pendingTxn = null
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                                    ) { Text(W.budgetIncomeLink, color = Color.White) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveTransaction(
        context: Context,
        txn: Transaction
    ) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        val transactions = TransactionRepository.load(context).toMutableList()
        if (transactions.none { it.id == txn.id }) {
            transactions.add(txn)
        }
        TransactionRepository.save(context, transactions)
        SyncWriteHelper.pushTransaction(txn)

        // Update available cash — mirror BudgetCalculator.recomputeAvailableCash logic
        val incomeMode = try {
            IncomeMode.valueOf(prefs.getString("incomeMode", null) ?: "FIXED")
        } catch (_: Exception) { IncomeMode.FIXED }
        val currentCash = prefs.getDoubleCompat("availableCash")
        val cashDelta = if (txn.excludeFromBudget) {
            0.0
        } else if (txn.type == TransactionType.EXPENSE) {
            when {
                txn.linkedAmortizationEntryId != null -> 0.0  // fully budget-accounted
                txn.amortizationAppliedAmount > 0.0 -> {
                    // Formerly linked to deleted amortization — only deduct unamortized remainder
                    -(BudgetCalculator.roundCents(txn.amount - txn.amortizationAppliedAmount).coerceAtLeast(0.0))
                }
                txn.linkedRecurringExpenseId != null && txn.linkedRecurringExpenseAmount > 0.0 ->
                    txn.linkedRecurringExpenseAmount - txn.amount  // delta from budgeted amount
                txn.linkedRecurringExpenseAmount > 0.0 ->
                    txn.linkedRecurringExpenseAmount - txn.amount  // formerly linked to deleted RE
                else -> -txn.amount
            }
        } else {
            // Income — behavior depends on income mode
            when {
                txn.linkedIncomeSourceId != null -> when (incomeMode) {
                    IncomeMode.FIXED -> 0.0  // already in budget, no cash effect
                    IncomeMode.ACTUAL -> {
                        // Delta between actual and budgeted
                        if (txn.linkedIncomeSourceAmount > 0.0)
                            txn.amount - txn.linkedIncomeSourceAmount
                        else txn.amount
                    }
                    IncomeMode.ACTUAL_ADJUST -> {
                        // Update the income source amount to match, then delta is 0
                        val srcId = txn.linkedIncomeSourceId!!
                        val sources = IncomeSourceRepository.load(context).toMutableList()
                        val idx = sources.indexOfFirst { it.id == srcId }
                        if (idx >= 0 && sources[idx].amount != txn.amount) {
                            sources[idx] = sources[idx].copy(
                                amount = txn.amount
                            )
                            IncomeSourceRepository.save(context, sources)
                        }
                        0.0  // source adjusted, no cash delta
                    }
                }
                txn.linkedIncomeSourceAmount > 0.0 ->
                    txn.amount - txn.linkedIncomeSourceAmount  // formerly linked to deleted income source
                txn.isBudgetIncome -> 0.0
                else -> txn.amount
            }
        }
        if (cashDelta != 0.0) {
            val newCash = BudgetCalculator.roundCents(currentCash + cashDelta)
            prefs.edit().putString("availableCash", newCash.toString()).apply()
        }

        // Mark sync dirty
        val syncPrefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
        syncPrefs.edit().putBoolean("syncDirty", true).apply()

        BudgetWidgetProvider.updateAllWidgets(context)
    }
}
