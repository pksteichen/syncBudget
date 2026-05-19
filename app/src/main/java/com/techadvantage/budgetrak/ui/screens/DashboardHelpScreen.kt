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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.techadvantage.budgetrak.R
import com.techadvantage.budgetrak.ui.strings.LocalStrings
import com.techadvantage.budgetrak.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardHelpScreen(
    onBack: () -> Unit,
    scrollTarget: String? = null,
    onScrollTargetConsumed: () -> Unit = {}
) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

    // Anchor Y for the Paid User & Subscriber section in the scrollable
    // column's coordinate space. Populated via onGloballyPositioned on an
    // invisible 0-dp marker just above the section title. Matches the
    // pattern used in TransactionsHelpScreen for the preselect-cats anchor.
    var upgradesAnchorY by remember { mutableIntStateOf(-1) }
    val scrollState = rememberScrollState()

    // When a deep-link target arrives (e.g. tap on an offline in-house ad),
    // wait for the anchor to be laid out, then animate-scroll to it and
    // clear the one-shot hint so back-navigations open at the top normally.
    LaunchedEffect(scrollTarget, upgradesAnchorY) {
        if (scrollTarget == "upgrades" && upgradesAnchorY >= 0) {
            scrollState.animateScrollTo(upgradesAnchorY)
            onScrollTargetConsumed()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.dashboardHelp.title,
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            val textColor = MaterialTheme.colorScheme.onBackground
            val dimColor = textColor.copy(alpha = 0.7f)
            val accentColor = MaterialTheme.colorScheme.primary
            val headerBg = customColors.headerBackground

            // ─── SECTION 1: WELCOME ───
            HelpSectionTitle(S.dashboardHelp.welcomeTitle)
            HelpBodyText(S.dashboardHelp.welcomeBody)
            Spacer(modifier = Modifier.height(12.dp))

            // Key value proposition box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.07f))
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        S.dashboardHelp.dailyBudgetNumberTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        S.dashboardHelp.dailyBudgetNumberBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: THE SOLARI DISPLAY ───
            HelpSectionTitle(S.dashboardHelp.solariDisplayTitle)
            HelpBodyText(S.dashboardHelp.solariDisplayBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.dashboardHelp.availableCashTitle)
            HelpBodyText(S.dashboardHelp.availableCashBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.dashboardHelp.bullet1)
            HelpBulletText(S.dashboardHelp.bullet2)
            HelpBulletText(S.dashboardHelp.bullet3)
            HelpBulletText(S.dashboardHelp.bullet4)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.dashboardHelp.budgetLabelTitle)
            HelpBodyText(S.dashboardHelp.budgetLabelBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── BUDGET CONFIGURATION ───
            HelpSectionTitle(S.dashboardHelp.budgetPeriodExplTitle)
            HelpBodyText(S.dashboardHelp.budgetPeriodExpl)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: HEADER BAR ───
            HelpSectionTitle(S.dashboardHelp.headerBarTitle)
            HelpBodyText(S.dashboardHelp.headerBarBody)
            Spacer(modifier = Modifier.height(8.dp))

            // Visual header mockup
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
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = customColors.headerText,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.budgetrak_logo),
                        contentDescription = S.dashboard.appTitle,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(customColors.headerText),
                        modifier = Modifier.height(22.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.Help,
                        contentDescription = null,
                        tint = customColors.headerText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(Icons.Filled.Settings, S.dashboard.settings, S.dashboardHelp.headerSettingsDesc)
            HelpIconRow(Icons.AutoMirrored.Filled.Help, S.common.help, S.dashboardHelp.headerHelpDesc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()


            // ─── SECTION 5: SPENDING CHART ───
            HelpSectionTitle(S.dashboardHelp.spendingChartTitle)
            HelpBodyText(S.dashboardHelp.spendingChartBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle(S.dashboardHelp.chartTitleBarTitle)
            HelpBulletText(S.dashboardHelp.chartRangeBullet)
            HelpBulletText(S.dashboardHelp.chartToggleBullet)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.dashboardHelp.chartIconsTitle)
            HelpBodyText(S.dashboardHelp.chartIconsBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: DASHBOARD ICON BAR ───
            HelpSectionTitle(S.dashboardHelp.quickButtonsTitle)
            HelpBodyText(S.dashboardHelp.quickButtonsBody)
            Spacer(modifier = Modifier.height(8.dp))

            // Icon-bar mockup using light Default theme colors (Header bg +
            // Header Text icons) so the help illustration is canonical
            // regardless of which theme the user has selected.
            val iconBarBg = Color(0xFF305880)   // LightCardBackground
            val iconBarFg = Color(0xFFFFFFFF)   // LightCardText
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBarBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Transaction (layered: body + blue plus overlay).
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_add_transaction_body),
                            contentDescription = null,
                            tint = iconBarFg,
                            modifier = Modifier.fillMaxSize()
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_add_transaction_plus),
                            contentDescription = null,
                            tint = Color(0xFF0D47A1),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = iconBarFg, modifier = Modifier.size(32.dp))
                    Icon(painter = painterResource(id = R.drawable.ic_coins), contentDescription = null, tint = iconBarFg, modifier = Modifier.size(32.dp))
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = iconBarFg, modifier = Modifier.size(32.dp))
                    Icon(Icons.Filled.Sync, contentDescription = null, tint = iconBarFg, modifier = Modifier.size(32.dp))
                    Icon(Icons.Filled.DateRange, contentDescription = null, tint = iconBarFg, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add_transaction_body),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxSize()
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add_transaction_plus),
                        contentDescription = null,
                        tint = Color(0xFF0D47A1),
                        modifier = Modifier.fillMaxSize()
                    )
                },
                label = S.common.addTransaction,
                description = S.dashboardHelp.quickAddIncomeDesc
            )
            HelpIconRow(Icons.AutoMirrored.Filled.List, S.dashboard.transactions, S.dashboardHelp.navTransactionsDesc)
            HelpIconRow(painterResource(id = R.drawable.ic_coins), S.dashboard.savingsGoals, S.dashboardHelp.navSavingsDesc)
            HelpIconRow(Icons.Filled.Schedule, S.dashboard.amortization, S.dashboardHelp.navAmortizationDesc)
            HelpIconRow(Icons.Filled.Sync, S.dashboard.recurringExpenses, S.dashboardHelp.navRecurringDesc)
            HelpIconRow(Icons.Filled.DateRange, S.dashboard.budgetCalendar, S.dashboardHelp.navCalendarDesc)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                S.dashboardHelp.quickMatchingNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: SUPERCHARGE ───
            HelpSectionTitle(S.dashboardHelp.superchargeTitle)
            HelpBodyText(S.dashboardHelp.superchargeBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpIconRow(Icons.Filled.Bolt, S.dashboard.supercharge, S.dashboardHelp.superchargeIconDesc)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(S.dashboardHelp.superchargeDialogBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: HOW IT ALL WORKS ───
            HelpSectionTitle(S.dashboardHelp.howBudgetWorksTitle)
            HelpBodyText(S.dashboardHelp.howBudgetWorksBody)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.dashboardHelp.budgetAmountTitle)
            HelpBodyText(S.dashboardHelp.budgetAmountBody)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.dashboardHelp.budgetSavingsBullet)
            HelpBulletText(S.dashboardHelp.budgetAmortBullet)
            HelpBulletText(S.dashboardHelp.budgetAccelBullet)
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle(S.dashboardHelp.availableCashSectionTitle)
            HelpBodyText(S.dashboardHelp.availableCashSectionBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: GETTING STARTED ───
            HelpSectionTitle(S.dashboardHelp.gettingStartedTitle)
            HelpBodyText(S.dashboardHelp.gettingStartedBody)
            Spacer(modifier = Modifier.height(8.dp))

            HelpNumberedItem(1, S.dashboardHelp.step1Title, S.dashboardHelp.step1Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, S.dashboardHelp.step2Title, S.dashboardHelp.step2Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, S.dashboardHelp.step3Title, S.dashboardHelp.step3Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(4, S.dashboardHelp.step4Title, S.dashboardHelp.step4Desc)
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(5, S.dashboardHelp.step5Title, S.dashboardHelp.step5Desc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: IMPROVING FINANCIAL WELLBEING ───
            HelpSectionTitle(S.dashboardHelp.habitsTitle)
            HelpBodyText(S.dashboardHelp.habitsBody)
            Spacer(modifier = Modifier.height(10.dp))

            // Tip boxes
            FinancialTipBox(
                title = S.dashboardHelp.tipKnowTitle,
                body = S.dashboardHelp.tipKnowBody,
                color = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.height(10.dp))

            FinancialTipBox(
                title = S.dashboardHelp.tipRecordTitle,
                body = S.dashboardHelp.tipRecordBody,
                color = Color(0xFF2196F3)
            )
            Spacer(modifier = Modifier.height(10.dp))

            FinancialTipBox(
                title = S.dashboardHelp.tipPlanTitle,
                body = S.dashboardHelp.tipPlanBody,
                color = Color(0xFFFF9800)
            )
            Spacer(modifier = Modifier.height(10.dp))

            FinancialTipBox(
                title = S.dashboardHelp.tipPaycheckTitle,
                body = S.dashboardHelp.tipPaycheckBody,
                color = Color(0xFF9C27B0)
            )
            Spacer(modifier = Modifier.height(10.dp))

            FinancialTipBox(
                title = S.dashboardHelp.tipWatchTitle,
                body = S.dashboardHelp.tipWatchBody,
                color = Color(0xFF00BCD4)
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: KEY FEATURES ───
            HelpSectionTitle(S.dashboardHelp.keyFeaturesTitle)

            HelpBulletText(S.dashboardHelp.featureBullet1)
            HelpBulletText(S.dashboardHelp.featureBullet2)
            HelpBulletText(S.dashboardHelp.featureBullet3)
            HelpBulletText(S.dashboardHelp.featureBullet4)
            HelpBulletText(S.dashboardHelp.featureBullet5)
            HelpBulletText(S.dashboardHelp.featureBullet6)
            HelpBulletText(S.dashboardHelp.featureBullet7)
            HelpBulletText(S.dashboardHelp.featureBullet8)
            HelpBulletText(S.dashboardHelp.featureBullet9)
            HelpBulletText(S.dashboardHelp.featureBullet10)
            HelpBulletText(S.dashboardHelp.featureBullet11)
            HelpBulletText(S.dashboardHelp.featureBullet12)
            HelpBulletText(S.dashboardHelp.featureBullet13)
            HelpBulletText(S.dashboardHelp.featureBullet14)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SYNC INDICATOR ───
            HelpSectionTitle(S.dashboardHelp.syncIndicatorTitle)
            HelpBodyText(S.dashboardHelp.syncIndicatorBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBulletText(S.dashboardHelp.syncArrowsBullet)
            HelpBulletText(S.dashboardHelp.syncDotsBullet)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── HOME SCREEN WIDGET ───
            HelpSectionTitle(S.dashboardHelp.widgetTitle)
            HelpBodyText(S.dashboardHelp.widgetBody)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBulletText(S.dashboardHelp.widgetSolariDesc)
            HelpBulletText(S.dashboardHelp.widgetButtonsDesc)
            HelpBulletText(S.dashboardHelp.widgetFreeDesc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── PAID USER & SUBSCRIBER TIERS ─── (moved from settingsHelp in v2.5.x)
            // Invisible 0-dp anchor marker — deep-linked from in-house
            // fallback ads (offline) and any future "view upgrade info" link.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .onGloballyPositioned { coords ->
                        upgradesAnchorY = coords.positionInParent().y.toInt()
                    }
            )
            HelpSectionTitle(S.dashboardHelp.paidTitle)
            HelpBodyText(S.dashboardHelp.paidBody)
            Spacer(modifier = Modifier.height(10.dp))
            HelpSubSectionTitle(S.dashboardHelp.paidSubHeader)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.dashboardHelp.paidSave)
            HelpBulletText(S.dashboardHelp.paidWidget)
            HelpBulletText(S.dashboardHelp.paidPhotos)
            HelpBulletText(S.dashboardHelp.paidLoad)
            HelpBulletText(S.dashboardHelp.paidAiCsv)
            HelpBulletText(S.dashboardHelp.paidSimulation)
            Spacer(modifier = Modifier.height(10.dp))

            // Subscriber is its own subsection heading (not a bullet)
            HelpSubSectionTitle(S.dashboardHelp.paidAdFree)
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText(S.dashboardHelp.subBulletOcr)
            HelpBulletText(S.dashboardHelp.subBulletSync)
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(S.dashboardHelp.subFooterNote)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SUBSCRIPTION EXPIRY NOTICE ───
            HelpSectionTitle(S.dashboardHelp.subExpiryWarningTitle)
            HelpBodyText(S.dashboardHelp.subExpiryWarningBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 9: PRIVACY ───
            HelpSectionTitle(S.dashboardHelp.privacyTitle)
            HelpBodyText(S.dashboardHelp.privacyBody)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FinancialTipBox(
    title: String,
    body: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 18.sp
            )
        }
    }
}
