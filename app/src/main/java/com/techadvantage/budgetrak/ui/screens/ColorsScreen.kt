package com.techadvantage.budgetrak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.techadvantage.budgetrak.data.ChartPalettesRepository
import com.techadvantage.budgetrak.data.ThemesRepository
import com.techadvantage.budgetrak.ui.strings.LocalStrings
import com.techadvantage.budgetrak.ui.theme.AdAwareAlertDialog
import com.techadvantage.budgetrak.ui.theme.AdAwareDialog
import com.techadvantage.budgetrak.ui.theme.BuiltInChartPalettes
import com.techadvantage.budgetrak.ui.theme.BuiltInThemes
import com.techadvantage.budgetrak.ui.theme.ChartPalette
import com.techadvantage.budgetrak.ui.theme.ColorPickerDialog
import com.techadvantage.budgetrak.ui.theme.DialogDangerButton
import com.techadvantage.budgetrak.ui.theme.DialogFooter
import com.techadvantage.budgetrak.ui.theme.DialogHeader
import com.techadvantage.budgetrak.ui.theme.DialogPrimaryButton
import com.techadvantage.budgetrak.ui.theme.DialogSecondaryButton
import com.techadvantage.budgetrak.ui.theme.DialogStyle
import com.techadvantage.budgetrak.ui.theme.LocalSyncBudgetColors
import com.techadvantage.budgetrak.ui.theme.ScrollableDropdownContent
import com.techadvantage.budgetrak.ui.theme.ThemeColorSet
import com.techadvantage.budgetrak.ui.theme.ThemeProfile

private enum class EditMode(val isChart: Boolean) {
    LIGHT(false),
    DARK(false),
    CHART_LIGHT(true),
    CHART_DARK(true),
}

@Composable
private fun EditMode.label(): String {
    val S = LocalStrings.current
    return when (this) {
        EditMode.LIGHT -> S.colors.modeLight
        EditMode.DARK -> S.colors.modeDark
        EditMode.CHART_LIGHT -> S.colors.modeChartLight
        EditMode.CHART_DARK -> S.colors.modeChartDark
    }
}

private data class Slot(val key: String)

private val BASE_SLOTS = listOf(
    Slot("cardBackground"),
    Slot("cardText"),
    Slot("background"),
    Slot("surfaceHeader"),
    Slot("surfaceHeaderText"),
    Slot("surface"),
    Slot("onSurface"),
    Slot("displayBackground"),
)

@Composable
private fun Slot.label(): String {
    val S = LocalStrings.current
    return when (key) {
        "cardBackground" -> S.colors.slotHeader
        "cardText" -> S.colors.slotHeaderText
        "background" -> S.colors.slotPageBackground
        "surfaceHeader" -> S.colors.slotWindowHeader
        "surfaceHeaderText" -> S.colors.slotWindowHeaderText
        "surface" -> S.colors.slotWindowBackground
        "onSurface" -> S.colors.slotGeneralText
        "displayBackground" -> S.colors.slotSolariBackground
        else -> key
    }
}

private fun ThemeColorSet.get(key: String): Color = when (key) {
    "cardBackground" -> cardBackground
    "cardText" -> cardText
    "background" -> background
    "surface" -> surface
    "surfaceHeader" -> surfaceHeader
    "surfaceHeaderText" -> surfaceHeaderText
    "onSurface" -> onSurface
    "displayBackground" -> displayBackground
    else -> Color.Transparent
}

private fun ThemeColorSet.set(key: String, c: Color): ThemeColorSet = when (key) {
    "cardBackground" -> copy(cardBackground = c)
    "cardText" -> copy(cardText = c)
    "background" -> copy(background = c)
    "surface" -> copy(surface = c)
    "surfaceHeader" -> copy(surfaceHeader = c)
    "surfaceHeaderText" -> copy(surfaceHeaderText = c)
    "onSurface" -> copy(onSurface = c)
    "displayBackground" -> copy(displayBackground = c)
    else -> this
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorsScreen(
    activeTheme: ThemeProfile,
    onActiveThemeChange: (ThemeProfile) -> Unit,
    activeChartPalette: ChartPalette,
    onActiveChartPaletteChange: (ChartPalette) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val S = LocalStrings.current

    var allThemes by remember { mutableStateOf(ThemesRepository.load(context)) }
    var allPalettes by remember { mutableStateOf(ChartPalettesRepository.load(context)) }
    var currentTheme by remember { mutableStateOf(activeTheme) }
    var currentPalette by remember { mutableStateOf(activeChartPalette) }

    var mode by remember { mutableStateOf(EditMode.LIGHT) }
    var slotIndex by remember { mutableStateOf(0) }
    var chartIndex by remember { mutableStateOf(0) }
    var pickerOpen by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    var slotExpanded by remember { mutableStateOf(false) }
    var newDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    fun persistTheme(updated: ThemeProfile) {
        currentTheme = updated
        onActiveThemeChange(updated)
        if (!updated.isBuiltIn) {
            val others = allThemes.filter { !it.isBuiltIn && it.name != updated.name }
            val newList = others + updated
            ThemesRepository.saveUserProfiles(context, newList)
            allThemes = BuiltInThemes.ALL + newList
        }
        ThemesRepository.setSelected(context, updated.name)
    }

    fun persistPalette(updated: ChartPalette) {
        currentPalette = updated
        onActiveChartPaletteChange(updated)
        if (!updated.isBuiltIn) {
            val others = allPalettes.filter { !it.isBuiltIn && it.name != updated.name }
            val newList = others + updated
            ChartPalettesRepository.saveUserPalettes(context, newList)
            allPalettes = BuiltInChartPalettes.ALL + newList
        }
        ChartPalettesRepository.setSelected(context, updated.name)
    }

    fun uniqueName(base: String, existingNames: Set<String>): String {
        var name = base; var i = 2
        while (name in existingNames) { name = "$base $i"; i++ }
        return name
    }

    fun forkThemeIfBuiltIn(): ThemeProfile {
        if (!currentTheme.isBuiltIn) return currentTheme
        val name = uniqueName("${currentTheme.name} (Custom)", allThemes.map { it.name }.toSet())
        return currentTheme.copy(name = name, isBuiltIn = false, forkedFrom = currentTheme.name)
    }

    fun forkPaletteIfBuiltIn(): ChartPalette {
        if (!currentPalette.isBuiltIn) return currentPalette
        val name = uniqueName("${currentPalette.name} (Custom)", allPalettes.map { it.name }.toSet())
        return currentPalette.copy(name = name, isBuiltIn = false, forkedFrom = currentPalette.name)
    }

    /** Built-in theme that a custom theme's "default" should restore from. */
    fun lineageBuiltInTheme(): ThemeProfile {
        val ref = if (currentTheme.isBuiltIn) currentTheme.name else currentTheme.forkedFrom
        return BuiltInThemes.ALL.firstOrNull { it.name == ref } ?: BuiltInThemes.DEFAULT
    }

    /** Built-in palette that a custom palette's "default" should restore from. */
    fun lineageBuiltInPalette(): ChartPalette {
        val ref = if (currentPalette.isBuiltIn) currentPalette.name else currentPalette.forkedFrom
        return BuiltInChartPalettes.ALL.firstOrNull { it.name == ref } ?: BuiltInChartPalettes.DEFAULT
    }

    val currentChartList: List<Color> = when (mode) {
        EditMode.CHART_LIGHT -> currentPalette.chartLight
        EditMode.CHART_DARK -> currentPalette.chartDark
        else -> emptyList()
    }
    val currentSlotColor: Color = when (mode) {
        EditMode.LIGHT -> currentTheme.light.get(BASE_SLOTS[slotIndex].key)
        EditMode.DARK -> currentTheme.dark.get(BASE_SLOTS[slotIndex].key)
        EditMode.CHART_LIGHT, EditMode.CHART_DARK ->
            currentChartList.getOrElse(chartIndex) { Color.Black }
    }
    // "Default" value for the active slot — used by the undo icon. Lineage-aware:
    // a custom theme/palette undoes to its source built-in, not always Default/Bright.
    // E.g. a Sunset-forked custom undoes to Sunset's value at that slot.
    val defaultSlotColor: Color = when (mode) {
        EditMode.LIGHT -> lineageBuiltInTheme().light.get(BASE_SLOTS[slotIndex].key)
        EditMode.DARK -> lineageBuiltInTheme().dark.get(BASE_SLOTS[slotIndex].key)
        EditMode.CHART_LIGHT -> lineageBuiltInPalette().chartLight.getOrElse(chartIndex) { Color.Black }
        EditMode.CHART_DARK -> lineageBuiltInPalette().chartDark.getOrElse(chartIndex) { Color.Black }
    }
    val canUndo = currentSlotColor != defaultSlotColor

    fun applyColor(picked: Color) {
        when (mode) {
            EditMode.LIGHT -> {
                val t = forkThemeIfBuiltIn()
                persistTheme(t.copy(light = t.light.set(BASE_SLOTS[slotIndex].key, picked)))
            }
            EditMode.DARK -> {
                val t = forkThemeIfBuiltIn()
                persistTheme(t.copy(dark = t.dark.set(BASE_SLOTS[slotIndex].key, picked)))
            }
            EditMode.CHART_LIGHT -> {
                val p = forkPaletteIfBuiltIn()
                persistPalette(p.copy(chartLight = p.chartLight.toMutableList().also { it[chartIndex] = picked }))
            }
            EditMode.CHART_DARK -> {
                val p = forkPaletteIfBuiltIn()
                persistPalette(p.copy(chartDark = p.chartDark.toMutableList().also { it[chartIndex] = picked }))
            }
        }
    }

    val dropdownLabel = if (mode.isChart) S.colors.dropdownChartPalette else S.colors.dropdownTheme
    val dropdownName = if (mode.isChart) {
        currentPalette.name + if (currentPalette.isBuiltIn) S.colors.builtInSuffix else ""
    } else {
        currentTheme.name + if (currentTheme.isBuiltIn) S.colors.builtInSuffix else ""
    }

    // Preview: page renders in the theme/mode being edited so user sees their
    // changes live. Light/Chart_Light → light cs; Dark/Chart_Dark → dark cs.
    val previewDark = mode == EditMode.DARK || mode == EditMode.CHART_DARK
    val previewCs = if (previewDark) currentTheme.dark else currentTheme.light
    val previewOnPrimary = if (previewCs.cardBackground.luminance() > 0.5f) Color.Black else Color.White
    val previewColorScheme = if (previewDark) {
        androidx.compose.material3.darkColorScheme(
            primary = previewCs.cardBackground,
            onPrimary = previewOnPrimary,
            background = previewCs.background,
            surface = previewCs.surface,
            onBackground = previewCs.onSurface,
            onSurface = previewCs.onSurface,
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = previewCs.cardBackground,
            onPrimary = previewOnPrimary,
            background = previewCs.background,
            surface = previewCs.surface,
            onBackground = previewCs.onSurface,
            onSurface = previewCs.onSurface,
        )
    }
    val previewSyncColors = com.techadvantage.budgetrak.ui.theme.SyncBudgetColors(
        headerBackground = previewCs.cardBackground,
        headerText = previewCs.cardText,
        cardBackground = previewCs.cardBackground,
        cardText = previewCs.cardText,
        surfaceHeader = previewCs.surfaceHeader,
        surfaceHeaderText = previewCs.surfaceHeaderText,
        displayBackground = previewCs.displayBackground,
        displayBorder = com.techadvantage.budgetrak.ui.theme.solariBorderFor(previewCs.displayBackground),
        userCategoryIconTint = previewCs.cardBackground,
        accentTint = if (previewDark) previewCs.cardText else previewCs.cardBackground,
    )
    val customColors = previewSyncColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalSyncBudgetColors provides previewSyncColors,
    ) {
    androidx.compose.material3.MaterialTheme(colorScheme = previewColorScheme) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.colors.title,
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
            // Mode dropdown
            item {
                ExposedDropdownMenuBox(
                    expanded = modeExpanded,
                    onExpandedChange = { modeExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = mode.label(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(S.colors.modeLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false },
                    ) {
                        EditMode.values().forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.label()) },
                                onClick = { mode = m; modeExpanded = false },
                            )
                        }
                    }
                }
            }

            // Theme / Chart Palette dropdown (context-sensitive)
            item {
                ExposedDropdownMenuBox(
                    expanded = themeExpanded,
                    onExpandedChange = { themeExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = dropdownName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(dropdownLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(themeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false },
                    ) {
                        ScrollableDropdownContent {
                            if (mode.isChart) {
                                allPalettes.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name + if (p.isBuiltIn) S.colors.builtInSuffix else "") },
                                        onClick = {
                                            currentPalette = p
                                            onActiveChartPaletteChange(p)
                                            ChartPalettesRepository.setSelected(context, p.name)
                                            themeExpanded = false
                                        },
                                    )
                                }
                            } else {
                                allThemes.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name + if (p.isBuiltIn) S.colors.builtInSuffix else "") },
                                        onClick = {
                                            currentTheme = p
                                            onActiveThemeChange(p)
                                            ThemesRepository.setSelected(context, p.name)
                                            themeExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Slot selector — base modes: dropdown; chart modes: 12-swatch row.
            item {
                if (!mode.isChart) {
                    ExposedDropdownMenuBox(
                        expanded = slotExpanded,
                        onExpandedChange = { slotExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = BASE_SLOTS[slotIndex].label(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S.colors.colorSettingLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(slotExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = slotExpanded,
                            onDismissRequest = { slotExpanded = false },
                        ) {
                            ScrollableDropdownContent {
                                BASE_SLOTS.forEachIndexed { i, slot ->
                                    DropdownMenuItem(
                                        text = { Text(slot.label()) },
                                        onClick = { slotIndex = i; slotExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column {
                        Text(S.colors.chartSlotLabel, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            currentChartList.forEachIndexed { i, c ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 24.dp, height = 36.dp)
                                        .background(c, RoundedCornerShape(4.dp))
                                        .border(
                                            width = if (i == chartIndex) 3.dp else 1.dp,
                                            color = if (i == chartIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(4.dp),
                                        )
                                        .clickable { chartIndex = i },
                                )
                            }
                        }
                    }
                }
            }

            // Current color row: swatch + pencil icon + undo icon (only when modified).
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(currentSlotColor, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable { pickerOpen = true },
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { pickerOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = S.colors.editColor,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    if (canUndo) {
                        IconButton(onClick = { applyColor(defaultSlotColor) }) {
                            Icon(
                                imageVector = Icons.Filled.Undo,
                                contentDescription = S.colors.restoreDefault,
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }

            // Theme / palette management buttons.
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.techadvantage.budgetrak.ui.theme.ScreenPrimaryButton(onClick = { newName = ""; newDialog = true }) {
                        Text(if (mode.isChart) S.colors.newPalette else S.colors.newTheme)
                    }
                    val canDelete = if (mode.isChart) !currentPalette.isBuiltIn else !currentTheme.isBuiltIn
                    if (canDelete) {
                        DialogDangerButton(onClick = { deleteConfirm = true }) { Text(S.common.delete) }
                    }
                }
            }

            item {
                Text(
                    S.colors.tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Live preview: sample dialog (base modes) or pie chart (chart modes).
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (mode.isChart) S.colors.samplePieChart else S.colors.sampleDialog,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                if (mode.isChart) {
                    SamplePieChart(colors = currentChartList)
                } else {
                    SampleDialog()
                }
            }
        }
    }
    }
    }

    if (pickerOpen) {
        ColorPickerDialog(
            title = S.colors.pickColor,
            initial = currentSlotColor,
            onDismiss = { pickerOpen = false },
            onSave = { picked ->
                pickerOpen = false
                applyColor(picked)
            },
        )
    }

    if (newDialog) {
        val sourceName = if (mode.isChart) currentPalette.name else currentTheme.name
        val existingNames = if (mode.isChart) allPalettes.map { it.name }.toSet() else allThemes.map { it.name }.toSet()
        AdAwareDialog(onDismissRequest = { newDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column {
                    DialogHeader(if (mode.isChart) S.colors.newChartPaletteDialogTitle else S.colors.newThemeDialogTitle)
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(S.colors.cloneUnderNewName(sourceName))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text(S.colors.nameLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DialogFooter {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            DialogSecondaryButton(onClick = { newDialog = false }) { Text(S.common.cancel) }
                            Spacer(Modifier.width(8.dp))
                            DialogPrimaryButton(
                                enabled = newName.isNotBlank() &&
                                    existingNames.none { it.equals(newName.trim(), ignoreCase = true) },
                                onClick = {
                                    if (mode.isChart) {
                                        // If cloning a built-in, lineage = that built-in.
                                        // If cloning a custom, inherit its lineage.
                                        val lineage = if (currentPalette.isBuiltIn) currentPalette.name
                                                      else currentPalette.forkedFrom
                                        persistPalette(currentPalette.copy(
                                            name = newName.trim(),
                                            isBuiltIn = false,
                                            forkedFrom = lineage,
                                        ))
                                    } else {
                                        val lineage = if (currentTheme.isBuiltIn) currentTheme.name
                                                      else currentTheme.forkedFrom
                                        persistTheme(currentTheme.copy(
                                            name = newName.trim(),
                                            isBuiltIn = false,
                                            forkedFrom = lineage,
                                        ))
                                    }
                                    newDialog = false
                                },
                            ) { Text(S.colors.create) }
                        }
                    }
                }
            }
        }
    }

    if (deleteConfirm) {
        val targetName = if (mode.isChart) currentPalette.name else currentTheme.name
        AdAwareAlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(if (mode.isChart) S.colors.deletePaletteTitle else S.colors.deleteThemeTitle) },
            text = { Text(S.colors.deleteConfirmBody(targetName)) },
            style = DialogStyle.DANGER,
            confirmButton = {
                DialogDangerButton(onClick = {
                    if (mode.isChart) {
                        val remaining = allPalettes.filter { !it.isBuiltIn && it.name != currentPalette.name }
                        ChartPalettesRepository.saveUserPalettes(context, remaining)
                        allPalettes = BuiltInChartPalettes.ALL + remaining
                        currentPalette = BuiltInChartPalettes.DEFAULT
                        onActiveChartPaletteChange(BuiltInChartPalettes.DEFAULT)
                        ChartPalettesRepository.setSelected(context, BuiltInChartPalettes.DEFAULT.name)
                    } else {
                        val remaining = allThemes.filter { !it.isBuiltIn && it.name != currentTheme.name }
                        ThemesRepository.saveUserProfiles(context, remaining)
                        allThemes = BuiltInThemes.ALL + remaining
                        currentTheme = BuiltInThemes.DEFAULT
                        onActiveThemeChange(BuiltInThemes.DEFAULT)
                        ThemesRepository.setSelected(context, BuiltInThemes.DEFAULT.name)
                    }
                    deleteConfirm = false
                }) { Text(S.common.delete) }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { deleteConfirm = false }) { Text(S.common.cancel) }
            },
        )
    }
}

/**
 * Inline mock dialog rendered into the Colors page so the user sees how their
 * edits affect dialog chrome. Reuses DialogHeader/DialogFooter so the green
 * header/footer + button colors match the real AdAwareAlertDialog.
 */
@Composable
private fun SampleDialog() {
    val S = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column {
            DialogHeader(title = S.colors.sampleDialog)
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    S.colors.sampleDialogBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            DialogFooter {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DialogSecondaryButton(onClick = {}) { Text(S.common.cancel) }
                    Spacer(Modifier.width(8.dp))
                    DialogPrimaryButton(onClick = {}) { Text(S.common.ok) }
                }
            }
        }
    }
}

/**
 * 12-wedge pie preview. Wedges follow a linear progression from 20% → 5% in
 * raw weights then normalize to sum=100% (strict 20/5/100 with 12 wedges is
 * over-determined). The largest wedge uses palette[0], smallest uses palette[11].
 */
@Composable
private fun SamplePieChart(colors: List<Color>) {
    val rawWeights = (0 until 12).map { i -> 20f - (15f * i / 11f) }
    val totalRaw = rawWeights.sum()
    val weights = rawWeights.map { it / totalRaw }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(200.dp)
        ) {
            val radius = size.minDimension / 2f
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - radius * 2f) / 2f,
                (size.height - radius * 2f) / 2f,
            )
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
            var startAngle = -90f
            weights.forEachIndexed { i, w ->
                val sweep = 360f * w
                drawArc(
                    color = colors.getOrElse(i) { Color.Gray },
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = arcSize,
                )
                startAngle += sweep
            }
        }
    }
}
