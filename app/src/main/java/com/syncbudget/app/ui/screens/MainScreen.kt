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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.SuperchargeMode
import com.syncbudget.app.data.TransactionType
import com.syncbudget.app.data.calculatePerPeriodDeduction
import com.syncbudget.app.data.getCategoryIcon
import com.syncbudget.app.sound.FlipSoundPlayer
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS
import com.syncbudget.app.ui.components.FlipDisplay
import com.syncbudget.app.ui.components.formatCurrency
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// SuperchargeMode is in com.syncbudget.app.data.SuperchargeMode

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
    Color(0xFF1B5E20),
    Color(0xFF0D47A1),
    Color(0xFF7F1D1D),
    Color(0xFF8B3A00),
    Color(0xFF4A148C),
    Color(0xFF004D40),
    Color(0xFF8C6D00),
    Color(0xFF3E2723),
    Color(0xFF6A0035),
    Color(0xFF263238),
    Color(0xFF33691E),
    Color(0xFF1A237E)
)

// Pastel palette for light mode
private val PIE_COLORS_PASTEL_LIGHT = listOf(
    Color(0xFFA5D6A7), // green
    Color(0xFF90CAF9), // blue
    Color(0xFFEF9A9A), // red
    Color(0xFFFFCC80), // orange
    Color(0xFFCE93D8), // purple
    Color(0xFF80DEEA), // teal
    Color(0xFFFFF59D), // yellow
    Color(0xFFBCAAA4), // brown
    Color(0xFFF48FB1), // pink
    Color(0xFFB0BEC5), // gray
    Color(0xFFC5E1A5), // lime
    Color(0xFF9FA8DA)  // indigo
)

// Pastel palette for dark mode
private val PIE_COLORS_PASTEL_DARK = listOf(
    Color(0xFF2E5E30), // green
    Color(0xFF1E4976), // blue
    Color(0xFF6D3434), // red
    Color(0xFF7A5020), // orange
    Color(0xFF5A3070), // purple
    Color(0xFF1A5055), // teal
    Color(0xFF6B5E1A), // yellow
    Color(0xFF4A3530), // brown
    Color(0xFF6B2845), // pink
    Color(0xFF37474F), // gray
    Color(0xFF3A5420), // lime
    Color(0xFF2A3570)  // indigo
)

// Sunset palette for light mode
private val PIE_COLORS_SUNSET_LIGHT = listOf(
    Color(0xFF4D1D46), // plum
    Color(0xFFDC7049), // burnt orange
    Color(0xFFEBB865), // golden yellow
    Color(0xFF35506E), // steel blue
    Color(0xFF8F5050), // dusty rose
    Color(0xFF563060), // purple
    Color(0xFF313967), // navy
    Color(0xFFC25D5D), // coral
    Color(0xFFD4956A), // peach
    Color(0xFF2D6B6B), // teal
    Color(0xFF7A5A3A), // sienna
    Color(0xFF8B7BA8)  // lavender
)

// Sunset palette for dark mode
private val PIE_COLORS_SUNSET_DARK = listOf(
    Color(0xFF2E1129), // plum
    Color(0xFF8A4530), // burnt orange
    Color(0xFF8A6D2E), // golden yellow
    Color(0xFF1E3045), // steel blue
    Color(0xFF5A3232), // dusty rose
    Color(0xFF331C39), // purple
    Color(0xFF1C2140), // navy
    Color(0xFF7A3A3A), // coral
    Color(0xFF7A5540), // peach
    Color(0xFF1A4040), // teal
    Color(0xFF4A3622), // sienna
    Color(0xFF524968)  // lavender
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
    onSupercharge: (Map<Int, Double>, Map<Int, SuperchargeMode>) -> Unit = { _, _ -> },
    weekStartDay: DayOfWeek = DayOfWeek.SUNDAY,
    chartPalette: String = "Bright",
    dateFormatPattern: String = "yyyy-MM-dd",
    budgetPeriod: BudgetPeriod = BudgetPeriod.DAILY,
    syncStatus: String = "off",
    staleDays: Int = 0
) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

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
        S.dashboard.notConfigured
    } else {
        val periodText = when {
            budgetAmount == 0.0 && !isNegative -> S.budgetConfig.recalculate
            else -> "${formatCurrency(budgetAmount, currencySymbol)}/$budgetPeriodLabel"
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
    var showBarChart by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.dashboard.appTitle,
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
                            contentDescription = S.dashboard.settings,
                            tint = customColors.headerText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate("dashboard_help") }) {
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
                        contentDescription = S.dashboard.supercharge,
                        tint = pulseColor,
                        modifier = Modifier.size(44.dp)
                    )
                }
                if (syncStatus != "off") {
                    val syncColor = when (syncStatus) {
                        "synced" -> Color(0xFF4CAF50)
                        "syncing" -> Color(0xFFFFEB3B)
                        "stale" -> Color(0xFFFF9800)
                        "error" -> Color(0xFFF44336)
                        else -> Color(0xFF9E9E9E)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp, bottom = 20.dp)
                            .size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = S.sync.title,
                            tint = syncColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Canvas(modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(8.dp)) {
                            drawCircle(color = syncColor)
                        }
                    }
                }
            }

            // Stale device warning banner
            if (staleDays >= 60) {
                val (staleBannerColor, staleBannerText) = when {
                    staleDays >= 90 -> Color(0xFFB71C1C) to S.sync.staleBlocked
                    staleDays >= 85 -> Color(0xFFF44336) to S.sync.staleWarning85
                    staleDays >= 75 -> Color(0xFFFF5722) to S.sync.staleWarning75
                    else -> Color(0xFFFF9800) to S.sync.staleWarning60
                }
                Surface(
                    color = staleBannerColor.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = staleBannerText,
                        style = MaterialTheme.typography.bodySmall,
                        color = staleBannerColor,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Chart title bar
            val isDarkTheme = isSystemInDarkTheme()
            val chartBarBg = customColors.headerBackground
            val chartBarFg = customColors.headerText
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(chartBarBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Range selector button
                Text(
                    text = selectedRange.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = chartBarFg,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(
                            chartBarFg.copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            val values = SpendingRange.entries.toTypedArray()
                            val next = (selectedRange.ordinal + 1) % values.size
                            selectedRange = values[next]
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                // Centered title
                Text(
                    text = S.dashboard.spending,
                    style = MaterialTheme.typography.titleSmall,
                    color = chartBarFg,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                // Chart type toggle icon
                Icon(
                    imageVector = if (showBarChart) Icons.Filled.PieChart else Icons.Filled.BarChart,
                    contentDescription = if (showBarChart) "Switch to pie chart" else "Switch to bar chart",
                    tint = chartBarFg,
                    modifier = Modifier
                        .background(
                            chartBarFg.copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { showBarChart = !showBarChart }
                        .padding(4.dp)
                        .size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Chart area
            SpendingPieChart(
                transactions = transactions,
                categories = categories,
                selectedRange = selectedRange,
                onRangeChange = { selectedRange = it },
                currencySymbol = currencySymbol,
                weekStartDay = weekStartDay,
                chartPalette = chartPalette,
                showBarChart = showBarChart,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // +/- buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(customColors.displayBackground)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = S.dashboard.addIncome,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onAddIncome)
                )
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = S.dashboard.addExpense,
                    tint = Color(0xFFF44336),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onAddExpense)
                )
            }

            // Nav icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onNavigate("transactions") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.List, S.dashboard.transactions, tint = customColors.headerBackground, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { onNavigate("future_expenditures") }, modifier = Modifier.size(48.dp)) {
                    Icon(painter = painterResource(id = R.drawable.ic_coins), contentDescription = S.dashboard.savingsGoals, tint = customColors.headerBackground, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { onNavigate("amortization") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Schedule, S.dashboard.amortization, tint = customColors.headerBackground, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { onNavigate("recurring_expenses") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Sync, S.dashboard.recurringExpenses, tint = customColors.headerBackground, modifier = Modifier.size(32.dp))
                }
            }
        }
    }

    if (showSuperchargeDialog) {
        SavingsSuperchargeDialog(
            savingsGoals = savingsGoals,
            currencySymbol = currencySymbol,
            availableExtra = availableCash,
            budgetPeriod = budgetPeriod,
            dateFormatPattern = dateFormatPattern,
            budgetPeriodLabel = budgetPeriodLabel,
            onDismiss = { showSuperchargeDialog = false },
            onApply = { allocations, selectedModes ->
                onSupercharge(allocations, selectedModes)
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
    chartPalette: String = "Bright",
    showBarChart: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val S = LocalStrings.current
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val otherCatId = remember(categories) { categories.find { it.tag == "other" }?.id ?: -1 }

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

    // Select color palette based on theme and user preference
    val isDark = isSystemInDarkTheme()
    val chartColors = when (chartPalette) {
        "Pastel" -> if (isDark) PIE_COLORS_PASTEL_DARK else PIE_COLORS_PASTEL_LIGHT
        "Sunset" -> if (isDark) PIE_COLORS_SUNSET_DARK else PIE_COLORS_SUNSET_LIGHT
        else -> if (isDark) PIE_COLORS_DARK else PIE_COLORS_LIGHT
    }

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

    Box(modifier = modifier) {
        if (wedges.isEmpty()) {
            Text(
                text = S.dashboard.noDataAvailable,
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
                                            "${w.categoryName}: ${formatCurrency(w.amount, currencySymbol)}",
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
                                        "${w.categoryName}: ${formatCurrency(w.amount, currencySymbol)}",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                    )
                }
            }
        }

    }
}

@Composable
private fun SavingsSuperchargeDialog(
    savingsGoals: List<SavingsGoal>,
    currencySymbol: String,
    availableExtra: Double,
    budgetPeriod: BudgetPeriod = BudgetPeriod.DAILY,
    dateFormatPattern: String = "yyyy-MM-dd",
    budgetPeriodLabel: String = "period",
    onDismiss: () -> Unit,
    onApply: (Map<Int, Double>, Map<Int, SuperchargeMode>) -> Unit
) {
    val maxDecimalPlaces = CURRENCY_DECIMALS[currencySymbol] ?: 2
    val S = LocalStrings.current
    val dateFormatter = remember(dateFormatPattern) { DateTimeFormatter.ofPattern(dateFormatPattern) }
    val eligibleGoals = savingsGoals.filter { it.totalSavedSoFar < it.targetAmount }
    val amounts = remember { mutableStateMapOf<Int, String>() }
    val modes = remember { mutableStateMapOf<Int, SuperchargeMode>() }

    // Initialize default modes
    eligibleGoals.forEach { goal ->
        if (goal.id !in modes) {
            modes[goal.id] = if (goal.targetDate != null)
                SuperchargeMode.REDUCE_CONTRIBUTIONS
            else SuperchargeMode.ACHIEVE_SOONER
        }
    }

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

    // Green pulse animation for preview values
    val previewTransition = rememberInfiniteTransition(label = "previewPulse")
    val previewPulseColor by previewTransition.animateColor(
        initialValue = Color(0xFF4CAF50),
        targetValue = Color(0xFFA5D6A7),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "previewPulseColor"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = Color(0xFFFFEB3B),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(S.dashboard.superchargeTitle, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    item {
                        Text(
                            text = S.dashboard.superchargeRemaining(formatCurrency(availableExtra, currencySymbol)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isOverBudget) {
                            Text(
                                text = "Total (${formatCurrency(totalAllocated, currencySymbol)}) exceeds available cash",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFF44336)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (eligibleGoals.isEmpty()) {
                        item {
                            Text(
                                text = S.dashboard.noDataAvailable,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        items(eligibleGoals) { goal ->
                            val remaining = goal.targetAmount - goal.totalSavedSoFar
                            val progress = if (goal.targetAmount > 0) {
                                (goal.totalSavedSoFar / goal.targetAmount).toFloat().coerceIn(0f, 1f)
                            } else 0f
                            val contentAlpha = if (goal.isPaused) 0.5f else 1f
                            val mode = modes[goal.id] ?: SuperchargeMode.ACHIEVE_SOONER

                            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = goal.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (goal.isPaused) {
                                        Text(
                                            text = S.futureExpenditures.paused,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                                Text(
                                    text = if (goal.targetDate != null) {
                                        S.futureExpenditures.targetAmountBy(
                                            formatCurrency(goal.targetAmount, currencySymbol),
                                            goal.targetDate.format(dateFormatter)
                                        )
                                    } else {
                                        S.futureExpenditures.targetLabel(
                                            formatCurrency(goal.targetAmount, currencySymbol)
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f * contentAlpha)
                                )
                                // Show current payoff date for fixed-contribution goals
                                if (goal.targetDate == null && goal.contributionPerPeriod > 0 && remaining > 0) {
                                    val periodsToPayoff = ceil(remaining / goal.contributionPerPeriod).toLong()
                                    val today = LocalDate.now()
                                    val payoffDate = when (budgetPeriod) {
                                        BudgetPeriod.DAILY -> today.plusDays(periodsToPayoff)
                                        BudgetPeriod.WEEKLY -> today.plusWeeks(periodsToPayoff)
                                        BudgetPeriod.MONTHLY -> today.plusMonths(periodsToPayoff)
                                    }
                                    Text(
                                        text = S.futureExpenditures.payoffDate(payoffDate.format(dateFormatter)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f * contentAlpha)
                                    )
                                }
                                // Current per-period contribution
                                val currentContribution = calculatePerPeriodDeduction(goal, budgetPeriod)
                                Text(
                                    text = S.futureExpenditures.contributionLabel(
                                        formatCurrency(currentContribution, currencySymbol),
                                        budgetPeriodLabel
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f * contentAlpha)
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
                                    text = S.futureExpenditures.savedOf(
                                        formatCurrency(goal.totalSavedSoFar, currencySymbol),
                                        formatCurrency(goal.targetAmount, currencySymbol)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4CAF50).copy(alpha = contentAlpha)
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Mode toggle
                                Text(
                                    text = S.dashboard.superchargeExtraShouldLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (mode == SuperchargeMode.REDUCE_CONTRIBUTIONS)
                                        S.dashboard.superchargeReduceContributions
                                    else S.dashboard.superchargeAchieveSooner,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            modes[goal.id] = if (mode == SuperchargeMode.REDUCE_CONTRIBUTIONS)
                                                SuperchargeMode.ACHIEVE_SOONER
                                            else SuperchargeMode.REDUCE_CONTRIBUTIONS
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                                if (goal.targetDate != null && mode == SuperchargeMode.ACHIEVE_SOONER) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = S.dashboard.superchargeTargetDateNote,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFF9A825)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                val enteredAmount = (amounts[goal.id] ?: "").toDoubleOrNull() ?: 0.0
                                val exceedsGoal = enteredAmount > remaining

                                // Exceeds-goal warning (above text field so keyboard doesn't hide it)
                                if (exceedsGoal) {
                                    Text(
                                        text = S.transactions.maxAmount2(formatCurrency(remaining, currencySymbol)),
                                        color = Color(0xFFF44336),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                // Live preview (above text field so it stays visible when keyboard is open)
                                if (enteredAmount > 0 && !exceedsGoal) {
                                    val newRemaining = remaining - enteredAmount
                                    if (goal.targetDate != null && mode == SuperchargeMode.REDUCE_CONTRIBUTIONS) {
                                        // Target-date goal, Reduce mode: show new lower contribution
                                        val today = LocalDate.now()
                                        val newDeduction = if (!today.isBefore(goal.targetDate)) 0.0
                                        else {
                                            val periods = when (budgetPeriod) {
                                                BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(today, goal.targetDate)
                                                BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(today, goal.targetDate)
                                                BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(today, goal.targetDate)
                                            }
                                            if (periods <= 0) 0.0 else newRemaining / periods.toDouble()
                                        }
                                        Text(
                                            text = S.dashboard.superchargeNewContribution(
                                                formatCurrency(newDeduction, currencySymbol),
                                                budgetPeriodLabel
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = previewPulseColor
                                        )
                                    } else if (goal.targetDate != null && mode == SuperchargeMode.ACHIEVE_SOONER) {
                                        // Target-date goal, Achieve Sooner mode: show new earlier target date
                                        val today = LocalDate.now()
                                        val currentContribution = calculatePerPeriodDeduction(goal, budgetPeriod)
                                        if (currentContribution > 0 && newRemaining > 0) {
                                            val periodsNeeded = ceil(newRemaining / currentContribution).toLong()
                                            val newTargetDate = when (budgetPeriod) {
                                                BudgetPeriod.DAILY -> today.plusDays(periodsNeeded)
                                                BudgetPeriod.WEEKLY -> today.plusWeeks(periodsNeeded)
                                                BudgetPeriod.MONTHLY -> today.plusMonths(periodsNeeded)
                                            }
                                            Text(
                                                text = S.dashboard.superchargeNewPayoff(newTargetDate.format(dateFormatter)),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = previewPulseColor
                                            )
                                        } else if (newRemaining <= 0) {
                                            Text(
                                                text = S.futureExpenditures.goalReached,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = previewPulseColor
                                            )
                                        }
                                    } else if (mode == SuperchargeMode.REDUCE_CONTRIBUTIONS) {
                                        // Fixed-contribution, Reduce mode: show new lower per-period amount
                                        if (goal.contributionPerPeriod > 0 && remaining > 0) {
                                            val currentPeriodsRemaining = ceil(remaining / goal.contributionPerPeriod).toLong()
                                            val newDeduction = if (currentPeriodsRemaining > 0) newRemaining / currentPeriodsRemaining.toDouble() else 0.0
                                            Text(
                                                text = S.dashboard.superchargeNewContribution(
                                                    formatCurrency(newDeduction, currencySymbol),
                                                    budgetPeriodLabel
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = previewPulseColor
                                            )
                                        }
                                    } else {
                                        // Fixed-contribution, Achieve Sooner mode: show new payoff date
                                        val today = LocalDate.now()
                                        if (goal.contributionPerPeriod > 0 && newRemaining > 0) {
                                            val periodsRemaining = ceil(newRemaining / goal.contributionPerPeriod).toLong()
                                            val payoffDate = when (budgetPeriod) {
                                                BudgetPeriod.DAILY -> today.plusDays(periodsRemaining)
                                                BudgetPeriod.WEEKLY -> today.plusWeeks(periodsRemaining)
                                                BudgetPeriod.MONTHLY -> today.plusMonths(periodsRemaining)
                                            }
                                            Text(
                                                text = S.dashboard.superchargeNewPayoff(payoffDate.format(dateFormatter)),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = previewPulseColor
                                            )
                                        } else if (newRemaining <= 0) {
                                            Text(
                                                text = S.futureExpenditures.goalReached,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = previewPulseColor
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                OutlinedTextField(
                                    value = amounts[goal.id] ?: "",
                                    onValueChange = { newVal ->
                                        if (newVal.isEmpty() || newVal == "." || newVal.toDoubleOrNull() != null) {
                                            val dotIndex = newVal.indexOf('.')
                                            val decimals = if (dotIndex >= 0) newVal.length - dotIndex - 1 else 0
                                            if (maxDecimalPlaces == 0 && dotIndex >= 0) {
                                                // No decimals allowed
                                            } else if (decimals <= maxDecimalPlaces) {
                                                amounts[goal.id] = newVal
                                            }
                                        }
                                    },
                                    label = { Text(S.dashboard.superchargeAllocate) },
                                    singleLine = true,
                                    isError = exceedsGoal,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = if (maxDecimalPlaces > 0) KeyboardType.Decimal else KeyboardType.Number
                                    ),
                                    colors = textFieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
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
                            val allocations = mutableMapOf<Int, Double>()
                            for ((id, text) in amounts) {
                                val value = text.toDoubleOrNull()
                                if (value != null && value > 0.0) {
                                    allocations[id] = value
                                }
                            }
                            if (allocations.isNotEmpty()) {
                                onApply(allocations, modes.toMap())
                            }
                        },
                        enabled = hasAnyAmount && !isOverBudget && !anyExceedsRemaining
                    ) {
                        Text(S.common.ok)
                    }
                }
            }
        }
    }
}
