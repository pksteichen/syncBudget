package com.techadvantage.budgetrak.ui.theme

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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.techadvantage.budgetrak.ui.strings.AppStrings
import com.techadvantage.budgetrak.ui.strings.EnglishStrings
import com.techadvantage.budgetrak.ui.strings.LocalStrings

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

// ── Share-intent blocking registrar ───────────────────────────────
// ⚠️ Purpose-scoped: consumed ONLY by MainViewModel.consumePendingSharedImages
// to bounce incoming shares arriving mid-dialog. Do NOT repurpose this
// registrar for other "is there a dialog open?" needs — the AdAware dialog
// wrappers auto-register EVERY dialog (pickers, confirmations, Add/Edit
// forms alike), so any other consumer would fire on benign popups too. If
// a new mechanism needs a different signal, add a separate registrar.
//
// Default is a no-op so previews without a provider still render.
val LocalShareBlockingDialogRegistrar = staticCompositionLocalOf<(Boolean) -> Unit> { { } }

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
                    painter = androidx.compose.ui.res.painterResource(id = com.techadvantage.budgetrak.R.mipmap.ic_launcher_foreground),
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

// ── In-tree dialog overlay system ───────────────────────────────────────
//
// Replaces the per-dialog `androidx.compose.ui.window.Dialog` (separate
// Android window) with an in-tree overlay rendered inside the main Activity
// window. Eliminates the Compose-Dialog window-stacking issue that absorbed
// taps on the visible-but-behind ad bar — AdMob's `NativeAdView` in the
// main window now receives ad clicks normally even while a dialog is open.
//
// Architecture:
//   • AdAwareDialogState holds a list of active dialog entries.
//   • Provided as LocalAdAwareDialogState at SyncBudgetTheme level.
//   • AdAwareDialog (call-site, unchanged signature) registers/unregisters
//     an entry via DisposableEffect — renders no UI of its own.
//   • AdAwareDialogHost (placed once inside SyncBudgetTheme just below the
//     ad bar) iterates the state and draws each entry as a dim layer +
//     centered content. Last entry = top of stack.
//   • Back press handled by per-entry BackHandler in the host (Compose's
//     stack semantics close the topmost dialog first).
//   • Scrim taps absorbed by a no-op clickable (no accidental dismiss).
//   • IME push-up via `.imePadding()` on the content wrapper.

/** State holder for active dialog overlays. One per SyncBudgetTheme call. */
class AdAwareDialogState {
    internal val activeDialogs = mutableStateListOf<AdAwareDialogEntry>()
    // Monotonically increasing sequence so the host can sort entries by
    // registration order regardless of list insertion order. Defends against
    // future re-keying around a screen with stacked dialogs flipping Z-order.
    internal val nextSequence = java.util.concurrent.atomic.AtomicLong(0)
}

/** Single active dialog entry. Identity-based equality (each call site
 *  creates a fresh instance), used as composition key in the host.
 *  [sequence] determines Z-order — higher = drawn later = on top. */
class AdAwareDialogEntry internal constructor(
    val sequence: Long,
    val onDismissRequest: () -> Unit,
    val content: @Composable () -> Unit,
)

// Defensive default: callers outside SyncBudgetTheme (e.g., WidgetTransactionActivity
// uses its own MaterialTheme) get a no-op fallback state instead of a crash. The
// fallback's entries are never rendered because there's no AdAwareDialogHost in those
// trees — AdAwareDialog calls become silent no-ops, with a logcat warning on first use.
private val FallbackAdAwareDialogState = AdAwareDialogState()
val LocalAdAwareDialogState = staticCompositionLocalOf<AdAwareDialogState> {
    android.util.Log.w("AdAwareDialog",
        "LocalAdAwareDialogState not provided — AdAwareDialog calls outside SyncBudgetTheme " +
        "will not render. Wrap your tree in SyncBudgetTheme or provide the local manually.")
    FallbackAdAwareDialogState
}

/**
 * Drop-in replacement for `androidx.compose.ui.window.Dialog` that renders
 * as an in-tree overlay inside the main Activity window — no separate
 * Android window means no window-stacking issues that absorb taps on the
 * visible-but-behind ad bar. AdMob's `NativeAdView` in the main window
 * receives ad clicks normally while this dialog is open.
 *
 * Existing signature is preserved so the ~13 call sites need no change.
 * `properties` is retained for API compatibility but ignored — the few
 * relevant bits (dismissOnBackPress / dismissOnClickOutside) are
 * reimplemented in [AdAwareDialogHost].
 *
 * Behavior preserved from the previous Compose-Dialog implementation:
 *   • Scrim-tap does NOT dismiss (no clickable scrim handler).
 *   • Back-press DOES dismiss (via BackHandler in the host).
 *   • IME push-up via `.imePadding()` in the host's content wrapper.
 *   • Share-intent blocking via LocalShareBlockingDialogRegistrar.
 *
 * Caller invokes conditionally: `if (showDialog) AdAwareDialog(...)`.
 * Disappearance is automatic when the conditional flips and the
 * composable leaves composition — DisposableEffect's onDispose unregisters.
 */
@Composable
fun AdAwareDialog(
    onDismissRequest: () -> Unit,
    @Suppress("UNUSED_PARAMETER")
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val state = LocalAdAwareDialogState.current
    val shareBlockingRegistrar = LocalShareBlockingDialogRegistrar.current
    // Keep latest callbacks live across recompositions — DisposableEffect's
    // captured `entry` would otherwise hold the FIRST onDismissRequest /
    // content, missing any updates the caller pushes via recomposition.
    val latestDismiss = rememberUpdatedState(onDismissRequest)
    val latestContent = rememberUpdatedState(content)
    DisposableEffect(Unit) {
        val entry = AdAwareDialogEntry(
            sequence = state.nextSequence.getAndIncrement(),
            onDismissRequest = { latestDismiss.value() },
            content = { latestContent.value() },
        )
        state.activeDialogs.add(entry)
        shareBlockingRegistrar(true)
        onDispose {
            state.activeDialogs.remove(entry)
            shareBlockingRegistrar(false)
        }
    }
}

/**
 * Renders every active AdAwareDialog entry as a dim overlay + centered
 * content layer. Place once in the layout below the ad bar so dialogs
 * draw above screen content while the ad bar above stays visible and
 * tappable. SyncBudgetTheme places this automatically; manual use
 * isn't required.
 *
 * Stacked dialogs render in registration order — last added is on top.
 * Back press closes the topmost only (Compose BackHandler stack
 * semantics).
 */
@Composable
fun AdAwareDialogHost() {
    val state = LocalAdAwareDialogState.current
    // Sort by sequence (registration order) — defends Z-order from any
    // future composition re-keying that might re-insert entries in source
    // order rather than original-creation order.
    state.activeDialogs.sortedBy { it.sequence }.forEach { entry ->
        // `key(entry)` so each entry gets its own composition slot —
        // BackHandler/remember calls inside scope to that entry's
        // lifecycle, not collapsed when the list reorders.
        key(entry) {
            BackHandler(enabled = true) { entry.onDismissRequest() }
            Box(modifier = Modifier.fillMaxSize()) {
                // Dim layer; clickable-no-op absorbs taps so they don't
                // leak to anything underneath, but doesn't dismiss.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* no-op: scrim taps must not dismiss the dialog */ }
                )
                // Content centered, IME-aware.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    entry.content()
                }
            }
        }
    }
}

/**
 * Drop-in scrollable content for a `DropdownMenu` / `ExposedDropdownMenu`
 * that needs bidirectional scroll affordance. Our own `ScrollState` lets
 * `PulsingScrollArrows` do its thing; the `heightIn(max = maxHeight)`
 * cap prevents the content from exceeding the outer popup and keeps
 * scroll ownership inside this Box. Short lists wrap to content size,
 * so small dropdowns don't bloat.
 *
 * Usage:
 * ```
 * ExposedDropdownMenu(expanded = …, onDismissRequest = …) {
 *     ScrollableDropdownContent {
 *         items.forEach { DropdownMenuItem(text = …, onClick = …) }
 *     }
 * }
 * ```
 */
@Composable
fun ScrollableDropdownContent(
    maxHeight: androidx.compose.ui.unit.Dp = 280.dp,
    contentStartPadding: androidx.compose.ui.unit.Dp = 32.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.heightIn(max = maxHeight)) {
        // Indent content start so it clears the scroll-arrow column on the
        // left (arrow icon is 24dp wide at start=12dp → ends at x≈36dp;
        // 32dp content padding + DropdownMenuItem's own ~16dp internal
        // padding puts text at ~48dp, leaving a visual gap).
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(start = contentStartPadding)
        ) {
            content()
        }
        PulsingScrollArrows(
            scrollState = scrollState,
            topPadding = 4.dp,
            bottomPadding = 4.dp,
        )
    }
}

/**
 * Bidirectional scroll affordance: pulsing up-arrow at top-start when
 * content can scroll up, pulsing down-arrow at bottom-start when content
 * can scroll down. Drop into any `Box` containing the scrollable body —
 * no modifier needed; alignments and paddings are managed internally. The
 * up-arrow is the key accessibility win for users with enlarged system
 * font: content that fit in one screen at default font size now scrolls,
 * and users need both directions indicated.
 *
 * Standard paddings: top `36.dp` drops the up-arrow just below the
 * DialogHeader title line; bottom `50.dp` leaves room for the footer
 * buttons. Override only if the containing layout has a different
 * header height or footer safe area.
 */
@Composable
fun BoxScope.PulsingScrollArrows(
    scrollState: ScrollState,
    topPadding: androidx.compose.ui.unit.Dp = 36.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 50.dp,
) {
    val canScrollUp by remember { derivedStateOf { scrollState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { scrollState.canScrollForward } }

    if (canScrollUp) {
        val transition = rememberInfiniteTransition(label = "arrowsUp")
        val offsetY by transition.animateFloat(
            initialValue = 0f,
            targetValue = -6f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "upBounce"
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = topPadding)
                .size(24.dp)
                .offset(y = offsetY.dp)
        )
    }
    if (canScrollDown) {
        val transition = rememberInfiniteTransition(label = "arrowsDown")
        val offsetY by transition.animateFloat(
            initialValue = 0f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "downBounce"
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = bottomPadding)
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
    val arrowScrollState = bodyScrollState ?: scrollState  // for PulsingScrollArrows when content manages own scroll

    AdAwareDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f),
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
                    // Body — scrollable so content is accessible when keyboard is open.
                    // When the caller manages its own scroll (scrollable = false,
                    // bodyScrollState = null), vertical padding here would sit
                    // OUTSIDE the caller's scrollable region, wasting visible
                    // space at the top and bottom of long lists. Apply horizontal-
                    // only padding in that case; the caller is expected to add
                    // its own top/bottom spacing inside the inner scroll.
                    if (text != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .then(if (bodyScrollState != null) Modifier.verticalScroll(bodyScrollState) else Modifier)
                                .padding(
                                    horizontal = 20.dp,
                                    vertical = if (bodyScrollState != null) 20.dp else 0.dp,
                                )
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
                    PulsingScrollArrows(scrollState = arrowScrollState)
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
    val adAwareDialogState = remember { AdAwareDialogState() }

    CompositionLocalProvider(
        LocalSyncBudgetColors provides customColors,
        LocalStrings provides strings,
        LocalAppToast provides appToastState,
        LocalAdBannerHeight provides adBannerHeight,
        LocalAdAwareDialogState provides adAwareDialogState,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SyncBudgetTypography
        ) {
            Box {
                content()
                // In-tree dialog overlay layer. Positioned below status bar
                // + ad banner (top) and above the navigation bar (bottom) so
                // the ad bar above stays visible AND tappable (AdMob
                // NativeAdView in the main window receives clicks normally)
                // and dialog content (e.g., the category picker opened from
                // the transaction dialog) doesn't extend behind the system
                // nav bar. Renders one dim+content layer per active
                // AdAwareDialog entry; stack order = registration order.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = adBannerHeight)
                ) {
                    AdAwareDialogHost()
                }
                AppToast(appToastState)
            }
        }
    }
}
