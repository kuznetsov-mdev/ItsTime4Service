package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.kuznetsov.itstime4service.domain.model.HistoryRecord
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository

/**
 * Emits the service history of every car, sold ones included, newest record first, each with its car, and
 * re-emits on every change. Records whose car no longer exists are left out.
 */
class ObserveAllHistoryUseCase(
    private val serviceHistoryRepository: ServiceHistoryRepository,
    private val carRepository: CarRepository,
) {
    operator fun invoke(): Flow<List<HistoryRecord>> = combine(
        serviceHistoryRepository.observeAllHistory(),
        carRepository.observeCarSummaries(),
    ) { entries, cars ->
        val carsById = cars.associateBy { it.id }
        entries
            .mapNotNull { entry -> carsById[entry.carId]?.let { HistoryRecord(entry, it) } }
            .sortedByDescending { it.entry.date }
    }
}
