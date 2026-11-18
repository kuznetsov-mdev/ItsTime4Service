package ru.kuznetsov.itstime4service.ui.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits
import kotlin.test.*

class BoundedTransferIoTest {
    @Test
    fun partialReadBuffersAreClearedAfterCopyAndOnFailure() = runTest {
        val buffers = mutableListOf<ByteArray>()
        var reads = 0
        val input = object : TransferInput {
            override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
                buffers.add(buffer)
                if (reads++ == 0) { buffer[0] = 42; return 1 }
                return -1
            }
            override fun close() = Unit
        }
        assertContentEquals(byteArrayOf(42), readTransferBytes(input, null))
        assertTrue(buffers.all { bytes -> bytes.all { it == 0.toByte() } })
        val failing = object : TransferInput {
            override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
                buffers.add(buffer)
                buffer.fill(42)
                throw TransferFileException(TransferFailure.ReadFailed)
            }
            override fun close() = Unit
        }
        assertFailsWith<TransferFileException> { readTransferBytes(failing, null) }
        assertTrue(buffers.all { bytes -> bytes.all { it == 0.toByte() } })
    }

    private class Input(val bytes: ByteArray, val chunk: Int = 8192) : TransferInput {
        var offset = 0
        var closes = 0
        var zero = false
        override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
            if (zero) return 0
            if (this.offset == bytes.size) return -1
            val n = minOf(count, chunk, bytes.size - this.offset)
            bytes.copyInto(buffer, offset, this.offset, this.offset + n)
            this.offset += n
            return n
        }
        override fun close() { closes++ }
    }

    private class Output : TransferOutput {
        val bytes = mutableListOf<Byte>()
        var closes = 0
        var flushes = 0
        var failClose = false
        var zero = false
        override fun write(buffer: ByteArray, offset: Int, count: Int): Int {
            if (zero) return 0
            val n = minOf(3, count)
            bytes.addAll(buffer.copyOfRange(offset, offset + n).toList())
            return n
        }
        override fun flush() { flushes++ }
        override fun close() {
            closes++
            if (failClose) throw TransferFileException(TransferFailure.WriteFailed)
        }
    }

    @Test
    fun partialReadsIgnoreUnderReportedSizeAndClose() = runTest {
        val input = Input(ByteArray(17000) { (it % 251).toByte() }, 7)
        assertContentEquals(input.bytes, readTransferBytes(input, 2))
        assertEquals(1, input.closes)
    }

    @Test
    fun boundaryFitsButBoundaryPlusOneIsRejectedWithoutUnboundedRead() = runTest {
        val exact = Input(ByteArray(TransferLimits.MAX_FILE_BYTES))
        assertEquals(TransferLimits.MAX_FILE_BYTES, readTransferBytes(exact, null).size)
        val tooLarge = Input(ByteArray(TransferLimits.MAX_FILE_BYTES + 100))
        val error = assertFailsWith<TransferFileException> { readTransferBytes(tooLarge, 1) }
        assertEquals(TransferFailure.LimitExceeded, error.reason)
        assertEquals(TransferLimits.MAX_FILE_BYTES + 1, tooLarge.offset)
        assertEquals(1, tooLarge.closes)
    }

    @Test
    fun oversizedMetadataRejectsBeforeReadingAndStillCloses() = runTest {
        val input = Input(byteArrayOf(1))
        assertEquals(TransferFailure.LimitExceeded, assertFailsWith<TransferFileException> {
            readTransferBytes(input, TransferLimits.MAX_FILE_BYTES.toLong() + 1)
        }.reason)
        assertEquals(0, input.offset)
        assertEquals(1, input.closes)
    }

    @Test
    fun zeroReadDoesNotSpinForever() = runTest {
        val input = Input(byteArrayOf(1)).also { it.zero = true }
        assertEquals(TransferFailure.ReadFailed, assertFailsWith<TransferFileException> { readTransferBytes(input, null) }.reason)
        assertEquals(1, input.closes)
    }

    @Test
    fun cancellationClosesInputAndIsNotAFileFailure() = runTest {
        var closes = 0
        val input = object : TransferInput {
            override fun read(buffer: ByteArray, offset: Int, count: Int): Int = throw CancellationException("Synthetic cancellation")
            override fun close() { closes++ }
        }
        assertFailsWith<CancellationException> { readTransferBytes(input, null) }
        assertEquals(1, closes)
    }

    @Test
    fun partialWritesFlushAndCloseBeforeSuccess() = runTest {
        val output = Output()
        val bytes = ByteArray(31) { it.toByte() }
        writeTransferBytes(output, bytes)
        assertContentEquals(bytes, output.bytes.toByteArray())
        assertEquals(1, output.flushes)
        assertEquals(1, output.closes)
    }

    @Test
    fun closeFailureCannotBeSuccessfulSave() = runTest {
        val output = Output().also { it.failClose = true }
        assertEquals(TransferFailure.WriteFailed, assertFailsWith<TransferFileException> { writeTransferBytes(output, byteArrayOf(1)) }.reason)
        assertEquals(1, output.closes)
    }

    @Test
    fun zeroWriteFailsAndCloses() = runTest {
        val output = Output().also { it.zero = true }
        assertEquals(TransferFailure.WriteFailed, assertFailsWith<TransferFileException> { writeTransferBytes(output, byteArrayOf(1)) }.reason)
        assertEquals(1, output.closes)
    }

    @Test
    fun namesAllowUnicodeButRejectPathsAndMissingExtension() {
        assertTrue(validTransferFileName("ItsTime4Service.it4s"))
        assertTrue(validTransferFileName("Копия.it4s"))
        listOf("../copy.it4s", "/copy.it4s", "a\\copy.it4s", "bad\n.it4s", ".it4s", "copy.json", "x".repeat(130) + ".it4s").forEach {
            assertFalse(validTransferFileName(it))
        }
    }
    @Test
    fun closeFailureDoesNotReplaceCancellation() = runTest {
        val input = object : TransferInput {
            override fun read(buffer: ByteArray, offset: Int, count: Int): Int = throw CancellationException("Synthetic cancellation")
            override fun close() { throw TransferFileException(TransferFailure.ReadFailed) }
        }
        assertFailsWith<CancellationException> { readTransferBytes(input, null) }
    }

}
