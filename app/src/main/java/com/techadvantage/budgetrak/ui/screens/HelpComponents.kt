package com.techadvantage.budgetrak.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techadvantage.budgetrak.R
import com.techadvantage.budgetrak.data.HelpChatUploader
import com.techadvantage.budgetrak.ui.strings.LocalStrings
import com.techadvantage.budgetrak.ui.theme.LocalSyncBudgetColors
import kotlinx.coroutines.launch

@Composable
internal fun HelpSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )
}

@Composable
internal fun HelpSubSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
internal fun HelpBodyText(text: String, italic: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        lineHeight = 22.sp
    )
}

@Composable
internal fun HelpBulletText(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "\u2022",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 20.sp
        )
    }
}

@Composable
internal fun HelpNumberedItem(number: Int, title: String, description: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(22.dp)
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
                append(" \u2014 $description")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 20.sp
        )
    }
}

@Composable
internal fun HelpIconRow(
    icon: ImageVector,
    label: String,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label) }
                append(" \u2014 $description")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 20.sp
        )
    }
}

@Composable
internal fun HelpIconRow(
    painter: Painter,
    label: String,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label) }
                append(" \u2014 $description")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 20.sp
        )
    }
}

/**
 * HelpIconRow overload with a composable icon slot — for cases where the
 * bullet icon is a layered drawable that can't be expressed as a single
 * ImageVector/Painter (e.g., the dashboard's Add Transaction icon which
 * stacks a receipt-body drawable with a blue-plus overlay).
 */
@Composable
internal fun HelpIconRow(
    icon: @Composable () -> Unit,
    label: String,
    description: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label) }
                append(" \u2014 $description")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 20.sp
        )
    }
}

@Composable
internal fun HelpDividerLine() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
    )
}

// ────────────────────────────────────────────────────────────────────
// Help Chat opener — shared by every help page's top app bar
// ────────────────────────────────────────────────────────────────────

/**
 * A request from somewhere in the composition to open the Help Chat.
 * Provided by [HelpChatHost]. Consumed by [HelpChatTopBarAction] (the
 * top-bar icon) and by any in-page opener (e.g. the Dashboard Help
 * page's "Chat With Our Helper" demo strip). Triggers the consent
 * dialog first if consent hasn't been granted yet.
 *
 * Default no-op makes the helper safe to call from contexts that
 * aren't wrapped in [HelpChatHost] — the icon just won't open
 * anything. We chose no-op (over `error("…")`) so that an in-app
 * preview / snapshot test doesn't have to build a host harness.
 */
val LocalHelpChatOpener = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Wraps the help-screen routing region. Holds the open/consent state,
 * provides [LocalHelpChatOpener] to descendants so any composable
 * (top-bar action, in-page CTA, etc.) can request the chat be opened
 * with a single call, and renders the [HelpChatDialog] +
 * [HelpChatConsentDialog] at its own scope so they appear over
 * whichever help page is currently routed.
 *
 * Should wrap the routing block in MainActivity so every help screen
 * (and any overlay help screen) shares the same opener and state
 * machine — opening the chat from any help page and dismissing it
 * returns the user to that same page automatically (the dialog is a
 * Compose overlay; no navigation work needed).
 */
@Composable
internal fun HelpChatHost(
    helpChatConsent: Boolean,
    onGrantHelpChatConsent: () -> Unit,
    content: @Composable () -> Unit,
) {
    var showHelpChat by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val opener: () -> Unit = {
        if (helpChatConsent) showHelpChat = true
        else showConsent = true
    }

    CompositionLocalProvider(LocalHelpChatOpener provides opener) {
        content()
    }

    if (showHelpChat) {
        HelpChatDialog(onDismissRequest = {
            // Fire-and-forget upload of the (possibly dirty) buffer
            // before dismiss. uploadIfStale no-ops when nothing has
            // changed since the last successful upload or when the
            // device is offline / unauthenticated.
            scope.launch { HelpChatUploader.uploadIfStale(context) }
            showHelpChat = false
        })
    }
    if (showConsent) {
        HelpChatConsentDialog(
            onCancel = { showConsent = false },
            onAccept = {
                onGrantHelpChatConsent()
                showConsent = false
                showHelpChat = true
            },
        )
    }
}

/**
 * The chatbot icon for the top app bar's `actions` slot on every
 * help screen. Reads the opener from [LocalHelpChatOpener] (provided
 * by [HelpChatHost]) so all help pages share the same state machine.
 */
@Composable
internal fun HelpChatTopBarAction() {
    val opener = LocalHelpChatOpener.current
    val S = LocalStrings.current
    val customColors = LocalSyncBudgetColors.current
    IconButton(onClick = opener) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_chatbot),
            contentDescription = S.helpChat.openIconDesc,
            colorFilter = ColorFilter.tint(customColors.headerText),
            modifier = Modifier.size(28.dp),
        )
    }
}
