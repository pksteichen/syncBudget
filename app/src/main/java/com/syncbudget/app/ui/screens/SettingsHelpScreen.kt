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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
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
fun SettingsHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.settingsHelp.title,
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
            HelpSectionTitle(S.settingsHelp.overviewTitle)
            HelpBodyText(S.settingsHelp.overviewBody)
            Spacer(modifier = Modifier.height(12.dp))

            // ─── HEADER ───
            HelpSectionTitle(S.settingsHelp.headerTitle)
            HelpBodyText(S.settingsHelp.headerBody)
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
                    Spacer(modifier = Modifier.weight(1f))
                    Text(S.settings.title, style = MaterialTheme.typography.titleMedium, color = customColors.headerText)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = customColors.headerText, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(Icons.AutoMirrored.Filled.ArrowBack, S.common.back, S.settingsHelp.backDesc)
            HelpIconRow(Icons.AutoMirrored.Filled.Help, S.common.help, S.settingsHelp.helpDesc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: BUDGET CONFIG BUTTON ───
            HelpSectionTitle(S.settingsHelp.configureTitle)
            HelpBodyText(S.settingsHelp.configureBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: CURRENCY ───
            HelpSectionTitle(S.settingsHelp.currencyTitle)
            HelpBodyText(S.settingsHelp.currencyBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBulletText(S.settingsHelp.currencyDollar)
            HelpBulletText(S.settingsHelp.currencyEuro)
            HelpBulletText(S.settingsHelp.currencyPound)
            HelpBulletText(S.settingsHelp.currencyYen)
            HelpBulletText(S.settingsHelp.currencyRupee)
            HelpBulletText(S.settingsHelp.currencyWon)
            HelpBulletText(S.settingsHelp.currencyMore)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                S.settingsHelp.currencyNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: DECIMALS ───
            HelpSectionTitle(S.settingsHelp.decimalsTitle)
            HelpBodyText(S.settingsHelp.decimalsBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: DATE FORMAT ───
            HelpSectionTitle(S.settingsHelp.dateFormatTitle)
            HelpBodyText(S.settingsHelp.dateFormatBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBulletText(S.settingsHelp.dateIso)
            HelpBulletText(S.settingsHelp.dateUs)
            HelpBulletText(S.settingsHelp.dateEu)
            HelpBulletText(S.settingsHelp.dateAbbrev)
            HelpBulletText(S.settingsHelp.dateFull)
            HelpBulletText(S.settingsHelp.dateMore)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                S.settingsHelp.dateNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: WEEK STARTS ON ───
            HelpSectionTitle(S.settingsHelp.weekStartTitle)
            HelpBodyText(S.settingsHelp.weekStartBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: CHART PALETTE ───
            HelpSectionTitle(S.settingsHelp.chartPaletteTitle)
            HelpBodyText(S.settingsHelp.chartPaletteBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBulletText(S.settingsHelp.paletteBright)
            HelpBulletText(S.settingsHelp.palettePastel)
            HelpBulletText(S.settingsHelp.paletteSunset)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                S.settingsHelp.paletteNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: MATCHING CONFIGURATION ───
            HelpSectionTitle(S.settingsHelp.matchingTitle)
            HelpBodyText(S.settingsHelp.matchingBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBulletText(S.settingsHelp.matchDaysBullet)
            HelpBulletText(S.settingsHelp.matchPercentBullet)
            HelpBulletText(S.settingsHelp.matchDollarBullet)
            HelpBulletText(S.settingsHelp.matchCharsBullet)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                S.settingsHelp.matchingNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 9: PAID USER ───
            HelpSectionTitle(S.settingsHelp.paidTitle)
            HelpBodyText(S.settingsHelp.paidBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.settingsHelp.paidSave)
            HelpBulletText(S.settingsHelp.paidLoad)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                S.settingsHelp.paidNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 10: CATEGORIES ───
            HelpSectionTitle(S.settingsHelp.categoriesTitle)
            HelpBodyText(S.settingsHelp.categoriesBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.settingsHelp.defaultCategoriesTitle)
            HelpBodyText(S.settingsHelp.defaultCategoriesBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.settingsHelp.catOther)
            HelpBulletText(S.settingsHelp.catRecurring)
            HelpBulletText(S.settingsHelp.catAmortization)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.settingsHelp.addCategoryTitle)
            HelpBodyText(S.settingsHelp.addCategoryBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.settingsHelp.editCategoryTitle)
            HelpBodyText(S.settingsHelp.editCategoryBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.settingsHelp.deleteCategoryTitle)
            HelpBodyText(S.settingsHelp.deleteCategoryBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.settingsHelp.deleteBullet1)
            HelpBulletText(S.settingsHelp.deleteBullet2)
            HelpBulletText(S.settingsHelp.deleteBullet3)
            HelpBulletText(S.settingsHelp.deleteBullet4)
            Spacer(modifier = Modifier.height(8.dp))

            // Reassignment info box
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
                        S.settingsHelp.reassignmentTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        S.settingsHelp.reassignmentBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 11: TIPS ───
            HelpSectionTitle(S.settingsHelp.tipsTitle)
            HelpBulletText(S.settingsHelp.tip1)
            HelpBulletText(S.settingsHelp.tip2)
            HelpBulletText(S.settingsHelp.tip3)
            HelpBulletText(S.settingsHelp.tip4)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
