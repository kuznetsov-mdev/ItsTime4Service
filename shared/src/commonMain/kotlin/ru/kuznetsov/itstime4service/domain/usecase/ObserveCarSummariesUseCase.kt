package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Emits every car, active ones first in the order they were added, then sold ones, and re-emits on every change.
 */
class ObserveCarSummariesUseCase(
    private val carRepository: CarRepository,
) {
    operator fun invoke(): Flow<List<CarSummary>> = carRepository.observeCarSummaries()
}
