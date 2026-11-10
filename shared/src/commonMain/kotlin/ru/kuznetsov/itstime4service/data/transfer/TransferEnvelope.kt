package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits
import kotlin.coroutines.CoroutineContext

internal object TransferEnvelope {
    data class Parsed(val protection: Int, val authenticatedHeader: ByteArray, val metadata: JsonObject, val body: ByteArray)

    fun plain(body: ByteArray): ByteArray {
        if (body.size > TransferLimits.MAX_PLAINTEXT_BYTES) throw SnapshotRejected(TransferFailure.LimitExceeded)
        return header(0, "{}".encodeToByteArray(), body.size) + body
    }

    fun header(protection: Int, metadata: ByteArray, bodySize: Int): ByteArray {
        valid(protection in 0..1 && bodySize >= 0)
        if (metadata.size > TransferLimits.MAX_METADATA_BYTES || bodySize > TransferLimits.MAX_PLAINTEXT_BYTES + if (protection == 1) 16 else 0) {
            throw SnapshotRejected(TransferFailure.LimitExceeded)
        }
        val prefix = byteArrayOf(0x49, 0x54, 0x34, 0x53, 1, protection.toByte()) + intBytes(metadata.size) + intBytes(bodySize)
        return prefix + metadata
    }

    fun parse(bytes: ByteArray, context: CoroutineContext): Parsed {
        if (bytes.size > TransferLimits.MAX_FILE_BYTES) throw SnapshotRejected(TransferFailure.LimitExceeded)
        valid(bytes.size >= 14 && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x49, 0x54, 0x34, 0x53)))
        if (bytes[4].toInt() != 1) throw SnapshotRejected(TransferFailure.UnsupportedVersion)
        val protection = bytes[5].toInt() and 0xff
        if (protection !in 0..1) throw SnapshotRejected(TransferFailure.UnsupportedProtection)
        val metadataSize = unsigned(bytes, 6)
        val bodySize = unsigned(bytes, 10)
        if (metadataSize > TransferLimits.MAX_METADATA_BYTES || bodySize > TransferLimits.MAX_PLAINTEXT_BYTES + if (protection == 1) 16 else 0) {
            throw SnapshotRejected(TransferFailure.LimitExceeded)
        }
        valid(14L + metadataSize + bodySize == bytes.size.toLong())
        valid(protection == 0 || bodySize >= 16)
        val metadataEnd = 14 + metadataSize.toInt()
        val metadata = try {
            StrictTransferJson.decode(bytes.copyOfRange(14, metadataEnd), context, TransferLimits.MAX_METADATA_BYTES) as? JsonObject
                ?: throw SnapshotRejected(TransferFailure.InvalidEnvelope)
        } catch (rejected: SnapshotRejected) {
            if (rejected.reason == TransferFailure.LimitExceeded) throw rejected
            throw SnapshotRejected(TransferFailure.InvalidEnvelope)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw SnapshotRejected(TransferFailure.InvalidEnvelope)
        }
        valid(protection == 1 || metadata.isEmpty())
        return Parsed(protection, bytes.copyOfRange(0, metadataEnd), metadata, bytes.copyOfRange(metadataEnd, bytes.size))
    }

    private fun unsigned(bytes: ByteArray, start: Int): Long = (start until start + 4).fold(0L) { value, index -> (value shl 8) or (bytes[index].toLong() and 0xff) }
    private fun intBytes(value: Int): ByteArray = ByteArray(4) { index -> (value ushr ((3 - index) * 8)).toByte() }
    private fun valid(value: Boolean) { if (!value) throw SnapshotRejected(TransferFailure.InvalidEnvelope) }
}
