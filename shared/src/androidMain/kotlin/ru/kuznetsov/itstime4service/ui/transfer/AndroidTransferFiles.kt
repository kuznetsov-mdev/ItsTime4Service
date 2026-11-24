package ru.kuznetsov.itstime4service.ui.transfer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits
import java.io.File
import java.util.UUID

class AndroidTransferFiles(context: Context) : TransferFiles {
    private val context = context.applicationContext
    private val session = TransferFileSession()
    private val storage = AndroidTransferStorage(context.contentResolver, File(context.cacheDir, "transfer"))
    private val handles = mutableMapOf<String, Uri>()
    private var staged: File? = null
    private val handler = Handler(Looper.getMainLooper())
    internal val sessionId get() = session.dialogs.sessionId
    internal var pickId: String? = null
    internal var saveId: String? = null
    private var activeShare: ShareRequest? = null
    private var exposedToSystem = false

    internal fun attach(owner: Job, launch: (TransferFileDialogRequest) -> Unit): String {
        handles.clear()
        return session.dialogs.attach(owner, launch)
    }

    internal fun detach(registration: String) {
        if (session.dialogs.detach(registration, preservePending = true)) handles.clear()
    }

    override suspend fun pick(): TransferFileResult<PickedFileHandle> = session.run({ androidTransferFailure(it, false) }) {
        storage.cleanExpired()
        if (withContext(session.main) { handles.size >= 16 }) return@run TransferFileResult.Failure(TransferFailure.FileOperationBusy)
        when (val reply = session.dialogs.await(TransferFileDialogKind.PICK)) {
            is TransferFileDialogReply.Picked -> TransferFileResult.Success(PickedFileHandle(sessionId, reply.token))
            is TransferFileDialogReply.Failure -> TransferFileResult.Failure(reply.reason)
            TransferFileDialogReply.Cancelled -> TransferFileResult.Cancelled
            is TransferFileDialogReply.SaveTarget, TransferFileDialogReply.Saved, TransferFileDialogReply.Shared -> TransferFileResult.Failure(TransferFailure.ReadFailed)
        }
    }

    override suspend fun read(handle: PickedFileHandle): TransferFileResult<ByteArray> = session.run({ androidTransferFailure(it, false) }) {
        val uri = withContext(session.main) { handles[handle.token]?.takeIf { handle.owner == sessionId } }
            ?: return@run TransferFileResult.Failure(TransferFailure.FileUnavailable)
        TransferFileResult.Success(storage.read(uri))
    }

    override suspend fun save(bytes: ByteArray, fileName: String): TransferFileResult<SavedFile> = session.run({ androidTransferFailure(it, true) }) {
        validate(bytes, fileName)
        storage.cleanExpired()
        val captured = bytes.copyOf()
        try {
            when (val reply = session.dialogs.await(TransferFileDialogKind.SAVE, fileName)) {
                is TransferFileDialogReply.SaveTarget -> {
                    val uri = withContext(session.main) { handles.remove(reply.token) }
                        ?: return@run TransferFileResult.Failure(TransferFailure.FileUnavailable)
                    storage.write(uri, captured)
                    TransferFileResult.Success(SavedFile)
                }
                is TransferFileDialogReply.Failure -> TransferFileResult.Failure(reply.reason)
                TransferFileDialogReply.Cancelled -> TransferFileResult.Cancelled
                is TransferFileDialogReply.Picked, TransferFileDialogReply.Saved, TransferFileDialogReply.Shared -> TransferFileResult.Failure(TransferFailure.WriteFailed)
            }
        } finally {
            captured.fill(0)
        }
    }

    override suspend fun share(bytes: ByteArray, fileName: String): TransferFileResult<SharedFile> = session.run({ androidTransferFailure(it, true) }) {
        validate(bytes, fileName)
        storage.cleanExpired()
        val captured = bytes.copyOf()
        var file: File? = null
        try {
            file = storage.stage(captured, fileName)
            withContext(session.main) {
                staged = file
                exposedToSystem = false
            }
            when (val reply = session.dialogs.await(TransferFileDialogKind.SHARE, fileName)) {
                TransferFileDialogReply.Shared -> TransferFileResult.Success(SharedFile)
                is TransferFileDialogReply.Failure -> TransferFileResult.Failure(reply.reason)
                TransferFileDialogReply.Cancelled -> TransferFileResult.Cancelled
                is TransferFileDialogReply.Picked, is TransferFileDialogReply.SaveTarget, TransferFileDialogReply.Saved -> TransferFileResult.Failure(TransferFailure.WriteFailed)
            }
        } finally {
            captured.fill(0)
            val retain = withContext(NonCancellable + session.main) {
                val exposed = exposedToSystem
                if (exposed) activeShare?.retireSignal()
                staged = null
                exposedToSystem = false
                exposed
            }
            if (!retain && file != null) withContext(NonCancellable) { storage.discard(file) }
        }
    }

    override suspend fun release(handle: PickedFileHandle) = withContext(session.main) {
        if (handle.owner == sessionId) handles.remove(handle.token)
        Unit
    }

    internal fun picked(uri: Uri?, saving: Boolean) {
        val id = if (saving) saveId.also { saveId = null } else pickId.also { pickId = null }
        if (id == null) return
        if (uri == null) {
            session.dialogs.complete(id, TransferFileDialogReply.Cancelled)
            return
        }
        val key = UUID.randomUUID().toString()
        handles[key] = uri
        val reply = if (saving) TransferFileDialogReply.SaveTarget(key) else TransferFileDialogReply.Picked(key)
        if (!session.dialogs.complete(id, reply)) handles.remove(key)
    }

    internal fun shareIntent(id: String): Intent {
        val file = staged ?: throw TransferFileException(TransferFailure.FileUnavailable)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.transfer-files", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", uri)
        val callback = TransferShareReceiver.callbackIntent(context, id)
        val sender = PendingIntent.getBroadcast(context, 0, callback, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val deadline = Runnable { completeShare(id, TransferFileDialogReply.Cancelled) }
        activeShare = ShareRequest(id, sender, deadline)
        AndroidShareSignals.register(id) { shareSelected(id) }
        exposedToSystem = true
        return Intent.createChooser(send, null, sender.intentSender).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    internal fun shareReturned() {
        val share = activeShare ?: return
        if (share.returned) return
        share.returned = true
        if (share.selected) completeShare(share.id, TransferFileDialogReply.Shared)
        else handler.postDelayed(share.cancellationDeadline, SELECTION_WAIT_MS)
    }

    private fun shareSelected(id: String) {
        val share = activeShare?.takeIf { it.id == id } ?: return
        share.selected = true
        if (share.returned) completeShare(share.id, TransferFileDialogReply.Shared)
    }

    private fun completeShare(id: String, reply: TransferFileDialogReply) {
        val share = activeShare?.takeIf { it.id == id } ?: return
        activeShare = null
        handler.removeCallbacks(share.cancellationDeadline)
        share.retireSignal()
        session.dialogs.complete(id, reply)
    }

    internal fun launchFailed(id: String) {
        val failure = TransferFileDialogReply.Failure(TransferFailure.FileUnavailable)
        if (activeShare?.id == id) {
            exposedToSystem = false
            completeShare(id, failure)
        } else session.dialogs.complete(id, failure)
    }

    private fun validate(bytes: ByteArray, name: String) {
        if (bytes.size > TransferLimits.MAX_FILE_BYTES) throw TransferFileException(TransferFailure.LimitExceeded)
        if (!validTransferFileName(name)) throw TransferFileException(TransferFailure.WriteFailed)
    }

    private class ShareRequest(val id: String, val signal: PendingIntent, val cancellationDeadline: Runnable) {
        var selected = false
        var returned = false

        fun retireSignal() {
            AndroidShareSignals.remove(id)
            signal.cancel()
        }
    }

    private companion object {
        const val SELECTION_WAIT_MS = 2_000L
    }
}
