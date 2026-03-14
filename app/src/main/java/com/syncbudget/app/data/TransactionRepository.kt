package com.syncbudget.app.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object TransactionRepository {

    private const val FILE_NAME = "transactions.json"
    private const val TAG = "TransactionRepo"

    fun save(context: Context, transactions: List<Transaction>) {
        val jsonArray = JSONArray()
        for (t in transactions) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("type", t.type.name)
            obj.put("date", t.date.toString())
            obj.put("source", t.source)
            obj.put("description", t.description)
            obj.put("amount", t.amount)
            obj.put("isUserCategorized", t.isUserCategorized)
            obj.put("excludeFromBudget", t.excludeFromBudget)
            obj.put("isBudgetIncome", t.isBudgetIncome)
            obj.put("linkedRecurringExpenseId", t.linkedRecurringExpenseId ?: JSONObject.NULL)
            obj.put("linkedAmortizationEntryId", t.linkedAmortizationEntryId ?: JSONObject.NULL)
            obj.put("linkedIncomeSourceId", t.linkedIncomeSourceId ?: JSONObject.NULL)
            if (t.categoryAmounts.isNotEmpty()) {
                val catArray = JSONArray()
                for (ca in t.categoryAmounts) {
                    val catObj = JSONObject()
                    catObj.put("categoryId", ca.categoryId)
                    catObj.put("amount", ca.amount)
                    catArray.put(catObj)
                }
                obj.put("categoryAmounts", catArray)
            }
            // Sync fields
            obj.put("deviceId", t.deviceId)
            obj.put("deleted", t.deleted)
            obj.put("source_clock", t.source_clock)
            obj.put("description_clock", t.description_clock)
            obj.put("amount_clock", t.amount_clock)
            obj.put("date_clock", t.date_clock)
            obj.put("type_clock", t.type_clock)
            obj.put("categoryAmounts_clock", t.categoryAmounts_clock)
            obj.put("isUserCategorized_clock", t.isUserCategorized_clock)
            obj.put("excludeFromBudget_clock", t.excludeFromBudget_clock)
            obj.put("isBudgetIncome_clock", t.isBudgetIncome_clock)
            obj.put("linkedRecurringExpenseId_clock", t.linkedRecurringExpenseId_clock)
            obj.put("linkedAmortizationEntryId_clock", t.linkedAmortizationEntryId_clock)
            obj.put("linkedIncomeSourceId_clock", t.linkedIncomeSourceId_clock)
            obj.put("amortizationAppliedAmount", t.amortizationAppliedAmount)
            obj.put("amortizationAppliedAmount_clock", t.amortizationAppliedAmount_clock)
            obj.put("linkedRecurringExpenseAmount", t.linkedRecurringExpenseAmount)
            obj.put("linkedRecurringExpenseAmount_clock", t.linkedRecurringExpenseAmount_clock)
            obj.put("linkedIncomeSourceAmount", t.linkedIncomeSourceAmount)
            obj.put("linkedIncomeSourceAmount_clock", t.linkedIncomeSourceAmount_clock)
            obj.put("linkedSavingsGoalId", t.linkedSavingsGoalId ?: JSONObject.NULL)
            obj.put("linkedSavingsGoalId_clock", t.linkedSavingsGoalId_clock)
            obj.put("linkedSavingsGoalAmount", t.linkedSavingsGoalAmount)
            obj.put("linkedSavingsGoalAmount_clock", t.linkedSavingsGoalAmount_clock)
            obj.put("deleted_clock", t.deleted_clock)
            obj.put("deviceId_clock", t.deviceId_clock)
            jsonArray.put(obj)
        }
        SafeIO.atomicWriteJson(context, FILE_NAME, jsonArray)
    }

    fun load(context: Context): List<Transaction> {
        val jsonArray = SafeIO.readJsonArray(context, FILE_NAME)
        val list = mutableListOf<Transaction>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                val amount = SafeIO.safeDouble(obj.getDouble("amount"))
                val categoryAmounts = if (obj.has("categoryAmounts")) {
                    val catArray = obj.getJSONArray("categoryAmounts")
                    (0 until catArray.length()).mapNotNull { j ->
                        try {
                            val catObj = catArray.getJSONObject(j)
                            CategoryAmount(
                                categoryId = catObj.getInt("categoryId"),
                                amount = SafeIO.safeDouble(catObj.getDouble("amount"))
                            )
                        } catch (_: Exception) { null }
                    }
                } else {
                    emptyList()
                }
                val isUserCategorized = if (obj.has("isUserCategorized")) obj.getBoolean("isUserCategorized") else true
                val excludeFromBudget = if (obj.has("excludeFromBudget")) obj.getBoolean("excludeFromBudget") else false
                val isBudgetIncome = if (obj.has("isBudgetIncome")) obj.getBoolean("isBudgetIncome") else false
                val linkedRecurringExpenseId = if (obj.has("linkedRecurringExpenseId") && !obj.isNull("linkedRecurringExpenseId")) obj.getInt("linkedRecurringExpenseId") else null
                val linkedAmortizationEntryId = if (obj.has("linkedAmortizationEntryId") && !obj.isNull("linkedAmortizationEntryId")) obj.getInt("linkedAmortizationEntryId") else null
                val linkedIncomeSourceId = if (obj.has("linkedIncomeSourceId") && !obj.isNull("linkedIncomeSourceId")) obj.getInt("linkedIncomeSourceId") else null
                val type = try { TransactionType.valueOf(obj.getString("type")) } catch (_: Exception) { TransactionType.EXPENSE }
                val date = try { LocalDate.parse(obj.getString("date")) } catch (_: Exception) { LocalDate.now() }
                list.add(
                    Transaction(
                        id = obj.getInt("id"),
                        type = type,
                        date = date,
                        source = obj.getString("source"),
                        description = obj.optString("description", ""),
                        categoryAmounts = categoryAmounts,
                        amount = amount,
                        isUserCategorized = isUserCategorized,
                        excludeFromBudget = excludeFromBudget,
                        isBudgetIncome = isBudgetIncome,
                        linkedRecurringExpenseId = linkedRecurringExpenseId,
                        linkedAmortizationEntryId = linkedAmortizationEntryId,
                        linkedIncomeSourceId = linkedIncomeSourceId,
                        amortizationAppliedAmount = SafeIO.safeDouble(obj.optDouble("amortizationAppliedAmount", 0.0)),
                        linkedRecurringExpenseAmount = SafeIO.safeDouble(obj.optDouble("linkedRecurringExpenseAmount", 0.0)),
                        linkedIncomeSourceAmount = SafeIO.safeDouble(obj.optDouble("linkedIncomeSourceAmount", 0.0)),
                        linkedSavingsGoalId = if (obj.has("linkedSavingsGoalId") && !obj.isNull("linkedSavingsGoalId")) obj.getInt("linkedSavingsGoalId") else null,
                        linkedSavingsGoalAmount = SafeIO.safeDouble(obj.optDouble("linkedSavingsGoalAmount", 0.0)),
                        deviceId = obj.optString("deviceId", ""),
                        deleted = obj.optBoolean("deleted", false),
                        source_clock = obj.optLong("source_clock", 0L),
                        description_clock = obj.optLong("description_clock", 0L),
                        amount_clock = obj.optLong("amount_clock", 0L),
                        date_clock = obj.optLong("date_clock", 0L),
                        type_clock = obj.optLong("type_clock", 0L),
                        categoryAmounts_clock = obj.optLong("categoryAmounts_clock", 0L),
                        isUserCategorized_clock = obj.optLong("isUserCategorized_clock", 0L),
                        excludeFromBudget_clock = obj.optLong("excludeFromBudget_clock", 0L),
                        isBudgetIncome_clock = obj.optLong("isBudgetIncome_clock", 0L),
                        linkedRecurringExpenseId_clock = obj.optLong("linkedRecurringExpenseId_clock", 0L),
                        linkedAmortizationEntryId_clock = obj.optLong("linkedAmortizationEntryId_clock", 0L),
                        linkedIncomeSourceId_clock = obj.optLong("linkedIncomeSourceId_clock", 0L),
                        amortizationAppliedAmount_clock = obj.optLong("amortizationAppliedAmount_clock", 0L),
                        linkedRecurringExpenseAmount_clock = obj.optLong("linkedRecurringExpenseAmount_clock", 0L),
                        linkedIncomeSourceAmount_clock = obj.optLong("linkedIncomeSourceAmount_clock", 0L),
                        linkedSavingsGoalId_clock = obj.optLong("linkedSavingsGoalId_clock", 0L),
                        linkedSavingsGoalAmount_clock = obj.optLong("linkedSavingsGoalAmount_clock", 0L),
                        deleted_clock = obj.optLong("deleted_clock", 0L),
                        deviceId_clock = obj.optLong("deviceId_clock", 0L)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupt transaction at index $i: ${e.message}")
            }
        }
        return list
    }
}
