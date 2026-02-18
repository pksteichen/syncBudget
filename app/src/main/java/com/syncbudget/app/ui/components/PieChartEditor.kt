package com.syncbudget.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syncbudget.app.data.Category
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val PIE_COLORS = listOf(
    Color(0xFF4CAF50), // Green
    Color(0xFF2196F3), // Blue
    Color(0xFFF44336), // Red
    Color(0xFFFF9800), // Orange
    Color(0xFF9C27B0), // Purple
    Color(0xFF00BCD4), // Cyan
    Color(0xFFFFEB3B), // Yellow
    Color(0xFF795548), // Brown
    Color(0xFFE91E63), // Pink
    Color(0xFF607D8B)  // Blue Grey
)

private fun colorForIndex(index: Int): Color = PIE_COLORS[index % PIE_COLORS.size]

/** Clockwise sweep from [from] to [to] in degrees, always >= 0. */
private fun sweepBetween(from: Float, to: Float): Float {
    return (to - from).mod(360f)
}

/** Convert a point to an angle in degrees (0 = right / 3 o'clock, clockwise). */
private fun pointToAngle(center: Offset, point: Offset): Float {
    val dx = point.x - center.x
    val dy = point.y - center.y
    val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    return deg.mod(360f)
}

/** Convert an angle to a point on the circle perimeter. */
private fun angleToPoint(center: Offset, radius: Float, angleDeg: Float): Offset {
    val rad = Math.toRadians(angleDeg.toDouble())
    return Offset(
        center.x + radius * cos(rad).toFloat(),
        center.y + radius * sin(rad).toFloat()
    )
}

/**
 * Constrain [angle] to lie within the clockwise arc from [prevHandle] to [nextHandle].
 * When prev == next (N=2 case), the handle can go anywhere — no constraint.
 */
private fun constrainAngle(angle: Float, prevHandle: Float, nextHandle: Float): Float {
    // N=2: prev and next are the same handle — full circle is valid
    val prevNorm = prevHandle.mod(360f)
    val nextNorm = nextHandle.mod(360f)
    if ((prevNorm - nextNorm).mod(360f) < 0.01f) return angle

    val totalArc = sweepBetween(prevHandle, nextHandle)
    if (totalArc < 0.01f) return prevHandle
    val fromPrev = sweepBetween(prevHandle, angle)
    return if (fromPrev <= totalArc) {
        angle
    } else {
        // Outside valid arc — clamp to nearest boundary
        val distToNext = fromPrev - totalArc
        val distToPrev = 360f - fromPrev
        if (distToNext < distToPrev) nextHandle else prevHandle
    }
}

/**
 * Among overlapping handles, pick the one that matches the user's drag direction.
 * Clockwise drag → pick handle with zero-sweep wedge BEFORE it (CW opens it).
 * Counter-clockwise drag → pick handle with zero-sweep wedge AFTER it (CCW opens it).
 */
private fun pickHandleForDirection(
    candidates: List<Int>,
    handleAngles: List<Float>,
    n: Int,
    isClockwise: Boolean
): Int {
    if (isClockwise) {
        for (idx in candidates) {
            val prevIdx = (idx - 1 + n) % n
            val sweepBefore = sweepBetween(handleAngles[prevIdx], handleAngles[idx])
            if (sweepBefore < 0.5f) return idx
        }
    } else {
        for (idx in candidates) {
            val nextIdx = (idx + 1) % n
            val sweepAfter = sweepBetween(handleAngles[idx], handleAngles[nextIdx])
            if (sweepAfter < 0.5f) return idx
        }
    }
    return candidates.first()
}

private fun formatPieAmount(value: Double, decimals: Int): String {
    return "%.${decimals}f".format(value)
}

@Composable
fun PieChartEditor(
    categories: List<Category>,
    totalAmount: Double,
    maxDecimals: Int,
    currencySymbol: String,
    categoryAmounts: Map<Int, Double>,
    onAmountsChanged: (Map<Int, Double>) -> Unit
) {
    val n = categories.size
    if (n < 2 || totalAmount <= 0) return

    val handleAngles = remember { mutableStateListOf<Float>() }
    var draggedIndex by remember { mutableIntStateOf(-1) }

    // Initialize handles from existing amounts or equal distribution
    LaunchedEffect(categories.map { it.id }) {
        val total = categoryAmounts.values.sum()
        val useExisting = total > 0.0

        val newAngles = mutableListOf<Float>()
        var cumulative = 0f
        for (i in 0 until n) {
            newAngles.add((270f + cumulative).mod(360f))
            val fraction = if (useExisting) {
                ((categoryAmounts[categories[i].id] ?: 0.0) / total).toFloat()
            } else {
                1f / n
            }
            cumulative += fraction * 360f
        }
        handleAngles.clear()
        handleAngles.addAll(newAngles)
    }

    // Don't render until initialized
    if (handleAngles.size != n) return

    val density = LocalDensity.current
    val handleRadiusDp = 12.dp
    val handleRadiusPx = with(density) { handleRadiusDp.toPx() }
    val touchThresholdPx = with(density) { 30.dp.toPx() }
    val divisionLineWidth = with(density) { 2.dp.toPx() }
    val overlapThresholdPx = with(density) { 6.dp.toPx() }

    // Zero-wedge pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "zeroPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "handlePulse"
    )

    // Compute current amounts from angles
    val currentAmounts = remember(handleAngles.toList(), totalAmount) {
        val amounts = mutableMapOf<Int, Double>()
        for (i in 0 until n) {
            val sweep = sweepBetween(handleAngles[i], handleAngles[(i + 1) % n])
            amounts[categories[i].id] = totalAmount * sweep / 360.0
        }
        amounts
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Pie chart canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(categories.map { it.id }) {
                        val canvasSize = size
                        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                        val radius = (minOf(canvasSize.width, canvasSize.height) / 2f) - handleRadiusPx - 4f

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            // Find all handles within touch threshold
                            val candidates = mutableListOf<Pair<Int, Float>>()
                            for (i in 0 until n) {
                                val pos = angleToPoint(center, radius, handleAngles[i])
                                val dx = down.position.x - pos.x
                                val dy = down.position.y - pos.y
                                val dist = sqrt(dx * dx + dy * dy)
                                if (dist < touchThresholdPx) {
                                    candidates.add(i to dist)
                                }
                            }

                            if (candidates.isEmpty()) {
                                // Not near any handle — don't consume, let parent scroll
                                return@awaitEachGesture
                            }

                            // Near a handle — claim this gesture
                            down.consume()

                            val minDist = candidates.minOf { it.second }
                            val closeOnes = candidates
                                .filter { it.second - minDist < overlapThresholdPx }
                                .map { it.first }

                            // Single handle: select immediately
                            // Overlapping: defer until we know drag direction
                            var selectedIdx = if (closeOnes.size == 1) closeOnes.first() else -1
                            if (selectedIdx >= 0) draggedIndex = selectedIdx

                            val startAngle = pointToAngle(center, down.position)

                            // Track drag
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    draggedIndex = -1
                                    break
                                }
                                change.consume()

                                val rawAngle = pointToAngle(center, change.position)

                                // Resolve overlapping handle based on drag direction
                                if (selectedIdx < 0) {
                                    val delta = (rawAngle - startAngle).mod(360f)
                                    val moved = if (delta > 180f) 360f - delta else delta
                                    if (moved > 1.5f) {
                                        val isClockwise = delta <= 180f
                                        selectedIdx = pickHandleForDirection(
                                            closeOnes, handleAngles, n, isClockwise
                                        )
                                        draggedIndex = selectedIdx
                                    }
                                }

                                if (selectedIdx >= 0) {
                                    val idx = selectedIdx
                                    val prevIdx = (idx - 1 + n) % n
                                    val nextIdx = (idx + 1) % n
                                    val constrained = constrainAngle(
                                        rawAngle,
                                        handleAngles[prevIdx],
                                        handleAngles[nextIdx]
                                    )
                                    handleAngles[idx] = constrained

                                    // If clamped, rotate the entire pie by the overflow
                                    val diff = (rawAngle - constrained).mod(360f)
                                    val signedDiff = if (diff > 180f) diff - 360f else diff
                                    if (abs(signedDiff) > 0.1f) {
                                        for (i in 0 until n) {
                                            handleAngles[i] = (handleAngles[i] + signedDiff).mod(360f)
                                        }
                                    }

                                    // Report new amounts
                                    val newAmounts = mutableMapOf<Int, Double>()
                                    for (i in 0 until n) {
                                        val sweep = sweepBetween(
                                            handleAngles[i],
                                            handleAngles[(i + 1) % n]
                                        )
                                        newAmounts[categories[i].id] = totalAmount * sweep / 360.0
                                    }
                                    onAmountsChanged(newAmounts)
                                }
                            }
                        }
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (minOf(size.width, size.height) / 2f) - handleRadiusPx - 4f
                val arcTopLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2, radius * 2)

                // Draw filled arcs
                for (i in 0 until n) {
                    val startAngle = handleAngles[i]
                    val sweep = sweepBetween(handleAngles[i], handleAngles[(i + 1) % n])
                    if (sweep > 0.01f) {
                        drawArc(
                            color = colorForIndex(i),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = true,
                            topLeft = arcTopLeft,
                            size = arcSize
                        )
                    }
                }

                // Draw thin outline around pie
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5f)
                )

                // Draw division lines from center to edge
                for (i in 0 until n) {
                    val edgePoint = angleToPoint(center, radius, handleAngles[i])
                    drawLine(
                        color = Color.White,
                        start = center,
                        end = edgePoint,
                        strokeWidth = divisionLineWidth
                    )
                }

                // Draw handle circles
                for (i in 0 until n) {
                    val pos = angleToPoint(center, radius, handleAngles[i])
                    val sweepBefore = sweepBetween(
                        handleAngles[(i - 1 + n) % n],
                        handleAngles[i]
                    )
                    val sweepAfter = sweepBetween(
                        handleAngles[i],
                        handleAngles[(i + 1) % n]
                    )
                    val isAtZero = sweepBefore < 0.5f || sweepAfter < 0.5f
                    val alpha = if (isAtZero) pulseAlpha else 1f

                    // Outer white circle
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = handleRadiusPx,
                        center = pos
                    )
                    // Inner dark circle
                    drawCircle(
                        color = Color(0xFF424242).copy(alpha = alpha),
                        radius = handleRadiusPx - 3f,
                        center = pos
                    )
                }
            }
        }

        // Legend
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEachIndexed { index, cat ->
                val amount = currentAmounts[cat.id] ?: 0.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(colorForIndex(index), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = cat.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$currencySymbol${formatPieAmount(amount, maxDecimals)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}
