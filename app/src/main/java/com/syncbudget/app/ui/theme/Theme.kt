package com.syncbudget.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syncbudget.app.ui.strings.AppStrings
import com.syncbudget.app.ui.strings.EnglishStrings
import com.syncbudget.app.ui.strings.LocalStrings

data class SyncBudgetColors(
    val headerBackground: Color,
    val headerText: Color,
    val cardBackground: Color,
    val cardText: Color,
    val displayBackground: Color,
    val displayBorder: Color,
    val userCategoryIconTint: Color
)

val LocalSyncBudgetColors = staticCompositionLocalOf {
    SyncBudgetColors(
        headerBackground = DarkHeaderBackground,
        headerText = DarkHeaderText,
        cardBackground = DarkCardBackground,
        cardText = DarkCardText,
        displayBackground = DarkDisplayBackground,
        displayBorder = DarkDisplayBorder,
        userCategoryIconTint = LightCardBackground
    )
}

/** Height of the ad banner (0.dp when hidden for paid users). */
val LocalAdBannerHeight = compositionLocalOf { 0.dp }

/**
 * Drop-in replacement for Dialog that avoids overlapping the ad banner.
 * Wraps content in a full-size Box with status-bar + ad-banner top padding,
 * centering the dialog content in the remaining space.
 */
@Composable
fun AdAwareDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    ),
    content: @Composable () -> Unit
) {
    val adPadding = LocalAdBannerHeight.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = adPadding),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
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
    strings: AppStrings = EnglishStrings,
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
            displayBorder = DarkDisplayBorder,
            userCategoryIconTint = LightCardBackground
        )
    } else {
        SyncBudgetColors(
            headerBackground = LightCardBackground,
            headerText = LightCardText,
            cardBackground = LightCardBackground,
            cardText = LightCardText,
            displayBackground = LightDisplayBackground,
            displayBorder = LightDisplayBorder,
            userCategoryIconTint = LightCardBackground
        )
    }

    CompositionLocalProvider(
        LocalSyncBudgetColors provides customColors,
        LocalStrings provides strings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SyncBudgetTypography,
            content = content
        )
    }
}
