package com.syncbudget.app.data.sync

import android.util.Base64
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.syncbudget.app.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Serializes data classes to/from encrypted Firestore documents.
 *
 * Each record becomes a Firestore doc with:
 *   enc       – Base64(encrypt(JSON of business fields))
 *   deviceId  – plain, for attribution
 *   updatedAt – ServerTimestamp, for LWW ordering
 *   deleted   – plain boolean, for tombstone queries
 *
 * Clock fields are NOT stored in Firestore — they exist only in local JSON
 * for backward compatibility and default to 0L on deserialization.
 */
object EncryptedDocSerializer {

    // ── helpers ─────────────────────────────────────────────────────────

    private fun encrypt(json: JSONObject, key: ByteArray): String {
        val plaintext = json.toString().toByteArray(Charsets.UTF_8)
        val ciphertext = CryptoHelper.encryptWithKey(plaintext, key)
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String, key: ByteArray): JSONObject {
        val ciphertext = Base64.decode(enc, Base64.NO_WRAP)
        val plaintext = CryptoHelper.decryptWithKey(ciphertext, key)
        return JSONObject(String(plaintext, Charsets.UTF_8))
    }

    /** Safe nullable Int read — JSONObject stores numbers as Integer or Long. */
    private fun JSONObject.nullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) getInt(name) else null

    /** Safe nullable String read. */
    private fun JSONObject.nullableString(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null

    private fun buildDoc(enc: String, deviceId: String, deleted: Boolean): Map<String, Any> =
        hashMapOf(
            "enc" to enc,
            "deviceId" to deviceId,
            "updatedAt" to FieldValue.serverTimestamp(),
            "deleted" to deleted
        )

    /** For SharedSettings / PeriodLedger — no deleted field. */
    private fun buildDocNoDeleted(enc: String, deviceId: String): Map<String, Any> =
        hashMapOf(
            "enc" to enc,
            "deviceId" to deviceId,
            "updatedAt" to FieldValue.serverTimestamp()
        )

    // ── Transaction ─────────────────────────────────────────────────────

    fun transactionToDoc(t: Transaction, key: ByteArray, deviceId: String): Map<String, Any> {
        val json = JSONObject().apply {
            put("id", t.id)
            put("type", t.type.name)
            put("date", t.date.toString())
            put("source", t.source)
            put("description", t.description)
            put("amount", t.amount)
            put("isUserCategorized", t.isUserCategorized)
            put("excludeFromBudget", t.excludeFromBudget)
            put("isBudgetIncome", t.isBudgetIncome)
            put("linkedRecurringExpenseId", t.linkedRecurringExpenseId ?: JSONObject.NULL)
            put("linkedAmortizationEntryId", t.linkedAmortizationEntryId ?: JSONObject.NULL)
            put("linkedIncomeSourceId", t.linkedIncomeSourceId ?: JSONObject.NULL)
            put("amortizationAppliedAmount", t.amortizationAppliedAmount)
            put("linkedRecurringExpenseAmount", t.linkedRecurringExpenseAmount)
            put("linkedIncomeSourceAmount", t.linkedIncomeSourceAmount)
            put("linkedSavingsGoalId", t.linkedSavingsGoalId ?: JSONObject.NULL)
            put("linkedSavingsGoalAmount", t.linkedSavingsGoalAmount)
            put("receiptId1", t.receiptId1 ?: JSONObject.NULL)
            put("receiptId2", t.receiptId2 ?: JSONObject.NULL)
            put("receiptId3", t.receiptId3 ?: JSONObject.NULL)
            put("receiptId4", t.receiptId4 ?: JSONObject.NULL)
            put("receiptId5", t.receiptId5 ?: JSONObject.NULL)
            if (t.categoryAmounts.isNotEmpty()) {
                val catArray = JSONArray()
                for (ca in t.categoryAmounts) {
                    catArray.put(JSONObject().apply {
                        put("categoryId", ca.categoryId)
                        put("amount", ca.amount)
                    })
                }
                put("categoryAmounts", catArray)
            }
        }
        return buildDoc(encrypt(json, key), deviceId, t.deleted)
    }

    fun transactionFromDoc(doc: DocumentSnapshot, key: ByteArray): Transaction {
        val json = decrypt(doc.getString("enc")!!, key)
        val catAmounts = if (json.has("categoryAmounts")) {
            val arr = json.getJSONArray("categoryAmounts")
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                CategoryAmount(c.getInt("categoryId"), SafeIO.safeDouble(c.getDouble("amount")))
            }
        } else emptyList()

        return Transaction(
            id = json.getInt("id"),
            type = TransactionType.valueOf(json.getString("type")),
            date = LocalDate.parse(json.getString("date")),
            source = json.optString("source", ""),
            description = json.optString("description", ""),
            amount = SafeIO.safeDouble(json.getDouble("amount")),
            isUserCategorized = json.optBoolean("isUserCategorized", true),
            excludeFromBudget = json.optBoolean("excludeFromBudget", false),
            isBudgetIncome = json.optBoolean("isBudgetIncome", false),
            linkedRecurringExpenseId = json.nullableInt("linkedRecurringExpenseId"),
            linkedAmortizationEntryId = json.nullableInt("linkedAmortizationEntryId"),
            linkedIncomeSourceId = json.nullableInt("linkedIncomeSourceId"),
            amortizationAppliedAmount = SafeIO.safeDouble(json.optDouble("amortizationAppliedAmount", 0.0)),
            linkedRecurringExpenseAmount = SafeIO.safeDouble(json.optDouble("linkedRecurringExpenseAmount", 0.0)),
            linkedIncomeSourceAmount = SafeIO.safeDouble(json.optDouble("linkedIncomeSourceAmount", 0.0)),
            linkedSavingsGoalId = json.nullableInt("linkedSavingsGoalId"),
            linkedSavingsGoalAmount = SafeIO.safeDouble(json.optDouble("linkedSavingsGoalAmount", 0.0)),
            receiptId1 = json.nullableString("receiptId1"),
            receiptId2 = json.nullableString("receiptId2"),
            receiptId3 = json.nullableString("receiptId3"),
            receiptId4 = json.nullableString("receiptId4"),
            receiptId5 = json.nullableString("receiptId5"),
            categoryAmounts = catAmounts,
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    // ── RecurringExpense ────────────────────────────────────────────────

    fun recurringExpenseToDoc(re: RecurringExpense, key: ByteArray, deviceId: String): Map<String, Any> {
        val json = JSONObject().apply {
            put("id", re.id)
            put("source", re.source)
            put("description", re.description)
            put("amount", re.amount)
            put("repeatType", re.repeatType.name)
            put("repeatInterval", re.repeatInterval)
            put("startDate", re.startDate?.toString() ?: JSONObject.NULL)
            put("monthDay1", re.monthDay1 ?: JSONObject.NULL)
            put("monthDay2", re.monthDay2 ?: JSONObject.NULL)
            put("setAsideSoFar", re.setAsideSoFar)
            put("isAccelerated", re.isAccelerated)
        }
        return buildDoc(encrypt(json, key), deviceId, re.deleted)
    }

    fun recurringExpenseFromDoc(doc: DocumentSnapshot, key: ByteArray): RecurringExpense {
        val json = decrypt(doc.getString("enc")!!, key)
        return RecurringExpense(
            id = json.getInt("id"),
            source = json.optString("source", ""),
            description = json.optString("description", ""),
            amount = SafeIO.safeDouble(json.getDouble("amount")),
            repeatType = try { RepeatType.valueOf(json.getString("repeatType")) } catch (_: Exception) { RepeatType.MONTHS },
            repeatInterval = json.optInt("repeatInterval", 1),
            startDate = json.nullableString("startDate")?.let { LocalDate.parse(it) },
            monthDay1 = json.nullableInt("monthDay1"),
            monthDay2 = json.nullableInt("monthDay2"),
            setAsideSoFar = SafeIO.safeDouble(json.optDouble("setAsideSoFar", 0.0)),
            isAccelerated = json.optBoolean("isAccelerated", false),
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    // ── IncomeSource ────────────────────────────────────────────────────

    fun incomeSourceToDoc(src: IncomeSource, key: ByteArray, deviceId: String): Map<String, Any> {
        val json = JSONObject().apply {
            put("id", src.id)
            put("source", src.source)
            put("description", src.description)
            put("amount", src.amount)
            put("repeatType", src.repeatType.name)
            put("repeatInterval", src.repeatInterval)
            put("startDate", src.startDate?.toString() ?: JSONObject.NULL)
            put("monthDay1", src.monthDay1 ?: JSONObject.NULL)
            put("monthDay2", src.monthDay2 ?: JSONObject.NULL)
        }
        return buildDoc(encrypt(json, key), deviceId, src.deleted)
    }

    fun incomeSourceFromDoc(doc: DocumentSnapshot, key: ByteArray): IncomeSource {
        val json = decrypt(doc.getString("enc")!!, key)
        return IncomeSource(
            id = json.getInt("id"),
            source = json.optString("source", ""),
            description = json.optString("description", ""),
            amount = SafeIO.safeDouble(json.getDouble("amount")),
            repeatType = try { RepeatType.valueOf(json.getString("repeatType")) } catch (_: Exception) { RepeatType.MONTHS },
            repeatInterval = json.optInt("repeatInterval", 1),
            startDate = json.nullableString("startDate")?.let { LocalDate.parse(it) },
            monthDay1 = json.nullableInt("monthDay1"),
            monthDay2 = json.nullableInt("monthDay2"),
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    // ── SavingsGoal ─────────────────────────────────────────────────────

    fun savingsGoalToDoc(sg: SavingsGoal, key: ByteArray, deviceId: String): Map<String, Any> {
        val json = JSONObject().apply {
            put("id", sg.id)
            put("name", sg.name)
            put("targetAmount", sg.targetAmount)
            put("targetDate", sg.targetDate?.toString() ?: JSONObject.NULL)
            put("totalSavedSoFar", sg.totalSavedSoFar)
            put("contributionPerPeriod", sg.contributionPerPeriod)
            put("isPaused", sg.isPaused)
        }
        return buildDoc(encrypt(json, key), deviceId, sg.deleted)
    }

    fun savingsGoalFromDoc(doc: DocumentSnapshot, key: ByteArray): SavingsGoal {
        val json = decrypt(doc.getString("enc")!!, key)
        return SavingsGoal(
            id = json.getInt("id"),
            name = json.optString("name", ""),
            targetAmount = SafeIO.safeDouble(json.getDouble("targetAmount")),
            targetDate = json.nullableString("targetDate")?.let { LocalDate.parse(it) },
            totalSavedSoFar = SafeIO.safeDouble(json.optDouble("totalSavedSoFar", 0.0)),
            contributionPerPeriod = SafeIO.safeDouble(json.optDouble("contributionPerPeriod", 0.0)),
            isPaused = json.optBoolean("isPaused", false),
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    // ── AmortizationEntry ───────────────────────────────────────────────

    fun amortizationEntryToDoc(ae: AmortizationEntry, key: ByteArray, deviceId: String): Map<String, Any> {
        val json = JSONObject().apply {
            put("id", ae.id)
            put("source", ae.source)
            put("description", ae.description)
            put("amount", ae.amount)
            put("totalPeriods", ae.totalPeriods)
            put("startDate", ae.startDate.toString())
            put("isPaused", ae.isPaused)
        }
        return buildDoc(encrypt(json, key), deviceId, ae.deleted)
    }

    fun amortizationEntryFromDoc(doc: DocumentSnapshot, key: ByteArray): AmortizationEntry {
        val json = decrypt(doc.getString("enc")!!, key)
        return AmortizationEntry(
            id = json.getInt("id"),
            source = json.optString("source", ""),
            description = json.optString("description", ""),
            amount = SafeIO.safeDouble(json.getDouble("amount")),
            totalPeriods = json.getInt("totalPeriods"),
            startDate = LocalDate.parse(json.getString("startDate")),
            isPaused = json.optBoolean("isPaused", false),
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    // ── Category ────────────────────────────────────────────────────────

    fun categoryToDoc(cat: Category, key: ByteArray, deviceId: String): Map<String, Any> {
        val json = JSONObject().apply {
            put("id", cat.id)
            put("name", cat.name)
            put("iconName", cat.iconName)
            put("tag", cat.tag)
            put("charted", cat.charted)
            put("widgetVisible", cat.widgetVisible)
        }
        return buildDoc(encrypt(json, key), deviceId, cat.deleted)
    }

    fun categoryFromDoc(doc: DocumentSnapshot, key: ByteArray): Category {
        val json = decrypt(doc.getString("enc")!!, key)
        return Category(
            id = json.getInt("id"),
            name = json.optString("name", ""),
            iconName = json.optString("iconName", ""),
            tag = json.optString("tag", ""),
            charted = json.optBoolean("charted", true),
            widgetVisible = json.optBoolean("widgetVisible", true),
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    // ── PeriodLedgerEntry ───────────────────────────────────────────────

    fun periodLedgerToDoc(ple: PeriodLedgerEntry, key: ByteArray, deviceId: String): Map<String, Any> {
        val json = JSONObject().apply {
            put("periodStartDate", ple.periodStartDate.toString())
            put("appliedAmount", ple.appliedAmount)
        }
        return buildDocNoDeleted(encrypt(json, key), deviceId)
    }

    fun periodLedgerFromDoc(doc: DocumentSnapshot, key: ByteArray): PeriodLedgerEntry {
        val json = decrypt(doc.getString("enc")!!, key)
        return PeriodLedgerEntry(
            periodStartDate = try { LocalDateTime.parse(json.getString("periodStartDate")) } catch (_: Exception) { LocalDateTime.now() },
            appliedAmount = SafeIO.safeDouble(json.getDouble("appliedAmount")),
            deviceId = doc.getString("deviceId") ?: ""
        )
    }

    // ── SharedSettings ──────────────────────────────────────────────────

    fun sharedSettingsToDoc(ss: SharedSettings, key: ByteArray, deviceId: String): Map<String, Any> {
        val json = JSONObject().apply {
            put("currency", ss.currency)
            put("budgetPeriod", ss.budgetPeriod)
            put("budgetStartDate", ss.budgetStartDate ?: JSONObject.NULL)
            put("isManualBudgetEnabled", ss.isManualBudgetEnabled)
            put("manualBudgetAmount", ss.manualBudgetAmount)
            put("weekStartSunday", ss.weekStartSunday)
            put("resetDayOfWeek", ss.resetDayOfWeek)
            put("resetDayOfMonth", ss.resetDayOfMonth)
            put("resetHour", ss.resetHour)
            put("familyTimezone", ss.familyTimezone)
            put("matchDays", ss.matchDays)
            put("matchPercent", ss.matchPercent)
            put("matchDollar", ss.matchDollar)
            put("matchChars", ss.matchChars)
            put("showAttribution", ss.showAttribution)
            put("availableCash", ss.availableCash)
            put("incomeMode", ss.incomeMode)
            put("deviceRoster", ss.deviceRoster)
            put("receiptPruneAgeDays", ss.receiptPruneAgeDays ?: JSONObject.NULL)
            put("lastChangedBy", ss.lastChangedBy)
        }
        return buildDocNoDeleted(encrypt(json, key), deviceId)
    }

    fun sharedSettingsFromDoc(doc: DocumentSnapshot, key: ByteArray): SharedSettings {
        val json = decrypt(doc.getString("enc")!!, key)
        return SharedSettings(
            currency = json.optString("currency", "$"),
            budgetPeriod = json.optString("budgetPeriod", "DAILY"),
            budgetStartDate = json.nullableString("budgetStartDate"),
            isManualBudgetEnabled = json.optBoolean("isManualBudgetEnabled", false),
            manualBudgetAmount = SafeIO.safeDouble(json.optDouble("manualBudgetAmount", 0.0)),
            weekStartSunday = json.optBoolean("weekStartSunday", true),
            resetDayOfWeek = json.optInt("resetDayOfWeek", 7),
            resetDayOfMonth = json.optInt("resetDayOfMonth", 1),
            resetHour = json.optInt("resetHour", 0),
            familyTimezone = json.optString("familyTimezone", ""),
            matchDays = json.optInt("matchDays", 7),
            matchPercent = SafeIO.safeDouble(json.optDouble("matchPercent", 1.0)),
            matchDollar = json.optInt("matchDollar", 1),
            matchChars = json.optInt("matchChars", 5),
            showAttribution = json.optBoolean("showAttribution", false),
            availableCash = SafeIO.safeDouble(json.optDouble("availableCash", 0.0)),
            incomeMode = json.optString("incomeMode", "FIXED"),
            deviceRoster = json.optString("deviceRoster", "{}"),
            receiptPruneAgeDays = json.nullableInt("receiptPruneAgeDays"),
            lastChangedBy = json.optString("lastChangedBy", "")
        )
    }

    // ── Document ID helpers ─────────────────────────────────────────────

    fun docId(record: Any): String = when (record) {
        is Transaction -> record.id.toString()
        is RecurringExpense -> record.id.toString()
        is IncomeSource -> record.id.toString()
        is SavingsGoal -> record.id.toString()
        is AmortizationEntry -> record.id.toString()
        is Category -> record.id.toString()
        is PeriodLedgerEntry -> record.id.toString()
        else -> throw IllegalArgumentException("Unknown record type: ${record::class}")
    }

    fun collectionName(record: Any): String = when (record) {
        is Transaction -> COLLECTION_TRANSACTIONS
        is RecurringExpense -> COLLECTION_RECURRING_EXPENSES
        is IncomeSource -> COLLECTION_INCOME_SOURCES
        is SavingsGoal -> COLLECTION_SAVINGS_GOALS
        is AmortizationEntry -> COLLECTION_AMORTIZATION_ENTRIES
        is Category -> COLLECTION_CATEGORIES
        is PeriodLedgerEntry -> COLLECTION_PERIOD_LEDGER
        is SharedSettings -> COLLECTION_SHARED_SETTINGS
        else -> throw IllegalArgumentException("Unknown record type: ${record::class}")
    }

    fun toDoc(record: Any, key: ByteArray, deviceId: String): Map<String, Any> = when (record) {
        is Transaction -> transactionToDoc(record, key, deviceId)
        is RecurringExpense -> recurringExpenseToDoc(record, key, deviceId)
        is IncomeSource -> incomeSourceToDoc(record, key, deviceId)
        is SavingsGoal -> savingsGoalToDoc(record, key, deviceId)
        is AmortizationEntry -> amortizationEntryToDoc(record, key, deviceId)
        is Category -> categoryToDoc(record, key, deviceId)
        is PeriodLedgerEntry -> periodLedgerToDoc(record, key, deviceId)
        is SharedSettings -> sharedSettingsToDoc(record, key, deviceId)
        else -> throw IllegalArgumentException("Unknown record type: ${record::class}")
    }

    // ── Collection name constants ───────────────────────────────────────

    const val COLLECTION_TRANSACTIONS = "transactions"
    const val COLLECTION_RECURRING_EXPENSES = "recurringExpenses"
    const val COLLECTION_INCOME_SOURCES = "incomeSources"
    const val COLLECTION_SAVINGS_GOALS = "savingsGoals"
    const val COLLECTION_AMORTIZATION_ENTRIES = "amortizationEntries"
    const val COLLECTION_CATEGORIES = "categories"
    const val COLLECTION_PERIOD_LEDGER = "periodLedger"
    const val COLLECTION_SHARED_SETTINGS = "sharedSettings"
    const val SHARED_SETTINGS_DOC_ID = "current"

    val ALL_COLLECTIONS = listOf(
        COLLECTION_TRANSACTIONS,
        COLLECTION_RECURRING_EXPENSES,
        COLLECTION_INCOME_SOURCES,
        COLLECTION_SAVINGS_GOALS,
        COLLECTION_AMORTIZATION_ENTRIES,
        COLLECTION_CATEGORIES,
        COLLECTION_PERIOD_LEDGER
    )
}
