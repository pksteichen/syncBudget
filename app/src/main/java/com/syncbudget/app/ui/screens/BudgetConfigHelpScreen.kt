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
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetConfigHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.budgetConfigHelp.title,
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
            HelpSectionTitle(S.budgetConfigHelp.overviewTitle)
            HelpBodyText(S.budgetConfigHelp.overviewBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: BUDGET PERIOD ───
            HelpSectionTitle(S.budgetConfigHelp.periodTitle)
            HelpBodyText(S.budgetConfigHelp.periodBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpNumberedItem(1, S.budgetConfigHelp.periodDaily, S.budgetConfigHelp.periodDailyDesc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, S.budgetConfigHelp.periodWeekly, S.budgetConfigHelp.periodWeeklyDesc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, S.budgetConfigHelp.periodMonthly, S.budgetConfigHelp.periodMonthlyDesc)
            Spacer(modifier = Modifier.height(10.dp))

            HelpBodyText(
                S.budgetConfigHelp.periodNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: RESET SETTINGS ───
            HelpSectionTitle(S.budgetConfigHelp.resetSettingsTitle)
            HelpBodyText(S.budgetConfigHelp.resetSettingsBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.resetHourTitle)
            HelpBodyText(S.budgetConfigHelp.resetHourBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.dayOfWeekTitle)
            HelpBodyText(S.budgetConfigHelp.dayOfWeekBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.dayOfMonthTitle)
            HelpBodyText(S.budgetConfigHelp.dayOfMonthBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: SAFE BUDGET AMOUNT ───
            HelpSectionTitle(S.budgetConfigHelp.safeBudgetTitle)
            HelpBodyText(S.budgetConfigHelp.safeBudgetBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.howCalculatedTitle)
            HelpBodyText(S.budgetConfigHelp.howCalculatedBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(1, S.budgetConfigHelp.calcStep1, S.budgetConfigHelp.calcStep1Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, S.budgetConfigHelp.calcStep2, S.budgetConfigHelp.calcStep2Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, S.budgetConfigHelp.calcStep3, S.budgetConfigHelp.calcStep3Desc)
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
                        S.budgetConfigHelp.importantTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        S.budgetConfigHelp.importantBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: AUTO-RECALCULATION & START/RESET ───
            HelpSectionTitle(S.budgetConfigHelp.autoRecalcTitle)
            HelpBodyText(S.budgetConfigHelp.autoRecalcBody)
            Spacer(modifier = Modifier.height(12.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.startResetTitle)
            HelpBodyText(S.budgetConfigHelp.startResetBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.budgetConfigHelp.resetBullet1)
            HelpBulletText(S.budgetConfigHelp.resetBullet2)
            HelpBulletText(S.budgetConfigHelp.resetBullet3)
            HelpBulletText(S.budgetConfigHelp.resetBullet4)
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
                        S.budgetConfigHelp.whenToResetTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        S.budgetConfigHelp.whenToResetBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: MANUAL OVERRIDE ───
            HelpSectionTitle(S.budgetConfigHelp.manualTitle)
            HelpBodyText(S.budgetConfigHelp.manualBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.budgetConfigHelp.manualBullet1)
            HelpBulletText(S.budgetConfigHelp.manualBullet2)
            HelpBulletText(S.budgetConfigHelp.manualBullet3)
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
                        S.budgetConfigHelp.warningTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFF44336)
                    )
                    Text(
                        S.budgetConfigHelp.warningBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: INCOME SOURCES ───
            HelpSectionTitle(S.budgetConfigHelp.incomeSourcesTitle)
            HelpBodyText(S.budgetConfigHelp.incomeSourcesBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.addingIncomeTitle)
            HelpBodyText(S.budgetConfigHelp.addingIncomeBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.budgetConfigHelp.incomeNameBullet)
            HelpBulletText(S.budgetConfigHelp.incomeAmountBullet)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.variablePayTitle)
            HelpBodyText(S.budgetConfigHelp.variablePayBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.managingTitle)
            HelpBulletText(S.budgetConfigHelp.manageTapBullet)
            HelpIconRow(Icons.Filled.Sync, S.common.repeatType, S.budgetConfigHelp.manageRepeatDesc)
            HelpIconRow(Icons.Filled.Delete, S.common.delete, S.budgetConfigHelp.manageDeleteDesc, Color(0xFFF44336))
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: REPEAT SETTINGS ───
            HelpSectionTitle(S.budgetConfigHelp.repeatTitle)
            HelpBodyText(S.budgetConfigHelp.repeatBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.everyXDaysTitle)
            HelpBodyText(S.budgetConfigHelp.everyXDaysBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.everyXWeeksTitle)
            HelpBodyText(S.budgetConfigHelp.everyXWeeksBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.everyXMonthsTitle)
            HelpBodyText(S.budgetConfigHelp.everyXMonthsBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.biMonthlyTitle)
            HelpBodyText(S.budgetConfigHelp.biMonthlyBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.budgetConfigHelp.annualTitle)
            HelpBodyText(S.budgetConfigHelp.annualBody)
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
                        S.budgetConfigHelp.dayLimitTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        S.budgetConfigHelp.dayLimitBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 9: BUDGET INCOME DETECTION ───
            HelpSectionTitle(S.budgetConfigHelp.budgetIncomeTitle)
            HelpBodyText(S.budgetConfigHelp.budgetIncomeBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.budgetConfigHelp.budgetIncomeBullet)
            HelpBulletText(S.budgetConfigHelp.extraIncomeBullet)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                S.budgetConfigHelp.budgetIncomeNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 10: TIPS ───
            HelpSectionTitle(S.budgetConfigHelp.tipsTitle)
            HelpBulletText(S.budgetConfigHelp.tip1)
            HelpBulletText(S.budgetConfigHelp.tip2)
            HelpBulletText(S.budgetConfigHelp.tip3)
            HelpBulletText(S.budgetConfigHelp.tip4)
            HelpBulletText(S.budgetConfigHelp.tip5)
            HelpBulletText(S.budgetConfigHelp.tip6)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
