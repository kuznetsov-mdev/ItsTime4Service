package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StrictTransferJsonTest {
    private val context = EmptyCoroutineContext

    @Test
    fun exactByteBudgetCountsEscapingAndMultibyteText() {
        val value = JsonArray(listOf(JsonPrimitive("я\u0000\"\\🔧")))
        val expected = value.toString().encodeToByteArray()
        assertContentEquals(expected, StrictTransferJson.encode(value, context, expected.size))
        assertEquals(TransferFailure.LimitExceeded, assertFailsWith<SnapshotRejected> {
            StrictTransferJson.encode(value, context, expected.size - 1)
        }.reason)
    }

    @Test
    fun depth24IsAcceptedAnd25IsRejected() {
        val valid = "[".repeat(24) + "0" + "]".repeat(24)
        assertContentEquals(valid.encodeToByteArray(), StrictTransferJson.encode(StrictTransferJson.decode(valid.encodeToByteArray(), context), context))
        assertEquals(TransferFailure.LimitExceeded, assertFailsWith<SnapshotRejected> {
            StrictTransferJson.decode(("[$valid]").encodeToByteArray(), context)
        }.reason)
    }

    @Test
    fun aggregateTaskCountAcrossCarsIsBoundedDuringParsing() {
        val first = List(15000) { "0" }.joinToString(",")
        val second = List(5000) { "0" }.joinToString(",")
        val exact = "{\"cars\":[{\"tasks\":[$first]},{\"tasks\":[$second]}]}"
        StrictTransferJson.decode(exact.encodeToByteArray(), context)
        val over = "{\"cars\":[{\"tasks\":[$first]},{\"tasks\":[$second,0]}]}"
        assertEquals(TransferFailure.LimitExceeded, assertFailsWith<SnapshotRejected> { StrictTransferJson.decode(over.encodeToByteArray(), context) }.reason)
    }

    @Test
    fun carHistoryAndReminderCountsAreBoundedDuringParsing() {
        listOf("cars" to 1000, "history" to 50000, "reminderState" to 20000).forEach { (field, size) ->
            val values = List(size) { "0" }.joinToString(",")
            StrictTransferJson.decode("{\"$field\":[$values]}".encodeToByteArray(), context)
            assertEquals(TransferFailure.LimitExceeded, assertFailsWith<SnapshotRejected> {
                StrictTransferJson.decode("{\"$field\":[$values,0]}".encodeToByteArray(), context)
            }.reason)
        }
    }

    @Test
    fun decodedStringByteLimitAppliesToEscapedUnicode() {
        val exact = "\"" + "\\u044f".repeat(8192) + "\""
        StrictTransferJson.decode(exact.encodeToByteArray(), context)
        assertEquals(TransferFailure.LimitExceeded, assertFailsWith<SnapshotRejected> {
            StrictTransferJson.decode((exact.dropLast(1) + "\\u044f\"").encodeToByteArray(), context)
        }.reason)
    }
}
