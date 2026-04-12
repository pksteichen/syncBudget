package com.techadvantage.budgetrak.ui.screens

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
import com.techadvantage.budgetrak.ui.strings.LocalStrings
import com.techadvantage.budgetrak.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalsHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.savingsGoalsHelp.title,
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

            // ─── SECTION 1: WHAT ARE SAVINGS GOALS ───
            HelpSectionTitle(S.savingsGoalsHelp.whatTitle)
            HelpBodyText(S.savingsGoalsHelp.whatBody)
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
                        S.savingsGoalsHelp.exampleTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        S.savingsGoalsHelp.exampleBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: TWO GOAL TYPES ───
            HelpSectionTitle(S.savingsGoalsHelp.twoTypesTitle)
            HelpBodyText(S.savingsGoalsHelp.twoTypesBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.savingsGoalsHelp.targetDateTitle)
            HelpBodyText(S.savingsGoalsHelp.targetDateBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.savingsGoalsHelp.fixedContribTitle)
            HelpBodyText(S.savingsGoalsHelp.fixedContribBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: HEADER BAR ───
            HelpSectionTitle(S.savingsGoalsHelp.headerTitle)
            HelpBodyText(S.savingsGoalsHelp.headerBody)
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
                    Text(S.dashboard.savingsGoals, style = MaterialTheme.typography.titleMedium, color = customColors.headerText)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = customColors.headerText, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(Icons.AutoMirrored.Filled.ArrowBack, S.common.back, S.savingsGoalsHelp.backDesc)
            HelpIconRow(Icons.Filled.Pause, S.savingsGoals.pauseAll, S.savingsGoalsHelp.pauseAllDesc)
            HelpIconRow(Icons.AutoMirrored.Filled.Help, S.common.help, S.savingsGoalsHelp.helpDesc)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                S.savingsGoalsHelp.pauseAllNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: ADDING A GOAL ───
            HelpSectionTitle(S.savingsGoalsHelp.addingTitle)
            HelpBodyText(S.savingsGoalsHelp.addingBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpNumberedItem(1, S.savingsGoalsHelp.addStep1, S.savingsGoalsHelp.addStep1Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, S.savingsGoalsHelp.addStep2, S.savingsGoalsHelp.addStep2Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, S.savingsGoalsHelp.addStep3, S.savingsGoalsHelp.addStep3Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(4, S.savingsGoalsHelp.addStep4, S.savingsGoalsHelp.addStep4Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(5, S.savingsGoalsHelp.addStep5, S.savingsGoalsHelp.addStep5Desc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: HOW DEDUCTIONS WORK ───
            HelpSectionTitle(S.savingsGoalsHelp.deductionsTitle)
            HelpBodyText(S.savingsGoalsHelp.deductionsBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.savingsGoalsHelp.targetDateDeductionTitle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(dimColor.copy(alpha = 0.08f))
                    .border(1.dp, dimColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text(
                    S.savingsGoalsHelp.targetDateDeductionFormula,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    lineHeight = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.savingsGoalsHelp.fixedDeductionTitle)
            HelpBodyText(S.savingsGoalsHelp.fixedDeductionBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpBodyText(
                S.savingsGoalsHelp.deductionNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: PROGRESS TRACKING ───
            HelpSectionTitle(S.savingsGoalsHelp.progressTitle)
            HelpBodyText(S.savingsGoalsHelp.progressBody)
            Spacer(modifier = Modifier.height(6.dp))
            HelpBulletText(S.savingsGoalsHelp.progressName)
            HelpBulletText(S.savingsGoalsHelp.progressTarget)
            HelpBulletText(S.savingsGoalsHelp.progressDeduction)
            HelpBulletText(S.savingsGoalsHelp.progressBar)
            HelpBulletText(S.savingsGoalsHelp.progressSaved)
            HelpBulletText(S.savingsGoalsHelp.progressGoalReached)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.savingsGoalsHelp.actionsTitle)
            HelpIconRow(Icons.Filled.Pause, S.savingsGoals.pause, S.savingsGoalsHelp.pauseDesc)
            HelpIconRow(Icons.Filled.PlayArrow, S.savingsGoals.resume, S.savingsGoalsHelp.resumeDesc)
            HelpIconRow(Icons.Filled.Delete, S.common.delete, S.savingsGoalsHelp.deleteDesc, Color(0xFFF44336))
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(S.savingsGoalsHelp.editNote)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: STATUSES ───
            HelpSectionTitle(S.savingsGoalsHelp.statusTitle)

            HelpSubSectionTitle(S.savingsGoalsHelp.activeTitle)
            HelpBodyText(S.savingsGoalsHelp.activeBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.savingsGoalsHelp.pausedTitle)
            HelpBodyText(S.savingsGoalsHelp.pausedBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.savingsGoalsHelp.goalReachedTitle)
            HelpBodyText(S.savingsGoalsHelp.goalReachedBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: MANUAL OVERRIDE WARNING ───
            HelpSectionTitle(S.savingsGoalsHelp.manualOverrideTitle)
            HelpBodyText(S.savingsGoalsHelp.manualOverrideBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION: LINKING TRANSACTIONS TO GOALS ───
            HelpSectionTitle(S.savingsGoalsHelp.linkingTitle)
            HelpBodyText(S.savingsGoalsHelp.linkingBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(S.savingsGoalsHelp.linkingPartialNote)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(S.savingsGoalsHelp.linkingSuperchargeNote)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 9: TIPS ───
            HelpSectionTitle(S.savingsGoalsHelp.tipsTitle)
            HelpBulletText(S.savingsGoalsHelp.tip1)
            HelpBulletText(S.savingsGoalsHelp.tip2)
            HelpBulletText(S.savingsGoalsHelp.tip3)
            HelpBulletText(S.savingsGoalsHelp.tip4)
            HelpBulletText(S.savingsGoalsHelp.tip5)
            HelpBulletText(S.savingsGoalsHelp.tip6)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
