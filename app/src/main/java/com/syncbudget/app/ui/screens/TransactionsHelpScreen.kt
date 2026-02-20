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
import com.syncbudget.app.ui.strings.LocalStrings
import com.syncbudget.app.ui.theme.LocalSyncBudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsHelpScreen(onBack: () -> Unit) {
    val customColors = LocalSyncBudgetColors.current
    val S = LocalStrings.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = S.transactionsHelp.title,
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
            SectionTitle(S.transactionsHelp.overviewTitle)
            BodyText(S.transactionsHelp.overviewBody)
            Spacer(modifier = Modifier.height(12.dp))

            // ─── SECTION 2: HEADER BAR ───
            SectionTitle(S.transactionsHelp.headerTitle)
            BodyText(S.transactionsHelp.headerBody)
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
                        S.transactions.title,
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

            IconExplanationRow(Icons.AutoMirrored.Filled.ArrowBack, S.common.back, S.transactionsHelp.backDesc)
            IconExplanationRow(Icons.Filled.Save, S.transactions.save, S.transactionsHelp.saveDesc)
            IconExplanationRow(Icons.Filled.MoveToInbox, S.transactions.load, S.transactionsHelp.loadDesc)
            IconExplanationRow(Icons.AutoMirrored.Filled.Help, S.common.help, S.transactionsHelp.helpDesc)
            Spacer(modifier = Modifier.height(4.dp))
            BodyText(
                S.transactionsHelp.saveLoadNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 3: ACTION BAR ───
            SectionTitle(S.transactionsHelp.actionBarTitle)
            BodyText(S.transactionsHelp.actionBarBody)
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
                        Text(S.transactions.all, style = MaterialTheme.typography.bodyMedium, color = textColor)
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
                    Text(S.transactions.all, style = MaterialTheme.typography.bodySmall, color = textColor)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    S.transactionsHelp.filterDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            IconExplanationRow(Icons.Filled.Add, S.transactions.addIncome, S.transactionsHelp.addIncomeDesc, Color(0xFF4CAF50))
            IconExplanationRow(Icons.Filled.Remove, S.transactions.addExpense, S.transactionsHelp.addExpenseDesc, Color(0xFFF44336))
            IconExplanationRow(Icons.Filled.Search, S.transactions.search, S.transactionsHelp.searchDesc)
            Spacer(modifier = Modifier.height(4.dp))
            Column(modifier = Modifier.padding(start = 40.dp)) {
                BulletText(S.transactionsHelp.dateSearchBullet)
                BulletText(S.transactionsHelp.textSearchBullet)
                BulletText(S.transactionsHelp.amountSearchBullet)
            }
            Spacer(modifier = Modifier.height(4.dp))
            BodyText(S.transactionsHelp.searchNote, italic = true)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 4: TRANSACTION LIST ───
            SectionTitle(S.transactionsHelp.listTitle)
            BodyText(S.transactionsHelp.listBody)
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

            BulletText(S.transactionsHelp.listIconBullet)
            BulletText(S.transactionsHelp.listDateBullet)
            BulletText(S.transactionsHelp.listMerchantBullet)
            BulletText(S.transactionsHelp.listAmountBullet)
            Spacer(modifier = Modifier.height(10.dp))

            // Category icon tinting explanation
            SubSectionTitle(S.transactionsHelp.iconColorsTitle)
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
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(S.transactionsHelp.coloredLabel) }
                        append(" \u2014 ${S.transactionsHelp.coloredDesc}")
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
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(S.transactionsHelp.defaultLabel) }
                        append(" \u2014 ${S.transactionsHelp.defaultDesc}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            BodyText(S.transactionsHelp.filterByIconNote)
            Spacer(modifier = Modifier.height(10.dp))

            // Multi-category rows
            SubSectionTitle(S.transactionsHelp.multiCategoryTitle)
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
                    S.transactionsHelp.multiCategoryBody,
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
            SectionTitle(S.transactionsHelp.tapEditTitle)
            BulletText(S.transactionsHelp.tapBullet)
            BulletText(S.transactionsHelp.longPressBullet)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 6: SELECTION MODE ───
            SectionTitle(S.transactionsHelp.selectionTitle)
            BodyText(S.transactionsHelp.selectionBody)
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
                    Text(S.transactions.selectAll, style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.weight(1f))
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
                Text(S.transactionsHelp.selectAllDesc, style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            Spacer(modifier = Modifier.height(4.dp))
            IconExplanationRow(Icons.Filled.Category, S.transactions.changeCategory, S.transactionsHelp.changeCategoryDesc)
            IconExplanationRow(Icons.Filled.Edit, S.transactions.editMerchant, S.transactionsHelp.editMerchantDesc)
            IconExplanationRow(Icons.Filled.Delete, S.common.delete, S.transactionsHelp.deleteDesc, Color(0xFFF44336))
            IconExplanationRow(Icons.Filled.Close, S.common.close, S.transactionsHelp.closeDesc)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 7: ADD / EDIT DIALOG ───
            SectionTitle(S.transactionsHelp.addEditTitle)
            BodyText(S.transactionsHelp.addEditBody)
            Spacer(modifier = Modifier.height(8.dp))
            NumberedItem(1, S.transactionsHelp.fieldDate, S.transactionsHelp.fieldDateDesc)
            NumberedItem(2, S.transactionsHelp.fieldMerchant, S.transactionsHelp.fieldMerchantDesc)
            NumberedItem(3, S.transactionsHelp.fieldCategory, S.transactionsHelp.fieldCategoryDesc)
            NumberedItem(4, S.transactionsHelp.fieldAmount, S.transactionsHelp.fieldAmountDesc)
            Spacer(modifier = Modifier.height(10.dp))

            SubSectionTitle(S.transactionsHelp.singleCatTitle)
            BodyText(S.transactionsHelp.singleCatBody)
            Spacer(modifier = Modifier.height(10.dp))

            SubSectionTitle(S.transactionsHelp.multiCatTitle)
            BodyText(S.transactionsHelp.multiCatBody)
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

            IconExplanationRow(Icons.Filled.PieChart, S.transactions.pieChart, S.transactionsHelp.pieChartDesc)
            IconExplanationRow(Icons.Filled.Calculate, S.transactions.calculator, S.transactionsHelp.calculatorDesc)
            IconExplanationRow(Icons.Filled.Percent, S.transactions.percentage, S.transactionsHelp.percentageDesc)
            Spacer(modifier = Modifier.height(10.dp))

            // Pie Chart illustration
            SubSectionTitle(S.transactionsHelp.pieChartModeTitle)
            BodyText(S.transactionsHelp.pieChartModeBody)
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
                S.transactionsHelp.pieChartDragNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(10.dp))

            SubSectionTitle(S.transactionsHelp.autoFillTitle)
            BodyText(S.transactionsHelp.autoFillBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 8: DUPLICATE DETECTION ───
            SectionTitle(S.transactionsHelp.duplicateTitle)
            BodyText(S.transactionsHelp.duplicateBody)
            Spacer(modifier = Modifier.height(6.dp))
            BulletText(S.transactionsHelp.dupAmountBullet)
            BulletText(S.transactionsHelp.dupDateBullet)
            BulletText(S.transactionsHelp.dupMerchantBullet)
            Spacer(modifier = Modifier.height(8.dp))
            BodyText(S.transactionsHelp.dupDialogBody)
            Spacer(modifier = Modifier.height(4.dp))
            BulletText(S.transactionsHelp.dupIgnore)
            BulletText(S.transactionsHelp.dupKeepNew)
            BulletText(S.transactionsHelp.dupKeepExisting)
            BulletText(S.transactionsHelp.dupIgnoreAll)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 9: SAVE ───
            SectionTitle(S.transactionsHelp.savingTitle)
            BodyText(S.transactionsHelp.savingBody)
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
                    Text(S.transactions.saveTransactions, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, color = textColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(S.transactions.csv, style = MaterialTheme.typography.bodyMedium, color = textColor)
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
                                Text(S.transactions.encrypted, style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            SubSectionTitle(S.transactionsHelp.csvFormatTitle)
            BodyText(S.transactionsHelp.csvFormatBody)
            Spacer(modifier = Modifier.height(10.dp))

            SubSectionTitle(S.transactionsHelp.encryptedFormatTitle)
            BodyText(S.transactionsHelp.encryptedFormatBody)
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
                        Text(S.transactionsHelp.encryptionDetailsTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = textColor)
                    }
                    Text(
                        S.transactionsHelp.encryptionDetailsBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            SubSectionTitle(S.transactionsHelp.passwordImportanceTitle)
            BodyText(S.transactionsHelp.passwordImportanceBody)
            Spacer(modifier = Modifier.height(8.dp))

            // Password strength table
            PasswordStrengthTable(S)
            Spacer(modifier = Modifier.height(10.dp))

            BodyText(S.transactionsHelp.pbkdfNote)
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
                    Text(S.transactionsHelp.recommendedTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = textColor)
                    Text(
                        S.transactionsHelp.recommendedBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            BodyText(
                S.transactionsHelp.passwordMinNote,
                italic = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 10: LOAD / IMPORT ───
            SectionTitle(S.transactionsHelp.loadingTitle)
            BodyText(S.transactionsHelp.loadingBody)
            Spacer(modifier = Modifier.height(8.dp))

            NumberedItem(1, S.transactionsHelp.loadUsBank, S.transactionsHelp.loadUsBankDesc)
            Spacer(modifier = Modifier.height(4.dp))
            NumberedItem(2, S.transactionsHelp.loadCsv, S.transactionsHelp.loadCsvDesc)
            Spacer(modifier = Modifier.height(4.dp))
            NumberedItem(3, S.transactionsHelp.loadEncrypted, S.transactionsHelp.loadEncryptedDesc)
            Spacer(modifier = Modifier.height(10.dp))

            BodyText(S.transactionsHelp.loadPasswordNote)
            Spacer(modifier = Modifier.height(8.dp))
            BodyText(S.transactionsHelp.loadDuplicateNote)
            Spacer(modifier = Modifier.height(8.dp))

            SubSectionTitle(S.transactionsHelp.autoCatTitle)
            BodyText(S.transactionsHelp.autoCatBody)
            Spacer(modifier = Modifier.height(16.dp))

            HelpDivider()

            // ─── SECTION 11: TIPS ───
            SectionTitle(S.transactionsHelp.tipsTitle)
            BulletText(S.transactionsHelp.tip1)
            BulletText(S.transactionsHelp.tip2)
            BulletText(S.transactionsHelp.tip3)
            BulletText(S.transactionsHelp.tip4)
            BulletText(S.transactionsHelp.tip5)

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
private fun PasswordStrengthTable(S: com.syncbudget.app.ui.strings.AppStrings) {
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
            Text(S.transactionsHelp.passwordTableHeader, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = textColor, modifier = Modifier.weight(1.2f))
            Text(S.transactionsHelp.passwordTableExample, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = textColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(S.transactionsHelp.passwordTableTime, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = textColor, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = dimColor.copy(alpha = 0.2f))

        PasswordTableRow(S.transactionsHelp.pw8Lower, S.transactionsHelp.pw8LowerEx, S.transactionsHelp.pw8LowerTime, Color(0xFFF44336))
        HorizontalDivider(color = dimColor.copy(alpha = 0.1f))
        PasswordTableRow(S.transactionsHelp.pw8Mixed, S.transactionsHelp.pw8MixedEx, S.transactionsHelp.pw8MixedTime, Color(0xFFF44336))
        HorizontalDivider(color = dimColor.copy(alpha = 0.1f))
        PasswordTableRow(S.transactionsHelp.pw10Mixed, S.transactionsHelp.pw10MixedEx, S.transactionsHelp.pw10MixedTime, Color(0xFFFF9800))
        HorizontalDivider(color = dimColor.copy(alpha = 0.1f))
        PasswordTableRow(S.transactionsHelp.pw12Mixed, S.transactionsHelp.pw12MixedEx, S.transactionsHelp.pw12MixedTime, Color(0xFF4CAF50))
        HorizontalDivider(color = dimColor.copy(alpha = 0.1f))
        PasswordTableRow(S.transactionsHelp.pw16Mixed, S.transactionsHelp.pw16MixedEx, S.transactionsHelp.pw16MixedTime, Color(0xFF4CAF50))
        HorizontalDivider(color = dimColor.copy(alpha = 0.1f))
        PasswordTableRow(S.transactionsHelp.pw4Word, S.transactionsHelp.pw4WordEx, S.transactionsHelp.pw4WordTime, Color(0xFF4CAF50))
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
