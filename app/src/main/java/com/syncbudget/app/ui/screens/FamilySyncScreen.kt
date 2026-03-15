package com.syncbudget.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import com.syncbudget.app.ui.theme.AdAwareAlertDialog
import com.syncbudget.app.ui.theme.AdAwareDialog
import com.syncbudget.app.ui.theme.DialogStyle
import com.syncbudget.app.ui.theme.DialogPrimaryButton
import com.syncbudget.app.ui.theme.DialogSecondaryButton
import com.syncbudget.app.ui.theme.DialogDangerButton
import com.syncbudget.app.ui.theme.DialogWarningButton
import com.syncbudget.app.ui.theme.dialogHeaderColor
import com.syncbudget.app.ui.theme.dialogHeaderTextColor
import com.syncbudget.app.ui.theme.dialogFooterColor
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncbudget.app.data.sync.AdminClaim
import com.syncbudget.app.data.sync.DeviceInfo
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalAppToast
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
    isSubscriber: Boolean = false,
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
    orphanedDeviceIds: Set<String> = emptySet(),
    deviceRoster: Map<String, String> = emptyMap(),
    onSaveDeviceRoster: (Map<String, String>) -> Unit = {},
    onPurgeStaleRoster: () -> Unit = {},
    staleDays: Int = 0,
    pendingAdminClaim: AdminClaim? = null,
    onClaimAdmin: () -> Unit = {},
    onObjectClaim: () -> Unit = {},
    syncErrorMessage: String? = null,
    syncProgressMessage: String? = null,
    onCreateGroup: (nickname: String) -> Unit,
    onJoinGroup: (pairingCode: String, nickname: String) -> Unit,
    onLeaveGroup: () -> Unit,
    onDissolveGroup: () -> Unit,
    onSyncNow: () -> Unit,
    onGeneratePairingCode: () -> Unit,
    generatedPairingCode: String?,
    onDismissPairingCode: () -> Unit,
    onRenameDevice: (deviceId: String, newName: String) -> Unit = { _, _ -> },
    onRemoveDevice: (deviceId: String) -> Unit = {},
    onHelpClick: () -> Unit = {},
    onBack: () -> Unit
) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current
    val context = LocalContext.current
    val toastState = LocalAppToast.current
    val clipboardManager = LocalClipboardManager.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var createNicknameInput by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTargetDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var removeTargetDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }
    var joinNicknameInput by remember { mutableStateOf("") }
    var showJoinWarning by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDissolveConfirm by remember { mutableStateOf(false) }
    var showTimezoneDialog by remember { mutableStateOf(false) }
    var showRepairDialog by remember { mutableStateOf(false) }

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
                        onClick = {
                            if (isSubscriber) showCreateDialog = true
                            else toastState.show(S.settings.subscribeToAccess)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isSubscriber
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
                        var copyBtnYPx by remember { mutableIntStateOf(0) }
                        IconButton(
                            onClick = {
                                groupId?.let {
                                    clipboardManager.setText(AnnotatedString(it))
                                    toastState.show(S.sync.pairingCodeCopied, copyBtnYPx)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                                .onGloballyPositioned { copyBtnYPx = it.positionInWindow().y.toInt() }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = S.sync.copy,
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
                            IconButton(
                                onClick = {
                                    onPurgeStaleRoster()
                                    showRepairDialog = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Build,
                                    contentDescription = S.sync.repairAttributions,
                                    tint = if (orphanedDeviceIds.isNotEmpty()) Color(0xFFF44336)
                                           else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                    @OptIn(ExperimentalFoundationApi::class)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isAdmin) Modifier.combinedClickable(
                                    onClick = {
                                        renameTargetDevice = device
                                        renameInput = device.deviceName
                                        showRenameDialog = true
                                    },
                                    onLongClick = {
                                        // Only allow removing non-admin, non-self devices
                                        if (device.deviceId != localDeviceId && !device.isAdmin) {
                                            removeTargetDevice = device
                                            showRemoveDialog = true
                                        }
                                    }
                                ) else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Canvas(modifier = Modifier.size(12.dp)) {
                                drawCircle(color = deviceSyncColor(device.lastSeen))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
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
                    // Claim Admin button (only when no pending claim, requires subscription)
                    if (pendingAdminClaim == null) {
                        item {
                            OutlinedButton(
                                onClick = {
                                    if (isSubscriber) onClaimAdmin()
                                    else toastState.show(S.settings.subscribeToAccess)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isSubscriber
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

        // Create group dialog (nickname input)
        if (showCreateDialog) {
            AdAwareAlertDialog(
                onDismissRequest = {
                    showCreateDialog = false
                    createNicknameInput = ""
                },
                title = { Text(S.sync.createGroupTitle) },
                text = {
                    OutlinedTextField(
                        value = createNicknameInput,
                        onValueChange = { createNicknameInput = it.take(20) },
                        label = { Text(S.sync.enterNickname) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    DialogPrimaryButton(
                        onClick = {
                            onCreateGroup(createNicknameInput.trim())
                            showCreateDialog = false
                            createNicknameInput = ""
                        },
                        enabled = createNicknameInput.isNotBlank()
                    ) {
                        Text(S.common.ok)
                    }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = {
                        showCreateDialog = false
                        createNicknameInput = ""
                    }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        // Join dialog
        if (showJoinDialog) {
            AdAwareAlertDialog(
                onDismissRequest = {
                    showJoinDialog = false
                    joinCodeInput = ""
                    joinNicknameInput = ""
                },
                title = { Text(S.sync.joinGroup) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = joinNicknameInput,
                            onValueChange = { joinNicknameInput = it.take(20) },
                            label = { Text(S.sync.enterNickname) },
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
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
                    }
                },
                confirmButton = {
                    DialogPrimaryButton(
                        onClick = {
                            showJoinDialog = false
                            showJoinWarning = true
                        },
                        enabled = joinCodeInput.length == 6 && joinNicknameInput.isNotBlank()
                    ) {
                        Text(S.common.ok)
                    }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = {
                        showJoinDialog = false
                        joinCodeInput = ""
                        joinNicknameInput = ""
                    }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        // Pairing code display dialog
        if (generatedPairingCode != null) {
            AdAwareAlertDialog(
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
                    var copyDlgBtnYPx by remember { mutableIntStateOf(0) }
                    DialogPrimaryButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(generatedPairingCode))
                            toastState.show(S.sync.pairingCodeCopied, copyDlgBtnYPx)
                        },
                        modifier = Modifier.onGloballyPositioned { copyDlgBtnYPx = it.positionInWindow().y.toInt() }
                    ) {
                        Text(S.sync.copy)
                    }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = onDismissPairingCode) {
                        Text(S.common.close)
                    }
                }
            )
        }

        // Leave confirmation
        if (showLeaveConfirm) {
            AdAwareAlertDialog(
                onDismissRequest = { showLeaveConfirm = false },
                style = DialogStyle.DANGER,
                title = { Text(S.sync.leaveGroup) },
                text = { Text(S.sync.confirmLeave) },
                confirmButton = {
                    DialogDangerButton(onClick = {
                        onLeaveGroup()
                        showLeaveConfirm = false
                    }) {
                        Text(S.common.ok)
                    }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = { showLeaveConfirm = false }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        // Dissolve confirmation
        if (showDissolveConfirm) {
            AdAwareAlertDialog(
                onDismissRequest = { showDissolveConfirm = false },
                style = DialogStyle.DANGER,
                title = { Text(S.sync.dissolveGroup) },
                text = { Text(S.sync.confirmDissolve) },
                confirmButton = {
                    DialogDangerButton(onClick = {
                        onDissolveGroup()
                        showDissolveConfirm = false
                    }) {
                        Text(S.common.ok)
                    }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = { showDissolveConfirm = false }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        // Join warning dialog
        if (showJoinWarning) {
            AdAwareAlertDialog(
                onDismissRequest = {
                    showJoinWarning = false
                    joinCodeInput = ""
                    joinNicknameInput = ""
                },
                style = DialogStyle.WARNING,
                title = { Text(S.sync.joinWarningTitle) },
                text = { Text(S.sync.joinWarningBody) },
                confirmButton = {
                    DialogWarningButton(onClick = {
                        onJoinGroup(joinCodeInput, joinNicknameInput.trim())
                        showJoinWarning = false
                        joinCodeInput = ""
                        joinNicknameInput = ""
                    }) {
                        Text(S.common.ok)
                    }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = {
                        showJoinWarning = false
                        joinCodeInput = ""
                        joinNicknameInput = ""
                    }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        // Rename device dialog (admin only)
        if (showRenameDialog && renameTargetDevice != null) {
            AdAwareAlertDialog(
                onDismissRequest = {
                    showRenameDialog = false
                    renameTargetDevice = null
                    renameInput = ""
                },
                title = { Text(S.sync.renameDevice) },
                text = {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it.take(20) },
                        label = { Text(S.sync.enterNickname) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    DialogPrimaryButton(
                        onClick = {
                            val target = renameTargetDevice!!
                            onRenameDevice(target.deviceId, renameInput.trim())
                            showRenameDialog = false
                            renameTargetDevice = null
                            renameInput = ""
                        },
                        enabled = renameInput.isNotBlank()
                    ) {
                        Text(S.common.ok)
                    }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = {
                        showRenameDialog = false
                        renameTargetDevice = null
                        renameInput = ""
                    }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        if (showRemoveDialog && removeTargetDevice != null) {
            val target = removeTargetDevice!!
            val displayName = target.deviceName.ifEmpty { target.deviceId.take(8) }
            AdAwareAlertDialog(
                onDismissRequest = {
                    showRemoveDialog = false
                    removeTargetDevice = null
                },
                title = { Text(S.sync.removeDeviceTitle) },
                text = {
                    Text(S.sync.removeDeviceMessage(displayName))
                },
                confirmButton = {
                    DialogDangerButton(onClick = {
                        onRemoveDevice(target.deviceId)
                        showRemoveDialog = false
                        removeTargetDevice = null
                    }) {
                        Text(S.sync.removeDeviceConfirm)
                    }
                },
                dismissButton = {
                    DialogSecondaryButton(onClick = {
                        showRemoveDialog = false
                        removeTargetDevice = null
                    }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        // Timezone picker dialog
        if (showTimezoneDialog) {
            AdAwareAlertDialog(
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
                    DialogSecondaryButton(onClick = { showTimezoneDialog = false }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        // Repair attributions dialog
        if (showRepairDialog) {
            // Combine roster + orphaned IDs + current devices for complete list
            val allDeviceIds = remember(deviceRoster, orphanedDeviceIds, devices, localDeviceId) {
                val ids = linkedSetOf<String>()
                devices.forEach { if (it.deviceId != localDeviceId) ids.add(it.deviceId) }
                deviceRoster.keys.forEach { ids.add(it) }
                orphanedDeviceIds.forEach { ids.add(it) }
                ids.toList()
            }
            val currentDeviceIds = remember(devices) { devices.map { it.deviceId }.toSet() }
            val editableRoster = remember(allDeviceIds, deviceRoster, devices) {
                mutableStateOf(
                    allDeviceIds.associateWith { id ->
                        deviceRoster[id]
                            ?: devices.find { it.deviceId == id }?.deviceName
                            ?: ""
                    }
                )
            }
            val headerBg = dialogHeaderColor()
            val headerTxt = dialogHeaderTextColor()
            val footerBg = dialogFooterColor()
            val scrollState = rememberScrollState()

            AdAwareDialog(onDismissRequest = { showRepairDialog = false }) {
                val focusManager = LocalFocusManager.current
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .imePadding(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { focusManager.clearFocus() }
                    ) {
                        // Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(headerBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            CompositionLocalProvider(LocalContentColor provides headerTxt) {
                                Text(
                                    S.sync.repairAttributions,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = headerTxt
                                )
                            }
                        }
                        // Scrollable body
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(scrollState)
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                S.sync.repairAttributionsBody,
                                style = MaterialTheme.typography.bodySmall
                            )
                            allDeviceIds.forEach { deviceId ->
                                val isOrphaned = deviceId in orphanedDeviceIds
                                val isCurrent = deviceId in currentDeviceIds
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            deviceId.take(8),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isOrphaned) Color(0xFFF44336)
                                                   else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                        if (isCurrent) {
                                            Canvas(modifier = Modifier.size(8.dp)) {
                                                drawCircle(Color(0xFF4CAF50))
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = editableRoster.value[deviceId] ?: "",
                                        onValueChange = { newVal ->
                                            editableRoster.value = editableRoster.value.toMutableMap().apply {
                                                put(deviceId, newVal)
                                            }
                                        },
                                        label = { Text(S.sync.nicknameHint) },
                                        singleLine = true,
                                        colors = textFieldColors,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        // Footer
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(footerBg)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DialogSecondaryButton(onClick = { showRepairDialog = false }) {
                                    Text(S.common.cancel, maxLines = 1)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                DialogPrimaryButton(onClick = {
                                    val result = editableRoster.value.filterValues { it.isNotBlank() }
                                    onSaveDeviceRoster(result)
                                    showRepairDialog = false
                                }) {
                                    Text(S.common.save, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sync progress overlay
        if (syncProgressMessage != null) {
            AdAwareDialog(
                onDismissRequest = { /* non-dismissable while in progress */ }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = syncProgressMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
