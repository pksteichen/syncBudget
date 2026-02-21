package com.syncbudget.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object IncomeSourceRepository {

    private const val FILE_NAME = "income_sources.json"

    fun save(context: Context, incomeSources: List<IncomeSource>) {
        val jsonArray = JSONArray()
        for (s in incomeSources) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("source", s.source)
            obj.put("amount", s.amount)
            obj.put("repeatType", s.repeatType.name)
            obj.put("repeatInterval", s.repeatInterval)
            if (s.startDate != null) obj.put("startDate", s.startDate.toString())
            if (s.monthDay1 != null) obj.put("monthDay1", s.monthDay1)
            if (s.monthDay2 != null) obj.put("monthDay2", s.monthDay2)
            // Sync fields
            obj.put("deviceId", s.deviceId)
            obj.put("deleted", s.deleted)
            obj.put("source_clock", s.source_clock)
            obj.put("amount_clock", s.amount_clock)
            obj.put("repeatType_clock", s.repeatType_clock)
            obj.put("repeatInterval_clock", s.repeatInterval_clock)
            obj.put("startDate_clock", s.startDate_clock)
            obj.put("monthDay1_clock", s.monthDay1_clock)
            obj.put("monthDay2_clock", s.monthDay2_clock)
            obj.put("deleted_clock", s.deleted_clock)
            jsonArray.put(obj)
        }
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { fos ->
            fos.write(jsonArray.toString().toByteArray())
        }
    }

    fun load(context: Context): List<IncomeSource> {
        val file = context.getFileStreamPath(FILE_NAME)
        if (!file.exists()) return emptyList()
        val json = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        if (json.isBlank()) return emptyList()
        val jsonArray = JSONArray(json)
        val list = mutableListOf<IncomeSource>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                IncomeSource(
                    id = obj.getInt("id"),
                    source = obj.getString("source"),
                    amount = obj.getDouble("amount"),
                    repeatType = RepeatType.valueOf(obj.getString("repeatType")),
                    repeatInterval = obj.getInt("repeatInterval"),
                    startDate = if (obj.has("startDate")) LocalDate.parse(obj.getString("startDate")) else null,
                    monthDay1 = if (obj.has("monthDay1")) obj.getInt("monthDay1") else null,
                    monthDay2 = if (obj.has("monthDay2")) obj.getInt("monthDay2") else null,
                    deviceId = obj.optString("deviceId", ""),
                    deleted = obj.optBoolean("deleted", false),
                    source_clock = obj.optLong("source_clock", 0L),
                    amount_clock = obj.optLong("amount_clock", 0L),
                    repeatType_clock = obj.optLong("repeatType_clock", 0L),
                    repeatInterval_clock = obj.optLong("repeatInterval_clock", 0L),
                    startDate_clock = obj.optLong("startDate_clock", 0L),
                    monthDay1_clock = obj.optLong("monthDay1_clock", 0L),
                    monthDay2_clock = obj.optLong("monthDay2_clock", 0L),
                    deleted_clock = obj.optLong("deleted_clock", 0L)
                )
            )
        }
        return list
    }
}
