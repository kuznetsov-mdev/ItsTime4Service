package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import ru.kuznetsov.itstime4service.domain.transfer.TransferCodec
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferOutcome
import ru.kuznetsov.itstime4service.domain.transfer.TransferSnapshot

class TransferCodecImpl(private val dispatcher: CoroutineDispatcher = Dispatchers.Default) : TransferCodec {
    override suspend fun encode(snapshot: TransferSnapshot): TransferOutcome<ByteArray> = onWorker {
        TransferValidator.validate(snapshot)
        StrictTransferJson.encode(TransferDocument.encode(snapshot), currentCoroutineContext())
    }

    override suspend fun decode(plaintext: ByteArray): TransferOutcome<TransferSnapshot> = onWorker {
        TransferDocument.decode(StrictTransferJson.decode(plaintext, currentCoroutineContext())).also(TransferValidator::validate)
    }

    private suspend fun <T> onWorker(block: suspend () -> T): TransferOutcome<T> = withContext(dispatcher) {
        try {
            TransferOutcome.Success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (rejected: SnapshotRejected) {
            TransferOutcome.Failure(rejected.reason)
        } catch (_: Exception) {
            TransferOutcome.Failure(TransferFailure.InvalidSnapshot)
        }
    }
}
