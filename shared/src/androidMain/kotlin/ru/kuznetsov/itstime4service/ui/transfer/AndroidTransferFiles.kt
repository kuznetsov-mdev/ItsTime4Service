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
    internal var shareId: String? = null
    private var shareChosen = false
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
        var handedOff = false
        var file: File? = null
        try {
            file = storage.stage(captured, fileName)
            withContext(session.main) { staged = file }
            when (val reply = session.dialogs.await(TransferFileDialogKind.SHARE, fileName)) {
                TransferFileDialogReply.Shared -> {
                    handedOff = true
                    TransferFileResult.Success(SharedFile)
                }
                is TransferFileDialogReply.Failure -> TransferFileResult.Failure(reply.reason)
                TransferFileDialogReply.Cancelled -> TransferFileResult.Cancelled
                is TransferFileDialogReply.Picked, is TransferFileDialogReply.SaveTarget, TransferFileDialogReply.Saved -> TransferFileResult.Failure(TransferFailure.WriteFailed)
            }
        } finally {
            captured.fill(0)
            withContext(NonCancellable + session.main) {
                handedOff = handedOff || shareChosen || exposedToSystem
                staged = null
            }
            if (!handedOff && file != null) withContext(NonCancellable) { storage.discard(file) }
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
        shareId = id
        shareChosen = false
        exposedToSystem = true
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.transfer-files", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", uri)
        AndroidShareSignals.register(id) { shareChosen = true }
        val callback = Intent(context, TransferShareReceiver::class.java).putExtra("operation", id)
        val sender = PendingIntent.getBroadcast(context, id.hashCode(), callback, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        return Intent.createChooser(send, null, sender.intentSender).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    internal fun shareReturned() {
        val id = shareId ?: return
        shareId = null
        handler.postDelayed({
            AndroidShareSignals.remove(id)
            session.dialogs.complete(id, if (shareChosen) TransferFileDialogReply.Shared else TransferFileDialogReply.Cancelled)
        }, 250)
    }

    internal fun launchFailed(id: String) {
        AndroidShareSignals.remove(id)
        session.dialogs.complete(id, TransferFileDialogReply.Failure(TransferFailure.FileUnavailable))
    }

    private fun validate(bytes: ByteArray, name: String) {
        if (bytes.size > TransferLimits.MAX_FILE_BYTES) throw TransferFileException(TransferFailure.LimitExceeded)
        if (!validTransferFileName(name)) throw TransferFileException(TransferFailure.WriteFailed)
    }
}
