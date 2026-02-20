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
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FutureExpendituresHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.futureExpendituresHelp.title,
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
            HelpSectionTitle(S.futureExpendituresHelp.whatTitle)
            HelpBodyText(S.futureExpendituresHelp.whatBody)
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
                        S.futureExpendituresHelp.exampleTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        S.futureExpendituresHelp.exampleBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: TWO GOAL TYPES ───
            HelpSectionTitle(S.futureExpendituresHelp.twoTypesTitle)
            HelpBodyText(S.futureExpendituresHelp.twoTypesBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.futureExpendituresHelp.targetDateTitle)
            HelpBodyText(S.futureExpendituresHelp.targetDateBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.futureExpendituresHelp.fixedContribTitle)
            HelpBodyText(S.futureExpendituresHelp.fixedContribBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: HEADER BAR ───
            HelpSectionTitle(S.futureExpendituresHelp.headerTitle)
            HelpBodyText(S.futureExpendituresHelp.headerBody)
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

            HelpIconRow(Icons.AutoMirrored.Filled.ArrowBack, S.common.back, S.futureExpendituresHelp.backDesc)
            HelpIconRow(Icons.Filled.Pause, S.futureExpenditures.pauseAll, S.futureExpendituresHelp.pauseAllDesc)
            HelpIconRow(Icons.AutoMirrored.Filled.Help, S.common.help, S.futureExpendituresHelp.helpDesc)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                S.futureExpendituresHelp.pauseAllNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: ADDING A GOAL ───
            HelpSectionTitle(S.futureExpendituresHelp.addingTitle)
            HelpBodyText(S.futureExpendituresHelp.addingBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpNumberedItem(1, S.futureExpendituresHelp.addStep1, S.futureExpendituresHelp.addStep1Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, S.futureExpendituresHelp.addStep2, S.futureExpendituresHelp.addStep2Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, S.futureExpendituresHelp.addStep3, S.futureExpendituresHelp.addStep3Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(4, S.futureExpendituresHelp.addStep4, S.futureExpendituresHelp.addStep4Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(5, S.futureExpendituresHelp.addStep5, S.futureExpendituresHelp.addStep5Desc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: HOW DEDUCTIONS WORK ───
            HelpSectionTitle(S.futureExpendituresHelp.deductionsTitle)
            HelpBodyText(S.futureExpendituresHelp.deductionsBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.futureExpendituresHelp.targetDateDeductionTitle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(dimColor.copy(alpha = 0.08f))
                    .border(1.dp, dimColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text(
                    S.futureExpendituresHelp.targetDateDeductionFormula,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    lineHeight = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.futureExpendituresHelp.fixedDeductionTitle)
            HelpBodyText(S.futureExpendituresHelp.fixedDeductionBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpBodyText(
                S.futureExpendituresHelp.deductionNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: PROGRESS TRACKING ───
            HelpSectionTitle(S.futureExpendituresHelp.progressTitle)
            HelpBodyText(S.futureExpendituresHelp.progressBody)
            Spacer(modifier = Modifier.height(6.dp))
            HelpBulletText(S.futureExpendituresHelp.progressName)
            HelpBulletText(S.futureExpendituresHelp.progressTarget)
            HelpBulletText(S.futureExpendituresHelp.progressDeduction)
            HelpBulletText(S.futureExpendituresHelp.progressBar)
            HelpBulletText(S.futureExpendituresHelp.progressSaved)
            HelpBulletText(S.futureExpendituresHelp.progressGoalReached)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.futureExpendituresHelp.actionsTitle)
            HelpIconRow(Icons.Filled.Pause, S.futureExpenditures.pause, S.futureExpendituresHelp.pauseDesc)
            HelpIconRow(Icons.Filled.PlayArrow, S.futureExpenditures.resume, S.futureExpendituresHelp.resumeDesc)
            HelpIconRow(Icons.Filled.Delete, S.common.delete, S.futureExpendituresHelp.deleteDesc, Color(0xFFF44336))
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(S.futureExpendituresHelp.editNote)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: STATUSES ───
            HelpSectionTitle(S.futureExpendituresHelp.statusTitle)

            HelpSubSectionTitle(S.futureExpendituresHelp.activeTitle)
            HelpBodyText(S.futureExpendituresHelp.activeBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.futureExpendituresHelp.pausedTitle)
            HelpBodyText(S.futureExpendituresHelp.pausedBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.futureExpendituresHelp.goalReachedTitle)
            HelpBodyText(S.futureExpendituresHelp.goalReachedBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: MANUAL OVERRIDE WARNING ───
            HelpSectionTitle(S.futureExpendituresHelp.manualOverrideTitle)
            HelpBodyText(S.futureExpendituresHelp.manualOverrideBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 9: TIPS ───
            HelpSectionTitle(S.futureExpendituresHelp.tipsTitle)
            HelpBulletText(S.futureExpendituresHelp.tip1)
            HelpBulletText(S.futureExpendituresHelp.tip2)
            HelpBulletText(S.futureExpendituresHelp.tip3)
            HelpBulletText(S.futureExpendituresHelp.tip4)
            HelpBulletText(S.futureExpendituresHelp.tip5)
            HelpBulletText(S.futureExpendituresHelp.tip6)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
