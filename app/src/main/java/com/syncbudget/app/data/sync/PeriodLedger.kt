package com.syncbudget.app.data.sync

import android.content.Context
import android.util.Log
import com.syncbudget.app.data.SafeIO
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

data class PeriodLedgerEntry(
    val periodStartDate: LocalDateTime,
    val appliedAmount: Double,
    val clockAtReset: Long,
    val corrected: Boolean = false,  // unused — kept for JSON backward compatibility
    // CRDT sync fields
    val deviceId: String = "",
    val clock: Long = 0L  // single creation clock (entries are immutable)
) {
    /** Deterministic ID from date — used for CRDT dedup (same date = same entry) */
    val id: Int get() = periodStartDate.toLocalDate().toEpochDay().toInt()
}

object PeriodLedgerRepository {

    private const val FILE_NAME = "period_ledger.json"
    private const val TAG = "PeriodLedgerRepo"

    /** Dedup by epoch day: keep highest-clock entry per date. */
    fun dedup(entries: List<PeriodLedgerEntry>): List<PeriodLedgerEntry> =
        entries.groupBy { it.id }
            .map { (_, group) -> group.maxByOrNull { it.clock } ?: group.first() }

    fun save(context: Context, entries: List<PeriodLedgerEntry>) {
        // Always dedup before writing — prevents any caller from persisting duplicates
        val clean = dedup(entries)
        val jsonArray = JSONArray()
        for (e in clean) {
            val obj = JSONObject()
            obj.put("periodStartDate", e.periodStartDate.toString())
            obj.put("appliedAmount", e.appliedAmount)
            obj.put("clockAtReset", e.clockAtReset)
            obj.put("corrected", e.corrected)
            obj.put("deviceId", e.deviceId)
            obj.put("clock", e.clock)
            jsonArray.put(obj)
        }
        SafeIO.atomicWriteJson(context, FILE_NAME, jsonArray)
    }

    fun load(context: Context): List<PeriodLedgerEntry> {
        val jsonArray = SafeIO.readJsonArray(context, FILE_NAME)
        val list = mutableListOf<PeriodLedgerEntry>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                val periodStartDate = try { LocalDateTime.parse(obj.getString("periodStartDate")) } catch (_: Exception) { LocalDateTime.now() }
                list.add(
                    PeriodLedgerEntry(
                        periodStartDate = periodStartDate,
                        appliedAmount = SafeIO.safeDouble(obj.getDouble("appliedAmount")),
                        clockAtReset = obj.optLong("clockAtReset", 0L),
                        corrected = obj.optBoolean("corrected", false),
                        deviceId = obj.optString("deviceId", ""),
                        clock = obj.optLong("clock", 0L)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupt record at index $i: ${e.message}")
            }
        }
        // Dedup by date: old code could create multiple entries for the same day
        // (e.g. multiple budget resets). Keep highest-clock entry per epoch day.
        val deduped = dedup(list)
        // Persist deduped list to clean up the file if duplicates were found
        if (deduped.size < list.size) {
            save(context, deduped)
        }
        return deduped
    }
}
