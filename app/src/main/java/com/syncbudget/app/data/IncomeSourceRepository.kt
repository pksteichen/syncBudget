package com.syncbudget.app.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object IncomeSourceRepository {

    private const val FILE_NAME = "income_sources.json"
    private const val TAG = "IncomeSourceRepo"

    fun save(context: Context, incomeSources: List<IncomeSource>) {
        val jsonArray = JSONArray()
        for (s in incomeSources) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("source", s.source)
            obj.put("description", s.description)
            obj.put("amount", s.amount)
            obj.put("repeatType", s.repeatType.name)
            obj.put("repeatInterval", s.repeatInterval)
            if (s.startDate != null) obj.put("startDate", s.startDate.toString())
            if (s.monthDay1 != null) obj.put("monthDay1", s.monthDay1)
            if (s.monthDay2 != null) obj.put("monthDay2", s.monthDay2)
            // Sync fields
            obj.put("deviceId", s.deviceId)
            obj.put("deleted", s.deleted)
            jsonArray.put(obj)
        }
        SafeIO.atomicWriteJson(context, FILE_NAME, jsonArray)
    }

    fun load(context: Context): List<IncomeSource> {
        val jsonArray = SafeIO.readJsonArray(context, FILE_NAME)
        val list = mutableListOf<IncomeSource>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                val amount = SafeIO.safeDouble(obj.getDouble("amount"))
                val repeatType = try { RepeatType.valueOf(obj.getString("repeatType")) } catch (_: Exception) { RepeatType.MONTHS }
                val startDate = if (obj.has("startDate")) {
                    try { LocalDate.parse(obj.getString("startDate")) } catch (_: Exception) { null }
                } else null
                list.add(
                    IncomeSource(
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
                        deleted = obj.optBoolean("deleted", false)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupt record at index $i: ${e.message}")
            }
        }
        return list
    }
}
