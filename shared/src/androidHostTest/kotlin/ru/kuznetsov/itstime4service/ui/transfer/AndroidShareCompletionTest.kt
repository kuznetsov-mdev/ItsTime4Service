package ru.kuznetsov.itstime4service.ui.transfer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.service.chooser.ChooserResult
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidShareCompletionTest {
    private val context = RuntimeEnvironment.getApplication()
    private val receiver = TransferShareReceiver()
    private val recipient = ComponentName("synthetic.recipient", "synthetic.recipient.TransferSink")

    @Test
    @Config(sdk = [24])
    fun delayedSelectionAfterChooserReturnsCompletesAsShared() = shareTest { files, launched, operations ->
        val sharing = operations.startShare(files)
        val id = launched.receive().getOrThrow()
        files.shareReturned()
        advanceChooserClock(Duration.ofMillis(600))
        signalLegacySelection(id)
        advanceChooserClock(Duration.ofSeconds(3))
        assertIs<TransferFileResult.Success<SharedFile>>(sharing.await())
    }

    @Test
    fun api35TypedSelectionBeforeActivityResultCompletesAfterChooserReturns() = shareTest { files, launched, operations ->
        val sharing = operations.startShare(files)
        val id = launched.receive().getOrThrow()
        signalTypedResult(id, ChooserResult.CHOOSER_RESULT_SELECTED_COMPONENT, recipient)
        runCurrent()
        assertFalse(sharing.isCompleted)
        files.shareReturned()
        runCurrent()
        assertIs<TransferFileResult.Success<SharedFile>>(sharing.await())
    }

    @Test
    fun chooserReturnWithoutSelectionCompletesAsCancelled() = shareTest { files, launched, operations ->
        val sharing = operations.startShare(files)
        launched.receive().getOrThrow()
        files.shareReturned()
        advanceChooserClock(Duration.ofSeconds(3))
        assertEquals(TransferFileResult.Cancelled, sharing.await())
    }

    @Test
    fun api35UnrelatedChooserActionDoesNotConfirmFileHandoff() = shareTest { files, launched, operations ->
        val sharing = operations.startShare(files)
        val id = launched.receive().getOrThrow()
        signalTypedResult(id, ChooserResult.CHOOSER_RESULT_COPY, null)
        files.shareReturned()
        advanceChooserClock(Duration.ofSeconds(3))
        assertEquals(TransferFileResult.Cancelled, sharing.await())
    }

    @Test
    fun priorSelectionCannotCompleteTheNextShare() = shareTest { files, launched, operations ->
        val first = operations.startShare(files)
        val priorId = launched.receive().getOrThrow()
        files.shareReturned()
        advanceChooserClock(Duration.ofSeconds(3))
        assertEquals(TransferFileResult.Cancelled, first.await())

        val second = operations.startShare(files)
        val activeId = launched.receive().getOrThrow()
        files.shareReturned()
        signalLegacySelection(priorId)
        advanceChooserClock(Duration.ofMillis(600))
        assertFalse(second.isCompleted)

        signalTypedResult(activeId, ChooserResult.CHOOSER_RESULT_SELECTED_COMPONENT, recipient)
        assertIs<TransferFileResult.Success<SharedFile>>(second.await())
    }

    @Test
    fun cancelledCallerLeavesChooserReturnBoundToItsOldOperation() = shareTest { files, launched, operations ->
        val abandoned = operations.startShare(files)
        val abandonedId = launched.receive().getOrThrow()
        abandoned.cancelAndJoin()
        assertEquals(TransferFileResult.Failure(TransferFailure.FileOperationBusy), shareFile(files))

        files.shareReturned()
        advanceChooserClock(Duration.ofSeconds(3))
        val next = operations.startShare(files)
        val activeId = launched.receive().getOrThrow()
        signalLegacySelection(abandonedId)
        files.shareReturned()
        signalLegacySelection(activeId)
        assertIs<TransferFileResult.Success<SharedFile>>(next.await())
    }

    private fun shareTest(block: suspend TestScope.(AndroidTransferFiles, Channel<Result<String>>, CoroutineScope) -> Unit) = runTest {
        val tests = this
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            coroutineScope {
                val authority = "${context.packageName}.transfer-files"
                val provider = checkNotNull(context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA))
                FileProvider().attachInfo(context, provider)
                val files = AndroidTransferFiles(context)
                val launched = Channel<Result<String>>(1)
                val registration = files.attach(backgroundScope.coroutineContext.job) { request ->
                    val launch = runCatching {
                        files.shareIntent(request.id)
                        request.id
                    }
                    check(launched.trySend(launch).isSuccess)
                    launch.getOrThrow()
                }
                try {
                    tests.block(files, launched, this)
                } finally {
                    files.detach(registration)
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun CoroutineScope.startShare(files: AndroidTransferFiles) = async { shareFile(files) }

    private suspend fun shareFile(files: AndroidTransferFiles) = files.share(byteArrayOf(1, 2), "Synthetic.it4s")

    private fun TestScope.advanceChooserClock(duration: Duration) {
        shadowOf(Looper.getMainLooper()).idleFor(duration)
        runCurrent()
    }

    private fun signalLegacySelection(id: String) {
        receiver.onReceive(context, TransferShareReceiver.callbackIntent(context, id).putExtra(Intent.EXTRA_CHOSEN_COMPONENT, recipient))
    }

    private fun signalTypedResult(id: String, type: Int, component: ComponentName?) {
        val result = ReflectionHelpers.callConstructor(
            ChooserResult::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType!!, type),
            ClassParameter.from(ComponentName::class.java, component),
            ClassParameter.from(Boolean::class.javaPrimitiveType!!, false),
        )
        receiver.onReceive(context, TransferShareReceiver.callbackIntent(context, id).putExtra(Intent.EXTRA_CHOOSER_RESULT, result))
    }
}
