package com.syncbudget.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Transactions Help",
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
            SectionTitle("Overview")
            BodyText(
                "The Transactions screen is where you manage all income and expenses. " +
                "You can add, edit, delete, search, filter, import, and export transactions."
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ─── SECTION 2: HEADER BAR ───
            SectionTitle("Header Bar")
            BodyText("The header bar contains navigation and action icons:")
            Spacer(modifier = Modifier.height(8.dp))

            // Visual header bar mockup
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
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = customColors.headerText,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = customColors.headerText
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = null,
                        tint = customColors.headerText,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        Icons.Filled.MoveToInbox,
                        contentDescription = null,
                        tint = customColors.headerText,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.Help,
                        contentDescription = null,
                        tint = customColors.headerText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            IconExplanationRow(Icons.AutoMirrored.Filled.ArrowBack, "Back", "Return to the main screen.")
            IconExplanationRow(Icons.Filled.Save, "Save", "Save all transactions to a file. Requires Paid User.")
            IconExplanationRow(Icons.Filled.MoveToInbox, "Load", "Import or load transactions from a file. Requires Paid User.")
            IconExplanationRow(Icons.AutoMirrored.Filled.Help, "Help", "Opens this help page.")
            Spacer(modifier = Modifier.height(4.dp))
            BodyText(
                "The Save and Load icons appear dimmed if Paid User is not enabled in Settings.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 3: ACTION BAR ───
            SectionTitle("Action Bar")
            BodyText("Below the header, the action bar provides quick access to common operations:")
            Spacer(modifier = Modifier.height(8.dp))

            // Visual action bar mockup
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, dimColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("All", style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    Icon(
                        Icons.Filled.Add, contentDescription = null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp)
                    )
                    Icon(
                        Icons.Filled.Remove, contentDescription = null,
                        tint = Color(0xFFF44336), modifier = Modifier.size(32.dp)
                    )
                    Icon(
                        Icons.Filled.Search, contentDescription = null,
                        tint = textColor, modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Filter button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, dimColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("All", style = MaterialTheme.typography.bodySmall, color = textColor)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Filter toggle \u2014 cycles through: All, Expenses, Income.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            IconExplanationRow(Icons.Filled.Add, "Add Income", "Create a new income transaction.", Color(0xFF4CAF50))
            IconExplanationRow(Icons.Filled.Remove, "Add Expense", "Create a new expense transaction.", Color(0xFFF44336))
            IconExplanationRow(Icons.Filled.Search, "Search", "Open search menu with three options:")
            Spacer(modifier = Modifier.height(4.dp))
            Column(modifier = Modifier.padding(start = 40.dp)) {
                BulletText("Date Search \u2014 pick a start and end date")
                BulletText("Text Search \u2014 search by merchant/source name")
                BulletText("Amount Search \u2014 search by amount range")
            }
            Spacer(modifier = Modifier.height(4.dp))
            BodyText("While search results are active, a banner appears at the top. Tap the banner to clear the search.", italic = true)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 4: TRANSACTION LIST ───
            SectionTitle("Transaction List")
            BodyText(
                "Transactions are displayed in a scrollable list, sorted by date (newest first). " +
                "Each row shows:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Mock transaction row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, dimColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Restaurant,
                        contentDescription = null,
                        tint = headerBg,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("2026-02-15", style = MaterialTheme.typography.bodyMedium, color = textColor)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Grocery Store", style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.weight(1f))
                    Text(
                        "-$45.20",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF44336)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            BulletText("Category icon (left) \u2014 colored to indicate the category")
            BulletText("Date \u2014 formatted per your Settings preference")
            BulletText("Merchant/Source \u2014 the name of the payee or payer")
            BulletText("Amount \u2014 red for expenses, green for income")
            Spacer(modifier = Modifier.height(10.dp))

            // Category icon tinting explanation
            SubSectionTitle("Category Icon Colors")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = headerBg,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Colored") }
                        append(" \u2014 category was set or confirmed by you")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Default") }
                        append(" \u2014 auto-assigned during import (not yet confirmed)")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            BodyText("Tap a category icon to filter the list to only that category. A filter banner will appear; tap it to clear.")
            Spacer(modifier = Modifier.height(10.dp))

            // Multi-category rows
            SubSectionTitle("Multi-Category Transactions")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = headerBg,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "A list icon indicates the transaction is split across multiple categories. " +
                    "Tap it to expand and see the per-category breakdown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Mock expanded breakdown
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, dimColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = headerBg, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2026-02-10", style = MaterialTheme.typography.bodyMedium, color = textColor)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Big Box Store", style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.weight(1f))
                        Text("-$120.00", fontWeight = FontWeight.SemiBold, color = Color(0xFFF44336), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Sub-rows
                    Row(modifier = Modifier.padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Restaurant, contentDescription = null, tint = headerBg.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Food", style = MaterialTheme.typography.bodySmall, color = dimColor, modifier = Modifier.weight(1f))
                        Text("$80.00", style = MaterialTheme.typography.bodySmall, color = dimColor)
                    }
                    Row(modifier = Modifier.padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = headerBg.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Shopping", style = MaterialTheme.typography.bodySmall, color = dimColor, modifier = Modifier.weight(1f))
                        Text("$40.00", style = MaterialTheme.typography.bodySmall, color = dimColor)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 5: TAP & LONG PRESS ───
            SectionTitle("Tapping & Editing")
            BulletText("Tap a transaction to open the Edit dialog")
            BulletText("Long-press a transaction to enter selection mode")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 6: SELECTION MODE ───
            SectionTitle("Selection Mode")
            BodyText("Long-press any transaction to enter selection mode. A toolbar appears with bulk actions:")
            Spacer(modifier = Modifier.height(8.dp))

            // Mock selection bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Checkbox
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(2.dp, accentColor, RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select All", style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Category, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(18.dp).border(2.dp, accentColor, RoundedCornerShape(3.dp)))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Select All \u2014 toggle all visible transactions", style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            Spacer(modifier = Modifier.height(4.dp))
            IconExplanationRow(Icons.Filled.Category, "Change Category", "Set a single category for all selected transactions.")
            IconExplanationRow(Icons.Filled.Edit, "Edit Merchant", "Replace the merchant/source name on all selected transactions.")
            IconExplanationRow(Icons.Filled.Delete, "Delete", "Delete all selected transactions.", Color(0xFFF44336))
            IconExplanationRow(Icons.Filled.Close, "Close", "Exit selection mode without changes.")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 7: ADD / EDIT DIALOG ───
            SectionTitle("Add / Edit Transaction Dialog")
            BodyText(
                "When adding or editing a transaction, a full-screen dialog appears with these fields:"
            )
            Spacer(modifier = Modifier.height(8.dp))
            NumberedItem(1, "Date", "Tap the calendar icon to pick a date.")
            NumberedItem(2, "Merchant / Source", "Type the name of the payee (expenses) or income source.")
            NumberedItem(3, "Category", "Tap to open the category picker. You can select one or multiple categories.")
            NumberedItem(4, "Amount", "Enter the transaction amount.")
            Spacer(modifier = Modifier.height(10.dp))

            SubSectionTitle("Single Category")
            BodyText("With one category selected, simply enter the total amount in the Amount field.")
            Spacer(modifier = Modifier.height(10.dp))

            SubSectionTitle("Multiple Categories")
            BodyText(
                "When two or more categories are selected, you unlock three entry modes. " +
                "First enter the Total amount, then choose a mode:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Entry mode icons mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, dimColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PieChart, contentDescription = null, tint = accentColor, modifier = Modifier.size(32.dp))
                    Icon(Icons.Filled.Calculate, contentDescription = null, tint = dimColor.copy(alpha = 0.35f), modifier = Modifier.size(32.dp))
                    Icon(Icons.Filled.Percent, contentDescription = null, tint = dimColor.copy(alpha = 0.35f), modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            IconExplanationRow(Icons.Filled.PieChart, "Pie Chart", "Drag slices on an interactive pie chart to distribute the total across categories.")
            IconExplanationRow(Icons.Filled.Calculate, "Calculator", "Enter a specific dollar amount for each category. The last empty field auto-fills.")
            IconExplanationRow(Icons.Filled.Percent, "Percentage", "Enter a percentage for each category. Percentages auto-adjust to total 100%.")
            Spacer(modifier = Modifier.height(10.dp))

            // Pie Chart illustration
            SubSectionTitle("Pie Chart Mode")
            BodyText("The interactive pie chart lets you visually distribute a transaction across categories by dragging the divider lines between slices.")
            Spacer(modifier = Modifier.height(8.dp))

            // Mini pie chart illustration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    val radius = size.minDimension / 2
                    val center = Offset(size.width / 2, size.height / 2)
                    val arcSize = Size(radius * 2, radius * 2)
                    val topLeft = Offset(center.x - radius, center.y - radius)

                    // Slice 1: 55% - Green (filled)
                    drawArc(
                        color = Color(0xFF4CAF50),
                        startAngle = -90f,
                        sweepAngle = 198f,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize
                    )
                    // Slice 2: 30% - Blue (filled)
                    drawArc(
                        color = Color(0xFF2196F3),
                        startAngle = 108f,
                        sweepAngle = 108f,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize
                    )
                    // Slice 3: 15% - Orange (filled)
                    drawArc(
                        color = Color(0xFFFF9800),
                        startAngle = 216f,
                        sweepAngle = 54f,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize
                    )

                    // White outline and division lines
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )
                    // Division line at -90° (top)
                    drawLine(
                        color = Color.White,
                        start = center,
                        end = Offset(center.x, center.y - radius),
                        strokeWidth = 2f
                    )
                    // Division line at 108°
                    val rad108 = Math.toRadians(108.0)
                    drawLine(
                        color = Color.White,
                        start = center,
                        end = Offset(
                            center.x + radius * kotlin.math.cos(rad108).toFloat(),
                            center.y + radius * kotlin.math.sin(rad108).toFloat()
                        ),
                        strokeWidth = 2f
                    )
                    // Division line at 216°
                    val rad216 = Math.toRadians(216.0)
                    drawLine(
                        color = Color.White,
                        start = center,
                        end = Offset(
                            center.x + radius * kotlin.math.cos(rad216).toFloat(),
                            center.y + radius * kotlin.math.sin(rad216).toFloat()
                        ),
                        strokeWidth = 2f
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                PieLegendItem(Color(0xFF4CAF50), "Food 55%")
                Spacer(modifier = Modifier.width(12.dp))
                PieLegendItem(Color(0xFF2196F3), "Shopping 30%")
                Spacer(modifier = Modifier.width(12.dp))
                PieLegendItem(Color(0xFFFF9800), "Other 15%")
            }
            Spacer(modifier = Modifier.height(6.dp))
            BodyText(
                "Drag the boundary between any two slices to redistribute. " +
                "The category labels and dollar amounts update in real time beneath the chart.",
                italic = true
            )
            Spacer(modifier = Modifier.height(10.dp))

            SubSectionTitle("Auto-Fill Behavior")
            BodyText(
                "In both Calculator and Percentage modes, when all fields except one are filled, " +
                "the remaining field automatically fills with the balance. " +
                "For example, with a \$100 total and two categories, entering \$60 for Food " +
                "will automatically set Shopping to \$40."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 8: DUPLICATE DETECTION ───
            SectionTitle("Duplicate Detection")
            BodyText(
                "When you save a new transaction or import from a file, the app checks for possible duplicates. " +
                "A transaction is flagged if it matches an existing one on all three criteria:"
            )
            Spacer(modifier = Modifier.height(6.dp))
            BulletText("Amount within 1% of each other")
            BulletText("Date within 7 days of each other")
            BulletText("Merchant name shares a common substring")
            Spacer(modifier = Modifier.height(8.dp))
            BodyText("When a duplicate is detected, you'll see a dialog with four options:")
            Spacer(modifier = Modifier.height(4.dp))
            BulletText("Ignore \u2014 keep both transactions")
            BulletText("Keep New \u2014 replace the existing with the new one")
            BulletText("Keep Existing \u2014 discard the new transaction")
            BulletText("Ignore All \u2014 keep all remaining duplicates (import only)")
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 9: SAVE ───
            SectionTitle("Saving Transactions")
            BodyText("Tap the Save icon in the header to export all transactions to a file. Two formats are available:")
            Spacer(modifier = Modifier.height(10.dp))

            // Save format mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, dimColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Save Transactions", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, color = textColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("CSV", style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, dimColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = textColor)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Encrypted", style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            SubSectionTitle("CSV Format")
            BodyText(
                "Saves your transactions as a plain-text CSV file (syncbudget_transactions.csv). " +
                "This file preserves all data including categories and can be loaded back into the app. " +
                "It can also be opened in spreadsheet software like Excel or Google Sheets for review."
            )
            Spacer(modifier = Modifier.height(10.dp))

            SubSectionTitle("Encrypted Format")
            BodyText(
                "Saves your transactions in an encrypted file (syncbudget_transactions.enc) " +
                "protected with a password you choose. This is the recommended format for backups " +
                "and transferring data between devices, as it keeps your financial information private."
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Encryption details box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.07f))
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Encryption Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = textColor)
                    }
                    Text(
                        "Your file is protected with ChaCha20-Poly1305 authenticated encryption \u2014 " +
                        "the same family of ciphers used by modern messaging apps and VPNs. " +
                        "Your password is never stored; instead, it is transformed into an encryption key " +
                        "using PBKDF2 with 100,000 iterations, making brute-force attacks extremely slow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            SubSectionTitle("Why Your Password Matters")
            BodyText(
                "Encryption is only as strong as your password. A short or common password " +
                "can be guessed quickly, even with strong encryption. Here's what a modern high-end " +
                "graphics card (capable of testing billions of simple hashes per second) could achieve:"
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Password strength table
            PasswordStrengthTable()
            Spacer(modifier = Modifier.height(10.dp))

            BodyText(
                "Because your password goes through 100,000 rounds of PBKDF2 before being used as an " +
                "encryption key, each guess is deliberately made very expensive. A single high-end GPU " +
                "can only attempt roughly 100,000\u2013500,000 passwords per second against this file \u2014 " +
                "millions of times slower than attacking a simple hash."
            )
            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF4CAF50).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Recommended Password Strategy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = textColor)
                    Text(
                        "Use 12 or more characters combining uppercase letters, lowercase letters, " +
                        "numbers, and symbols. A passphrase of 4\u20135 random words (e.g., \"correct horse battery staple\") " +
                        "is also excellent. With a strong password of this kind, " +
                        "even a nation-state adversary with thousands of GPUs " +
                        "would need trillions of years to crack your file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            BodyText(
                "The minimum required length is 8 characters, but longer is always better. " +
                "You must enter your password twice to confirm it before saving. " +
                "There is no password recovery \u2014 if you forget your password, " +
                "the file cannot be opened.",
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 10: LOAD / IMPORT ───
            SectionTitle("Loading & Importing")
            BodyText("Tap the Load icon in the header to import transactions from a file. Three formats are supported:")
            Spacer(modifier = Modifier.height(8.dp))

            NumberedItem(1, "US Bank", "Import transactions from a US Bank CSV export file. " +
                    "Transactions are automatically categorized based on your existing merchant history.")
            Spacer(modifier = Modifier.height(4.dp))
            NumberedItem(2, "SecureSync CSV Save File", "Load a CSV file previously saved from this app. " +
                    "All categories and data are preserved exactly as they were.")
            Spacer(modifier = Modifier.height(4.dp))
            NumberedItem(3, "SecureSync Encrypted Save File", "Load a previously encrypted save file. " +
                    "You must enter the password used when the file was saved.")
            Spacer(modifier = Modifier.height(10.dp))

            BodyText(
                "For encrypted files, the password field appears automatically when you select the " +
                "encrypted format. The \"Select File\" button is disabled until you enter at least 8 characters."
            )
            Spacer(modifier = Modifier.height(8.dp))
            BodyText(
                "After loading, each imported transaction is checked for duplicates against your " +
                "existing transactions. If duplicates are found, you'll be prompted to resolve them " +
                "one at a time (see Duplicate Detection above)."
            )
            Spacer(modifier = Modifier.height(8.dp))

            SubSectionTitle("Auto-Categorization (Bank Imports)")
            BodyText(
                "When importing from a bank CSV, the app looks at your existing transactions from the " +
                "past 6 months to find matching merchants. If a match is found, the most frequently used " +
                "category is assigned automatically. Transactions without a match are assigned to \"Other\". " +
                "Auto-categorized transactions show a default-colored icon until you manually confirm " +
                "or change the category."
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 11: TIPS ───
            SectionTitle("Tips")
            BulletText("Use CSV saves for spreadsheet-compatible backups that you can review on a computer.")
            BulletText("Use Encrypted saves for secure backups and transferring data between devices.")
            BulletText("The same file can be loaded as many times as needed \u2014 duplicate detection prevents accidental double-entries.")
            BulletText("Use the category filter (tap any category icon) combined with selection mode for efficient bulk edits.")
            BulletText("After a bank import, review auto-categorized transactions and use bulk Change Category to correct any misassignments.")

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─── Helper Composables ───

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SubSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun BodyText(text: String, italic: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        lineHeight = 22.sp
    )
}

@Composable
private fun BulletText(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "\u2022",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun NumberedItem(number: Int, title: String, description: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(22.dp)
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
                append(" \u2014 $description")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun IconExplanationRow(
    icon: ImageVector,
    label: String,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label) }
                append(" \u2014 $description")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun HelpDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
    )
}

@Composable
private fun PieLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun PasswordStrengthTable() {
    val textColor = MaterialTheme.colorScheme.onBackground
    val dimColor = textColor.copy(alpha = 0.6f)
    val headerColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, dimColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
    ) {
        // Table header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("Password", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = textColor, modifier = Modifier.weight(1.2f))
            Text("Example", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = textColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("Time to Crack", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = textColor, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = dimColor.copy(alpha = 0.2f))

        PasswordTableRow("8 chars, lowercase", "password", "minutes", Color(0xFFF44336))
        HorizontalDivider(color = dimColor.copy(alpha = 0.1f))
        PasswordTableRow("8 chars, mixed", "Pa\$sw0rd", "hours", Color(0xFFF44336))
        HorizontalDivider(color = dimColor.copy(alpha = 0.1f))
        PasswordTableRow("10 chars, mixed", "K9#mP2x!qL", "months", Color(0xFFFF9800))
        HorizontalDivider(color = dimColor.copy(alpha = 0.1f))
        PasswordTableRow("12 chars, mixed", "7hR!q2Lp#9Zk", "millennia", Color(0xFF4CAF50))
        HorizontalDivider(color = dimColor.copy(alpha = 0.1f))
        PasswordTableRow("16+ chars, mixed", "cT8!nQ#2mK@5rW9j", "trillions of years", Color(0xFF4CAF50))
        HorizontalDivider(color = dimColor.copy(alpha = 0.1f))
        PasswordTableRow("4-word phrase", "maple cloud river fox", "trillions of years", Color(0xFF4CAF50))
    }
}

@Composable
private fun PasswordTableRow(
    type: String,
    example: String,
    timeToCrack: String,
    strengthColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            type,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1.2f),
            lineHeight = 16.sp
        )
        Text(
            example,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
        Text(
            timeToCrack,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = strengthColor,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            lineHeight = 16.sp
        )
    }
}
