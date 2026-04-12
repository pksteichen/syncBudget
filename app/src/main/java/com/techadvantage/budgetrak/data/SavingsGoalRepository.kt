package com.techadvantage.budgetrak.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object SavingsGoalRepository {

    private const val FILE_NAME = "future_expenditures.json"
    private const val TAG = "SavingsGoalRepo"

    fun save(context: Context, goals: List<SavingsGoal>) {
        val jsonArray = JSONArray()
        for (g in goals) {
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("name", g.name)
            obj.put("targetAmount", g.targetAmount)
            if (g.targetDate != null) {
                obj.put("targetDate", g.targetDate.toString())
            }
            obj.put("totalSavedSoFar", g.totalSavedSoFar)
            obj.put("contributionPerPeriod", g.contributionPerPeriod)
            obj.put("isPaused", g.isPaused)
            // Sync fields
            obj.put("deviceId", g.deviceId)
            obj.put("deleted", g.deleted)
            jsonArray.put(obj)
        }
        SafeIO.atomicWriteJson(context, FILE_NAME, jsonArray)
    }

    fun load(context: Context): List<SavingsGoal> {
        val jsonArray = SafeIO.readJsonArray(context, FILE_NAME)
        val list = mutableListOf<SavingsGoal>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                val name = if (obj.has("name")) obj.getString("name") else ""
                val targetAmount = SafeIO.safeDouble(if (obj.has("targetAmount")) obj.getDouble("targetAmount") else 0.0)
                val targetDate = if (obj.has("targetDate") && !obj.isNull("targetDate")) {
                    try { LocalDate.parse(obj.getString("targetDate")) } catch (_: Exception) { null }
                } else null
                list.add(
                    SavingsGoal(
                        id = obj.getInt("id"),
                        name = name,
                        targetAmount = targetAmount,
                        targetDate = targetDate,
                        totalSavedSoFar = SafeIO.safeDouble(if (obj.has("totalSavedSoFar")) obj.getDouble("totalSavedSoFar") else 0.0),
                        contributionPerPeriod = SafeIO.safeDouble(if (obj.has("contributionPerPeriod")) obj.getDouble("contributionPerPeriod") else 0.0),
                        isPaused = if (obj.has("isPaused")) obj.getBoolean("isPaused") else false,
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
