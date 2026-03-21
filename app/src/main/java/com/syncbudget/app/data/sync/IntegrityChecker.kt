package com.syncbudget.app.data.sync

import com.syncbudget.app.data.*
import org.json.JSONArray
import org.json.JSONObject

object IntegrityChecker {

    private const val SEGMENT_COUNT = 16

    // ── Data classes ─────────────────────────────────────────────

    data class SegmentFingerprint(
        val count: Int,
        val idXor: Int,
        val clockSum: Long
    )

    data class CollectionFingerprint(
        val count: Int,
        val idXor: Int,
        val clockSum: Long,
        val segments: List<SegmentFingerprint>  // SEGMENT_COUNT buckets, keyed by id % SEGMENT_COUNT
    )

    data class IntegrityFingerprint(
        val deviceId: String,
        val syncVersion: Long,
        val timestamp: Long,
        val collections: Map<String, CollectionFingerprint>,
        val settingsMaxClock: Long
    )

    /** A single repair action: re-push specific records from one collection. */
    data class RepairAction(
        val collection: String,
        val recordIds: Set<Int>,
        val reason: String
    )

    data class DivergenceReport(
        val diverged: Boolean,
        val details: List<String>,
        val repairs: List<RepairAction>
    )

    // ── Per-record max field clock ────────────────────────────────

    fun maxClock(t: Transaction): Long = maxOf(
        t.source_clock, t.description_clock, t.amount_clock, t.date_clock,
        t.type_clock, t.categoryAmounts_clock, t.isUserCategorized_clock,
        t.excludeFromBudget_clock, t.isBudgetIncome_clock,
        t.linkedRecurringExpenseId_clock, t.linkedAmortizationEntryId_clock,
        t.linkedIncomeSourceId_clock, t.amortizationAppliedAmount_clock,
        t.linkedRecurringExpenseAmount_clock, t.linkedIncomeSourceAmount_clock,
        t.linkedSavingsGoalId_clock, t.linkedSavingsGoalAmount_clock,
        t.receiptId1_clock, t.receiptId2_clock, t.receiptId3_clock,
        t.receiptId4_clock, t.receiptId5_clock,
        t.deleted_clock, t.deviceId_clock
    )

    fun maxClock(r: RecurringExpense): Long = maxOf(
        r.source_clock, r.description_clock, r.amount_clock,
        r.repeatType_clock, r.repeatInterval_clock, r.startDate_clock,
        r.monthDay1_clock, r.monthDay2_clock, r.deleted_clock, r.deviceId_clock,
        r.setAsideSoFar_clock, r.isAccelerated_clock
    )

    fun maxClock(s: IncomeSource): Long = maxOf(
        s.source_clock, s.description_clock, s.amount_clock,
        s.repeatType_clock, s.repeatInterval_clock, s.startDate_clock,
        s.monthDay1_clock, s.monthDay2_clock, s.deleted_clock, s.deviceId_clock
    )

    fun maxClock(g: SavingsGoal): Long = maxOf(
        g.name_clock, g.targetAmount_clock, g.targetDate_clock,
        g.totalSavedSoFar_clock, g.contributionPerPeriod_clock,
        g.isPaused_clock, g.deleted_clock, g.deviceId_clock
    )

    fun maxClock(e: AmortizationEntry): Long = maxOf(
        e.source_clock, e.description_clock, e.amount_clock,
        e.totalPeriods_clock, e.startDate_clock, e.isPaused_clock,
        e.deleted_clock, e.deviceId_clock
    )

    // Exclude charted_clock and widgetVisible_clock (device-local)
    fun maxClock(c: Category): Long = maxOf(
        c.name_clock, c.iconName_clock, c.tag_clock,
        c.deleted_clock, c.deviceId_clock
    )

    fun maxClock(p: PeriodLedgerEntry): Long = p.clock

    fun maxClock(s: SharedSettings): Long = maxOf(
        s.currency_clock, s.budgetPeriod_clock, s.budgetStartDate_clock,
        s.isManualBudgetEnabled_clock, s.manualBudgetAmount_clock,
        s.weekStartSunday_clock, s.resetDayOfWeek_clock, s.resetDayOfMonth_clock,
        s.resetHour_clock, s.familyTimezone_clock, s.matchDays_clock,
        s.matchPercent_clock, s.matchDollar_clock, s.matchChars_clock,
        s.showAttribution_clock, s.availableCash_clock, s.incomeMode_clock,
        s.deviceRoster_clock, s.receiptPruneAgeDays_clock
    )

    // ── Segmented collection fingerprint ─────────────────────────

    private fun <T> fingerprint(list: List<T>, getId: (T) -> Int, getMaxClock: (T) -> Long): CollectionFingerprint {
        val segCounts = IntArray(SEGMENT_COUNT)
        val segIdXor = IntArray(SEGMENT_COUNT)
        val segClockSum = LongArray(SEGMENT_COUNT)
        var totalIdXor = 0
        var totalClockSum = 0L

        for (item in list) {
            val id = getId(item)
            val clk = getMaxClock(item)
            val seg = (id and 0x7FFFFFFF) % SEGMENT_COUNT  // non-negative modulo
            segCounts[seg]++
            segIdXor[seg] = segIdXor[seg] xor id
            segClockSum[seg] += clk
            totalIdXor = totalIdXor xor id
            totalClockSum += clk
        }

        val segments = (0 until SEGMENT_COUNT).map { i ->
            SegmentFingerprint(segCounts[i], segIdXor[i], segClockSum[i])
        }
        return CollectionFingerprint(
            count = list.size, idXor = totalIdXor,
            clockSum = totalClockSum, segments = segments
        )
    }

    // ── Compute full fingerprint ─────────────────────────────────

    fun computeFingerprint(
        deviceId: String,
        syncVersion: Long,
        transactions: List<Transaction>,
        recurringExpenses: List<RecurringExpense>,
        incomeSources: List<IncomeSource>,
        savingsGoals: List<SavingsGoal>,
        amortizationEntries: List<AmortizationEntry>,
        categories: List<Category>,
        periodLedgerEntries: List<PeriodLedgerEntry>,
        sharedSettings: SharedSettings
    ): IntegrityFingerprint {
        val collections = mapOf(
            "transactions" to fingerprint(transactions, { it.id }, ::maxClock),
            "recurringExpenses" to fingerprint(recurringExpenses, { it.id }, ::maxClock),
            "incomeSources" to fingerprint(incomeSources, { it.id }, ::maxClock),
            "savingsGoals" to fingerprint(savingsGoals, { it.id }, ::maxClock),
            "amortizationEntries" to fingerprint(amortizationEntries, { it.id }, ::maxClock),
            "categories" to fingerprint(categories, { it.id }, ::maxClock),
            "periodLedger" to fingerprint(periodLedgerEntries, { it.id }, ::maxClock)
        )
        return IntegrityFingerprint(
            deviceId = deviceId,
            syncVersion = syncVersion,
            timestamp = System.currentTimeMillis(),
            collections = collections,
            settingsMaxClock = maxClock(sharedSettings)
        )
    }

    // ── JSON serialization ───────────────────────────────────────

    fun toJson(fp: IntegrityFingerprint): JSONObject {
        val obj = JSONObject()
        obj.put("deviceId", fp.deviceId)
        obj.put("syncVersion", fp.syncVersion)
        obj.put("timestamp", fp.timestamp)
        obj.put("settingsMaxClock", fp.settingsMaxClock)
        val cols = JSONObject()
        for ((key, cfp) in fp.collections) {
            val c = JSONObject()
            c.put("count", cfp.count)
            c.put("idXor", cfp.idXor)
            c.put("clockSum", cfp.clockSum)
            val segs = JSONArray()
            for (seg in cfp.segments) {
                val s = JSONObject()
                s.put("c", seg.count)
                s.put("x", seg.idXor)
                s.put("s", seg.clockSum)
                segs.put(s)
            }
            c.put("seg", segs)
            cols.put(key, c)
        }
        obj.put("collections", cols)
        return obj
    }

    fun fromJson(json: JSONObject): IntegrityFingerprint {
        val cols = json.getJSONObject("collections")
        val map = mutableMapOf<String, CollectionFingerprint>()
        for (key in cols.keys()) {
            val c = cols.getJSONObject(key)
            val segsArr = c.optJSONArray("seg")
            val segments = if (segsArr != null) {
                (0 until segsArr.length()).map { i ->
                    val s = segsArr.getJSONObject(i)
                    SegmentFingerprint(s.getInt("c"), s.getInt("x"), s.getLong("s"))
                }
            } else {
                // Backward compat: old fingerprints without segments
                List(SEGMENT_COUNT) { SegmentFingerprint(0, 0, 0L) }
            }
            map[key] = CollectionFingerprint(
                count = c.getInt("count"),
                idXor = c.getInt("idXor"),
                clockSum = c.getLong("clockSum"),
                segments = segments
            )
        }
        return IntegrityFingerprint(
            deviceId = json.getString("deviceId"),
            syncVersion = json.getLong("syncVersion"),
            timestamp = json.getLong("timestamp"),
            collections = map,
            settingsMaxClock = json.getLong("settingsMaxClock")
        )
    }

    // ── Compare fingerprints and generate repair plan ────────────

    /**
     * Compare local fingerprint against ONE remote fingerprint.
     * Returns divergence details and repair actions this device should take.
     *
     * Call once per peer device; merge the repair lists across all peers
     * before executing (dedup by collection+recordId).
     */
    fun compare(local: IntegrityFingerprint, remote: IntegrityFingerprint): DivergenceReport {
        val details = mutableListOf<String>()
        val repairs = mutableListOf<RepairAction>()

        val allKeys = (local.collections.keys + remote.collections.keys).distinct()
        for (key in allKeys) {
            val l = local.collections[key]
            val r = remote.collections[key]
            if (l == null) { details.add("$key: missing locally"); continue }
            if (r == null) { details.add("$key: missing on remote"); continue }

            // Top-level match → skip segment analysis
            if (l.count == r.count && l.idXor == r.idXor && l.clockSum == r.clockSum) continue

            details.add("$key: diverged (local=${l.count}/${l.idXor}/${l.clockSum}, " +
                "remote=${r.count}/${r.idXor}/${r.clockSum})")

            // Walk segments to pinpoint divergent records
            for (seg in 0 until minOf(l.segments.size, r.segments.size)) {
                val ls = l.segments[seg]
                val rs = r.segments[seg]
                if (ls.count == rs.count && ls.idXor == rs.idXor && ls.clockSum == rs.clockSum) continue

                // Case 1: local has MORE records in this segment → re-push extras
                if (ls.count > rs.count) {
                    val diff = ls.count - rs.count
                    if (diff == 1) {
                        // XOR trick: the single missing ID = localXor ^ remoteXor.
                        // Only trust it if the computed ID actually exists in the
                        // local dataset — XOR is not injective and can produce
                        // garbage IDs when multiple records differ.
                        val missingId = ls.idXor xor rs.idXor
                        if (missingId > 0) {
                            repairs.add(RepairAction(key, setOf(missingId),
                                "seg$seg: local has 1 extra record (id=$missingId)"))
                        } else {
                            // Invalid ID — fall back to segment scan
                            repairs.add(RepairAction(key, setOf(-seg - 1),
                                "seg$seg: XOR produced invalid id=$missingId, using segment scan"))
                        }
                    } else {
                        // Multiple missing — can't pinpoint with XOR alone.
                        // Mark segment for full scan (handled by caller).
                        repairs.add(RepairAction(key, setOf(-seg - 1),  // negative = segment marker
                            "seg$seg: local has $diff extra records"))
                    }
                }
                // Case 2: remote has MORE → remote should re-push; we do nothing
                else if (rs.count > ls.count) {
                    details.add("$key seg$seg: remote has ${rs.count - ls.count} extra records (awaiting their push)")
                }
                // Case 3: same count, different IDs → both sides have unique records
                else if (ls.idXor != rs.idXor) {
                    repairs.add(RepairAction(key, setOf(-seg - 1),
                        "seg$seg: same count but different IDs"))
                }
                // Case 4: same count, same IDs, different clocks → BOTH sides
                // must re-push.  Different fields may be higher on each device,
                // and only pushing from the "higher sum" side doesn't converge
                // when the divergence is spread across different fields.
                else if (ls.clockSum != rs.clockSum) {
                    repairs.add(RepairAction(key, setOf(-seg - 1),
                        "seg$seg: clock mismatch (local=${ls.clockSum}, remote=${rs.clockSum})"))
                }
            }
        }

        if (local.settingsMaxClock != remote.settingsMaxClock) {
            details.add("sharedSettings: clock mismatch (local=${local.settingsMaxClock}, remote=${remote.settingsMaxClock})")
        }

        return DivergenceReport(
            diverged = details.isNotEmpty(),
            details = details,
            repairs = repairs
        )
    }

    /**
     * Merge repair actions from comparisons with multiple peers.
     * Deduplicates by collection + recordId.
     */
    fun mergeRepairs(reports: List<DivergenceReport>): List<RepairAction> {
        val byCollection = mutableMapOf<String, MutableSet<Int>>()
        val reasons = mutableMapOf<String, MutableList<String>>()
        for (report in reports) {
            for (repair in report.repairs) {
                byCollection.getOrPut(repair.collection) { mutableSetOf() }.addAll(repair.recordIds)
                reasons.getOrPut(repair.collection) { mutableListOf() }.add(repair.reason)
            }
        }
        return byCollection.map { (col, ids) ->
            RepairAction(col, ids, reasons[col]?.joinToString("; ") ?: "")
        }
    }

    /**
     * Resolve repair actions into concrete record IDs.
     * Segment markers (negative IDs) are expanded into all local record IDs
     * that belong to that segment.
     */
    fun <T> resolveIds(
        action: RepairAction,
        records: List<T>,
        getId: (T) -> Int
    ): Set<Int> {
        val direct = mutableSetOf<Int>()
        val segmentsToScan = mutableSetOf<Int>()
        for (id in action.recordIds) {
            if (id < 0) {
                segmentsToScan.add(-(id + 1))  // decode segment index
            } else {
                direct.add(id)
            }
        }
        if (segmentsToScan.isEmpty()) return direct

        // Expand segment markers into actual record IDs
        for (record in records) {
            val rid = getId(record)
            val seg = (rid and 0x7FFFFFFF) % SEGMENT_COUNT
            if (seg in segmentsToScan) {
                direct.add(rid)
            }
        }
        return direct
    }
}
