package com.syncbudget.app.data

import android.content.Context
import org.json.JSONObject

object SharedSettingsRepository {

    private const val FILE_NAME = "shared_settings.json"

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
        json.put("matchPercent", settings.matchPercent.toDouble())
        json.put("matchDollar", settings.matchDollar)
        json.put("matchChars", settings.matchChars)
        json.put("showAttribution", settings.showAttribution)
        json.put("lastChangedBy", settings.lastChangedBy)
        // Clocks
        json.put("currency_clock", settings.currency_clock)
        json.put("budgetPeriod_clock", settings.budgetPeriod_clock)
        json.put("budgetStartDate_clock", settings.budgetStartDate_clock)
        json.put("isManualBudgetEnabled_clock", settings.isManualBudgetEnabled_clock)
        json.put("manualBudgetAmount_clock", settings.manualBudgetAmount_clock)
        json.put("weekStartSunday_clock", settings.weekStartSunday_clock)
        json.put("resetDayOfWeek_clock", settings.resetDayOfWeek_clock)
        json.put("resetDayOfMonth_clock", settings.resetDayOfMonth_clock)
        json.put("resetHour_clock", settings.resetHour_clock)
        json.put("familyTimezone_clock", settings.familyTimezone_clock)
        json.put("matchDays_clock", settings.matchDays_clock)
        json.put("matchPercent_clock", settings.matchPercent_clock)
        json.put("matchDollar_clock", settings.matchDollar_clock)
        json.put("matchChars_clock", settings.matchChars_clock)
        json.put("showAttribution_clock", settings.showAttribution_clock)

        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
            it.write(json.toString().toByteArray())
        }
    }

    fun load(context: Context): SharedSettings {
        val file = context.getFileStreamPath(FILE_NAME)
        if (!file.exists()) return SharedSettings()
        return try {
            val text = file.readText()
            val json = JSONObject(text)
            SharedSettings(
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
                matchPercent = json.optDouble("matchPercent", 1.0).toFloat(),
                matchDollar = json.optInt("matchDollar", 1),
                matchChars = json.optInt("matchChars", 5),
                showAttribution = json.optBoolean("showAttribution", false),
                lastChangedBy = json.optString("lastChangedBy", ""),
                currency_clock = json.optLong("currency_clock", 0L),
                budgetPeriod_clock = json.optLong("budgetPeriod_clock", 0L),
                budgetStartDate_clock = json.optLong("budgetStartDate_clock", 0L),
                isManualBudgetEnabled_clock = json.optLong("isManualBudgetEnabled_clock", 0L),
                manualBudgetAmount_clock = json.optLong("manualBudgetAmount_clock", 0L),
                weekStartSunday_clock = json.optLong("weekStartSunday_clock", 0L),
                resetDayOfWeek_clock = json.optLong("resetDayOfWeek_clock", 0L),
                resetDayOfMonth_clock = json.optLong("resetDayOfMonth_clock", 0L),
                resetHour_clock = json.optLong("resetHour_clock", 0L),
                familyTimezone_clock = json.optLong("familyTimezone_clock", 0L),
                matchDays_clock = json.optLong("matchDays_clock", 0L),
                matchPercent_clock = json.optLong("matchPercent_clock", 0L),
                matchDollar_clock = json.optLong("matchDollar_clock", 0L),
                matchChars_clock = json.optLong("matchChars_clock", 0L),
                showAttribution_clock = json.optLong("showAttribution_clock", 0L)
            )
        } catch (_: Exception) {
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
        json.put("matchPercent", settings.matchPercent.toDouble())
        json.put("matchDollar", settings.matchDollar)
        json.put("matchChars", settings.matchChars)
        json.put("showAttribution", settings.showAttribution)
        json.put("lastChangedBy", settings.lastChangedBy)
        json.put("currency_clock", settings.currency_clock)
        json.put("budgetPeriod_clock", settings.budgetPeriod_clock)
        json.put("budgetStartDate_clock", settings.budgetStartDate_clock)
        json.put("isManualBudgetEnabled_clock", settings.isManualBudgetEnabled_clock)
        json.put("manualBudgetAmount_clock", settings.manualBudgetAmount_clock)
        json.put("weekStartSunday_clock", settings.weekStartSunday_clock)
        json.put("resetDayOfWeek_clock", settings.resetDayOfWeek_clock)
        json.put("resetDayOfMonth_clock", settings.resetDayOfMonth_clock)
        json.put("resetHour_clock", settings.resetHour_clock)
        json.put("familyTimezone_clock", settings.familyTimezone_clock)
        json.put("matchDays_clock", settings.matchDays_clock)
        json.put("matchPercent_clock", settings.matchPercent_clock)
        json.put("matchDollar_clock", settings.matchDollar_clock)
        json.put("matchChars_clock", settings.matchChars_clock)
        json.put("showAttribution_clock", settings.showAttribution_clock)
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
            matchPercent = json.optDouble("matchPercent", 1.0).toFloat(),
            matchDollar = json.optInt("matchDollar", 1),
            matchChars = json.optInt("matchChars", 5),
            showAttribution = json.optBoolean("showAttribution", false),
            lastChangedBy = json.optString("lastChangedBy", ""),
            currency_clock = json.optLong("currency_clock", 0L),
            budgetPeriod_clock = json.optLong("budgetPeriod_clock", 0L),
            budgetStartDate_clock = json.optLong("budgetStartDate_clock", 0L),
            isManualBudgetEnabled_clock = json.optLong("isManualBudgetEnabled_clock", 0L),
            manualBudgetAmount_clock = json.optLong("manualBudgetAmount_clock", 0L),
            weekStartSunday_clock = json.optLong("weekStartSunday_clock", 0L),
            resetDayOfWeek_clock = json.optLong("resetDayOfWeek_clock", 0L),
            resetDayOfMonth_clock = json.optLong("resetDayOfMonth_clock", 0L),
            resetHour_clock = json.optLong("resetHour_clock", 0L),
            familyTimezone_clock = json.optLong("familyTimezone_clock", 0L),
            matchDays_clock = json.optLong("matchDays_clock", 0L),
            matchPercent_clock = json.optLong("matchPercent_clock", 0L),
            matchDollar_clock = json.optLong("matchDollar_clock", 0L),
            matchChars_clock = json.optLong("matchChars_clock", 0L),
            showAttribution_clock = json.optLong("showAttribution_clock", 0L)
        )
    }
}
