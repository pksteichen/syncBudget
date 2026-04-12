package com.techadvantage.budgetrak.data

import android.content.Context
import com.techadvantage.budgetrak.data.sync.PeriodLedgerRepository
import org.json.JSONArray
import org.json.JSONObject
import com.techadvantage.budgetrak.data.BackupManager
import java.time.LocalDateTime

object FullBackupSerializer {

    fun serialize(context: Context, mode: String = "backup"): String {
        val isJoinSnapshot = mode == "joinSnapshot"
        val json = JSONObject()
        json.put("type", if (isJoinSnapshot) "syncbudget_join_snapshot" else "syncbudget_full_backup")
        json.put("version", 1)
        json.put("savedAt", LocalDateTime.now().toString())
        if (isJoinSnapshot) {
            json.put("snapshotTimestamp", System.currentTimeMillis())
        }

        // Read each repo file and embed its raw JSON
        fun readFileArray(fileName: String): JSONArray? {
            val file = context.getFileStreamPath(fileName)
            if (!file.exists()) return null
            val text = file.readText()
            if (text.isBlank()) return null
            return try { JSONArray(text) } catch (_: Exception) { null }
        }

        // Filter out tombstoned (deleted=true) records from backup
        fun filterActive(arr: JSONArray): JSONArray {
            val active = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (!obj.optBoolean("deleted", false)) active.put(obj)
            }
            return active
        }

        // joinSnapshot keeps tombstones (needed for sync); backup filters them out
        fun maybeFilter(arr: JSONArray): JSONArray = if (isJoinSnapshot) arr else filterActive(arr)

        readFileArray("transactions.json")?.let { json.put("transactions", maybeFilter(it)) }
        readFileArray("categories.json")?.let { json.put("categories", maybeFilter(it)) }
        readFileArray("recurring_expenses.json")?.let { json.put("recurringExpenses", maybeFilter(it)) }
        readFileArray("income_sources.json")?.let { json.put("incomeSources", maybeFilter(it)) }
        readFileArray("amortization_entries.json")?.let { json.put("amortizationEntries", maybeFilter(it)) }
        readFileArray("future_expenditures.json")?.let { json.put("savingsGoals", maybeFilter(it)) }
        readFileArray("period_ledger.json")?.let { json.put("periodLedger", it) }
        readFileArray("archived_transactions.json")?.let { json.put("archivedTransactions", maybeFilter(it)) }

        // Shared settings
        val sharedSettings = SharedSettingsRepository.load(context)
        json.put("sharedSettings", SharedSettingsRepository.toJson(sharedSettings))

        // Local prefs — only for backup mode
        if (!isJoinSnapshot) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val localPrefs = JSONObject()
            localPrefs.put("availableCash", prefs.getDoubleCompat("availableCash"))
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
            localPrefs.put("manualBudgetAmount", prefs.getDoubleCompat("manualBudgetAmount"))
            localPrefs.put("weekStartSunday", prefs.getBoolean("weekStartSunday", true))
            localPrefs.put("matchDays", prefs.getInt("matchDays", 7))
            localPrefs.put("matchPercent", prefs.getDoubleCompat("matchPercent", 1.0))
            localPrefs.put("matchDollar", prefs.getInt("matchDollar", 1))
            localPrefs.put("matchChars", prefs.getInt("matchChars", 5))
            json.put("localPrefs", localPrefs)
        }

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
                    if (obj.optBoolean("deleted", false)) continue
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
                            description = obj.optString("description", ""),
                            amount = obj.getDouble("amount"),
                            categoryAmounts = categoryAmounts,
                            isUserCategorized = obj.optBoolean("isUserCategorized", false),
                            excludeFromBudget = obj.optBoolean("excludeFromBudget", false),
                            isBudgetIncome = obj.optBoolean("isBudgetIncome", false),
                            linkedRecurringExpenseId = if (obj.has("linkedRecurringExpenseId") && !obj.isNull("linkedRecurringExpenseId")) obj.getInt("linkedRecurringExpenseId") else null,
                            linkedAmortizationEntryId = if (obj.has("linkedAmortizationEntryId") && !obj.isNull("linkedAmortizationEntryId")) obj.getInt("linkedAmortizationEntryId") else null,
                            linkedIncomeSourceId = if (obj.has("linkedIncomeSourceId") && !obj.isNull("linkedIncomeSourceId")) obj.getInt("linkedIncomeSourceId") else null,
                            linkedRecurringExpenseAmount = obj.optDouble("linkedRecurringExpenseAmount", 0.0).let { if (it.isNaN()) 0.0 else it },
                            linkedIncomeSourceAmount = obj.optDouble("linkedIncomeSourceAmount", 0.0).let { if (it.isNaN()) 0.0 else it },
                            amortizationAppliedAmount = obj.optDouble("amortizationAppliedAmount", 0.0).let { if (it.isNaN()) 0.0 else it },
                            linkedSavingsGoalId = if (obj.has("linkedSavingsGoalId") && !obj.isNull("linkedSavingsGoalId")) obj.getInt("linkedSavingsGoalId") else null,
                            linkedSavingsGoalAmount = obj.optDouble("linkedSavingsGoalAmount", 0.0).let { if (it.isNaN()) 0.0 else it },
                            receiptId1 = if (obj.has("receiptId1") && !obj.isNull("receiptId1")) obj.getString("receiptId1") else null,
                            receiptId2 = if (obj.has("receiptId2") && !obj.isNull("receiptId2")) obj.getString("receiptId2") else null,
                            receiptId3 = if (obj.has("receiptId3") && !obj.isNull("receiptId3")) obj.getString("receiptId3") else null,
                            receiptId4 = if (obj.has("receiptId4") && !obj.isNull("receiptId4")) obj.getString("receiptId4") else null,
                            receiptId5 = if (obj.has("receiptId5") && !obj.isNull("receiptId5")) obj.getString("receiptId5") else null
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

    /**
     * Auto-backup current state before a restore, so the user can
     * recover if the restore data is corrupt or wrong.
     */
    fun backupBeforeRestore(context: Context): String {
        return serialize(context)
    }

    /**
     * Restore data from a join snapshot JSONObject (already parsed).
     * Simpler than restoreFullState: no local prefs, no pre-restore backup.
     */
    fun restoreFromSnapshot(context: Context, json: JSONObject) {
        fun writeArray(key: String, fileName: String) {
            val arr = json.optJSONArray(key)
            if (arr != null) {
                SafeIO.atomicWriteJson(context, fileName, arr)
            }
        }
        writeArray("transactions", "transactions.json")
        writeArray("categories", "categories.json")
        writeArray("recurringExpenses", "recurring_expenses.json")
        writeArray("incomeSources", "income_sources.json")
        writeArray("amortizationEntries", "amortization_entries.json")
        writeArray("savingsGoals", "future_expenditures.json")
        writeArray("periodLedger", "period_ledger.json")
        writeArray("archivedTransactions", "archived_transactions.json")

        if (json.has("sharedSettings")) {
            val settings = SharedSettingsRepository.fromJson(json.getJSONObject("sharedSettings"))
            SharedSettingsRepository.save(context, settings)
        }
    }

    fun restoreFullState(context: Context, content: String) {
        val json = JSONObject(content)

        // Validate backup format before overwriting anything
        val type = json.optString("type", "")
        if (type != "syncbudget_full_backup") {
            throw IllegalArgumentException("Not a valid BudgeTrak backup file")
        }

        // Auto-backup current state so the user can recover
        val preRestoreBackup = try { backupBeforeRestore(context) } catch (_: Exception) { null }
        if (preRestoreBackup != null) {
            try {
                val dir = BackupManager.getSupportDir()
                java.io.File(dir, "pre_restore_backup.json").writeText(preRestoreBackup)
            } catch (_: Exception) { /* best effort */ }
        }

        // Write each data array to its repo file using atomic writes
        fun writeArray(key: String, fileName: String) {
            val arr = json.optJSONArray(key)
            if (arr != null) {
                SafeIO.atomicWriteJson(context, fileName, arr)
            }
        }

        writeArray("transactions", "transactions.json")
        writeArray("categories", "categories.json")
        writeArray("recurringExpenses", "recurring_expenses.json")
        writeArray("incomeSources", "income_sources.json")
        writeArray("amortizationEntries", "amortization_entries.json")
        writeArray("savingsGoals", "future_expenditures.json")
        writeArray("periodLedger", "period_ledger.json")
        writeArray("archivedTransactions", "archived_transactions.json")

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
                val restoredCash = lp.optDouble("availableCash", 0.0).let {
                    if (it.isNaN() || it.isInfinite()) 0.0 else BudgetCalculator.roundCents(it)
                }
                putString("availableCash", restoredCash.toString())
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
                putString("manualBudgetAmount", lp.optDouble("manualBudgetAmount", 0.0).toString())
                putBoolean("weekStartSunday", lp.optBoolean("weekStartSunday", true))
                putInt("matchDays", lp.optInt("matchDays", 7))
                putString("matchPercent", lp.optDouble("matchPercent", 1.0).toString())
                putInt("matchDollar", lp.optInt("matchDollar", 1))
                putInt("matchChars", lp.optInt("matchChars", 5))
                apply()
            }
        }
    }
}
