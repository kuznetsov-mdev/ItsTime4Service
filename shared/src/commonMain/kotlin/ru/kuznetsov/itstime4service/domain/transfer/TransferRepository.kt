package ru.kuznetsov.itstime4service.domain.transfer

interface TransferRepository {
    suspend fun readSnapshot(selection: ExportSelection): TransferOutcome<TransferSnapshot>
}
