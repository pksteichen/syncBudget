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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Savings
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
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Dashboard Help",
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

            // ─── SECTION 1: WELCOME ───
            HelpSectionTitle("Welcome to SecureSync Daily Budget")
            HelpBodyText(
                "SecureSync Daily Budget is a privacy-first budgeting app designed to give you " +
                "a clear, real-time picture of how much money you can safely spend right now. " +
                "Unlike traditional budget trackers that only show you where your money went, " +
                "this app tells you where your money can go \u2014 calculated from your actual " +
                "income schedule, recurring bills, and financial goals."
            )
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
                        "Your Daily Budget Number",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "The large number on the Solari display is your Available Cash \u2014 the amount " +
                        "you can spend right now without jeopardizing your bills, savings goals, or " +
                        "financial commitments. Think of it as the answer to the question everyone asks: " +
                        "\"How much can I afford to spend today?\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 2: THE SOLARI DISPLAY ───
            HelpSectionTitle("The Solari Display")
            HelpBodyText(
                "The centerpiece of the app is the Solari-style flip display \u2014 inspired by the " +
                "split-flap departure boards found in train stations and airports. It shows two " +
                "key pieces of information:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Available Cash (Main Number)")
            HelpBodyText(
                "This is the running total of money available for discretionary spending. " +
                "It starts at one period's budget amount when you first configure your budget, " +
                "and updates in real time:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("Increases each budget period (daily, weekly, or monthly) by your budget amount")
            HelpBulletText("Decreases when you record an expense")
            HelpBulletText("Increases when you record extra (non-budget) income")
            HelpBulletText("Shows red/negative when you've overspent")
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Budget Label (Below the Number)")
            HelpBodyText(
                "The label beneath the digits shows your budget rate \u2014 for example, " +
                "\"$42.50/day\" or \"$297.50/week\". This tells you how much is added to " +
                "your available cash each period. If your budget is not yet configured, " +
                "it shows \"Not configured\"."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 3: HEADER BAR ───
            HelpSectionTitle("Header Bar")
            HelpBodyText("The header bar provides access to settings and this help page:")
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
                    Text(
                        "SecureSync Daily Budget",
                        style = MaterialTheme.typography.titleMedium,
                        color = customColors.headerText
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

            HelpIconRow(Icons.Filled.Settings, "Settings", "Open the Settings screen to configure display options, categories, and access Budget Configuration.")
            HelpIconRow(Icons.AutoMirrored.Filled.Help, "Help", "Opens this help page.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 4: NAVIGATION BAR ───
            HelpSectionTitle("Navigation Bar")
            HelpBodyText("The bottom navigation bar provides quick access to all major features:")
            Spacer(modifier = Modifier.height(8.dp))

            // Visual nav bar mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, dimColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = textColor, modifier = Modifier.size(28.dp))
                    Icon(Icons.Filled.Savings, contentDescription = null, tint = textColor, modifier = Modifier.size(28.dp))
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = textColor, modifier = Modifier.size(28.dp))
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = textColor, modifier = Modifier.size(28.dp))
                    Icon(Icons.Filled.Sync, contentDescription = null, tint = textColor, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            HelpIconRow(Icons.AutoMirrored.Filled.List, "Transactions", "Record and manage your income and expenses. Import bank statements, search, filter, and categorize.")
            HelpIconRow(Icons.Filled.Savings, "Savings", "Track your savings goals. (Coming soon)")
            HelpIconRow(Icons.Filled.CalendarMonth, "Future Expenditures", "Plan for large upcoming purchases. The app automatically sets aside money each period so you're ready when the expense arrives.")
            HelpIconRow(Icons.Filled.Schedule, "Amortization", "Spread a past large expense across multiple budget periods so it doesn't hit your budget all at once.")
            HelpIconRow(Icons.Filled.Sync, "Recurring Expenses", "Register bills, subscriptions, and loan payments so the budget calculator accounts for them automatically.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 5: HOW IT ALL WORKS ───
            HelpSectionTitle("How the Budget Works")
            HelpBodyText(
                "The budget engine runs a cash flow simulation using your income schedule and " +
                "recurring expenses to determine a safe spending amount for each budget period."
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpSubSectionTitle("Safe Budget Amount")
            HelpBodyText(
                "This is the maximum you can spend per period (day, week, or month) without " +
                "running out of money to cover your bills. The calculation:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(1, "Income projection", "Your income sources and their repeat schedules are projected forward one year.")
            HelpNumberedItem(2, "Expense simulation", "Your recurring expenses are projected over the same period.")
            HelpNumberedItem(3, "Timing safety", "The engine ensures that even in months with clustered bills, the budget amount covers all obligations.")
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Budget Amount")
            HelpBodyText(
                "Your actual per-period budget is the Safe Budget Amount minus any active deductions:"
            )
            Spacer(modifier = Modifier.height(4.dp))
            HelpBulletText("Future Expenditure deductions \u2014 money set aside for planned purchases")
            HelpBulletText("Amortization deductions \u2014 spreading past large expenses over time")
            Spacer(modifier = Modifier.height(8.dp))
            HelpBodyText(
                "This ensures your spending money is already adjusted for both upcoming and past large expenses.",
                italic = true
            )
            Spacer(modifier = Modifier.height(10.dp))

            HelpSubSectionTitle("Available Cash")
            HelpBodyText(
                "Available Cash is the number shown on the Solari display. Each budget period, your " +
                "budget amount is added to the total. Each expense you record is subtracted. " +
                "Extra income (income not already counted in your budget sources) is added. " +
                "The result: a single number that tells you exactly how much you can spend."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 6: GETTING STARTED ───
            HelpSectionTitle("Getting Started")
            HelpBodyText(
                "Follow these steps to set up your budget for the first time:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            HelpNumberedItem(1, "Open Settings", "Tap the gear icon in the top left to configure your currency, display preferences, and transaction categories.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(2, "Configure Your Budget", "In Settings, tap \"Configure Your Budget\" to open Budget Configuration. Choose a budget period (Daily is recommended for most people).")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(3, "Add Income Sources", "In Budget Configuration, add all reliable income sources \u2014 your salary, regular side income, etc. Set the repeat schedule for each (e.g., \"Every X Months\" on the 1st and 15th for bi-monthly pay).")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(4, "Add Recurring Expenses", "Navigate to Recurring Expenses (the sync icon on the dashboard) and add all your regular bills: rent, utilities, insurance, subscriptions, loan payments.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(5, "Hit Recalculate", "Back in Budget Configuration, tap \"Recalculate\" to compute your safe budget. Tap \"Reset Budget\" to initialize your available cash.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpNumberedItem(6, "Start Tracking", "Return to the dashboard. Your Solari display now shows your available cash. Record expenses as you spend and watch your number update in real time.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 7: IMPROVING FINANCIAL WELLBEING ───
            HelpSectionTitle("Building Better Financial Habits")
            HelpBodyText(
                "SecureSync Daily Budget is more than a tracker \u2014 it's a tool for building " +
                "lasting financial awareness. Here's how to get the most out of it:"
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Tip boxes
            FinancialTipBox(
                title = "Know Your Number",
                body = "Check the Solari display at least once a day. The simple act of knowing " +
                    "how much you can spend creates mindfulness around purchases. Research shows " +
                    "that people who track their spending spend 10\u201320% less on average, " +
                    "simply from awareness.",
                color = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.height(10.dp))

            FinancialTipBox(
                title = "Record Every Expense",
                body = "Small purchases are where budgets quietly fail. A coffee here, a snack " +
                    "there \u2014 they add up fast. Recording every expense keeps you honest " +
                    "and helps you spot patterns you might not notice otherwise. Use bank imports " +
                    "for efficiency, and manually log cash purchases.",
                color = Color(0xFF2196F3)
            )
            Spacer(modifier = Modifier.height(10.dp))

            FinancialTipBox(
                title = "Plan for the Unexpected",
                body = "Use Future Large Expenditures to plan for things like car tires, appliance " +
                    "replacements, holiday gifts, or vacations. When you save a little each period, " +
                    "these expenses don't become emergencies. The key to financial peace is " +
                    "eliminating surprises.",
                color = Color(0xFFFF9800)
            )
            Spacer(modifier = Modifier.height(10.dp))

            FinancialTipBox(
                title = "Avoid the Paycheck Trap",
                body = "Many people overspend right after payday and scramble before the next one. " +
                    "The daily budget approach smooths your income across every day, so you have " +
                    "a consistent, predictable amount to spend regardless of when your paycheck arrives. " +
                    "No more feast-and-famine cycles.",
                color = Color(0xFF9C27B0)
            )
            Spacer(modifier = Modifier.height(10.dp))

            FinancialTipBox(
                title = "Watch Your Available Cash Grow",
                body = "If you consistently spend less than your budget amount, your available cash " +
                    "will gradually increase. This surplus is your buffer for unexpected expenses " +
                    "and a sign that your financial habits are working. Don't feel pressured to " +
                    "spend it \u2014 let it grow.",
                color = Color(0xFF00BCD4)
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 8: KEY FEATURES ───
            HelpSectionTitle("Key Features at a Glance")

            HelpBulletText("Real-time budget tracking with a beautiful Solari flip display")
            HelpBulletText("Smart budget calculation that accounts for irregular income and expense timing")
            HelpBulletText("Automatic recurring expense and income recognition from bank imports")
            HelpBulletText("Future expenditure planning \u2014 save for big purchases automatically")
            HelpBulletText("Amortization \u2014 spread large past purchases over time")
            HelpBulletText("Multi-category transaction splitting with pie chart, calculator, or percentage modes")
            HelpBulletText("Encrypted transaction backup and restore")
            HelpBulletText("Bank statement import with auto-categorization")
            HelpBulletText("Duplicate transaction detection")
            HelpBulletText("Fully customizable categories with icon selection")
            HelpBulletText("Multiple currency and date format support")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDividerLine()

            // ─── SECTION 9: PRIVACY ───
            HelpSectionTitle("Privacy & Security")
            HelpBodyText(
                "Your financial data stays on your device. SecureSync Daily Budget does not " +
                "connect to the internet, does not collect analytics, and does not share your " +
                "data with anyone. When you export your transactions, you can choose encrypted " +
                "format (ChaCha20-Poly1305 with PBKDF2 key derivation) for maximum security. " +
                "Your money, your data, your control."
            )

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
