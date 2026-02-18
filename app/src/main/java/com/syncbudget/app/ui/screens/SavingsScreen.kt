package com.syncbudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.generateSavingsGoalId
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

private fun periodSingularLabel(budgetPeriod: BudgetPeriod): String = when (budgetPeriod) {
    BudgetPeriod.DAILY -> "day"
    BudgetPeriod.WEEKLY -> "week"
    BudgetPeriod.MONTHLY -> "month"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(
    savingsGoals: List<SavingsGoal>,
    currencySymbol: String,
    budgetPeriod: BudgetPeriod,
    isManualBudgetEnabled: Boolean = false,
    onAddGoal: (SavingsGoal) -> Unit,
    onUpdateGoal: (SavingsGoal) -> Unit,
    onDeleteGoal: (SavingsGoal) -> Unit,
    onBack: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var deletingGoal by remember { mutableStateOf<SavingsGoal?>(null) }

    val allPaused = savingsGoals.isNotEmpty() && savingsGoals.all { it.isPaused }
    val anyActive = savingsGoals.any { !it.isPaused }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Savings",
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText
                    )
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = customColors.headerText
                            )
                        }
                        if (savingsGoals.isNotEmpty()) {
                            IconButton(onClick = {
                                if (anyActive) {
                                    savingsGoals.forEach { onUpdateGoal(it.copy(isPaused = true)) }
                                } else {
                                    savingsGoals.forEach { onUpdateGoal(it.copy(isPaused = false)) }
                                }
                            }) {
                                Icon(
                                    imageVector = if (allPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = if (allPaused) "Resume All" else "Pause All",
                                    tint = customColors.headerText
                                )
                            }
                        }
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = "Set savings goals with target amounts and per-period contributions. " +
                            "Each period, the contribution amount is deducted from your budget and " +
                            "added to the goal's savings. Use the pause button to temporarily stop " +
                            "contributions for a goal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (isManualBudgetEnabled) {
                    Text(
                        text = "Budget deductions are disabled. Manual budget override is active in Settings > Budget Configuration.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFF44336),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Add Savings Goal")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            items(savingsGoals) { goal ->
                val goalReached = goal.savedSoFar >= goal.targetAmount
                val progress = if (goal.targetAmount > 0) {
                    (goal.savedSoFar / goal.targetAmount).toFloat().coerceIn(0f, 1f)
                } else 0f
                val label = periodSingularLabel(budgetPeriod)
                val contentAlpha = if (goal.isPaused) 0.5f else 1f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingGoal = goal }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onUpdateGoal(goal.copy(isPaused = !goal.isPaused)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (goal.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (goal.isPaused) "Resume" else "Pause",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha)
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha)
                        )
                        Text(
                            text = "Target: $currencySymbol${"%.2f".format(goal.targetAmount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f * contentAlpha)
                        )
                        if (!goalReached && !goal.isPaused) {
                            Text(
                                text = "Contribution: $currencySymbol${"%.2f".format(goal.contributionPerPeriod)}/$label",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (goalReached) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Saved: $currencySymbol${"%.2f".format(goal.savedSoFar)} of $currencySymbol${"%.2f".format(goal.targetAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50).copy(alpha = contentAlpha)
                        )
                        if (goalReached) {
                            Text(
                                text = "Goal reached!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        } else if (goal.isPaused) {
                            Text(
                                text = "Paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }
                    }
                    IconButton(onClick = { deletingGoal = goal }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFF44336)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditSavingsGoalDialog(
            title = "Add Savings Goal",
            initialName = "",
            initialTargetAmount = "",
            initialContribution = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, targetAmount, contribution ->
                val id = generateSavingsGoalId(savingsGoals.map { it.id }.toSet())
                onAddGoal(
                    SavingsGoal(
                        id = id,
                        name = name,
                        targetAmount = targetAmount,
                        contributionPerPeriod = contribution
                    )
                )
                showAddDialog = false
            }
        )
    }

    editingGoal?.let { goal ->
        AddEditSavingsGoalDialog(
            title = "Edit Savings Goal",
            initialName = goal.name,
            initialTargetAmount = "%.2f".format(goal.targetAmount),
            initialContribution = "%.2f".format(goal.contributionPerPeriod),
            onDismiss = { editingGoal = null },
            onSave = { name, targetAmount, contribution ->
                onUpdateGoal(
                    goal.copy(
                        name = name,
                        targetAmount = targetAmount,
                        contributionPerPeriod = contribution
                    )
                )
                editingGoal = null
            }
        )
    }

    deletingGoal?.let { goal ->
        AlertDialog(
            onDismissRequest = { deletingGoal = null },
            title = { Text("Delete Savings Goal?") },
            text = { Text("Delete \"${goal.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGoal(goal)
                    deletingGoal = null
                }) {
                    Text("Delete", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingGoal = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddEditSavingsGoalDialog(
    title: String,
    initialName: String,
    initialTargetAmount: String,
    initialContribution: String,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var targetAmountText by remember { mutableStateOf(initialTargetAmount) }
    var contributionText by remember { mutableStateOf(initialContribution) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetAmountText,
                    onValueChange = { newVal ->
                        if (newVal.isEmpty() || newVal.toDoubleOrNull() != null || newVal == ".") {
                            targetAmountText = newVal
                        }
                    },
                    label = { Text("Target Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contributionText,
                    onValueChange = { newVal ->
                        if (newVal.isEmpty() || newVal.toDoubleOrNull() != null || newVal == ".") {
                            contributionText = newVal
                        }
                    },
                    label = { Text("Contribution per Period") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val targetAmount = targetAmountText.toDoubleOrNull()
                    val contribution = contributionText.toDoubleOrNull()
                    if (name.isNotBlank() && targetAmount != null && targetAmount > 0 &&
                        contribution != null && contribution >= 0) {
                        onSave(name.trim(), targetAmount, contribution)
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
