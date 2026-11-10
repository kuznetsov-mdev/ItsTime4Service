package ru.kuznetsov.itstime4service.domain.transfer

interface TransferCipher {
    suspend fun seal(plaintext: ByteArray, password: SecretBytes): TransferOutcome<ByteArray>
    suspend fun open(envelope: ByteArray, password: SecretBytes): TransferOutcome<ByteArray>
}
