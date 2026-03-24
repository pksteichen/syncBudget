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
            obj.put("amortizationAppliedAmount", t.amortizationAppliedAmount)
            obj.put("linkedRecurringExpenseAmount", t.linkedRecurringExpenseAmount)
            obj.put("linkedIncomeSourceAmount", t.linkedIncomeSourceAmount)
            obj.put("linkedSavingsGoalId", t.linkedSavingsGoalId ?: JSONObject.NULL)
            obj.put("linkedSavingsGoalAmount", t.linkedSavingsGoalAmount)
            obj.put("receiptId1", t.receiptId1 ?: JSONObject.NULL)
            obj.put("receiptId2", t.receiptId2 ?: JSONObject.NULL)
            obj.put("receiptId3", t.receiptId3 ?: JSONObject.NULL)
            obj.put("receiptId4", t.receiptId4 ?: JSONObject.NULL)
            obj.put("receiptId5", t.receiptId5 ?: JSONObject.NULL)
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
                        receiptId1 = if (obj.has("receiptId1") && !obj.isNull("receiptId1")) obj.getString("receiptId1") else null,
                        receiptId2 = if (obj.has("receiptId2") && !obj.isNull("receiptId2")) obj.getString("receiptId2") else null,
                        receiptId3 = if (obj.has("receiptId3") && !obj.isNull("receiptId3")) obj.getString("receiptId3") else null,
                        receiptId4 = if (obj.has("receiptId4") && !obj.isNull("receiptId4")) obj.getString("receiptId4") else null,
                        receiptId5 = if (obj.has("receiptId5") && !obj.isNull("receiptId5")) obj.getString("receiptId5") else null,
                        deviceId = obj.optString("deviceId", ""),
                        deleted = obj.optBoolean("deleted", false)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupt transaction at index $i: ${e.message}")
            }
        }
        return list
    }
}
