package com.syncbudget.app.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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
    val userCategoryIconTint: Color,
    val accentTint: Color
)

val LocalSyncBudgetColors = staticCompositionLocalOf {
    SyncBudgetColors(
        headerBackground = DarkHeaderBackground,
        headerText = DarkHeaderText,
        cardBackground = DarkCardBackground,
        cardText = DarkCardText,
        displayBackground = DarkDisplayBackground,
        displayBorder = DarkDisplayBorder,
        userCategoryIconTint = LightCardBackground,
        accentTint = DarkCardText
    )
}

/** Height of the ad banner (0.dp when hidden for paid users). */
val LocalAdBannerHeight = compositionLocalOf { 0.dp }

/** Visual style for dialog header/footer. */
enum class DialogStyle { DEFAULT, DANGER, WARNING }

@Composable
fun dialogHeaderColor(style: DialogStyle = DialogStyle.DEFAULT): Color {
    val isDark = isSystemInDarkTheme()
    return when (style) {
        DialogStyle.DEFAULT -> if (isDark) Color(0xFF1B5E20) else Color(0xFF2E7D32)
        DialogStyle.DANGER -> Color(0xFFB71C1C)
        DialogStyle.WARNING -> Color(0xFFE65100)
    }
}

@Composable
fun dialogHeaderTextColor(style: DialogStyle = DialogStyle.DEFAULT): Color {
    return when (style) {
        DialogStyle.DEFAULT -> if (isSystemInDarkTheme()) Color(0xFFE8F5E9) else Color.White
        DialogStyle.DANGER -> Color(0xFFFFEBEE)
        DialogStyle.WARNING -> Color(0xFFFFF3E0)
    }
}

@Composable
fun dialogFooterColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF1A3A1A) else Color(0xFFE8F5E9)
}

@Composable
fun dialogSectionLabelColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
}

/** Green filled primary button for dialogs. */
private val CompactButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

@Composable
fun DialogPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = CompactButtonPadding,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    var lastClickTime by remember { mutableStateOf(0L) }
    Button(
        onClick = {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > 500) { lastClickTime = now; onClick() }
        },
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSystemInDarkTheme()) Color(0xFF388E3C) else Color(0xFF2E7D32),
            contentColor = Color.White
        ),
        content = content
    )
}

/** Gray filled secondary button for dialogs. */
@Composable
fun DialogSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = CompactButtonPadding,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSystemInDarkTheme()) Color(0xFF3A3A3A) else Color(0xFFE0E0E0),
            contentColor = if (isSystemInDarkTheme()) Color(0xFFCCCCCC) else Color(0xFF555555)
        ),
        content = content
    )
}

// ── App Toast ─────────────────────────────────────────────────────

val LocalAppToast = staticCompositionLocalOf { AppToastState() }

class AppToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var tapYPx by mutableIntStateOf(0)
        private set
    var counter by mutableIntStateOf(0)
        private set
    var durationMs by mutableStateOf(2500L)
        private set

    /** Show a toast near the tap that triggered it. [windowYPx] from positionInWindow(). */
    fun show(msg: String, windowYPx: Int = -1, durationMs: Long = 2500L) {
        message = msg
        tapYPx = windowYPx
        this.durationMs = durationMs
        counter++
    }

    fun dismiss() {
        message = null
    }
}

@Composable
fun AppToast(state: AppToastState) {
    val msg = state.message ?: return
    val density = LocalDensity.current
    val adBannerDp = LocalAdBannerHeight.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(state.counter) {
        if (state.message != null) {
            kotlinx.coroutines.delay(state.durationMs)
            state.dismiss()
        }
    }

    val adBannerPx = with(density) { adBannerDp.toPx() }.toInt()
    val statusBarPx = with(density) { 24.dp.toPx() }.toInt()  // approximate
    val toastHeightPx = with(density) { 48.dp.toPx() }.toInt()
    val marginPx = with(density) { 12.dp.toPx() }.toInt()
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }.toInt()
    val minY = statusBarPx + adBannerPx + marginPx  // below status bar + ad

    val usableHeight = screenHeightPx - minY
    val posY = if (state.tapYPx <= 0) {
        // No tap position provided — show at 60% of usable screen
        minY + (usableHeight * 0.6f).toInt()
    } else {
        // Try above the tap point
        val aboveY = state.tapYPx - toastHeightPx - marginPx
        val belowY = state.tapYPx + toastHeightPx + marginPx
        if (aboveY >= minY) aboveY
        else if (belowY + toastHeightPx <= screenHeightPx) belowY
        else minY
    }

    val offsetY = with(density) { posY.toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF5F5F5),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.syncbudget.app.R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = msg,
                    color = if (isDark) Color(0xFFE0E0E0) else Color(0xFF333333),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/** Red filled danger button for dialogs. */
@Composable
fun DialogDangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    var lastClickTime by remember { mutableStateOf(0L) }
    Button(
        onClick = {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > 500) { lastClickTime = now; onClick() }
        },
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFC62828),
            contentColor = Color.White
        ),
        content = content
    )
}

/** Orange filled warning button for dialogs. */
@Composable
fun DialogWarningButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    var lastClickTime by remember { mutableStateOf(0L) }
    Button(
        onClick = {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > 500) { lastClickTime = now; onClick() }
        },
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE65100),
            contentColor = Color.White
        ),
        content = content
    )
}

/** Colored header for form dialogs that use AdAwareDialog directly. */
@Composable
fun DialogHeader(title: String, style: DialogStyle = DialogStyle.DEFAULT) {
    val headerBg = dialogHeaderColor(style)
    val headerTxt = dialogHeaderTextColor(style)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = headerTxt
        )
    }
}

/** Colored footer for form dialogs that use AdAwareDialog directly. */
@Composable
fun DialogFooter(content: @Composable () -> Unit) {
    val footerBg = dialogFooterColor()
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(footerBg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        content()
    }
}

/**
 * Themed date picker dialog with green header/footer.
 * Replaces Material3 DatePickerDialog for consistent styling.
 */
@Composable
fun AdAwareDatePickerDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    AdAwareDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column {
                DialogHeader(title = title)
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    content()
                }
                DialogFooter {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dismissButton()
                        Spacer(modifier = Modifier.width(8.dp))
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * Drop-in replacement for Dialog that avoids overlapping the ad banner.
 * Disables system dim so the ad stays bright, and adds a custom dim
 * overlay only below the status-bar + ad-banner area.
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
    val appToast = LocalAppToast.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        // Disable system dim so the ad banner stays fully visible,
        // and prevent the dialog from sliding up when the keyboard opens.
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let { window ->
            SideEffect {
                window.setDimAmount(0f)
                @Suppress("DEPRECATION")
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Custom dim overlay below status bar + ad banner
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = adPadding)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismissRequest() }
            )

            // Dialog content centered below ad banner
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = adPadding),
                contentAlignment = Alignment.Center
            ) {
                content()
            }

            // Toast overlay inside dialog window
            AppToast(appToast)
        }
    }
}

/**
 * Pulsing down-arrow that appears when a scrollable area has more content below.
 * Disappears when scrolled to the bottom or when content fits without scrolling.
 */
@Composable
fun PulsingScrollArrow(scrollState: ScrollState, modifier: Modifier = Modifier) {
    val canScrollDown by remember {
        derivedStateOf { scrollState.canScrollForward }
    }
    if (canScrollDown) {
        val infiniteTransition = rememberInfiniteTransition(label = "scrollArrow")
        val offsetY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "arrowBounce"
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = modifier
                .size(24.dp)
                .offset(y = offsetY.dp)
        )
    }
}

/**
 * Drop-in replacement for AlertDialog that avoids overlapping the ad banner.
 * Uses AdAwareDialog internally so the content is positioned below the ad,
 * scrolls when content is tall, and shows a pulsing arrow when scrollable.
 * Green themed header/footer with filled buttons.
 */
@Composable
fun AdAwareAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    scrollState: ScrollState? = null,
    style: DialogStyle = DialogStyle.DEFAULT,
    scrollable: Boolean = true,  // set false if text content has its own scrollable (LazyColumn etc.)
) {
    val headerBg = dialogHeaderColor(style)
    val headerTxt = dialogHeaderTextColor(style)
    val footerBg = dialogFooterColor()

    val bodyScrollState = if (scrollable) (scrollState ?: rememberScrollState()) else null
    val arrowScrollState = bodyScrollState ?: scrollState  // for PulsingScrollArrow when content manages own scroll

    AdAwareDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .imePadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Box {
                Column {
                    // Colored header
                    if (title != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    headerBg,
                                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                                CompositionLocalProvider(LocalContentColor provides headerTxt) {
                                    title()
                                }
                            }
                        }
                    }
                    // Body — scrollable so content is accessible when keyboard is open
                    if (text != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .then(if (bodyScrollState != null) Modifier.verticalScroll(bodyScrollState) else Modifier)
                                .padding(20.dp)
                        ) {
                            text()
                        }
                    }
                    // Divider + colored footer — always visible at bottom
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(footerBg)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = CenterVertically
                        ) {
                            if (dismissButton != null) {
                                dismissButton()
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            confirmButton()
                        }
                    }
                }
                if (arrowScrollState != null) {
                    PulsingScrollArrow(
                        scrollState = arrowScrollState,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 50.dp)
                    )
                }
            }
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
    primaryContainer = Color(0xFF4A3270),
    onPrimaryContainer = Color(0xFFE8DEF8),
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface
)

@Composable
fun SyncBudgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    strings: AppStrings = EnglishStrings,
    adBannerHeight: Dp = 0.dp,
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
            userCategoryIconTint = LightCardBackground,
            accentTint = DarkCardText
        )
    } else {
        SyncBudgetColors(
            headerBackground = LightCardBackground,
            headerText = LightCardText,
            cardBackground = LightCardBackground,
            cardText = LightCardText,
            displayBackground = LightDisplayBackground,
            displayBorder = LightDisplayBorder,
            userCategoryIconTint = LightCardBackground,
            accentTint = LightCardBackground
        )
    }

    val appToastState = remember { AppToastState() }

    CompositionLocalProvider(
        LocalSyncBudgetColors provides customColors,
        LocalStrings provides strings,
        LocalAppToast provides appToastState,
        LocalAdBannerHeight provides adBannerHeight
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SyncBudgetTypography
        ) {
            Box {
                content()
                AppToast(appToastState)
            }
        }
    }
}
