package com.techadvantage.budgetrak.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.techadvantage.budgetrak.BuildConfig
import com.techadvantage.budgetrak.data.HelpChatMessage
import com.techadvantage.budgetrak.data.HelpChatStore
import com.techadvantage.budgetrak.data.HelpChatUploader
import com.techadvantage.budgetrak.data.ai.HelpChatGeminiService
import com.techadvantage.budgetrak.ui.strings.LocalStrings
import com.techadvantage.budgetrak.ui.theme.AdAwareDialog
import com.techadvantage.budgetrak.ui.theme.DialogHeader
import com.techadvantage.budgetrak.ui.theme.DialogPrimaryButton
import com.techadvantage.budgetrak.ui.theme.DialogSecondaryButton
import com.techadvantage.budgetrak.ui.theme.LocalSyncBudgetColors
import com.techadvantage.budgetrak.ui.theme.PulsingScrollArrows
import com.techadvantage.budgetrak.ui.theme.dialogFooterColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpChatDialog(
    onDismissRequest: () -> Unit,
) {
    val S = LocalStrings.current
    val customColors = LocalSyncBudgetColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val messages by HelpChatStore.messages.collectAsState()
    val dailyCount by HelpChatStore.dailyCount.collectAsState()
    var input by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    val historyScroll = rememberScrollState()
    // Cap state — recomputes whenever dailyCount changes, AND whenever the
    // dialog recomposes after midnight (the underlying store rolls the
    // counter forward inside canSendToday/remainingToday at next send).
    val dailyCap = remember(dailyCount) { HelpChatStore.getDailyCap(context) }
    val limitReached by remember(dailyCount, dailyCap) {
        derivedStateOf { dailyCount >= dailyCap }
    }
    val canSend by remember {
        derivedStateOf { input.isNotBlank() && !isThinking && !limitReached }
    }

    LaunchedEffect(Unit) { HelpChatStore.load(context) }

    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty() || isThinking) {
            historyScroll.animateScrollTo(historyScroll.maxValue)
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = customColors.surfaceHeader,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        focusedLabelColor = customColors.surfaceHeader,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )

    AdAwareDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column {
                DialogHeader(title = S.helpChat.title)

                // Scrollable chat history. Pulsing scroll arrows surface
                // whenever the history overflows.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(historyScroll)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (messages.isEmpty()) {
                            Text(
                                text = S.helpChat.emptyBody,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                        } else {
                            messages.forEach { msg ->
                                ChatMessageRow(
                                    msg = msg,
                                    youLabel = S.helpChat.youLabel,
                                    botLabel = S.helpChat.botLabel,
                                )
                            }
                        }
                        if (isThinking) {
                            ChatMessageRow(
                                msg = HelpChatMessage(
                                    timestamp = 0L,
                                    fromUser = false,
                                    text = S.helpChat.thinkingLabel,
                                ),
                                youLabel = S.helpChat.youLabel,
                                botLabel = S.helpChat.botLabel,
                            )
                        }
                    }
                    PulsingScrollArrows(
                        scrollState = historyScroll,
                        topPadding = 4.dp,
                        bottomPadding = 4.dp,
                    )
                }

                // Debug-only QA status line: today's count vs cap, and
                // how long ago the in-chat Play Store review prompt last
                // fired (or "never"). Stripped from release builds at
                // compile time via the BuildConfig.DEBUG conditional.
                if (BuildConfig.DEBUG) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val lastReviewMs = remember(dailyCount) {
                        HelpChatStore.lastReviewPromptAt(context)
                    }
                    val lastReviewText = remember(lastReviewMs) {
                        if (lastReviewMs == 0L) "never"
                        else formatRelativeAge(System.currentTimeMillis() - lastReviewMs)
                    }
                    Text(
                        text = "Debug · $dailyCount of $dailyCap today · last review: $lastReviewText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                // Pinned 3-line input field above the button bar.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        enabled = !limitReached,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 88.dp, max = 88.dp),
                        placeholder = {
                            Text(
                                text = if (limitReached) S.helpChat.dailyLimitHint
                                       else S.helpChat.inputHint,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        },
                        maxLines = 3,
                        minLines = 3,
                        colors = textFieldColors,
                        shape = RoundedCornerShape(8.dp),
                    )
                }

                // Footer button bar — Email (left) + Exit + Clear + Send (right).
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(dialogFooterColor())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DialogSecondaryButton(onClick = {
                            launchEmail(
                                context = context,
                                subject = S.helpChat.emailSubject,
                                intro = S.helpChat.emailBodyIntro,
                                youLabel = S.helpChat.youLabel,
                                botLabel = S.helpChat.botLabel,
                                messages = messages,
                                chatId = HelpChatStore.chatId.value,
                            )
                        }) {
                            Text(S.helpChat.btnEmail)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        DialogSecondaryButton(onClick = { onDismissRequest() }) {
                            Text(S.helpChat.btnExit)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        DialogSecondaryButton(onClick = {
                            // Capture the snapshot BEFORE clearing so the
                            // async upload still has data even after the
                            // local wipe lands. Failures log in DEBUG only.
                            val snapshotId = HelpChatStore.chatId.value
                            val snapshotMsgs = HelpChatStore.messages.value
                            scope.launch {
                                if (snapshotId != null) {
                                    HelpChatUploader.uploadNow(
                                        context, snapshotId, snapshotMsgs,
                                    )
                                }
                            }
                            scope.launch { HelpChatStore.clear(context) }
                            input = ""
                        }) {
                            Text(S.helpChat.btnClear)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        DialogPrimaryButton(
                            onClick = {
                                val trimmed = input.trim()
                                if (trimmed.isNotEmpty() && !isThinking) {
                                    val ctx = context
                                    val errorReply = S.helpChat.errorReply
                                    val reviewPromptText = S.helpChat.reviewPromptText
                                    input = ""
                                    isThinking = true
                                    scope.launch {
                                        HelpChatStore.addMessage(
                                            ctx, fromUser = true, text = trimmed,
                                        )
                                        val result = HelpChatGeminiService.reply(
                                            context = ctx,
                                            history = HelpChatStore.messages.value,
                                            latestUserMessage = trimmed,
                                        )
                                        val reply = result.getOrNull()
                                        if (reply != null) {
                                            HelpChatStore.addMessage(
                                                context = ctx,
                                                fromUser = false,
                                                text = reply.text,
                                                sentiment = reply.sentiment,
                                            )
                                            // Only successful Gemini
                                            // replies count against the
                                            // daily cap — transient
                                            // failures don't burn quota.
                                            HelpChatStore.incrementDailyCount(ctx)

                                            // If the user just gave us
                                            // strong positive feedback
                                            // AND we haven't asked them
                                            // for a review in the last
                                            // 2 days, append a tappable
                                            // Play Store request as a
                                            // SECOND bot message. The
                                            // model didn't write this
                                            // text — it's a localized
                                            // template — so it doesn't
                                            // carry a sentiment score.
                                            if (HelpChatStore.shouldShowReviewPrompt(ctx, reply.sentiment)) {
                                                HelpChatStore.addMessage(
                                                    context = ctx,
                                                    fromUser = false,
                                                    text = reviewPromptText,
                                                    isReviewPrompt = true,
                                                )
                                                HelpChatStore.markReviewPromptShown(ctx)
                                            }
                                        } else {
                                            HelpChatStore.addMessage(
                                                context = ctx,
                                                fromUser = false,
                                                text = errorReply,
                                            )
                                        }
                                        isThinking = false
                                        // Fire-and-forget upload: debounced
                                        // 5-min window per HelpChatUploader.
                                        HelpChatUploader.uploadIfStale(ctx)
                                    }
                                }
                            },
                            enabled = canSend,
                        ) {
                            Text(S.helpChat.btnSend)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageRow(msg: HelpChatMessage, youLabel: String, botLabel: String) {
    val customColors = LocalSyncBudgetColors.current
    val context = LocalContext.current
    val isUser = msg.fromUser
    val isReview = msg.isReviewPrompt
    // Review-prompt bubble uses an accent-tinted background so users
    // perceive it as a distinct CTA, and the whole surface is clickable
    // — tapping anywhere on the bubble opens the Play Store listing.
    val surfaceColor = when {
        isUser   -> customColors.surfaceHeader
        isReview -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else     -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isUser   -> customColors.surfaceHeaderText
        else     -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        val surfaceMod = Modifier
            .wrapContentHeight()
            .then(
                if (isReview) {
                    Modifier.clickable { openPlayStoreListing(context) }
                } else {
                    Modifier
                }
            )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = surfaceColor,
            modifier = surfaceMod,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // In debug builds, bot labels get a `[N]` suffix where N
                // is the Gemini-assigned sentiment score (1-10) for the
                // user message that prompted this reply. Stripped from
                // release builds. User messages never show a score.
                val displayLabel = when {
                    isUser -> youLabel
                    BuildConfig.DEBUG && msg.sentiment != null -> "$botLabel [${msg.sentiment}]"
                    else -> botLabel
                }
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
            }
        }
    }
}

/**
 * Compact relative-age formatter for the debug-only "last review:"
 * status line. Returns "Xs / Xm / Xh / Xd ago" depending on magnitude.
 */
private fun formatRelativeAge(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 60     -> "${seconds}s ago"
        seconds < 3600   -> "${seconds / 60}m ago"
        seconds < 86400  -> "${seconds / 3600}h ago"
        else             -> "${seconds / 86400}d ago"
    }
}

/**
 * Open the BudgeTrak Play Store listing for a user-requested review.
 * Tries the Play app deep link first (`market://...`); falls back to
 * the https listing if the Play app isn't installed (rare on stock
 * Android, common on degoogled devices). Debug builds strip the
 * `.debug` package suffix so the link points at the production listing.
 */
private fun openPlayStoreListing(context: android.content.Context) {
    val packageName = context.packageName.removeSuffix(".debug")
    val marketUri = Uri.parse("market://details?id=$packageName")
    val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")

    val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = runCatching { context.startActivity(marketIntent) }.isSuccess
    if (!opened) {
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(webIntent) }
    }
}

/**
 * Open the user's email client with a prefilled help-chat follow-up. The
 * full transcript is appended after [intro]; the chatId is included as
 * the last line so Tech Advantage support can look up the Firestore doc
 * (assuming the user previously granted consent for upload).
 *
 * mailto: bodies have a practical length limit (~4 KB on most Android
 * clients). We truncate at 3500 chars to leave headroom for the URL
 * encoding overhead — if the chat is longer, we replace the middle with
 * a marker and let support pull the rest from Firestore.
 */
private fun launchEmail(
    context: android.content.Context,
    subject: String,
    intro: String,
    youLabel: String,
    botLabel: String,
    messages: List<HelpChatMessage>,
    chatId: String?,
) {
    val transcript = buildString {
        append(intro)
        append("\n\n")
        messages.forEach { m ->
            val role = if (m.fromUser) youLabel else botLabel
            append(role).append(": ").append(m.text).append("\n\n")
        }
        if (chatId != null) {
            append("[chat-id: ").append(chatId).append("]")
        }
    }
    val capped = if (transcript.length > 3500) {
        transcript.substring(0, 3500) + "\n\n[…transcript truncated…]"
    } else {
        transcript
    }
    val uri = Uri.parse(
        "mailto:support@techadvantageapps.com" +
            "?subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(capped)
    )
    val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
