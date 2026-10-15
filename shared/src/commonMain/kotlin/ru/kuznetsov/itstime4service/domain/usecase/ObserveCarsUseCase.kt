package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Emits the list of all cars and re-emits it on every change.
 */
class ObserveCarsUseCase(
    private val carRepository: CarRepository,
) {
    operator fun invoke(): Flow<List<Car>> = carRepository.observeCars()
}
