package com.syncbudget.app.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object RecurringExpenseRepository {

    private const val FILE_NAME = "recurring_expenses.json"
    private const val TAG = "RecurringExpenseRepo"

    fun save(context: Context, recurringExpenses: List<RecurringExpense>) {
        val jsonArray = JSONArray()
        for (r in recurringExpenses) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("source", r.source)
            obj.put("description", r.description)
            obj.put("amount", r.amount)
            obj.put("repeatType", r.repeatType.name)
            obj.put("repeatInterval", r.repeatInterval)
            if (r.startDate != null) obj.put("startDate", r.startDate.toString())
            if (r.monthDay1 != null) obj.put("monthDay1", r.monthDay1)
            if (r.monthDay2 != null) obj.put("monthDay2", r.monthDay2)
            // Sync fields
            obj.put("deviceId", r.deviceId)
            obj.put("deleted", r.deleted)
            // Set-aside tracking
            obj.put("setAsideSoFar", r.setAsideSoFar)
            obj.put("isAccelerated", r.isAccelerated)
            jsonArray.put(obj)
        }
        SafeIO.atomicWriteJson(context, FILE_NAME, jsonArray)
    }

    fun load(context: Context): List<RecurringExpense> {
        val jsonArray = SafeIO.readJsonArray(context, FILE_NAME)
        val list = mutableListOf<RecurringExpense>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                val amount = SafeIO.safeDouble(obj.getDouble("amount"))
                val repeatType = try { RepeatType.valueOf(obj.getString("repeatType")) } catch (_: Exception) { RepeatType.MONTHS }
                val startDate = if (obj.has("startDate")) {
                    try { LocalDate.parse(obj.getString("startDate")) } catch (_: Exception) { null }
                } else null
                list.add(
                    RecurringExpense(
                        id = obj.getInt("id"),
                        source = obj.getString("source"),
                        description = obj.optString("description", ""),
                        amount = amount,
                        repeatType = repeatType,
                        repeatInterval = obj.getInt("repeatInterval"),
                        startDate = startDate,
                        monthDay1 = if (obj.has("monthDay1")) obj.getInt("monthDay1") else null,
                        monthDay2 = if (obj.has("monthDay2")) obj.getInt("monthDay2") else null,
                        deviceId = obj.optString("deviceId", ""),
                        deleted = obj.optBoolean("deleted", false),
                        setAsideSoFar = SafeIO.safeDouble(obj.optDouble("setAsideSoFar", 0.0)),
                        isAccelerated = obj.optBoolean("isAccelerated", false)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupt record at index $i: ${e.message}")
            }
        }
        return list
    }
}
