package com.syncbudget.app.data.sync

import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SharedSettings
import com.syncbudget.app.data.Transaction
import org.json.JSONArray
import org.json.JSONObject

object DeltaBuilder {

    fun buildTransactionDelta(txn: Transaction, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (txn.source_clock > lastPushedClock) fields["source"] = FieldDelta(txn.source, txn.source_clock)
        if (txn.amount_clock > lastPushedClock) fields["amount"] = FieldDelta(txn.amount, txn.amount_clock)
        if (txn.date_clock > lastPushedClock) fields["date"] = FieldDelta(txn.date.toString(), txn.date_clock)
        if (txn.type_clock > lastPushedClock) fields["type"] = FieldDelta(txn.type.name, txn.type_clock)
        if (txn.categoryAmounts_clock > lastPushedClock) {
            val catJson = JSONArray()
            for (ca in txn.categoryAmounts) {
                val obj = JSONObject()
                obj.put("categoryId", ca.categoryId)
                obj.put("amount", ca.amount)
                catJson.put(obj)
            }
            fields["categoryAmounts"] = FieldDelta(catJson.toString(), txn.categoryAmounts_clock)
        }
        if (txn.isUserCategorized_clock > lastPushedClock) fields["isUserCategorized"] = FieldDelta(txn.isUserCategorized, txn.isUserCategorized_clock)
        if (txn.isBudgetIncome_clock > lastPushedClock) fields["isBudgetIncome"] = FieldDelta(txn.isBudgetIncome, txn.isBudgetIncome_clock)
        if (txn.deleted_clock > lastPushedClock) fields["deleted"] = FieldDelta(txn.deleted, txn.deleted_clock)
        if (fields.isEmpty()) return null
        return RecordDelta("transaction", "upsert", txn.id, txn.deviceId, fields)
    }

    fun buildRecurringExpenseDelta(re: RecurringExpense, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (re.source_clock > lastPushedClock) fields["source"] = FieldDelta(re.source, re.source_clock)
        if (re.amount_clock > lastPushedClock) fields["amount"] = FieldDelta(re.amount, re.amount_clock)
        if (re.repeatType_clock > lastPushedClock) fields["repeatType"] = FieldDelta(re.repeatType.name, re.repeatType_clock)
        if (re.repeatInterval_clock > lastPushedClock) fields["repeatInterval"] = FieldDelta(re.repeatInterval, re.repeatInterval_clock)
        if (re.startDate_clock > lastPushedClock) fields["startDate"] = FieldDelta(re.startDate?.toString(), re.startDate_clock)
        if (re.monthDay1_clock > lastPushedClock) fields["monthDay1"] = FieldDelta(re.monthDay1, re.monthDay1_clock)
        if (re.monthDay2_clock > lastPushedClock) fields["monthDay2"] = FieldDelta(re.monthDay2, re.monthDay2_clock)
        if (re.deleted_clock > lastPushedClock) fields["deleted"] = FieldDelta(re.deleted, re.deleted_clock)
        if (fields.isEmpty()) return null
        return RecordDelta("recurring_expense", "upsert", re.id, re.deviceId, fields)
    }

    fun buildIncomeSourceDelta(src: IncomeSource, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (src.source_clock > lastPushedClock) fields["source"] = FieldDelta(src.source, src.source_clock)
        if (src.amount_clock > lastPushedClock) fields["amount"] = FieldDelta(src.amount, src.amount_clock)
        if (src.repeatType_clock > lastPushedClock) fields["repeatType"] = FieldDelta(src.repeatType.name, src.repeatType_clock)
        if (src.repeatInterval_clock > lastPushedClock) fields["repeatInterval"] = FieldDelta(src.repeatInterval, src.repeatInterval_clock)
        if (src.startDate_clock > lastPushedClock) fields["startDate"] = FieldDelta(src.startDate?.toString(), src.startDate_clock)
        if (src.monthDay1_clock > lastPushedClock) fields["monthDay1"] = FieldDelta(src.monthDay1, src.monthDay1_clock)
        if (src.monthDay2_clock > lastPushedClock) fields["monthDay2"] = FieldDelta(src.monthDay2, src.monthDay2_clock)
        if (src.deleted_clock > lastPushedClock) fields["deleted"] = FieldDelta(src.deleted, src.deleted_clock)
        if (fields.isEmpty()) return null
        return RecordDelta("income_source", "upsert", src.id, src.deviceId, fields)
    }

    fun buildSavingsGoalDelta(goal: SavingsGoal, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (goal.name_clock > lastPushedClock) fields["name"] = FieldDelta(goal.name, goal.name_clock)
        if (goal.targetAmount_clock > lastPushedClock) fields["targetAmount"] = FieldDelta(goal.targetAmount, goal.targetAmount_clock)
        if (goal.targetDate_clock > lastPushedClock) fields["targetDate"] = FieldDelta(goal.targetDate?.toString(), goal.targetDate_clock)
        if (goal.totalSavedSoFar_clock > lastPushedClock) fields["totalSavedSoFar"] = FieldDelta(goal.totalSavedSoFar, goal.totalSavedSoFar_clock)
        if (goal.contributionPerPeriod_clock > lastPushedClock) fields["contributionPerPeriod"] = FieldDelta(goal.contributionPerPeriod, goal.contributionPerPeriod_clock)
        if (goal.isPaused_clock > lastPushedClock) fields["isPaused"] = FieldDelta(goal.isPaused, goal.isPaused_clock)
        if (goal.deleted_clock > lastPushedClock) fields["deleted"] = FieldDelta(goal.deleted, goal.deleted_clock)
        if (fields.isEmpty()) return null
        return RecordDelta("savings_goal", "upsert", goal.id, goal.deviceId, fields)
    }

    fun buildAmortizationEntryDelta(entry: AmortizationEntry, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (entry.source_clock > lastPushedClock) fields["source"] = FieldDelta(entry.source, entry.source_clock)
        if (entry.amount_clock > lastPushedClock) fields["amount"] = FieldDelta(entry.amount, entry.amount_clock)
        if (entry.totalPeriods_clock > lastPushedClock) fields["totalPeriods"] = FieldDelta(entry.totalPeriods, entry.totalPeriods_clock)
        if (entry.startDate_clock > lastPushedClock) fields["startDate"] = FieldDelta(entry.startDate.toString(), entry.startDate_clock)
        if (entry.deleted_clock > lastPushedClock) fields["deleted"] = FieldDelta(entry.deleted, entry.deleted_clock)
        if (entry.isPaused_clock > lastPushedClock) fields["isPaused"] = FieldDelta(entry.isPaused, entry.isPaused_clock)
        if (fields.isEmpty()) return null
        return RecordDelta("amortization_entry", "upsert", entry.id, entry.deviceId, fields)
    }

    fun buildCategoryDelta(cat: Category, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (cat.name_clock > lastPushedClock) fields["name"] = FieldDelta(cat.name, cat.name_clock)
        if (cat.iconName_clock > lastPushedClock) fields["iconName"] = FieldDelta(cat.iconName, cat.iconName_clock)
        if (cat.deleted_clock > lastPushedClock) fields["deleted"] = FieldDelta(cat.deleted, cat.deleted_clock)
        if (cat.tag.isNotEmpty()) fields["tag"] = FieldDelta(cat.tag, 0L)
        if (fields.isEmpty()) return null
        return RecordDelta("category", "upsert", cat.id, cat.deviceId, fields)
    }

    fun buildSharedSettingsDelta(settings: SharedSettings, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (settings.currency_clock > lastPushedClock) fields["currency"] = FieldDelta(settings.currency, settings.currency_clock)
        if (settings.budgetPeriod_clock > lastPushedClock) fields["budgetPeriod"] = FieldDelta(settings.budgetPeriod, settings.budgetPeriod_clock)
        if (settings.budgetStartDate_clock > lastPushedClock) fields["budgetStartDate"] = FieldDelta(settings.budgetStartDate, settings.budgetStartDate_clock)
        if (settings.isManualBudgetEnabled_clock > lastPushedClock) fields["isManualBudgetEnabled"] = FieldDelta(settings.isManualBudgetEnabled, settings.isManualBudgetEnabled_clock)
        if (settings.manualBudgetAmount_clock > lastPushedClock) fields["manualBudgetAmount"] = FieldDelta(settings.manualBudgetAmount, settings.manualBudgetAmount_clock)
        if (settings.weekStartSunday_clock > lastPushedClock) fields["weekStartSunday"] = FieldDelta(settings.weekStartSunday, settings.weekStartSunday_clock)
        if (settings.resetDayOfWeek_clock > lastPushedClock) fields["resetDayOfWeek"] = FieldDelta(settings.resetDayOfWeek, settings.resetDayOfWeek_clock)
        if (settings.resetDayOfMonth_clock > lastPushedClock) fields["resetDayOfMonth"] = FieldDelta(settings.resetDayOfMonth, settings.resetDayOfMonth_clock)
        if (settings.resetHour_clock > lastPushedClock) fields["resetHour"] = FieldDelta(settings.resetHour, settings.resetHour_clock)
        if (settings.familyTimezone_clock > lastPushedClock) fields["familyTimezone"] = FieldDelta(settings.familyTimezone, settings.familyTimezone_clock)
        if (settings.matchDays_clock > lastPushedClock) fields["matchDays"] = FieldDelta(settings.matchDays, settings.matchDays_clock)
        if (settings.matchPercent_clock > lastPushedClock) fields["matchPercent"] = FieldDelta(settings.matchPercent.toDouble(), settings.matchPercent_clock)
        if (settings.matchDollar_clock > lastPushedClock) fields["matchDollar"] = FieldDelta(settings.matchDollar, settings.matchDollar_clock)
        if (settings.matchChars_clock > lastPushedClock) fields["matchChars"] = FieldDelta(settings.matchChars, settings.matchChars_clock)
        if (settings.showAttribution_clock > lastPushedClock) fields["showAttribution"] = FieldDelta(settings.showAttribution, settings.showAttribution_clock)
        if (fields.isEmpty()) return null
        return RecordDelta("shared_settings", "upsert", 0, settings.lastChangedBy, fields)
    }
}
