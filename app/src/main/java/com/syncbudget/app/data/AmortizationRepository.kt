package com.syncbudget.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object AmortizationRepository {

    private const val FILE_NAME = "amortization_entries.json"

    fun save(context: Context, entries: List<AmortizationEntry>) {
        val jsonArray = JSONArray()
        for (e in entries) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("source", e.source)
            obj.put("amount", e.amount)
            obj.put("totalPeriods", e.totalPeriods)
            obj.put("startDate", e.startDate.toString())
            jsonArray.put(obj)
        }
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { fos ->
            fos.write(jsonArray.toString().toByteArray())
        }
    }

    fun load(context: Context): List<AmortizationEntry> {
        val file = context.getFileStreamPath(FILE_NAME)
        if (!file.exists()) return emptyList()
        val json = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        if (json.isBlank()) return emptyList()
        val jsonArray = JSONArray(json)
        val list = mutableListOf<AmortizationEntry>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                AmortizationEntry(
                    id = obj.getInt("id"),
                    source = obj.getString("source"),
                    amount = obj.getDouble("amount"),
                    totalPeriods = obj.getInt("totalPeriods"),
                    startDate = LocalDate.parse(obj.getString("startDate"))
                )
            )
        }
        return list
    }
}
