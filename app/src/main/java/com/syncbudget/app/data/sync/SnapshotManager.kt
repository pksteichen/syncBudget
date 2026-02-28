package com.syncbudget.app.data.sync

import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.RepeatType
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SharedSettings
import com.syncbudget.app.data.SharedSettingsRepository
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class FullState(
    val transactions: List<Transaction>,
    val recurringExpenses: List<RecurringExpense>,
    val incomeSources: List<IncomeSource>,
    val savingsGoals: List<SavingsGoal>,
    val amortizationEntries: List<AmortizationEntry>,
    val categories: List<Category>,
    val sharedSettings: SharedSettings = SharedSettings()
)

object SnapshotManager {

    fun serializeFullState(
        transactions: List<Transaction>,
        recurringExpenses: List<RecurringExpense>,
        incomeSources: List<IncomeSource>,
        savingsGoals: List<SavingsGoal>,
        amortizationEntries: List<AmortizationEntry>,
        categories: List<Category>,
        sharedSettings: SharedSettings = SharedSettings()
    ): JSONObject {
        val json = JSONObject()

        // Transactions
        val txnArray = JSONArray()
        for (t in transactions) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("type", t.type.name)
            obj.put("date", t.date.toString())
            obj.put("source", t.source)
            obj.put("description", t.description)
            obj.put("amount", t.amount)
            obj.put("isUserCategorized", t.isUserCategorized)
            obj.put("isBudgetIncome", t.isBudgetIncome)
            obj.put("linkedRecurringExpenseId", t.linkedRecurringExpenseId ?: JSONObject.NULL)
            obj.put("linkedAmortizationEntryId", t.linkedAmortizationEntryId ?: JSONObject.NULL)
            if (t.categoryAmounts.isNotEmpty()) {
                val catArray = JSONArray()
                for (ca in t.categoryAmounts) {
                    val catObj = JSONObject()
                    catObj.put("categoryId", ca.categoryId)
                    catObj.put("amount", ca.amount)
                    catArray.put(catObj)
                }
                obj.put("categoryAmounts", catArray)
            }
            obj.put("deviceId", t.deviceId)
            obj.put("deleted", t.deleted)
            obj.put("source_clock", t.source_clock)
            obj.put("description_clock", t.description_clock)
            obj.put("amount_clock", t.amount_clock)
            obj.put("date_clock", t.date_clock)
            obj.put("type_clock", t.type_clock)
            obj.put("categoryAmounts_clock", t.categoryAmounts_clock)
            obj.put("isUserCategorized_clock", t.isUserCategorized_clock)
            obj.put("isBudgetIncome_clock", t.isBudgetIncome_clock)
            obj.put("linkedRecurringExpenseId_clock", t.linkedRecurringExpenseId_clock)
            obj.put("linkedAmortizationEntryId_clock", t.linkedAmortizationEntryId_clock)
            obj.put("deleted_clock", t.deleted_clock)
            obj.put("deviceId_clock", t.deviceId_clock)
            txnArray.put(obj)
        }
        json.put("transactions", txnArray)

        // Recurring expenses
        val reArray = JSONArray()
        for (r in recurringExpenses) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("source", r.source)
            obj.put("description", r.description)
            obj.put("amount", r.amount)
            obj.put("repeatType", r.repeatType.name)
            obj.put("repeatInterval", r.repeatInterval)
            if (r.startDate != null) obj.put("startDate", r.startDate.toString())
            if (r.monthDay1 != null) obj.put("monthDay1", r.monthDay1)
            if (r.monthDay2 != null) obj.put("monthDay2", r.monthDay2)
            obj.put("deviceId", r.deviceId)
            obj.put("deleted", r.deleted)
            obj.put("source_clock", r.source_clock)
            obj.put("description_clock", r.description_clock)
            obj.put("amount_clock", r.amount_clock)
            obj.put("repeatType_clock", r.repeatType_clock)
            obj.put("repeatInterval_clock", r.repeatInterval_clock)
            obj.put("startDate_clock", r.startDate_clock)
            obj.put("monthDay1_clock", r.monthDay1_clock)
            obj.put("monthDay2_clock", r.monthDay2_clock)
            obj.put("deleted_clock", r.deleted_clock)
            obj.put("deviceId_clock", r.deviceId_clock)
            reArray.put(obj)
        }
        json.put("recurringExpenses", reArray)

        // Income sources
        val isArray = JSONArray()
        for (s in incomeSources) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("source", s.source)
            obj.put("description", s.description)
            obj.put("amount", s.amount)
            obj.put("repeatType", s.repeatType.name)
            obj.put("repeatInterval", s.repeatInterval)
            if (s.startDate != null) obj.put("startDate", s.startDate.toString())
            if (s.monthDay1 != null) obj.put("monthDay1", s.monthDay1)
            if (s.monthDay2 != null) obj.put("monthDay2", s.monthDay2)
            obj.put("deviceId", s.deviceId)
            obj.put("deleted", s.deleted)
            obj.put("source_clock", s.source_clock)
            obj.put("description_clock", s.description_clock)
            obj.put("amount_clock", s.amount_clock)
            obj.put("repeatType_clock", s.repeatType_clock)
            obj.put("repeatInterval_clock", s.repeatInterval_clock)
            obj.put("startDate_clock", s.startDate_clock)
            obj.put("monthDay1_clock", s.monthDay1_clock)
            obj.put("monthDay2_clock", s.monthDay2_clock)
            obj.put("deleted_clock", s.deleted_clock)
            obj.put("deviceId_clock", s.deviceId_clock)
            isArray.put(obj)
        }
        json.put("incomeSources", isArray)

        // Savings goals
        val sgArray = JSONArray()
        for (g in savingsGoals) {
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("name", g.name)
            obj.put("targetAmount", g.targetAmount)
            if (g.targetDate != null) obj.put("targetDate", g.targetDate.toString())
            obj.put("totalSavedSoFar", g.totalSavedSoFar)
            obj.put("contributionPerPeriod", g.contributionPerPeriod)
            obj.put("isPaused", g.isPaused)
            obj.put("deviceId", g.deviceId)
            obj.put("deleted", g.deleted)
            obj.put("name_clock", g.name_clock)
            obj.put("targetAmount_clock", g.targetAmount_clock)
            obj.put("targetDate_clock", g.targetDate_clock)
            obj.put("totalSavedSoFar_clock", g.totalSavedSoFar_clock)
            obj.put("contributionPerPeriod_clock", g.contributionPerPeriod_clock)
            obj.put("isPaused_clock", g.isPaused_clock)
            obj.put("deleted_clock", g.deleted_clock)
            obj.put("deviceId_clock", g.deviceId_clock)
            sgArray.put(obj)
        }
        json.put("savingsGoals", sgArray)

        // Amortization entries
        val amArray = JSONArray()
        for (e in amortizationEntries) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("source", e.source)
            obj.put("description", e.description)
            obj.put("amount", e.amount)
            obj.put("totalPeriods", e.totalPeriods)
            obj.put("startDate", e.startDate.toString())
            obj.put("deviceId", e.deviceId)
            obj.put("deleted", e.deleted)
            obj.put("source_clock", e.source_clock)
            obj.put("description_clock", e.description_clock)
            obj.put("amount_clock", e.amount_clock)
            obj.put("totalPeriods_clock", e.totalPeriods_clock)
            obj.put("startDate_clock", e.startDate_clock)
            obj.put("deleted_clock", e.deleted_clock)
            obj.put("isPaused", e.isPaused)
            obj.put("isPaused_clock", e.isPaused_clock)
            obj.put("deviceId_clock", e.deviceId_clock)
            amArray.put(obj)
        }
        json.put("amortizationEntries", amArray)

        // Categories
        val catArray = JSONArray()
        for (c in categories) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("iconName", c.iconName)
            obj.put("tag", c.tag)
            obj.put("deviceId", c.deviceId)
            obj.put("deleted", c.deleted)
            obj.put("name_clock", c.name_clock)
            obj.put("iconName_clock", c.iconName_clock)
            obj.put("tag_clock", c.tag_clock)
            obj.put("deleted_clock", c.deleted_clock)
            obj.put("deviceId_clock", c.deviceId_clock)
            catArray.put(obj)
        }
        json.put("categories", catArray)

        json.put("sharedSettings", SharedSettingsRepository.toJson(sharedSettings))

        return json
    }

    fun deserializeFullState(json: JSONObject): FullState {
        val transactions = if (json.has("transactions")) {
            val arr = json.getJSONArray("transactions")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val categoryAmounts = if (obj.has("categoryAmounts")) {
                    val catArr = obj.getJSONArray("categoryAmounts")
                    (0 until catArr.length()).map { j ->
                        val catObj = catArr.getJSONObject(j)
                        CategoryAmount(catObj.getInt("categoryId"), catObj.getDouble("amount"))
                    }
                } else emptyList()
                Transaction(
                    id = obj.getInt("id"),
                    type = try { TransactionType.valueOf(obj.getString("type")) } catch (_: Exception) { TransactionType.EXPENSE },
                    date = try { LocalDate.parse(obj.getString("date")) } catch (_: Exception) { LocalDate.now() },
                    source = obj.getString("source"),
                    description = obj.optString("description", ""),
                    categoryAmounts = categoryAmounts,
                    amount = obj.getDouble("amount"),
                    isUserCategorized = if (obj.has("isUserCategorized")) obj.getBoolean("isUserCategorized") else true,
                    isBudgetIncome = if (obj.has("isBudgetIncome")) obj.getBoolean("isBudgetIncome") else false,
                    linkedRecurringExpenseId = if (obj.has("linkedRecurringExpenseId") && !obj.isNull("linkedRecurringExpenseId")) obj.getInt("linkedRecurringExpenseId") else null,
                    linkedAmortizationEntryId = if (obj.has("linkedAmortizationEntryId") && !obj.isNull("linkedAmortizationEntryId")) obj.getInt("linkedAmortizationEntryId") else null,
                    deviceId = obj.optString("deviceId", ""),
                    deleted = obj.optBoolean("deleted", false),
                    source_clock = obj.optLong("source_clock", 0L),
                    description_clock = obj.optLong("description_clock", 0L),
                    amount_clock = obj.optLong("amount_clock", 0L),
                    date_clock = obj.optLong("date_clock", 0L),
                    type_clock = obj.optLong("type_clock", 0L),
                    categoryAmounts_clock = obj.optLong("categoryAmounts_clock", 0L),
                    isUserCategorized_clock = obj.optLong("isUserCategorized_clock", 0L),
                    isBudgetIncome_clock = obj.optLong("isBudgetIncome_clock", 0L),
                    linkedRecurringExpenseId_clock = obj.optLong("linkedRecurringExpenseId_clock", 0L),
                    linkedAmortizationEntryId_clock = obj.optLong("linkedAmortizationEntryId_clock", 0L),
                    deleted_clock = obj.optLong("deleted_clock", 0L),
                    deviceId_clock = obj.optLong("deviceId_clock", 0L)
                )
            }
        } else emptyList()

        val recurringExpenses = if (json.has("recurringExpenses")) {
            val arr = json.getJSONArray("recurringExpenses")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RecurringExpense(
                    id = obj.getInt("id"),
                    source = obj.getString("source"),
                    description = obj.optString("description", ""),
                    amount = obj.getDouble("amount"),
                    repeatType = try { RepeatType.valueOf(obj.getString("repeatType")) } catch (_: Exception) { RepeatType.MONTHS },
                    repeatInterval = obj.optInt("repeatInterval", 1),
                    startDate = if (obj.has("startDate")) try { LocalDate.parse(obj.getString("startDate")) } catch (_: Exception) { null } else null,
                    monthDay1 = if (obj.has("monthDay1")) obj.getInt("monthDay1") else null,
                    monthDay2 = if (obj.has("monthDay2")) obj.getInt("monthDay2") else null,
                    deviceId = obj.optString("deviceId", ""),
                    deleted = obj.optBoolean("deleted", false),
                    source_clock = obj.optLong("source_clock", 0L),
                    description_clock = obj.optLong("description_clock", 0L),
                    amount_clock = obj.optLong("amount_clock", 0L),
                    repeatType_clock = obj.optLong("repeatType_clock", 0L),
                    repeatInterval_clock = obj.optLong("repeatInterval_clock", 0L),
                    startDate_clock = obj.optLong("startDate_clock", 0L),
                    monthDay1_clock = obj.optLong("monthDay1_clock", 0L),
                    monthDay2_clock = obj.optLong("monthDay2_clock", 0L),
                    deleted_clock = obj.optLong("deleted_clock", 0L),
                    deviceId_clock = obj.optLong("deviceId_clock", 0L)
                )
            }
        } else emptyList()

        val incomeSources = if (json.has("incomeSources")) {
            val arr = json.getJSONArray("incomeSources")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                IncomeSource(
                    id = obj.getInt("id"),
                    source = obj.getString("source"),
                    description = obj.optString("description", ""),
                    amount = obj.getDouble("amount"),
                    repeatType = try { RepeatType.valueOf(obj.getString("repeatType")) } catch (_: Exception) { RepeatType.MONTHS },
                    repeatInterval = obj.optInt("repeatInterval", 1),
                    startDate = if (obj.has("startDate")) try { LocalDate.parse(obj.getString("startDate")) } catch (_: Exception) { null } else null,
                    monthDay1 = if (obj.has("monthDay1")) obj.getInt("monthDay1") else null,
                    monthDay2 = if (obj.has("monthDay2")) obj.getInt("monthDay2") else null,
                    deviceId = obj.optString("deviceId", ""),
                    deleted = obj.optBoolean("deleted", false),
                    source_clock = obj.optLong("source_clock", 0L),
                    description_clock = obj.optLong("description_clock", 0L),
                    amount_clock = obj.optLong("amount_clock", 0L),
                    repeatType_clock = obj.optLong("repeatType_clock", 0L),
                    repeatInterval_clock = obj.optLong("repeatInterval_clock", 0L),
                    startDate_clock = obj.optLong("startDate_clock", 0L),
                    monthDay1_clock = obj.optLong("monthDay1_clock", 0L),
                    monthDay2_clock = obj.optLong("monthDay2_clock", 0L),
                    deleted_clock = obj.optLong("deleted_clock", 0L),
                    deviceId_clock = obj.optLong("deviceId_clock", 0L)
                )
            }
        } else emptyList()

        val savingsGoals = if (json.has("savingsGoals")) {
            val arr = json.getJSONArray("savingsGoals")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val name = if (obj.has("name")) obj.getString("name") else ""
                val targetAmount = if (obj.has("targetAmount")) obj.getDouble("targetAmount") else 0.0
                val targetDate = if (obj.has("targetDate") && !obj.isNull("targetDate")) {
                    try { LocalDate.parse(obj.getString("targetDate")) } catch (_: Exception) { null }
                } else null
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
                    deleted_clock = obj.optLong("deleted_clock", 0L),
                    deviceId_clock = obj.optLong("deviceId_clock", 0L)
                )
            }
        } else emptyList()

        val amortizationEntries = if (json.has("amortizationEntries")) {
            val arr = json.getJSONArray("amortizationEntries")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                AmortizationEntry(
                    id = obj.getInt("id"),
                    source = obj.getString("source"),
                    description = obj.optString("description", ""),
                    amount = obj.getDouble("amount"),
                    totalPeriods = obj.getInt("totalPeriods"),
                    startDate = try { LocalDate.parse(obj.getString("startDate")) } catch (_: Exception) { LocalDate.now() },
                    deviceId = obj.optString("deviceId", ""),
                    deleted = obj.optBoolean("deleted", false),
                    isPaused = if (obj.has("isPaused")) obj.getBoolean("isPaused") else false,
                    source_clock = obj.optLong("source_clock", 0L),
                    description_clock = obj.optLong("description_clock", 0L),
                    amount_clock = obj.optLong("amount_clock", 0L),
                    totalPeriods_clock = obj.optLong("totalPeriods_clock", 0L),
                    startDate_clock = obj.optLong("startDate_clock", 0L),
                    deleted_clock = obj.optLong("deleted_clock", 0L),
                    isPaused_clock = obj.optLong("isPaused_clock", 0L),
                    deviceId_clock = obj.optLong("deviceId_clock", 0L)
                )
            }
        } else emptyList()

        val categories = if (json.has("categories")) {
            val arr = json.getJSONArray("categories")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Category(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    iconName = obj.optString("iconName", "label"),
                    tag = obj.optString("tag", ""),
                    deviceId = obj.optString("deviceId", ""),
                    deleted = obj.optBoolean("deleted", false),
                    name_clock = obj.optLong("name_clock", 0L),
                    iconName_clock = obj.optLong("iconName_clock", 0L),
                    tag_clock = obj.optLong("tag_clock", 0L),
                    deleted_clock = obj.optLong("deleted_clock", 0L),
                    deviceId_clock = obj.optLong("deviceId_clock", 0L)
                )
            }
        } else emptyList()

        val loadedSettings = if (json.has("sharedSettings")) {
            SharedSettingsRepository.fromJson(json.getJSONObject("sharedSettings"))
        } else SharedSettings()

        return FullState(
            transactions = transactions,
            recurringExpenses = recurringExpenses,
            incomeSources = incomeSources,
            savingsGoals = savingsGoals,
            amortizationEntries = amortizationEntries,
            categories = categories,
            sharedSettings = loadedSettings
        )
    }
}
