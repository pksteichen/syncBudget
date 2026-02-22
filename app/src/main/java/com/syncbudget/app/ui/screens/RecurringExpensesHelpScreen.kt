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
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpensesHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.recurringExpensesHelp.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = S.common.back,
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
            HelpSectionTitle(S.recurringExpensesHelp.whatTitle)
            HelpBodyText(S.recurringExpensesHelp.whatBody)
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
                        S.recurringExpensesHelp.whyTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        S.recurringExpensesHelp.whyBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: HEADER BAR ───
            HelpSectionTitle(S.recurringExpensesHelp.headerTitle)

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
                    Text(S.dashboard.recurringExpenses, style = MaterialTheme.typography.titleMedium, color = customColors.headerText)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = customColors.headerText, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(Icons.AutoMirrored.Filled.ArrowBack, S.common.back, S.recurringExpensesHelp.backDesc)
            HelpIconRow(Icons.AutoMirrored.Filled.Help, S.common.help, S.recurringExpensesHelp.helpDesc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: ADDING AN EXPENSE ───
            HelpSectionTitle(S.recurringExpensesHelp.addingTitle)
            HelpBodyText(S.recurringExpensesHelp.addingBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpNumberedItem(1, S.recurringExpensesHelp.addStep1, S.recurringExpensesHelp.addStep1Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, S.recurringExpensesHelp.addStep2, S.recurringExpensesHelp.addStep2Desc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: REPEAT SETTINGS ───
            HelpSectionTitle(S.recurringExpensesHelp.repeatTitle)
            HelpBodyText(S.recurringExpensesHelp.repeatBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.recurringExpensesHelp.everyXDaysTitle)
            HelpBodyText(S.recurringExpensesHelp.everyXDaysBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.recurringExpensesHelp.everyXWeeksTitle)
            HelpBodyText(S.recurringExpensesHelp.everyXWeeksBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.recurringExpensesHelp.everyXMonthsTitle)
            HelpBodyText(S.recurringExpensesHelp.everyXMonthsBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.recurringExpensesHelp.biMonthlyTitle)
            HelpBodyText(S.recurringExpensesHelp.biMonthlyBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.recurringExpensesHelp.annualTitle)
            HelpBodyText(S.recurringExpensesHelp.annualBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpBodyText(
                S.recurringExpensesHelp.dayLimitNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: EXPENSE LIST ───
            HelpSectionTitle(S.recurringExpensesHelp.expenseListTitle)
            HelpBodyText(S.recurringExpensesHelp.expenseListBody)
            Spacer(modifier = Modifier.height(6.dp))
            HelpBulletText(S.recurringExpensesHelp.expenseSource)
            HelpBulletText(S.recurringExpensesHelp.expenseAmount)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.recurringExpensesHelp.actionsTitle)
            HelpBodyText(S.recurringExpensesHelp.editNote)
            Spacer(modifier = Modifier.height(4.dp))
            HelpIconRow(Icons.Filled.Sync, S.common.repeatType, S.recurringExpensesHelp.repeatSettingsDesc)
            HelpIconRow(Icons.Filled.Delete, S.common.delete, S.recurringExpensesHelp.deleteDesc, Color(0xFFF44336))
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: HOW THEY AFFECT YOUR BUDGET ───
            HelpSectionTitle(S.recurringExpensesHelp.budgetEffectTitle)
            HelpBodyText(S.recurringExpensesHelp.budgetEffectBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.recurringExpensesHelp.timingSafetyTitle)
            HelpBodyText(S.recurringExpensesHelp.timingSafetyBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.recurringExpensesHelp.autoMatchTitle)
            HelpBodyText(S.recurringExpensesHelp.autoMatchBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.recurringExpensesHelp.yesRecurringBullet)
            HelpBulletText(S.recurringExpensesHelp.noRegularBullet)
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
                        S.recurringExpensesHelp.whyMatchingTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        S.recurringExpensesHelp.whyMatchingBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: SOURCE NAME MATCHING ───
            HelpSectionTitle(S.recurringExpensesHelp.sourceMatchTitle)
            HelpBodyText(S.recurringExpensesHelp.sourceMatchBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.recurringExpensesHelp.matchBullet1)
            HelpBulletText(S.recurringExpensesHelp.matchBullet2)
            HelpBulletText(S.recurringExpensesHelp.matchBullet3)
            HelpBulletText(S.recurringExpensesHelp.matchBullet4)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: TIPS ───
            HelpSectionTitle(S.recurringExpensesHelp.tipsTitle)
            HelpBulletText(S.recurringExpensesHelp.tip1)
            HelpBulletText(S.recurringExpensesHelp.tip2)
            HelpBulletText(S.recurringExpensesHelp.tip3)
            HelpBulletText(S.recurringExpensesHelp.tip4)
            HelpBulletText(S.recurringExpensesHelp.tip5)
            HelpBulletText(S.recurringExpensesHelp.tip6)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
