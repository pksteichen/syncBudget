package com.syncbudget.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncbudget.app.data.sync.AdminClaim
import com.syncbudget.app.data.sync.DeviceInfo
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

private val COMMON_TIMEZONES = listOf(
    "America/New_York",
    "America/Chicago",
    "America/Denver",
    "America/Los_Angeles",
    "America/Anchorage",
    "Pacific/Honolulu",
    "America/Toronto",
    "America/Vancouver",
    "America/Mexico_City",
    "America/Sao_Paulo",
    "America/Argentina/Buenos_Aires",
    "Europe/London",
    "Europe/Paris",
    "Europe/Berlin",
    "Europe/Madrid",
    "Europe/Rome",
    "Europe/Moscow",
    "Asia/Dubai",
    "Asia/Kolkata",
    "Asia/Shanghai",
    "Asia/Tokyo",
    "Asia/Seoul",
    "Asia/Singapore",
    "Australia/Sydney",
    "Pacific/Auckland"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySyncScreen(
    isConfigured: Boolean,
    groupId: String?,
    isAdmin: Boolean,
    deviceName: String,
    localDeviceId: String,
    devices: List<DeviceInfo>,
    syncStatus: String,
    lastSyncTime: String?,
    familyTimezone: String = "",
    onTimezoneChange: (String) -> Unit = {},
    showAttribution: Boolean = false,
    onShowAttributionChange: (Boolean) -> Unit = {},
    staleDays: Int = 0,
    pendingAdminClaim: AdminClaim? = null,
    onClaimAdmin: () -> Unit = {},
    onObjectClaim: () -> Unit = {},
    syncErrorMessage: String? = null,
    onCreateGroup: () -> Unit,
    onJoinGroup: (pairingCode: String) -> Unit,
    onLeaveGroup: () -> Unit,
    onDissolveGroup: () -> Unit,
    onSyncNow: () -> Unit,
    onGeneratePairingCode: () -> Unit,
    generatedPairingCode: String?,
    onDismissPairingCode: () -> Unit,
    onHelpClick: () -> Unit = {},
    onBack: () -> Unit
) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }
    var showJoinWarning by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDissolveConfirm by remember { mutableStateOf(false) }
    var showTimezoneDialog by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.sync.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = S.common.back,
                            tint = customColors.headerText
                        )
                    }
                },
                actions = {
                    if (isConfigured) {
                        IconButton(onClick = onHelpClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Help,
                                contentDescription = S.common.help,
                                tint = customColors.headerText
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = customColors.headerBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isConfigured) {
                // Not configured state
                item {
                    Text(
                        text = S.sync.familySyncDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }

                item {
                    OutlinedButton(
                        onClick = onCreateGroup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(S.sync.createGroup)
                    }
                }

                item {
                    Text(
                        text = S.sync.createGroupDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    OutlinedButton(
                        onClick = { showJoinDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(S.sync.joinGroup)
                    }
                }

                item {
                    Text(
                        text = S.sync.joinGroupDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                // Configured state
                // Sync status row
                item {
                    val statusColor = when (syncStatus) {
                        "synced" -> Color(0xFF4CAF50)
                        "syncing" -> Color(0xFFFFEB3B)
                        "stale" -> Color(0xFFFF9800)
                        "error" -> Color(0xFFF44336)
                        else -> Color(0xFF9E9E9E)
                    }
                    val statusText = when (syncStatus) {
                        "synced" -> S.sync.syncStatusSynced
                        "syncing" -> S.sync.syncStatusSyncing
                        "stale" -> S.sync.syncStatusStale
                        "error" -> S.sync.syncStatusError
                        else -> S.sync.syncStatusOff
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Canvas(modifier = Modifier.size(12.dp)) {
                                drawCircle(color = statusColor)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (lastSyncTime != null) {
                                    Text(
                                        text = S.sync.lastSynced(lastSyncTime),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                if (syncStatus == "error" && syncErrorMessage != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val errorText = when (syncErrorMessage) {
                                        "removed_from_group" -> S.sync.errorRemovedFromGroup
                                        "group_deleted" -> S.sync.errorGroupDeleted
                                        "encryption_error" -> S.sync.errorEncryption
                                        "sync_blocked_stale" -> S.sync.staleBlocked
                                        else -> syncErrorMessage
                                    }
                                    Text(
                                        text = errorText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFF44336)
                                    )
                                }
                                if (staleDays >= 60) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val staleText = when {
                                        staleDays >= 90 -> S.sync.staleBlocked
                                        staleDays >= 85 -> S.sync.staleWarning85
                                        staleDays >= 75 -> S.sync.staleWarning75
                                        else -> S.sync.staleWarning60
                                    }
                                    val staleColor = when {
                                        staleDays >= 85 -> Color(0xFFF44336)
                                        staleDays >= 75 -> Color(0xFFFF5722)
                                        else -> Color(0xFFFF9800)
                                    }
                                    Text(
                                        text = staleText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = staleColor
                                    )
                                }
                            }
                        }
                    }
                }

                // Group ID
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${S.sync.groupId}: ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = groupId?.take(8)?.plus("...") ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                groupId?.let {
                                    clipboardManager.setText(AnnotatedString(it))
                                    Toast.makeText(context, S.sync.pairingCodeCopied, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Family Timezone
                item {
                    val displayTz = familyTimezone.ifEmpty { java.util.TimeZone.getDefault().id }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${S.sync.familyTimezone}: ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = displayTz,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (isAdmin) {
                            TextButton(onClick = { showTimezoneDialog = true }) {
                                Text(S.common.reset)
                            }
                        }
                    }
                }

                // Attribution toggle (admin-only)
                if (isAdmin) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = S.sync.showAttributionLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = showAttribution,
                                onCheckedChange = onShowAttributionChange
                            )
                        }
                    }
                }

                // Sync Now button
                item {
                    OutlinedButton(
                        onClick = onSyncNow,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = syncStatus != "syncing"
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(S.sync.syncNow)
                    }
                }

                // Device roster
                item {
                    Text(
                        text = S.sync.deviceRoster,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(devices) { device ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = device.deviceName.ifEmpty { device.deviceId.take(8) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (device.deviceId == localDeviceId) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(${S.sync.thisDevice})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                                if (device.isAdmin) {
                                    Text(
                                        text = S.sync.admin,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Admin claim notification
                if (pendingAdminClaim != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFF3E0),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (pendingAdminClaim.claimantDeviceId == localDeviceId) {
                                    Text(
                                        text = S.sync.claimPending,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = S.sync.claimBy(pendingAdminClaim.claimantName),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = onObjectClaim,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(S.sync.objectClaim)
                                    }
                                }
                            }
                        }
                    }
                }

                // Admin actions
                if (isAdmin) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        OutlinedButton(
                            onClick = onGeneratePairingCode,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(S.sync.generateCode)
                        }
                    }
                    item {
                        Text(
                            text = S.sync.pairingCodeExpiry,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = { showDissolveConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = S.sync.dissolveGroup,
                                color = Color(0xFFF44336)
                            )
                        }
                    }
                } else {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    // Claim Admin button (only when no pending claim)
                    if (pendingAdminClaim == null) {
                        item {
                            OutlinedButton(
                                onClick = onClaimAdmin,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(S.sync.claimAdmin)
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showLeaveConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = S.sync.leaveGroup,
                                color = Color(0xFFF44336)
                            )
                        }
                    }
                }
            }
        }

        // Join dialog
        if (showJoinDialog) {
            AlertDialog(
                onDismissRequest = {
                    showJoinDialog = false
                    joinCodeInput = ""
                },
                title = { Text(S.sync.joinGroup) },
                text = {
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = { joinCodeInput = it.uppercase().take(6) },
                        label = { Text(S.sync.enterPairingCode) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showJoinDialog = false
                            showJoinWarning = true
                        },
                        enabled = joinCodeInput.length == 6
                    ) {
                        Text(S.common.ok)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showJoinDialog = false
                        joinCodeInput = ""
                    }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        // Pairing code display dialog
        if (generatedPairingCode != null) {
            AlertDialog(
                onDismissRequest = onDismissPairingCode,
                title = { Text(S.sync.pairingCode) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = generatedPairingCode,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 6.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = S.sync.pairingCodeExpiry,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(generatedPairingCode))
                        Toast.makeText(context, S.sync.pairingCodeCopied, Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissPairingCode) {
                        Text(S.common.close)
                    }
                }
            )
        }

        // Leave confirmation
        if (showLeaveConfirm) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirm = false },
                title = { Text(S.sync.leaveGroup) },
                text = { Text(S.sync.confirmLeave) },
                confirmButton = {
                    TextButton(onClick = {
                        onLeaveGroup()
                        showLeaveConfirm = false
                    }) {
                        Text(S.common.ok, color = Color(0xFFF44336))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveConfirm = false }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        // Dissolve confirmation
        if (showDissolveConfirm) {
            AlertDialog(
                onDismissRequest = { showDissolveConfirm = false },
                title = { Text(S.sync.dissolveGroup) },
                text = { Text(S.sync.confirmDissolve) },
                confirmButton = {
                    TextButton(onClick = {
                        onDissolveGroup()
                        showDissolveConfirm = false
                    }) {
                        Text(S.common.ok, color = Color(0xFFF44336))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDissolveConfirm = false }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        // Join warning dialog
        if (showJoinWarning) {
            AlertDialog(
                onDismissRequest = {
                    showJoinWarning = false
                    joinCodeInput = ""
                },
                title = { Text(S.sync.joinWarningTitle) },
                text = { Text(S.sync.joinWarningBody) },
                confirmButton = {
                    TextButton(onClick = {
                        onJoinGroup(joinCodeInput)
                        showJoinWarning = false
                        joinCodeInput = ""
                    }) {
                        Text(S.common.ok, color = Color(0xFFF44336))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showJoinWarning = false
                        joinCodeInput = ""
                    }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        // Timezone picker dialog
        if (showTimezoneDialog) {
            AlertDialog(
                onDismissRequest = { showTimezoneDialog = false },
                title = { Text(S.sync.selectTimezone) },
                text = {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(COMMON_TIMEZONES) { tz ->
                            val isCurrent = tz == familyTimezone || (familyTimezone.isEmpty() && tz == java.util.TimeZone.getDefault().id)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onTimezoneChange(tz)
                                        showTimezoneDialog = false
                                    }
                            ) {
                                Text(
                                    text = tz,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showTimezoneDialog = false }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }
    }
}
