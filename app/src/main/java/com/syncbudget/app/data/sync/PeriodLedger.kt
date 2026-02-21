package com.syncbudget.app.data.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

data class PeriodLedgerEntry(
    val periodStartDate: LocalDateTime,
    val appliedAmount: Double,
    val clockAtReset: Long,
    val corrected: Boolean = false
)

object PeriodLedgerRepository {

    private const val FILE_NAME = "period_ledger.json"

    fun save(context: Context, entries: List<PeriodLedgerEntry>) {
        val jsonArray = JSONArray()
        for (e in entries) {
            val obj = JSONObject()
            obj.put("periodStartDate", e.periodStartDate.toString())
            obj.put("appliedAmount", e.appliedAmount)
            obj.put("clockAtReset", e.clockAtReset)
            obj.put("corrected", e.corrected)
            jsonArray.put(obj)
        }
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { fos ->
            fos.write(jsonArray.toString().toByteArray())
        }
    }

    fun load(context: Context): List<PeriodLedgerEntry> {
        val file = context.getFileStreamPath(FILE_NAME)
        if (!file.exists()) return emptyList()
        val json = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        if (json.isBlank()) return emptyList()
        val jsonArray = JSONArray(json)
        val list = mutableListOf<PeriodLedgerEntry>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                PeriodLedgerEntry(
                    periodStartDate = LocalDateTime.parse(obj.getString("periodStartDate")),
                    appliedAmount = obj.getDouble("appliedAmount"),
                    clockAtReset = obj.optLong("clockAtReset", 0L),
                    corrected = obj.optBoolean("corrected", false)
                )
            )
        }
        return list
    }
}
