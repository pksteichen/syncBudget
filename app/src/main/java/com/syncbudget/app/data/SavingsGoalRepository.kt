package com.syncbudget.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SavingsGoalRepository {

    private const val FILE_NAME = "savings_goals.json"

    fun save(context: Context, goals: List<SavingsGoal>) {
        val jsonArray = JSONArray()
        for (g in goals) {
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("name", g.name)
            obj.put("targetAmount", g.targetAmount)
            obj.put("savedSoFar", g.savedSoFar)
            obj.put("contributionPerPeriod", g.contributionPerPeriod)
            obj.put("isPaused", g.isPaused)
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
            list.add(
                SavingsGoal(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    targetAmount = obj.getDouble("targetAmount"),
                    savedSoFar = if (obj.has("savedSoFar")) obj.getDouble("savedSoFar") else 0.0,
                    contributionPerPeriod = if (obj.has("contributionPerPeriod")) obj.getDouble("contributionPerPeriod") else 0.0,
                    isPaused = if (obj.has("isPaused")) obj.getBoolean("isPaused") else false
                )
            )
        }
        return list
    }
}
