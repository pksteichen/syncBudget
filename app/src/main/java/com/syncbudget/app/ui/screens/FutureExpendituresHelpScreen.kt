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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
                        text = "Future Expenditures Help",
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

            // ─── SECTION 1: WHAT ARE FLEs ───
            HelpSectionTitle("What Are Future Large Expenditures?")
            HelpBodyText(
                "Future Large Expenditures (FLEs) let you plan and save for big upcoming " +
                "expenses without blowing your budget. Instead of a large expense hitting " +
                "your available cash all at once, the app automatically reduces your daily " +
                "budget by a small amount to save up over time."
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

            // ─── SECTION 2: HEADER BAR ───
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
                    Text("Future Large Expenditures", style = MaterialTheme.typography.titleMedium, color = customColors.headerText)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = customColors.headerText, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(Icons.AutoMirrored.Filled.ArrowBack, "Back", "Return to the dashboard.")
            HelpIconRow(Icons.Filled.Pause, "Pause All", "Pause all active expenditures at once. Toggles to Play when all are paused.")
            HelpIconRow(Icons.AutoMirrored.Filled.Help, "Help", "Opens this help page.")
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                "The Pause All button only appears when you have at least one expenditure.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: ADDING AN EXPENDITURE ───
            HelpSectionTitle("Adding a Future Expenditure")
            HelpBodyText(
                "Tap \"Add Future Expenditure\" and fill in:"
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpNumberedItem(1, "Description", "What you're saving for (e.g., \"New Tires\", \"Vacation to Hawaii\", \"Holiday Gifts\").")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, "Amount", "The total cost you need to save.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, "Target Date", "When you need the money by. Tap to open a date picker.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: HOW DEDUCTIONS WORK ───
            HelpSectionTitle("How Budget Deductions Work")
            HelpBodyText(
                "For each active (non-paused) expenditure, the app calculates a per-period deduction:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(dimColor.copy(alpha = 0.08f))
                    .border(1.dp, dimColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "Deduction = (Amount \u2212 Total Saved So Far) \u00f7 Remaining Periods until Target Date",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    lineHeight = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpBodyText(
                "This deduction is subtracted from your Safe Budget Amount to produce " +
                "your actual Budget Amount. As you approach the target date, if you've been " +
                "saving consistently, the remaining amount and remaining periods both decrease, " +
                "keeping the deduction stable."
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                "The \"Total Saved So Far\" automatically increases each budget period based " +
                "on the deduction amount. You don't need to manually track savings.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: EXPENDITURE LIST ───
            HelpSectionTitle("Expenditure List")
            HelpBodyText("Each expenditure in the list shows:")
            Spacer(modifier = Modifier.height(6.dp))
            HelpBulletText("Description \u2014 what you're saving for")
            HelpBulletText("Total amount and target date")
            HelpBulletText("Budget reduction \u2014 how much is deducted per period (or \"Paused\" / \"Fully Saved!\")")
            HelpBulletText("Total Saved So Far \u2014 green text showing accumulated savings")
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Actions")
            HelpIconRow(Icons.Filled.Pause, "Pause", "Temporarily stop deductions for this item. Budget returns to normal while paused.")
            HelpIconRow(Icons.Filled.PlayArrow, "Resume", "Resume deductions. The per-period amount recalculates based on remaining time and savings.")
            HelpIconRow(Icons.Filled.Delete, "Delete", "Permanently remove the expenditure.", Color(0xFFF44336))
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText("Tap any expenditure to edit its description, amount, or target date.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: STATUSES ───
            HelpSectionTitle("Status Indicators")

            HelpSubSectionTitle("Active")
            HelpBodyText(
                "Normal state \u2014 the deduction is being applied each period and savings accumulate."
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Paused")
            HelpBodyText(
                "Deductions are temporarily stopped. The item appears dimmed. Pausing is useful " +
                "when you have a tight month and need the full budget temporarily. Savings progress " +
                "is preserved. When you resume, the deduction recalculates with the reduced remaining " +
                "time, so it will be slightly higher."
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Fully Saved")
            HelpBodyText(
                "Shows \"Fully Saved!\" in green when Total Saved So Far meets or exceeds the amount. " +
                "No further deductions are taken. You can delete the item or keep it as a record."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: MANUAL OVERRIDE WARNING ───
            HelpSectionTitle("Manual Budget Override")
            HelpBodyText(
                "If Manual Budget Override is enabled in Budget Configuration, a red warning " +
                "banner appears at the top of this screen. When manual override is active, " +
                "FLE deductions are NOT subtracted from your budget \u2014 you must account " +
                "for these expenses yourself."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: TIPS ───
            HelpSectionTitle("Tips")
            HelpBulletText("Create expenditures as early as possible \u2014 the more time you have, the smaller each period's deduction.")
            HelpBulletText("Use Pause strategically during tight months, but resume promptly to avoid a spike in deductions as the target date approaches.")
            HelpBulletText("Common uses: car maintenance, medical procedures, holiday gifts, vacations, electronics, furniture, annual subscriptions.")
            HelpBulletText("If an expenditure's target date passes and it's not fully saved, the remaining amount shows as the deduction amount.")
            HelpBulletText("Pair this with Amortization: use FLE to save before a purchase, and Amortization to spread costs after an unexpected purchase.")

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
