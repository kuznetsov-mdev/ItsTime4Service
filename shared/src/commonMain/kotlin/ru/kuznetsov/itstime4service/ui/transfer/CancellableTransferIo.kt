package ru.kuznetsov.itstime4service.ui.transfer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalAtomicApi::class)
internal class TransferIoCancellation {
    private val cancelled = AtomicBoolean(false)
    private val action = AtomicReference<(() -> Unit)?>(null)

    fun install(close: () -> Unit) {
        action.store(close)
        if (cancelled.load()) cancel()
    }

    fun clear() { action.store(null) }

    fun cancel() {
        cancelled.store(true)
        try { action.exchange(null)?.invoke() } catch (_: Exception) { }
    }
}

internal suspend fun <T> cancellableTransferIo(
    worker: CoroutineDispatcher,
    cancellation: TransferIoCancellation,
    block: suspend () -> T,
): T = coroutineScope {
    suspendCancellableCoroutine { waiting ->
        waiting.invokeOnCancellation { cancellation.cancel() }
        launch(worker) {
            try {
                waiting.resume(block(), onCancellation = { _, value, _ -> if (value is ByteArray) value.fill(0) })
            } catch (error: Exception) {
                waiting.resumeWithException(error)
            } finally {
                cancellation.clear()
            }
        }
    }
}
