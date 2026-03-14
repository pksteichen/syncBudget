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
            obj.put("source_clock", r.source_clock)
            obj.put("description_clock", r.description_clock)
            obj.put("amount_clock", r.amount_clock)
            obj.put("repeatType_clock", r.repeatType_clock)
            obj.put("repeatInterval_clock", r.repeatInterval_clock)
            obj.put("startDate_clock", r.startDate_clock)
            obj.put("monthDay1_clock", r.monthDay1_clock)
            obj.put("monthDay2_clock", r.monthDay2_clock)
            obj.put("deleted_clock", r.deleted_clock)
            obj.put("deviceId_clock", r.deviceId_clock)
            // Set-aside tracking
            obj.put("setAsideSoFar", r.setAsideSoFar)
            obj.put("isAccelerated", r.isAccelerated)
            obj.put("setAsideSoFar_clock", r.setAsideSoFar_clock)
            obj.put("isAccelerated_clock", r.isAccelerated_clock)
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
                        source_clock = obj.optLong("source_clock", 0L),
                        description_clock = obj.optLong("description_clock", 0L),
                        amount_clock = obj.optLong("amount_clock", 0L),
                        repeatType_clock = obj.optLong("repeatType_clock", 0L),
                        repeatInterval_clock = obj.optLong("repeatInterval_clock", 0L),
                        startDate_clock = obj.optLong("startDate_clock", 0L),
                        monthDay1_clock = obj.optLong("monthDay1_clock", 0L),
                        monthDay2_clock = obj.optLong("monthDay2_clock", 0L),
                        deleted_clock = obj.optLong("deleted_clock", 0L),
                        deviceId_clock = obj.optLong("deviceId_clock", 0L),
                        setAsideSoFar = SafeIO.safeDouble(obj.optDouble("setAsideSoFar", 0.0)),
                        isAccelerated = obj.optBoolean("isAccelerated", false),
                        setAsideSoFar_clock = obj.optLong("setAsideSoFar_clock", 0L),
                        isAccelerated_clock = obj.optLong("isAccelerated_clock", 0L)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupt record at index $i: ${e.message}")
            }
        }
        return list
    }
}
