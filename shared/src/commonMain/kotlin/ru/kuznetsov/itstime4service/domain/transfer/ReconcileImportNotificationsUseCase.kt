package ru.kuznetsov.itstime4service.domain.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SyncRemindersUseCase

class ReconcileImportNotificationsUseCase(
    private val repository: TransferRepository,
    private val observeCars: ObserveCarsUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val syncReminders: SyncRemindersUseCase,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(operationId: String): TransferOutcome<ImportResult> = mutex.withLock {
        when (val found = repository.findOperation(operationId)) {
            is TransferOutcome.Failure -> found
            is TransferOutcome.Success -> {
                val result = found.value ?: return@withLock TransferOutcome.Failure(TransferFailure.InvalidOperationReuse)
                if (result.notifications == NotificationState.APPLIED) return@withLock TransferOutcome.Success(result)
                val state = try {
                    syncReminders(observeCars().first(), observeSettings().first())
                    NotificationState.APPLIED
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    NotificationState.RETRY_REQUIRED
                }
                repository.recordNotificationState(operationId, state)
            }
        }
    }

    suspend fun retryPending(): TransferOutcome<List<ImportResult>> = when (val pending = repository.pendingNotificationOperations()) {
        is TransferOutcome.Failure -> pending
        is TransferOutcome.Success -> {
            val results = mutableListOf<ImportResult>()
            for (id in pending.value) {
                when (val result = invoke(id)) {
                    is TransferOutcome.Failure -> return result
                    is TransferOutcome.Success -> results.add(result.value)
                }
            }
            TransferOutcome.Success(results)
        }
    }
}
