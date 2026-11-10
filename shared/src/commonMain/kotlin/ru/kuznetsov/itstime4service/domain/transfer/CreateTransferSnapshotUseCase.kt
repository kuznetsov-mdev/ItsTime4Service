package ru.kuznetsov.itstime4service.domain.transfer

class CreateTransferSnapshotUseCase(private val repository: TransferRepository) {
    suspend operator fun invoke(selection: ExportSelection): TransferOutcome<TransferSnapshot> {
        if (selection is ExportSelection.Car && !selection.isValid()) {
            return TransferOutcome.Failure(TransferFailure.InvalidSnapshot)
        }
        return repository.readSnapshot(selection)
    }
}
