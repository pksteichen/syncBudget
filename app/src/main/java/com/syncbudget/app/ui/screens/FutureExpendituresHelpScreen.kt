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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Savings
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
fun FutureExpendituresHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Savings Goals Help",
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

            // ─── SECTION 1: WHAT ARE SAVINGS GOALS ───
            HelpSectionTitle("What Are Savings Goals?")
            HelpBodyText(
                "Savings Goals let you plan and save for future expenses or financial " +
                "targets without blowing your budget. Instead of a large expense hitting " +
                "your available cash all at once, the app automatically reduces your budget " +
                "by a small amount each period to save up over time."
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Example box
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
                        "Example",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "You need new tires in 6 months, estimated cost: \$600. " +
                        "On a daily budget, the app deducts about \$3.29/day from your budget. " +
                        "You barely notice the daily reduction, but when tire day arrives, " +
                        "the money is ready. No surprise, no stress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: TWO GOAL TYPES ───
            HelpSectionTitle("Two Goal Types")
            HelpBodyText(
                "Savings Goals supports two different approaches to saving:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Target Date")
            HelpBodyText(
                "Set a date by which you need the money. The app automatically calculates " +
                "how much to deduct each period based on the remaining amount and remaining " +
                "time. As you get closer to the date, the deduction adjusts dynamically."
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Fixed Contribution")
            HelpBodyText(
                "Set a fixed amount to contribute each budget period. There's no target date \u2014 " +
                "the app simply deducts your chosen amount every period until the goal is " +
                "reached. This is ideal for open-ended savings like an emergency fund."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: HEADER BAR ───
            HelpSectionTitle("Header Bar")
            HelpBodyText("The header provides navigation and bulk actions:")
            Spacer(modifier = Modifier.height(8.dp))

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
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.Pause, contentDescription = null, tint = customColors.headerText, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Savings Goals", style = MaterialTheme.typography.titleMedium, color = customColors.headerText)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = customColors.headerText, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(Icons.AutoMirrored.Filled.ArrowBack, "Back", "Return to the dashboard.")
            HelpIconRow(Icons.Filled.Pause, "Pause All", "Pause all active goals at once. Toggles to Play when all are paused.")
            HelpIconRow(Icons.AutoMirrored.Filled.Help, "Help", "Opens this help page.")
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                "The Pause All button only appears when you have at least one goal.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: ADDING A GOAL ───
            HelpSectionTitle("Adding a Savings Goal")
            HelpBodyText(
                "Tap \"Add Savings Goal\" and fill in:"
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpNumberedItem(1, "Name", "What you're saving for (e.g., \"New Tires\", \"Vacation to Hawaii\", \"Emergency Fund\").")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, "Target Amount", "The total cost you need to save.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, "Starting Saved Amount", "Optional. If you already have some money saved toward this goal, enter it here to pre-fill the progress bar.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(4, "Goal Type", "Choose \"Target Date\" to set a deadline, or \"Fixed Contribution\" for a regular per-period amount.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(5, "Target Date / Contribution", "Depending on the goal type, select a target date or enter a contribution per period.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: HOW DEDUCTIONS WORK ───
            HelpSectionTitle("How Budget Deductions Work")
            HelpBodyText(
                "For each active (non-paused) goal, the app calculates a per-period deduction:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Target Date Goals")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(dimColor.copy(alpha = 0.08f))
                    .border(1.dp, dimColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "Deduction = (Target Amount \u2212 Saved So Far) \u00f7 Remaining Periods until Target Date",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    lineHeight = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Fixed Contribution Goals")
            HelpBodyText(
                "The deduction equals the contribution per period you set when creating the goal. " +
                "It stays constant until the goal is reached."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpBodyText(
                "These deductions are subtracted from your Safe Budget Amount to produce " +
                "your actual Budget Amount. The \"Saved So Far\" automatically increases each " +
                "budget period based on the deduction amount.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: PROGRESS TRACKING ───
            HelpSectionTitle("Progress Tracking")
            HelpBodyText("Each goal in the list shows:")
            Spacer(modifier = Modifier.height(6.dp))
            HelpBulletText("Name \u2014 what you're saving for")
            HelpBulletText("Target amount (and target date for date-based goals)")
            HelpBulletText("Budget deduction or contribution per period")
            HelpBulletText("Progress bar \u2014 visual indicator of how close you are to the target")
            HelpBulletText("Saved amount \u2014 green text showing accumulated savings vs. target")
            HelpBulletText("\"Goal reached!\" label when fully saved")
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Actions")
            HelpIconRow(Icons.Filled.Pause, "Pause", "Temporarily stop deductions for this goal. Budget returns to normal while paused.")
            HelpIconRow(Icons.Filled.PlayArrow, "Resume", "Resume deductions. The per-period amount recalculates based on remaining time and savings.")
            HelpIconRow(Icons.Filled.Delete, "Delete", "Permanently remove the savings goal.", Color(0xFFF44336))
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText("Tap any goal to edit its name, target amount, goal type, or other settings.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: STATUSES ───
            HelpSectionTitle("Status Indicators")

            HelpSubSectionTitle("Active")
            HelpBodyText(
                "Normal state \u2014 the deduction is being applied each period and savings accumulate."
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Paused")
            HelpBodyText(
                "Deductions are temporarily stopped. The goal appears dimmed. Pausing is useful " +
                "when you have a tight month and need the full budget temporarily. Savings progress " +
                "is preserved. When you resume, the deduction recalculates with the reduced remaining " +
                "time (for target-date goals), so it will be slightly higher."
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Goal Reached")
            HelpBodyText(
                "Shows \"Goal reached!\" in green when Saved So Far meets or exceeds the target. " +
                "No further deductions are taken. You can delete the goal or keep it as a record."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: MANUAL OVERRIDE WARNING ───
            HelpSectionTitle("Manual Budget Override")
            HelpBodyText(
                "If Manual Budget Override is enabled in Budget Configuration, a red warning " +
                "banner appears at the top of this screen. When manual override is active, " +
                "Savings Goal deductions are NOT subtracted from your budget \u2014 you must account " +
                "for these expenses yourself."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 9: TIPS ───
            HelpSectionTitle("Tips")
            HelpBulletText("Create target-date goals as early as possible \u2014 the more time you have, the smaller each period's deduction.")
            HelpBulletText("Use fixed contribution goals for open-ended savings like emergency funds or general savings.")
            HelpBulletText("Use Pause strategically during tight months, but resume promptly to avoid a spike in deductions as the target date approaches.")
            HelpBulletText("Enter a starting saved amount when creating a goal if you already have money set aside.")
            HelpBulletText("Common uses: car maintenance, medical procedures, holiday gifts, vacations, electronics, furniture, annual subscriptions, emergency fund.")
            HelpBulletText("Pair this with Amortization: use Savings Goals to save before a purchase, and Amortization to spread costs after an unexpected purchase.")

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
