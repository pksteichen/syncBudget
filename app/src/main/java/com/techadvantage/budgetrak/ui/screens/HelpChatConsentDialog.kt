package com.techadvantage.budgetrak.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.techadvantage.budgetrak.ui.strings.LocalStrings
import com.techadvantage.budgetrak.ui.theme.AdAwareDialog
import com.techadvantage.budgetrak.ui.theme.DialogHeader
import com.techadvantage.budgetrak.ui.theme.DialogPrimaryButton
import com.techadvantage.budgetrak.ui.theme.DialogSecondaryButton
import com.techadvantage.budgetrak.ui.theme.LocalSyncBudgetColors
import com.techadvantage.budgetrak.ui.theme.PulsingScrollArrows
import com.techadvantage.budgetrak.ui.theme.dialogFooterColor

/**
 * One-time consent gate for the Help Chat assistant. Cancel returns the
 * user to the help page without recording consent; Accept persists consent
 * (caller flips the Privacy-section checkbox) and opens the chat dialog.
 */
@Composable
fun HelpChatConsentDialog(
    onCancel: () -> Unit,
    onAccept: () -> Unit,
) {
    val S = LocalStrings.current
    val customColors = LocalSyncBudgetColors.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val linkInteraction = remember { MutableInteractionSource() }

    AdAwareDialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Box {
                Column {
                    DialogHeader(title = S.helpChat.consentTitle)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = S.helpChat.consentBody,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            val linkText = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = customColors.surfaceHeader,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = TextDecoration.Underline,
                                    )
                                ) {
                                    append(S.helpChat.consentLink)
                                }
                            }
                            Text(
                                text = linkText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable(
                                        interactionSource = linkInteraction,
                                        indication = null,
                                    ) {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(S.helpChat.consentUrl),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        runCatching { context.startActivity(intent) }
                                    },
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(dialogFooterColor())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DialogSecondaryButton(onClick = onCancel) {
                                Text(S.helpChat.consentCancel)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            DialogPrimaryButton(onClick = onAccept) {
                                Text(S.helpChat.consentAccept)
                            }
                        }
                    }
                }
                PulsingScrollArrows(scrollState = scrollState)
            }
        }
    }
}
