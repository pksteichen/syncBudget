package com.syncbudget.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.painterResource
import com.syncbudget.app.R
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionType
import com.syncbudget.app.data.getCategoryIcon
import com.syncbudget.app.sound.FlipSoundPlayer
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS
import com.syncbudget.app.ui.components.FlipDisplay
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class SpendingRange(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    ROLLING_7("7 Days"),
    THIS_MONTH("This Month"),
    ROLLING_30("30 Days"),
    THIS_YEAR("This Year"),
    ROLLING_365("365 Days")
}

private val PIE_COLORS_LIGHT = listOf(
    Color(0xFF4CAF50),
    Color(0xFF2196F3),
    Color(0xFFF44336),
    Color(0xFFFF9800),
    Color(0xFF9C27B0),
    Color(0xFF00BCD4),
    Color(0xFFFFEB3B),
    Color(0xFF795548),
    Color(0xFFE91E63),
    Color(0xFF607D8B),
    Color(0xFF8BC34A),
    Color(0xFF3F51B5)
)

// Low-luminance muted colors for dark mode
private val PIE_COLORS_DARK = listOf(
    Color(0xFF2E7D32),
    Color(0xFF1565C0),
    Color(0xFFC62828),
    Color(0xFFE65100),
    Color(0xFF6A1B9A),
    Color(0xFF00838F),
    Color(0xFFF9A825),
    Color(0xFF4E342E),
    Color(0xFFAD1457),
    Color(0xFF455A64),
    Color(0xFF558B2F),
    Color(0xFF283593)
)

private data class PieWedge(
    val categoryId: Int,
    val categoryName: String,
    val iconName: String,
    val amount: Double,
    val color: Color,
    val startAngle: Float,
    val sweepAngle: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    soundPlayer: FlipSoundPlayer,
    currencySymbol: String,
    digitCount: Int,
    showDecimals: Boolean,
    availableCash: Double = 0.0,
    budgetAmount: Double = 0.0,
    budgetStartDate: String? = null,
    budgetPeriodLabel: String = "period",
    savingsGoals: List<SavingsGoal> = emptyList(),
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList(),
    onSettingsClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onAddIncome: () -> Unit = {},
    onAddExpense: () -> Unit = {},
    onSupercharge: (Map<Int, Double>) -> Unit = {},
    weekStartDay: DayOfWeek = DayOfWeek.SUNDAY
) {
    val customColors = LocalSyncBudgetColors.current

    val decimalPlaces = if (showDecimals) (CURRENCY_DECIMALS[currencySymbol] ?: 2) else 0

    val isNegative = availableCash < 0
    val displayAmount = if (decimalPlaces > 0) {
        var divisor = 1
        repeat(decimalPlaces) { divisor *= 10 }
        (abs(availableCash) * divisor).roundToInt()
    } else {
        abs(availableCash).roundToInt()
    }

    // Auto-compute digit count from the whole part of the amount
    val wholeValue = if (decimalPlaces > 0) {
        var d = 1
        repeat(decimalPlaces) { d *= 10 }
        displayAmount / d
    } else displayAmount
    val autoDigitCount = maxOf(1, wholeValue.toString().length)

    val bottomLabel = if (budgetStartDate == null) {
        "Not configured"
    } else {
        val periodText = when {
            budgetAmount == 0.0 && !isNegative -> "Recalculate budget"
            else -> "$currencySymbol${"%.2f".format(budgetAmount)}/$budgetPeriodLabel"
        }
        periodText
    }

    val hasExtraSavings = availableCash > budgetAmount && budgetAmount > 0.0
    val hasEligibleGoals = savingsGoals.any { it.totalSavedSoFar < it.targetAmount }
    val showPulse = hasExtraSavings && hasEligibleGoals

    val infiniteTransition = rememberInfiniteTransition(label = "boltPulse")
    val animatedBoltColor by infiniteTransition.animateColor(
        initialValue = customColors.cardText.copy(alpha = 0.5f),
        targetValue = Color(0xFFFFEB3B),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boltPulseColor"
    )
    val pulseColor = if (showPulse) animatedBoltColor else customColors.cardText.copy(alpha = 0.5f)

    var showSuperchargeDialog by remember { mutableStateOf(false) }
    var selectedRange by remember { mutableStateOf(SpendingRange.ROLLING_7) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "SecureSync Daily Budget",
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = customColors.headerText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate("dashboard_help") }) {
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
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Solari board with bolt
            Box(contentAlignment = Alignment.Center) {
                FlipDisplay(
                    amount = displayAmount,
                    isNegative = isNegative,
                    currencySymbol = currencySymbol,
                    digitCount = autoDigitCount,
                    decimalPlaces = decimalPlaces,
                    soundPlayer = soundPlayer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                    bottomLabel = bottomLabel
                )
                IconButton(
                    onClick = { showSuperchargeDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Savings Supercharge",
                        tint = pulseColor,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            // Chart title
            Text(
                text = "Spending",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )

            // Chart area
            SpendingPieChart(
                transactions = transactions,
                categories = categories,
                selectedRange = selectedRange,
                onRangeChange = { selectedRange = it },
                currencySymbol = currencySymbol,
                weekStartDay = weekStartDay,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            // +/- buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddIncome,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.displayBackground
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Income",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Button(
                    onClick = onAddExpense,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.displayBackground
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Add Expense",
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Nav icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onNavigate("transactions") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.List, "Transactions", tint = customColors.headerBackground, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { onNavigate("future_expenditures") }, modifier = Modifier.size(48.dp)) {
                    Icon(painter = painterResource(id = R.drawable.ic_coins), contentDescription = "Savings Goals", tint = customColors.headerBackground, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { onNavigate("amortization") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Schedule, "Amortization", tint = customColors.headerBackground, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { onNavigate("recurring_expenses") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Sync, "Recurring Expenses", tint = customColors.headerBackground, modifier = Modifier.size(32.dp))
                }
            }
        }
    }

    if (showSuperchargeDialog) {
        SavingsSuperchargeDialog(
            savingsGoals = savingsGoals,
            currencySymbol = currencySymbol,
            availableExtra = availableCash,
            onDismiss = { showSuperchargeDialog = false },
            onApply = { allocations ->
                onSupercharge(allocations)
                showSuperchargeDialog = false
            }
        )
    }
}

@Composable
private fun SpendingPieChart(
    transactions: List<Transaction>,
    categories: List<Category>,
    selectedRange: SpendingRange,
    onRangeChange: (SpendingRange) -> Unit,
    currencySymbol: String,
    weekStartDay: DayOfWeek = DayOfWeek.SUNDAY,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val otherCatId = remember(categories) { categories.find { it.name == "Other" }?.id ?: -1 }

    val today = LocalDate.now()
    val startDate = when (selectedRange) {
        SpendingRange.TODAY -> today
        SpendingRange.THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(weekStartDay))
        SpendingRange.ROLLING_7 -> today.minusDays(6)
        SpendingRange.THIS_MONTH -> today.withDayOfMonth(1)
        SpendingRange.ROLLING_30 -> today.minusDays(29)
        SpendingRange.THIS_YEAR -> today.withDayOfYear(1)
        SpendingRange.ROLLING_365 -> today.minusDays(364)
    }

    val filteredExpenses = transactions.filter {
        it.type == TransactionType.EXPENSE &&
            !it.date.isBefore(startDate) &&
            !it.date.isAfter(today)
    }

    // Aggregate spending by category
    val spending = mutableMapOf<Int, Double>()
    for (txn in filteredExpenses) {
        if (txn.categoryAmounts.isEmpty()) {
            spending[otherCatId] = (spending[otherCatId] ?: 0.0) + txn.amount
        } else {
            for (ca in txn.categoryAmounts) {
                val catId = if (categoryMap.containsKey(ca.categoryId)) ca.categoryId else otherCatId
                spending[catId] = (spending[catId] ?: 0.0) + ca.amount
            }
        }
    }

    val totalSpending = spending.values.sum()
    val sortedEntries = spending.entries
        .filter { it.value > 0 }
        .sortedByDescending { it.value }

    // Select color palette based on theme
    val isDark = isSystemInDarkTheme()
    val chartColors = if (isDark) PIE_COLORS_DARK else PIE_COLORS_LIGHT

    // Build wedge data
    val wedges = mutableListOf<PieWedge>()
    var currentAngle = -90f
    sortedEntries.forEachIndexed { index, (catId, amount) ->
        val sweep = if (totalSpending > 0) (amount / totalSpending * 360f).toFloat() else 0f
        val cat = categoryMap[catId]
        wedges.add(
            PieWedge(
                categoryId = catId,
                categoryName = cat?.name ?: "Other",
                iconName = cat?.iconName ?: "Category",
                amount = amount,
                color = chartColors[index % chartColors.size],
                startAngle = currentAngle,
                sweepAngle = sweep
            )
        )
        currentAngle += sweep
    }

    var showBarChart by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (wedges.isEmpty()) {
            Text(
                text = "No spending data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (showBarChart) {
            // Bar chart view
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 28.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val maxAmount = wedges.maxOfOrNull { it.amount } ?: 0.0
                for (w in wedges) {
                    val barFraction = if (maxAmount > 0) (w.amount / maxAmount).toFloat().coerceIn(0.01f, 1f) else 0.01f
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Bar area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .fillMaxHeight(barFraction)
                                    .background(w.color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                        }
                        // Category icon at bottom
                        Icon(
                            imageVector = getCategoryIcon(w.iconName),
                            contentDescription = w.categoryName,
                            tint = w.color,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    Toast
                                        .makeText(
                                            context,
                                            "${w.categoryName}: $currencySymbol${"%.2f".format(w.amount)}",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                }
                        )
                    }
                }
            }
        } else {
            // Pie chart view
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val chartSize = min(maxWidth, maxHeight)
                val pieRadiusDp = chartSize / 2 - 30.dp
                val iconRadiusDp = pieRadiusDp + 18.dp
                val iconSize = 20.dp

                // Draw pie
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cX = size.width / 2
                    val cY = size.height / 2
                    val radius = pieRadiusDp.toPx()
                    val topLeft = Offset(cX - radius, cY - radius)
                    val arcSize = Size(radius * 2, radius * 2)

                    for (w in wedges) {
                        drawArc(
                            color = w.color,
                            startAngle = w.startAngle,
                            sweepAngle = w.sweepAngle,
                            useCenter = true,
                            topLeft = topLeft,
                            size = arcSize
                        )
                    }
                }

                // Position category icons around the pie
                for (w in wedges) {
                    val pct = if (totalSpending > 0) w.amount / totalSpending else 0.0
                    if (pct < 0.04) continue

                    val midAngle = w.startAngle + w.sweepAngle / 2f
                    val rad = Math.toRadians(midAngle.toDouble())
                    val iconX = iconRadiusDp * cos(rad).toFloat()
                    val iconY = iconRadiusDp * sin(rad).toFloat()

                    Icon(
                        imageVector = getCategoryIcon(w.iconName),
                        contentDescription = w.categoryName,
                        tint = w.color,
                        modifier = Modifier
                            .offset(x = iconX, y = iconY)
                            .size(iconSize)
                            .clickable {
                                Toast
                                    .makeText(
                                        context,
                                        "${w.categoryName}: $currencySymbol${"%.2f".format(w.amount)}",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                    )
                }
            }
        }

        // Time range toggle in top-left
        Text(
            text = selectedRange.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                    RoundedCornerShape(6.dp)
                )
                .clickable {
                    val values = SpendingRange.entries.toTypedArray()
                    val next = (selectedRange.ordinal + 1) % values.size
                    onRangeChange(values[next])
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // Chart type toggle in top-right
        Icon(
            imageVector = if (showBarChart) Icons.Filled.PieChart else Icons.Filled.BarChart,
            contentDescription = if (showBarChart) "Switch to pie chart" else "Switch to bar chart",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                    RoundedCornerShape(6.dp)
                )
                .clickable { showBarChart = !showBarChart }
                .padding(4.dp)
                .size(18.dp)
        )
    }
}

@Composable
private fun SavingsSuperchargeDialog(
    savingsGoals: List<SavingsGoal>,
    currencySymbol: String,
    availableExtra: Double,
    onDismiss: () -> Unit,
    onApply: (Map<Int, Double>) -> Unit
) {
    val eligibleGoals = savingsGoals.filter { it.totalSavedSoFar < it.targetAmount }
    val amounts = remember { mutableStateMapOf<Int, String>() }

    val totalAllocated = amounts.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val isOverBudget = totalAllocated > availableExtra
    val hasAnyAmount = totalAllocated > 0.0
    val anyExceedsRemaining = eligibleGoals.any { goal ->
        val entered = (amounts[goal.id] ?: "").toDoubleOrNull() ?: 0.0
        entered > goal.targetAmount - goal.totalSavedSoFar
    }

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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = Color(0xFFFFEB3B),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Savings Supercharge!")
            }
        },
        text = {
            Column {
                Text(
                    text = "Move extra cash into your savings goals. Available: $currencySymbol${"%.2f".format(availableExtra)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isOverBudget) {
                    Text(
                        text = "Total ($currencySymbol${"%.2f".format(totalAllocated)}) exceeds available cash",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF44336)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (eligibleGoals.isEmpty()) {
                    Text(
                        text = "No savings goals to supercharge.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(eligibleGoals) { goal ->
                            val remaining = goal.targetAmount - goal.totalSavedSoFar
                            val progress = if (goal.targetAmount > 0) {
                                (goal.totalSavedSoFar / goal.targetAmount).toFloat().coerceIn(0f, 1f)
                            } else 0f
                            val contentAlpha = if (goal.isPaused) 0.5f else 1f

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = goal.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (goal.isPaused) {
                                        Text(
                                            text = "Paused",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                                Text(
                                    text = if (goal.targetDate != null) {
                                        "$currencySymbol${"%.2f".format(goal.targetAmount)} by ${goal.targetDate}"
                                    } else {
                                        "Target: $currencySymbol${"%.2f".format(goal.targetAmount)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f * contentAlpha)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
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
                                    text = "Saved: $currencySymbol${"%.2f".format(goal.totalSavedSoFar)} of $currencySymbol${"%.2f".format(goal.targetAmount)} (remaining: $currencySymbol${"%.2f".format(remaining)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4CAF50).copy(alpha = contentAlpha)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val enteredAmount = (amounts[goal.id] ?: "").toDoubleOrNull() ?: 0.0
                                val exceedsGoal = enteredAmount > remaining
                                OutlinedTextField(
                                    value = amounts[goal.id] ?: "",
                                    onValueChange = { newVal ->
                                        if (newVal.isEmpty() || newVal.toDoubleOrNull() != null || newVal == ".") {
                                            amounts[goal.id] = newVal
                                        }
                                    },
                                    label = { Text("Amount to add") },
                                    singleLine = true,
                                    isError = exceedsGoal,
                                    supportingText = if (exceedsGoal) ({
                                        Text("Max: $currencySymbol${"%.2f".format(remaining)}", color = Color(0xFFF44336))
                                    }) else null,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = textFieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val allocations = mutableMapOf<Int, Double>()
                    for ((id, text) in amounts) {
                        val value = text.toDoubleOrNull()
                        if (value != null && value > 0.0) {
                            allocations[id] = value
                        }
                    }
                    if (allocations.isNotEmpty()) {
                        onApply(allocations)
                    }
                },
                enabled = hasAnyAmount && !isOverBudget && !anyExceedsRemaining
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
