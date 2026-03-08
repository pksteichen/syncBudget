package com.syncbudget.app.widget

import android.content.Context
import android.os.Bundle
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
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.CategoryRepository
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionRepository
import com.syncbudget.app.data.TransactionType
import com.syncbudget.app.data.generateTransactionId
import com.syncbudget.app.data.getCategoryIcon
import com.syncbudget.app.data.sync.LamportClock
import com.syncbudget.app.data.sync.SyncIdGenerator
import com.syncbudget.app.data.sync.active
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS
import com.syncbudget.app.ui.strings.AppStrings
import com.syncbudget.app.ui.strings.EnglishStrings
import com.syncbudget.app.ui.strings.SpanishStrings
import java.time.LocalDate

class WidgetTransactionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

                val categories = remember { CategoryRepository.load(context).active.filter { it.widgetVisible } }
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
                val canSave = parsedAmount != null && parsedAmount > 0 && source.isNotBlank() &&
                    selectedIds.isNotEmpty() && (isSingleCategory || abs(remaining) < 0.005)

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
                                // Source / Merchant
                                OutlinedTextField(
                                    value = source,
                                    onValueChange = { source = it.take(50) },
                                    label = { Text(if (isExpense) W.merchantService else W.source) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

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
                                    categories.forEach { cat ->
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
                                    saveTransaction(
                                        context = context,
                                        amount = pa,
                                        source = source.trim(),
                                        description = description.trim(),
                                        isExpense = isExpense,
                                        catAmounts = catAmounts
                                    )
                                    // Track widget transaction count for non-paid daily limit
                                    prefs.edit()
                                        .putString("widgetTxDate", today)
                                        .putInt("widgetTxCount", widgetTxCount + 1)
                                        .apply()
                                    finish()
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
            }
        }
    }

    private fun saveTransaction(
        context: Context,
        amount: Double,
        source: String,
        description: String,
        isExpense: Boolean,
        catAmounts: List<CategoryAmount> = emptyList()
    ) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lamportClock = LamportClock(context)
        val deviceId = SyncIdGenerator.getOrCreateDeviceId(context)
        val clock = lamportClock.tick()

        val transactions = TransactionRepository.load(context).toMutableList()
        val existingIds = transactions.map { it.id }.toSet()

        val txn = Transaction(
            id = generateTransactionId(existingIds),
            type = if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
            date = LocalDate.now(),
            source = source,
            description = description,
            amount = amount,
            categoryAmounts = catAmounts,
            isUserCategorized = catAmounts.isNotEmpty(),
            deviceId = deviceId,
            source_clock = clock,
            description_clock = clock,
            amount_clock = clock,
            date_clock = clock,
            type_clock = clock,
            categoryAmounts_clock = clock,
            isUserCategorized_clock = clock,
            isBudgetIncome_clock = clock,
            linkedRecurringExpenseId_clock = clock,
            linkedAmortizationEntryId_clock = clock,
            linkedIncomeSourceId_clock = clock,
            amortizationAppliedAmount_clock = clock,
            linkedRecurringExpenseAmount_clock = clock,
            linkedIncomeSourceAmount_clock = clock,
            deviceId_clock = clock
        )

        transactions.add(txn)
        TransactionRepository.save(context, transactions)

        // Update available cash
        val currentCash = prefs.getString("availableCash", "0.0")?.toDoubleOrNull() ?: 0.0
        val newCash = if (isExpense) currentCash - amount else currentCash + amount
        prefs.edit().putString("availableCash", newCash.toString()).apply()

        // Mark sync dirty
        val syncPrefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
        syncPrefs.edit().putBoolean("syncDirty", true).apply()

        BudgetWidgetProvider.updateAllWidgets(context)
    }
}
