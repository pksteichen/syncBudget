package com.syncbudget.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object TransactionRepository {

    private const val FILE_NAME = "transactions.json"

    fun save(context: Context, transactions: List<Transaction>) {
        val jsonArray = JSONArray()
        for (t in transactions) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("type", t.type.name)
            obj.put("date", t.date.toString())
            obj.put("source", t.source)
            obj.put("amount", t.amount)
            obj.put("isUserCategorized", t.isUserCategorized)
            obj.put("isBudgetIncome", t.isBudgetIncome)
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
            obj.put("amount_clock", t.amount_clock)
            obj.put("date_clock", t.date_clock)
            obj.put("type_clock", t.type_clock)
            obj.put("categoryAmounts_clock", t.categoryAmounts_clock)
            obj.put("isUserCategorized_clock", t.isUserCategorized_clock)
            obj.put("isBudgetIncome_clock", t.isBudgetIncome_clock)
            obj.put("deleted_clock", t.deleted_clock)
            jsonArray.put(obj)
        }
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { fos ->
            fos.write(jsonArray.toString().toByteArray())
        }
    }

    fun load(context: Context): List<Transaction> {
        val file = context.getFileStreamPath(FILE_NAME)
        if (!file.exists()) return emptyList()
        val json = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        if (json.isBlank()) return emptyList()
        val jsonArray = JSONArray(json)
        val list = mutableListOf<Transaction>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val amount = obj.getDouble("amount")
            val categoryAmounts = if (obj.has("categoryAmounts")) {
                val catArray = obj.getJSONArray("categoryAmounts")
                (0 until catArray.length()).map { j ->
                    val catObj = catArray.getJSONObject(j)
                    CategoryAmount(
                        categoryId = catObj.getInt("categoryId"),
                        amount = catObj.getDouble("amount")
                    )
                }
            } else {
                emptyList()
            }
            val isUserCategorized = if (obj.has("isUserCategorized")) obj.getBoolean("isUserCategorized") else true
            val isBudgetIncome = if (obj.has("isBudgetIncome")) obj.getBoolean("isBudgetIncome") else false
            list.add(
                Transaction(
                    id = obj.getInt("id"),
                    type = TransactionType.valueOf(obj.getString("type")),
                    date = LocalDate.parse(obj.getString("date")),
                    source = obj.getString("source"),
                    categoryAmounts = categoryAmounts,
                    amount = amount,
                    isUserCategorized = isUserCategorized,
                    isBudgetIncome = isBudgetIncome,
                    deviceId = obj.optString("deviceId", ""),
                    deleted = obj.optBoolean("deleted", false),
                    source_clock = obj.optLong("source_clock", 0L),
                    amount_clock = obj.optLong("amount_clock", 0L),
                    date_clock = obj.optLong("date_clock", 0L),
                    type_clock = obj.optLong("type_clock", 0L),
                    categoryAmounts_clock = obj.optLong("categoryAmounts_clock", 0L),
                    isUserCategorized_clock = obj.optLong("isUserCategorized_clock", 0L),
                    isBudgetIncome_clock = obj.optLong("isBudgetIncome_clock", 0L),
                    deleted_clock = obj.optLong("deleted_clock", 0L)
                )
            )
        }
        return list
    }
}
