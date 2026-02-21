package com.syncbudget.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object SavingsGoalRepository {

    private const val FILE_NAME = "future_expenditures.json"

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
            obj.put("name_clock", g.name_clock)
            obj.put("targetAmount_clock", g.targetAmount_clock)
            obj.put("targetDate_clock", g.targetDate_clock)
            obj.put("totalSavedSoFar_clock", g.totalSavedSoFar_clock)
            obj.put("contributionPerPeriod_clock", g.contributionPerPeriod_clock)
            obj.put("isPaused_clock", g.isPaused_clock)
            obj.put("deleted_clock", g.deleted_clock)
            jsonArray.put(obj)
        }
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { fos ->
            fos.write(jsonArray.toString().toByteArray())
        }
    }

    fun load(context: Context): List<SavingsGoal> {
        val file = context.getFileStreamPath(FILE_NAME)
        if (!file.exists()) return emptyList()
        val json = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        if (json.isBlank()) return emptyList()
        val jsonArray = JSONArray(json)
        val list = mutableListOf<SavingsGoal>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            // Support legacy field names: "description" → "name", "amount" → "targetAmount"
            val name = if (obj.has("name")) obj.getString("name")
                       else if (obj.has("description")) obj.getString("description")
                       else ""
            val targetAmount = if (obj.has("targetAmount")) obj.getDouble("targetAmount")
                               else if (obj.has("amount")) obj.getDouble("amount")
                               else 0.0
            val targetDate = if (obj.has("targetDate") && !obj.isNull("targetDate")) {
                try { LocalDate.parse(obj.getString("targetDate")) } catch (_: Exception) { null }
            } else null
            list.add(
                SavingsGoal(
                    id = obj.getInt("id"),
                    name = name,
                    targetAmount = targetAmount,
                    targetDate = targetDate,
                    totalSavedSoFar = if (obj.has("totalSavedSoFar")) obj.getDouble("totalSavedSoFar") else 0.0,
                    contributionPerPeriod = if (obj.has("contributionPerPeriod")) obj.getDouble("contributionPerPeriod") else 0.0,
                    isPaused = if (obj.has("isPaused")) obj.getBoolean("isPaused") else false,
                    deviceId = obj.optString("deviceId", ""),
                    deleted = obj.optBoolean("deleted", false),
                    name_clock = obj.optLong("name_clock", 0L),
                    targetAmount_clock = obj.optLong("targetAmount_clock", 0L),
                    targetDate_clock = obj.optLong("targetDate_clock", 0L),
                    totalSavedSoFar_clock = obj.optLong("totalSavedSoFar_clock", 0L),
                    contributionPerPeriod_clock = obj.optLong("contributionPerPeriod_clock", 0L),
                    isPaused_clock = obj.optLong("isPaused_clock", 0L),
                    deleted_clock = obj.optLong("deleted_clock", 0L)
                )
            )
        }
        return list
    }
}
