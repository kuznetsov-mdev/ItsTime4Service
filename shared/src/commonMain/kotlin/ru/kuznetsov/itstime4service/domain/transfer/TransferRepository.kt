package ru.kuznetsov.itstime4service.domain.transfer

interface ImportRepository {
    suspend fun fingerprint(snapshot: TransferSnapshot): TransferOutcome<String>
    suspend fun prepareImport(request: ImportRequest): TransferOutcome<PreparedImport>
    suspend fun applyImport(plan: ImportPlan): TransferOutcome<ImportResult>
    suspend fun findOperation(operationId: String): TransferOutcome<ImportResult?>
    suspend fun pendingNotificationOperations(): TransferOutcome<List<String>>
    suspend fun recordNotificationState(operationId: String, state: NotificationState): TransferOutcome<ImportResult>
}

interface TransferRepository : ImportRepository {
    suspend fun readSnapshot(selection: ExportSelection): TransferOutcome<TransferSnapshot>
}
