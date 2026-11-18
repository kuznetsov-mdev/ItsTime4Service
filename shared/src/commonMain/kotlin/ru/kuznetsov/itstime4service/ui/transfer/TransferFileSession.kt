package ru.kuznetsov.itstime4service.ui.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure

internal class TransferFileSession(val main: CoroutineDispatcher = Dispatchers.Main.immediate) {
    val dialogs = TransferFileDialogs(main)
    private val operation = Mutex()

    suspend fun <T> run(mapFailure: (Exception) -> TransferFailure, block: suspend () -> TransferFileResult<T>): TransferFileResult<T> {
        if (!operation.tryLock()) return TransferFileResult.Failure(TransferFailure.FileOperationBusy)
        try {
            return coroutineScope {
                val host = withContext(main) { dialogs.owner?.takeIf { it.isActive } }
                    ?: return@coroutineScope TransferFileResult.Failure(TransferFailure.FileUnavailable)
                val caller = coroutineContext.job
                val registration = host.invokeOnCompletion { caller.cancel(CancellationException("File host ended")) }
                try {
                    block()
                } finally {
                    registration.dispose()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: TransferFileException) {
            return TransferFileResult.Failure(failure.reason)
        } catch (error: Exception) {
            return TransferFileResult.Failure(mapFailure(error))
        } finally {
            operation.unlock()
        }
    }
}
