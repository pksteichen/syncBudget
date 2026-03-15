package com.syncbudget.app.ui.screens

import android.graphics.Paint as NativePaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SavingsSimulator
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationGraphScreen(
    incomeSources: List<IncomeSource>,
    recurringExpenses: List<RecurringExpense>,
    budgetPeriod: BudgetPeriod,
    baseBudget: Double,
    amortizationEntries: List<AmortizationEntry>,
    savingsGoals: List<SavingsGoal>,
    availableCash: Double,
    resetDayOfWeek: Int,
    resetDayOfMonth: Int,
    currencySymbol: String,
    today: LocalDate = LocalDate.now(),
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val S = LocalStrings.current
    val customColors = LocalSyncBudgetColors.current
    val density = LocalDensity.current

    val (simResult, timeline) = SavingsSimulator.simulateTimeline(
        incomeSources, recurringExpenses, budgetPeriod,
        baseBudget, amortizationEntries, savingsGoals,
        availableCash, resetDayOfWeek, resetDayOfMonth, today
    )

    val maxDecimals = CURRENCY_DECIMALS[currencySymbol] ?: 2
    var savingsText by remember {
        mutableStateOf("%.${maxDecimals}f".format(simResult.savingsRequired))
    }
    val currentSavings = savingsText.toDoubleOrNull() ?: simResult.savingsRequired

    var savedPerPeriodText by remember { mutableStateOf("") }
    val savedPerPeriod = savedPerPeriodText.toDoubleOrNull() ?: 0.0

    val periodLabel = when (budgetPeriod) {
        BudgetPeriod.DAILY -> S.common.periodDay
        BudgetPeriod.WEEKLY -> S.common.periodWeek
        BudgetPeriod.MONTHLY -> S.common.periodMonth
    }

    val savingsExceedBudget = savedPerPeriod > 0 && savedPerPeriod >= baseBudget

    // Re-run simulation when savedPerPeriod changes
    val (adjSimResult, adjTimeline) = SavingsSimulator.simulateTimeline(
        incomeSources, recurringExpenses, budgetPeriod,
        baseBudget - savedPerPeriod, amortizationEntries, savingsGoals,
        availableCash, resetDayOfWeek, resetDayOfMonth, today
    )

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.futureExpenditures.simulationGraphTitle,
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = S.futureExpenditures.simulationGraphDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = savingsText,
                    onValueChange = { newVal ->
                        if (newVal.isEmpty() || newVal == "." || newVal.toDoubleOrNull() != null) {
                            val dotIdx = newVal.indexOf('.')
                            val decs = if (dotIdx >= 0) newVal.length - dotIdx - 1 else 0
                            if (maxDecimals == 0 && dotIdx >= 0) { /* block */ }
                            else if (decs <= maxDecimals) { savingsText = newVal }
                        }
                    },
                    label = { Text(S.futureExpenditures.simulationSavingsLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (maxDecimals > 0) KeyboardType.Decimal else KeyboardType.Number),
                    colors = textFieldColors,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = savedPerPeriodText,
                    onValueChange = { newVal ->
                        val cleaned = newVal.filter { it.isDigit() || it == '.' || it == '-' }
                        // Allow empty, just minus, minus-dot, or valid number
                        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "." || cleaned == "-." ||
                            cleaned.toDoubleOrNull() != null) {
                            val dotIdx = cleaned.indexOf('.')
                            val decs = if (dotIdx >= 0) cleaned.length - dotIdx - 1 else 0
                            // Only one minus, must be at start
                            val minusCount = cleaned.count { it == '-' }
                            if (minusCount > 1 || (minusCount == 1 && cleaned[0] != '-')) return@OutlinedTextField
                            if (maxDecimals == 0 && dotIdx >= 0) { /* block */ }
                            else if (decs <= maxDecimals) { savedPerPeriodText = cleaned }
                        }
                    },
                    label = { Text(S.futureExpenditures.simulationSavedPerLabel(periodLabel)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            if (savingsExceedBudget) {
                Text(
                    S.futureExpenditures.simulationSavingsExceedBudget,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            } else if (adjTimeline.size < 2) {
                Text(
                    S.futureExpenditures.simulationNoData,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            } else {
                val yAxisWidthDp = 80.dp
                val xAxisHeightDp = 80.dp

                val startDate = adjTimeline.first().date
                val totalDays = ChronoUnit.DAYS.between(startDate, adjTimeline.last().date)
                    .toInt().coerceAtLeast(1)

                val adjustedPoints = remember(adjTimeline, currentSavings) {
                    adjTimeline.map { it.balance + currentSavings }
                }
                val yMin = remember(adjustedPoints) {
                    minOf(0.0, adjustedPoints.minOrNull() ?: 0.0)
                }
                val yMax = remember(adjustedPoints) {
                    val raw = adjustedPoints.maxOrNull() ?: 1.0
                    if (raw <= yMin) yMin + 1.0 else raw
                }

                val lineColor = customColors.accentTint
                val gridColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                val axisTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                val zeroLineColor = Color(0xFFF44336).copy(alpha = 0.4f)

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val chartVisibleWidth = maxWidth - yAxisWidthDp
                    val initialDpPerDay = (chartVisibleWidth.value / 30f).coerceAtLeast(1f)
                    var dpPerDay by remember { mutableFloatStateOf(initialDpPerDay) }
                    val minDpPerDay = (chartVisibleWidth.value / totalDays.coerceAtLeast(1)).coerceAtLeast(0.5f)

                    val chartWidthDp: Dp = with(density) { (totalDays * dpPerDay).toDp() }
                        .coerceAtLeast(chartVisibleWidth)

                    val scrollState = rememberScrollState()
                    val zoomScope = rememberCoroutineScope()

                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Y-axis (fixed)
                            Canvas(
                                modifier = Modifier
                                    .width(yAxisWidthDp)
                                    .fillMaxHeight()
                                    .padding(bottom = xAxisHeightDp)
                            ) {
                                val h = size.height
                                val pad = h * 0.05f
                                val drawH = h - 2 * pad

                                fun valueToY(v: Double): Float =
                                    h - pad - ((v - yMin) / (yMax - yMin) * drawH).toFloat()

                                val interval = niceInterval(yMax - yMin)
                                val textPaint = NativePaint().apply {
                                    textSize = 20.dp.toPx()
                                    color = axisTextColor.toArgb()
                                    isAntiAlias = true
                                    textAlign = NativePaint.Align.RIGHT
                                }
                                val textX = size.width - 8.dp.toPx()
                                val tickEnd = size.width
                                val tickStart = size.width - 4.dp.toPx()

                                var tick = ceil(yMin / interval) * interval
                                while (tick <= yMax + interval * 0.001) {
                                    val y = valueToY(tick)
                                    if (y in 0f..h) {
                                        drawLine(
                                            gridColor, Offset(tickStart, y),
                                            Offset(tickEnd, y), strokeWidth = 1.dp.toPx()
                                        )
                                        drawContext.canvas.nativeCanvas.drawText(
                                            formatAxisValue(tick, currencySymbol),
                                            textX, y + 4.dp.toPx(), textPaint
                                        )
                                    }
                                    tick += interval
                                }
                            }

                            // Scrollable chart + X-axis
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .pointerInput(Unit) {
                                        val decay = exponentialDecay<Float>(frictionMultiplier = 1.2f)
                                        awaitEachGesture {
                                            var zoom = 1f
                                            var pan = Offset.Zero
                                            var pastTouchSlop = false
                                            val touchSlop = viewConfiguration.touchSlop
                                            var isPanning = false
                                            val velocityTracker = VelocityTracker()

                                            awaitFirstDown(requireUnconsumed = false)
                                            do {
                                                val event = awaitPointerEvent()
                                                val canceled = event.changes.any { it.isConsumed }
                                                if (!canceled) {
                                                    val zoomChange = event.calculateZoom()
                                                    val panChange = event.calculatePan()

                                                    if (!pastTouchSlop) {
                                                        zoom *= zoomChange
                                                        pan += panChange
                                                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                                        val zoomMotion = abs(1 - zoom) * centroidSize
                                                        val panMotion = pan.getDistance()
                                                        if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                                            pastTouchSlop = true
                                                        }
                                                    }

                                                    if (pastTouchSlop) {
                                                        val centroid = event.calculateCentroid(useCurrent = false)
                                                        if (zoomChange != 1f) {
                                                            // Pinch zoom
                                                            isPanning = false
                                                            velocityTracker.resetTracking()
                                                            val oldVal = dpPerDay
                                                            val newVal = (oldVal * zoomChange).coerceIn(minDpPerDay, 80f)
                                                            if (newVal != oldVal) {
                                                                dpPerDay = newVal
                                                                val scale = newVal / oldVal
                                                                val focusPx = scrollState.value + centroid.x
                                                                val newScrollPx = (focusPx * scale - centroid.x)
                                                                    .toInt().coerceAtLeast(0)
                                                                zoomScope.launch { scrollState.scrollTo(newScrollPx) }
                                                            }
                                                        } else if (panChange != Offset.Zero) {
                                                            // Pan / scroll
                                                            isPanning = true
                                                            val ptr = event.changes.firstOrNull()
                                                            if (ptr != null) {
                                                                velocityTracker.addPosition(
                                                                    ptr.uptimeMillis,
                                                                    ptr.position
                                                                )
                                                            }
                                                            val target = (scrollState.value - panChange.x)
                                                                .toInt().coerceIn(0, scrollState.maxValue)
                                                            zoomScope.launch { scrollState.scrollTo(target) }
                                                        }
                                                        event.changes.forEach {
                                                            if (it.positionChanged()) {
                                                                it.consume()
                                                            }
                                                        }
                                                    }
                                                }
                                            } while (event.changes.any { it.pressed })

                                            // Fling on lift
                                            if (isPanning) {
                                                val velocity = velocityTracker.calculateVelocity()
                                                if (abs(velocity.x) > 50f) {
                                                    zoomScope.launch {
                                                        scrollState.scroll {
                                                            var prevValue = 0f
                                                            AnimationState(
                                                                initialValue = 0f,
                                                                initialVelocity = -velocity.x
                                                            ).animateDecay(decay) {
                                                                val delta = value - prevValue
                                                                prevValue = value
                                                                scrollBy(delta)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .horizontalScroll(scrollState, enabled = false)
                            ) {
                                // Chart canvas
                                Canvas(
                                    modifier = Modifier
                                        .requiredWidth(chartWidthDp)
                                        .weight(1f)
                                        .clipToBounds()
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val pad = h * 0.05f
                                    val drawH = h - 2 * pad

                                    fun valueToY(v: Double): Float =
                                        h - pad - ((v - yMin) / (yMax - yMin) * drawH).toFloat()

                                    fun dateToX(date: LocalDate): Float {
                                        val days = ChronoUnit.DAYS.between(startDate, date).toFloat()
                                        return days / totalDays * w
                                    }

                                    // Horizontal grid lines
                                    val interval = niceInterval(yMax - yMin)
                                    var tick = ceil(yMin / interval) * interval
                                    while (tick <= yMax + interval * 0.001) {
                                        val y = valueToY(tick)
                                        if (y in 0f..h) {
                                            drawLine(
                                                gridColor, Offset(0f, y),
                                                Offset(w, y), strokeWidth = 0.5.dp.toPx()
                                            )
                                        }
                                        tick += interval
                                    }

                                    // Zero line (if negative region visible)
                                    if (yMin < -0.01) {
                                        val zeroY = valueToY(0.0)
                                        if (zeroY in 0f..h) {
                                            drawLine(
                                                zeroLineColor, Offset(0f, zeroY),
                                                Offset(w, zeroY), strokeWidth = 1.5.dp.toPx()
                                            )
                                        }
                                    }

                                    // Build line + shade paths
                                    val linePath = Path()
                                    val shadePath = Path()
                                    val bottomY = h - pad

                                    adjTimeline.forEachIndexed { i, point ->
                                        val x = dateToX(point.date)
                                        val y = valueToY(adjustedPoints[i])
                                        if (i == 0) {
                                            linePath.moveTo(x, y)
                                            shadePath.moveTo(x, y)
                                        } else {
                                            linePath.lineTo(x, y)
                                            shadePath.lineTo(x, y)
                                        }
                                    }

                                    // Close shade path along bottom
                                    val lastX = dateToX(adjTimeline.last().date)
                                    val firstX = dateToX(adjTimeline.first().date)
                                    shadePath.lineTo(lastX, bottomY)
                                    shadePath.lineTo(firstX, bottomY)
                                    shadePath.close()

                                    // Draw shading
                                    drawPath(
                                        path = shadePath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                lineColor.copy(alpha = 0.35f),
                                                lineColor.copy(alpha = 0.03f)
                                            ),
                                            startY = pad,
                                            endY = bottomY
                                        )
                                    )

                                    // Draw line
                                    drawPath(
                                        path = linePath,
                                        color = lineColor,
                                        style = Stroke(
                                            width = 2.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )

                                    // Low point marker
                                    if (adjSimResult.lowPointDate != null) {
                                        val lowIdx = adjTimeline.indexOfFirst {
                                            it.date == adjSimResult.lowPointDate
                                        }
                                        if (lowIdx >= 0) {
                                            val lx = dateToX(adjTimeline[lowIdx].date)
                                            val ly = valueToY(adjustedPoints[lowIdx])
                                            drawCircle(
                                                Color(0xFFF44336),
                                                radius = 5.dp.toPx(),
                                                center = Offset(lx, ly)
                                            )
                                            drawCircle(
                                                Color.White,
                                                radius = 2.5.dp.toPx(),
                                                center = Offset(lx, ly)
                                            )
                                        }
                                    }
                                }

                                // X-axis canvas
                                Canvas(
                                    modifier = Modifier
                                        .requiredWidth(chartWidthDp)
                                        .height(xAxisHeightDp)
                                        .clipToBounds()
                                ) {
                                    val w = size.width

                                    fun dateToX(date: LocalDate): Float {
                                        val days = ChronoUnit.DAYS.between(startDate, date).toFloat()
                                        return days / totalDays * w
                                    }

                                    val labelInterval = when {
                                        dpPerDay < 2f -> 61
                                        dpPerDay < 4f -> 30
                                        dpPerDay < 10f -> 14
                                        dpPerDay < 55f -> 7
                                        else -> 1
                                    }
                                    val dateFormat = when {
                                        labelInterval > 30 -> "MMM yy"
                                        else -> "MMM d"
                                    }
                                    val formatter = DateTimeFormatter.ofPattern(dateFormat)

                                    val tickColor = gridColor.toArgb()
                                    val textPaint = NativePaint().apply {
                                        textSize = 18.dp.toPx()
                                        color = axisTextColor.toArgb()
                                        isAntiAlias = true
                                    }

                                    drawContext.canvas.nativeCanvas.apply {
                                        // Top border of X-axis area
                                        val borderPaint = NativePaint().apply {
                                            color = tickColor
                                            strokeWidth = 1.dp.toPx()
                                        }
                                        drawLine(0f, 0f, w, 0f, borderPaint)

                                        var d = startDate
                                        val endDate = adjTimeline.last().date
                                        while (!d.isAfter(endDate)) {
                                            val x = dateToX(d)
                                            // Tick mark
                                            drawLine(x, 0f, x, 6.dp.toPx(), borderPaint)
                                            // Rotated label
                                            save()
                                            translate(x, 8.dp.toPx())
                                            rotate(60f)
                                            drawText(d.format(formatter), 0f, 0f, textPaint)
                                            restore()
                                            d = d.plusDays(labelInterval.toLong())
                                        }
                                    }
                                }
                            }
                        }

                        // Zoom buttons (bottom-right, above X-axis)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = 8.dp,
                                    bottom = xAxisHeightDp + 8.dp
                                ),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = {
                                    dpPerDay = (dpPerDay / 1.5f).coerceAtLeast(minDpPerDay)
                                },
                                shape = CircleShape,
                                color = customColors.headerBackground.copy(alpha = 0.9f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Filled.Remove,
                                        contentDescription = "Zoom out",
                                        tint = customColors.headerText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Surface(
                                onClick = {
                                    dpPerDay = (dpPerDay * 1.5f).coerceAtMost(80f)
                                },
                                shape = CircleShape,
                                color = customColors.headerBackground.copy(alpha = 0.9f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Zoom in",
                                        tint = customColors.headerText,
                                        modifier = Modifier.size(18.dp)
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

private fun niceInterval(range: Double, targetLines: Int = 5): Double {
    if (range <= 0) return 1.0
    val rough = range / targetLines
    val exponent = floor(log10(rough))
    val magnitude = 10.0.pow(exponent)
    val normalized = rough / magnitude
    return magnitude * when {
        normalized <= 1.5 -> 1.0
        normalized <= 3.5 -> 2.5
        normalized <= 7.5 -> 5.0
        else -> 10.0
    }
}

private fun formatAxisValue(value: Double, currencySymbol: String): String {
    val absVal = abs(value)
    val prefix = if (value < 0) "-" else ""
    return when {
        absVal >= 10000 -> "$prefix$currencySymbol${"%.0f".format(absVal / 1000)}k"
        absVal >= 1000 -> "$prefix$currencySymbol${"%.1f".format(absVal / 1000)}k"
        else -> "$prefix$currencySymbol${"%.0f".format(absVal)}"
    }
}
