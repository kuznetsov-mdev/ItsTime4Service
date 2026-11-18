package ru.kuznetsov.itstime4service.ui.transfer

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal class AndroidTransferStorage(
    private val resolver: ContentResolver,
    root: File,
    private val worker: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val root = root.canonicalFile

    suspend fun read(uri: Uri): ByteArray {
        val cancellation = TransferIoCancellation()
        return cancellableTransferIo(worker, cancellation) {
            prune()
            val signal = CancellationSignal()
            cancellation.install { signal.cancel() }
            val reported = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null, signal)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0).takeIf { size -> size >= 0 } else null
            }
            currentCoroutineContext().ensureActive()
            val descriptor = resolver.openFileDescriptor(uri, "r", signal) ?: throw TransferFileException(TransferFailure.FileUnavailable)
            val stream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            val closed = AtomicBoolean(false)
            val close = { if (closed.compareAndSet(false, true)) stream.close() }
            cancellation.install { signal.cancel(); close() }
            readTransferBytes(object : TransferInput {
                override fun read(buffer: ByteArray, offset: Int, count: Int): Int = stream.read(buffer, offset, count)
                override fun close() = close()
            }, reported)
        }
    }

    suspend fun write(uri: Uri, bytes: ByteArray) {
        val cancellation = TransferIoCancellation()
        cancellableTransferIo(worker, cancellation) {
            val signal = CancellationSignal()
            cancellation.install { signal.cancel() }
            val descriptor = resolver.openFileDescriptor(uri, "wt", signal) ?: throw TransferFileException(TransferFailure.FileUnavailable)
            val stream = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
            val closed = AtomicBoolean(false)
            val close = { if (closed.compareAndSet(false, true)) stream.close() }
            cancellation.install { signal.cancel(); close() }
            writeTransferBytes(object : TransferOutput {
                override fun write(buffer: ByteArray, offset: Int, count: Int): Int {
                    stream.write(buffer, offset, count)
                    return count
                }
                override fun flush() = stream.flush()
                override fun close() = close()
            }, bytes)
        }
    }

    suspend fun stage(bytes: ByteArray, name: String): File = withContext(worker) {
        prune()
        if (!root.isDirectory && !root.mkdirs()) throw TransferFileException(TransferFailure.WriteFailed)
        val folder = File(root, UUID.randomUUID().toString())
        if (!folder.mkdir()) throw TransferFileException(TransferFailure.WriteFailed)
        val file = File(folder, name)
        try {
            val stream = FileOutputStream(file)
            writeTransferBytes(object : TransferOutput {
                override fun write(buffer: ByteArray, offset: Int, count: Int): Int {
                    stream.write(buffer, offset, count)
                    return count
                }
                override fun flush() { stream.flush(); stream.fd.sync() }
                override fun close() = stream.close()
            }, bytes)
            folder.setLastModified(now())
            file
        } catch (error: Exception) {
            remove(file)
            throw error
        }
    }

    suspend fun cleanExpired() = withContext(worker) { prune() }

    suspend fun discard(file: File) = withContext(worker) { remove(file) }

    internal fun prune() {
        root.listFiles().orEmpty().filter { folder ->
            UUID_PATTERN.matches(folder.name) && folder.isDirectory && folder.canonicalFile == folder.absoluteFile &&
                now() - folder.lastModified() >= RETENTION_MS
        }.forEach { folder ->
            folder.listFiles().orEmpty().filter { it.isFile && it.canonicalFile.parentFile == folder.canonicalFile }.forEach { it.delete() }
            folder.delete()
        }
    }

    private fun remove(file: File) {
        val folder = file.parentFile ?: return
        val parent = folder.parentFile ?: return
        if (parent.canonicalFile != root.canonicalFile || !UUID_PATTERN.matches(folder.name) ||
            file.canonicalFile.parentFile != folder.canonicalFile) return
        file.delete()
        folder.delete()
    }

    companion object {
        internal const val RETENTION_MS = 24L * 60 * 60 * 1000
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

internal fun androidTransferFailure(error: Exception, writing: Boolean): TransferFailure {
    var cause: Throwable? = error
    var unavailable = false
    repeat(8) {
        when (val current = cause) {
            is SecurityException -> return TransferFailure.PermissionDenied
            is FileNotFoundException -> unavailable = true
            is ErrnoException -> when (current.errno) {
                OsConstants.ENOSPC -> return TransferFailure.InsufficientSpace
                OsConstants.EACCES, OsConstants.EPERM -> return TransferFailure.PermissionDenied
                OsConstants.ENOENT -> unavailable = true
            }
        }
        cause = cause?.cause
    }
    return if (unavailable) TransferFailure.FileUnavailable else if (writing) TransferFailure.WriteFailed else TransferFailure.ReadFailed
}
