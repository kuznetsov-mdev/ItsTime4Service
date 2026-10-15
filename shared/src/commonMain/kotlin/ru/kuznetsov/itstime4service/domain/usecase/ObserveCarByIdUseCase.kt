package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Emits one car by its id and re-emits it on every change.
 * Emits `null` when the car does not exist, for example right after it was deleted.
 */
class ObserveCarByIdUseCase(
    private val carRepository: CarRepository,
) {
    operator fun invoke(carId: String): Flow<Car?> = carRepository.observeCarById(carId)
}
