package com.syncbudget.app.data.sync

import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SharedSettings
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.sync.PeriodLedgerEntry
import org.json.JSONArray
import org.json.JSONObject

object DeltaBuilder {

    /** Add a field to the map only if it is not already present and its clock > 0. */
    private fun ensureField(fields: MutableMap<String, FieldDelta>, key: String, value: Any?, clock: Long) {
        if (key !in fields && clock > 0L) {
            fields[key] = FieldDelta(value, clock)
        }
    }

    fun buildTransactionDelta(txn: Transaction, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (txn.source_clock > lastPushedClock) fields["source"] = FieldDelta(txn.source, txn.source_clock)
        if (txn.description_clock > lastPushedClock) fields["description"] = FieldDelta(txn.description, txn.description_clock)
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
        if (txn.linkedRecurringExpenseId_clock > lastPushedClock) fields["linkedRecurringExpenseId"] = FieldDelta(txn.linkedRecurringExpenseId, txn.linkedRecurringExpenseId_clock)
        if (txn.linkedAmortizationEntryId_clock > lastPushedClock) fields["linkedAmortizationEntryId"] = FieldDelta(txn.linkedAmortizationEntryId, txn.linkedAmortizationEntryId_clock)
        if (txn.linkedIncomeSourceId_clock > lastPushedClock) fields["linkedIncomeSourceId"] = FieldDelta(txn.linkedIncomeSourceId, txn.linkedIncomeSourceId_clock)
        if (txn.amortizationAppliedAmount_clock > lastPushedClock) fields["amortizationAppliedAmount"] = FieldDelta(txn.amortizationAppliedAmount, txn.amortizationAppliedAmount_clock)
        if (txn.linkedRecurringExpenseAmount_clock > lastPushedClock) fields["linkedRecurringExpenseAmount"] = FieldDelta(txn.linkedRecurringExpenseAmount, txn.linkedRecurringExpenseAmount_clock)
        if (txn.linkedIncomeSourceAmount_clock > lastPushedClock) fields["linkedIncomeSourceAmount"] = FieldDelta(txn.linkedIncomeSourceAmount, txn.linkedIncomeSourceAmount_clock)
        if (txn.deleted_clock > lastPushedClock) fields["deleted"] = FieldDelta(txn.deleted, txn.deleted_clock)
        if (txn.deviceId_clock > lastPushedClock) fields["deviceId"] = FieldDelta(txn.deviceId, txn.deviceId_clock)
        if (fields.isEmpty()) return null
        // Piggyback critical fields so the receiving device never creates a
        // skeleton record.  CRDT merge ignores fields whose clocks are lower
        // than the receiver's, so this is safe for existing records.
        ensureField(fields, "source", txn.source, txn.source_clock)
        ensureField(fields, "amount", txn.amount, txn.amount_clock)
        ensureField(fields, "date", txn.date.toString(), txn.date_clock)
        ensureField(fields, "type", txn.type.name, txn.type_clock)
        ensureField(fields, "deviceId", txn.deviceId, txn.deviceId_clock)
        ensureField(fields, "linkedRecurringExpenseId", txn.linkedRecurringExpenseId, txn.linkedRecurringExpenseId_clock)
        ensureField(fields, "linkedAmortizationEntryId", txn.linkedAmortizationEntryId, txn.linkedAmortizationEntryId_clock)
        ensureField(fields, "linkedIncomeSourceId", txn.linkedIncomeSourceId, txn.linkedIncomeSourceId_clock)
        ensureField(fields, "amortizationAppliedAmount", txn.amortizationAppliedAmount, txn.amortizationAppliedAmount_clock)
        ensureField(fields, "linkedRecurringExpenseAmount", txn.linkedRecurringExpenseAmount, txn.linkedRecurringExpenseAmount_clock)
        ensureField(fields, "linkedIncomeSourceAmount", txn.linkedIncomeSourceAmount, txn.linkedIncomeSourceAmount_clock)
        ensureField(fields, "isBudgetIncome", txn.isBudgetIncome, txn.isBudgetIncome_clock)
        if ("categoryAmounts" !in fields && txn.categoryAmounts_clock > 0L) {
            val catJson = JSONArray()
            for (ca in txn.categoryAmounts) {
                val obj = JSONObject()
                obj.put("categoryId", ca.categoryId)
                obj.put("amount", ca.amount)
                catJson.put(obj)
            }
            fields["categoryAmounts"] = FieldDelta(catJson.toString(), txn.categoryAmounts_clock)
        }
        return RecordDelta("transaction", "upsert", txn.id, txn.deviceId, fields)
    }

    fun buildRecurringExpenseDelta(re: RecurringExpense, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (re.source_clock > lastPushedClock) fields["source"] = FieldDelta(re.source, re.source_clock)
        if (re.description_clock > lastPushedClock) fields["description"] = FieldDelta(re.description, re.description_clock)
        if (re.amount_clock > lastPushedClock) fields["amount"] = FieldDelta(re.amount, re.amount_clock)
        if (re.repeatType_clock > lastPushedClock) fields["repeatType"] = FieldDelta(re.repeatType.name, re.repeatType_clock)
        if (re.repeatInterval_clock > lastPushedClock) fields["repeatInterval"] = FieldDelta(re.repeatInterval, re.repeatInterval_clock)
        if (re.startDate_clock > lastPushedClock) fields["startDate"] = FieldDelta(re.startDate?.toString(), re.startDate_clock)
        if (re.monthDay1_clock > lastPushedClock) fields["monthDay1"] = FieldDelta(re.monthDay1, re.monthDay1_clock)
        if (re.monthDay2_clock > lastPushedClock) fields["monthDay2"] = FieldDelta(re.monthDay2, re.monthDay2_clock)
        if (re.deleted_clock > lastPushedClock) fields["deleted"] = FieldDelta(re.deleted, re.deleted_clock)
        if (re.deviceId_clock > lastPushedClock) fields["deviceId"] = FieldDelta(re.deviceId, re.deviceId_clock)
        if (fields.isEmpty()) return null
        ensureField(fields, "source", re.source, re.source_clock)
        ensureField(fields, "amount", re.amount, re.amount_clock)
        ensureField(fields, "deviceId", re.deviceId, re.deviceId_clock)
        return RecordDelta("recurring_expense", "upsert", re.id, re.deviceId, fields)
    }

    fun buildIncomeSourceDelta(src: IncomeSource, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (src.source_clock > lastPushedClock) fields["source"] = FieldDelta(src.source, src.source_clock)
        if (src.description_clock > lastPushedClock) fields["description"] = FieldDelta(src.description, src.description_clock)
        if (src.amount_clock > lastPushedClock) fields["amount"] = FieldDelta(src.amount, src.amount_clock)
        if (src.repeatType_clock > lastPushedClock) fields["repeatType"] = FieldDelta(src.repeatType.name, src.repeatType_clock)
        if (src.repeatInterval_clock > lastPushedClock) fields["repeatInterval"] = FieldDelta(src.repeatInterval, src.repeatInterval_clock)
        if (src.startDate_clock > lastPushedClock) fields["startDate"] = FieldDelta(src.startDate?.toString(), src.startDate_clock)
        if (src.monthDay1_clock > lastPushedClock) fields["monthDay1"] = FieldDelta(src.monthDay1, src.monthDay1_clock)
        if (src.monthDay2_clock > lastPushedClock) fields["monthDay2"] = FieldDelta(src.monthDay2, src.monthDay2_clock)
        if (src.deleted_clock > lastPushedClock) fields["deleted"] = FieldDelta(src.deleted, src.deleted_clock)
        if (src.deviceId_clock > lastPushedClock) fields["deviceId"] = FieldDelta(src.deviceId, src.deviceId_clock)
        if (fields.isEmpty()) return null
        ensureField(fields, "source", src.source, src.source_clock)
        ensureField(fields, "amount", src.amount, src.amount_clock)
        ensureField(fields, "deviceId", src.deviceId, src.deviceId_clock)
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
        if (goal.deviceId_clock > lastPushedClock) fields["deviceId"] = FieldDelta(goal.deviceId, goal.deviceId_clock)
        if (fields.isEmpty()) return null
        ensureField(fields, "name", goal.name, goal.name_clock)
        ensureField(fields, "deviceId", goal.deviceId, goal.deviceId_clock)
        return RecordDelta("savings_goal", "upsert", goal.id, goal.deviceId, fields)
    }

    fun buildAmortizationEntryDelta(entry: AmortizationEntry, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (entry.source_clock > lastPushedClock) fields["source"] = FieldDelta(entry.source, entry.source_clock)
        if (entry.description_clock > lastPushedClock) fields["description"] = FieldDelta(entry.description, entry.description_clock)
        if (entry.amount_clock > lastPushedClock) fields["amount"] = FieldDelta(entry.amount, entry.amount_clock)
        if (entry.totalPeriods_clock > lastPushedClock) fields["totalPeriods"] = FieldDelta(entry.totalPeriods, entry.totalPeriods_clock)
        if (entry.startDate_clock > lastPushedClock) fields["startDate"] = FieldDelta(entry.startDate.toString(), entry.startDate_clock)
        if (entry.deleted_clock > lastPushedClock) fields["deleted"] = FieldDelta(entry.deleted, entry.deleted_clock)
        if (entry.isPaused_clock > lastPushedClock) fields["isPaused"] = FieldDelta(entry.isPaused, entry.isPaused_clock)
        if (entry.deviceId_clock > lastPushedClock) fields["deviceId"] = FieldDelta(entry.deviceId, entry.deviceId_clock)
        if (fields.isEmpty()) return null
        ensureField(fields, "source", entry.source, entry.source_clock)
        ensureField(fields, "amount", entry.amount, entry.amount_clock)
        ensureField(fields, "deviceId", entry.deviceId, entry.deviceId_clock)
        return RecordDelta("amortization_entry", "upsert", entry.id, entry.deviceId, fields)
    }

    fun buildCategoryDelta(cat: Category, lastPushedClock: Long): RecordDelta? {
        val fields = mutableMapOf<String, FieldDelta>()
        if (cat.name_clock > lastPushedClock) fields["name"] = FieldDelta(cat.name, cat.name_clock)
        if (cat.iconName_clock > lastPushedClock) fields["iconName"] = FieldDelta(cat.iconName, cat.iconName_clock)
        if (cat.tag_clock > lastPushedClock) fields["tag"] = FieldDelta(cat.tag, cat.tag_clock)
        if (cat.charted_clock > lastPushedClock) fields["charted"] = FieldDelta(cat.charted, cat.charted_clock)
        if (cat.deleted_clock > lastPushedClock) fields["deleted"] = FieldDelta(cat.deleted, cat.deleted_clock)
        if (cat.deviceId_clock > lastPushedClock) fields["deviceId"] = FieldDelta(cat.deviceId, cat.deviceId_clock)
        if (fields.isEmpty()) return null
        ensureField(fields, "name", cat.name, cat.name_clock)
        ensureField(fields, "iconName", cat.iconName, cat.iconName_clock)
        ensureField(fields, "tag", cat.tag, cat.tag_clock)
        ensureField(fields, "charted", cat.charted, cat.charted_clock)
        ensureField(fields, "deviceId", cat.deviceId, cat.deviceId_clock)
        return RecordDelta("category", "upsert", cat.id, cat.deviceId, fields)
    }

    fun buildPeriodLedgerDelta(entry: PeriodLedgerEntry, lastPushedClock: Long): RecordDelta? {
        if (entry.clock <= lastPushedClock) return null
        val fields = mutableMapOf<String, FieldDelta>()
        fields["periodStartDate"] = FieldDelta(entry.periodStartDate.toString(), entry.clock)
        fields["appliedAmount"] = FieldDelta(entry.appliedAmount, entry.clock)
        fields["clockAtReset"] = FieldDelta(entry.clockAtReset, entry.clock)
        fields["corrected"] = FieldDelta(entry.corrected, entry.clock)
        fields["deviceId"] = FieldDelta(entry.deviceId, entry.clock)
        return RecordDelta("period_ledger", "upsert", entry.id, entry.deviceId, fields)
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
        if (settings.matchPercent_clock > lastPushedClock) fields["matchPercent"] = FieldDelta(settings.matchPercent, settings.matchPercent_clock)
        if (settings.matchDollar_clock > lastPushedClock) fields["matchDollar"] = FieldDelta(settings.matchDollar, settings.matchDollar_clock)
        if (settings.matchChars_clock > lastPushedClock) fields["matchChars"] = FieldDelta(settings.matchChars, settings.matchChars_clock)
        if (settings.showAttribution_clock > lastPushedClock) fields["showAttribution"] = FieldDelta(settings.showAttribution, settings.showAttribution_clock)
        if (settings.availableCash_clock > lastPushedClock) fields["availableCash"] = FieldDelta(settings.availableCash, settings.availableCash_clock)
        if (settings.incomeMode_clock > lastPushedClock) fields["incomeMode"] = FieldDelta(settings.incomeMode, settings.incomeMode_clock)
        if (fields.isEmpty()) return null
        return RecordDelta("shared_settings", "upsert", 0, settings.lastChangedBy, fields)
    }
}
