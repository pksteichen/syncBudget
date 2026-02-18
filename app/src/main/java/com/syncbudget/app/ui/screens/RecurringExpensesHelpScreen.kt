package com.syncbudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpensesHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Recurring Expenses Help",
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
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            val textColor = MaterialTheme.colorScheme.onBackground
            val dimColor = textColor.copy(alpha = 0.7f)
            val accentColor = MaterialTheme.colorScheme.primary
            val headerBg = customColors.headerBackground

            // ─── SECTION 1: OVERVIEW ───
            HelpSectionTitle("What Are Recurring Expenses?")
            HelpBodyText(
                "Recurring expenses are bills and payments that repeat on a regular schedule: " +
                "rent, mortgage, utilities, insurance, subscriptions, loan payments, and similar " +
                "obligations. By registering them here, the budget calculator accounts for these " +
                "costs automatically, so your daily/weekly/monthly budget reflects only what's " +
                "truly available for discretionary spending."
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Key concept box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.07f))
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Why This Matters",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "Without recurring expenses in the budget calculator, your budget amount " +
                        "would be based on income alone. You'd see a high daily budget, spend freely, " +
                        "and then scramble when rent is due. Registering expenses ensures the budget " +
                        "reserves enough for bills, even in months where several bills cluster together.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: HEADER BAR ───
            HelpSectionTitle("Header Bar")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(headerBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = customColors.headerText, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Recurring Expenses", style = MaterialTheme.typography.titleMedium, color = customColors.headerText)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = customColors.headerText, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(Icons.AutoMirrored.Filled.ArrowBack, "Back", "Return to the dashboard.")
            HelpIconRow(Icons.AutoMirrored.Filled.Help, "Help", "Opens this help page.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: ADDING AN EXPENSE ───
            HelpSectionTitle("Adding a Recurring Expense")
            HelpBodyText(
                "Tap \"Add Recurring Expense\" and fill in:"
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpNumberedItem(1, "Source Name", "A descriptive name for the expense (e.g., \"Rent\", \"Netflix\", \"Car Insurance\"). Important: this name is matched against bank transaction merchant names for automatic recognition.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, "Amount", "The amount per occurrence.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: REPEAT SETTINGS ───
            HelpSectionTitle("Repeat Settings")
            HelpBodyText(
                "Every recurring expense needs a repeat schedule so the budget calculator " +
                "knows when to expect the charge. Tap the sync icon on any expense to configure:"
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Every X Days")
            HelpBodyText(
                "The expense occurs every N days (1\u201360). Requires a Start Date. " +
                "Useful for irregular-interval expenses like medication refills."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Every X Weeks")
            HelpBodyText(
                "The expense occurs every N weeks (1\u201318). Requires a Start Date. " +
                "The day of the week is determined by the start date."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Every 2 Weeks (Bi-Weekly)")
            HelpBodyText(
                "Occurs every 14 days from the start date. Useful for bi-weekly " +
                "recurring charges. Results in 26 occurrences per year."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Every X Months")
            HelpBodyText(
                "Occurs on a specific day of the month, every N months (1\u20133). " +
                "Enter the Day of Month (1\u201328). Most bills use this type: rent on the 1st, " +
                "phone bill on the 15th, etc."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Twice per Month (Bi-Monthly)")
            HelpBodyText(
                "Occurs on two specific days each month. Enter both days (1\u201328 each). " +
                "Useful for expenses that bill twice monthly."
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpBodyText(
                "All day-of-month values are limited to 28 to ensure the date exists in every " +
                "month, including February.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: EXPENSE LIST ───
            HelpSectionTitle("Expense List")
            HelpBodyText("Each recurring expense in the list shows:")
            Spacer(modifier = Modifier.height(6.dp))
            HelpBulletText("Source name")
            HelpBulletText("Amount per occurrence")
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Actions")
            HelpBodyText("Tap an expense to edit its name and amount.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpIconRow(Icons.Filled.Sync, "Repeat Settings", "Configure or change the repeat schedule.")
            HelpIconRow(Icons.Filled.Delete, "Delete", "Permanently remove the recurring expense.", Color(0xFFF44336))
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: HOW THEY AFFECT YOUR BUDGET ───
            HelpSectionTitle("How Recurring Expenses Affect Your Budget")
            HelpBodyText(
                "Recurring expenses play two roles in the budget system:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("1. Budget Calculation (Timing Safety)")
            HelpBodyText(
                "The budget calculator projects all recurring expenses forward one year and " +
                "simulates each budget period. It ensures your budget amount is high enough " +
                "to cover bills even in months where multiple expenses cluster together. " +
                "Without this, you might have enough money overall but not enough in a " +
                "particular week or month."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("2. Automatic Transaction Matching")
            HelpBodyText(
                "When you add a transaction (manually or via bank import), the app checks " +
                "whether the merchant name and amount match any recurring expense. If a match " +
                "is found, you're shown a confirmation dialog:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("\"Yes, Recurring\" \u2014 the transaction is tagged as a recurring expense and does NOT reduce your available cash (since it's already accounted for in the budget)")
            HelpBulletText("\"No, Regular\" \u2014 the transaction is treated as a normal expense")
            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF4CAF50).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Why Matching Matters",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "Your budget amount already has recurring expenses \"baked in\" \u2014 the " +
                        "calculator reserved money for them. If a recurring expense also subtracted " +
                        "from available cash, it would be double-counted. The matching system prevents " +
                        "this by recognizing recurring transactions and keeping them from affecting " +
                        "your spending money.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: SOURCE NAME MATCHING ───
            HelpSectionTitle("Source Name Matching")
            HelpBodyText(
                "The automatic recognition system matches transaction merchant names against " +
                "your recurring expense source names. For best results:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("Use descriptive names that overlap with how the expense appears on bank statements")
            HelpBulletText("For example, \"State Farm\" will match \"STATE FARM INSURANCE\" from your bank")
            HelpBulletText("The match looks for common substrings, so partial matches work")
            HelpBulletText("Amount must also be within 1% for the match to trigger")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: TIPS ───
            HelpSectionTitle("Tips")
            HelpBulletText("Add ALL recurring expenses, even small ones like streaming subscriptions. They add up and the budget calculator needs the full picture.")
            HelpBulletText("If an expense amount varies slightly (like a utility bill), use the average amount.")
            HelpBulletText("Remember to Recalculate your budget (in Budget Configuration) after adding or removing recurring expenses.")
            HelpBulletText("Common expenses to add: rent/mortgage, utilities (electric, gas, water), insurance (car, health, home), subscriptions (streaming, gym, software), loan payments, phone bill.")
            HelpBulletText("If an expense is truly one-time, don't add it here. Use Amortization instead to spread it over time.")
            HelpBulletText("Check your bank statements to make sure you haven't missed any recurring charges.")

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
