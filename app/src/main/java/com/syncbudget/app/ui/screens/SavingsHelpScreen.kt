package com.syncbudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Savings Help",
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
            HelpSectionTitle("Savings Goals")
            HelpBodyText(
                "The Savings page lets you create savings goals with target amounts and " +
                "per-period contributions. Each budget period, the contribution amount for each " +
                "active goal is deducted from your budget, effectively setting aside money toward " +
                "your goals."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            HelpSectionTitle("Creating a Goal")
            HelpBodyText(
                "Tap \"Add Savings Goal\" to create a new goal. You'll need to provide:"
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpBulletText("A name for the goal (e.g., \"Emergency Fund\", \"Vacation\")")
            HelpBulletText("A target amount \u2014 how much you want to save in total")
            HelpBulletText("A contribution per period \u2014 how much to set aside each budget period")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            HelpSectionTitle("Progress Tracking")
            HelpBodyText(
                "Each goal shows a progress bar indicating how much you've saved relative to " +
                "the target amount. The \"Saved\" line shows the exact amounts. When a goal " +
                "reaches its target, it displays \"Goal reached!\" and stops deducting from " +
                "your budget."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            HelpSectionTitle("Budget Deductions")
            HelpBodyText(
                "For each active (non-paused) goal that hasn't reached its target, the " +
                "contribution per period is subtracted from your budget amount each period. " +
                "This works the same way as Future Large Expenditure deductions \u2014 your " +
                "available spending money is reduced, and the difference is tracked as savings."
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                "When a new budget period starts, the contribution is automatically added to " +
                "the goal's saved amount (capped at the target). If manual budget override is " +
                "active, deductions are disabled but goals still track progress."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            HelpSectionTitle("Pause & Resume")
            HelpBodyText(
                "Tap the pause/play button next to a goal to pause or resume its contributions. " +
                "Paused goals do not deduct from your budget and do not accumulate savings. " +
                "Use the pause-all button in the top bar to pause or resume all goals at once."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            HelpSectionTitle("Editing & Deleting")
            HelpBodyText(
                "Tap a goal to edit its name, target amount, or contribution. Tap the trash " +
                "icon to delete a goal. Deleting a goal removes it permanently \u2014 the saved " +
                "amount is not returned to your available cash."
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
