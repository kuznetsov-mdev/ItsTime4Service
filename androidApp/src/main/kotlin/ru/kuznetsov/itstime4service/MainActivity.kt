package ru.kuznetsov.itstime4service

import android.os.Bundle
import org.koin.android.ext.android.inject
import ru.kuznetsov.itstime4service.ui.transfer.TransferFiles
import ru.kuznetsov.itstime4service.ui.transfer.TransferFilesHost
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    private val transferFiles: TransferFiles by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            TransferFilesHost(transferFiles) { App() }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}