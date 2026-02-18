package com.syncbudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncbudget.app.sound.FlipSoundPlayer
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS
import com.syncbudget.app.ui.components.FlipDisplay
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    soundPlayer: FlipSoundPlayer,
    currencySymbol: String,
    digitCount: Int,
    showDecimals: Boolean,
    availableCash: Double = 0.0,
    budgetAmount: Double = 0.0,
    budgetStartDate: String? = null,
    onSettingsClick: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val customColors = LocalSyncBudgetColors.current

    val decimalPlaces = if (showDecimals) (CURRENCY_DECIMALS[currencySymbol] ?: 2) else 0

    val isNegative = availableCash < 0
    val displayAmount = if (decimalPlaces > 0) {
        var divisor = 1
        repeat(decimalPlaces) { divisor *= 10 }
        (abs(availableCash) * divisor).roundToInt()
    } else {
        abs(availableCash).roundToInt()
    }

    val bottomLabel = if (budgetStartDate == null) {
        "Not configured"
    } else {
        val periodLabel = when {
            budgetAmount == 0.0 && !isNegative -> "Recalculate budget"
            else -> "$currencySymbol${"%.2f".format(budgetAmount)}/period"
        }
        periodLabel
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "SecureSync Daily Budget",
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = customColors.headerText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate("dashboard_help") }) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FlipDisplay(
                amount = displayAmount,
                isNegative = isNegative,
                currencySymbol = currencySymbol,
                digitCount = digitCount,
                decimalPlaces = decimalPlaces,
                soundPlayer = soundPlayer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                bottomLabel = bottomLabel
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onNavigate("transactions") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Transactions",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = { onNavigate("savings") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Savings,
                        contentDescription = "Savings",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = { onNavigate("future_expenditures") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Future Large Expenditures",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = { onNavigate("amortization") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "Amortization",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = { onNavigate("recurring_expenses") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = "Recurring Expenses",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
