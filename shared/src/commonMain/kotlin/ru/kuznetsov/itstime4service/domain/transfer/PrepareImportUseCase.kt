package ru.kuznetsov.itstime4service.domain.transfer

class PrepareImportUseCase(private val repository: TransferRepository) {
    suspend operator fun invoke(request: ImportRequest): TransferOutcome<PreparedImport> = repository.prepareImport(request)
}
