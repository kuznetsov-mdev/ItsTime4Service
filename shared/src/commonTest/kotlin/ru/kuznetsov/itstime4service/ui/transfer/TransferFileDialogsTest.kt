package ru.kuznetsov.itstime4service.ui.transfer

import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TransferFileDialogsTest {
    @Test
    fun cancelledUserDialogIsNormalAndDuplicateCallbackIsIgnored() = runTest {
        val dialogs = TransferFileDialogs(StandardTestDispatcher(testScheduler))
        var shown: TransferFileDialogRequest? = null
        dialogs.attach(Job()) { shown = it }
        val result = async { dialogs.await(TransferFileDialogKind.PICK) }
        runCurrent()
        assertNotNull(shown)
        assertTrue(dialogs.complete(shown.id, TransferFileDialogReply.Cancelled))
        assertFalse(dialogs.complete(shown.id, TransferFileDialogReply.Shared))
        assertEquals(TransferFileDialogReply.Cancelled, result.await())
    }

    @Test
    fun missingHostAndParallelRequestHaveDistinctFailures() = runTest {
        val dialogs = TransferFileDialogs(StandardTestDispatcher(testScheduler))
        assertEquals(TransferFileDialogReply.Failure(TransferFailure.FileUnavailable), dialogs.await(TransferFileDialogKind.PICK))
        var id = ""
        dialogs.attach(Job()) { id = it.id }
        val first = async { dialogs.await(TransferFileDialogKind.PICK) }
        runCurrent()
        assertEquals(TransferFileDialogReply.Failure(TransferFailure.FileOperationBusy), dialogs.await(TransferFileDialogKind.SAVE))
        dialogs.complete(id, TransferFileDialogReply.Cancelled)
        first.await()
    }

    @Test
    fun coroutineCancellationDrainsOldDialogBeforeAllowingNext() = runTest {
        val dialogs = TransferFileDialogs(StandardTestDispatcher(testScheduler))
        var id = ""
        dialogs.attach(Job()) { id = it.id }
        val first = async { dialogs.await(TransferFileDialogKind.PICK) }
        runCurrent()
        first.cancel()
        runCurrent()
        assertEquals(TransferFileDialogReply.Failure(TransferFailure.FileOperationBusy), dialogs.await(TransferFileDialogKind.SAVE))
        assertFalse(dialogs.complete(id, TransferFileDialogReply.Cancelled))
        val next = async { dialogs.await(TransferFileDialogKind.SAVE) }
        runCurrent()
        assertTrue(dialogs.complete(id, TransferFileDialogReply.Saved))
        assertEquals(TransferFileDialogReply.Saved, next.await())
    }

    @Test
    fun staleHostDetachAndStaleCallbackCannotCancelReplacement() = runTest {
        val dialogs = TransferFileDialogs(StandardTestDispatcher(testScheduler))
        val old = dialogs.attach(Job()) { }
        val current = dialogs.attach(Job()) { }
        dialogs.detach(old, preservePending = false)
        val pending = async { dialogs.await(TransferFileDialogKind.PICK) }
        runCurrent()
        assertFalse(dialogs.complete("stale", TransferFileDialogReply.Cancelled))
        val id = assertNotNull(dialogs.pending).id
        dialogs.complete(id, TransferFileDialogReply.Cancelled)
        assertEquals(TransferFileDialogReply.Cancelled, pending.await())
        dialogs.detach(current, preservePending = false)
    }
}
