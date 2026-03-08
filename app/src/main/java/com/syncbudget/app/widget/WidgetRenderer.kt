package com.syncbudget.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.abs

data class RenderResult(
    val bitmap: Bitmap,
    val leftCardEdge: Int,
    val rightCardEdge: Int,
    val cardBottomY: Int
)

/**
 * Renders the Solari flip-display as a Bitmap for the widget.
 * Mirrors the Compose FlipDisplay appearance using Android Canvas.
 */
object WidgetRenderer {

    private const val CARD_ASPECT = 1.5f
    private const val CARD_CORNER = 8f
    private const val GAP = 5f
    private const val DOT_WIDTH = 10f
    private const val FRAME_H_PAD = 16f
    private const val FRAME_V_PAD = 20f
    private const val FRAME_CORNER = 16f

    // Dark theme colors (matching app DarkCardBackground/DarkCardText)
    private const val DARK_CARD_BG = 0xFF1A1A1A.toInt()
    private const val DARK_CARD_TEXT = 0xFFE8D5A0.toInt()
    private const val DARK_DIVIDER = 0xFF333333.toInt()

    // Light theme colors (matching app LightCardBackground/LightCardText)
    private const val LIGHT_CARD_BG = 0xFF305880.toInt()
    private const val LIGHT_CARD_TEXT = 0xFFFFFFFF.toInt()
    private const val LIGHT_DIVIDER = 0xFF264868.toInt()

    // Logo tint color (light mode blue)
    const val LOGO_TINT = 0xFF305880.toInt()

    fun render(
        widgetWidth: Int,
        maxHeight: Int,
        isDarkMode: Boolean,
        isPaidUser: Boolean,
        amount: Double,
        currencySymbol: String,
        decimalPlaces: Int,
        minDigitCount: Int = 3
    ): RenderResult {
        val cardBg = if (isDarkMode) DARK_CARD_BG else LIGHT_CARD_BG
        val cardText = if (isDarkMode) DARK_CARD_TEXT else LIGHT_CARD_TEXT
        val dividerColor = if (isDarkMode) DARK_DIVIDER else LIGHT_DIVIDER

        val isNegative = amount < 0
        val absAmountScaled = (abs(amount) * pow10(decimalPlaces)).toLong()
        val divisor = pow10(decimalPlaces).toLong()
        val wholePart = (absAmountScaled / divisor).toInt()
        val decimalPart = (absAmountScaled % divisor).toInt()

        // Use app's digit count as minimum, expand if amount requires more
        val digitCount = when {
            wholePart >= 100000 -> maxOf(6, minDigitCount)
            wholePart >= 10000 -> maxOf(5, minDigitCount)
            wholePart >= 1000 -> maxOf(4, minDigitCount)
            else -> minDigitCount
        }

        val wholeDigits = mutableListOf<Int>()
        var remaining = wholePart
        for (i in 0 until digitCount) {
            wholeDigits.add(0, remaining % 10)
            remaining /= 10
        }
        val firstNonZero = wholeDigits.indexOfFirst { it != 0 }
        val blankUntil = if (firstNonZero == -1) wholeDigits.size - 1 else firstNonZero
        for (i in 0 until blankUntil) wholeDigits[i] = -1

        val decimalDigits = mutableListOf<Int>()
        var decRemaining = decimalPart
        for (i in 0 until decimalPlaces) {
            decimalDigits.add(0, decRemaining % 10)
            decRemaining /= 10
        }

        val hasDecimals = decimalPlaces > 0
        val signCurrency = if (isNegative) "-$currencySymbol" else currencySymbol

        // Calculate card dimensions based on width, capped by max height
        val fullCardCount = 1 + digitCount + decimalPlaces
        val totalItems = fullCardCount + if (hasDecimals) 1 else 0
        val gapCount = totalItems - 1
        val dotSpace = if (hasDecimals) DOT_WIDTH else 0f
        val availableWidth = widgetWidth - 2 * FRAME_H_PAD - gapCount * GAP - dotSpace
        val maxCardHeight = (maxHeight.toFloat() - 2 * FRAME_V_PAD).coerceAtLeast(20f)
        val cardWidth = (availableWidth / fullCardCount)
            .coerceAtMost(maxCardHeight / CARD_ASPECT)
        val cardHeight = (cardWidth * CARD_ASPECT).coerceAtMost(maxCardHeight)

        // Bitmap is only as tall as the cards need
        val bitmapHeight = (FRAME_V_PAD + cardHeight + FRAME_V_PAD).toInt()
            .coerceAtMost(maxHeight).coerceAtLeast(20)

        val bitmap = Bitmap.createBitmap(widgetWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Total row width for centering
        val totalRowWidth = fullCardCount * cardWidth + gapCount * GAP + dotSpace

        // Top-align the row with padding
        val rowTop = FRAME_V_PAD
        var x = (widgetWidth - totalRowWidth) / 2f

        val textPaint = Paint().apply {
            color = cardText
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        // Track card edges for button alignment
        val leftCardEdge = x.toInt()

        // Draw sign+currency card
        drawCard(canvas, x, rowTop, cardWidth, cardHeight, signCurrency,
            cardWidth * 0.45f, textPaint, cardBg, cardText, dividerColor)
        x += cardWidth + GAP

        // Draw whole digits
        for (digit in wholeDigits) {
            val text = if (digit == -1) "" else digit.toString()
            drawCard(canvas, x, rowTop, cardWidth, cardHeight, text,
                cardWidth * 0.9f, textPaint, cardBg, cardText, dividerColor)
            x += cardWidth + GAP
        }

        // Decimal dot + decimal digits
        if (hasDecimals) {
            // Draw dot
            textPaint.textSize = cardWidth * 0.7f
            textPaint.color = cardText
            val dotX = x + DOT_WIDTH / 2f
            val dotY = rowTop + cardHeight - 4f
            canvas.drawText(".", dotX, dotY, textPaint)
            x += DOT_WIDTH + GAP

            for (digit in decimalDigits) {
                drawCard(canvas, x, rowTop, cardWidth, cardHeight, digit.toString(),
                    cardWidth * 0.9f, textPaint, cardBg, cardText, dividerColor)
                x += cardWidth + GAP
            }
        }

        // x has advanced past the last card + GAP, so subtract GAP to get right edge
        val rightCardEdge = (x - GAP).toInt()

        val cardBottomY = (rowTop + cardHeight).toInt()

        // Overlay message for non-paid users
        if (!isPaidUser) {
            val overlayPaint = Paint().apply {
                color = if (isDarkMode) 0xCCFFFFFF.toInt() else 0xCC000000.toInt()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
                // Scale font to fit across the cards
                textSize = (totalRowWidth * 0.07f).coerceIn(14f, 36f)
            }
            val overlayX = widgetWidth / 2f
            val overlayY = rowTop + cardHeight / 2f
            // Semi-transparent backdrop
            val backdropPaint = Paint().apply {
                color = if (isDarkMode) 0x99000000.toInt() else 0x99FFFFFF.toInt()
                isAntiAlias = true
            }
            val metrics = overlayPaint.fontMetrics
            val textH = metrics.descent - metrics.ascent
            val pad = textH * 0.4f
            canvas.drawRoundRect(
                RectF(leftCardEdge.toFloat(), overlayY - textH - pad,
                    rightCardEdge.toFloat(), overlayY + pad),
                8f, 8f, backdropPaint
            )
            canvas.drawText("Upgrade for full widget", overlayX, overlayY, overlayPaint)
        }

        return RenderResult(bitmap, leftCardEdge, rightCardEdge, cardBottomY)
    }

    private fun drawCard(
        canvas: Canvas, x: Float, y: Float,
        width: Float, height: Float,
        text: String, fontSize: Float,
        paint: Paint,
        cardBg: Int, cardText: Int, dividerColor: Int
    ) {
        val halfHeight = height / 2f

        // Top half
        val cardPaint = Paint().apply { color = cardBg; isAntiAlias = true }
        val topRect = RectF(x, y, x + width, y + halfHeight)
        canvas.drawRoundRect(
            RectF(x, y, x + width, y + halfHeight + CARD_CORNER),
            CARD_CORNER, CARD_CORNER, cardPaint
        )
        // Square off bottom corners of top half
        canvas.drawRect(x, y + halfHeight - CARD_CORNER, x + width, y + halfHeight, cardPaint)

        // Bottom half
        canvas.drawRoundRect(
            RectF(x, y + halfHeight, x + width, y + height),
            CARD_CORNER, CARD_CORNER, cardPaint
        )
        // Square off top corners of bottom half
        canvas.drawRect(x, y + halfHeight, x + width, y + halfHeight + CARD_CORNER, cardPaint)

        // Subtle gradient overlays
        val topGrad = Paint().apply {
            shader = LinearGradient(
                x, y, x, y + halfHeight,
                0x0AFFFFFF, 0x00000000, Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        canvas.drawRect(topRect, topGrad)

        val bottomGrad = Paint().apply {
            shader = LinearGradient(
                x, y + halfHeight, x, y + height,
                0x00000000, 0x08000000.toInt(), Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        canvas.drawRect(RectF(x, y + halfHeight, x + width, y + height), bottomGrad)

        // Text centered in card
        if (text.isNotEmpty()) {
            paint.textSize = fontSize
            paint.color = cardText
            val textX = x + width / 2f
            val metrics = paint.fontMetrics
            val textHeight = metrics.descent - metrics.ascent
            val textY = y + (height - textHeight) / 2f - metrics.ascent
            canvas.drawText(text, textX, textY, paint)
        }

        // Flip divider line — drawn last so it renders over the text
        val divPaint = Paint().apply {
            color = 0xFF000000.toInt()
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawLine(x, y + halfHeight, x + width, y + halfHeight, divPaint)
    }

    private fun pow10(n: Int): Int {
        var result = 1
        repeat(n) { result *= 10 }
        return result
    }
}
