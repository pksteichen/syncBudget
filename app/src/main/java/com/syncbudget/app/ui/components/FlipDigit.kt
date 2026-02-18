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
import androidx.compose.runtime.mutableIntStateOf
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

private val CARD_WIDTH = 72.dp
private val CARD_HEIGHT = 108.dp
private val CARD_CORNER = 8.dp
private val DIGIT_FONT_SIZE = 80.sp
private const val FLIP_DURATION_MS = 250

/**
 * Flip digit card. Pass -1 for targetDigit to show a blank card.
 */
@Composable
fun FlipDigit(
    targetDigit: Int,
    onFlipSound: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = CARD_WIDTH,
    cardHeight: Dp = CARD_HEIGHT,
    fontSize: TextUnit = DIGIT_FONT_SIZE
) {
    var currentDigit by remember { mutableIntStateOf(targetDigit) }
    var fromDigit by remember { mutableIntStateOf(targetDigit) }
    var toDigit by remember { mutableIntStateOf(targetDigit) }
    var progress by remember { mutableFloatStateOf(0f) }
    var soundFired by remember { mutableIntStateOf(0) }

    val flipAnim = remember { Animatable(0f) }

    LaunchedEffect(targetDigit) {
        if (targetDigit == currentDigit) return@LaunchedEffect

        if (targetDigit == -1 || currentDigit == -1) {
            // Direct single flip to/from blank
            fromDigit = currentDigit
            toDigit = targetDigit
            soundFired = 0
            flipAnim.snapTo(0f)
            flipAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = FLIP_DURATION_MS,
                    easing = LinearOutSlowInEasing
                )
            ) {
                progress = value
                if (value >= 0.45f && soundFired == 0) {
                    soundFired = 1
                    onFlipSound()
                }
            }
            currentDigit = targetDigit
        } else {
            // Normal stepping animation through digits
            val steps = mutableListOf<Int>()
            var d = currentDigit
            while (d != targetDigit) {
                d = (d + 1) % 10
                steps.add(d)
            }

            for (nextDigit in steps) {
                fromDigit = currentDigit
                toDigit = nextDigit
                soundFired = 0
                flipAnim.snapTo(0f)
                flipAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FLIP_DURATION_MS,
                        easing = LinearOutSlowInEasing
                    )
                ) {
                    progress = value
                    if (value >= 0.45f && soundFired == 0) {
                        soundFired = 1
                        onFlipSound()
                    }
                }
                currentDigit = nextDigit
            }
        }

        progress = 0f
        fromDigit = targetDigit
        toDigit = targetDigit
    }

    FlipCard(
        currentDigit = fromDigit,
        nextDigit = toDigit,
        progress = progress,
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        fontSize = fontSize,
        modifier = modifier
    )
}

@Composable
private fun FlipCard(
    currentDigit: Int,
    nextDigit: Int,
    progress: Float,
    cardWidth: Dp,
    cardHeight: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val halfHeight = cardHeight / 2
    val isAnimating = currentDigit != nextDigit && progress > 0f && progress < 1f

    Box(modifier = modifier.size(cardWidth, cardHeight)) {

        // ===== LAYER 1: STATIC BACKGROUND HALVES =====

        DigitHalf(
            digit = if (progress < 0.5f) currentDigit else nextDigit,
            isTopHalf = false,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            fontSize = fontSize,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        DigitHalf(
            digit = if (isAnimating) nextDigit else currentDigit,
            isTopHalf = true,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            fontSize = fontSize,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // ===== LAYER 2: ANIMATED FLAPS =====

        if (isAnimating && progress < 0.5f) {
            val rotation = -180f * progress
            val shadowAlpha = progress * 0.6f

            DigitHalf(
                digit = currentDigit,
                isTopHalf = true,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                fontSize = fontSize,
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

            DigitHalf(
                digit = nextDigit,
                isTopHalf = false,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                fontSize = fontSize,
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

        // ===== LAYER 3: CENTER DIVIDER LINE =====
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0xFF000000))
        )

    }
}

/**
 * Renders one half of a digit card. Uses Layout composable to measure the
 * text unconstrained, then places it as if centered in the full card height.
 * The parent clip shows only the top or bottom half.
 */
@Composable
private fun DigitHalf(
    digit: Int,
    isTopHalf: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    fontSize: TextUnit = DIGIT_FONT_SIZE,
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
        // Layout measures text unconstrained, then places it centered
        // in the full card height. The parent's clip reveals only the
        // correct half.
        Layout(
            content = {
                Text(
                    text = if (digit == -1) "" else digit.toString(),
                    fontSize = fontSize,
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
                // Center text in the FULL card height
                val centerY = (cardHeightPx - placeable.height) / 2
                // For top half: place at centerY (top portion visible 0..halfHeight)
                // For bottom half: shift up by halfHeight so bottom portion is visible
                val y = if (isTopHalf) centerY else centerY - halfHeightPx
                placeable.placeRelative(x, y)
            }
        }

        // Subtle gradient for card depth
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

        // Dynamic shadow overlay on animated flaps
        if (shadowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = shadowAlpha))
            )
        }
    }
}
