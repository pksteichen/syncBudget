package com.techadvantage.budgetrak

import com.techadvantage.budgetrak.data.*
import com.techadvantage.budgetrak.data.sync.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DeltaSerializerTest {

    private val now = Instant.parse("2026-01-15T12:00:00Z")

    // ── Round-trip ──────────────────────────────────────────────────

    @Test
    fun roundTrip_singleTransaction() {
        val packet = DeltaPacket(
            sourceDeviceId = "dev1",
            timestamp = now,
            changes = listOf(
                RecordDelta(
                    type = "transaction",
                    action = "upsert",
                    id = 42,
                    deviceId = "dev1",
                    fields = mapOf(
                        "source" to FieldDelta("Store", 5L),
                        "amount" to FieldDelta(99.99, 5L),
                        "date" to FieldDelta("2026-01-15", 5L),
                        "type" to FieldDelta("EXPENSE", 5L)
                    )
                )
            )
        )

        val json = DeltaSerializer.serialize(packet)
        val deserialized = DeltaSerializer.deserialize(json)

        assertEquals(packet.sourceDeviceId, deserialized.sourceDeviceId)
        assertEquals(packet.timestamp, deserialized.timestamp)
        assertEquals(1, deserialized.changes.size)
        val change = deserialized.changes[0]
        assertEquals("transaction", change.type)
        assertEquals(42, change.id)
        assertEquals("Store", change.fields["source"]?.value)
        assertEquals(5L, change.fields["source"]?.clock)
    }

    @Test
    fun roundTrip_multipleRecordTypes() {
        val packet = DeltaPacket(
            sourceDeviceId = "dev1",
            timestamp = now,
            changes = listOf(
                RecordDelta("transaction", "upsert", 1, "dev1",
                    mapOf("source" to FieldDelta("Store", 3L))),
                RecordDelta("category", "upsert", 10, "dev1",
                    mapOf("name" to FieldDelta("Food", 5L))),
                RecordDelta("shared_settings", "upsert", 0, "dev1",
                    mapOf("currency" to FieldDelta("€", 7L)))
            )
        )

        val json = DeltaSerializer.serialize(packet)
        val deserialized = DeltaSerializer.deserialize(json)

        assertEquals(3, deserialized.changes.size)
        assertEquals("transaction", deserialized.changes[0].type)
        assertEquals("category", deserialized.changes[1].type)
        assertEquals("shared_settings", deserialized.changes[2].type)
    }

    @Test
    fun roundTrip_nullFieldValues() {
        val packet = DeltaPacket(
            sourceDeviceId = "dev1",
            timestamp = now,
            changes = listOf(
                RecordDelta("income_source", "upsert", 1, "dev1",
                    mapOf(
                        "source" to FieldDelta("Salary", 5L),
                        "startDate" to FieldDelta(null, 3L)
                    ))
            )
        )

        val json = DeltaSerializer.serialize(packet)
        val deserialized = DeltaSerializer.deserialize(json)

        val fields = deserialized.changes[0].fields
        assertEquals("Salary", fields["source"]?.value)
        assertNull(fields["startDate"]?.value)
        assertEquals(3L, fields["startDate"]?.clock)
    }

    @Test
    fun roundTrip_emptyChangesList() {
        val packet = DeltaPacket("dev1", now, emptyList())

        val json = DeltaSerializer.serialize(packet)
        val deserialized = DeltaSerializer.deserialize(json)

        assertEquals("dev1", deserialized.sourceDeviceId)
        assertTrue(deserialized.changes.isEmpty())
    }

    @Test
    fun roundTrip_categoryAmountsAsString() {
        val catJson = "[{\"categoryId\":10,\"amount\":25.0},{\"categoryId\":20,\"amount\":17.0}]"
        val packet = DeltaPacket(
            sourceDeviceId = "dev1",
            timestamp = now,
            changes = listOf(
                RecordDelta("transaction", "upsert", 1, "dev1",
                    mapOf("categoryAmounts" to FieldDelta(catJson, 5L)))
            )
        )

        val json = DeltaSerializer.serialize(packet)
        val deserialized = DeltaSerializer.deserialize(json)

        val value = deserialized.changes[0].fields["categoryAmounts"]?.value as String
        assertTrue(value.contains("categoryId"))
    }

    @Test
    fun roundTrip_timestampPreserved() {
        val specificTime = Instant.parse("2026-06-15T08:30:45.123Z")
        val packet = DeltaPacket("dev1", specificTime, emptyList())

        val json = DeltaSerializer.serialize(packet)
        val deserialized = DeltaSerializer.deserialize(json)

        assertEquals(specificTime, deserialized.timestamp)
    }

    @Test
    fun roundTrip_largeClocksPreserved() {
        val largeClock = Long.MAX_VALUE - 100
        val packet = DeltaPacket(
            sourceDeviceId = "dev1",
            timestamp = now,
            changes = listOf(
                RecordDelta("transaction", "upsert", 1, "dev1",
                    mapOf("source" to FieldDelta("Test", largeClock)))
            )
        )

        val json = DeltaSerializer.serialize(packet)
        val deserialized = DeltaSerializer.deserialize(json)

        assertEquals(largeClock, deserialized.changes[0].fields["source"]?.clock)
    }

    // ── Build + serialize round-trip ────────────────────────────────

    @Test
    fun fullPipeline_buildThenSerializeDeserialize() {
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 1, 15), source = "Amazon",
            amount = 42.99, deviceId = "dev1",
            source_clock = 5, amount_clock = 5, date_clock = 5, type_clock = 5)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 0)!!
        val packet = DeltaPacket("dev1", now, listOf(delta))

        val json = DeltaSerializer.serialize(packet)
        val deserialized = DeltaSerializer.deserialize(json)

        val change = deserialized.changes[0]
        assertEquals("Amazon", change.fields["source"]?.value)
        assertEquals(5L, change.fields["amount"]?.clock)
    }

    @Test
    fun roundTrip_booleanValues() {
        val packet = DeltaPacket(
            sourceDeviceId = "dev1",
            timestamp = now,
            changes = listOf(
                RecordDelta("transaction", "upsert", 1, "dev1",
                    mapOf(
                        "deleted" to FieldDelta(true, 5L),
                        "isUserCategorized" to FieldDelta(false, 3L)
                    ))
            )
        )

        val json = DeltaSerializer.serialize(packet)
        val deserialized = DeltaSerializer.deserialize(json)

        assertEquals(true, deserialized.changes[0].fields["deleted"]?.value)
        assertEquals(false, deserialized.changes[0].fields["isUserCategorized"]?.value)
    }
}
