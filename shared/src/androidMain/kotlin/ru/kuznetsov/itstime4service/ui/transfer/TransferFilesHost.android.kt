package ru.kuznetsov.itstime4service.ui.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.job

@Composable
actual fun TransferFilesHost(files: TransferFiles, content: @Composable () -> Unit) {
    if (files is AndroidTransferFiles) key(files.sessionId) {
        val scope = rememberCoroutineScope()
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { files.picked(it, false) }
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { files.picked(it, true) }
        val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { files.shareReturned() }
        DisposableEffect(files, pick, save, share, scope) {
            val registration = files.attach(scope.coroutineContext.job) { request ->
                try {
                    when (request.kind) {
                        TransferFileDialogKind.PICK -> { files.pickId = request.id; pick.launch(arrayOf("*/*")) }
                        TransferFileDialogKind.SAVE -> { files.saveId = request.id; save.launch(request.fileName) }
                        TransferFileDialogKind.SHARE -> share.launch(files.shareIntent(request.id))
                    }
                } catch (_: Exception) {
                    files.launchFailed(request.id)
                }
            }
            onDispose { files.detach(registration) }
        }
    }
    content()
}
