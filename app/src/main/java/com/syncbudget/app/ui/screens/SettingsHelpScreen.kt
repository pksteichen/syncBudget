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
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Settings Help",
                        style = MaterialTheme.typography.titleLarge,
                        color = customColors.headerText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            HelpSectionTitle("Overview")
            HelpBodyText(
                "The Settings screen lets you customize how the app displays information " +
                "and manage your transaction categories. Access it by tapping the gear icon " +
                "on the dashboard."
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ─── HEADER ───
            HelpSectionTitle("Header Bar")
            HelpBodyText("The header provides navigation and help access:")
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
                    Text("Settings", style = MaterialTheme.typography.titleMedium, color = customColors.headerText)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = customColors.headerText, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(Icons.AutoMirrored.Filled.ArrowBack, "Back", "Return to the dashboard.")
            HelpIconRow(Icons.AutoMirrored.Filled.Help, "Help", "Opens this help page.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: BUDGET CONFIG BUTTON ───
            HelpSectionTitle("Configure Your Budget")
            HelpBodyText(
                "The first item in Settings is a button that opens Budget Configuration. " +
                "This is where you set up your income sources, choose your budget period, " +
                "and calculate your safe daily budget. See the Budget Configuration help page " +
                "for full details."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: CURRENCY ───
            HelpSectionTitle("Currency")
            HelpBodyText(
                "Choose the currency symbol displayed throughout the app. The dropdown includes " +
                "common symbols:"
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpBulletText("$ \u2014 US Dollar, Canadian Dollar, Australian Dollar, etc.")
            HelpBulletText("\u20ac \u2014 Euro")
            HelpBulletText("\u00a3 \u2014 British Pound")
            HelpBulletText("\u00a5 \u2014 Japanese Yen / Chinese Yuan")
            HelpBulletText("\u20b9 \u2014 Indian Rupee")
            HelpBulletText("\u20a9 \u2014 Korean Won")
            HelpBulletText("And more")
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                "The currency symbol affects the Solari display, transaction amounts, budget " +
                "configuration, and all other monetary displays. Decimal places are automatically " +
                "adjusted for currencies that traditionally don't use them (e.g., Yen).",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: DIGITS ───
            HelpSectionTitle("Digits")
            HelpBodyText(
                "Controls how many digit positions the Solari display shows, from 2 to 5. " +
                "This affects the maximum value the display can show:"
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpBulletText("2 digits \u2014 up to 99 (or 99.99 with decimals)")
            HelpBulletText("3 digits \u2014 up to 999")
            HelpBulletText("4 digits \u2014 up to 9,999")
            HelpBulletText("5 digits \u2014 up to 99,999")
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                "Choose based on your typical budget range. For daily budgets, 3 digits " +
                "is usually sufficient. For weekly or monthly budgets, 4 or 5 may be needed.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: DECIMALS ───
            HelpSectionTitle("Show Decimal Places")
            HelpBodyText(
                "When checked, the Solari display shows cents/pence after a decimal point. " +
                "The number of decimal places depends on your currency (2 for most currencies, " +
                "0 for currencies like the Japanese Yen). Unchecking this rounds the display to " +
                "whole numbers for a cleaner look."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: DATE FORMAT ───
            HelpSectionTitle("Date Format")
            HelpBodyText(
                "Choose how dates are displayed throughout the app, including the transaction " +
                "list, date pickers, and export files. Options include:"
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpBulletText("2026-02-17 \u2014 ISO format (default)")
            HelpBulletText("02/17/2026 \u2014 US format")
            HelpBulletText("17/02/2026 \u2014 European format")
            HelpBulletText("Feb 17, 2026 \u2014 Abbreviated month")
            HelpBulletText("February 17, 2026 \u2014 Full month name")
            HelpBulletText("And several other international formats")
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                "The dropdown shows a sample date in each format so you can preview how " +
                "it will look before selecting.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: PAID USER ───
            HelpSectionTitle("Paid User")
            HelpBodyText(
                "When checked, this unlocks premium features:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("Save transactions \u2014 export to CSV or encrypted file")
            HelpBulletText("Load transactions \u2014 import from bank CSV, app CSV, or encrypted backup")
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                "When Paid User is not enabled, the Save and Load icons on the Transactions " +
                "screen appear dimmed and are non-functional.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: CATEGORIES ───
            HelpSectionTitle("Categories")
            HelpBodyText(
                "Categories let you classify your transactions for better spending insight. " +
                "Each category has a name and an icon."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Default Categories")
            HelpBodyText(
                "Three categories are protected and cannot be deleted or renamed:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("Other \u2014 the default fallback category for uncategorized transactions")
            HelpBulletText("Recurring \u2014 automatically assigned to transactions matched to recurring expenses")
            HelpBulletText("Amortization \u2014 automatically assigned to transactions matched to amortization entries")
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Adding a Category")
            HelpBodyText(
                "Tap \"Add Category\" to create a new category. Enter a name and choose an icon " +
                "from the icon grid. Icons are displayed as a visual grid you can scroll through."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Editing a Category")
            HelpBodyText(
                "Tap any non-protected category to open the edit dialog. You can change the " +
                "name and icon. The delete button (red trash icon) appears in the dialog title bar."
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Deleting a Category")
            HelpBodyText(
                "When deleting a category that has existing transactions:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("If no transactions use the category, it is deleted immediately")
            HelpBulletText("If transactions exist, a reassignment dialog appears")
            HelpBulletText("You must choose another category to move the affected transactions to")
            HelpBulletText("Tap \"Move & Delete\" to reassign all affected transactions and remove the category")
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
                        "Category Reassignment",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "When you delete a category, all transactions assigned to it " +
                        "are moved to your chosen replacement category. This includes " +
                        "multi-category transactions where only the specific category " +
                        "split is affected. The reassignment is permanent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 9: TIPS ───
            HelpSectionTitle("Tips")
            HelpBulletText("Set up categories before importing transactions \u2014 the auto-categorization uses your existing transaction history to match merchants.")
            HelpBulletText("Create categories that match your spending habits. Common examples: Food, Transport, Entertainment, Health, Housing, Utilities, Shopping.")
            HelpBulletText("Use the \"Daily\" budget period if you want the most granular spending control.")
            HelpBulletText("The Budget Configuration button is the first thing to set up after installing the app.")

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
