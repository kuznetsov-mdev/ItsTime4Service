package ru.kuznetsov.itstime4service.ui.transfer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTTypeData
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class, ExperimentalUuidApi::class)
class IosTransferFiles : TransferFiles {
    private val session = TransferFileSession()
    private val storage = IosTransferStorage()
    private val handles = mutableMapOf<String, NSURL>()
    private var presenter: UIViewController? = null
    private var presented: UIViewController? = null
    private var delegate: FilePickerDelegate? = null
    private var staged: NSURL? = null
    private var registration: String? = null
    private var ownedJob: Job? = null
    internal val sessionId get() = session.dialogs.sessionId

    fun attach(controller: UIViewController) {
        detach()
        val owner = Job()
        ownedJob = owner
        attach(controller, owner)
    }

    fun detach() { registration?.let { detach(it) } }

    internal fun attach(controller: UIViewController, owner: Job): String {
        registration?.let { detach(it) }
        handles.clear()
        presenter = controller
        return session.dialogs.attach(owner, ::launch).also { registration = it }
    }

    internal fun detach(token: String) {
        if (registration != token) return
        presented?.dismissViewControllerAnimated(false, null)
        presented = null
        delegate = null
        presenter = null
        handles.clear()
        ownedJob?.cancel()
        ownedJob = null
        session.dialogs.detach(token, preservePending = false)
        registration = null
    }

    override suspend fun pick(): TransferFileResult<PickedFileHandle> = session.run({ TransferFailure.ReadFailed }) {
        storage.cleanExpired()
        if (withContext(session.main) { handles.size >= 16 }) return@run TransferFileResult.Failure(TransferFailure.FileOperationBusy)
        when (val result = session.dialogs.await(TransferFileDialogKind.PICK)) {
            is TransferFileDialogReply.Picked -> TransferFileResult.Success(PickedFileHandle(sessionId, result.token))
            is TransferFileDialogReply.Failure -> TransferFileResult.Failure(result.reason)
            TransferFileDialogReply.Cancelled -> TransferFileResult.Cancelled
            is TransferFileDialogReply.SaveTarget, TransferFileDialogReply.Saved, TransferFileDialogReply.Shared -> TransferFileResult.Failure(TransferFailure.ReadFailed)
        }
    }

    override suspend fun read(handle: PickedFileHandle): TransferFileResult<ByteArray> = session.run({ TransferFailure.ReadFailed }) {
        val url = withContext(session.main) { handles[handle.token]?.takeIf { handle.owner == sessionId } }
            ?: return@run TransferFileResult.Failure(TransferFailure.FileUnavailable)
        TransferFileResult.Success(storage.read(url))
    }

    override suspend fun save(bytes: ByteArray, fileName: String): TransferFileResult<SavedFile> = stagedOperation(bytes, fileName, TransferFileDialogKind.SAVE) { SavedFile }
    override suspend fun share(bytes: ByteArray, fileName: String): TransferFileResult<SharedFile> = stagedOperation(bytes, fileName, TransferFileDialogKind.SHARE) { SharedFile }

    private suspend fun <T> stagedOperation(bytes: ByteArray, name: String, kind: TransferFileDialogKind, success: () -> T): TransferFileResult<T> =
        session.run({ TransferFailure.WriteFailed }) {
            if (bytes.size > TransferLimits.MAX_FILE_BYTES) throw TransferFileException(TransferFailure.LimitExceeded)
            if (!validTransferFileName(name)) throw TransferFileException(TransferFailure.WriteFailed)
            val captured = bytes.copyOf()
            var file: NSURL? = null
            var settled = false
            var shared = false
            try {
                file = storage.stage(captured, name)
                withContext(session.main) { staged = file }
                val reply = session.dialogs.await(kind, name)
                settled = true
                when (reply) {
                    TransferFileDialogReply.Saved -> if (kind == TransferFileDialogKind.SAVE) TransferFileResult.Success(success()) else TransferFileResult.Failure(TransferFailure.WriteFailed)
                    TransferFileDialogReply.Shared -> {
                        shared = true
                        if (kind == TransferFileDialogKind.SHARE) TransferFileResult.Success(success()) else TransferFileResult.Failure(TransferFailure.WriteFailed)
                    }
                    TransferFileDialogReply.Cancelled -> TransferFileResult.Cancelled
                    is TransferFileDialogReply.Failure -> TransferFileResult.Failure(reply.reason)
                    is TransferFileDialogReply.Picked, is TransferFileDialogReply.SaveTarget -> TransferFileResult.Failure(TransferFailure.WriteFailed)
                }
            } finally {
                captured.fill(0)
                withContext(NonCancellable + session.main) { staged = null }
                if (settled && !shared && file != null) withContext(NonCancellable) { storage.discard(file) }
            }
        }

    override suspend fun release(handle: PickedFileHandle) = withContext(session.main) {
        if (handle.owner == sessionId) handles.remove(handle.token)
        Unit
    }

    private fun launch(request: TransferFileDialogRequest) {
        val controller = presenter
        if (controller == null || controller.view.window == null || controller.presentedViewController != null) {
            finish(request.id, TransferFileDialogReply.Failure(TransferFailure.FileUnavailable))
            return
        }
        when (request.kind) {
            TransferFileDialogKind.PICK, TransferFileDialogKind.SAVE -> {
                val picker = if (request.kind == TransferFileDialogKind.PICK) {
                    UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = false)
                } else {
                    val file = staged ?: throw TransferFileException(TransferFailure.FileUnavailable)
                    UIDocumentPickerViewController(forExportingURLs = listOf(file), asCopy = true)
                }
                picker.allowsMultipleSelection = false
                val listener = FilePickerDelegate(this, request)
                delegate = listener
                picker.delegate = listener
                presented = picker
                controller.presentViewController(picker, true) { picker.presentationController?.delegate = listener }
            }
            TransferFileDialogKind.SHARE -> {
                val file = staged ?: throw TransferFileException(TransferFailure.FileUnavailable)
                val sheet = UIActivityViewController(listOf(file), null)
                sheet.popoverPresentationController?.let {
                    it.sourceView = controller.view
                    it.sourceRect = controller.view.bounds
                }
                sheet.completionWithItemsHandler = { _, completed, _, error ->
                    val reply = when {
                        error != null -> TransferFileDialogReply.Failure(iosTransferFailure(error, true))
                        completed -> TransferFileDialogReply.Shared
                        else -> TransferFileDialogReply.Cancelled
                    }
                    finish(request.id, reply)
                }
                presented = sheet
                controller.presentViewController(sheet, true, null)
            }
        }
    }

    internal fun picked(request: TransferFileDialogRequest, urls: List<*>) {
        if (request.kind == TransferFileDialogKind.SAVE) {
            finish(request.id, if (urls.isEmpty()) TransferFileDialogReply.Cancelled else TransferFileDialogReply.Saved)
            return
        }
        val url = urls.singleOrNull() as? NSURL
        if (url == null) { finish(request.id, TransferFileDialogReply.Failure(TransferFailure.FileUnavailable)); return }
        val key = Uuid.random().toString()
        handles[key] = url
        if (!finish(request.id, TransferFileDialogReply.Picked(key))) handles.remove(key)
    }

    internal fun finish(id: String, reply: TransferFileDialogReply): Boolean {
        if (session.dialogs.pending?.id != id) return false
        presented = null
        delegate = null
        return session.dialogs.complete(id, reply)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class FilePickerDelegate(
    private val files: IosTransferFiles,
    private val request: TransferFileDialogRequest,
) : NSObject(), UIDocumentPickerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        files.picked(request, didPickDocumentsAtURLs)
    }
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        files.finish(request.id, TransferFileDialogReply.Cancelled)
    }
    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        files.finish(request.id, TransferFileDialogReply.Cancelled)
    }
}
