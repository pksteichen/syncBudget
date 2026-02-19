package com.syncbudget.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CATEGORY_ICON_MAP
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.getCategoryIcon
import com.syncbudget.app.ui.components.CURRENCY_OPTIONS
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
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
    onCurrencyChange: (String) -> Unit,
    digitCount: Int,
    onDigitCountChange: (Int) -> Unit,
    showDecimals: Boolean,
    onDecimalsChange: (Boolean) -> Unit,
    dateFormatPattern: String,
    onDateFormatChange: (String) -> Unit,
    isPaidUser: Boolean = false,
    onPaidUserChange: (Boolean) -> Unit = {},
    matchDays: Int = 7,
    onMatchDaysChange: (Int) -> Unit = {},
    matchPercent: Float = 1.0f,
    onMatchPercentChange: (Float) -> Unit = {},
    matchDollar: Int = 1,
    onMatchDollarChange: (Int) -> Unit = {},
    matchChars: Int = 5,
    onMatchCharsChange: (Int) -> Unit = {},
    categories: List<Category>,
    transactions: List<Transaction> = emptyList(),
    onAddCategory: (Category) -> Unit,
    onUpdateCategory: (Category) -> Unit = {},
    onDeleteCategory: (Category) -> Unit,
    onReassignCategory: (fromId: Int, toId: Int) -> Unit = { _, _ -> },
    weekStartSunday: Boolean = true,
    onWeekStartChange: (Boolean) -> Unit = {},
    onNavigateToBudgetConfig: () -> Unit = {},
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current
    var showAddCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

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
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = customColors.headerText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onHelpClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = "Help",
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
                OutlinedButton(
                    onClick = onNavigateToBudgetConfig,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Configure Your Budget")
                }
            }

            // Currency dropdown
            item {
                var currencyExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = currencyExpanded,
                    onExpandedChange = { currencyExpanded = it }
                ) {
                    OutlinedTextField(
                        value = currencySymbol,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Currency") },
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
            }

            // Digits dropdown
            item {
                var digitsExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = digitsExpanded,
                    onExpandedChange = { digitsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = digitCount.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Digits") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = digitsExpanded) },
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = digitsExpanded,
                        onDismissRequest = { digitsExpanded = false }
                    ) {
                        (2..5).forEach { count ->
                            DropdownMenuItem(
                                text = { Text(count.toString()) },
                                onClick = {
                                    onDigitCountChange(count)
                                    digitsExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Decimals checkbox
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = showDecimals,
                        onCheckedChange = onDecimalsChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                    Text(
                        text = "Show decimal places",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Date format dropdown
            item {
                var dateFormatExpanded by remember { mutableStateOf(false) }
                val sampleDate = remember { LocalDate.of(2026, 2, 17) }
                ExposedDropdownMenuBox(
                    expanded = dateFormatExpanded,
                    onExpandedChange = { dateFormatExpanded = it }
                ) {
                    OutlinedTextField(
                        value = sampleDate.format(DateTimeFormatter.ofPattern(dateFormatPattern)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date Format") },
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
            }

            // Week start day dropdown
            item {
                var weekStartExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = weekStartExpanded,
                    onExpandedChange = { weekStartExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (weekStartSunday) "Sunday" else "Monday",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Week Starts On") },
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
                            text = { Text("Sunday") },
                            onClick = {
                                onWeekStartChange(true)
                                weekStartExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Monday") },
                            onClick = {
                                onWeekStartChange(false)
                                weekStartExpanded = false
                            }
                        )
                    }
                }
            }

            // Matching Configuration section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Matching Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                var matchDaysText by remember { mutableStateOf(matchDays.toString()) }
                OutlinedTextField(
                    value = matchDaysText,
                    onValueChange = { text ->
                        if (text.isEmpty() || text.all { it.isDigit() }) {
                            matchDaysText = text
                            text.toIntOrNull()?.let { onMatchDaysChange(it) }
                        }
                    },
                    label = { Text("Match Days (±N)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                var matchPercentText by remember { mutableStateOf(matchPercent.toString()) }
                OutlinedTextField(
                    value = matchPercentText,
                    onValueChange = { text ->
                        if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                            matchPercentText = text
                            text.toFloatOrNull()?.let { onMatchPercentChange(it) }
                        }
                    },
                    label = { Text("Match Percent (±%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                var matchDollarText by remember { mutableStateOf(matchDollar.toString()) }
                OutlinedTextField(
                    value = matchDollarText,
                    onValueChange = { text ->
                        if (text.isEmpty() || text.all { it.isDigit() }) {
                            matchDollarText = text
                            text.toIntOrNull()?.let { onMatchDollarChange(it) }
                        }
                    },
                    label = { Text("Match Dollar (±\$)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                var matchCharsText by remember { mutableStateOf(matchChars.toString()) }
                OutlinedTextField(
                    value = matchCharsText,
                    onValueChange = { text ->
                        if (text.isEmpty() || text.all { it.isDigit() }) {
                            matchCharsText = text
                            text.toIntOrNull()?.let { if (it >= 1) onMatchCharsChange(it) }
                        }
                    },
                    label = { Text("Match Characters") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Paid User checkbox
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
                        text = "Paid User",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Categories section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(categories) { category ->
                val isProtected = category.name == "Other" || category.name == "Recurring" || category.name == "Amortization" || category.name == "Recurring Income"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (!isProtected) Modifier.clickable { editingCategory = category }
                            else Modifier
                        )
                        .padding(vertical = 8.dp),
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
                    Text("Add Category")
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
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf<String?>(null) }
    val iconEntries = remember { CATEGORY_ICON_MAP.entries.toList() }
    var showValidation by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Category") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    isError = showValidation && name.isBlank(),
                    supportingText = if (showValidation && name.isBlank()) ({
                        Text("Required, e.g. Groceries", color = Color(0xFFF44336))
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
                    text = if (showValidation && selectedIcon == null) "Choose Icon: (required)" else "Choose Icon:",
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
        },
        confirmButton = {
            TextButton(
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit Category", modifier = Modifier.weight(1f))
                if (category.name != "Other" && category.name != "Recurring" && category.name != "Amortization" && category.name != "Recurring Income") {
                    IconButton(onClick = {
                        if (txnCount > 0) {
                            showReassignDialog = true
                        } else {
                            onDelete()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete category",
                            tint = androidx.compose.ui.graphics.Color(0xFFF44336)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
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
                    text = "Choose Icon:",
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(category.copy(name = name.trim(), iconName = selectedIcon))
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReassignCategoryDialog(
    deletingCategory: Category,
    categories: List<Category>,
    txnCount: Int,
    onDismiss: () -> Unit,
    onReassign: (toId: Int) -> Unit
) {
    var selectedTargetId by remember { mutableStateOf<Int?>(null) }
    val otherCategories = categories.filter { it.id != deletingCategory.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reassign Transactions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "$txnCount transaction${if (txnCount != 1) "s" else ""} use \"${deletingCategory.name}\". Choose a category to move them to:",
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
        confirmButton = {
            TextButton(
                onClick = { selectedTargetId?.let { onReassign(it) } },
                enabled = selectedTargetId != null
            ) {
                Text("Move & Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
