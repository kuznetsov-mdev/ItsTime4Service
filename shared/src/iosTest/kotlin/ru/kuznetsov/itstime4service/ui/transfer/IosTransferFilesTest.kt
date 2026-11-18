package ru.kuznetsov.itstime4service.ui.transfer

import kotlinx.cinterop.*
import kotlinx.coroutines.test.runTest
import platform.Foundation.*
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalUuidApi::class)
class IosTransferFilesTest {
    private val root = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent("transfer-test-" + Uuid.random(), true)!!
    private var time = 100000000L
    private val storage = IosTransferStorage(root = root, now = { time })

    @AfterTest
    fun close() { NSFileManager.defaultManager.removeItemAtURL(root, null) }

    @Test
    fun coordinatedBoundedStreamRoundTripsBytesAndCanReopenAfterClose() = runTest {
        val bytes = ByteArray(17000) { (it % 251).toByte() }
        val file = storage.stage(bytes, "Fixture.it4s")
        assertReadableCoordinate(file)
        assertContentEquals(bytes, storage.read(file))
        assertContentEquals(bytes, storage.read(file))
    }

    @Test
    fun missingFileHasFixedFailureAndNoNativeDiagnostic() = runTest {
        val missing = root.URLByAppendingPathComponent("Missing.it4s", false)!!
        assertEquals(TransferFailure.FileUnavailable, assertFailsWith<TransferFileException> { storage.read(missing) }.reason)
    }

    @Test
    fun cocoaAndPosixErrorsAreClassifiedWithoutMessages() {
        assertEquals(TransferFailure.PermissionDenied, iosTransferFailure(NSError(NSCocoaErrorDomain, NSFileReadNoPermissionError, null), false))
        assertEquals(TransferFailure.InsufficientSpace, iosTransferFailure(NSError(NSCocoaErrorDomain, NSFileWriteOutOfSpaceError, null), true))
        assertEquals(TransferFailure.FileUnavailable, iosTransferFailure(NSError(NSCocoaErrorDomain, NSFileReadNoSuchFileError, null), false))
    }

    @Test
    fun pruningKeepsUnmanagedFilesAndOnlyRemovesExpiredOwnStaging() = runTest {
        val first = storage.stage(byteArrayOf(1, 2), "First.it4s")
        val unmanaged = root.URLByAppendingPathComponent("unmanaged", true)!!
        NSFileManager.defaultManager.createDirectoryAtURL(unmanaged, true, null, null)
        time += IosTransferStorage.RETENTION_MS + 1
        val second = storage.stage(byteArrayOf(3), "Second.it4s")
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(first.path!!))
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(second.path!!))
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(unmanaged.path!!))
    }
    @Test
    fun securityScopeIsBalancedAfterReadFailureAndNotStoppedWhenNotStarted() = runTest {
        var starts = 0
        var stops = 0
        var granted = true
        val scope = object : IosTransferSecurityScope {
            override fun start(url: NSURL): Boolean { starts++; return granted }
            override fun stop(url: NSURL) { stops++ }
        }
        val scoped = IosTransferStorage(root = root, scope = scope)
        val missing = root.URLByAppendingPathComponent("Missing.it4s", false)!!
        assertFailsWith<TransferFileException> { scoped.read(missing) }
        assertEquals(1, starts)
        assertEquals(1, stops)
        granted = false
        val file = scoped.stage(byteArrayOf(1, 2), "Fixture.it4s")
        assertContentEquals(byteArrayOf(1, 2), scoped.read(file))
        assertEquals(2, starts)
        assertEquals(1, stops)
    }

    private fun assertReadableCoordinate(url: NSURL) = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        error.value = null
        var invoked = false
        val coordinator = NSFileCoordinator(null)
        coordinator.purposeIdentifier = "ru.kuznetsov.itstime4service.transfer-tests"
        coordinator.coordinateReadingItemAtURL(url, 0u, error.ptr) { invoked = true }
        val code = error.value?.code
        val domain = error.value?.domain?.takeIf { it in setOf(NSCocoaErrorDomain, NSPOSIXErrorDomain) } ?: "none-or-other"
        val underlying = error.value?.userInfo?.get(NSUnderlyingErrorKey) as? NSError
        val nested = underlying?.domain?.takeIf { it in setOf(NSCocoaErrorDomain, NSPOSIXErrorDomain) } ?: "none-or-other"
        val bundled = NSBundle.mainBundle.bundleIdentifier != null
        val exists = NSFileManager.defaultManager.fileExistsAtPath(url.path!!)
        val debug = (error.value?.userInfo?.get("NSDebugDescription") as? String).orEmpty().replace(Regex("(?:file://)?/[^\\s]+"), "<path>").take(160)
        assertTrue(invoked, "Foundation read accessor not invoked; domain=$domain code=$code nested=$nested:${underlying?.code} bundled=$bundled exists=$exists debug=$debug")
    }

}
