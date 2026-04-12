package com.techadvantage.budgetrak.data

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ExpenseReportGenerator {

    private const val PAGE_WIDTH = 612   // Letter size in points (8.5 x 11 inches)
    private const val PAGE_HEIGHT = 792
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    fun generateReports(
        context: Context,
        transactions: List<Transaction>,
        categories: List<Category>,
        currencySymbol: String = "$"
    ): List<File> {
        val outputDir = File(BackupManager.getBudgetrakDir(), "PDF")
        outputDir.mkdirs()

        val catMap = categories.associateBy { it.id }
        val files = mutableListOf<File>()

        for (txn in transactions) {
            val file = generateSingleReport(context, txn, catMap, currencySymbol, outputDir)
            if (file != null) files.add(file)
        }

        return files
    }

    private fun generateSingleReport(
        context: Context,
        txn: Transaction,
        catMap: Map<Int, Category>,
        currencySymbol: String,
        outputDir: File
    ): File? {
        val doc = PdfDocument()
        var pageNum = 1

        // === PAGE 1: Expense Report Form ===
        val pageInfo1 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        val page1 = doc.startPage(pageInfo1)
        val canvas = page1.canvas
        drawReportForm(canvas, txn, catMap, currencySymbol)
        doc.finishPage(page1)

        // === PHOTO PAGES: Full-size receipt photos ===
        val receiptIds = listOfNotNull(txn.receiptId1, txn.receiptId2, txn.receiptId3, txn.receiptId4, txn.receiptId5)
        for (rid in receiptIds) {
            val file = com.techadvantage.budgetrak.data.sync.ReceiptManager.getReceiptFile(context, rid)
            if (!file.exists()) continue
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue

            pageNum++
            val photoPage = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            val pPage = doc.startPage(photoPage)
            drawPhotoPage(pPage.canvas, bitmap, rid, txn, pageNum)
            bitmap.recycle()
            doc.finishPage(pPage)
        }

        // Save
        val dateStr = txn.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val merchant = txn.source.take(20).replace(Regex("[^a-zA-Z0-9 ]"), "").trim().replace(" ", "_")
        val fileName = "expense_${dateStr}_${merchant}_${txn.id}.pdf"
        val outFile = File(outputDir, fileName)
        outFile.outputStream().use { doc.writeTo(it) }
        doc.close()

        return outFile
    }

    private fun drawReportForm(
        canvas: Canvas,
        txn: Transaction,
        catMap: Map<Int, Category>,
        currencySymbol: String
    ): Float {
        var y = MARGIN
        val left = MARGIN
        val right = PAGE_WIDTH - MARGIN

        // Paints
        val titlePaint = Paint().apply {
            textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK; isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(30, 80, 30); isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.DKGRAY; isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            textSize = 12f; color = Color.BLACK; isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY; strokeWidth = 0.5f; style = Paint.Style.STROKE
        }
        val thickLinePaint = Paint().apply {
            color = Color.rgb(30, 80, 30); strokeWidth = 2f; style = Paint.Style.STROKE
        }
        val boxPaint = Paint().apply {
            color = Color.rgb(240, 248, 240); style = Paint.Style.FILL
        }
        val checkBoxPaint = Paint().apply {
            color = Color.BLACK; strokeWidth = 1f; style = Paint.Style.STROKE
        }

        // TITLE BAR
        canvas.drawRect(left, y, right, y + 36f, Paint().apply { color = Color.rgb(30, 80, 30); style = Paint.Style.FILL })
        canvas.drawText("EXPENSE REPORT", left + 12f, y + 25f, Paint().apply {
            textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE; isAntiAlias = true
        })
        // Report date on right
        canvas.drawText("Report Date: ${LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))}", right - 180f, y + 25f, Paint().apply {
            textSize = 11f; color = Color.WHITE; isAntiAlias = true
        })
        y += 46f

        // EMPLOYEE INFORMATION
        canvas.drawText("EMPLOYEE INFORMATION", left, y, headerPaint)
        y += 4f
        canvas.drawLine(left, y, right, y, thickLinePaint)
        y += 16f

        // Row 1: Name, Employee ID
        canvas.drawText("Name:", left, y, labelPaint)
        canvas.drawLine(left + 40f, y + 2f, left + 260f, y + 2f, linePaint)
        canvas.drawText("Employee ID:", left + 280f, y, labelPaint)
        canvas.drawLine(left + 360f, y + 2f, right, y + 2f, linePaint)
        y += 20f

        // Row 2: Department, Manager
        canvas.drawText("Department:", left, y, labelPaint)
        canvas.drawLine(left + 70f, y + 2f, left + 260f, y + 2f, linePaint)
        canvas.drawText("Manager:", left + 280f, y, labelPaint)
        canvas.drawLine(left + 340f, y + 2f, right, y + 2f, linePaint)
        y += 20f

        // Row 3: Cost Center, Project Code
        canvas.drawText("Cost Center:", left, y, labelPaint)
        canvas.drawLine(left + 75f, y + 2f, left + 260f, y + 2f, linePaint)
        canvas.drawText("Project Code:", left + 280f, y, labelPaint)
        canvas.drawLine(left + 365f, y + 2f, right, y + 2f, linePaint)
        y += 30f

        // EXPENSE DETAILS
        canvas.drawText("EXPENSE DETAILS", left, y, headerPaint)
        y += 4f
        canvas.drawLine(left, y, right, y, thickLinePaint)
        y += 16f

        // Transaction info (pre-filled from data)
        val dateStr = txn.date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))
        val amountStr = "$currencySymbol${"%.2f".format(txn.amount)}"
        val typeStr = if (txn.type == TransactionType.EXPENSE) "Expense" else "Income"
        val catNames = txn.categoryAmounts.mapNotNull { catMap[it.categoryId]?.name }.joinToString(", ").ifEmpty { "Uncategorized" }

        // Shaded detail box
        canvas.drawRect(left, y - 4f, right, y + 76f, boxPaint)
        canvas.drawRect(left, y - 4f, right, y + 76f, linePaint)

        canvas.drawText("Date:", left + 8f, y + 10f, labelPaint)
        canvas.drawText(dateStr, left + 45f, y + 10f, valuePaint)

        canvas.drawText("Type:", left + 200f, y + 10f, labelPaint)
        canvas.drawText(typeStr, left + 235f, y + 10f, valuePaint)

        canvas.drawText("Amount:", left + 350f, y + 10f, labelPaint)
        canvas.drawText(amountStr, left + 400f, y + 10f, Paint().apply {
            textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK; isAntiAlias = true
        })

        canvas.drawText("Merchant:", left + 8f, y + 30f, labelPaint)
        canvas.drawText(txn.source, left + 65f, y + 30f, valuePaint)

        canvas.drawText("Category:", left + 8f, y + 50f, labelPaint)
        canvas.drawText(catNames, left + 65f, y + 50f, valuePaint)

        canvas.drawText("Description:", left + 8f, y + 70f, labelPaint)
        canvas.drawText(txn.description.ifEmpty { "\u2014" }, left + 75f, y + 70f, valuePaint)

        y += 90f

        // EXPENSE PURPOSE / CATEGORY
        canvas.drawText("EXPENSE PURPOSE", left, y, headerPaint)
        y += 4f
        canvas.drawLine(left, y, right, y, thickLinePaint)
        y += 18f

        // Checkboxes in 2 columns
        val purposes = listOf(
            "Business Meal", "Client Entertainment",
            "Travel / Transportation", "Lodging / Hotel",
            "Office Supplies", "Equipment / Software",
            "Conference / Training", "Telephone / Internet",
            "Postage / Shipping", "Professional Services",
            "Vehicle / Mileage", "Other (specify below)"
        )
        val colWidth = CONTENT_WIDTH / 2
        for (i in purposes.indices step 2) {
            // Left column
            canvas.drawRect(left, y - 9f, left + 10f, y + 1f, checkBoxPaint)
            canvas.drawText(purposes[i], left + 16f, y, valuePaint)
            // Right column
            if (i + 1 < purposes.size) {
                canvas.drawRect(left + colWidth, y - 9f, left + colWidth + 10f, y + 1f, checkBoxPaint)
                canvas.drawText(purposes[i + 1], left + colWidth + 16f, y, valuePaint)
            }
            y += 18f
        }
        y += 4f

        // Other explanation line
        canvas.drawText("If Other:", left, y, labelPaint)
        canvas.drawLine(left + 55f, y + 2f, right, y + 2f, linePaint)
        y += 28f

        // BUSINESS JUSTIFICATION
        canvas.drawText("BUSINESS JUSTIFICATION", left, y, headerPaint)
        y += 4f
        canvas.drawLine(left, y, right, y, thickLinePaint)
        y += 14f

        // 4 blank lines
        for (i in 0 until 4) {
            y += 18f
            canvas.drawLine(left, y, right, y, linePaint)
        }
        y += 24f

        // ATTENDEES (for meals)
        canvas.drawText("ATTENDEES (if applicable)", left, y, headerPaint)
        y += 4f
        canvas.drawLine(left, y, right, y, thickLinePaint)
        y += 14f

        // 2 blank lines
        for (i in 0 until 2) {
            y += 18f
            canvas.drawLine(left, y, right, y, linePaint)
        }
        y += 24f

        // RECEIPT ATTACHED
        val receiptCount = listOfNotNull(txn.receiptId1, txn.receiptId2, txn.receiptId3, txn.receiptId4, txn.receiptId5).size
        canvas.drawText("RECEIPTS", left, y, headerPaint)
        y += 4f
        canvas.drawLine(left, y, right, y, thickLinePaint)
        y += 16f

        canvas.drawRect(left, y - 9f, left + 10f, y + 1f, checkBoxPaint)
        if (receiptCount > 0) {
            // Check the box
            canvas.drawLine(left + 1f, y - 4f, left + 4f, y, Paint().apply { color = Color.BLACK; strokeWidth = 1.5f })
            canvas.drawLine(left + 4f, y, left + 9f, y - 8f, Paint().apply { color = Color.BLACK; strokeWidth = 1.5f })
        }
        canvas.drawText("Receipt(s) attached: $receiptCount photo(s) on following page(s)", left + 16f, y, valuePaint)
        y += 16f
        canvas.drawRect(left, y - 9f, left + 10f, y + 1f, checkBoxPaint)
        canvas.drawText("No receipt available \u2014 reason:", left + 16f, y, valuePaint)
        canvas.drawLine(left + 190f, y + 2f, right, y + 2f, linePaint)
        y += 30f

        // APPROVAL
        canvas.drawText("APPROVAL", left, y, headerPaint)
        y += 4f
        canvas.drawLine(left, y, right, y, thickLinePaint)
        y += 20f

        canvas.drawText("Employee Signature:", left, y, labelPaint)
        canvas.drawLine(left + 110f, y + 2f, left + 320f, y + 2f, linePaint)
        canvas.drawText("Date:", left + 340f, y, labelPaint)
        canvas.drawLine(left + 375f, y + 2f, right, y + 2f, linePaint)
        y += 24f

        canvas.drawText("Supervisor Signature:", left, y, labelPaint)
        canvas.drawLine(left + 120f, y + 2f, left + 320f, y + 2f, linePaint)
        canvas.drawText("Date:", left + 340f, y, labelPaint)
        canvas.drawLine(left + 375f, y + 2f, right, y + 2f, linePaint)
        y += 24f

        canvas.drawRect(left, y - 9f, left + 10f, y + 1f, checkBoxPaint)
        canvas.drawText("Approved", left + 16f, y, valuePaint)
        canvas.drawRect(left + 100f, y - 9f, left + 110f, y + 1f, checkBoxPaint)
        canvas.drawText("Denied", left + 116f, y, valuePaint)
        canvas.drawRect(left + 180f, y - 9f, left + 190f, y + 1f, checkBoxPaint)
        canvas.drawText("Requires additional information", left + 196f, y, valuePaint)
        y += 20f

        // Footer
        y = PAGE_HEIGHT - MARGIN - 12f
        canvas.drawLine(left, y - 4f, right, y - 4f, thickLinePaint)
        canvas.drawText("Generated by BudgeTrak \u2014 ${LocalDate.now()}", left, y + 8f, Paint().apply {
            textSize = 8f; color = Color.GRAY; isAntiAlias = true
        })
        canvas.drawText("Page 1", right - 35f, y + 8f, Paint().apply {
            textSize = 8f; color = Color.GRAY; isAntiAlias = true
        })

        return y
    }

    private fun drawPhotoPage(
        canvas: Canvas,
        bitmap: Bitmap,
        receiptId: String,
        txn: Transaction,
        pageNum: Int
    ) {
        val left = MARGIN
        val right = PAGE_WIDTH - MARGIN
        var y = MARGIN

        val headerPaint = Paint().apply {
            textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(30, 80, 30); isAntiAlias = true
        }
        val infoPaint = Paint().apply {
            textSize = 9f; color = Color.DKGRAY; isAntiAlias = true
        }

        // Mini header
        canvas.drawText("RECEIPT \u2014 ${txn.source} \u2014 ${txn.date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))}", left, y + 12f, headerPaint)
        canvas.drawLine(left, y + 16f, right, y + 16f, Paint().apply { color = Color.rgb(30, 80, 30); strokeWidth = 1f })
        y += 28f

        // Scale photo to fit page width, maintain aspect ratio
        val availWidth = CONTENT_WIDTH
        val availHeight = PAGE_HEIGHT - y - MARGIN - 24f  // leave room for footer

        val scale = minOf(availWidth / bitmap.width, availHeight / bitmap.height)
        val drawWidth = (bitmap.width * scale).toInt()
        val drawHeight = (bitmap.height * scale).toInt()

        // Center horizontally
        val drawLeft = left + (availWidth - drawWidth) / 2f

        // Draw border
        canvas.drawRect(drawLeft - 1f, y - 1f, drawLeft + drawWidth + 1f, y + drawHeight + 1f,
            Paint().apply { color = Color.LTGRAY; strokeWidth = 1f; style = Paint.Style.STROKE })

        // Draw image
        val destRect = RectF(drawLeft, y, drawLeft + drawWidth, y + drawHeight)
        canvas.drawBitmap(bitmap, null, destRect, Paint().apply { isAntiAlias = true; isFilterBitmap = true })

        // Footer
        val footerY = PAGE_HEIGHT - MARGIN - 12f
        canvas.drawLine(left, footerY - 4f, right, footerY - 4f, Paint().apply { color = Color.rgb(30, 80, 30); strokeWidth = 1f })
        canvas.drawText("Receipt ID: ${receiptId.take(8)}...", left, footerY + 8f, infoPaint)
        canvas.drawText("Page $pageNum", right - 35f, footerY + 8f, infoPaint)
    }
}
