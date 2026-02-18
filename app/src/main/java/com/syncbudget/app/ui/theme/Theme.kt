package com.syncbudget.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class SyncBudgetColors(
    val headerBackground: Color,
    val headerText: Color,
    val cardBackground: Color,
    val cardText: Color,
    val displayBackground: Color,
    val displayBorder: Color
)

val LocalSyncBudgetColors = staticCompositionLocalOf {
    SyncBudgetColors(
        headerBackground = DarkHeaderBackground,
        headerText = DarkHeaderText,
        cardBackground = DarkCardBackground,
        cardText = DarkCardText,
        displayBackground = DarkDisplayBackground,
        displayBorder = DarkDisplayBorder
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkCardText,
    onSurface = DarkCardText
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface
)

@Composable
fun SyncBudgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val customColors = if (darkTheme) {
        SyncBudgetColors(
            headerBackground = DarkCardBackground,
            headerText = DarkCardText,
            cardBackground = DarkCardBackground,
            cardText = DarkCardText,
            displayBackground = DarkDisplayBackground,
            displayBorder = DarkDisplayBorder
        )
    } else {
        SyncBudgetColors(
            headerBackground = LightCardBackground,
            headerText = LightCardText,
            cardBackground = LightCardBackground,
            cardText = LightCardText,
            displayBackground = LightDisplayBackground,
            displayBorder = LightDisplayBorder
        )
    }

    CompositionLocalProvider(LocalSyncBudgetColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SyncBudgetTypography,
            content = content
        )
    }
}
