package ru.kuznetsov.itstime4service.ui.transfer

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import kotlin.coroutines.resume
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal enum class TransferFileDialogKind { PICK, SAVE, SHARE }

internal data class TransferFileDialogRequest(val id: String, val kind: TransferFileDialogKind, val fileName: String)

internal sealed interface TransferFileDialogReply {
    data class Picked(val token: String) : TransferFileDialogReply
    data class SaveTarget(val token: String) : TransferFileDialogReply
    data object Saved : TransferFileDialogReply
    data object Shared : TransferFileDialogReply
    data object Cancelled : TransferFileDialogReply
    data class Failure(val reason: TransferFailure) : TransferFileDialogReply
}

@OptIn(ExperimentalUuidApi::class)
internal class TransferFileDialogs(private val main: CoroutineDispatcher = Dispatchers.Main.immediate) {
    val sessionId = Uuid.random().toString()
    private var registration: String? = null
    private var launcher: ((TransferFileDialogRequest) -> Unit)? = null
    var owner: Job? = null
        private set
    var pending: TransferFileDialogRequest? = null
        private set
    private var continuation: CancellableContinuation<TransferFileDialogReply>? = null

    fun attach(job: Job, launch: (TransferFileDialogRequest) -> Unit): String {
        continuation?.cancel(CancellationException("File host replaced"))
        val token = Uuid.random().toString()
        registration = token
        owner = job
        launcher = launch
        return token
    }

    fun detach(token: String, preservePending: Boolean): Boolean {
        if (registration != token) return false
        continuation?.cancel(CancellationException("File host detached"))
        if (!preservePending) {
            pending = null
            continuation = null
        }
        registration = null
        owner = null
        launcher = null
        return true
    }

    suspend fun await(kind: TransferFileDialogKind, fileName: String = TransferFiles.DEFAULT_FILE_NAME): TransferFileDialogReply = withContext(main) {
        val launch = launcher
        if (launch == null || owner?.isActive != true) return@withContext TransferFileDialogReply.Failure(TransferFailure.FileUnavailable)
        if (pending != null) return@withContext TransferFileDialogReply.Failure(TransferFailure.FileOperationBusy)
        suspendCancellableCoroutine<TransferFileDialogReply> { waiting ->
            val request = TransferFileDialogRequest(Uuid.random().toString(), kind, fileName)
            pending = request
            continuation = waiting
            try {
                launch(request)
            } catch (_: Exception) {
                complete(request.id, TransferFileDialogReply.Failure(TransferFailure.FileUnavailable))
            }
        }
    }

    fun complete(id: String, result: TransferFileDialogReply): Boolean {
        if (pending?.id != id) return false
        val waiting = continuation
        pending = null
        continuation = null
        if (waiting?.isActive != true) return false
        waiting.resume(result)
        return true
    }
}
