package com.techadvantage.budgetrak.data

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

enum class BankFormat(val displayName: String) {
    GENERIC_CSV("Any Bank CSV"),
    US_BANK("US Bank"),
    SECURESYNC_CSV("BudgeTrak CSV Save File")
}

data class CsvParseResult(
    val transactions: List<Transaction>,
    val error: String?,
    val totalDataRows: Int = 0,
    val skippedRows: Int = 0
)

fun parseUsBank(reader: BufferedReader, existingIds: Set<Int>): CsvParseResult {
    val transactions = mutableListOf<Transaction>()
    val usedIds = existingIds.toMutableSet()
    var error: String? = null
    var totalDataRows = 0
    var skippedRows = 0

    try {
        val header = reader.readLine() ?: return CsvParseResult(emptyList(), "Empty file")

        var lineNumber = 1
        reader.forEachLine { line ->
            lineNumber++
            if (line.isBlank()) return@forEachLine
            totalDataRows++
            try {
                val fields = parseCsvLine(line)
                if (fields.size < 5) {
                    skippedRows++
                    error = "Line $lineNumber: expected 5 fields, got ${fields.size}"
                    return@forEachLine
                }

                val date = LocalDate.parse(fields[0].trim())
                if (date.year < 2000 || date.year > LocalDate.now().year + 1) {
                    skippedRows++
                    error = "Line $lineNumber: date out of range (${fields[0].trim()})"
                    return@forEachLine
                }
                val txnType = fields[1].trim()
                val name = fields[2].trim()
                val amount = abs(fields[4].trim().toDouble())

                val type = if (txnType.equals("CREDIT", ignoreCase = true))
                    TransactionType.INCOME else TransactionType.EXPENSE

                val id = generateTransactionId(usedIds)
                usedIds.add(id)

                transactions.add(
                    Transaction(
                        id = id,
                        type = type,
                        date = date,
                        source = cleanMerchantName(name),
                        categoryAmounts = emptyList(),
                        amount = amount,
                        isUserCategorized = false
                    )
                )
            } catch (e: Exception) {
                skippedRows++
                error = "Line $lineNumber: ${e.message}"
            }
        }
    } catch (e: Exception) {
        error = "Failed to read file: ${e.message}"
    }

    // If more than half the rows were skipped, escalate to an error
    if (error == null && totalDataRows > 0 && skippedRows > totalDataRows / 2) {
        error = "$skippedRows of $totalDataRows rows could not be parsed — file may not be a US Bank CSV"
    }

    return CsvParseResult(transactions, error, totalDataRows, skippedRows)
}

fun parseCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < line.length) {
        val ch = line[i]
        when {
            // Handle backslash-escaped quotes: \" inside quoted fields
            ch == '\\' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                current.append('"')
                i += 2
                continue
            }
            // Handle RFC 4180 doubled quotes: "" inside quoted fields → literal "
            ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                current.append('"')
                i += 2
                continue
            }
            ch == '"' -> inQuotes = !inQuotes
            ch == ',' && !inQuotes -> {
                fields.add(current.toString())
                current.clear()
            }
            else -> current.append(ch)
        }
        i++
    }
    fields.add(current.toString())
    return fields
}

private fun cleanMerchantName(raw: String): String {
    return raw.trim().replace(Regex("\\s+"), " ")
}

fun serializeTransactionsXlsx(
    transactions: List<Transaction>,
    categories: List<Category>,
    currencySymbol: String = "$"
): ByteArray {
    val catMap = categories.associateBy { it.id }
    val esc = { s: String -> s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;") }

    // Shared strings table — collect all unique strings
    val sharedStrings = mutableListOf<String>()
    val ssIndex = mutableMapOf<String, Int>()
    fun ssId(s: String): Int = ssIndex.getOrPut(s) { sharedStrings.size.also { sharedStrings.add(s) } }

    val headers = listOf("Date", "Type", "Source", "Description", "Amount", "Category")
    headers.forEach { ssId(it) }

    // Pre-build row data
    data class RowData(val dateSerial: Int, val typeIdx: Int, val sourceIdx: Int, val descIdx: Int, val amount: Double, val catIdx: Int)
    val rows = transactions.map { t ->
        val typeStr = if (t.type == TransactionType.EXPENSE) "Expense" else "Income"
        val catNames = t.categoryAmounts.mapNotNull { ca -> catMap[ca.categoryId]?.name }.joinToString(", ").ifEmpty { "" }
        // Excel date serial: days since 1899-12-30 (with the Lotus 1-2-3 leap year bug)
        val epoch = LocalDate.of(1899, 12, 30)
        val dateSerial = java.time.temporal.ChronoUnit.DAYS.between(epoch, t.date).toInt()
        RowData(dateSerial, ssId(typeStr), ssId(t.source), ssId(t.description), t.amount, ssId(catNames))
    }

    // Build sheet XML
    val sheet = StringBuilder()
    sheet.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    sheet.appendLine("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
    sheet.appendLine("<cols>")
    sheet.appendLine("  <col min=\"1\" max=\"1\" width=\"12\" bestFit=\"1\" customWidth=\"1\"/>")
    sheet.appendLine("  <col min=\"2\" max=\"2\" width=\"10\" bestFit=\"1\" customWidth=\"1\"/>")
    sheet.appendLine("  <col min=\"3\" max=\"3\" width=\"22\" bestFit=\"1\" customWidth=\"1\"/>")
    sheet.appendLine("  <col min=\"4\" max=\"4\" width=\"26\" bestFit=\"1\" customWidth=\"1\"/>")
    sheet.appendLine("  <col min=\"5\" max=\"5\" width=\"12\" bestFit=\"1\" customWidth=\"1\"/>")
    sheet.appendLine("  <col min=\"6\" max=\"6\" width=\"22\" bestFit=\"1\" customWidth=\"1\"/>")
    sheet.appendLine("</cols>")
    sheet.appendLine("<sheetData>")
    // Header row — style 1 (bold)
    sheet.append("<row r=\"1\">")
    for ((ci, h) in headers.withIndex()) {
        val col = ('A' + ci)
        sheet.append("<c r=\"${col}1\" t=\"s\" s=\"1\"><v>${ssId(h)}</v></c>")
    }
    sheet.appendLine("</row>")
    // Data rows
    for ((ri, rd) in rows.withIndex()) {
        val r = ri + 2
        sheet.append("<row r=\"$r\">")
        sheet.append("<c r=\"A$r\" s=\"2\"><v>${rd.dateSerial}</v></c>")  // date format
        sheet.append("<c r=\"B$r\" t=\"s\"><v>${rd.typeIdx}</v></c>")
        sheet.append("<c r=\"C$r\" t=\"s\"><v>${rd.sourceIdx}</v></c>")
        sheet.append("<c r=\"D$r\" t=\"s\"><v>${rd.descIdx}</v></c>")
        sheet.append("<c r=\"E$r\" s=\"3\"><v>${rd.amount}</v></c>")      // currency format
        sheet.append("<c r=\"F$r\" t=\"s\"><v>${rd.catIdx}</v></c>")
        sheet.appendLine("</row>")
    }
    sheet.appendLine("</sheetData>")
    sheet.appendLine("</worksheet>")

    // Styles XML
    val fmtId = 164  // custom format IDs start at 164
    val fmtIdMoney = 165
    val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="2">
  <numFmt numFmtId="$fmtId" formatCode="yyyy\-mm\-dd"/>
  <numFmt numFmtId="$fmtIdMoney" formatCode="${esc(currencySymbol)}#,##0.00"/>
</numFmts>
<fonts count="2">
  <font><sz val="11"/><name val="Calibri"/></font>
  <font><b/><sz val="11"/><name val="Calibri"/></font>
</fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="4">
  <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  <xf numFmtId="0" fontId="1" fillId="0" borderId="0" applyFont="1"/>
  <xf numFmtId="$fmtId" fontId="0" fillId="0" borderId="0" applyNumberFormat="1"/>
  <xf numFmtId="$fmtIdMoney" fontId="0" fillId="0" borderId="0" applyNumberFormat="1"/>
</cellXfs>
</styleSheet>"""

    // Shared strings XML
    val ss = StringBuilder()
    ss.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    ss.appendLine("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"${sharedStrings.size}\" uniqueCount=\"${sharedStrings.size}\">")
    for (s in sharedStrings) {
        ss.appendLine("<si><t>${esc(s)}</t></si>")
    }
    ss.appendLine("</sst>")

    val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Transactions" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>"""

    val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>"""

    // Pack into ZIP
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zip ->
        fun addEntry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        addEntry("[Content_Types].xml", contentTypes)
        addEntry("_rels/.rels", rootRels)
        addEntry("xl/workbook.xml", workbook)
        addEntry("xl/_rels/workbook.xml.rels", workbookRels)
        addEntry("xl/worksheets/sheet1.xml", sheet.toString())
        addEntry("xl/styles.xml", styles)
        addEntry("xl/sharedStrings.xml", ss.toString())
    }
    return baos.toByteArray()
}

fun serializeTransactionsCsv(transactions: List<Transaction>): String {
    val sb = StringBuilder()
    sb.appendLine("id,type,date,source,description,amount,categoryAmounts,isUserCategorized,isBudgetIncome,deviceId,deleted,linkedRecurringExpenseId,linkedAmortizationEntryId,linkedIncomeSourceId,excludeFromBudget,linkedRecurringExpenseAmount,linkedIncomeSourceAmount,amortizationAppliedAmount,linkedSavingsGoalId,linkedSavingsGoalAmount,receiptId1,receiptId2,receiptId3,receiptId4,receiptId5")
    for (t in transactions) {
        val categoryAmountsStr = t.categoryAmounts.joinToString(";") { "${it.categoryId}:${it.amount}" }
        val escapedSource = "\"${t.source.replace("\"", "\"\"")}\""
        val escapedDescription = "\"${t.description.replace("\"", "\"\"")}\""
        val escapedDeviceId = "\"${t.deviceId.replace("\"", "\"\"")}\""
        val linkedRecStr = t.linkedRecurringExpenseId?.toString() ?: ""
        val linkedAmortStr = t.linkedAmortizationEntryId?.toString() ?: ""
        val linkedIncomeStr = t.linkedIncomeSourceId?.toString() ?: ""
        val linkedSgStr = t.linkedSavingsGoalId?.toString() ?: ""
        val r1 = t.receiptId1?.let { "\"${it.replace("\"", "\"\"")}\"" } ?: ""
        val r2 = t.receiptId2?.let { "\"${it.replace("\"", "\"\"")}\"" } ?: ""
        val r3 = t.receiptId3?.let { "\"${it.replace("\"", "\"\"")}\"" } ?: ""
        val r4 = t.receiptId4?.let { "\"${it.replace("\"", "\"\"")}\"" } ?: ""
        val r5 = t.receiptId5?.let { "\"${it.replace("\"", "\"\"")}\"" } ?: ""
        sb.appendLine("${t.id},${t.type.name},${t.date},$escapedSource,$escapedDescription,${t.amount},\"$categoryAmountsStr\",${t.isUserCategorized},${t.isBudgetIncome},$escapedDeviceId,${t.deleted},$linkedRecStr,$linkedAmortStr,$linkedIncomeStr,${t.excludeFromBudget},${t.linkedRecurringExpenseAmount},${t.linkedIncomeSourceAmount},${t.amortizationAppliedAmount},$linkedSgStr,${t.linkedSavingsGoalAmount},$r1,$r2,$r3,$r4,$r5")
    }
    return sb.toString()
}

// ── Generic CSV Parser ──────────────────────────────────────────────

private data class ColumnMapping(
    val dateCol: Int,
    val amountCol: Int?,       // single amount column (null if split debit/credit)
    val debitCol: Int?,        // split debit column
    val creditCol: Int?,       // split credit column
    val merchantCol: Int,
    val typeCol: Int?,         // optional DEBIT/CREDIT type column
    val dateFormatter: DateTimeFormatter
)

// Lightweight intermediate — avoids generating transaction IDs until we pick the winning mapping
private data class ParsedRow(
    val date: LocalDate,
    val amount: Double,
    val type: TransactionType,
    val merchant: String
)

private data class MappingAttempt(
    val rows: List<ParsedRow>,
    val totalDataRows: Int,
    val skippedRows: Int
)

fun parseGenericCsv(reader: BufferedReader, existingIds: Set<Int>): CsvParseResult {
    try {
        // Read lines with quote-aware joining: if a line has an unclosed quote,
        // the actual newline is embedded in a field — join with the next line(s).
        val rawLines = mutableListOf<String>()
        val allFileLines = reader.readLines().take(50_000)
        var pending: StringBuilder? = null
        for (fileLine in allFileLines) {
            if (pending != null) {
                pending.append('\n').append(fileLine)
                if (pending.count { it == '"' } % 2 == 0) {
                    // Quotes balanced — record the joined line
                    rawLines.add(pending.toString())
                    pending = null
                }
            } else {
                if (fileLine.count { it == '"' } % 2 != 0) {
                    // Odd number of quotes — newline is inside a quoted field
                    pending = StringBuilder(fileLine)
                } else {
                    rawLines.add(fileLine)
                }
            }
        }
        // If there's still an unclosed quote, add whatever we have
        if (pending != null) rawLines.add(pending.toString())

        if (rawLines.isEmpty()) return CsvParseResult(emptyList(), "Empty file")

        // Strip BOM
        val lines = rawLines.toMutableList()
        if (lines[0].startsWith("\uFEFF")) lines[0] = lines[0].removePrefix("\uFEFF")

        // Detect delimiter (comma vs semicolon vs tab)
        val delimiter = detectDelimiter(lines.take(10))

        // Parse all lines into fields
        val allRows = lines.filter { it.isNotBlank() }.map { parseCsvLineWithDelimiter(it, delimiter) }
        if (allRows.size < 2) return CsvParseResult(emptyList(), "File has fewer than 2 rows")

        // Detect header
        val hasHeader = isHeaderRow(allRows[0])
        val header = if (hasHeader) allRows[0] else null
        val dataRows = if (hasHeader) allRows.drop(1) else allRows

        if (dataRows.isEmpty()) return CsvParseResult(emptyList(), "No data rows found")

        // Generate up to 3 candidate column mappings, ranked by score
        val candidates = detectColumnCandidates(dataRows, header)
        if (candidates.isEmpty()) {
            return CsvParseResult(
                emptyList(),
                "Could not detect CSV structure. Need at least a date and amount column.",
                dataRows.size, dataRows.size
            )
        }

        // Try each candidate mapping — accept the first one that parses consistently
        var bestAttempt: MappingAttempt? = null

        for (mapping in candidates) {
            val attempt = tryParseWithMapping(dataRows, mapping)
            if (attempt.rows.isEmpty()) continue

            val isConsistent = attempt.skippedRows <= 2 ||
                (attempt.totalDataRows > 0 &&
                 (attempt.totalDataRows - attempt.skippedRows).toDouble() / attempt.totalDataRows >= 0.90)

            if (isConsistent) {
                bestAttempt = attempt
                break  // Good enough — use this mapping
            }

            // Track best attempt so far (fewest skips)
            if (bestAttempt == null || attempt.skippedRows < bestAttempt.skippedRows) {
                bestAttempt = attempt
            }
        }

        // No candidate produced any rows at all
        if (bestAttempt == null || bestAttempt.rows.isEmpty()) {
            return CsvParseResult(
                emptyList(),
                "Could not parse any transactions. The file format may not be compatible.",
                dataRows.size, dataRows.size
            )
        }

        // Check if the best attempt is consistent enough to trust
        val isAcceptable = bestAttempt.skippedRows <= 2 ||
            (bestAttempt.totalDataRows > 0 &&
             (bestAttempt.totalDataRows - bestAttempt.skippedRows).toDouble() / bestAttempt.totalDataRows >= 0.90)

        if (!isAcceptable) {
            val best = bestAttempt.totalDataRows - bestAttempt.skippedRows
            return CsvParseResult(
                emptyList(),
                "File format could not be reliably determined. " +
                    "Tried ${candidates.size} column arrangement${if (candidates.size > 1) "s" else ""} " +
                    "but none could consistently parse the data " +
                    "(best result: $best of ${bestAttempt.totalDataRows} rows). " +
                    "The file may use an unusual format.",
                bestAttempt.totalDataRows, bestAttempt.skippedRows
            )
        }

        // Convert ParsedRows to Transactions with generated IDs
        val usedIds = existingIds.toMutableSet()
        val transactions = bestAttempt.rows.map { row ->
            val id = generateTransactionId(usedIds)
            usedIds.add(id)
            Transaction(
                id = id,
                type = row.type,
                date = row.date,
                source = row.merchant,
                categoryAmounts = emptyList(),
                amount = row.amount,
                isUserCategorized = false
            )
        }

        // Minor skip info (1-2 footer rows) — note but don't block
        val error = if (bestAttempt.skippedRows > 0) {
            "${bestAttempt.skippedRows} of ${bestAttempt.totalDataRows} rows could not be parsed and were skipped"
        } else null

        return CsvParseResult(transactions, error, bestAttempt.totalDataRows, bestAttempt.skippedRows)

    } catch (e: Exception) {
        return CsvParseResult(emptyList(), "Failed to read file: ${e.message}")
    }
}

/** Try parsing all data rows with a given column mapping. Returns lightweight ParsedRows (no IDs). */
private fun tryParseWithMapping(
    dataRows: List<List<String>>,
    mapping: ColumnMapping
): MappingAttempt {
    val parsed = mutableListOf<ParsedRow>()
    var total = 0
    var skipped = 0

    for (row in dataRows) {
        if (row.all { it.isBlank() }) continue  // blank rows don't count toward total
        total++

        try {
            val dateStr = row.getOrNull(mapping.dateCol)?.trim()
            if (dateStr.isNullOrBlank()) { skipped++; continue }
            val date = parseDateFlexible(dateStr, mapping.dateFormatter)
            if (date == null) { skipped++; continue }
            if (date.year < 2000 || date.year > LocalDate.now().year + 1) { skipped++; continue }

            val amount: Double
            val isDebit: Boolean?
            if (mapping.amountCol != null) {
                val raw = row.getOrNull(mapping.amountCol)?.trim()
                if (raw.isNullOrBlank()) { skipped++; continue }
                val p = parseAmount(raw)
                if (p == null) { skipped++; continue }
                amount = p
                isDebit = null
            } else {
                val debitRaw = row.getOrNull(mapping.debitCol!!)?.trim() ?: ""
                val creditRaw = row.getOrNull(mapping.creditCol!!)?.trim() ?: ""
                val debitAmt = parseAmount(debitRaw)
                val creditAmt = parseAmount(creditRaw)
                when {
                    debitAmt != null && (creditAmt == null || creditAmt == 0.0) -> {
                        amount = abs(debitAmt); isDebit = true
                    }
                    creditAmt != null && (debitAmt == null || debitAmt == 0.0) -> {
                        amount = abs(creditAmt); isDebit = false
                    }
                    else -> { skipped++; continue }
                }
            }

            if (amount == 0.0) { skipped++; continue }

            val typeCell = mapping.typeCol?.let { row.getOrNull(it)?.trim() }
            val type = inferTransactionType(amount, typeCell, isDebit)

            val merchantRaw = row.getOrNull(mapping.merchantCol)?.trim() ?: ""
            val merchant = cleanMerchantName(merchantRaw)
            if (merchant.isBlank()) { skipped++; continue }

            parsed.add(ParsedRow(date, abs(amount), type, merchant))
        } catch (_: Exception) {
            skipped++
        }
    }

    return MappingAttempt(parsed, total, skipped)
}

private fun parseCsvLineWithDelimiter(line: String, delimiter: Char): List<String> {
    if (delimiter == ',') return parseCsvLine(line)
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            // RFC 4180 doubled quotes: "" → literal "
            ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                current.append('"')
                i += 2
                continue
            }
            ch == '"' -> inQuotes = !inQuotes
            ch == delimiter && !inQuotes -> { fields.add(current.toString()); current.clear() }
            else -> current.append(ch)
        }
        i++
    }
    fields.add(current.toString())
    return fields
}

private fun detectDelimiter(firstLines: List<String>): Char {
    if (firstLines.isEmpty()) return ','
    var commaCount = 0
    var semiCount = 0
    var tabCount = 0
    for (line in firstLines) {
        var inQuotes = false
        for (ch in line) {
            if (ch == '"') inQuotes = !inQuotes
            else if (!inQuotes) when (ch) {
                ',' -> commaCount++
                ';' -> semiCount++
                '\t' -> tabCount++
            }
        }
    }
    val perLine = firstLines.size.coerceAtLeast(1)
    return when {
        semiCount / perLine > commaCount / perLine && semiCount / perLine >= 2 -> ';'
        tabCount / perLine > commaCount / perLine && tabCount / perLine >= 2 -> '\t'
        else -> ','
    }
}

private val HEADER_KEYWORDS = setOf(
    "date", "transaction", "amount", "description", "name", "memo", "type",
    "debit", "credit", "balance", "category", "merchant", "payee", "reference",
    "check", "posted", "details", "particulars", "narration", "value"
)

private fun isHeaderRow(row: List<String>): Boolean {
    val matches = row.count { cell ->
        val lower = cell.trim().lowercase().replace(Regex("[^a-z]"), "")
        lower in HEADER_KEYWORDS || HEADER_KEYWORDS.any { lower.contains(it) }
    }
    return matches >= 2
}

/**
 * Generate up to 3 candidate column mappings by varying date and amount column choices.
 * Banks produce consistent CSVs, so if the top-scoring mapping doesn't parse cleanly,
 * alternatives let us recover (e.g., balance column mis-scored as amount, two date columns).
 */
private fun detectColumnCandidates(dataRows: List<List<String>>, header: List<String>?): List<ColumnMapping> {
    val numCols = dataRows.maxOf { it.size }
    if (numCols < 2) return emptyList()

    // Extract column data (padded to uniform width)
    val columns = (0 until numCols).map { col ->
        dataRows.map { row -> row.getOrNull(col)?.trim() ?: "" }
    }

    // Score each column (mutable for header boosting)
    val dScores = columns.map { scoreDate(it) }.toMutableList()
    val aScores = columns.map { scoreAmount(it) }.toMutableList()
    val tScores = columns.map { scoreTransactionType(it) }.toMutableList()
    val mScores = columns.map { scoreMerchant(it) }.toMutableList()

    // Boost from header keywords
    val headerLower = header?.map { it.trim().lowercase() }
    if (headerLower != null) {
        for (i in 0 until minOf(numCols, headerLower.size)) {
            val h = headerLower[i]
            if (h.contains("date") || h.contains("posted") || h.contains("value")) dScores[i] = dScores[i] + 0.15
            if (h.contains("amount") || h.contains("total") || h.contains("sum")) aScores[i] = aScores[i] + 0.15
            if (h.contains("debit") || h.contains("withdrawal") || h.contains("payment")) aScores[i] = aScores[i] + 0.1
            if (h.contains("credit") || h.contains("deposit")) aScores[i] = aScores[i] + 0.1
            if (h.contains("type") || h.contains("transaction")) tScores[i] = tScores[i] + 0.15
            if (h.contains("name") || h.contains("description") || h.contains("merchant") ||
                h.contains("payee") || h.contains("detail") ||
                h.contains("narration") || h.contains("particular")) mScores[i] = mScores[i] + 0.15
            // Memo/reference columns often contain codes, not merchant names — slight penalty
            if (h.contains("memo") || h.contains("reference") || h.contains("ref")) mScores[i] = mScores[i] * 0.5
        }
    }

    // Demote balance columns (monotonically increasing or decreasing amounts)
    for (i in 0 until numCols) {
        if (aScores[i] > 0.5 && isLikelyBalanceColumn(columns[i])) {
            aScores[i] = aScores[i] * 0.3
        }
    }

    // Viable date columns (score ≥ 0.4), best first
    val viableDateCols = dScores.indices
        .filter { dScores[it] >= 0.4 }
        .sortedByDescending { dScores[it] }
    if (viableDateCols.isEmpty()) return emptyList()

    val candidates = mutableListOf<ColumnMapping>()

    // Try up to 2 date column choices × up to 2 amount arrangements = up to 4 combos, capped at 3
    for (dateCol in viableDateCols.take(2)) {
        val dateFormatter = detectDateFormat(columns[dateCol]) ?: continue

        // Viable amount columns excluding date column
        val amountCandidates = aScores.indices
            .filter { it != dateCol && aScores[it] > 0.4 }
            .sortedByDescending { aScores[it] }
        if (amountCandidates.isEmpty()) continue

        // Build amount configurations to try: (amountCol, debitCol, creditCol)
        val amountConfigs = mutableListOf<Triple<Int?, Int?, Int?>>()

        if (amountCandidates.size >= 2) {
            val c1 = amountCandidates[0]
            val c2 = amountCandidates[1]
            if (areSplitDebitCredit(columns[c1], columns[c2])) {
                // Determine which is debit vs credit
                val h1 = headerLower?.getOrNull(c1) ?: ""
                val h2 = headerLower?.getOrNull(c2) ?: ""
                val (debit, credit) = when {
                    h1.contains("credit") || h2.contains("debit") -> c2 to c1
                    h1.contains("debit") || h2.contains("credit") -> c1 to c2
                    else -> {
                        val count1 = columns[c1].count { parseAmount(it) != null && parseAmount(it) != 0.0 }
                        val count2 = columns[c2].count { parseAmount(it) != null && parseAmount(it) != 0.0 }
                        if (count1 >= count2) c1 to c2 else c2 to c1
                    }
                }
                amountConfigs.add(Triple(null, debit, credit))
                // Also try single-column as alternative
                amountConfigs.add(Triple(amountCandidates[0], null, null))
            } else {
                amountConfigs.add(Triple(amountCandidates[0], null, null))
                // Try 2nd best amount as alternative
                amountConfigs.add(Triple(amountCandidates[1], null, null))
            }
        } else {
            amountConfigs.add(Triple(amountCandidates[0], null, null))
        }

        for ((amountCol, debitCol, creditCol) in amountConfigs) {
            val usedCols = setOfNotNull(dateCol, amountCol, debitCol, creditCol)

            val typeCol = tScores.indices
                .filter { it !in usedCols && tScores[it] > 0.5 }
                .maxByOrNull { tScores[it] }

            val usedCols2 = usedCols + setOfNotNull(typeCol)

            val merchantCol = mScores.indices
                .filter { it !in usedCols2 && mScores[it] > 0.0 }
                .maxByOrNull { mScores[it] }
                ?: mScores.indices.filter { it !in usedCols2 }.firstOrNull()
                ?: continue

            val mapping = ColumnMapping(dateCol, amountCol, debitCol, creditCol, merchantCol, typeCol, dateFormatter)
            if (mapping !in candidates) {
                candidates.add(mapping)
            }
            if (candidates.size >= 3) return candidates
        }
    }

    return candidates
}

private fun scoreDate(column: List<String>): Double {
    if (column.isEmpty()) return 0.0
    val nonBlank = column.filter { it.isNotBlank() }
    if (nonBlank.isEmpty()) return 0.0
    val parsed = nonBlank.count { detectDateFormat(listOf(it)) != null }
    return parsed.toDouble() / nonBlank.size
}

private fun scoreAmount(column: List<String>): Double {
    if (column.isEmpty()) return 0.0
    val nonBlank = column.filter { it.isNotBlank() }
    if (nonBlank.isEmpty()) return 0.0
    val parsed = nonBlank.count { parseAmount(it) != null }
    return parsed.toDouble() / nonBlank.size
}

private fun scoreTransactionType(column: List<String>): Double {
    val typeWords = setOf(
        "debit", "credit", "purchase", "payment", "deposit", "withdrawal",
        "dr", "cr", "d", "c", "pos", "ach", "dbt", "cdt", "transfer"
    )
    if (column.isEmpty()) return 0.0
    val nonBlank = column.filter { it.isNotBlank() }
    if (nonBlank.isEmpty()) return 0.0
    val matched = nonBlank.count { cell ->
        val lower = cell.trim().lowercase()
        lower in typeWords || typeWords.any { lower.contains(it) }
    }
    val score = matched.toDouble() / nonBlank.size
    // Penalize if values are too long (likely descriptions, not type codes)
    val avgLen = nonBlank.map { it.trim().length }.average()
    return if (avgLen > 15) score * 0.3 else score
}

private fun scoreMerchant(column: List<String>): Double {
    if (column.isEmpty()) return 0.0
    val nonBlank = column.filter { it.isNotBlank() }
    if (nonBlank.isEmpty()) return 0.0

    // Not a merchant if mostly numeric
    val numericCount = nonBlank.count { parseAmount(it) != null }
    if (numericCount.toDouble() / nonBlank.size > 0.7) return 0.0

    // Not a merchant if mostly dates
    val dateCount = nonBlank.count { detectDateFormat(listOf(it)) != null }
    if (dateCount.toDouble() / nonBlank.size > 0.7) return 0.0

    val avgLen = nonBlank.map { it.trim().length }.average()
    val uniqueRatio = nonBlank.toSet().size.toDouble() / nonBlank.size

    // Merchant names are human-readable text — penalize columns dominated by
    // digits, semicolons, or other non-letter characters (reference/memo columns)
    val avgAlphaRatio = nonBlank.map { cell ->
        val letters = cell.count { it.isLetter() || it == ' ' }
        if (cell.isNotEmpty()) letters.toDouble() / cell.length else 0.0
    }.average()

    // Prefer columns with readable text, moderate length, diverse values
    val lengthScore = (avgLen / 30.0).coerceAtMost(1.0)
    val diversityScore = uniqueRatio.coerceAtMost(1.0)
    val readabilityScore = avgAlphaRatio  // 0.0 = all digits/symbols, 1.0 = all letters
    return (lengthScore * 0.3 + diversityScore * 0.3 + readabilityScore * 0.4)
}

private fun isLikelyBalanceColumn(column: List<String>): Boolean {
    val values = column.mapNotNull { parseAmount(it) }
    if (values.size < 3) return false
    // Check if values are mostly non-negative (balances typically are)
    val nonNeg = values.count { it >= 0 }
    if (nonNeg.toDouble() / values.size < 0.9) return false
    // Check monotonicity — balance columns trend in one direction
    var increases = 0
    var decreases = 0
    for (i in 1 until values.size) {
        if (values[i] > values[i - 1]) increases++
        else if (values[i] < values[i - 1]) decreases++
    }
    val total = (increases + decreases).coerceAtLeast(1)
    val dominantDirection = maxOf(increases, decreases).toDouble() / total
    return dominantDirection > 0.75
}

private fun areSplitDebitCredit(col1: List<String>, col2: List<String>): Boolean {
    if (col1.size != col2.size) return false
    var mutuallyExclusive = 0
    var total = 0
    for (i in col1.indices) {
        val has1 = col1[i].isNotBlank() && parseAmount(col1[i]) != null && parseAmount(col1[i]) != 0.0
        val has2 = col2[i].isNotBlank() && parseAmount(col2[i]) != null && parseAmount(col2[i]) != 0.0
        if (has1 || has2) {
            total++
            if (has1 != has2) mutuallyExclusive++
        }
    }
    return total > 0 && mutuallyExclusive.toDouble() / total > 0.75
}

private val DATE_FORMATS = listOf(
    "yyyy-MM-dd" to DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    "MM/dd/yyyy" to DateTimeFormatter.ofPattern("MM/dd/yyyy"),
    "M/d/yyyy" to DateTimeFormatter.ofPattern("M/d/yyyy"),
    "dd/MM/yyyy" to DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    "MM-dd-yyyy" to DateTimeFormatter.ofPattern("MM-dd-yyyy"),
    "dd-MM-yyyy" to DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    "yyyy/MM/dd" to DateTimeFormatter.ofPattern("yyyy/MM/dd"),
    "MM/dd/yy" to DateTimeFormatter.ofPattern("MM/dd/yy"),
    "M/d/yy" to DateTimeFormatter.ofPattern("M/d/yy"),
    "dd/MM/yy" to DateTimeFormatter.ofPattern("dd/MM/yy")
)

private fun detectDateFormat(samples: List<String>): DateTimeFormatter? {
    val nonBlank = samples.filter { it.isNotBlank() }.map { it.trim().split(" ")[0].split("T")[0] }
    if (nonBlank.isEmpty()) return null

    for ((_, fmt) in DATE_FORMATS) {
        val successCount = nonBlank.count { s ->
            try {
                LocalDate.parse(s, fmt)
                true
            } catch (_: Exception) { false }
        }
        if (successCount.toDouble() / nonBlank.size > 0.8) return fmt
    }
    return null
}

private fun parseDateFlexible(raw: String, primaryFormatter: DateTimeFormatter): LocalDate? {
    val cleaned = raw.trim().split(" ")[0].split("T")[0]
    return try {
        LocalDate.parse(cleaned, primaryFormatter)
    } catch (_: Exception) {
        // Fallback: try all formats
        for ((_, fmt) in DATE_FORMATS) {
            try { return LocalDate.parse(cleaned, fmt) } catch (_: Exception) { }
        }
        null
    }
}

private fun parseAmount(raw: String): Double? {
    var s = raw.trim()
    if (s.isBlank()) return null

    // Remove currency symbols
    s = s.replace(Regex("[$€£¥₹]"), "").trim()

    // Parenthesized negatives: (123.45) -> -123.45
    if (s.startsWith("(") && s.endsWith(")")) {
        s = "-" + s.substring(1, s.length - 1)
    }

    // Remove thousands separators (commas in 1,234.56)
    s = s.replace(",", "")

    return s.toDoubleOrNull()
}

private fun inferTransactionType(amount: Double, typeCell: String?, isDebit: Boolean?): TransactionType {
    // If split debit/credit columns, the column tells us
    if (isDebit != null) return if (isDebit) TransactionType.EXPENSE else TransactionType.INCOME

    // If explicit type column
    if (typeCell != null) {
        val upper = typeCell.trim().uppercase()
        val creditWords = setOf("CREDIT", "CR", "C", "DEPOSIT", "DEP")
        val debitWords = setOf("DEBIT", "DR", "D", "PURCHASE", "POS", "WITHDRAWAL", "PAYMENT", "ACH", "DBT")
        if (creditWords.any { upper.contains(it) }) return TransactionType.INCOME
        if (debitWords.any { upper.contains(it) }) return TransactionType.EXPENSE
    }

    // Fall back to amount sign
    return if (amount < 0) TransactionType.EXPENSE else TransactionType.INCOME
}

fun parseSyncBudgetCsv(reader: BufferedReader, existingIds: Set<Int>): CsvParseResult {
    val transactions = mutableListOf<Transaction>()
    val usedIds = existingIds.toMutableSet()
    var error: String? = null

    try {
        val header = reader.readLine() ?: return CsvParseResult(emptyList(), "Empty file")
        if (!header.startsWith("id,type,date,")) {
            return CsvParseResult(emptyList(), "Not a BudgeTrak CSV file (invalid header)")
        }

        var lineNumber = 1
        reader.forEachLine { line ->
            lineNumber++
            if (line.isBlank()) return@forEachLine
            try {
                val fields = parseCsvLine(line)
                if (fields.size < 7) {
                    error = "Line $lineNumber: expected 7 fields, got ${fields.size}"
                    return@forEachLine
                }

                val originalId = fields[0].trim().toInt()
                val type = TransactionType.valueOf(fields[1].trim())
                val date = LocalDate.parse(fields[2].trim())
                val source = fields[3].trim()

                // Detect old format (no description column) vs new format
                // Old header: id,type,date,source,amount,...  (field[4] is amount, numeric)
                // New header: id,type,date,source,description,amount,...  (field[4] is description, string)
                val hasDescription = fields.size > 4 && fields[4].trim().toDoubleOrNull() == null
                val description = if (hasDescription) fields[4].trim() else ""
                val offset = if (hasDescription) 1 else 0

                val amount = fields[4 + offset].trim().toDouble()
                val categoryAmountsStr = fields[5 + offset].trim()
                val isUserCategorized = fields[6 + offset].trim().toBoolean()

                val categoryAmounts = if (categoryAmountsStr.isNotEmpty()) {
                    categoryAmountsStr.split(";").mapNotNull { pair ->
                        val parts = pair.split(":")
                        if (parts.size == 2) {
                            CategoryAmount(
                                categoryId = parts[0].toInt(),
                                amount = parts[1].toDouble()
                            )
                        } else null
                    }
                } else emptyList()

                val id = if (originalId !in usedIds) originalId
                         else generateTransactionId(usedIds)
                usedIds.add(id)

                // Parse optional sync metadata (backward compatible with old and new formats)
                val isBudgetIncome = if (fields.size > 7 + offset) fields[7 + offset].trim().toBooleanStrictOrNull() ?: false else false
                val deviceId = if (fields.size > 8 + offset) fields[8 + offset].trim() else ""
                val deleted = if (fields.size > 9 + offset) fields[9 + offset].trim().toBooleanStrictOrNull() ?: false else false
                // Skip old clock columns (10-17) if present, read linking fields after them
                // Old format had clocks at columns 10+, new format has linking fields at 10+
                // Detect by checking if column 10 looks like a clock (numeric Long) or linking field
                val col10 = if (fields.size > 10 + offset) fields[10 + offset].trim() else ""
                val oldFormat = col10.toLongOrNull() != null && col10.toIntOrNull()?.let { it > 65535 } == true
                val linkBase = if (oldFormat) {
                    // Old format: skip clock columns, linking starts after clocks
                    val clockOffset = if (hasDescription) 1 else 0
                    18 + offset + clockOffset
                } else {
                    // New format: linking fields start right after deleted
                    10 + offset
                }
                val linkedRecurringExpenseId = if (fields.size > linkBase) fields[linkBase].trim().toIntOrNull() else null
                val linkedAmortizationEntryId = if (fields.size > linkBase + 1) fields[linkBase + 1].trim().toIntOrNull() else null
                val linkedIncomeSourceId = if (fields.size > linkBase + 2) fields[linkBase + 2].trim().toIntOrNull() else null
                val excludeFromBudget = if (fields.size > linkBase + 3) fields[linkBase + 3].trim().toBooleanStrictOrNull() ?: false else false
                val linkedRecurringExpenseAmount = if (fields.size > linkBase + 4) fields[linkBase + 4].trim().toDoubleOrNull() ?: 0.0 else 0.0
                val linkedIncomeSourceAmount = if (fields.size > linkBase + 5) fields[linkBase + 5].trim().toDoubleOrNull() ?: 0.0 else 0.0
                val amortizationAppliedAmount = if (fields.size > linkBase + 6) fields[linkBase + 6].trim().toDoubleOrNull() ?: 0.0 else 0.0
                val linkedSavingsGoalId = if (fields.size > linkBase + 7) fields[linkBase + 7].trim().toIntOrNull() else null
                val linkedSavingsGoalAmount = if (fields.size > linkBase + 8) fields[linkBase + 8].trim().toDoubleOrNull() ?: 0.0 else 0.0
                val receiptId1 = if (fields.size > linkBase + 9) fields[linkBase + 9].trim().ifEmpty { null } else null
                val receiptId2 = if (fields.size > linkBase + 10) fields[linkBase + 10].trim().ifEmpty { null } else null
                val receiptId3 = if (fields.size > linkBase + 11) fields[linkBase + 11].trim().ifEmpty { null } else null
                val receiptId4 = if (fields.size > linkBase + 12) fields[linkBase + 12].trim().ifEmpty { null } else null
                val receiptId5 = if (fields.size > linkBase + 13) fields[linkBase + 13].trim().ifEmpty { null } else null

                transactions.add(
                    Transaction(
                        id = id,
                        type = type,
                        date = date,
                        source = source,
                        description = description,
                        categoryAmounts = categoryAmounts,
                        amount = amount,
                        isUserCategorized = isUserCategorized,
                        isBudgetIncome = isBudgetIncome,
                        excludeFromBudget = excludeFromBudget,
                        linkedRecurringExpenseId = linkedRecurringExpenseId,
                        linkedAmortizationEntryId = linkedAmortizationEntryId,
                        linkedIncomeSourceId = linkedIncomeSourceId,
                        linkedRecurringExpenseAmount = linkedRecurringExpenseAmount,
                        linkedIncomeSourceAmount = linkedIncomeSourceAmount,
                        amortizationAppliedAmount = amortizationAppliedAmount,
                        linkedSavingsGoalId = linkedSavingsGoalId,
                        linkedSavingsGoalAmount = linkedSavingsGoalAmount,
                        receiptId1 = receiptId1,
                        receiptId2 = receiptId2,
                        receiptId3 = receiptId3,
                        receiptId4 = receiptId4,
                        receiptId5 = receiptId5,
                        deviceId = deviceId,
                        deleted = deleted
                    )
                )
            } catch (e: Exception) {
                error = "Line $lineNumber: ${e.message}"
            }
        }
    } catch (e: Exception) {
        error = "Failed to read file: ${e.message}"
    }

    return CsvParseResult(transactions, error)
}
