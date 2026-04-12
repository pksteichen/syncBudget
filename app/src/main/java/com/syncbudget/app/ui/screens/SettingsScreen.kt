package com.syncbudget.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import com.syncbudget.app.ui.theme.LocalAppToast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import com.syncbudget.app.ui.theme.AdAwareAlertDialog
import com.syncbudget.app.ui.theme.DialogStyle
import com.syncbudget.app.ui.theme.DialogPrimaryButton
import com.syncbudget.app.ui.theme.DialogSecondaryButton
import com.syncbudget.app.ui.theme.DialogDangerButton
import com.syncbudget.app.ui.theme.DialogWarningButton
import com.syncbudget.app.ui.theme.DialogHeader
import com.syncbudget.app.ui.theme.DialogFooter
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.syncbudget.app.ui.theme.AdAwareDialog
import com.syncbudget.app.ui.theme.PulsingScrollArrow
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CATEGORY_ICON_MAP
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.getCategoryIcon
import com.syncbudget.app.ui.components.CURRENCY_OPTIONS
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import com.syncbudget.app.ui.strings.LocalStrings
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMAT_OPTIONS = listOf(
    "yyyy-MM-dd",   // 2026-02-17
    "MM/dd/yyyy",   // 02/17/2026
    "dd/MM/yyyy",   // 17/02/2026
    "MM-dd-yyyy",   // 02-17-2026
    "dd-MM-yyyy",   // 17-02-2026
    "MMM dd, yyyy", // Feb 17, 2026
    "dd MMM yyyy",  // 17 Feb 2026
    "MMMM dd, yyyy",// February 17, 2026
    "dd MMMM yyyy", // 17 February 2026
    "M/d/yyyy",     // 2/17/2026
    "d/M/yyyy"      // 17/2/2026
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currencySymbol: String,
    appLanguage: String = "en",
    onLanguageChange: (String) -> Unit = {},
    onCurrencyChange: (String) -> Unit,
    showDecimals: Boolean,
    onDecimalsChange: (Boolean) -> Unit,
    dateFormatPattern: String,
    onDateFormatChange: (String) -> Unit,
    isPaidUser: Boolean = false,
    onPaidUserChange: (Boolean) -> Unit = {},
    isSubscriber: Boolean = false,
    onSubscriberChange: (Boolean) -> Unit = {},
    subscriptionExpiry: Long = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000,
    onSubscriptionExpiryChange: (Long) -> Unit = {},
    showWidgetLogo: Boolean = true,
    onWidgetLogoChange: (Boolean) -> Unit = {},
    autoCapitalize: Boolean = true,
    onAutoCapitalizeChange: (Boolean) -> Unit = {},
    crashlyticsEnabled: Boolean = true,
    onCrashlyticsEnabledChange: (Boolean) -> Unit = {},
    matchDays: Int = 7,
    onMatchDaysChange: (Int) -> Unit = {},
    matchPercent: Double = 1.0,
    onMatchPercentChange: (Double) -> Unit = {},
    matchDollar: Int = 1,
    onMatchDollarChange: (Int) -> Unit = {},
    matchChars: Int = 5,
    onMatchCharsChange: (Int) -> Unit = {},
    categories: List<Category>,
    transactions: List<Transaction> = emptyList(),
    onAddCategory: (Category) -> Unit,
    onUpdateCategory: (Category) -> Unit = {},
    onDeleteCategory: (Category) -> Unit,
    onToggleCharted: (Category) -> Unit = {},
    onToggleWidgetVisible: (Category) -> Unit = {},
    onReassignCategory: (fromId: Int, toId: Int) -> Unit = { _, _ -> },
    chartPalette: String = "Bright",
    onChartPaletteChange: (String) -> Unit = {},
    weekStartSunday: Boolean = true,
    onWeekStartChange: (Boolean) -> Unit = {},
    budgetPeriod: String = "DAILY",
    onNavigateToBudgetConfig: () -> Unit = {},
    onNavigateToSync: () -> Unit = {},
    onNavigateToQuickStart: () -> Unit = {},
    isSyncConfigured: Boolean = false,
    isAdmin: Boolean = true,
    receiptPruneAgeDays: Int? = null,
    onReceiptPruneChange: (Int?) -> Unit = {},
    receiptCacheSize: Long = 0L,
    backupsEnabled: Boolean = false,
    onBackupsEnabledChange: (Boolean) -> Unit = {},
    backupFrequencyWeeks: Int = 1,
    onBackupFrequencyChange: (Int) -> Unit = {},
    backupRetention: Int = 1,
    onBackupRetentionChange: (Int) -> Unit = {},
    lastBackupDate: String? = null,
    nextBackupDate: String? = null,
    onBackupNow: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    onSavePhotos: () -> Unit = {},
    onDumpDebug: () -> Unit = {},
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {},
    // Data Management
    activeTransactionCount: Int = 0,
    archiveThreshold: Int = 10_000,
    onArchiveThresholdChange: (Int) -> Unit = {},
    lastArchiveDate: String? = null,
    lastArchiveCount: Int = 0,
    totalArchivedCount: Int = 0
) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current
    val toastState = LocalAppToast.current
    var showAddCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    val isLocked = isSyncConfigured && !isAdmin

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
                        text = S.settings.title,
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
                    IconButton(onClick = onHelpClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = S.common.help,
                            tint = customColors.headerText
                        )
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
            item {
                Text(
                    text = "Build: ${com.syncbudget.app.BuildConfig.BUILD_TIME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
            item {
                OutlinedButton(
                    onClick = onNavigateToQuickStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(S.settings.quickStartGuide)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToBudgetConfig,
                        modifier = Modifier.weight(2f)
                    ) {
                        Text(S.settings.configureYourBudget)
                    }
                    OutlinedButton(
                        onClick = onNavigateToSync,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(S.sync.syncButton)
                    }
                }
            }

            if (com.syncbudget.app.BuildConfig.DEBUG) {
                item {
                    OutlinedButton(
                        onClick = onDumpDebug,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dump & Sync Debug")
                    }
                }
            }

            // Row 1: Currency + Decimals checkbox
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Currency dropdown
                    var currencyExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = if (isLocked) false else currencyExpanded,
                        onExpandedChange = {
                            if (isLocked) toastState.show(S.settings.administratorOnly)
                            else currencyExpanded = it
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = currencySymbol,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isLocked,
                            label = { Text(S.settings.currency) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = currencyExpanded,
                            onDismissRequest = { currencyExpanded = false }
                        ) {
                            CURRENCY_OPTIONS.forEach { symbol ->
                                DropdownMenuItem(
                                    text = { Text(symbol) },
                                    onClick = {
                                        onCurrencyChange(symbol)
                                        currencyExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Decimals checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onDecimalsChange(!showDecimals) }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = showDecimals,
                            onCheckedChange = onDecimalsChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                        Text(
                            text = S.settings.showDecimalPlaces,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Row 2: Date format + Week start
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date format dropdown
                    var dateFormatExpanded by remember { mutableStateOf(false) }
                    val sampleDate = remember { LocalDate.of(2026, 2, 17) }
                    ExposedDropdownMenuBox(
                        expanded = dateFormatExpanded,
                        onExpandedChange = { dateFormatExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = sampleDate.format(DateTimeFormatter.ofPattern(dateFormatPattern)),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S.settings.dateFormat) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dateFormatExpanded) },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = dateFormatExpanded,
                            onDismissRequest = { dateFormatExpanded = false }
                        ) {
                            DATE_FORMAT_OPTIONS.forEach { pattern ->
                                val display = sampleDate.format(DateTimeFormatter.ofPattern(pattern))
                                DropdownMenuItem(
                                    text = { Text(display) },
                                    onClick = {
                                        onDateFormatChange(pattern)
                                        dateFormatExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Week start dropdown
                    val isWeeklyBudget = budgetPeriod == "WEEKLY"
                    val isDisabled = isWeeklyBudget && isLocked
                    var weekStartExpanded by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = if (isDisabled) false else weekStartExpanded,
                            onExpandedChange = {
                                if (isLocked) toastState.show(S.settings.administratorOnly)
                                else if (!isDisabled) weekStartExpanded = it
                            }
                        ) {
                            OutlinedTextField(
                                value = if (weekStartSunday) S.settings.sunday else S.settings.monday,
                                onValueChange = {},
                                readOnly = true,
                                enabled = !isDisabled,
                                label = { Text(S.settings.weekStartsOn) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weekStartExpanded) },
                                colors = textFieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = weekStartExpanded,
                                onDismissRequest = { weekStartExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(S.settings.sunday) },
                                    onClick = {
                                        onWeekStartChange(true)
                                        weekStartExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(S.settings.monday) },
                                    onClick = {
                                        onWeekStartChange(false)
                                        weekStartExpanded = false
                                    }
                                )
                            }
                        }
                        if (isWeeklyBudget) {
                            Text(
                                S.settings.weekStartWeeklyNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Row 3: Chart palette + Language
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Chart Palette dropdown
                    var paletteExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = paletteExpanded,
                        onExpandedChange = { paletteExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = when (chartPalette) {
                                "Bright" -> S.settings.bright
                                "Pastel" -> S.settings.pastel
                                "Sunset" -> S.settings.sunset
                                else -> chartPalette
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S.settings.chartPalette) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paletteExpanded) },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = paletteExpanded,
                            onDismissRequest = { paletteExpanded = false }
                        ) {
                            listOf("Bright" to S.settings.bright, "Pastel" to S.settings.pastel, "Sunset" to S.settings.sunset).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        onChartPaletteChange(value)
                                        paletteExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Language dropdown
                    var languageExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = languageExpanded,
                        onExpandedChange = { languageExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = if (appLanguage == "es") "Español" else "English",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S.settings.languageLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = languageExpanded,
                            onDismissRequest = { languageExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("English") },
                                onClick = {
                                    onLanguageChange("en")
                                    languageExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Español") },
                                onClick = {
                                    onLanguageChange("es")
                                    languageExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Matching Configuration section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = S.settings.matchingConfiguration,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (isLocked) {
                    Text(
                        text = S.sync.adminOnly,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800)
                    )
                }
            }

            // Row 1: Match Days + Match Percent
            item {
                Box(modifier = if (isLocked) Modifier.fillMaxWidth().clickable {
                    toastState.show(S.settings.administratorOnly)
                } else Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    var matchDaysText by remember { mutableStateOf(matchDays.toString()) }
                    OutlinedTextField(
                        value = matchDaysText,
                        onValueChange = { text ->
                            if (text.isEmpty() || text.all { it.isDigit() }) {
                                matchDaysText = text
                                text.toIntOrNull()?.let { onMatchDaysChange(it) }
                            }
                        },
                        label = { Text(S.settings.matchDays) },
                        singleLine = true,
                        enabled = !isLocked,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )

                    var matchPercentText by remember { mutableStateOf(matchPercent.toString()) }
                    OutlinedTextField(
                        value = matchPercentText,
                        onValueChange = { text ->
                            if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                                matchPercentText = text
                                text.toDoubleOrNull()?.let { onMatchPercentChange(it) }
                            }
                        },
                        label = { Text(S.settings.matchPercent) },
                        singleLine = true,
                        enabled = !isLocked,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                } // Box
            }

            // Row 2: Match Dollar + Match Chars
            item {
                Box(modifier = if (isLocked) Modifier.fillMaxWidth().clickable {
                    toastState.show(S.settings.administratorOnly)
                } else Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    var matchDollarText by remember { mutableStateOf(matchDollar.toString()) }
                    OutlinedTextField(
                        value = matchDollarText,
                        onValueChange = { text ->
                            if (text.isEmpty() || text.all { it.isDigit() }) {
                                matchDollarText = text
                                text.toIntOrNull()?.let { onMatchDollarChange(it) }
                            }
                        },
                        label = { Text(S.settings.matchDollar) },
                        singleLine = true,
                        enabled = !isLocked,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )

                    var matchCharsText by remember { mutableStateOf(matchChars.toString()) }
                    OutlinedTextField(
                        value = matchCharsText,
                        onValueChange = { text ->
                            if (text.isEmpty() || text.all { it.isDigit() }) {
                                matchCharsText = text
                                text.toIntOrNull()?.let { if (it >= 1) onMatchCharsChange(it) }
                            }
                        },
                        label = { Text(S.settings.matchChars) },
                        singleLine = true,
                        enabled = !isLocked,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                } // Box
            }

            // Paid User / Subscriber test toggles — DEBUG BUILDS ONLY.
            // In release builds these tiers are controlled by Google Play Billing,
            // not by user-facing checkboxes.
            if (com.syncbudget.app.BuildConfig.DEBUG) {
                // Paid User checkbox (debug override)
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isPaidUser,
                            onCheckedChange = onPaidUserChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                        Text(
                            text = S.settings.paidUser,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Subscriber checkbox + expiration date picker (debug override)
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isSubscriber,
                            onCheckedChange = { newValue ->
                                onSubscriberChange(newValue)
                                if (newValue && !isPaidUser) onPaidUserChange(true)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                        Text(
                            text = S.settings.subscriber,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (isSubscriber) {
                            Spacer(modifier = Modifier.weight(1f))
                            val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd") }
                            val expiryDate = java.time.Instant.ofEpochMilli(subscriptionExpiry)
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            var showExpiryPicker by remember { mutableStateOf(false) }
                            val pickerContext = LocalContext.current
                            Text(
                                text = "Exp: ${expiryDate.format(dateFormatter)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { showExpiryPicker = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            if (showExpiryPicker) {
                                LaunchedEffect(Unit) {
                                    val dialog = android.app.DatePickerDialog(
                                        pickerContext,
                                        { _, year, month, day ->
                                            val picked = java.time.LocalDate.of(year, month + 1, day)
                                            val millis = picked.atStartOfDay(java.time.ZoneId.systemDefault())
                                                .toInstant().toEpochMilli()
                                            onSubscriptionExpiryChange(millis)
                                            showExpiryPicker = false
                                        },
                                        expiryDate.year, expiryDate.monthValue - 1, expiryDate.dayOfMonth
                                    )
                                    dialog.setOnDismissListener { showExpiryPicker = false }
                                    dialog.show()
                                }
                            }
                        }
                    }
                }
            }

            // Show Widget Logo checkbox
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = showWidgetLogo,
                        onCheckedChange = onWidgetLogoChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                    Text(
                        text = S.settings.showWidgetLogo,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Auto Capitalize checkbox
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoCapitalize,
                        onCheckedChange = onAutoCapitalizeChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                    Text(
                        text = S.settings.autoCapitalize,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // ── PRIVACY SECTION ──
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = S.settings.privacySection,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
            }

            // Send Crash Reports checkbox
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = crashlyticsEnabled,
                            onCheckedChange = onCrashlyticsEnabledChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                        Text(
                            text = S.settings.crashReports,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = S.settings.crashReportsDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 4.dp)
                    )
                }
            }

            // Receipt photo settings (paid users only)
            if (isPaidUser) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = S.settings.receiptPhotosSection,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    val cacheMb = receiptCacheSize / (1024.0 * 1024.0)
                    Text(
                        text = S.settings.cacheSize("%.1f".format(cacheMb)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))
                    val pruneOptions = listOf(null to S.settings.keepAll, 30 to S.settings.days30, 60 to S.settings.days60, 90 to S.settings.days90, 180 to S.settings.days180, 365 to S.settings.days365)
                    val currentLabel = pruneOptions.find { it.first == receiptPruneAgeDays }?.second ?: S.settings.keepAll
                    var pruneExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedTextField(
                            value = currentLabel,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isLocked,
                            label = { Text(S.settings.receiptRetention) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors
                        )
                        // Transparent overlay to capture clicks (readOnly TextField absorbs them)
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    if (isLocked) toastState.show(S.settings.administratorOnly)
                                    else pruneExpanded = true
                                }
                        )
                        DropdownMenu(
                            expanded = pruneExpanded,
                            onDismissRequest = { pruneExpanded = false }
                        ) {
                            pruneOptions.forEach { (days, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        pruneExpanded = false
                                        onReceiptPruneChange(days)
                                    }
                                )
                            }
                        }
                    }
                    if (isLocked) {
                        Text(
                            text = S.settings.adminOnlyRetention,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSavePhotos,
                        enabled = isPaidUser,
                        modifier = Modifier.alpha(if (isPaidUser) 1f else 0.5f)
                    ) {
                        Text("Save Photos")
                    }
                }
            }

            // Backups section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    S.settings.backupsSection,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))

                // Enable checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onBackupsEnabledChange(!backupsEnabled) }
                ) {
                    Checkbox(
                        checked = backupsEnabled,
                        onCheckedChange = onBackupsEnabledChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                    Text(
                        S.settings.enableAutoBackups,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (backupsEnabled) {
                    Spacer(Modifier.height(8.dp))
                    // Last/next backup info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${S.settings.lastBackupLabel}: ${lastBackupDate ?: "Never"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        if (nextBackupDate != null) {
                            Text(
                                "${S.settings.nextBackupLabel}: $nextBackupDate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    // Frequency and Retention dropdowns side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Frequency dropdown
                        var freqExpanded by remember { mutableStateOf(false) }
                        val freqOptions = listOf(1 to S.settings.week1, 2 to S.settings.weeks2, 4 to S.settings.weeks4)
                        val freqLabel = freqOptions.find { it.first == backupFrequencyWeeks }?.second ?: S.settings.week1
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = freqLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(S.settings.frequencyLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { freqExpanded = true })
                            DropdownMenu(
                                expanded = freqExpanded,
                                onDismissRequest = { freqExpanded = false }
                            ) {
                                freqOptions.forEach { (weeks, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            freqExpanded = false
                                            onBackupFrequencyChange(weeks)
                                        }
                                    )
                                }
                            }
                        }
                        // Retention dropdown
                        var retExpanded by remember { mutableStateOf(false) }
                        val retOptions = listOf(1 to "1", 10 to "10", -1 to S.settings.retentionAll)
                        val retLabel = retOptions.find { it.first == backupRetention }?.second ?: "1"
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = retLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(S.settings.retentionLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { retExpanded = true })
                            DropdownMenu(
                                expanded = retExpanded,
                                onDismissRequest = { retExpanded = false }
                            ) {
                                retOptions.forEach { (ret, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            retExpanded = false
                                            onBackupRetentionChange(ret)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBackupNow,
                            modifier = Modifier.weight(1f)
                        ) { Text(S.settings.backupNow) }
                        val canRestore = !isSyncConfigured
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = onRestoreBackup,
                                enabled = canRestore,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(S.settings.restoreBackup) }
                            if (!canRestore) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable {
                                            val action = if (isAdmin) "dissolve" else "leave"
                                            toastState.show("You must $action your SYNC group to use this feature.")
                                        }
                                )
                            }
                        }
                    }
                    if (isSyncConfigured) {
                        Text(
                            S.settings.leaveGroupToRestore,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Text(
                            "See help (?) page for more information.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    // Even when disabled, show restore button
                    Spacer(Modifier.height(8.dp))
                    val canRestore = !isSyncConfigured
                    Box {
                        OutlinedButton(
                            onClick = onRestoreBackup,
                            enabled = canRestore
                        ) { Text(S.settings.restoreBackup) }
                        if (!canRestore) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable {
                                        val action = if (isAdmin) "dissolve" else "leave"
                                        toastState.show("You must $action your SYNC group to use this feature.")
                                    }
                            )
                        }
                    }
                    if (isSyncConfigured) {
                        Text(
                            S.settings.leaveGroupToRestore,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Text(
                            "See help (?) page for more information.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Data Management section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    S.settings.dataManagementSection,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (archiveThreshold > 0) S.settings.activeTransactionsTally(activeTransactionCount, archiveThreshold)
                           else S.settings.activeTransactionsCount(activeTransactionCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (archiveThreshold > 0) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (activeTransactionCount.toFloat() / archiveThreshold).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (activeTransactionCount > archiveThreshold)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                var thresholdExpanded by remember { mutableStateOf(false) }
                val thresholdOptions = listOf(
                    5_000 to "5,000",
                    10_000 to "10,000",
                    25_000 to "25,000",
                    0 to S.settings.archiveOff
                )
                ExposedDropdownMenuBox(
                    expanded = if (isLocked) false else thresholdExpanded,
                    onExpandedChange = {
                        if (isLocked) toastState.show(S.settings.administratorOnly)
                        else thresholdExpanded = it
                    }
                ) {
                    OutlinedTextField(
                        value = thresholdOptions.find { it.first == archiveThreshold }?.second ?: "10,000",
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isLocked,
                        label = { Text(S.settings.archiveThresholdLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = thresholdExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = thresholdExpanded, onDismissRequest = { thresholdExpanded = false }) {
                        thresholdOptions.forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                onArchiveThresholdChange(value)
                                thresholdExpanded = false
                            })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (lastArchiveDate != null) {
                    Text(
                        S.settings.lastArchivedInfo(lastArchiveDate, lastArchiveCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                if (totalArchivedCount > 0) {
                    Text(
                        S.settings.totalArchivedCount(totalArchivedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }

            // Categories section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = S.settings.categories,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = S.settings.charted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = S.settings.widget,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            items(categories) { category ->
                val isProtected = category.tag in setOf("other", "recurring_income", "supercharge")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (!isProtected) Modifier.clickable { editingCategory = category }
                            else Modifier
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getCategoryIcon(category.iconName),
                        contentDescription = category.name,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                        Checkbox(
                            checked = category.charted,
                            onCheckedChange = { onToggleCharted(category) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                    }
                    Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                        Checkbox(
                            checked = if (category.tag == "supercharge") false else category.widgetVisible,
                            onCheckedChange = if (category.tag == "supercharge") null else ({ onToggleWidgetVisible(category) }),
                            enabled = category.tag != "supercharge",
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showAddCategory = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(S.settings.addCategory)
                }
            }
        }
    }

    if (showAddCategory) {
        AddCategoryDialog(
            onDismiss = { showAddCategory = false },
            onSave = { category ->
                onAddCategory(category)
                showAddCategory = false
            },
            existingIds = categories.map { it.id }.toSet()
        )
    }

    editingCategory?.let { cat ->
        EditCategoryDialog(
            category = cat,
            categories = categories,
            transactions = transactions,
            onDismiss = { editingCategory = null },
            onSave = { updated ->
                onUpdateCategory(updated)
                editingCategory = null
            },
            onDelete = {
                onDeleteCategory(cat)
                editingCategory = null
            },
            onReassignAndDelete = { toId ->
                onReassignCategory(cat.id, toId)
                onDeleteCategory(cat)
                editingCategory = null
            }
        )
    }
}

@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    existingIds: Set<Int>
) {
    val S = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf<String?>(null) }
    val iconEntries = remember { CATEGORY_ICON_MAP.entries.toList() }
    var showValidation by remember { mutableStateOf(false) }

    AdAwareDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).imePadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            val dialogScrollState = rememberScrollState()
            Box {
            Column {
                DialogHeader(S.settings.addCategory)

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(dialogScrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(S.settings.categoryName) },
                        singleLine = true,
                        isError = showValidation && name.isBlank(),
                        supportingText = if (showValidation && name.isBlank()) ({
                            Text(S.settings.categoryName, color = Color(0xFFF44336))
                        }) else null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = if (showValidation && selectedIcon == null) "${S.settings.chooseIcon}: (required)" else "${S.settings.chooseIcon}:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (showValidation && selectedIcon == null) Color(0xFFF44336)
                            else MaterialTheme.colorScheme.onBackground
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(iconEntries) { (iconName, iconVector) ->
                            val isSelected = iconName == selectedIcon
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (isSelected) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    .clickable { selectedIcon = iconName },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = iconName,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                DialogFooter {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DialogSecondaryButton(onClick = onDismiss) { Text(S.common.cancel, maxLines = 1) }
                        Spacer(modifier = Modifier.width(8.dp))
                        DialogPrimaryButton(
                            onClick = {
                                if (name.isNotBlank() && selectedIcon != null) {
                                    var id: Int
                                    do {
                                        id = (0..65535).random()
                                    } while (id in existingIds)
                                    onSave(Category(id, name.trim(), selectedIcon!!))
                                } else {
                                    showValidation = true
                                }
                            }
                        ) {
                            Text(S.common.save, maxLines = 1)
                        }
                    }
                }
            }
            PulsingScrollArrow(
                scrollState = dialogScrollState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 50.dp)
            )
            }
        }
    }
}

@Composable
private fun EditCategoryDialog(
    category: Category,
    categories: List<Category>,
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: () -> Unit,
    onReassignAndDelete: (toId: Int) -> Unit
) {
    val S = LocalStrings.current
    var name by remember { mutableStateOf(category.name) }
    var selectedIcon by remember { mutableStateOf(category.iconName) }
    var showReassignDialog by remember { mutableStateOf(false) }
    val iconEntries = remember { CATEGORY_ICON_MAP.entries.toList() }

    val txnCount = remember(category.id, transactions) {
        transactions.count { t -> t.categoryAmounts.any { it.categoryId == category.id } }
    }

    if (showReassignDialog) {
        ReassignCategoryDialog(
            deletingCategory = category,
            categories = categories,
            txnCount = txnCount,
            onDismiss = { showReassignDialog = false },
            onReassign = { toId ->
                showReassignDialog = false
                onReassignAndDelete(toId)
            }
        )
        return
    }

    AdAwareDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).imePadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            val editScrollState = rememberScrollState()
            Box {
            Column {
                DialogHeader(S.settings.editCategory)

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(editScrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    if (category.tag !in setOf("other", "recurring_income", "supercharge")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = {
                                if (txnCount > 0) {
                                    showReassignDialog = true
                                } else {
                                    onDelete()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = S.common.delete,
                                    tint = Color(0xFFF44336)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(S.settings.categoryName) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "${S.settings.chooseIcon}:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(iconEntries) { (iconName, iconVector) ->
                            val isSelected = iconName == selectedIcon
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (isSelected) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    .clickable { selectedIcon = iconName },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = iconName,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                DialogFooter {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DialogSecondaryButton(onClick = onDismiss) { Text(S.common.cancel, maxLines = 1) }
                        Spacer(modifier = Modifier.width(8.dp))
                        DialogPrimaryButton(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onSave(category.copy(name = name.trim(), iconName = selectedIcon))
                                }
                            }
                        ) {
                            Text(S.common.save, maxLines = 1)
                        }
                    }
                }
            }
            PulsingScrollArrow(
                scrollState = editScrollState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 50.dp)
            )
            }
        }
    }
}

@Composable
private fun ReassignCategoryDialog(
    deletingCategory: Category,
    categories: List<Category>,
    txnCount: Int,
    onDismiss: () -> Unit,
    onReassign: (toId: Int) -> Unit
) {
    val S = LocalStrings.current
    var selectedTargetId by remember { mutableStateOf<Int?>(null) }
    val otherCategories = categories.filter { it.id != deletingCategory.id }

    AdAwareAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.settings.reassignCategoryTitle(deletingCategory.name, txnCount)) },
        scrollable = false,  // content has LazyColumn
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = S.settings.reassignCategoryBody(deletingCategory.name, txnCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(otherCategories) { cat ->
                        val isTarget = selectedTargetId == cat.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable { selectedTargetId = cat.id }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(cat.iconName),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        },
        style = DialogStyle.WARNING,
        confirmButton = {
            DialogWarningButton(
                onClick = { selectedTargetId?.let { onReassign(it) } },
                enabled = selectedTargetId != null
            ) {
                Text(S.settings.moveAndDelete)
            }
        },
        dismissButton = {
            DialogSecondaryButton(onClick = onDismiss) { Text(S.common.cancel) }
        }
    )
}
