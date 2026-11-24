package ru.kuznetsov.itstime4service.domain.transfer

internal object UnusedImportRepository : ImportRepository {
    override suspend fun fingerprint(snapshot: TransferSnapshot): TransferOutcome<String> = error("Unused")
    override suspend fun readPreviousTaskMatches(snapshotId: String, snapshotDigest: String): TransferOutcome<Map<ImportTaskMatchKey, List<String>>> = error("Unused")
    override suspend fun prepareImport(request: ImportRequest): TransferOutcome<PreparedImport> = error("Unused")
    override suspend fun applyImport(plan: ImportPlan): TransferOutcome<ImportResult> = error("Unused")
    override suspend fun findOperation(operationId: String): TransferOutcome<ImportResult?> = error("Unused")
    override suspend fun pendingNotificationOperations(): TransferOutcome<List<String>> = error("Unused")
    override suspend fun recordNotificationState(operationId: String, state: NotificationState): TransferOutcome<ImportResult> = error("Unused")
}
