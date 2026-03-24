package com.syncbudget.app.data

import android.content.Context
import android.util.Log
import org.json.JSONObject

object SharedSettingsRepository {

    private const val FILE_NAME = "shared_settings.json"
    private const val TAG = "SharedSettingsRepo"

    fun save(context: Context, settings: SharedSettings) {
        val json = JSONObject()
        json.put("currency", settings.currency)
        json.put("budgetPeriod", settings.budgetPeriod)
        json.put("budgetStartDate", settings.budgetStartDate ?: JSONObject.NULL)
        json.put("isManualBudgetEnabled", settings.isManualBudgetEnabled)
        json.put("manualBudgetAmount", settings.manualBudgetAmount)
        json.put("weekStartSunday", settings.weekStartSunday)
        json.put("resetDayOfWeek", settings.resetDayOfWeek)
        json.put("resetDayOfMonth", settings.resetDayOfMonth)
        json.put("resetHour", settings.resetHour)
        json.put("familyTimezone", settings.familyTimezone)
        json.put("matchDays", settings.matchDays)
        json.put("matchPercent", settings.matchPercent)
        json.put("matchDollar", settings.matchDollar)
        json.put("matchChars", settings.matchChars)
        json.put("showAttribution", settings.showAttribution)
        json.put("availableCash", settings.availableCash)
        json.put("incomeMode", settings.incomeMode)
        json.put("deviceRoster", settings.deviceRoster)
        json.put("receiptPruneAgeDays", settings.receiptPruneAgeDays ?: JSONObject.NULL)
        json.put("lastChangedBy", settings.lastChangedBy)

        SafeIO.atomicWriteJson(context, FILE_NAME, json)
    }

    fun load(context: Context): SharedSettings {
        val json = SafeIO.readJsonObject(context, FILE_NAME) ?: return SharedSettings()
        return try {
            SharedSettings(
                currency = json.optString("currency", "$"),
                budgetPeriod = json.optString("budgetPeriod", "DAILY"),
                budgetStartDate = if (json.isNull("budgetStartDate")) null else json.optString("budgetStartDate", null),
                isManualBudgetEnabled = json.optBoolean("isManualBudgetEnabled", false),
                manualBudgetAmount = SafeIO.safeDouble(json.optDouble("manualBudgetAmount", 0.0)),
                weekStartSunday = json.optBoolean("weekStartSunday", true),
                resetDayOfWeek = json.optInt("resetDayOfWeek", 7),
                resetDayOfMonth = json.optInt("resetDayOfMonth", 1),
                resetHour = json.optInt("resetHour", 0),
                familyTimezone = json.optString("familyTimezone", ""),
                matchDays = json.optInt("matchDays", 7),
                matchPercent = SafeIO.safeDouble(json.optDouble("matchPercent", 1.0), 1.0),
                matchDollar = json.optInt("matchDollar", 1),
                matchChars = json.optInt("matchChars", 5),
                showAttribution = json.optBoolean("showAttribution", false),
                availableCash = SafeIO.safeDouble(json.optDouble("availableCash", 0.0)),
                incomeMode = json.optString("incomeMode", "FIXED"),
                deviceRoster = json.optString("deviceRoster", "{}"),
                receiptPruneAgeDays = if (json.has("receiptPruneAgeDays") && !json.isNull("receiptPruneAgeDays")) json.getInt("receiptPruneAgeDays") else null,
                lastChangedBy = json.optString("lastChangedBy", "")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse shared settings: ${e.message}")
            SharedSettings()
        }
    }

    fun toJson(settings: SharedSettings): JSONObject {
        val json = JSONObject()
        json.put("currency", settings.currency)
        json.put("budgetPeriod", settings.budgetPeriod)
        json.put("budgetStartDate", settings.budgetStartDate ?: JSONObject.NULL)
        json.put("isManualBudgetEnabled", settings.isManualBudgetEnabled)
        json.put("manualBudgetAmount", settings.manualBudgetAmount)
        json.put("weekStartSunday", settings.weekStartSunday)
        json.put("resetDayOfWeek", settings.resetDayOfWeek)
        json.put("resetDayOfMonth", settings.resetDayOfMonth)
        json.put("resetHour", settings.resetHour)
        json.put("familyTimezone", settings.familyTimezone)
        json.put("matchDays", settings.matchDays)
        json.put("matchPercent", settings.matchPercent)
        json.put("matchDollar", settings.matchDollar)
        json.put("matchChars", settings.matchChars)
        json.put("showAttribution", settings.showAttribution)
        json.put("availableCash", settings.availableCash)
        json.put("incomeMode", settings.incomeMode)
        json.put("deviceRoster", settings.deviceRoster)
        json.put("receiptPruneAgeDays", settings.receiptPruneAgeDays ?: JSONObject.NULL)
        json.put("lastChangedBy", settings.lastChangedBy)
        return json
    }

    fun fromJson(json: JSONObject): SharedSettings {
        return SharedSettings(
            currency = json.optString("currency", "$"),
            budgetPeriod = json.optString("budgetPeriod", "DAILY"),
            budgetStartDate = if (json.isNull("budgetStartDate")) null else json.optString("budgetStartDate", null),
            isManualBudgetEnabled = json.optBoolean("isManualBudgetEnabled", false),
            manualBudgetAmount = json.optDouble("manualBudgetAmount", 0.0),
            weekStartSunday = json.optBoolean("weekStartSunday", true),
            resetDayOfWeek = json.optInt("resetDayOfWeek", 7),
            resetDayOfMonth = json.optInt("resetDayOfMonth", 1),
            resetHour = json.optInt("resetHour", 0),
            familyTimezone = json.optString("familyTimezone", ""),
            matchDays = json.optInt("matchDays", 7),
            matchPercent = json.optDouble("matchPercent", 1.0),
            matchDollar = json.optInt("matchDollar", 1),
            matchChars = json.optInt("matchChars", 5),
            showAttribution = json.optBoolean("showAttribution", false),
            availableCash = json.optDouble("availableCash", 0.0),
            incomeMode = json.optString("incomeMode", "FIXED"),
            deviceRoster = json.optString("deviceRoster", "{}"),
            receiptPruneAgeDays = if (json.has("receiptPruneAgeDays") && !json.isNull("receiptPruneAgeDays")) json.getInt("receiptPruneAgeDays") else null,
            lastChangedBy = json.optString("lastChangedBy", "")
        )
    }
}
