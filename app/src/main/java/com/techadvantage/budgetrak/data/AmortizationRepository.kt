package com.techadvantage.budgetrak.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object AmortizationRepository {

    private const val FILE_NAME = "amortization_entries.json"
    private const val TAG = "AmortizationRepo"

    fun save(context: Context, entries: List<AmortizationEntry>) {
        val jsonArray = JSONArray()
        for (e in entries) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("source", e.source)
            obj.put("description", e.description)
            obj.put("amount", e.amount)
            obj.put("totalPeriods", e.totalPeriods)
            obj.put("startDate", e.startDate.toString())
            // Sync fields
            obj.put("deviceId", e.deviceId)
            obj.put("deleted", e.deleted)
            obj.put("isPaused", e.isPaused)
            jsonArray.put(obj)
        }
        SafeIO.atomicWriteJson(context, FILE_NAME, jsonArray)
    }

    fun load(context: Context): List<AmortizationEntry> {
        val jsonArray = SafeIO.readJsonArray(context, FILE_NAME)
        val list = mutableListOf<AmortizationEntry>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                val amount = SafeIO.safeDouble(obj.getDouble("amount"))
                val startDate = try { LocalDate.parse(obj.getString("startDate")) } catch (_: Exception) { LocalDate.now() }
                list.add(
                    AmortizationEntry(
                        id = obj.getInt("id"),
                        source = obj.getString("source"),
                        description = obj.optString("description", ""),
                        amount = amount,
                        totalPeriods = obj.getInt("totalPeriods"),
                        startDate = startDate,
                        deviceId = obj.optString("deviceId", ""),
                        deleted = obj.optBoolean("deleted", false),
                        isPaused = obj.optBoolean("isPaused", false)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupt record at index $i: ${e.message}")
            }
        }
        return list
    }
}
