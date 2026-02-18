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
import androidx.compose.foundation.layout.width
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
fun BudgetConfigHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Budget Configuration Help",
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
            HelpSectionTitle("Overview")
            HelpBodyText(
                "Budget Configuration is the core setup screen where you define your income " +
                "sources, choose your budget period, and calculate your safe spending amount. " +
                "The budget engine uses this information to determine how much you can safely " +
                "spend each period."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: BUDGET PERIOD ───
            HelpSectionTitle("Budget Period")
            HelpBodyText(
                "The budget period determines how often your available cash is replenished. " +
                "Choose the period that best matches how you think about spending:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpNumberedItem(1, "Daily", "Your budget is calculated per day. Best for tight budgets or people who want maximum daily awareness.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, "Weekly", "Your budget is calculated per week. Good for people who plan expenses by the week.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, "Monthly", "Your budget is calculated per month. Suits people with monthly pay who prefer monthly planning.")
            Spacer(modifier = Modifier.height(10.dp))

            HelpBodyText(
                "The period also affects how Future Expenditure and Amortization deductions are calculated, " +
                "since they deduct a fixed amount per period.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: RESET SETTINGS ───
            HelpSectionTitle("Reset Settings")
            HelpBodyText(
                "Tap the \"Reset\" button next to the Budget Period selector to configure " +
                "when your budget period rolls over:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Reset Hour")
            HelpBodyText(
                "The hour of the day when a new period begins and your budget amount is " +
                "added to available cash. Default is 12 AM (midnight). Set this to when " +
                "you typically start your day \u2014 for example, 6 AM if you're an early riser."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Day of Week (Weekly)")
            HelpBodyText(
                "For weekly budgets, choose which day the new week starts. For example, " +
                "if you set Monday, your budget resets every Monday at the configured reset hour."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Day of Month (Monthly)")
            HelpBodyText(
                "For monthly budgets, choose which day of the month the new period starts " +
                "(1\u201328). If you're paid on the 1st, set this to 1. If paid on the 15th, " +
                "set it to 15. The maximum is 28 to ensure the date exists in all months."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: SAFE BUDGET AMOUNT ───
            HelpSectionTitle("Safe Budget Amount")
            HelpBodyText(
                "The Safe Budget Amount is the calculated maximum you can spend per period " +
                "while still covering all your recurring expenses. It is displayed at the top " +
                "of the configuration screen."
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("How It's Calculated")
            HelpBodyText(
                "The engine projects your income and expenses forward one year:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(1, "Income summing", "All income source occurrences are generated for the next 12 months based on their repeat schedules. Total annual income is computed.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, "Base amount", "The base budget is annual income divided by the number of budget periods in a year (e.g., 365 for daily, 52 for weekly, 12 for monthly).")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, "Timing safety", "The engine simulates each period and checks that cumulative expenses never exceed the budget. If bills cluster in certain periods, the budget amount is increased to cover the worst case.")
            Spacer(modifier = Modifier.height(10.dp))

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
                        "Important",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "The Safe Budget Amount only considers income sources and recurring expenses " +
                        "that have complete repeat schedule configurations. If a source has no repeat " +
                        "settings, it will be excluded from the calculation. Make sure to configure " +
                        "repeat schedules for all your income sources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: RECALCULATE & RESET ───
            HelpSectionTitle("Recalculate & Reset Budget")

            HelpSubSectionTitle("Recalculate")
            HelpBodyText(
                "Tap \"Recalculate\" to recompute the Safe Budget Amount based on your current " +
                "income sources and recurring expenses. Do this whenever you:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("Add, remove, or change an income source")
            HelpBulletText("Add, remove, or change a recurring expense")
            HelpBulletText("Change the budget period")
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                "On first use, Recalculate also initializes your budget tracking \u2014 it sets " +
                "the budget start date and gives you one period's budget as starting available cash.",
                italic = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            HelpSubSectionTitle("Reset Budget")
            HelpBodyText(
                "Tap \"Reset Budget\" when you need a fresh start. This:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("Recalculates the safe budget amount")
            HelpBulletText("Resets the budget start date to today")
            HelpBulletText("Sets available cash to one period's budget amount")
            HelpBulletText("Does NOT delete your transactions")
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFF9800).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "When to Reset",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "Use Reset Budget when your available cash has drifted from reality " +
                        "(e.g., after a major life change like a new job or move), or when " +
                        "you've made significant changes to your income sources or expenses " +
                        "and want to start fresh. Resetting will lose your accumulated surplus " +
                        "or deficit, so use it deliberately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: MANUAL OVERRIDE ───
            HelpSectionTitle("Manual Budget Override")
            HelpBodyText(
                "Check \"Manual Budget Override\" to set your own per-period budget amount " +
                "instead of using the calculated value. When enabled:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("A text field appears where you enter your desired amount per period")
            HelpBulletText("The safe budget calculation is ignored")
            HelpBulletText("Amortization and Future Expenditure deductions are disabled")
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF44336).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFFF44336).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Warning",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFF44336)
                    )
                    Text(
                        "Manual override disables the safety net provided by the budget calculator. " +
                        "Amortization and Future Expenditure deductions will not be subtracted " +
                        "from your budget. Red warning banners will appear on the Amortization " +
                        "and Future Expenditures screens when manual override is active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: INCOME SOURCES ───
            HelpSectionTitle("Income Sources")
            HelpBodyText(
                "Income sources represent your reliable, recurring income \u2014 the money you " +
                "can count on for budgeting purposes. Add all consistent income streams: " +
                "salary, freelance retainers, pension, recurring side income, etc."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Adding an Income Source")
            HelpBodyText(
                "Tap \"Add Income Source\" and fill in:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("Source Name \u2014 a descriptive name (e.g., \"Main Job Paycheck\"). This is also used for budget income detection when you add transactions.")
            HelpBulletText("Amount \u2014 the amount you receive per occurrence")
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Variable Pay")
            HelpBodyText(
                "If your pay varies (for example, a large paycheck and a small paycheck " +
                "each month), create separate entries for each amount. The budget calculator " +
                "will handle the different amounts correctly."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Managing Income Sources")
            HelpBulletText("Tap a source to edit its name and amount")
            HelpIconRow(Icons.Filled.Sync, "Repeat Settings", "Configure the income schedule (when you get paid)")
            HelpIconRow(Icons.Filled.Delete, "Delete", "Permanently remove the income source", Color(0xFFF44336))
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: REPEAT SETTINGS ───
            HelpSectionTitle("Repeat Schedules")
            HelpBodyText(
                "Every income source needs a repeat schedule so the budget calculator knows " +
                "when to expect payments. The same repeat types are available for income sources " +
                "and recurring expenses:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Every X Days")
            HelpBodyText(
                "Income arrives every N days (1\u201360). Requires a Start Date \u2014 the date " +
                "of any past or future occurrence. The engine calculates all future dates from " +
                "this reference point."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Every X Weeks")
            HelpBodyText(
                "Income arrives every N weeks (1\u201318). Requires a Start Date. The day of " +
                "the week is determined by your start date (e.g., if your start date falls on " +
                "a Friday, income repeats every N Fridays)."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Every 2 Weeks (Bi-Weekly)")
            HelpBodyText(
                "A common pay schedule. Requires a Start Date. Income arrives every 14 days " +
                "from the start date. This is different from \"Twice per Month\" \u2014 bi-weekly " +
                "results in 26 pay periods per year, not 24."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Every X Months")
            HelpBodyText(
                "Income arrives on a specific day of the month, every N months (1\u20133). " +
                "Enter the Day of Month (1\u201328). No start date is needed \u2014 the engine " +
                "uses the day number directly."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Twice per Month (Bi-Monthly)")
            HelpBodyText(
                "Income arrives on two specific days each month. Enter both the First Day and " +
                "Second Day (1\u201328 each). For example, if you're paid on the 1st and 15th, " +
                "enter 1 and 15. This results in exactly 24 occurrences per year."
            )
            Spacer(modifier = Modifier.height(10.dp))

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
                        "Day Limit: 1\u201328",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "Day-of-month values are limited to 28 to ensure the date exists in all months, " +
                        "including February. If your actual pay date is the 29th, 30th, or 31st, " +
                        "use 28 as the closest approximation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 9: BUDGET INCOME DETECTION ───
            HelpSectionTitle("Budget Income Detection")
            HelpBodyText(
                "When you add an income transaction in the Transactions screen, the app " +
                "checks whether it matches one of your configured income sources (by name " +
                "and amount). If a match is found, you're asked whether this is:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("Budget income \u2014 already accounted for in your budget (does NOT increase available cash)")
            HelpBulletText("Extra income \u2014 unexpected or additional income (DOES increase available cash)")
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                "This prevents your paycheck from being double-counted \u2014 once in the budget " +
                "calculation and again as a manual income entry.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 10: TIPS ───
            HelpSectionTitle("Tips")
            HelpBulletText("Set up all income sources and recurring expenses BEFORE hitting Recalculate for the best result.")
            HelpBulletText("Recalculate after any change to income or expenses to keep your budget accurate.")
            HelpBulletText("Use Reset Budget sparingly \u2014 it wipes your accumulated surplus/deficit.")
            HelpBulletText("For variable income, create separate entries for each pay amount to improve accuracy.")
            HelpBulletText("Use descriptive source names like \"Acme Corp Paycheck\" \u2014 the name is used for automatic budget income matching.")
            HelpBulletText("Only include reliable, recurring income. Don't add one-time windfalls \u2014 record those as extra income in Transactions.")

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
