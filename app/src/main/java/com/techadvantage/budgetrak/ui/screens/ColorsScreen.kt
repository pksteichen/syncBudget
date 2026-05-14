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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.techadvantage.budgetrak.data.ChartPalettesRepository
import com.techadvantage.budgetrak.data.ThemesRepository
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
import com.techadvantage.budgetrak.ui.theme.ScrollableDropdownContent
import com.techadvantage.budgetrak.ui.theme.ThemeColorSet
import com.techadvantage.budgetrak.ui.theme.ThemeProfile

private enum class EditMode(val label: String, val isChart: Boolean) {
    LIGHT("Light Mode Colors", false),
    DARK("Dark Mode Colors", false),
    CHART_LIGHT("Chart Colors (Light)", true),
    CHART_DARK("Chart Colors (Dark)", true),
}

private data class Slot(val key: String, val label: String)

private val BASE_SLOTS = listOf(
    Slot("primary", "Primary / Accent"),
    Slot("cardBackground", "Header / Card Background"),
    Slot("cardText", "Header / Card Text"),
    Slot("background", "Page Background"),
    Slot("surface", "Surface (cards/dialogs)"),
    Slot("onSurface", "Body Text"),
    Slot("displayBackground", "Solari Display Background"),
    Slot("displayBorder", "Solari Display Border"),
    Slot("incomeGreen", "Income / Success"),
    Slot("expenseRed", "Expense / Error"),
)

private fun ThemeColorSet.get(key: String): Color = when (key) {
    "primary" -> primary
    "cardBackground" -> cardBackground
    "cardText" -> cardText
    "background" -> background
    "surface" -> surface
    "onSurface" -> onSurface
    "displayBackground" -> displayBackground
    "displayBorder" -> displayBorder
    "incomeGreen" -> incomeGreen
    "expenseRed" -> expenseRed
    else -> Color.Transparent
}

private fun ThemeColorSet.set(key: String, c: Color): ThemeColorSet = when (key) {
    "primary" -> copy(primary = c)
    "cardBackground" -> copy(cardBackground = c)
    "cardText" -> copy(cardText = c)
    "background" -> copy(background = c)
    "surface" -> copy(surface = c)
    "onSurface" -> copy(onSurface = c)
    "displayBackground" -> copy(displayBackground = c)
    "displayBorder" -> copy(displayBorder = c)
    "incomeGreen" -> copy(incomeGreen = c)
    "expenseRed" -> copy(expenseRed = c)
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

    // Two independent lists: themes (base colors) and chart palettes.
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
        return currentTheme.copy(name = name, isBuiltIn = false)
    }

    fun forkPaletteIfBuiltIn(): ChartPalette {
        if (!currentPalette.isBuiltIn) return currentPalette
        val name = uniqueName("${currentPalette.name} (Custom)", allPalettes.map { it.name }.toSet())
        return currentPalette.copy(name = name, isBuiltIn = false)
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

    // Dropdown context — themes vs chart palettes.
    val dropdownLabel = if (mode.isChart) "Chart Palette" else "Theme"
    val dropdownName = if (mode.isChart) {
        currentPalette.name + if (currentPalette.isBuiltIn) " (Built-in)" else ""
    } else {
        currentTheme.name + if (currentTheme.isBuiltIn) " (Built-in)" else ""
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Colors", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // Mode dropdown
            ExposedDropdownMenuBox(
                expanded = modeExpanded,
                onExpandedChange = { modeExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = mode.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = modeExpanded,
                    onDismissRequest = { modeExpanded = false },
                ) {
                    EditMode.values().forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label) },
                            onClick = { mode = m; modeExpanded = false },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            // Theme/ChartPalette dropdown (context-sensitive)
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
                                    text = { Text(p.name + if (p.isBuiltIn) " (Built-in)" else "") },
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
                                    text = { Text(p.name + if (p.isBuiltIn) " (Built-in)" else "") },
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

            Spacer(Modifier.height(8.dp))
            // Slot dropdown (base modes) OR 12-swatch grid (chart modes)
            if (!mode.isChart) {
                ExposedDropdownMenuBox(
                    expanded = slotExpanded,
                    onExpandedChange = { slotExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = BASE_SLOTS[slotIndex].label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Color Setting") },
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
                                    text = { Text(slot.label) },
                                    onClick = { slotIndex = i; slotExpanded = false },
                                )
                            }
                        }
                    }
                }
            } else {
                Text("Chart slot", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
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

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(currentSlotColor, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { pickerOpen = true },
                )
                Spacer(Modifier.width(12.dp))
                DialogPrimaryButton(onClick = { pickerOpen = true }) { Text("Edit color") }
            }

            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogPrimaryButton(onClick = { newName = ""; newDialog = true }) {
                    Text(if (mode.isChart) "New palette" else "New theme")
                }
                val canDelete = if (mode.isChart) !currentPalette.isBuiltIn else !currentTheme.isBuiltIn
                if (canDelete) {
                    DialogDangerButton(onClick = { deleteConfirm = true }) { Text("Delete") }
                }
            }

            Spacer(Modifier.height(16.dp))
            DialogSecondaryButton(onClick = onBack) { Text("Back to Settings") }

            Spacer(Modifier.height(8.dp))
            Text(
                "Tip: editing a built-in theme or palette creates a custom copy automatically. " +
                    "Built-ins can never be modified or deleted.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (pickerOpen) {
        ColorPickerDialog(
            title = "Pick a color",
            initial = currentSlotColor,
            onDismiss = { pickerOpen = false },
            onSave = { picked ->
                pickerOpen = false
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
                    DialogHeader(if (mode.isChart) "New chart palette" else "New theme")
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Clone \"$sourceName\" under a new name:")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DialogFooter {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            DialogSecondaryButton(onClick = { newDialog = false }) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            DialogPrimaryButton(
                                enabled = newName.isNotBlank() &&
                                    existingNames.none { it.equals(newName.trim(), ignoreCase = true) },
                                onClick = {
                                    if (mode.isChart) {
                                        persistPalette(currentPalette.copy(name = newName.trim(), isBuiltIn = false))
                                    } else {
                                        persistTheme(currentTheme.copy(name = newName.trim(), isBuiltIn = false))
                                    }
                                    newDialog = false
                                },
                            ) { Text("Create") }
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
            title = { Text(if (mode.isChart) "Delete palette?" else "Delete theme?") },
            text = { Text("Delete \"$targetName\"? This cannot be undone.") },
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
                }) { Text("Delete") }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { deleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
