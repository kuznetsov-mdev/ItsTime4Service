package ru.kuznetsov.itstime4service.domain.transfer

enum class TransferProtection { PLAIN, PASSWORD }

interface TransferDocumentCodec {
    suspend fun inspect(bytes: ByteArray): TransferOutcome<TransferProtection>
    suspend fun encode(snapshot: TransferSnapshot, password: SecretBytes?): TransferOutcome<ByteArray>
    suspend fun decode(bytes: ByteArray, password: SecretBytes?): TransferOutcome<TransferSnapshot>
}
