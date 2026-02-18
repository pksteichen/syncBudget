package com.syncbudget.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object RecurringExpenseRepository {

    private const val FILE_NAME = "recurring_expenses.json"

    fun save(context: Context, recurringExpenses: List<RecurringExpense>) {
        val jsonArray = JSONArray()
        for (r in recurringExpenses) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("source", r.source)
            obj.put("amount", r.amount)
            obj.put("repeatType", r.repeatType.name)
            obj.put("repeatInterval", r.repeatInterval)
            if (r.startDate != null) obj.put("startDate", r.startDate.toString())
            if (r.monthDay1 != null) obj.put("monthDay1", r.monthDay1)
            if (r.monthDay2 != null) obj.put("monthDay2", r.monthDay2)
            jsonArray.put(obj)
        }
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { fos ->
            fos.write(jsonArray.toString().toByteArray())
        }
    }

    fun load(context: Context): List<RecurringExpense> {
        val file = context.getFileStreamPath(FILE_NAME)
        if (!file.exists()) return emptyList()
        val json = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        if (json.isBlank()) return emptyList()
        val jsonArray = JSONArray(json)
        val list = mutableListOf<RecurringExpense>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                RecurringExpense(
                    id = obj.getInt("id"),
                    source = obj.getString("source"),
                    amount = obj.getDouble("amount"),
                    repeatType = RepeatType.valueOf(obj.getString("repeatType")),
                    repeatInterval = obj.getInt("repeatInterval"),
                    startDate = if (obj.has("startDate")) LocalDate.parse(obj.getString("startDate")) else null,
                    monthDay1 = if (obj.has("monthDay1")) obj.getInt("monthDay1") else null,
                    monthDay2 = if (obj.has("monthDay2")) obj.getInt("monthDay2") else null
                )
            )
        }
        return list
    }
}
