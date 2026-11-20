package ru.kuznetsov.itstime4service

import android.os.Bundle
import org.koin.android.ext.android.getKoin
import ru.kuznetsov.itstime4service.domain.transfer.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.kuznetsov.itstime4service.ui.transfer.*
import java.security.MessageDigest

class TransferFilesCheckActivity : ComponentActivity() {
    private val files by lazy { AndroidTransferFiles(this) }
    private var fixtureName = "Plain.it4s"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            TransferFilesHost(files) {
                AndroidView(factory = { context ->
                    val layout = LinearLayout(context).also {
                        it.orientation = LinearLayout.VERTICAL
                        it.setPadding(0, (48 * context.resources.displayMetrics.density).toInt(), 0, 0)
                    }
                    val status = TextView(context).also { it.text = "Ready"; it.textSize = 16f }
                    fun button(label: String, action: () -> Unit) {
                        layout.addView(Button(context).also { it.text = label; it.setOnClickListener { action() } })
                    }
                    fun fixture() = assets.open(fixtureName).use { it.readBytes() }
                    button("Crypto") { scope.launch {
                        status.text = "Working"
                        val document = getKoin().get<TransferDocumentCodec>()
                        val input = assets.open("Protected.it4s").use { it.readBytes() }
                        val secret = SecretBytes.fromPassword("Synthetic-file-check")
                        try {
                            val source = document.decode(input, secret)
                            check(source is TransferOutcome.Success)
                            val encoded = document.encode(source.value, secret)
                            check(encoded is TransferOutcome.Success)
                            try {
                                val reopened = document.decode(encoded.value, secret)
                                check(reopened is TransferOutcome.Success && reopened.value == source.value)
                                status.text = "Crypto PASS"
                            } finally { encoded.value.fill(0) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { status.text = "Crypto FAILED" }
                        finally { secret.close(); input.fill(0) }
                    } }
                    button("Plain") { fixtureName = "Plain.it4s"; status.text = "Ready Plain" }
                    button("Protected") { fixtureName = "Protected.it4s"; status.text = "Ready Protected" }
                    button("Save") { scope.launch {
                        try { status.text = result(files.save(fixture(), fixtureName)) }
                        catch (_: CancellationException) { status.text = "CoroutineCancelled" }
                    } }
                    button("Pick") { scope.launch {
                        try {
                            when (val picked = files.pick()) {
                                is TransferFileResult.Success -> {
                                    try {
                                        val read = files.read(picked.value)
                                        status.text = when (read) {
                                            is TransferFileResult.Success -> "Read " + digest(read.value)
                                            is TransferFileResult.Failure -> "Failed " + read.reason::class.simpleName
                                            TransferFileResult.Cancelled -> "Cancelled"
                                        }
                                    } finally { files.release(picked.value) }
                                }
                                is TransferFileResult.Failure -> status.text = "Failed " + picked.reason::class.simpleName
                                TransferFileResult.Cancelled -> status.text = "Cancelled"
                            }
                        } catch (_: CancellationException) { status.text = "CoroutineCancelled" }
                    } }
                    button("Share") { scope.launch {
                        java.io.File(filesDir, "received-sha.txt").writeText("")
                        try { status.text = result(files.share(fixture(), fixtureName)) }
                        catch (_: CancellationException) { status.text = "CoroutineCancelled" }
                    } }
                    button("Receipt") {
                        val receipt = java.io.File(filesDir, "received-sha.txt")
                        status.text = if (receipt.isFile) "Received " + receipt.readText() else "No receipt"
                    }
                    layout.addView(status)
                    layout
                })
            }
        }
    }

    private fun result(value: TransferFileResult<*>): String = when (value) {
        is TransferFileResult.Success -> when (value.value) { SavedFile -> "Saved"; SharedFile -> "Shared"; else -> "Success" }
        is TransferFileResult.Failure -> "Failed " + value.reason::class.simpleName
        TransferFileResult.Cancelled -> "Cancelled"
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
