package com.syncbudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySyncHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.familySyncHelp.title,
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
            HelpSectionTitle(S.familySyncHelp.whatTitle)
            HelpBodyText(S.familySyncHelp.whatBody)

            HelpSectionTitle(S.familySyncHelp.adminRoleTitle)
            HelpBodyText(S.familySyncHelp.adminRoleBody)

            HelpSectionTitle(S.familySyncHelp.gettingStartedTitle)
            HelpBodyText(S.familySyncHelp.gettingStartedBody)

            HelpSectionTitle(S.familySyncHelp.joiningTitle)
            HelpBodyText(S.familySyncHelp.joiningBody)

            HelpSectionTitle(S.familySyncHelp.syncStatusTitle)
            HelpBodyText(S.familySyncHelp.syncStatusBody)

            HelpSectionTitle(S.familySyncHelp.staleWarningsTitle)
            HelpBodyText(S.familySyncHelp.staleWarningsBody)

            HelpSectionTitle(S.familySyncHelp.attributionTitle)
            HelpBodyText(S.familySyncHelp.attributionBody)

            HelpSectionTitle(S.familySyncHelp.adminClaimsTitle)
            HelpBodyText(S.familySyncHelp.adminClaimsBody)

            HelpSectionTitle(S.familySyncHelp.leavingTitle)
            HelpBodyText(S.familySyncHelp.leavingBody)

            HelpSectionTitle(S.familySyncHelp.privacyTitle)
            HelpBodyText(S.familySyncHelp.privacyBody)
        }
    }
}
