package com.techadvantage.budgetrak.ui.screens

import androidx.compose.foundation.background
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
import com.techadvantage.budgetrak.data.HelpChatMessage
import com.techadvantage.budgetrak.data.HelpChatStore
import com.techadvantage.budgetrak.data.HelpChatUploader
import com.techadvantage.budgetrak.ui.strings.LocalStrings
import com.techadvantage.budgetrak.ui.theme.AdAwareDialog
import com.techadvantage.budgetrak.ui.theme.DialogHeader
import com.techadvantage.budgetrak.ui.theme.DialogPrimaryButton
import com.techadvantage.budgetrak.ui.theme.DialogSecondaryButton
import com.techadvantage.budgetrak.ui.theme.LocalAppToast
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
    val toastState = LocalAppToast.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val messages by HelpChatStore.messages.collectAsState()
    var input by remember { mutableStateOf("") }

    val historyScroll = rememberScrollState()
    val canSend by remember { derivedStateOf { input.isNotBlank() } }

    LaunchedEffect(Unit) { HelpChatStore.load(context) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) historyScroll.animateScrollTo(historyScroll.maxValue)
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
                    }
                    PulsingScrollArrows(
                        scrollState = historyScroll,
                        topPadding = 4.dp,
                        bottomPadding = 4.dp,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 88.dp, max = 88.dp),
                        placeholder = {
                            Text(
                                text = S.helpChat.inputHint,
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
                            toastState.show("Email support — coming soon")
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
                                if (trimmed.isNotEmpty()) {
                                    val ctx = context
                                    scope.launch {
                                        HelpChatStore.addMessage(ctx, fromUser = true, text = trimmed)
                                        // Backend wiring lands in a later
                                        // phase; append a placeholder bot
                                        // reply so the UI shell can be
                                        // exercised end-to-end.
                                        HelpChatStore.addMessage(
                                            ctx,
                                            fromUser = false,
                                            text = "(AI backend not yet connected)",
                                        )
                                    }
                                    input = ""
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
    val isUser = msg.fromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) customColors.surfaceHeader
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.wrapContentHeight(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = if (isUser) youLabel else botLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) customColors.surfaceHeaderText
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) customColors.surfaceHeaderText
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
