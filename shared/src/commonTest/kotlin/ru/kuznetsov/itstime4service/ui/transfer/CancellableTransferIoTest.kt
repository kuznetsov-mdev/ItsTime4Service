package ru.kuznetsov.itstime4service.ui.transfer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CancellableTransferIoTest {
    @Test
    fun cancellationClosesRegisteredResourceAndWaitsForStructuredWorker() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancellation = TransferIoCancellation()
        var closes = 0
        val task = async {
            cancellableTransferIo(StandardTestDispatcher(testScheduler), cancellation) {
                cancellation.install { closes++; release.complete(Unit) }
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        task.cancel()
        task.join()
        assertTrue(task.isCancelled)
        assertEquals(1, closes)
    }

    @Test
    fun resourceRegisteredAfterCancellationIsClosedImmediately() {
        val cancellation = TransferIoCancellation()
        cancellation.cancel()
        var closes = 0
        cancellation.install { closes++ }
        cancellation.cancel()
        assertEquals(1, closes)
    }
}
