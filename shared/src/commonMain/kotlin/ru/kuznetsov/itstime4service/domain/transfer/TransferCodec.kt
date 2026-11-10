package ru.kuznetsov.itstime4service.domain.transfer

interface TransferCodec {
    suspend fun encode(snapshot: TransferSnapshot): TransferOutcome<ByteArray>
    suspend fun decode(plaintext: ByteArray): TransferOutcome<TransferSnapshot>
}
