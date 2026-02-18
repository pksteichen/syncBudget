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
    sb.appendLine("id,type,date,source,amount,categoryAmounts,isUserCategorized")
    for (t in transactions) {
        val categoryAmountsStr = t.categoryAmounts.joinToString(";") { "${it.categoryId}:${it.amount}" }
        val escapedSource = "\"${t.source.replace("\"", "\"\"")}\""
        sb.appendLine("${t.id},${t.type.name},${t.date},$escapedSource,${t.amount},\"$categoryAmountsStr\",${t.isUserCategorized}")
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
                val amount = fields[4].trim().toDouble()
                val categoryAmountsStr = fields[5].trim()
                val isUserCategorized = fields[6].trim().toBoolean()

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

                transactions.add(
                    Transaction(
                        id = id,
                        type = type,
                        date = date,
                        source = source,
                        categoryAmounts = categoryAmounts,
                        amount = amount,
                        isUserCategorized = isUserCategorized
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
