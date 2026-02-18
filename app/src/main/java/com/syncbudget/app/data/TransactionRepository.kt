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
                    isBudgetIncome = isBudgetIncome
                )
            )
        }
        return list
    }
}
