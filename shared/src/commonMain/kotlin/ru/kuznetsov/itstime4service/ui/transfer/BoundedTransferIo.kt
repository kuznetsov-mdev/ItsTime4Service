package ru.kuznetsov.itstime4service.ui.transfer

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits

internal interface TransferInput {
    fun read(buffer: ByteArray, offset: Int, count: Int): Int
    fun close()
}

internal interface TransferOutput {
    fun write(buffer: ByteArray, offset: Int, count: Int): Int
    fun flush()
    fun close()
}

internal class TransferFileException(val reason: TransferFailure) : Exception("Transfer file operation failed")

internal suspend fun readTransferBytes(input: TransferInput, reportedSize: Long?): ByteArray =
    readTransferBytesBlocking(input, reportedSize, currentCoroutineContext())

internal fun readTransferBytesBlocking(input: TransferInput, reportedSize: Long?, context: CoroutineContext): ByteArray {
    val chunks = mutableListOf<ByteArray>()
    var result: ByteArray? = null
    var activeBuffer: ByteArray? = null
    var complete = false
    var failure: Exception? = null
    try {
        if (reportedSize != null && reportedSize > TransferLimits.MAX_FILE_BYTES) throw TransferFileException(TransferFailure.LimitExceeded)
        var size = 0
        while (true) {
            context.ensureActive()
            val buffer = ByteArray(minOf(8192, TransferLimits.MAX_FILE_BYTES + 1 - size))
            activeBuffer = buffer
            val count = input.read(buffer, 0, buffer.size)
            if (count == -1) break
            if (count <= 0 || count > buffer.size) throw TransferFileException(TransferFailure.ReadFailed)
            size += count
            chunks.add(if (count == buffer.size) buffer else buffer.copyOf(count).also { buffer.fill(0) })
            if (size > TransferLimits.MAX_FILE_BYTES) throw TransferFileException(TransferFailure.LimitExceeded)
        }
        val bytes = ByteArray(size)
        result = bytes
        var offset = 0
        for (chunk in chunks) {
            context.ensureActive()
            chunk.copyInto(bytes, offset)
            offset += chunk.size
        }
        complete = true
        return bytes
    } catch (error: Exception) {
        failure = error
        throw error
    } finally {
        activeBuffer?.fill(0)
        chunks.forEach { it.fill(0) }
        try {
            input.close()
        } catch (error: Exception) {
            result?.fill(0)
            if (failure == null) throw error else failure.addSuppressed(error)
        }
        if (!complete) result?.fill(0)
    }
}

internal suspend fun writeTransferBytes(output: TransferOutput, bytes: ByteArray) =
    writeTransferBytesBlocking(output, bytes, currentCoroutineContext())

internal fun writeTransferBytesBlocking(output: TransferOutput, bytes: ByteArray, context: CoroutineContext) {
    var failure: Exception? = null
    try {
        if (bytes.size > TransferLimits.MAX_FILE_BYTES) throw TransferFileException(TransferFailure.LimitExceeded)
        var offset = 0
        while (offset < bytes.size) {
            context.ensureActive()
            val count = output.write(bytes, offset, minOf(8192, bytes.size - offset))
            if (count <= 0 || count > minOf(8192, bytes.size - offset)) throw TransferFileException(TransferFailure.WriteFailed)
            offset += count
        }
        context.ensureActive()
        output.flush()
    } catch (error: Exception) {
        failure = error
        throw error
    } finally {
        try { output.close() } catch (error: Exception) {
            if (failure == null) throw error else failure.addSuppressed(error)
        }
    }
}

internal fun validTransferFileName(name: String): Boolean = name.length in 6..128 && name.endsWith(".it4s") &&
    name.encodeToByteArray().size <= 128 && name.none { it == '/' || it == '\\' || it.code < 32 || it.code == 127 } && name !in setOf(".it4s", "..it4s")
