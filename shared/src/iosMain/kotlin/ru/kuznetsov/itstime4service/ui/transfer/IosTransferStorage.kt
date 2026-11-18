package ru.kuznetsov.itstime4service.ui.transfer

import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.EACCES
import platform.posix.EPERM
import platform.posix.ENOENT
import platform.posix.ENOSPC
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalUuidApi::class)
internal class IosTransferStorage(
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val root: NSURL = (NSFileManager.defaultManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).first() as NSURL)
        .URLByAppendingPathComponent("transfer", true)!!,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val scope: IosTransferSecurityScope = NativeIosTransferSecurityScope,
) {
    suspend fun read(url: NSURL): ByteArray {
        val cancellation = TransferIoCancellation()
        return cancellableTransferIo(worker, cancellation) {
            currentCoroutineContext().ensureActive()
            prune()
            if (!url.fileURL) throw TransferFileException(TransferFailure.FileUnavailable)
            val scoped = scope.start(url)
            val coordinator = NSFileCoordinator(null)
            cancellation.install { coordinator.cancel() }
            try {
                val context = currentCoroutineContext()
                var result: ByteArray? = null
                var failure: Exception? = null
                memScoped {
                    val error = alloc<ObjCObjectVar<NSError?>>()
                    error.value = null
                    coordinator.coordinateReadingItemAtURL(url, 0u, error.ptr) { coordinated ->
                        try {
                            context.ensureActive()
                            val resolved = coordinated ?: throw TransferFileException(TransferFailure.FileUnavailable)
                            val stream = NSInputStream.inputStreamWithURL(resolved) ?: throw TransferFileException(TransferFailure.FileUnavailable)
                            cancellation.install { coordinator.cancel(); stream.close() }
                            stream.open()
                            val hint = NSFileManager.defaultManager.attributesOfItemAtPath(resolved.path!!, null)
                                ?.get(NSFileSize) as? NSNumber
                            result = readTransferBytesBlocking(object : TransferInput {
                                override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
                                    val n = buffer.usePinned { stream.read(it.addressOf(offset).reinterpret(), count.toULong()) }.toInt()
                                    if (n < 0) throw TransferFileException(iosTransferFailure(stream.streamError, false))
                                    return if (n == 0) -1 else n
                                }
                                override fun close() = stream.close()
                            }, hint?.longLongValue?.takeIf { it >= 0 }, context)
                        } catch (error: Exception) {
                            failure = error
                        }
                    }
                    failure?.let { throw it }
                    error.value?.let { throw TransferFileException(iosTransferFailure(it, false)) }
                }
                result ?: throw TransferFileException(TransferFailure.ReadFailed)
            } finally {
                if (scoped) scope.stop(url)
            }
        }
    }

    suspend fun stage(bytes: ByteArray, name: String): NSURL = withContext(worker) {
        prune()
        val folder = root.URLByAppendingPathComponent(Uuid.random().toString(), true)!!
        val file = folder.URLByAppendingPathComponent(name, false)!!
        try {
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                error.value = null
                if (!NSFileManager.defaultManager.createDirectoryAtURL(folder, true, null, error.ptr)) {
                    throw TransferFileException(iosTransferFailure(error.value, true))
                }
            }
            val stream = NSOutputStream.outputStreamToFileAtPath(file.path!!, false)
            stream.open()
            writeTransferBytes(object : TransferOutput {
                override fun write(buffer: ByteArray, offset: Int, count: Int): Int {
                    val n = buffer.usePinned { stream.write(it.addressOf(offset).reinterpret(), count.toULong()) }.toInt()
                    if (n <= 0) throw TransferFileException(iosTransferFailure(stream.streamError, true))
                    return n
                }
                override fun flush() {
                    stream.streamError?.let { throw TransferFileException(iosTransferFailure(it, true)) }
                }
                override fun close() {
                    stream.close()
                    stream.streamError?.let { throw TransferFileException(iosTransferFailure(it, true)) }
                }
            }, bytes)
            NSFileManager.defaultManager.setAttributes(mapOf(NSFileModificationDate to NSDate.dateWithTimeIntervalSince1970(now() / 1000.0)), folder.path!!, null)
            file
        } catch (error: Exception) {
            remove(file)
            throw error
        }
    }

    suspend fun cleanExpired() = withContext(worker) { prune() }

    suspend fun discard(file: NSURL) = withContext(worker) { remove(file) }

    internal fun prune() {
        NSFileManager.defaultManager.contentsOfDirectoryAtURL(root, null, 0u, null).orEmpty().forEach { item ->
            val folder = item as? NSURL ?: return@forEach
            if (!UUID_PATTERN.matches(folder.lastPathComponent.orEmpty())) return@forEach
            val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(folder.path!!, null).orEmpty()
            if (attributes[NSFileType] != NSFileTypeDirectory) return@forEach
            val modified = attributes[NSFileModificationDate] as? NSDate ?: return@forEach
            if (now() - (modified.timeIntervalSince1970 * 1000).toLong() >= RETENTION_MS) {
                NSFileManager.defaultManager.removeItemAtURL(folder, null)
            }
        }
    }

    private fun remove(file: NSURL) {
        val folder = file.URLByDeletingLastPathComponent ?: return
        if (!UUID_PATTERN.matches(folder.lastPathComponent.orEmpty()) || folder.URLByDeletingLastPathComponent?.path != root.path) return
        NSFileManager.defaultManager.removeItemAtURL(folder, null)
    }

    companion object {
        internal const val RETENTION_MS = 24L * 60 * 60 * 1000
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

internal fun iosTransferFailure(error: NSError?, writing: Boolean): TransferFailure {
    var current = error
    repeat(8) {
        val cause = current ?: return@repeat
        if (cause.domain == NSCocoaErrorDomain) when (cause.code) {
            NSFileReadNoPermissionError, NSFileWriteNoPermissionError -> return TransferFailure.PermissionDenied
            NSFileReadNoSuchFileError, NSFileNoSuchFileError -> return TransferFailure.FileUnavailable
            NSFileWriteOutOfSpaceError -> return TransferFailure.InsufficientSpace
        }
        if (cause.domain == NSPOSIXErrorDomain) when (cause.code.toInt()) {
            EACCES, EPERM -> return TransferFailure.PermissionDenied
            ENOENT -> return TransferFailure.FileUnavailable
            ENOSPC -> return TransferFailure.InsufficientSpace
        }
        current = cause.userInfo[NSUnderlyingErrorKey] as? NSError
    }
    return if (writing) TransferFailure.WriteFailed else TransferFailure.ReadFailed
}


internal interface IosTransferSecurityScope {
    fun start(url: NSURL): Boolean
    fun stop(url: NSURL)
}

private object NativeIosTransferSecurityScope : IosTransferSecurityScope {
    override fun start(url: NSURL): Boolean = url.startAccessingSecurityScopedResource()
    override fun stop(url: NSURL) = url.stopAccessingSecurityScopedResource()
}
