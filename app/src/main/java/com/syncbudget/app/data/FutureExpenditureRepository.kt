package com.syncbudget.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object FutureExpenditureRepository {

    private const val FILE_NAME = "future_expenditures.json"

    fun save(context: Context, expenditures: List<FutureExpenditure>) {
        val jsonArray = JSONArray()
        for (e in expenditures) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("description", e.description)
            obj.put("amount", e.amount)
            obj.put("targetDate", e.targetDate.toString())
            obj.put("totalSavedSoFar", e.totalSavedSoFar)
            obj.put("isPaused", e.isPaused)
            jsonArray.put(obj)
        }
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { fos ->
            fos.write(jsonArray.toString().toByteArray())
        }
    }

    fun load(context: Context): List<FutureExpenditure> {
        val file = context.getFileStreamPath(FILE_NAME)
        if (!file.exists()) return emptyList()
        val json = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        if (json.isBlank()) return emptyList()
        val jsonArray = JSONArray(json)
        val list = mutableListOf<FutureExpenditure>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                FutureExpenditure(
                    id = obj.getInt("id"),
                    description = obj.getString("description"),
                    amount = obj.getDouble("amount"),
                    targetDate = LocalDate.parse(obj.getString("targetDate")),
                    totalSavedSoFar = if (obj.has("totalSavedSoFar")) obj.getDouble("totalSavedSoFar") else 0.0,
                    isPaused = if (obj.has("isPaused")) obj.getBoolean("isPaused") else false
                )
            )
        }
        return list
    }
}
