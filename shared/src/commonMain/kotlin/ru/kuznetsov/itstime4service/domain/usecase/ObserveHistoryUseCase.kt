package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository

/**
 * Emits a car's service history, newest record first, and re-emits it on every change.
 */
class ObserveHistoryUseCase(
    private val serviceHistoryRepository: ServiceHistoryRepository,
) {
    operator fun invoke(carId: String): Flow<List<ServiceHistoryEntry>> =
        serviceHistoryRepository.observeHistory(carId).map { entries ->
            entries.sortedByDescending { it.date }
        }
}
