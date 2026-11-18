package ru.kuznetsov.itstime4service.ui.transfer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class AndroidTransferFilesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val root = File(context.cacheDir, "transfer-test-" + UUID.randomUUID()).also { it.mkdir() }
    private val fixture = File(root, "fixture.it4s").also { it.writeBytes(ByteArray(17000) { (it % 251).toByte() }) }
    private val uri = Uri.parse("content://transfer.synthetic/file")
    private val provider = TestProvider(fixture)
    private var time = 100000000L
    private val storage = AndroidTransferStorage(context.contentResolver, File(root, "transfer"), now = { time })

    init {
        provider.attachInfo(context, ProviderInfo().also { it.authority = "transfer.synthetic"; it.exported = false; it.grantUriPermissions = true })
        ShadowContentResolver.registerProviderInternal("transfer.synthetic", provider)
    }

    @AfterTest
    fun close() { root.deleteRecursively() }

    @Test
    fun contentResolverReadDoesNotTrustReportedLength() = runTest {
        provider.size = 2
        assertContentEquals(fixture.readBytes(), storage.read(uri))
    }

    @Test
    fun writeClosesDescriptorAndPreservesAllBytes() = runTest {
        val bytes = ByteArray(19000) { (it % 239).toByte() }
        storage.write(uri, bytes)
        assertContentEquals(bytes, fixture.readBytes())
    }

    @Test
    fun permissionAndMissingProviderErrorsAreFixedReasons() = runTest {
        provider.deny = true
        assertEquals(TransferFailure.PermissionDenied, androidTransferFailure(assertFailsWith<SecurityException> { storage.read(uri) }, false))
        provider.deny = false
        provider.missing = true
        assertEquals(TransferFailure.FileUnavailable, androidTransferFailure(assertFailsWith<FileNotFoundException> { storage.read(uri) }, false))
        assertEquals(TransferFailure.InsufficientSpace, androidTransferFailure(IOException(ErrnoException("write", OsConstants.ENOSPC)), true))
    }

    @Test
    fun nestedNativeErrorTakesPriorityOverMissingFileWrapper() {
        val full = FileNotFoundException("Synthetic provider wrapper").also { it.initCause(ErrnoException("open", OsConstants.ENOSPC)) }
        val denied = FileNotFoundException("Synthetic provider wrapper").also { it.initCause(ErrnoException("open", OsConstants.EACCES)) }
        assertEquals(TransferFailure.InsufficientSpace, androidTransferFailure(full, true))
        assertEquals(TransferFailure.PermissionDenied, androidTransferFailure(denied, true))
    }

    @Test
    fun stagingIsPrivateAndPrunesOnlyExpiredOwnedDirectories() = runTest {
        val first = storage.stage(byteArrayOf(1, 2), "First.it4s")
        val foreign = File(assertNotNull(assertNotNull(first.parentFile).parentFile), "unmanaged").also { it.mkdir() }
        val foreignFile = File(foreign, "keep.it4s").also { it.writeBytes(byteArrayOf(9)) }
        time += AndroidTransferStorage.RETENTION_MS + 1
        val second = storage.stage(byteArrayOf(3), "Second.it4s")
        assertFalse(first.exists())
        assertTrue(second.exists())
        assertTrue(foreignFile.exists())
        assertEquals(assertNotNull(second.parentFile).canonicalPath, assertNotNull(second.canonicalFile.parentFile).path)
    }

    private class TestProvider(private val file: File) : ContentProvider() {
        var size = file.length()
        var deny = false
        var missing = false
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            if (deny) throw SecurityException("Synthetic denial")
            return MatrixCursor(arrayOf(OpenableColumns.SIZE)).also { it.addRow(arrayOf(size)) }
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (deny) throw SecurityException("Synthetic denial")
            if (missing) throw FileNotFoundException("Synthetic unavailable file")
            return ParcelFileDescriptor.open(file, if (mode.contains('w')) ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE else ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun getType(uri: Uri) = "application/octet-stream"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
