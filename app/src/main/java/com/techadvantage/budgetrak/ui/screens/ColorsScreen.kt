package com.techadvantage.budgetrak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.techadvantage.budgetrak.data.ThemesRepository
import com.techadvantage.budgetrak.ui.theme.AdAwareAlertDialog
import com.techadvantage.budgetrak.ui.theme.AdAwareDialog
import com.techadvantage.budgetrak.ui.theme.BuiltInThemes
import com.techadvantage.budgetrak.ui.theme.ColorPickerDialog
import com.techadvantage.budgetrak.ui.theme.DialogFooter
import com.techadvantage.budgetrak.ui.theme.DialogHeader
import com.techadvantage.budgetrak.ui.theme.DialogPrimaryButton
import com.techadvantage.budgetrak.ui.theme.DialogSecondaryButton
import com.techadvantage.budgetrak.ui.theme.DialogStyle
import com.techadvantage.budgetrak.ui.theme.DialogDangerButton
import com.techadvantage.budgetrak.ui.theme.ScrollableDropdownContent
import com.techadvantage.budgetrak.ui.theme.ThemeColorSet
import com.techadvantage.budgetrak.ui.theme.ThemeProfile

private enum class EditMode(val label: String) {
    LIGHT("Light Mode Colors"),
    DARK("Dark Mode Colors"),
    CHART_LIGHT("Chart Colors (Light)"),
    CHART_DARK("Chart Colors (Dark)"),
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
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var allProfiles by remember { mutableStateOf(ThemesRepository.load(context)) }
    var current by remember { mutableStateOf(activeTheme) }
    var mode by remember { mutableStateOf(EditMode.LIGHT) }
    var slotIndex by remember { mutableStateOf(0) }
    var chartIndex by remember { mutableStateOf(0) }
    var pickerOpen by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    var slotExpanded by remember { mutableStateOf(false) }
    var newThemeDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    fun persistAndApply(updated: ThemeProfile) {
        current = updated
        onActiveThemeChange(updated)
        if (!updated.isBuiltIn) {
            val others = allProfiles.filter { !it.isBuiltIn && it.name != updated.name }
            val newUserList = others + updated
            ThemesRepository.saveUserProfiles(context, newUserList)
            allProfiles = BuiltInThemes.ALL + newUserList
        }
        ThemesRepository.setSelected(context, updated.name)
    }

    /** If user edits a built-in, fork it to "<name> (Custom)". */
    fun forkIfBuiltIn(): ThemeProfile {
        if (!current.isBuiltIn) return current
        var base = "${current.name} (Custom)"
        var name = base
        var i = 2
        val existing = allProfiles.map { it.name }.toSet()
        while (name in existing) { name = "$base $i"; i++ }
        val forked = current.copy(name = name, isBuiltIn = false)
        return forked
    }

    val isChart = mode == EditMode.CHART_LIGHT || mode == EditMode.CHART_DARK
    val currentChart = if (mode == EditMode.CHART_LIGHT) current.chartLight else current.chartDark
    val currentSlotColor: Color = when (mode) {
        EditMode.LIGHT -> current.light.get(BASE_SLOTS[slotIndex].key)
        EditMode.DARK -> current.dark.get(BASE_SLOTS[slotIndex].key)
        EditMode.CHART_LIGHT, EditMode.CHART_DARK -> currentChart.getOrElse(chartIndex) { Color.Black }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
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
            // Theme dropdown
            ExposedDropdownMenuBox(
                expanded = themeExpanded,
                onExpandedChange = { themeExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = current.name + if (current.isBuiltIn) " (Built-in)" else "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Theme") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(themeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = themeExpanded,
                    onDismissRequest = { themeExpanded = false },
                ) {
                    ScrollableDropdownContent {
                        allProfiles.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name + if (p.isBuiltIn) " (Built-in)" else "") },
                                onClick = {
                                    current = p
                                    onActiveThemeChange(p)
                                    ThemesRepository.setSelected(context, p.name)
                                    themeExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            // Slot dropdown (base modes) OR chart-index numeric grid (chart modes)
            if (!isChart) {
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
                // Show 12 numbered swatches; tap to select.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    currentChart.forEachIndexed { i, c ->
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
            // Swatch + Edit button
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
            // Theme management buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogPrimaryButton(onClick = { newName = ""; newThemeDialog = true }) {
                    Text("New theme")
                }
                if (!current.isBuiltIn) {
                    DialogDangerButton(onClick = { deleteConfirm = true }) { Text("Delete") }
                }
            }

            Spacer(Modifier.height(16.dp))
            DialogSecondaryButton(onClick = onBack) { Text("Back to Settings") }

            Spacer(Modifier.height(8.dp))
            Text(
                "Tip: editing a built-in theme creates a custom copy automatically. " +
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
                val target = forkIfBuiltIn()
                val updated = when (mode) {
                    EditMode.LIGHT -> target.copy(light = target.light.set(BASE_SLOTS[slotIndex].key, picked))
                    EditMode.DARK -> target.copy(dark = target.dark.set(BASE_SLOTS[slotIndex].key, picked))
                    EditMode.CHART_LIGHT -> target.copy(
                        chartLight = target.chartLight.toMutableList().also { it[chartIndex] = picked },
                    )
                    EditMode.CHART_DARK -> target.copy(
                        chartDark = target.chartDark.toMutableList().also { it[chartIndex] = picked },
                    )
                }
                persistAndApply(updated)
            },
        )
    }

    if (newThemeDialog) {
        AdAwareDialog(onDismissRequest = { newThemeDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column {
                    DialogHeader("New theme")
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Clone \"${current.name}\" under a new name:")
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
                            DialogSecondaryButton(onClick = { newThemeDialog = false }) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            DialogPrimaryButton(
                                enabled = newName.isNotBlank() &&
                                    allProfiles.none { it.name.equals(newName.trim(), ignoreCase = true) },
                                onClick = {
                                    val clone = current.copy(name = newName.trim(), isBuiltIn = false)
                                    persistAndApply(clone)
                                    newThemeDialog = false
                                },
                            ) { Text("Create") }
                        }
                    }
                }
            }
        }
    }

    if (deleteConfirm) {
        AdAwareAlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete theme?") },
            text = { Text("Delete \"${current.name}\"? This cannot be undone.") },
            style = DialogStyle.DANGER,
            confirmButton = {
                DialogDangerButton(onClick = {
                    val remaining = allProfiles.filter { !it.isBuiltIn && it.name != current.name }
                    ThemesRepository.saveUserProfiles(context, remaining)
                    allProfiles = BuiltInThemes.ALL + remaining
                    current = BuiltInThemes.DEFAULT
                    onActiveThemeChange(BuiltInThemes.DEFAULT)
                    ThemesRepository.setSelected(context, BuiltInThemes.DEFAULT.name)
                    deleteConfirm = false
                }) { Text("Delete") }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { deleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
