package com.syncbudget.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncbudget.app.ui.theme.FlipFontFamily
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

private val CARD_CORNER = 8.dp
private const val FLIP_DURATION_MS = 250

/**
 * A flip card that animates between arbitrary string values.
 * Steps through the values list in order (wrapping) to reach the target.
 */
@Composable
fun FlipChar(
    targetValue: String,
    values: List<String>,
    onFlipSound: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 72.dp,
    cardHeight: Dp = 108.dp,
    fontSize: TextUnit = 64.sp,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    var currentValue by remember { mutableStateOf(targetValue) }
    var fromValue by remember { mutableStateOf(targetValue) }
    var toValue by remember { mutableStateOf(targetValue) }
    var progress by remember { mutableFloatStateOf(0f) }
    var soundFired by remember { mutableStateOf(false) }

    val flipAnim = remember { Animatable(0f) }

    LaunchedEffect(targetValue) {
        if (targetValue == currentValue) return@LaunchedEffect

        val currentIdx = values.indexOf(currentValue).coerceAtLeast(0)
        val targetIdx = values.indexOf(targetValue).coerceAtLeast(0)

        // Build steps: go forward through the list wrapping around
        val steps = mutableListOf<String>()
        var idx = currentIdx
        while (idx != targetIdx) {
            idx = (idx + 1) % values.size
            steps.add(values[idx])
        }

        // If no steps computed (same index), just snap
        if (steps.isEmpty()) {
            currentValue = targetValue
            return@LaunchedEffect
        }

        for (nextValue in steps) {
            fromValue = currentValue
            toValue = nextValue
            soundFired = false
            flipAnim.snapTo(0f)
            flipAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = FLIP_DURATION_MS,
                    easing = LinearOutSlowInEasing
                )
            ) {
                progress = value
                if (value >= 0.45f && !soundFired) {
                    soundFired = true
                    onFlipSound()
                }
            }
            currentValue = nextValue
        }

        progress = 0f
        fromValue = targetValue
        toValue = targetValue
    }

    CharFlipCard(
        currentValue = fromValue,
        nextValue = toValue,
        progress = progress,
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        modifier = modifier
    )
}

@Composable
private fun CharFlipCard(
    currentValue: String,
    nextValue: String,
    progress: Float,
    cardWidth: Dp,
    cardHeight: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val halfHeight = cardHeight / 2
    val isAnimating = currentValue != nextValue && progress > 0f && progress < 1f

    Box(modifier = modifier.size(cardWidth, cardHeight)) {

        CharHalf(
            text = if (progress < 0.5f) currentValue else nextValue,
            isTopHalf = false,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        CharHalf(
            text = if (isAnimating) nextValue else currentValue,
            isTopHalf = true,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (isAnimating && progress < 0.5f) {
            val rotation = -180f * progress
            val shadowAlpha = progress * 0.6f

            CharHalf(
                text = currentValue,
                isTopHalf = true,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                fontSize = fontSize,
                lineHeight = lineHeight,
                shadowAlpha = shadowAlpha,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        rotationX = rotation
                        cameraDistance = 6f * density.density
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
            )
        }

        if (isAnimating && progress >= 0.5f) {
            val rotation = 180f * (1f - progress)
            val shadowAlpha = (1f - progress) * 0.6f

            CharHalf(
                text = nextValue,
                isTopHalf = false,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                fontSize = fontSize,
                lineHeight = lineHeight,
                shadowAlpha = shadowAlpha,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        rotationX = rotation
                        cameraDistance = 6f * density.density
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0xFF000000))
        )

    }
}

@Composable
private fun CharHalf(
    text: String,
    isTopHalf: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier,
    shadowAlpha: Float = 0f
) {
    val colors = LocalSyncBudgetColors.current
    val halfHeight = cardHeight / 2
    val density = LocalDensity.current
    val cardHeightPx = with(density) { cardHeight.roundToPx() }
    val halfHeightPx = with(density) { halfHeight.roundToPx() }

    val shape = if (isTopHalf) {
        RoundedCornerShape(topStart = CARD_CORNER, topEnd = CARD_CORNER)
    } else {
        RoundedCornerShape(bottomStart = CARD_CORNER, bottomEnd = CARD_CORNER)
    }

    Box(
        modifier = modifier
            .size(cardWidth, halfHeight)
            .clip(shape)
            .background(colors.cardBackground)
    ) {
        Layout(
            content = {
                Text(
                    text = text,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = colors.cardText,
                    fontFamily = FlipFontFamily,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        ) { measurables, constraints ->
            val placeable = measurables.first().measure(
                Constraints(maxWidth = constraints.maxWidth)
            )

            layout(constraints.maxWidth, constraints.maxHeight) {
                val x = (constraints.maxWidth - placeable.width) / 2
                val centerY = (cardHeightPx - placeable.height) / 2
                val y = if (isTopHalf) centerY else centerY - halfHeightPx
                placeable.placeRelative(x, y)
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isTopHalf) listOf(
                            Color(0x0AFFFFFF),
                            Color.Transparent
                        ) else listOf(
                            Color.Transparent,
                            Color(0x08000000)
                        )
                    )
                )
        )

        if (shadowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = shadowAlpha))
            )
        }
    }
}
