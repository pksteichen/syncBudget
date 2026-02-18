package com.syncbudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            val textColor = MaterialTheme.colorScheme.onBackground
            val accentColor = MaterialTheme.colorScheme.primary

            HelpSectionTitle("Savings")
            HelpBodyText(
                "The Savings feature is coming soon. It will allow you to set savings goals, " +
                "track progress toward them, and integrate with the budget system to " +
                "automatically set aside money each period."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            HelpSectionTitle("What to Expect")
            HelpBodyText(
                "The planned Savings feature will include:"
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpBulletText("Named savings goals with target amounts (e.g., \"Emergency Fund \u2014 \$5,000\")")
            HelpBulletText("Progress tracking with visual indicators")
            HelpBulletText("Automatic per-period contributions deducted from your budget")
            HelpBulletText("Flexible contribution amounts \u2014 save more in good months, less in tight ones")
            HelpBulletText("Goal completion celebrations")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            HelpSectionTitle("In the Meantime")
            HelpBodyText(
                "While the Savings feature is being developed, you can use these strategies " +
                "to track savings:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Using Future Expenditures")
            HelpBodyText(
                "Future Expenditures can serve as a savings tool for goal-based saving. " +
                "Create an entry with the amount you want to save and a target date. The app " +
                "will automatically deduct a small amount from your budget each period, " +
                "effectively saving for you."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Watching Available Cash")
            HelpBodyText(
                "If you consistently spend less than your budget amount, your available cash " +
                "grows naturally. This accumulated surplus is a form of savings. The Solari " +
                "display makes this visible \u2014 if your number keeps growing, you're saving."
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Coming soon box
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
                        "Coming Soon",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "The Savings feature is actively being developed and will be available " +
                        "in a future update. Stay tuned!",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
