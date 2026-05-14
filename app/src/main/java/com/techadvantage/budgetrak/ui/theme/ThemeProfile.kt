package com.techadvantage.budgetrak.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One of the ten themable color roles per light/dark mode. The following are
 * intentionally NOT here — semantically or policy-locked:
 *   • Sync-indicator state colors (green/blue/yellow/red/grey) — convention
 *   • Dialog Danger (red) / Warning (orange) — convention
 *   • AdMob "Ad" badge yellow (#FFCC00) + black stroke — AdMob policy
 *   • Native-ad overlay backdrop (#B3000000) — readability backstop
 *   • UpgradeBadge yellow/black in InHouseAd — mirrors Ad badge convention
 *
 * onPrimary (text/icons on Primary buttons + CTA + price pills) is auto-derived
 * from Primary's luminance inside SyncBudgetTheme — no slot needed.
 */
data class ThemeColorSet(
    val primary: Color,
    val cardBackground: Color,
    val cardText: Color,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val displayBackground: Color,
    val displayBorder: Color,
    val incomeGreen: Color,
    val expenseRed: Color,
)

data class ThemeProfile(
    val name: String,
    val isBuiltIn: Boolean,
    val light: ThemeColorSet,
    val dark: ThemeColorSet,
    val chartLight: List<Color>,
    val chartDark: List<Color>,
) {
    init {
        require(chartLight.size == 12) { "ThemeProfile.chartLight must contain exactly 12 colors" }
        require(chartDark.size == 12) { "ThemeProfile.chartDark must contain exactly 12 colors" }
    }
}

object BuiltInThemes {
    // Default light/dark base — shared across all built-in profiles.
    private val DEFAULT_LIGHT = ThemeColorSet(
        primary = LightPrimary,
        cardBackground = LightCardBackground,
        cardText = LightCardText,
        background = LightBackground,
        surface = LightSurface,
        onSurface = LightOnSurface,
        displayBackground = LightDisplayBackground,
        displayBorder = LightDisplayBorder,
        incomeGreen = IncomeGreen,
        expenseRed = ExpenseRed,
    )
    private val DEFAULT_DARK = ThemeColorSet(
        primary = DarkPrimary,
        cardBackground = DarkCardBackground,
        cardText = DarkCardText,
        background = DarkBackground,
        surface = DarkSurface,
        onSurface = DarkCardText,
        displayBackground = DarkDisplayBackground,
        displayBorder = DarkDisplayBorder,
        incomeGreen = IncomeGreen,
        expenseRed = ExpenseRed,
    )

    private val BRIGHT_LIGHT = listOf(
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFF44336), Color(0xFFFF9800),
        Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFFEB3B), Color(0xFF795548),
        Color(0xFFE91E63), Color(0xFF607D8B), Color(0xFF8BC34A), Color(0xFF3F51B5),
    )
    private val BRIGHT_DARK = listOf(
        Color(0xFF1B5E20), Color(0xFF0D47A1), Color(0xFF7F1D1D), Color(0xFF8B3A00),
        Color(0xFF4A148C), Color(0xFF004D40), Color(0xFF8C6D00), Color(0xFF3E2723),
        Color(0xFF6A0035), Color(0xFF263238), Color(0xFF33691E), Color(0xFF1A237E),
    )
    private val PASTEL_LIGHT = listOf(
        Color(0xFFA5D6A7), Color(0xFF90CAF9), Color(0xFFEF9A9A), Color(0xFFFFCC80),
        Color(0xFFCE93D8), Color(0xFF80DEEA), Color(0xFFFFF59D), Color(0xFFBCAAA4),
        Color(0xFFF48FB1), Color(0xFFB0BEC5), Color(0xFFC5E1A5), Color(0xFF9FA8DA),
    )
    private val PASTEL_DARK = listOf(
        Color(0xFF2E5E30), Color(0xFF1E4976), Color(0xFF6D3434), Color(0xFF7A5020),
        Color(0xFF5A3070), Color(0xFF1A5055), Color(0xFF6B5E1A), Color(0xFF4A3530),
        Color(0xFF6B2845), Color(0xFF37474F), Color(0xFF3A5420), Color(0xFF2A3570),
    )
    private val SUNSET_LIGHT = listOf(
        Color(0xFF4D1D46), Color(0xFFDC7049), Color(0xFFEBB865), Color(0xFF35506E),
        Color(0xFF8F5050), Color(0xFF563060), Color(0xFF313967), Color(0xFFC25D5D),
        Color(0xFFD4956A), Color(0xFF2D6B6B), Color(0xFF7A5A3A), Color(0xFF8B7BA8),
    )
    private val SUNSET_DARK = listOf(
        Color(0xFF2E1129), Color(0xFF8A4530), Color(0xFF8A6D2E), Color(0xFF1E3045),
        Color(0xFF5A3232), Color(0xFF331C39), Color(0xFF1C2140), Color(0xFF7A3A3A),
        Color(0xFF7A5540), Color(0xFF1A4040), Color(0xFF4A3622), Color(0xFF524968),
    )

    val DEFAULT = ThemeProfile(
        name = "Default",
        isBuiltIn = true,
        light = DEFAULT_LIGHT,
        dark = DEFAULT_DARK,
        chartLight = BRIGHT_LIGHT,
        chartDark = BRIGHT_DARK,
    )

    val PASTEL = ThemeProfile(
        name = "Pastel",
        isBuiltIn = true,
        light = DEFAULT_LIGHT,
        dark = DEFAULT_DARK,
        chartLight = PASTEL_LIGHT,
        chartDark = PASTEL_DARK,
    )

    val SUNSET = ThemeProfile(
        name = "Sunset",
        isBuiltIn = true,
        light = DEFAULT_LIGHT,
        dark = DEFAULT_DARK,
        chartLight = SUNSET_LIGHT,
        chartDark = SUNSET_DARK,
    )

    val ALL = listOf(DEFAULT, PASTEL, SUNSET)
}

/** Kept for backwards-compat with earlier checkpoint code; resolves to BuiltInThemes.DEFAULT. */
object DefaultThemeProfile {
    val DEFAULT: ThemeProfile get() = BuiltInThemes.DEFAULT
}
