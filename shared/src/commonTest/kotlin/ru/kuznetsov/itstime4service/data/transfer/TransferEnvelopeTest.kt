package ru.kuznetsov.itstime4service.data.transfer

import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransferEnvelopeTest {
    private val context = EmptyCoroutineContext

    @Test
    fun plainEnvelopeHasExactFramingAndNoImplicitAuthenticity() {
        val body = "{\"test\":true}".encodeToByteArray()
        val bytes = TransferEnvelope.plain(body)
        assertContentEquals(byteArrayOf(0x49, 0x54, 0x34, 0x53, 1, 0, 0, 0, 0, 2, 0, 0, 0, body.size.toByte()), bytes.copyOfRange(0, 14))
        val parsed = TransferEnvelope.parse(bytes, context)
        assertEquals(0, parsed.protection)
        assertEquals(emptyMap(), parsed.metadata)
        assertContentEquals(body, parsed.body)
    }

    @Test
    fun truncatedTrailingWrongMagicAndNonemptyPlainMetadataAreRejected() {
        val plain = TransferEnvelope.plain("{}".encodeToByteArray())
        listOf(plain.copyOf(13), plain + 0, plain.copyOf(plain.size - 1), plain.copyOf().also { it[0] = 0 },
            TransferEnvelope.header(0, "{\"kind\":\"backup\"}".encodeToByteArray(), 2) + "{}".encodeToByteArray(),
            TransferEnvelope.header(0, byteArrayOf(0xc3.toByte(), 0x28), 2) + "{}".encodeToByteArray(),
        ).forEach { assertEquals(TransferFailure.InvalidEnvelope, assertFailsWith<SnapshotRejected> { TransferEnvelope.parse(it, context) }.reason) }
    }

    @Test
    fun unsignedLengthFieldsCannotOverflowAndFileBoundIsCheckedFirst() {
        val bytes = TransferEnvelope.plain(byteArrayOf()).also { (6..9).forEach { position -> it[position] = 0xff.toByte() } }
        assertEquals(TransferFailure.LimitExceeded, assertFailsWith<SnapshotRejected> { TransferEnvelope.parse(bytes, context) }.reason)
        assertEquals(TransferFailure.LimitExceeded, assertFailsWith<SnapshotRejected> {
            TransferEnvelope.parse(ByteArray(TransferLimits.MAX_FILE_BYTES + 1), context)
        }.reason)
        assertEquals(TransferFailure.LimitExceeded, assertFailsWith<SnapshotRejected> {
            TransferEnvelope.plain(ByteArray(TransferLimits.MAX_PLAINTEXT_BYTES + 1))
        }.reason)
    }
}
