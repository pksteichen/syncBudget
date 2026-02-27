package com.syncbudget.app.data

import java.io.BufferedReader
import java.time.LocalDate
import kotlin.math.abs

enum class BankFormat(val displayName: String) {
    US_BANK("US Bank"),
    SECURESYNC_CSV("SecureSync CSV Save File"),
    SECURESYNC_ENCRYPTED("SecureSync Encrypted Save File")
}

data class CsvParseResult(
    val transactions: List<Transaction>,
    val error: String?
)

fun parseUsBank(reader: BufferedReader, existingIds: Set<Int>): CsvParseResult {
    val transactions = mutableListOf<Transaction>()
    val usedIds = existingIds.toMutableSet()
    var error: String? = null

    try {
        val header = reader.readLine() ?: return CsvParseResult(emptyList(), "Empty file")

        var lineNumber = 1
        reader.forEachLine { line ->
            lineNumber++
            if (line.isBlank()) return@forEachLine
            try {
                val fields = parseCsvLine(line)
                if (fields.size < 5) {
                    error = "Line $lineNumber: expected 5 fields, got ${fields.size}"
                    return@forEachLine
                }

                val date = LocalDate.parse(fields[0].trim())
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
                error = "Line $lineNumber: ${e.message}"
            }
        }
    } catch (e: Exception) {
        error = "Failed to read file: ${e.message}"
    }

    return CsvParseResult(transactions, error)
}

fun parseCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false

    for (ch in line) {
        when {
            ch == '"' -> inQuotes = !inQuotes
            ch == ',' && !inQuotes -> {
                fields.add(current.toString())
                current.clear()
            }
            else -> current.append(ch)
        }
    }
    fields.add(current.toString())
    return fields
}

private fun cleanMerchantName(raw: String): String {
    return raw.trim().replace(Regex("\\s+"), " ")
}

fun serializeTransactionsCsv(transactions: List<Transaction>): String {
    val sb = StringBuilder()
    sb.appendLine("id,type,date,source,description,amount,categoryAmounts,isUserCategorized,isBudgetIncome,deviceId,deleted,source_clock,description_clock,amount_clock,date_clock,type_clock,categoryAmounts_clock,isUserCategorized_clock,isBudgetIncome_clock,deleted_clock")
    for (t in transactions) {
        val categoryAmountsStr = t.categoryAmounts.joinToString(";") { "${it.categoryId}:${it.amount}" }
        val escapedSource = "\"${t.source.replace("\"", "\"\"")}\""
        val escapedDescription = "\"${t.description.replace("\"", "\"\"")}\""
        val escapedDeviceId = "\"${t.deviceId.replace("\"", "\"\"")}\""
        sb.appendLine("${t.id},${t.type.name},${t.date},$escapedSource,$escapedDescription,${t.amount},\"$categoryAmountsStr\",${t.isUserCategorized},${t.isBudgetIncome},$escapedDeviceId,${t.deleted},${t.source_clock},${t.description_clock},${t.amount_clock},${t.date_clock},${t.type_clock},${t.categoryAmounts_clock},${t.isUserCategorized_clock},${t.isBudgetIncome_clock},${t.deleted_clock}")
    }
    return sb.toString()
}

fun parseSyncBudgetCsv(reader: BufferedReader, existingIds: Set<Int>): CsvParseResult {
    val transactions = mutableListOf<Transaction>()
    val usedIds = existingIds.toMutableSet()
    var error: String? = null

    try {
        val header = reader.readLine() ?: return CsvParseResult(emptyList(), "Empty file")
        if (!header.startsWith("id,type,date,")) {
            return CsvParseResult(emptyList(), "Not a SecureSync CSV file (invalid header)")
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
                val sourceClock = if (fields.size > 10 + offset) fields[10 + offset].trim().toLongOrNull() ?: 0L else 0L
                val descriptionClock = if (hasDescription && fields.size > 11 + offset) fields[11 + offset].trim().toLongOrNull() ?: 0L else 0L
                val clockOffset = if (hasDescription) 1 else 0
                val amountClock = if (fields.size > 11 + offset + clockOffset) fields[11 + offset + clockOffset].trim().toLongOrNull() ?: 0L else 0L
                val dateClock = if (fields.size > 12 + offset + clockOffset) fields[12 + offset + clockOffset].trim().toLongOrNull() ?: 0L else 0L
                val typeClock = if (fields.size > 13 + offset + clockOffset) fields[13 + offset + clockOffset].trim().toLongOrNull() ?: 0L else 0L
                val catAmountsClock = if (fields.size > 14 + offset + clockOffset) fields[14 + offset + clockOffset].trim().toLongOrNull() ?: 0L else 0L
                val isUserCatClock = if (fields.size > 15 + offset + clockOffset) fields[15 + offset + clockOffset].trim().toLongOrNull() ?: 0L else 0L
                val isBudgetIncomeClock = if (fields.size > 16 + offset + clockOffset) fields[16 + offset + clockOffset].trim().toLongOrNull() ?: 0L else 0L
                val deletedClock = if (fields.size > 17 + offset + clockOffset) fields[17 + offset + clockOffset].trim().toLongOrNull() ?: 0L else 0L

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
                        deviceId = deviceId,
                        deleted = deleted,
                        source_clock = sourceClock,
                        description_clock = descriptionClock,
                        amount_clock = amountClock,
                        date_clock = dateClock,
                        type_clock = typeClock,
                        categoryAmounts_clock = catAmountsClock,
                        isUserCategorized_clock = isUserCatClock,
                        isBudgetIncome_clock = isBudgetIncomeClock,
                        deleted_clock = deletedClock
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
