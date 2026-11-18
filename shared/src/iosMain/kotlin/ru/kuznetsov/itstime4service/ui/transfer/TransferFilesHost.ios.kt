package ru.kuznetsov.itstime4service.ui.transfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.coroutines.job

@Composable
actual fun TransferFilesHost(files: TransferFiles, content: @Composable () -> Unit) {
    if (files is IosTransferFiles) {
        val controller = LocalUIViewController.current
        val scope = rememberCoroutineScope()
        DisposableEffect(files, controller, scope) {
            val registration = files.attach(controller, scope.coroutineContext.job)
            onDispose { files.detach(registration) }
        }
    }
    content()
}
