package ru.kuznetsov.itstime4service.domain.transfer

class ApplyImportUseCase(
    private val repository: TransferRepository,
    private val reconcile: ReconcileImportNotificationsUseCase,
) {
    suspend operator fun invoke(plan: ImportPlan): TransferOutcome<ImportResult> = when (val result = repository.applyImport(plan)) {
        is TransferOutcome.Failure -> result
        is TransferOutcome.Success -> when (val synced = reconcile(result.value.operationId)) {
            is TransferOutcome.Failure -> TransferOutcome.Success(result.value.copy(notifications = NotificationState.RETRY_REQUIRED))
            is TransferOutcome.Success -> TransferOutcome.Success(synced.value.copy(alreadyApplied = result.value.alreadyApplied))
        }
    }
}
