package com.techadvantage.budgetrak.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.Help
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
import com.techadvantage.budgetrak.ui.strings.LocalStrings
import com.techadvantage.budgetrak.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorsHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.colorsHelp.title,
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
            // ─── OVERVIEW ───
            HelpSectionTitle(S.colorsHelp.overviewTitle)
            HelpBodyText(S.colorsHelp.overviewBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── HEADER BAR ───
            HelpSectionTitle(S.colorsHelp.headerTitle)
            HelpBodyText(S.colorsHelp.headerBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpIconRow(Icons.AutoMirrored.Filled.ArrowBack, S.common.back, S.colorsHelp.backDesc)
            HelpIconRow(Icons.AutoMirrored.Filled.Help, S.common.help, S.colorsHelp.helpDesc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── MODE SELECTOR ───
            HelpSectionTitle(S.colorsHelp.modeTitle)
            HelpBodyText(S.colorsHelp.modeBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── THEME / CHART PALETTE DROPDOWN ───
            HelpSectionTitle(S.colorsHelp.dropdownTitle)
            HelpBodyText(S.colorsHelp.dropdownBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── COLOR SETTING (8 slots) ───
            HelpSectionTitle(S.colorsHelp.slotTitle)
            HelpBodyText(S.colorsHelp.slotBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── CHART SLOT (12 swatches) ───
            HelpSectionTitle(S.colorsHelp.chartSlotTitle)
            HelpBodyText(S.colorsHelp.chartSlotBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── EDITING A COLOR ───
            HelpSectionTitle(S.colorsHelp.editTitle)
            HelpBodyText(S.colorsHelp.editBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── COLOR PICKER ───
            HelpSectionTitle(S.colorsHelp.pickerTitle)
            HelpBodyText(S.colorsHelp.pickerBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── NEW THEME / NEW PALETTE ───
            HelpSectionTitle(S.colorsHelp.newTitle)
            HelpBodyText(S.colorsHelp.newBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── DELETE ───
            HelpSectionTitle(S.colorsHelp.deleteTitle)
            HelpBodyText(S.colorsHelp.deleteBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SAVING AND STORAGE ───
            HelpSectionTitle(S.colorsHelp.savingTitle)
            HelpBodyText(S.colorsHelp.savingBody)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
