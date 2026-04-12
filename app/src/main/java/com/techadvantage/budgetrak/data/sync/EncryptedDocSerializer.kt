package com.techadvantage.budgetrak.data.sync

import android.util.Base64
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.techadvantage.budgetrak.data.*
import com.techadvantage.budgetrak.data.sync.PeriodLedgerEntry
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Serializes data classes to/from encrypted Firestore documents using
 * per-field encryption.
 *
 * Each business field is individually encrypted and stored as `enc_<fieldName>`.
 * Metadata fields (deviceId, updatedAt, deleted, lastEditBy) are stored in
 * plain text for Firestore queries and conflict resolution.
 *
 * Backward-compatible: `xxxFromDoc` detects old single-blob format (has "enc"
 * key) and falls back to decrypting that blob as JSON.
 */
object EncryptedDocSerializer {

    // ── Per-field encryption helpers ────────────────────────────────────

    /** Encrypt a single string value, returning Base64. */
    fun encryptField(value: String, key: ByteArray): String {
        val plaintext = value.toByteArray(Charsets.UTF_8)
        val ciphertext = CryptoHelper.encryptWithKey(plaintext, key)
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /** Decrypt a Base64-encoded encrypted field value. */
    fun decryptField(enc: String, key: ByteArray): String {
        val ciphertext = Base64.decode(enc, Base64.NO_WRAP)
        val plaintext = CryptoHelper.decryptWithKey(ciphertext, key)
        return String(plaintext, Charsets.UTF_8)
    }

    /** Encrypt a nullable Int — returns null if the value is null. */
    private fun encryptNullableInt(value: Int?, key: ByteArray): String? =
        value?.let { encryptField(it.toString(), key) }

    /** Encrypt a nullable String — returns null if the value is null. */
    private fun encryptNullableString(value: String?, key: ByteArray): String? =
        value?.let { encryptField(it, key) }

    /** Decrypt a nullable encrypted Int field from a doc. */
    private fun DocumentSnapshot.decryptNullableInt(field: String, key: ByteArray): Int? =
        getString(field)?.let { decryptField(it, key).toInt() }

    /** Decrypt a nullable encrypted String field from a doc. */
    private fun DocumentSnapshot.decryptNullableString(field: String, key: ByteArray): String? =
        getString(field)?.let { decryptField(it, key) }

    /** Decrypt a required encrypted String field from a doc. */
    private fun DocumentSnapshot.decryptString(field: String, key: ByteArray, default: String = ""): String =
        getString(field)?.let { decryptField(it, key) } ?: default

    /** Decrypt a required encrypted Int field from a doc. */
    private fun DocumentSnapshot.decryptInt(field: String, key: ByteArray, default: Int = 0): Int =
        getString(field)?.let { decryptField(it, key).toIntOrNull() } ?: default

    /** Decrypt a required encrypted Double field from a doc. */
    private fun DocumentSnapshot.decryptDouble(field: String, key: ByteArray, default: Double = 0.0): Double =
        getString(field)?.let { SafeIO.safeDouble(decryptField(it, key).toDoubleOrNull() ?: default) } ?: default

    /** Decrypt a required encrypted Boolean field from a doc. */
    private fun DocumentSnapshot.decryptBoolean(field: String, key: ByteArray, default: Boolean = false): Boolean =
        getString(field)?.let { decryptField(it, key).toBooleanStrictOrNull() } ?: default

    // ── Old single-blob helpers (backward compat) ───────────────────────

    private fun decryptBlob(enc: String, key: ByteArray): JSONObject {
        val ciphertext = Base64.decode(enc, Base64.NO_WRAP)
        val plaintext = CryptoHelper.decryptWithKey(ciphertext, key)
        return JSONObject(String(plaintext, Charsets.UTF_8))
    }

    private fun JSONObject.nullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) getInt(name) else null

    private fun JSONObject.nullableString(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null

    /** Returns true if the doc uses new per-field format. */
    private fun isPerField(doc: DocumentSnapshot): Boolean =
        doc.contains("enc_id") || doc.contains("enc_periodStartDate") || doc.contains("enc_currency")

    // ── Transaction ─────────────────────────────────────────────────────

    fun transactionToFieldMap(t: Transaction, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "enc_id" to encryptField(t.id.toString(), key),
            "enc_type" to encryptField(t.type.name, key),
            "enc_date" to encryptField(t.date.toString(), key),
            "enc_source" to encryptField(t.source, key),
            "enc_description" to encryptField(t.description, key),
            "enc_amount" to encryptField(t.amount.toString(), key),
            "enc_isUserCategorized" to encryptField(t.isUserCategorized.toString(), key),
            "enc_excludeFromBudget" to encryptField(t.excludeFromBudget.toString(), key),
            "enc_isBudgetIncome" to encryptField(t.isBudgetIncome.toString(), key),
            "enc_amortizationAppliedAmount" to encryptField(t.amortizationAppliedAmount.toString(), key),
            "enc_linkedRecurringExpenseAmount" to encryptField(t.linkedRecurringExpenseAmount.toString(), key),
            "enc_linkedIncomeSourceAmount" to encryptField(t.linkedIncomeSourceAmount.toString(), key),
            "enc_linkedSavingsGoalAmount" to encryptField(t.linkedSavingsGoalAmount.toString(), key),
            "deviceId" to deviceId,
            "updatedAt" to FieldValue.serverTimestamp(),
            "deleted" to t.deleted,
            "lastEditBy" to deviceId
        )
        // Nullable fields — only include if non-null
        encryptNullableInt(t.linkedRecurringExpenseId, key)?.let { map["enc_linkedRecurringExpenseId"] = it }
        encryptNullableInt(t.linkedAmortizationEntryId, key)?.let { map["enc_linkedAmortizationEntryId"] = it }
        encryptNullableInt(t.linkedIncomeSourceId, key)?.let { map["enc_linkedIncomeSourceId"] = it }
        encryptNullableInt(t.linkedSavingsGoalId, key)?.let { map["enc_linkedSavingsGoalId"] = it }
        encryptNullableString(t.receiptId1, key)?.let { map["enc_receiptId1"] = it }
        encryptNullableString(t.receiptId2, key)?.let { map["enc_receiptId2"] = it }
        encryptNullableString(t.receiptId3, key)?.let { map["enc_receiptId3"] = it }
        encryptNullableString(t.receiptId4, key)?.let { map["enc_receiptId4"] = it }
        encryptNullableString(t.receiptId5, key)?.let { map["enc_receiptId5"] = it }
        // categoryAmounts — serialize as JSON array string, encrypt
        if (t.categoryAmounts.isNotEmpty()) {
            val catArray = JSONArray()
            for (ca in t.categoryAmounts) {
                catArray.put(JSONObject().apply {
                    put("categoryId", ca.categoryId)
                    put("amount", ca.amount)
                })
            }
            map["enc_categoryAmounts"] = encryptField(catArray.toString(), key)
        }
        return map
    }

    fun transactionFieldUpdate(changedFields: Set<String>, t: Transaction, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastEditBy" to deviceId
        )
        for (field in changedFields) {
            when (field) {
                "id" -> map["enc_id"] = encryptField(t.id.toString(), key)
                "type" -> map["enc_type"] = encryptField(t.type.name, key)
                "date" -> map["enc_date"] = encryptField(t.date.toString(), key)
                "source" -> map["enc_source"] = encryptField(t.source, key)
                "description" -> map["enc_description"] = encryptField(t.description, key)
                "amount" -> map["enc_amount"] = encryptField(t.amount.toString(), key)
                "isUserCategorized" -> map["enc_isUserCategorized"] = encryptField(t.isUserCategorized.toString(), key)
                "excludeFromBudget" -> map["enc_excludeFromBudget"] = encryptField(t.excludeFromBudget.toString(), key)
                "isBudgetIncome" -> map["enc_isBudgetIncome"] = encryptField(t.isBudgetIncome.toString(), key)
                "linkedRecurringExpenseId" -> map["enc_linkedRecurringExpenseId"] =
                    encryptNullableInt(t.linkedRecurringExpenseId, key) ?: FieldValue.delete()
                "linkedAmortizationEntryId" -> map["enc_linkedAmortizationEntryId"] =
                    encryptNullableInt(t.linkedAmortizationEntryId, key) ?: FieldValue.delete()
                "linkedIncomeSourceId" -> map["enc_linkedIncomeSourceId"] =
                    encryptNullableInt(t.linkedIncomeSourceId, key) ?: FieldValue.delete()
                "amortizationAppliedAmount" -> map["enc_amortizationAppliedAmount"] = encryptField(t.amortizationAppliedAmount.toString(), key)
                "linkedRecurringExpenseAmount" -> map["enc_linkedRecurringExpenseAmount"] = encryptField(t.linkedRecurringExpenseAmount.toString(), key)
                "linkedIncomeSourceAmount" -> map["enc_linkedIncomeSourceAmount"] = encryptField(t.linkedIncomeSourceAmount.toString(), key)
                "linkedSavingsGoalId" -> map["enc_linkedSavingsGoalId"] =
                    encryptNullableInt(t.linkedSavingsGoalId, key) ?: FieldValue.delete()
                "linkedSavingsGoalAmount" -> map["enc_linkedSavingsGoalAmount"] = encryptField(t.linkedSavingsGoalAmount.toString(), key)
                "receiptId1" -> map["enc_receiptId1"] =
                    encryptNullableString(t.receiptId1, key) ?: FieldValue.delete()
                "receiptId2" -> map["enc_receiptId2"] =
                    encryptNullableString(t.receiptId2, key) ?: FieldValue.delete()
                "receiptId3" -> map["enc_receiptId3"] =
                    encryptNullableString(t.receiptId3, key) ?: FieldValue.delete()
                "receiptId4" -> map["enc_receiptId4"] =
                    encryptNullableString(t.receiptId4, key) ?: FieldValue.delete()
                "receiptId5" -> map["enc_receiptId5"] =
                    encryptNullableString(t.receiptId5, key) ?: FieldValue.delete()
                "categoryAmounts" -> {
                    if (t.categoryAmounts.isNotEmpty()) {
                        val catArray = JSONArray()
                        for (ca in t.categoryAmounts) {
                            catArray.put(JSONObject().apply {
                                put("categoryId", ca.categoryId)
                                put("amount", ca.amount)
                            })
                        }
                        map["enc_categoryAmounts"] = encryptField(catArray.toString(), key)
                    } else {
                        map["enc_categoryAmounts"] = FieldValue.delete()
                    }
                }
                "deleted" -> map["deleted"] = t.deleted
            }
        }
        return map
    }

    fun transactionFromDoc(doc: DocumentSnapshot, key: ByteArray): Transaction {
        if (!isPerField(doc)) {
            // Old single-blob format
            val json = decryptBlob(doc.getString("enc")!!, key)
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
        // New per-field format
        val catAmounts = doc.getString("enc_categoryAmounts")?.let { enc ->
            val arr = JSONArray(decryptField(enc, key))
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                CategoryAmount(c.getInt("categoryId"), SafeIO.safeDouble(c.getDouble("amount")))
            }
        } ?: emptyList()

        return Transaction(
            id = doc.decryptInt("enc_id", key),
            type = try { TransactionType.valueOf(doc.decryptString("enc_type", key, "EXPENSE")) } catch (_: Exception) { TransactionType.EXPENSE },
            date = try { LocalDate.parse(doc.decryptString("enc_date", key)) } catch (_: Exception) { LocalDate.now() },
            source = doc.decryptString("enc_source", key),
            description = doc.decryptString("enc_description", key),
            amount = doc.decryptDouble("enc_amount", key),
            isUserCategorized = doc.decryptBoolean("enc_isUserCategorized", key, true),
            excludeFromBudget = doc.decryptBoolean("enc_excludeFromBudget", key, false),
            isBudgetIncome = doc.decryptBoolean("enc_isBudgetIncome", key, false),
            linkedRecurringExpenseId = doc.decryptNullableInt("enc_linkedRecurringExpenseId", key),
            linkedAmortizationEntryId = doc.decryptNullableInt("enc_linkedAmortizationEntryId", key),
            linkedIncomeSourceId = doc.decryptNullableInt("enc_linkedIncomeSourceId", key),
            amortizationAppliedAmount = doc.decryptDouble("enc_amortizationAppliedAmount", key),
            linkedRecurringExpenseAmount = doc.decryptDouble("enc_linkedRecurringExpenseAmount", key),
            linkedIncomeSourceAmount = doc.decryptDouble("enc_linkedIncomeSourceAmount", key),
            linkedSavingsGoalId = doc.decryptNullableInt("enc_linkedSavingsGoalId", key),
            linkedSavingsGoalAmount = doc.decryptDouble("enc_linkedSavingsGoalAmount", key),
            receiptId1 = doc.decryptNullableString("enc_receiptId1", key),
            receiptId2 = doc.decryptNullableString("enc_receiptId2", key),
            receiptId3 = doc.decryptNullableString("enc_receiptId3", key),
            receiptId4 = doc.decryptNullableString("enc_receiptId4", key),
            receiptId5 = doc.decryptNullableString("enc_receiptId5", key),
            categoryAmounts = catAmounts,
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    fun diffTransactionFields(old: Transaction, new: Transaction): Set<String> {
        val changed = mutableSetOf<String>()
        if (old.id != new.id) changed.add("id")
        if (old.type != new.type) changed.add("type")
        if (old.date != new.date) changed.add("date")
        if (old.source != new.source) changed.add("source")
        if (old.description != new.description) changed.add("description")
        if (old.amount != new.amount) changed.add("amount")
        if (old.categoryAmounts != new.categoryAmounts) changed.add("categoryAmounts")
        if (old.isUserCategorized != new.isUserCategorized) changed.add("isUserCategorized")
        if (old.excludeFromBudget != new.excludeFromBudget) changed.add("excludeFromBudget")
        if (old.isBudgetIncome != new.isBudgetIncome) changed.add("isBudgetIncome")
        if (old.linkedRecurringExpenseId != new.linkedRecurringExpenseId) changed.add("linkedRecurringExpenseId")
        if (old.linkedAmortizationEntryId != new.linkedAmortizationEntryId) changed.add("linkedAmortizationEntryId")
        if (old.linkedIncomeSourceId != new.linkedIncomeSourceId) changed.add("linkedIncomeSourceId")
        if (old.amortizationAppliedAmount != new.amortizationAppliedAmount) changed.add("amortizationAppliedAmount")
        if (old.linkedRecurringExpenseAmount != new.linkedRecurringExpenseAmount) changed.add("linkedRecurringExpenseAmount")
        if (old.linkedIncomeSourceAmount != new.linkedIncomeSourceAmount) changed.add("linkedIncomeSourceAmount")
        if (old.linkedSavingsGoalId != new.linkedSavingsGoalId) changed.add("linkedSavingsGoalId")
        if (old.linkedSavingsGoalAmount != new.linkedSavingsGoalAmount) changed.add("linkedSavingsGoalAmount")
        if (old.receiptId1 != new.receiptId1) changed.add("receiptId1")
        if (old.receiptId2 != new.receiptId2) changed.add("receiptId2")
        if (old.receiptId3 != new.receiptId3) changed.add("receiptId3")
        if (old.receiptId4 != new.receiptId4) changed.add("receiptId4")
        if (old.receiptId5 != new.receiptId5) changed.add("receiptId5")
        if (old.deleted != new.deleted) changed.add("deleted")
        return changed
    }

    // ── RecurringExpense ────────────────────────────────────────────────

    fun recurringExpenseToFieldMap(re: RecurringExpense, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "enc_id" to encryptField(re.id.toString(), key),
            "enc_source" to encryptField(re.source, key),
            "enc_description" to encryptField(re.description, key),
            "enc_amount" to encryptField(re.amount.toString(), key),
            "enc_repeatType" to encryptField(re.repeatType.name, key),
            "enc_repeatInterval" to encryptField(re.repeatInterval.toString(), key),
            "enc_setAsideSoFar" to encryptField(re.setAsideSoFar.toString(), key),
            "enc_isAccelerated" to encryptField(re.isAccelerated.toString(), key),
            "deviceId" to deviceId,
            "updatedAt" to FieldValue.serverTimestamp(),
            "deleted" to re.deleted,
            "lastEditBy" to deviceId
        )
        encryptNullableString(re.startDate?.toString(), key)?.let { map["enc_startDate"] = it }
        encryptNullableInt(re.monthDay1, key)?.let { map["enc_monthDay1"] = it }
        encryptNullableInt(re.monthDay2, key)?.let { map["enc_monthDay2"] = it }
        return map
    }

    fun recurringExpenseFieldUpdate(changedFields: Set<String>, re: RecurringExpense, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastEditBy" to deviceId
        )
        for (field in changedFields) {
            when (field) {
                "id" -> map["enc_id"] = encryptField(re.id.toString(), key)
                "source" -> map["enc_source"] = encryptField(re.source, key)
                "description" -> map["enc_description"] = encryptField(re.description, key)
                "amount" -> map["enc_amount"] = encryptField(re.amount.toString(), key)
                "repeatType" -> map["enc_repeatType"] = encryptField(re.repeatType.name, key)
                "repeatInterval" -> map["enc_repeatInterval"] = encryptField(re.repeatInterval.toString(), key)
                "startDate" -> map["enc_startDate"] =
                    encryptNullableString(re.startDate?.toString(), key) ?: FieldValue.delete()
                "monthDay1" -> map["enc_monthDay1"] =
                    encryptNullableInt(re.monthDay1, key) ?: FieldValue.delete()
                "monthDay2" -> map["enc_monthDay2"] =
                    encryptNullableInt(re.monthDay2, key) ?: FieldValue.delete()
                "setAsideSoFar" -> map["enc_setAsideSoFar"] = encryptField(re.setAsideSoFar.toString(), key)
                "isAccelerated" -> map["enc_isAccelerated"] = encryptField(re.isAccelerated.toString(), key)
                "deleted" -> map["deleted"] = re.deleted
            }
        }
        return map
    }

    fun recurringExpenseFromDoc(doc: DocumentSnapshot, key: ByteArray): RecurringExpense {
        if (!isPerField(doc)) {
            val json = decryptBlob(doc.getString("enc")!!, key)
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
        return RecurringExpense(
            id = doc.decryptInt("enc_id", key),
            source = doc.decryptString("enc_source", key),
            description = doc.decryptString("enc_description", key),
            amount = doc.decryptDouble("enc_amount", key),
            repeatType = try { RepeatType.valueOf(doc.decryptString("enc_repeatType", key, "MONTHS")) } catch (_: Exception) { RepeatType.MONTHS },
            repeatInterval = doc.decryptInt("enc_repeatInterval", key, 1),
            startDate = doc.decryptNullableString("enc_startDate", key)?.let { LocalDate.parse(it) },
            monthDay1 = doc.decryptNullableInt("enc_monthDay1", key),
            monthDay2 = doc.decryptNullableInt("enc_monthDay2", key),
            setAsideSoFar = doc.decryptDouble("enc_setAsideSoFar", key),
            isAccelerated = doc.decryptBoolean("enc_isAccelerated", key, false),
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    fun diffRecurringExpenseFields(old: RecurringExpense, new: RecurringExpense): Set<String> {
        val changed = mutableSetOf<String>()
        if (old.id != new.id) changed.add("id")
        if (old.source != new.source) changed.add("source")
        if (old.description != new.description) changed.add("description")
        if (old.amount != new.amount) changed.add("amount")
        if (old.repeatType != new.repeatType) changed.add("repeatType")
        if (old.repeatInterval != new.repeatInterval) changed.add("repeatInterval")
        if (old.startDate != new.startDate) changed.add("startDate")
        if (old.monthDay1 != new.monthDay1) changed.add("monthDay1")
        if (old.monthDay2 != new.monthDay2) changed.add("monthDay2")
        if (old.setAsideSoFar != new.setAsideSoFar) changed.add("setAsideSoFar")
        if (old.isAccelerated != new.isAccelerated) changed.add("isAccelerated")
        if (old.deleted != new.deleted) changed.add("deleted")
        return changed
    }

    // ── IncomeSource ────────────────────────────────────────────────────

    fun incomeSourceToFieldMap(src: IncomeSource, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "enc_id" to encryptField(src.id.toString(), key),
            "enc_source" to encryptField(src.source, key),
            "enc_description" to encryptField(src.description, key),
            "enc_amount" to encryptField(src.amount.toString(), key),
            "enc_repeatType" to encryptField(src.repeatType.name, key),
            "enc_repeatInterval" to encryptField(src.repeatInterval.toString(), key),
            "deviceId" to deviceId,
            "updatedAt" to FieldValue.serverTimestamp(),
            "deleted" to src.deleted,
            "lastEditBy" to deviceId
        )
        encryptNullableString(src.startDate?.toString(), key)?.let { map["enc_startDate"] = it }
        encryptNullableInt(src.monthDay1, key)?.let { map["enc_monthDay1"] = it }
        encryptNullableInt(src.monthDay2, key)?.let { map["enc_monthDay2"] = it }
        return map
    }

    fun incomeSourceFieldUpdate(changedFields: Set<String>, src: IncomeSource, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastEditBy" to deviceId
        )
        for (field in changedFields) {
            when (field) {
                "id" -> map["enc_id"] = encryptField(src.id.toString(), key)
                "source" -> map["enc_source"] = encryptField(src.source, key)
                "description" -> map["enc_description"] = encryptField(src.description, key)
                "amount" -> map["enc_amount"] = encryptField(src.amount.toString(), key)
                "repeatType" -> map["enc_repeatType"] = encryptField(src.repeatType.name, key)
                "repeatInterval" -> map["enc_repeatInterval"] = encryptField(src.repeatInterval.toString(), key)
                "startDate" -> map["enc_startDate"] =
                    encryptNullableString(src.startDate?.toString(), key) ?: FieldValue.delete()
                "monthDay1" -> map["enc_monthDay1"] =
                    encryptNullableInt(src.monthDay1, key) ?: FieldValue.delete()
                "monthDay2" -> map["enc_monthDay2"] =
                    encryptNullableInt(src.monthDay2, key) ?: FieldValue.delete()
                "deleted" -> map["deleted"] = src.deleted
            }
        }
        return map
    }

    fun incomeSourceFromDoc(doc: DocumentSnapshot, key: ByteArray): IncomeSource {
        if (!isPerField(doc)) {
            val json = decryptBlob(doc.getString("enc")!!, key)
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
        return IncomeSource(
            id = doc.decryptInt("enc_id", key),
            source = doc.decryptString("enc_source", key),
            description = doc.decryptString("enc_description", key),
            amount = doc.decryptDouble("enc_amount", key),
            repeatType = try { RepeatType.valueOf(doc.decryptString("enc_repeatType", key, "MONTHS")) } catch (_: Exception) { RepeatType.MONTHS },
            repeatInterval = doc.decryptInt("enc_repeatInterval", key, 1),
            startDate = doc.decryptNullableString("enc_startDate", key)?.let { LocalDate.parse(it) },
            monthDay1 = doc.decryptNullableInt("enc_monthDay1", key),
            monthDay2 = doc.decryptNullableInt("enc_monthDay2", key),
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    fun diffIncomeSourceFields(old: IncomeSource, new: IncomeSource): Set<String> {
        val changed = mutableSetOf<String>()
        if (old.id != new.id) changed.add("id")
        if (old.source != new.source) changed.add("source")
        if (old.description != new.description) changed.add("description")
        if (old.amount != new.amount) changed.add("amount")
        if (old.repeatType != new.repeatType) changed.add("repeatType")
        if (old.repeatInterval != new.repeatInterval) changed.add("repeatInterval")
        if (old.startDate != new.startDate) changed.add("startDate")
        if (old.monthDay1 != new.monthDay1) changed.add("monthDay1")
        if (old.monthDay2 != new.monthDay2) changed.add("monthDay2")
        if (old.deleted != new.deleted) changed.add("deleted")
        return changed
    }

    // ── SavingsGoal ─────────────────────────────────────────────────────

    fun savingsGoalToFieldMap(sg: SavingsGoal, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "enc_id" to encryptField(sg.id.toString(), key),
            "enc_name" to encryptField(sg.name, key),
            "enc_targetAmount" to encryptField(sg.targetAmount.toString(), key),
            "enc_totalSavedSoFar" to encryptField(sg.totalSavedSoFar.toString(), key),
            "enc_contributionPerPeriod" to encryptField(sg.contributionPerPeriod.toString(), key),
            "enc_isPaused" to encryptField(sg.isPaused.toString(), key),
            "deviceId" to deviceId,
            "updatedAt" to FieldValue.serverTimestamp(),
            "deleted" to sg.deleted,
            "lastEditBy" to deviceId
        )
        encryptNullableString(sg.targetDate?.toString(), key)?.let { map["enc_targetDate"] = it }
        return map
    }

    fun savingsGoalFieldUpdate(changedFields: Set<String>, sg: SavingsGoal, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastEditBy" to deviceId
        )
        for (field in changedFields) {
            when (field) {
                "id" -> map["enc_id"] = encryptField(sg.id.toString(), key)
                "name" -> map["enc_name"] = encryptField(sg.name, key)
                "targetAmount" -> map["enc_targetAmount"] = encryptField(sg.targetAmount.toString(), key)
                "targetDate" -> map["enc_targetDate"] =
                    encryptNullableString(sg.targetDate?.toString(), key) ?: FieldValue.delete()
                "totalSavedSoFar" -> map["enc_totalSavedSoFar"] = encryptField(sg.totalSavedSoFar.toString(), key)
                "contributionPerPeriod" -> map["enc_contributionPerPeriod"] = encryptField(sg.contributionPerPeriod.toString(), key)
                "isPaused" -> map["enc_isPaused"] = encryptField(sg.isPaused.toString(), key)
                "deleted" -> map["deleted"] = sg.deleted
            }
        }
        return map
    }

    fun savingsGoalFromDoc(doc: DocumentSnapshot, key: ByteArray): SavingsGoal {
        if (!isPerField(doc)) {
            val json = decryptBlob(doc.getString("enc")!!, key)
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
        return SavingsGoal(
            id = doc.decryptInt("enc_id", key),
            name = doc.decryptString("enc_name", key),
            targetAmount = doc.decryptDouble("enc_targetAmount", key),
            targetDate = doc.decryptNullableString("enc_targetDate", key)?.let { LocalDate.parse(it) },
            totalSavedSoFar = doc.decryptDouble("enc_totalSavedSoFar", key),
            contributionPerPeriod = doc.decryptDouble("enc_contributionPerPeriod", key),
            isPaused = doc.decryptBoolean("enc_isPaused", key, false),
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    fun diffSavingsGoalFields(old: SavingsGoal, new: SavingsGoal): Set<String> {
        val changed = mutableSetOf<String>()
        if (old.id != new.id) changed.add("id")
        if (old.name != new.name) changed.add("name")
        if (old.targetAmount != new.targetAmount) changed.add("targetAmount")
        if (old.targetDate != new.targetDate) changed.add("targetDate")
        if (old.totalSavedSoFar != new.totalSavedSoFar) changed.add("totalSavedSoFar")
        if (old.contributionPerPeriod != new.contributionPerPeriod) changed.add("contributionPerPeriod")
        if (old.isPaused != new.isPaused) changed.add("isPaused")
        if (old.deleted != new.deleted) changed.add("deleted")
        return changed
    }

    // ── AmortizationEntry ───────────────────────────────────────────────

    fun amortizationEntryToFieldMap(ae: AmortizationEntry, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "enc_id" to encryptField(ae.id.toString(), key),
            "enc_source" to encryptField(ae.source, key),
            "enc_description" to encryptField(ae.description, key),
            "enc_amount" to encryptField(ae.amount.toString(), key),
            "enc_totalPeriods" to encryptField(ae.totalPeriods.toString(), key),
            "enc_startDate" to encryptField(ae.startDate.toString(), key),
            "enc_isPaused" to encryptField(ae.isPaused.toString(), key),
            "deviceId" to deviceId,
            "updatedAt" to FieldValue.serverTimestamp(),
            "deleted" to ae.deleted,
            "lastEditBy" to deviceId
        )
        return map
    }

    fun amortizationEntryFieldUpdate(changedFields: Set<String>, ae: AmortizationEntry, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastEditBy" to deviceId
        )
        for (field in changedFields) {
            when (field) {
                "id" -> map["enc_id"] = encryptField(ae.id.toString(), key)
                "source" -> map["enc_source"] = encryptField(ae.source, key)
                "description" -> map["enc_description"] = encryptField(ae.description, key)
                "amount" -> map["enc_amount"] = encryptField(ae.amount.toString(), key)
                "totalPeriods" -> map["enc_totalPeriods"] = encryptField(ae.totalPeriods.toString(), key)
                "startDate" -> map["enc_startDate"] = encryptField(ae.startDate.toString(), key)
                "isPaused" -> map["enc_isPaused"] = encryptField(ae.isPaused.toString(), key)
                "deleted" -> map["deleted"] = ae.deleted
            }
        }
        return map
    }

    fun amortizationEntryFromDoc(doc: DocumentSnapshot, key: ByteArray): AmortizationEntry {
        if (!isPerField(doc)) {
            val json = decryptBlob(doc.getString("enc")!!, key)
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
        return AmortizationEntry(
            id = doc.decryptInt("enc_id", key),
            source = doc.decryptString("enc_source", key),
            description = doc.decryptString("enc_description", key),
            amount = doc.decryptDouble("enc_amount", key),
            totalPeriods = doc.decryptInt("enc_totalPeriods", key, 1),
            startDate = try { LocalDate.parse(doc.decryptString("enc_startDate", key)) } catch (_: Exception) { LocalDate.now() },
            isPaused = doc.decryptBoolean("enc_isPaused", key, false),
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    fun diffAmortizationEntryFields(old: AmortizationEntry, new: AmortizationEntry): Set<String> {
        val changed = mutableSetOf<String>()
        if (old.id != new.id) changed.add("id")
        if (old.source != new.source) changed.add("source")
        if (old.description != new.description) changed.add("description")
        if (old.amount != new.amount) changed.add("amount")
        if (old.totalPeriods != new.totalPeriods) changed.add("totalPeriods")
        if (old.startDate != new.startDate) changed.add("startDate")
        if (old.isPaused != new.isPaused) changed.add("isPaused")
        if (old.deleted != new.deleted) changed.add("deleted")
        return changed
    }

    // ── Category ────────────────────────────────────────────────────────

    fun categoryToFieldMap(cat: Category, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "enc_id" to encryptField(cat.id.toString(), key),
            "enc_name" to encryptField(cat.name, key),
            "enc_iconName" to encryptField(cat.iconName, key),
            "enc_tag" to encryptField(cat.tag, key),
            "enc_charted" to encryptField(cat.charted.toString(), key),
            "enc_widgetVisible" to encryptField(cat.widgetVisible.toString(), key),
            "deviceId" to deviceId,
            "updatedAt" to FieldValue.serverTimestamp(),
            "deleted" to cat.deleted,
            "lastEditBy" to deviceId
        )
        return map
    }

    fun categoryFieldUpdate(changedFields: Set<String>, cat: Category, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastEditBy" to deviceId
        )
        for (field in changedFields) {
            when (field) {
                "id" -> map["enc_id"] = encryptField(cat.id.toString(), key)
                "name" -> map["enc_name"] = encryptField(cat.name, key)
                "iconName" -> map["enc_iconName"] = encryptField(cat.iconName, key)
                "tag" -> map["enc_tag"] = encryptField(cat.tag, key)
                "charted" -> map["enc_charted"] = encryptField(cat.charted.toString(), key)
                "widgetVisible" -> map["enc_widgetVisible"] = encryptField(cat.widgetVisible.toString(), key)
                "deleted" -> map["deleted"] = cat.deleted
            }
        }
        return map
    }

    fun categoryFromDoc(doc: DocumentSnapshot, key: ByteArray): Category {
        if (!isPerField(doc)) {
            val json = decryptBlob(doc.getString("enc")!!, key)
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
        return Category(
            id = doc.decryptInt("enc_id", key),
            name = doc.decryptString("enc_name", key),
            iconName = doc.decryptString("enc_iconName", key),
            tag = doc.decryptString("enc_tag", key),
            charted = doc.decryptBoolean("enc_charted", key, true),
            widgetVisible = doc.decryptBoolean("enc_widgetVisible", key, true),
            deviceId = doc.getString("deviceId") ?: "",
            deleted = doc.getBoolean("deleted") ?: false
        )
    }

    fun diffCategoryFields(old: Category, new: Category): Set<String> {
        val changed = mutableSetOf<String>()
        if (old.id != new.id) changed.add("id")
        if (old.name != new.name) changed.add("name")
        if (old.iconName != new.iconName) changed.add("iconName")
        if (old.tag != new.tag) changed.add("tag")
        if (old.charted != new.charted) changed.add("charted")
        if (old.widgetVisible != new.widgetVisible) changed.add("widgetVisible")
        if (old.deleted != new.deleted) changed.add("deleted")
        return changed
    }

    // ── PeriodLedgerEntry ───────────────────────────────────────────────

    fun periodLedgerToFieldMap(ple: PeriodLedgerEntry, key: ByteArray, deviceId: String): Map<String, Any> {
        return hashMapOf(
            "enc_periodStartDate" to encryptField(ple.periodStartDate.toString(), key),
            "enc_appliedAmount" to encryptField(ple.appliedAmount.toString(), key),
            "deviceId" to deviceId,
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastEditBy" to deviceId
        )
    }

    fun periodLedgerFieldUpdate(changedFields: Set<String>, ple: PeriodLedgerEntry, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastEditBy" to deviceId
        )
        for (field in changedFields) {
            when (field) {
                "periodStartDate" -> map["enc_periodStartDate"] = encryptField(ple.periodStartDate.toString(), key)
                "appliedAmount" -> map["enc_appliedAmount"] = encryptField(ple.appliedAmount.toString(), key)
            }
        }
        return map
    }

    fun periodLedgerFromDoc(doc: DocumentSnapshot, key: ByteArray): PeriodLedgerEntry {
        if (!isPerField(doc)) {
            val json = decryptBlob(doc.getString("enc")!!, key)
            return PeriodLedgerEntry(
                periodStartDate = try { LocalDateTime.parse(json.getString("periodStartDate")) } catch (_: Exception) { LocalDateTime.now() },
                appliedAmount = SafeIO.safeDouble(json.getDouble("appliedAmount")),
                deviceId = doc.getString("deviceId") ?: ""
            )
        }
        return PeriodLedgerEntry(
            periodStartDate = try { LocalDateTime.parse(doc.decryptString("enc_periodStartDate", key)) } catch (_: Exception) { LocalDateTime.now() },
            appliedAmount = doc.decryptDouble("enc_appliedAmount", key),
            deviceId = doc.getString("deviceId") ?: ""
        )
    }

    fun diffPeriodLedgerFields(old: PeriodLedgerEntry, new: PeriodLedgerEntry): Set<String> {
        val changed = mutableSetOf<String>()
        if (old.periodStartDate != new.periodStartDate) changed.add("periodStartDate")
        if (old.appliedAmount != new.appliedAmount) changed.add("appliedAmount")
        return changed
    }

    // ── SharedSettings ──────────────────────────────────────────────────

    fun sharedSettingsToFieldMap(ss: SharedSettings, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "enc_currency" to encryptField(ss.currency, key),
            "enc_budgetPeriod" to encryptField(ss.budgetPeriod, key),
            "enc_isManualBudgetEnabled" to encryptField(ss.isManualBudgetEnabled.toString(), key),
            "enc_manualBudgetAmount" to encryptField(ss.manualBudgetAmount.toString(), key),
            "enc_weekStartSunday" to encryptField(ss.weekStartSunday.toString(), key),
            "enc_resetDayOfWeek" to encryptField(ss.resetDayOfWeek.toString(), key),
            "enc_resetDayOfMonth" to encryptField(ss.resetDayOfMonth.toString(), key),
            "enc_resetHour" to encryptField(ss.resetHour.toString(), key),
            "enc_familyTimezone" to encryptField(ss.familyTimezone, key),
            "enc_matchDays" to encryptField(ss.matchDays.toString(), key),
            "enc_matchPercent" to encryptField(ss.matchPercent.toString(), key),
            "enc_matchDollar" to encryptField(ss.matchDollar.toString(), key),
            "enc_matchChars" to encryptField(ss.matchChars.toString(), key),
            "enc_showAttribution" to encryptField(ss.showAttribution.toString(), key),
            "enc_availableCash" to encryptField(ss.availableCash.toString(), key),
            "enc_incomeMode" to encryptField(ss.incomeMode, key),
            "enc_deviceRoster" to encryptField(ss.deviceRoster, key),
            "enc_lastChangedBy" to encryptField(ss.lastChangedBy, key),
            "deviceId" to deviceId,
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastEditBy" to deviceId
        )
        encryptNullableString(ss.budgetStartDate, key)?.let { map["enc_budgetStartDate"] = it }
        encryptNullableInt(ss.receiptPruneAgeDays, key)?.let { map["enc_receiptPruneAgeDays"] = it }
        map["enc_archiveThreshold"] = encryptField(ss.archiveThreshold.toString(), key)
        return map
    }

    fun sharedSettingsFieldUpdate(changedFields: Set<String>, ss: SharedSettings, key: ByteArray, deviceId: String): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastEditBy" to deviceId
        )
        for (field in changedFields) {
            when (field) {
                "currency" -> map["enc_currency"] = encryptField(ss.currency, key)
                "budgetPeriod" -> map["enc_budgetPeriod"] = encryptField(ss.budgetPeriod, key)
                "budgetStartDate" -> map["enc_budgetStartDate"] =
                    encryptNullableString(ss.budgetStartDate, key) ?: FieldValue.delete()
                "isManualBudgetEnabled" -> map["enc_isManualBudgetEnabled"] = encryptField(ss.isManualBudgetEnabled.toString(), key)
                "manualBudgetAmount" -> map["enc_manualBudgetAmount"] = encryptField(ss.manualBudgetAmount.toString(), key)
                "weekStartSunday" -> map["enc_weekStartSunday"] = encryptField(ss.weekStartSunday.toString(), key)
                "resetDayOfWeek" -> map["enc_resetDayOfWeek"] = encryptField(ss.resetDayOfWeek.toString(), key)
                "resetDayOfMonth" -> map["enc_resetDayOfMonth"] = encryptField(ss.resetDayOfMonth.toString(), key)
                "resetHour" -> map["enc_resetHour"] = encryptField(ss.resetHour.toString(), key)
                "familyTimezone" -> map["enc_familyTimezone"] = encryptField(ss.familyTimezone, key)
                "matchDays" -> map["enc_matchDays"] = encryptField(ss.matchDays.toString(), key)
                "matchPercent" -> map["enc_matchPercent"] = encryptField(ss.matchPercent.toString(), key)
                "matchDollar" -> map["enc_matchDollar"] = encryptField(ss.matchDollar.toString(), key)
                "matchChars" -> map["enc_matchChars"] = encryptField(ss.matchChars.toString(), key)
                "showAttribution" -> map["enc_showAttribution"] = encryptField(ss.showAttribution.toString(), key)
                "availableCash" -> map["enc_availableCash"] = encryptField(ss.availableCash.toString(), key)
                "incomeMode" -> map["enc_incomeMode"] = encryptField(ss.incomeMode, key)
                "deviceRoster" -> map["enc_deviceRoster"] = encryptField(ss.deviceRoster, key)
                "receiptPruneAgeDays" -> map["enc_receiptPruneAgeDays"] =
                    encryptNullableInt(ss.receiptPruneAgeDays, key) ?: FieldValue.delete()
                "lastChangedBy" -> map["enc_lastChangedBy"] = encryptField(ss.lastChangedBy, key)
                "archiveThreshold" -> map["enc_archiveThreshold"] = encryptField(ss.archiveThreshold.toString(), key)
            }
        }
        return map
    }

    fun sharedSettingsFromDoc(doc: DocumentSnapshot, key: ByteArray): SharedSettings {
        if (!isPerField(doc)) {
            val json = decryptBlob(doc.getString("enc")!!, key)
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
        return SharedSettings(
            currency = doc.decryptString("enc_currency", key, "$"),
            budgetPeriod = doc.decryptString("enc_budgetPeriod", key, "DAILY"),
            budgetStartDate = doc.decryptNullableString("enc_budgetStartDate", key),
            isManualBudgetEnabled = doc.decryptBoolean("enc_isManualBudgetEnabled", key, false),
            manualBudgetAmount = doc.decryptDouble("enc_manualBudgetAmount", key),
            weekStartSunday = doc.decryptBoolean("enc_weekStartSunday", key, true),
            resetDayOfWeek = doc.decryptInt("enc_resetDayOfWeek", key, 7),
            resetDayOfMonth = doc.decryptInt("enc_resetDayOfMonth", key, 1),
            resetHour = doc.decryptInt("enc_resetHour", key, 0),
            familyTimezone = doc.decryptString("enc_familyTimezone", key),
            matchDays = doc.decryptInt("enc_matchDays", key, 7),
            matchPercent = doc.decryptDouble("enc_matchPercent", key, 1.0),
            matchDollar = doc.decryptInt("enc_matchDollar", key, 1),
            matchChars = doc.decryptInt("enc_matchChars", key, 5),
            showAttribution = doc.decryptBoolean("enc_showAttribution", key, false),
            availableCash = doc.decryptDouble("enc_availableCash", key),
            incomeMode = doc.decryptString("enc_incomeMode", key, "FIXED"),
            deviceRoster = doc.decryptString("enc_deviceRoster", key, "{}"),
            receiptPruneAgeDays = doc.decryptNullableInt("enc_receiptPruneAgeDays", key),
            lastChangedBy = doc.decryptString("enc_lastChangedBy", key),
            archiveThreshold = doc.decryptInt("enc_archiveThreshold", key, 10_000)
        )
    }

    fun diffSharedSettingsFields(old: SharedSettings, new: SharedSettings): Set<String> {
        val changed = mutableSetOf<String>()
        if (old.currency != new.currency) changed.add("currency")
        if (old.budgetPeriod != new.budgetPeriod) changed.add("budgetPeriod")
        if (old.budgetStartDate != new.budgetStartDate) changed.add("budgetStartDate")
        if (old.isManualBudgetEnabled != new.isManualBudgetEnabled) changed.add("isManualBudgetEnabled")
        if (old.manualBudgetAmount != new.manualBudgetAmount) changed.add("manualBudgetAmount")
        if (old.weekStartSunday != new.weekStartSunday) changed.add("weekStartSunday")
        if (old.resetDayOfWeek != new.resetDayOfWeek) changed.add("resetDayOfWeek")
        if (old.resetDayOfMonth != new.resetDayOfMonth) changed.add("resetDayOfMonth")
        if (old.resetHour != new.resetHour) changed.add("resetHour")
        if (old.familyTimezone != new.familyTimezone) changed.add("familyTimezone")
        if (old.matchDays != new.matchDays) changed.add("matchDays")
        if (old.matchPercent != new.matchPercent) changed.add("matchPercent")
        if (old.matchDollar != new.matchDollar) changed.add("matchDollar")
        if (old.matchChars != new.matchChars) changed.add("matchChars")
        if (old.showAttribution != new.showAttribution) changed.add("showAttribution")
        if (old.availableCash != new.availableCash) changed.add("availableCash")
        if (old.incomeMode != new.incomeMode) changed.add("incomeMode")
        if (old.deviceRoster != new.deviceRoster) changed.add("deviceRoster")
        if (old.receiptPruneAgeDays != new.receiptPruneAgeDays) changed.add("receiptPruneAgeDays")
        if (old.lastChangedBy != new.lastChangedBy) changed.add("lastChangedBy")
        if (old.archiveThreshold != new.archiveThreshold) changed.add("archiveThreshold")
        return changed
    }

    // ── Generic dispatchers ─────────────────────────────────────────────

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

    fun toFieldMap(record: Any, key: ByteArray, deviceId: String): Map<String, Any> = when (record) {
        is Transaction -> transactionToFieldMap(record, key, deviceId)
        is RecurringExpense -> recurringExpenseToFieldMap(record, key, deviceId)
        is IncomeSource -> incomeSourceToFieldMap(record, key, deviceId)
        is SavingsGoal -> savingsGoalToFieldMap(record, key, deviceId)
        is AmortizationEntry -> amortizationEntryToFieldMap(record, key, deviceId)
        is Category -> categoryToFieldMap(record, key, deviceId)
        is PeriodLedgerEntry -> periodLedgerToFieldMap(record, key, deviceId)
        is SharedSettings -> sharedSettingsToFieldMap(record, key, deviceId)
        else -> throw IllegalArgumentException("Unknown record type: ${record::class}")
    }

    fun fieldUpdate(record: Any, changedFields: Set<String>, key: ByteArray, deviceId: String): Map<String, Any> = when (record) {
        is Transaction -> transactionFieldUpdate(changedFields, record, key, deviceId)
        is RecurringExpense -> recurringExpenseFieldUpdate(changedFields, record, key, deviceId)
        is IncomeSource -> incomeSourceFieldUpdate(changedFields, record, key, deviceId)
        is SavingsGoal -> savingsGoalFieldUpdate(changedFields, record, key, deviceId)
        is AmortizationEntry -> amortizationEntryFieldUpdate(changedFields, record, key, deviceId)
        is Category -> categoryFieldUpdate(changedFields, record, key, deviceId)
        is PeriodLedgerEntry -> periodLedgerFieldUpdate(changedFields, record, key, deviceId)
        is SharedSettings -> sharedSettingsFieldUpdate(changedFields, record, key, deviceId)
        else -> throw IllegalArgumentException("Unknown record type: ${record::class}")
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> diffFields(old: T, new: T): Set<String> = when (old) {
        is Transaction -> diffTransactionFields(old, new as Transaction)
        is RecurringExpense -> diffRecurringExpenseFields(old, new as RecurringExpense)
        is IncomeSource -> diffIncomeSourceFields(old, new as IncomeSource)
        is SavingsGoal -> diffSavingsGoalFields(old, new as SavingsGoal)
        is AmortizationEntry -> diffAmortizationEntryFields(old, new as AmortizationEntry)
        is Category -> diffCategoryFields(old, new as Category)
        is PeriodLedgerEntry -> diffPeriodLedgerFields(old, new as PeriodLedgerEntry)
        is SharedSettings -> diffSharedSettingsFields(old, new as SharedSettings)
        else -> throw IllegalArgumentException("Unknown record type: ${(old as Any)::class}")
    }

    // ── Backward-compat aliases (old toDoc names → new toFieldMap) ─────

    fun transactionToDoc(t: Transaction, key: ByteArray, deviceId: String): Map<String, Any> =
        transactionToFieldMap(t, key, deviceId)

    fun recurringExpenseToDoc(re: RecurringExpense, key: ByteArray, deviceId: String): Map<String, Any> =
        recurringExpenseToFieldMap(re, key, deviceId)

    fun incomeSourceToDoc(src: IncomeSource, key: ByteArray, deviceId: String): Map<String, Any> =
        incomeSourceToFieldMap(src, key, deviceId)

    fun savingsGoalToDoc(sg: SavingsGoal, key: ByteArray, deviceId: String): Map<String, Any> =
        savingsGoalToFieldMap(sg, key, deviceId)

    fun amortizationEntryToDoc(ae: AmortizationEntry, key: ByteArray, deviceId: String): Map<String, Any> =
        amortizationEntryToFieldMap(ae, key, deviceId)

    fun categoryToDoc(cat: Category, key: ByteArray, deviceId: String): Map<String, Any> =
        categoryToFieldMap(cat, key, deviceId)

    fun periodLedgerToDoc(ple: PeriodLedgerEntry, key: ByteArray, deviceId: String): Map<String, Any> =
        periodLedgerToFieldMap(ple, key, deviceId)

    fun sharedSettingsToDoc(ss: SharedSettings, key: ByteArray, deviceId: String): Map<String, Any> =
        sharedSettingsToFieldMap(ss, key, deviceId)

    fun toDoc(record: Any, key: ByteArray, deviceId: String): Map<String, Any> =
        toFieldMap(record, key, deviceId)

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
