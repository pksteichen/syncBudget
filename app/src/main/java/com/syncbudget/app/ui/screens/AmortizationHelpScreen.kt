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
fun AmortizationHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Amortization Help",
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

            // ─── SECTION 1: WHAT IS AMORTIZATION ───
            HelpSectionTitle("What Is Amortization?")
            HelpBodyText(
                "Amortization lets you spread the impact of a large expense across " +
                "multiple budget periods. Instead of the full cost destroying your budget " +
                "in a single day/week/month, the cost is divided evenly and deducted " +
                "from your budget over time."
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
                        "Your car unexpectedly needs a \$900 repair. On a daily budget of \$40/day, " +
                        "that would wipe out more than 22 days of budget. Instead, you create an " +
                        "amortization entry for \$900 over 90 days. Your budget is reduced by only " +
                        "\$10/day for 90 days, keeping you above water while the cost is absorbed gradually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Amortization vs. Future Expenditures")
            HelpBodyText(
                "These two features are complementary:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("Future Expenditures \u2014 save BEFORE a planned expense (proactive)")
            HelpBulletText("Amortization \u2014 spread AFTER an unplanned or past expense (reactive)")
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
                    Text("Amortization", style = MaterialTheme.typography.titleMedium, color = customColors.headerText)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = customColors.headerText, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(Icons.AutoMirrored.Filled.ArrowBack, "Back", "Return to the dashboard.")
            HelpIconRow(Icons.AutoMirrored.Filled.Help, "Help", "Opens this help page.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: ADDING AN ENTRY ───
            HelpSectionTitle("Adding an Amortization Entry")
            HelpBodyText(
                "Tap \"Add Amortization Entry\" and fill in:"
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpNumberedItem(1, "Source Name", "A descriptive name for the expense (e.g., \"Car Repair\", \"Emergency Room Visit\", \"New Laptop\"). Important: this name is matched against bank transaction merchant names for automatic recognition.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, "Total Amount", "The full cost of the expense.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, "Budget Periods", "How many periods to spread the cost over. The label shows your current period type (days, weeks, or months).")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(4, "Start Date", "When the amortization begins (usually the date of the expense).")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: HOW DEDUCTIONS WORK ───
            HelpSectionTitle("How Deductions Work")
            HelpBodyText(
                "The per-period deduction is straightforward:"
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
                    "Deduction = Total Amount \u00f7 Number of Budget Periods",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    lineHeight = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpBodyText(
                "This deduction is subtracted from your Safe Budget Amount (along with any " +
                "FLE deductions) to produce your actual Budget Amount. The deduction remains " +
                "constant for the full amortization period, then stops automatically."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: ENTRY LIST ───
            HelpSectionTitle("Entry List")
            HelpBodyText("Each amortization entry displays:")
            Spacer(modifier = Modifier.height(6.dp))
            HelpBulletText("Source name")
            HelpBulletText("Total amount and per-period deduction")
            HelpBulletText("Progress \u2014 \"X of Y [periods] complete\" or \"Completed\" in green")
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Actions")
            HelpBodyText("Tap an entry to edit its details (source name, amount, periods, start date).")
            Spacer(modifier = Modifier.height(4.dp))
            HelpIconRow(Icons.Filled.Delete, "Delete", "Permanently remove the amortization entry.", Color(0xFFF44336))
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: AUTOMATIC MATCHING ───
            HelpSectionTitle("Automatic Transaction Matching")
            HelpBodyText(
                "When you add a transaction (manually or via bank import), the app checks " +
                "whether the merchant name and amount match any of your amortization entries. " +
                "If a match is found, you're shown a confirmation dialog:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("\"Yes, Amortization\" \u2014 the transaction is tagged as amortized and does NOT reduce your available cash (since the cost is already being deducted from your budget over time)")
            HelpBulletText("\"No, Regular\" \u2014 the transaction is treated as a normal expense")
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
                        "Source Name Matching",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "Use descriptive names for your amortization sources. The matching " +
                        "algorithm looks for common substrings between the source name and " +
                        "the transaction merchant name. For example, a source named \"Toyota Service\" " +
                        "would match a bank transaction from \"TOYOTA SERVICE CENTER\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: MANUAL OVERRIDE WARNING ───
            HelpSectionTitle("Manual Budget Override")
            HelpBodyText(
                "If Manual Budget Override is enabled in Budget Configuration, a red warning " +
                "banner appears at the top of this screen. When manual override is active, " +
                "amortization deductions are NOT subtracted from your budget \u2014 you must " +
                "account for these costs yourself."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: TIPS ───
            HelpSectionTitle("Tips")
            HelpBulletText("Choose a number of periods that results in a comfortable per-period deduction. If \$10/day feels too much, spread over more days.")
            HelpBulletText("Use amortization for any expense that would otherwise devastate your budget: medical bills, car repairs, appliance replacements, emergency travel.")
            HelpBulletText("Completed entries (all periods elapsed) can be deleted to keep the list clean.")
            HelpBulletText("Remember to also record the actual transaction \u2014 amortization only adjusts your budget rate, it doesn't record the expense itself.")
            HelpBulletText("If you knew about the expense in advance, Future Expenditures would have been the better tool. Use Amortization for surprises.")

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
