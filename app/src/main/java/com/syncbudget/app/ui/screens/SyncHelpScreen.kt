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
fun SyncHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.syncHelp.title,
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
            HelpSectionTitle(S.syncHelp.whatTitle)
            HelpBodyText(S.syncHelp.whatBody)

            HelpSectionTitle(S.syncHelp.adminRoleTitle)
            HelpBodyText(S.syncHelp.adminRoleBody)

            HelpSectionTitle(S.syncHelp.gettingStartedTitle)
            HelpBodyText(S.syncHelp.gettingStartedBody)

            HelpSectionTitle(S.syncHelp.joiningTitle)
            HelpBodyText(S.syncHelp.joiningBody)

            HelpSectionTitle(S.syncHelp.syncStatusTitle)
            HelpBodyText(S.syncHelp.syncStatusBody)

            HelpSectionTitle(S.syncHelp.staleWarningsTitle)
            HelpBodyText(S.syncHelp.staleWarningsBody)

            HelpSectionTitle(S.syncHelp.attributionTitle)
            HelpBodyText(S.syncHelp.attributionBody)

            HelpSectionTitle(S.syncHelp.adminClaimsTitle)
            HelpBodyText(S.syncHelp.adminClaimsBody)

            HelpSectionTitle(S.syncHelp.leavingTitle)
            HelpBodyText(S.syncHelp.leavingBody)

            HelpSectionTitle(S.syncHelp.privacyTitle)
            HelpBodyText(S.syncHelp.privacyBody)

            if (S.syncHelp.subscriptionTitle.isNotEmpty()) {
                HelpSectionTitle(S.syncHelp.subscriptionTitle)
                HelpBodyText(S.syncHelp.subscriptionBody)
            }
        }
    }
}
