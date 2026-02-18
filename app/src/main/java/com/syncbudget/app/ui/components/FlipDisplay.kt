package com.syncbudget.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncbudget.app.sound.FlipSoundPlayer
import com.syncbudget.app.ui.theme.FlipFontFamily
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import kotlin.math.abs

val CURRENCY_OPTIONS = listOf("$", "€", "£", "¥", "₹", "₩", "Fr")

val CURRENCY_DECIMALS = mapOf(
    "$" to 2,
    "€" to 2,
    "£" to 2,
    "¥" to 0,
    "₹" to 2,
    "₩" to 0,
    "Fr" to 2
)

private const val CARD_ASPECT = 1.5f
private val GAP = 5.dp
private val DOT_WIDTH = 10.dp
private val FRAME_H_PAD = 16.dp
private val FRAME_V_PAD = 20.dp
private val MAX_CARD_WIDTH = 72.dp

fun buildSignCurrencyValues(currencies: List<String>): List<String> {
    val values = mutableListOf<String>()
    for (c in currencies) {
        values.add(c)
        // Multi-char symbols: split "-" on top line, symbol on bottom line
        if (c.length > 1) {
            values.add("-\n$c")
        } else {
            values.add("-$c")
        }
    }
    return values
}

@Composable
fun FlipDisplay(
    amount: Int,
    isNegative: Boolean,
    currencySymbol: String,
    digitCount: Int,
    decimalPlaces: Int,
    soundPlayer: FlipSoundPlayer,
    modifier: Modifier = Modifier,
    bottomLabel: String? = null
) {
    val colors = LocalSyncBudgetColors.current
    val absAmount = abs(amount)
    val hasDecimals = decimalPlaces > 0

    // Split amount into whole and decimal parts
    val divisor = pow10(decimalPlaces)
    val wholePart = absAmount / divisor
    val decimalPart = absAmount % divisor

    val wholeDigits = mutableListOf<Int>()
    var remaining = wholePart
    for (i in 0 until digitCount) {
        wholeDigits.add(0, remaining % 10)
        remaining /= 10
    }
    // Replace leading zeros with -1 (blank card), keeping at least the last digit
    val firstNonZero = wholeDigits.indexOfFirst { it != 0 }
    val blankUntil = if (firstNonZero == -1) wholeDigits.size - 1 else firstNonZero
    for (i in 0 until blankUntil) {
        wholeDigits[i] = -1
    }

    val decimalDigits = mutableListOf<Int>()
    var decRemaining = decimalPart
    for (i in 0 until decimalPlaces) {
        decimalDigits.add(0, decRemaining % 10)
        decRemaining /= 10
    }

    // Combined sign+currency value
    val signCurrencyValue = if (isNegative) {
        if (currencySymbol.length > 1) "-\n$currencySymbol" else "-$currencySymbol"
    } else {
        currencySymbol
    }
    val signCurrencyValues = remember(CURRENCY_OPTIONS) {
        buildSignCurrencyValues(CURRENCY_OPTIONS)
    }

    // Count of full-width card slots:
    // 1 sign+currency + digitCount whole digits + decimalPlaces decimal digits
    val fullCardCount = 1 + digitCount + decimalPlaces
    // Dot is bare text on the Solari background, not a card slot
    val totalItems = fullCardCount + if (hasDecimals) 1 else 0
    val gapCount = totalItems - 1

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.displayBackground)
            .border(
                width = 1.dp,
                color = colors.displayBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = FRAME_H_PAD, vertical = FRAME_V_PAD),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val dotSpace = if (hasDecimals) DOT_WIDTH else 0.dp
        val availableWidth = maxWidth - GAP * gapCount - dotSpace
        val computedCardWidth = availableWidth / fullCardCount.toFloat()
        val cardWidth = if (computedCardWidth < MAX_CARD_WIDTH) computedCardWidth else MAX_CARD_WIDTH
        val cardHeight = cardWidth * CARD_ASPECT

        // Scale font sizes proportionally to card width
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val digitFontSize = with(density) { (cardWidthPx * 1.11f).toSp() }
        val currencyFontSize = with(density) { (cardWidthPx * 0.55f).toSp() }
        val dotFontSize = with(density) { (cardWidthPx * 0.9f).toSp() }
        // Line height to push "-" and "Fr" toward top/bottom of their halves
        val currencyLineHeight = with(density) { (cardHeightPx * 0.55f).toSp() }

        val isDark = isSystemInDarkTheme()
        val dotColor = if (isDark) colors.cardText else colors.cardBackground

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(GAP),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Combined sign + currency card
                FlipChar(
                    targetValue = signCurrencyValue,
                    values = signCurrencyValues,
                    onFlipSound = { soundPlayer.playClack() },
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    fontSize = currencyFontSize,
                    lineHeight = currencyLineHeight
                )

                // Whole digit cards
                wholeDigits.forEach { digit ->
                    FlipDigit(
                        targetDigit = digit,
                        onFlipSound = { soundPlayer.playClack() },
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        fontSize = digitFontSize
                    )
                }

                // Decimal point + decimal digits
                if (hasDecimals) {
                    DecimalDot(
                        cardHeight = cardHeight,
                        fontSize = dotFontSize,
                        color = dotColor
                    )

                    decimalDigits.forEach { digit ->
                        FlipDigit(
                            targetDigit = digit,
                            onFlipSound = { soundPlayer.playClack() },
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            fontSize = digitFontSize
                        )
                    }
                }
            }

            if (bottomLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = bottomLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.cardText.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun pow10(n: Int): Int {
    var result = 1
    repeat(n) { result *= 10 }
    return result
}

@Composable
private fun DecimalDot(
    cardHeight: Dp,
    fontSize: TextUnit,
    color: Color
) {
    Box(
        modifier = Modifier
            .width(DOT_WIDTH)
            .height(cardHeight),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = ".",
            fontSize = fontSize,
            color = color,
            fontFamily = FlipFontFamily,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
