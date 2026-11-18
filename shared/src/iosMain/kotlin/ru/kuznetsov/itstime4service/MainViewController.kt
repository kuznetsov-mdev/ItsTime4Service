package ru.kuznetsov.itstime4service

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.compose.koinInject
import ru.kuznetsov.itstime4service.ui.transfer.TransferFiles
import ru.kuznetsov.itstime4service.ui.transfer.TransferFilesHost

fun MainViewController() = ComposeUIViewController {
    TransferFilesHost(koinInject<TransferFiles>()) { App() }
}