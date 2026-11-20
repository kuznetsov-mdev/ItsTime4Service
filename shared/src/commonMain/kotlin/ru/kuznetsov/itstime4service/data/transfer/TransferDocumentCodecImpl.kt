package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import ru.kuznetsov.itstime4service.domain.transfer.*

class TransferDocumentCodecImpl(
    private val codec: TransferCodec,
    private val cipher: TransferCipher,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TransferDocumentCodec {
    override suspend fun inspect(bytes: ByteArray): TransferOutcome<TransferProtection> = guarded {
        val parsed = TransferEnvelope.parse(bytes, currentCoroutineContext())
        try {
            TransferOutcome.Success(if (parsed.protection == 1) TransferProtection.PASSWORD else TransferProtection.PLAIN)
        } finally {
            parsed.body.fill(0)
            parsed.authenticatedHeader.fill(0)
        }
    }

    override suspend fun encode(snapshot: TransferSnapshot, password: SecretBytes?): TransferOutcome<ByteArray> = guarded {
        when (val encoded = codec.encode(snapshot)) {
            is TransferOutcome.Failure -> encoded
            is TransferOutcome.Success -> try {
                if (password == null) TransferOutcome.Success(TransferEnvelope.plain(encoded.value))
                else cipher.seal(encoded.value, password)
            } finally { encoded.value.fill(0) }
        }
    }

    override suspend fun decode(bytes: ByteArray, password: SecretBytes?): TransferOutcome<TransferSnapshot> = guarded {
        val parsed = TransferEnvelope.parse(bytes, currentCoroutineContext())
        try {
            if (parsed.protection == 0) codec.decode(parsed.body)
            else if (password == null) TransferOutcome.Failure(TransferFailure.AuthenticationFailed)
            else when (val opened = cipher.open(bytes, password)) {
                is TransferOutcome.Failure -> opened
                is TransferOutcome.Success -> try { codec.decode(opened.value) } finally { opened.value.fill(0) }
            }
        } finally {
            parsed.body.fill(0)
            parsed.authenticatedHeader.fill(0)
        }
    }

    private suspend fun <T> guarded(block: suspend () -> TransferOutcome<T>): TransferOutcome<T> = withContext(dispatcher) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (rejected: SnapshotRejected) { TransferOutcome.Failure(rejected.reason) }
        catch (_: Exception) { TransferOutcome.Failure(TransferFailure.InvalidEnvelope) }
    }
}
