package ru.kuznetsov.itstime4service.ui.transfer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TransferFileSessionTest {
    @Test
    fun hostEndingCancelsOperationAndReplacementCanRun() = runTest {
        val session = TransferFileSession(StandardTestDispatcher(testScheduler))
        val owner = Job()
        session.dialogs.attach(owner) { }
        val running = async { session.run<Unit>({ TransferFailure.ReadFailed }) { awaitCancellation() } }
        runCurrent()
        assertEquals(TransferFileResult.Failure(TransferFailure.FileOperationBusy), session.run<Unit>({ TransferFailure.ReadFailed }) {
            fail("Parallel operation started")
        })
        owner.cancel()
        running.join()
        assertTrue(running.isCancelled)
        session.dialogs.attach(Job()) { }
        assertEquals(TransferFileResult.Success(Unit), session.run<Unit>({ TransferFailure.ReadFailed }) { TransferFileResult.Success(Unit) })
    }

    @Test
    fun absentHostDoesNotStartFileWork() = runTest {
        val session = TransferFileSession(StandardTestDispatcher(testScheduler))
        assertEquals(TransferFileResult.Failure(TransferFailure.FileUnavailable), session.run<Unit>({ TransferFailure.ReadFailed }) {
            fail("Unattached operation started")
        })
    }

    @Test
    fun nativeAndTypedFailuresKeepFixedReasonsAndReleaseLock() = runTest {
        val session = TransferFileSession(StandardTestDispatcher(testScheduler))
        session.dialogs.attach(Job()) { }
        assertEquals(TransferFileResult.Failure(TransferFailure.ReadFailed), session.run<Unit>({ TransferFailure.ReadFailed }) {
            throw IllegalStateException("Synthetic native diagnostic")
        })
        assertEquals(TransferFileResult.Failure(TransferFailure.LimitExceeded), session.run<Unit>({ TransferFailure.ReadFailed }) {
            throw TransferFileException(TransferFailure.LimitExceeded)
        })
    }
}
