package com.techadvantage.budgetrak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techadvantage.budgetrak.data.BudgetCalculator
import com.techadvantage.budgetrak.data.IncomeSource
import com.techadvantage.budgetrak.data.RecurringExpense
import com.techadvantage.budgetrak.ui.components.formatCurrency
import com.techadvantage.budgetrak.ui.strings.LocalStrings
import com.techadvantage.budgetrak.ui.theme.AdAwareAlertDialog
import com.techadvantage.budgetrak.ui.theme.DialogSecondaryButton
import com.techadvantage.budgetrak.ui.theme.LocalSyncBudgetColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong

private data class CalendarEvent(val source: String, val amount: Double, val isIncome: Boolean)

private fun formatCurrencyRounded(amount: Double, currencySymbol: String): String {
    val rounded = amount.roundToLong().toString()
    return if (currencySymbol in com.techadvantage.budgetrak.ui.components.CURRENCY_SUFFIX_SYMBOLS)
        "$rounded $currencySymbol" else "$currencySymbol$rounded"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetCalendarScreen(
    recurringExpenses: List<RecurringExpense>,
    incomeSources: List<IncomeSource>,
    currencySymbol: String,
    weekStartSunday: Boolean = true,
    budgetPeriod: com.techadvantage.budgetrak.data.BudgetPeriod = com.techadvantage.budgetrak.data.BudgetPeriod.DAILY,
    resetDayOfWeek: Int = 1,
    resetDayOfMonth: Int = 1,
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current
    val density = LocalDensity.current

    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val rangeStart = displayedMonth.atDay(1)
    val rangeEnd = displayedMonth.atEndOfMonth()

    val dayEventsMap: Map<LocalDate, List<CalendarEvent>> = remember(
        displayedMonth, recurringExpenses, incomeSources
    ) {
        val map = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()
        for (income in incomeSources) {
            val dates = BudgetCalculator.generateOccurrences(
                income.repeatType, income.repeatInterval, income.startDate,
                income.monthDay1, income.monthDay2, rangeStart, rangeEnd
            )
            for (d in dates) {
                map.getOrPut(d) { mutableListOf() }
                    .add(CalendarEvent(income.source, income.amount, isIncome = true))
            }
        }
        for (expense in recurringExpenses) {
            val dates = BudgetCalculator.generateOccurrences(
                expense.repeatType, expense.repeatInterval, expense.startDate,
                expense.monthDay1, expense.monthDay2, rangeStart, rangeEnd
            )
            for (d in dates) {
                map.getOrPut(d) { mutableListOf() }
                    .add(CalendarEvent(expense.source, expense.amount, isIncome = false))
            }
        }
        map
    }

    val weekdayLabels = if (weekStartSunday) {
        listOf(S.budgetCalendar.sun, S.budgetCalendar.mon, S.budgetCalendar.tue,
            S.budgetCalendar.wed, S.budgetCalendar.thu, S.budgetCalendar.fri, S.budgetCalendar.sat)
    } else {
        listOf(S.budgetCalendar.mon, S.budgetCalendar.tue, S.budgetCalendar.wed,
            S.budgetCalendar.thu, S.budgetCalendar.fri, S.budgetCalendar.sat, S.budgetCalendar.sun)
    }

    // Offset: day-of-week of the 1st of the month
    val firstDow = displayedMonth.atDay(1).dayOfWeek.value // 1=Mon..7=Sun
    val startOffset = if (weekStartSunday) firstDow % 7 else (firstDow - 1)
    val daysInMonth = displayedMonth.lengthOfMonth()
    val today = LocalDate.now()

    // Compute which days in this month are budget reset days (weekly/monthly only)
    val resetDays = remember(displayedMonth, budgetPeriod, resetDayOfWeek, resetDayOfMonth) {
        val days = mutableSetOf<Int>()
        if (budgetPeriod == com.techadvantage.budgetrak.data.BudgetPeriod.WEEKLY) {
            val targetDow = java.time.DayOfWeek.of(resetDayOfWeek)
            var d = displayedMonth.atDay(1).with(java.time.temporal.TemporalAdjusters.nextOrSame(targetDow))
            while (d.monthValue == displayedMonth.monthValue) {
                days.add(d.dayOfMonth)
                d = d.plusWeeks(1)
            }
        } else if (budgetPeriod == com.techadvantage.budgetrak.data.BudgetPeriod.MONTHLY) {
            val clampedDay = resetDayOfMonth.coerceAtMost(daysInMonth)
            days.add(clampedDay)
        }
        days
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.budgetCalendar.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = S.common.back,
                            tint = customColors.headerText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onHelpClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.Help,
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
                .padding(horizontal = 8.dp)
        ) {
            // Month navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous month",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                        " " + displayedMonth.year,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next month",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Weekday headers
            val headerFontSize = with(density) { 18.dp.toSp() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(customColors.displayBackground)
                    .padding(vertical = 6.dp)
            ) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = headerFontSize,
                        color = customColors.headerText
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Calendar grid: 6 rows x 7 cols
            val dayFontSize = with(density) { 16.5f.dp.toSp() }
            val amountFontSize = with(density) { 11.25f.dp.toSp() }
            val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            val incomeColor = Color(0xFF4CAF50)
            val expenseColor = Color(0xFFF44336)
            val incomeBg = incomeColor.copy(alpha = 0.18f)
            val expenseBg = expenseColor.copy(alpha = 0.18f)
            val todayBorder = MaterialTheme.colorScheme.primary

            val swipeThreshold = with(density) { 48.dp.toPx() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                if (totalDrag > swipeThreshold) {
                                    displayedMonth = displayedMonth.minusMonths(1)
                                } else if (totalDrag < -swipeThreshold) {
                                    displayedMonth = displayedMonth.plusMonths(1)
                                }
                            },
                            onDragCancel = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                            }
                        )
                    }
            ) {
            for (week in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (col in 0 until 7) {
                        val dayIndex = week * 7 + col - startOffset + 1
                        val date = if (dayIndex in 1..daysInMonth) {
                            displayedMonth.atDay(dayIndex)
                        } else null

                        val events = date?.let { dayEventsMap[it] } ?: emptyList()
                        val hasIncome = events.any { it.isIncome }
                        val hasExpense = events.any { !it.isIncome }
                        val incomeTotal = events.filter { it.isIncome }.sumOf { it.amount }
                        val expenseTotal = events.filter { !it.isIncome }.sumOf { it.amount }
                        val isToday = date == today
                        val isResetDay = date != null && date.dayOfMonth in resetDays

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .border(
                                    width = if (isToday) 2.dp else 0.5.dp,
                                    color = if (isToday) todayBorder else gridLineColor
                                )
                                .then(
                                    if (isResetDay) Modifier.background(Color(0x1A2196F3)) // subtle blue tint
                                    else Modifier
                                )
                                .then(
                                    if (date != null && (events.isNotEmpty() || isResetDay))
                                        Modifier.clickable { selectedDate = date }
                                    else Modifier
                                )
                        ) {
                            if (date != null) {
                                // Background
                                if (hasIncome && hasExpense) {
                                    Column(Modifier.fillMaxSize()) {
                                        Box(Modifier.weight(1f).fillMaxWidth().background(incomeBg))
                                        Box(Modifier.weight(1f).fillMaxWidth().background(expenseBg))
                                    }
                                } else if (hasIncome) {
                                    Box(Modifier.fillMaxSize().background(incomeBg))
                                } else if (hasExpense) {
                                    Box(Modifier.fillMaxSize().background(expenseBg))
                                }

                                // Content
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(1.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = dayIndex.toString(),
                                        fontSize = dayFontSize,
                                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    if (hasIncome) {
                                        Text(
                                            text = formatCurrencyRounded(incomeTotal, currencySymbol),
                                            fontSize = amountFontSize,
                                            color = Color(0xFF2E7D32),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (hasExpense) {
                                        Text(
                                            text = formatCurrencyRounded(expenseTotal, currencySymbol),
                                            fontSize = amountFontSize,
                                            color = Color(0xFFD32F2F),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (events.size > 1) {
                                        Text(
                                            text = "[${events.size}]",
                                            fontSize = amountFontSize,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    // Day detail popup
    if (selectedDate != null) {
        val events = dayEventsMap[selectedDate] ?: emptyList()
        val incomes = events.filter { it.isIncome }
        val expenses = events.filter { !it.isIncome }

        AdAwareAlertDialog(
            onDismissRequest = { selectedDate = null },
            title = {
                Text(
                    S.budgetCalendar.dayDetails + " \u2014 " +
                        selectedDate!!.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
                        " " + selectedDate!!.dayOfMonth,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (incomes.isNotEmpty()) {
                        Text(
                            S.budgetCalendar.income,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                        incomes.forEach { event ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(event.source, modifier = Modifier.weight(1f))
                                Text(
                                    formatCurrency(event.amount, currencySymbol),
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                        if (incomes.size > 1) {
                            Text(
                                S.budgetCalendar.totalIncome(
                                    formatCurrency(incomes.sumOf { it.amount }, currencySymbol)
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    if (incomes.isNotEmpty() && expenses.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    }
                    if (expenses.isNotEmpty()) {
                        Text(
                            S.budgetCalendar.expenses,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF44336)
                        )
                        expenses.forEach { event ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(event.source, modifier = Modifier.weight(1f))
                                Text(
                                    formatCurrency(event.amount, currencySymbol),
                                    color = Color(0xFFF44336)
                                )
                            }
                        }
                        if (expenses.size > 1) {
                            Text(
                                S.budgetCalendar.totalExpenses(
                                    formatCurrency(expenses.sumOf { it.amount }, currencySymbol)
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFD32F2F),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    if (selectedDate != null && selectedDate!!.dayOfMonth in resetDays) {
                        if (incomes.isNotEmpty() || expenses.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                        Text(
                            S.budgetCalendar.budgetResetDay,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                    }
                    if (incomes.isEmpty() && expenses.isEmpty() && (selectedDate == null || selectedDate!!.dayOfMonth !in resetDays)) {
                        Text(S.budgetCalendar.noEvents)
                    }
                }
            },
            confirmButton = {
                DialogSecondaryButton(onClick = { selectedDate = null }) {
                    Text(S.common.close)
                }
            }
        )
    }
}
