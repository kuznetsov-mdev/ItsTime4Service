package ru.kuznetsov.itstime4service.ui.transfer

import androidx.compose.runtime.Composable
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure

interface TransferFiles {
    suspend fun pick(): TransferFileResult<PickedFileHandle>
    suspend fun read(handle: PickedFileHandle): TransferFileResult<ByteArray>
    suspend fun save(bytes: ByteArray, fileName: String = DEFAULT_FILE_NAME): TransferFileResult<SavedFile>
    suspend fun share(bytes: ByteArray, fileName: String = DEFAULT_FILE_NAME): TransferFileResult<SharedFile>
    suspend fun release(handle: PickedFileHandle)

    companion object {
        const val DEFAULT_FILE_NAME = "ItsTime4Service.it4s"
    }
}

class PickedFileHandle internal constructor(internal val owner: String, internal val token: String)

data object SavedFile
data object SharedFile

sealed interface TransferFileResult<out T> {
    data class Success<T>(val value: T) : TransferFileResult<T>
    data object Cancelled : TransferFileResult<Nothing>
    data class Failure(val reason: TransferFailure) : TransferFileResult<Nothing>
}

@Composable
expect fun TransferFilesHost(files: TransferFiles, content: @Composable () -> Unit)
