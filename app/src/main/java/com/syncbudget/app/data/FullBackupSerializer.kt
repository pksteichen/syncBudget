package com.syncbudget.app.data

import android.content.Context
import com.syncbudget.app.data.sync.PeriodLedgerRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

object FullBackupSerializer {

    fun serialize(context: Context): String {
        val json = JSONObject()
        json.put("type", "syncbudget_full_backup")
        json.put("version", 1)
        json.put("savedAt", LocalDateTime.now().toString())

        // Read each repo file and embed its raw JSON
        fun readFileArray(fileName: String): JSONArray? {
            val file = context.getFileStreamPath(fileName)
            if (!file.exists()) return null
            val text = file.readText()
            if (text.isBlank()) return null
            return try { JSONArray(text) } catch (_: Exception) { null }
        }

        readFileArray("transactions.json")?.let { json.put("transactions", it) }
        readFileArray("categories.json")?.let { json.put("categories", it) }
        readFileArray("recurring_expenses.json")?.let { json.put("recurringExpenses", it) }
        readFileArray("income_sources.json")?.let { json.put("incomeSources", it) }
        readFileArray("amortization_entries.json")?.let { json.put("amortizationEntries", it) }
        readFileArray("future_expenditures.json")?.let { json.put("savingsGoals", it) }
        readFileArray("period_ledger.json")?.let { json.put("periodLedger", it) }

        // Shared settings
        val sharedSettings = SharedSettingsRepository.load(context)
        json.put("sharedSettings", SharedSettingsRepository.toJson(sharedSettings))

        // Local prefs
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val localPrefs = JSONObject()
        localPrefs.put("availableCash", prefs.getFloat("availableCash", 0f).toDouble())
        localPrefs.put("lastRefreshDate", prefs.getString("lastRefreshDate", null) ?: JSONObject.NULL)
        localPrefs.put("budgetStartDate", prefs.getString("budgetStartDate", null) ?: JSONObject.NULL)
        localPrefs.put("currencySymbol", prefs.getString("currencySymbol", "$"))
        localPrefs.put("digitCount", prefs.getInt("digitCount", 3))
        localPrefs.put("showDecimals", prefs.getBoolean("showDecimals", false))
        localPrefs.put("dateFormatPattern", prefs.getString("dateFormatPattern", "yyyy-MM-dd"))
        localPrefs.put("chartPalette", prefs.getString("chartPalette", "Sunset"))
        localPrefs.put("appLanguage", prefs.getString("appLanguage", "en"))
        localPrefs.put("budgetPeriod", prefs.getString("budgetPeriod", "DAILY"))
        localPrefs.put("resetHour", prefs.getInt("resetHour", 0))
        localPrefs.put("resetDayOfWeek", prefs.getInt("resetDayOfWeek", 7))
        localPrefs.put("resetDayOfMonth", prefs.getInt("resetDayOfMonth", 1))
        localPrefs.put("isManualBudgetEnabled", prefs.getBoolean("isManualBudgetEnabled", false))
        localPrefs.put("manualBudgetAmount", prefs.getFloat("manualBudgetAmount", 0f).toDouble())
        localPrefs.put("weekStartSunday", prefs.getBoolean("weekStartSunday", true))
        localPrefs.put("matchDays", prefs.getInt("matchDays", 7))
        localPrefs.put("matchPercent", prefs.getFloat("matchPercent", 1.0f).toDouble())
        localPrefs.put("matchDollar", prefs.getInt("matchDollar", 1))
        localPrefs.put("matchChars", prefs.getInt("matchChars", 5))
        json.put("localPrefs", localPrefs)

        return json.toString()
    }

    fun isFullBackup(content: String): Boolean {
        if (!content.trimStart().startsWith("{")) return false
        return try {
            val json = JSONObject(content)
            json.optString("type") == "syncbudget_full_backup"
        } catch (_: Exception) {
            false
        }
    }

    fun extractTransactions(content: String, existingIds: Set<Int>): CsvParseResult {
        return try {
            val json = JSONObject(content)
            val txnArray = json.optJSONArray("transactions")
                ?: return CsvParseResult(emptyList(), "No transactions in backup")

            // Write transactions array to a temp internal file, then use existing repo to parse
            val transactions = mutableListOf<Transaction>()
            val usedIds = existingIds.toMutableSet()
            for (i in 0 until txnArray.length()) {
                try {
                    val obj = txnArray.getJSONObject(i)
                    val originalId = obj.getInt("id")
                    val id = if (originalId !in usedIds) originalId
                             else generateTransactionId(usedIds)
                    usedIds.add(id)

                    val categoryAmounts = mutableListOf<CategoryAmount>()
                    val catArray = obj.optJSONArray("categoryAmounts")
                    if (catArray != null) {
                        for (j in 0 until catArray.length()) {
                            val catObj = catArray.getJSONObject(j)
                            categoryAmounts.add(
                                CategoryAmount(
                                    categoryId = catObj.getInt("categoryId"),
                                    amount = catObj.getDouble("amount")
                                )
                            )
                        }
                    }

                    transactions.add(
                        Transaction(
                            id = id,
                            type = TransactionType.valueOf(obj.getString("type")),
                            date = java.time.LocalDate.parse(obj.getString("date")),
                            source = obj.getString("source"),
                            amount = obj.getDouble("amount"),
                            categoryAmounts = categoryAmounts,
                            isUserCategorized = obj.optBoolean("isUserCategorized", false)
                        )
                    )
                } catch (e: Exception) {
                    // Skip malformed entries
                }
            }
            CsvParseResult(transactions, null)
        } catch (e: Exception) {
            CsvParseResult(emptyList(), "Failed to parse backup: ${e.message}")
        }
    }

    fun restoreFullState(context: Context, content: String) {
        val json = JSONObject(content)

        // Write each data array to its repo file
        fun writeArray(key: String, fileName: String) {
            val arr = json.optJSONArray(key)
            if (arr != null) {
                context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                    it.write(arr.toString().toByteArray())
                }
            }
        }

        writeArray("transactions", "transactions.json")
        writeArray("categories", "categories.json")
        writeArray("recurringExpenses", "recurring_expenses.json")
        writeArray("incomeSources", "income_sources.json")
        writeArray("amortizationEntries", "amortization_entries.json")
        writeArray("savingsGoals", "future_expenditures.json")
        writeArray("periodLedger", "period_ledger.json")

        // Restore shared settings
        if (json.has("sharedSettings")) {
            val settings = SharedSettingsRepository.fromJson(json.getJSONObject("sharedSettings"))
            SharedSettingsRepository.save(context, settings)
        }

        // Restore local prefs
        if (json.has("localPrefs")) {
            val lp = json.getJSONObject("localPrefs")
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putFloat("availableCash", lp.optDouble("availableCash", 0.0).toFloat())
                if (!lp.isNull("lastRefreshDate")) {
                    putString("lastRefreshDate", lp.getString("lastRefreshDate"))
                } else {
                    remove("lastRefreshDate")
                }
                if (!lp.isNull("budgetStartDate")) {
                    putString("budgetStartDate", lp.getString("budgetStartDate"))
                } else {
                    remove("budgetStartDate")
                }
                putString("currencySymbol", lp.optString("currencySymbol", "$"))
                putInt("digitCount", lp.optInt("digitCount", 3))
                putBoolean("showDecimals", lp.optBoolean("showDecimals", false))
                putString("dateFormatPattern", lp.optString("dateFormatPattern", "yyyy-MM-dd"))
                putString("chartPalette", lp.optString("chartPalette", "Sunset"))
                putString("appLanguage", lp.optString("appLanguage", "en"))
                putString("budgetPeriod", lp.optString("budgetPeriod", "DAILY"))
                putInt("resetHour", lp.optInt("resetHour", 0))
                putInt("resetDayOfWeek", lp.optInt("resetDayOfWeek", 7))
                putInt("resetDayOfMonth", lp.optInt("resetDayOfMonth", 1))
                putBoolean("isManualBudgetEnabled", lp.optBoolean("isManualBudgetEnabled", false))
                putFloat("manualBudgetAmount", lp.optDouble("manualBudgetAmount", 0.0).toFloat())
                putBoolean("weekStartSunday", lp.optBoolean("weekStartSunday", true))
                putInt("matchDays", lp.optInt("matchDays", 7))
                putFloat("matchPercent", lp.optDouble("matchPercent", 1.0).toFloat())
                putInt("matchDollar", lp.optInt("matchDollar", 1))
                putInt("matchChars", lp.optInt("matchChars", 5))
                apply()
            }
        }
    }
}
