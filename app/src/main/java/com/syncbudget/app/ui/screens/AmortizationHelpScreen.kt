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
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmortizationHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.amortizationHelp.title,
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

            // ─── SECTION 1: WHAT IS AMORTIZATION ───
            HelpSectionTitle(S.amortizationHelp.whatTitle)
            HelpBodyText(S.amortizationHelp.whatBody)
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
                        S.amortizationHelp.exampleTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        S.amortizationHelp.exampleBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.amortizationHelp.vsGoalsTitle)
            HelpBodyText(S.amortizationHelp.vsGoalsBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.amortizationHelp.goalsBullet)
            HelpBulletText(S.amortizationHelp.amortBullet)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: HEADER BAR ───
            HelpSectionTitle(S.amortizationHelp.headerTitle)

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
                    Text(S.dashboard.amortization, style = MaterialTheme.typography.titleMedium, color = customColors.headerText)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = customColors.headerText, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(Icons.AutoMirrored.Filled.ArrowBack, S.common.back, S.amortizationHelp.backDesc)
            HelpIconRow(Icons.AutoMirrored.Filled.Help, S.common.help, S.amortizationHelp.helpDesc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: ADDING AN ENTRY ───
            HelpSectionTitle(S.amortizationHelp.addingTitle)
            HelpBodyText(S.amortizationHelp.addingBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpNumberedItem(1, S.amortizationHelp.addStep1, S.amortizationHelp.addStep1Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, S.amortizationHelp.addStep2, S.amortizationHelp.addStep2Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, S.amortizationHelp.addStep3, S.amortizationHelp.addStep3Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(4, S.amortizationHelp.addStep4, S.amortizationHelp.addStep4Desc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: HOW DEDUCTIONS WORK ───
            HelpSectionTitle(S.amortizationHelp.deductionsTitle)
            HelpBodyText(S.amortizationHelp.deductionsBody)
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
                    S.amortizationHelp.deductionFormula,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    lineHeight = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpBodyText(S.amortizationHelp.deductionNote)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: ENTRY LIST ───
            HelpSectionTitle(S.amortizationHelp.entryListTitle)
            HelpBodyText(S.amortizationHelp.entryListBody)
            Spacer(modifier = Modifier.height(6.dp))
            HelpBulletText(S.amortizationHelp.entrySource)
            HelpBulletText(S.amortizationHelp.entryTotal)
            HelpBulletText(S.amortizationHelp.entryProgress)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.amortizationHelp.actionsTitle)
            HelpBodyText(S.amortizationHelp.editNote)
            Spacer(modifier = Modifier.height(4.dp))
            HelpIconRow(Icons.Filled.Delete, S.common.delete, S.amortizationHelp.deleteDesc, Color(0xFFF44336))
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: AUTOMATIC MATCHING ───
            HelpSectionTitle(S.amortizationHelp.matchingTitle)
            HelpBodyText(S.amortizationHelp.matchingBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.amortizationHelp.yesAmortBullet)
            HelpBulletText(S.amortizationHelp.noRegularBullet)
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
                        S.amortizationHelp.sourceMatchingTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        S.amortizationHelp.sourceMatchingBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: MANUAL BUDGET OVERRIDE ───
            HelpSectionTitle(S.amortizationHelp.manualOverrideTitle)
            HelpBodyText(S.amortizationHelp.manualOverrideBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: TIPS ───
            HelpSectionTitle(S.amortizationHelp.tipsTitle)
            HelpBulletText(S.amortizationHelp.tip1)
            HelpBulletText(S.amortizationHelp.tip2)
            HelpBulletText(S.amortizationHelp.tip3)
            HelpBulletText(S.amortizationHelp.tip4)
            HelpBulletText(S.amortizationHelp.tip5)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
