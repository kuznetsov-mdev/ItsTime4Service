package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Removes a maintenance task from a car.
 * Service history records of that task are kept.
 */
class DeleteTaskUseCase(
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(carId: String, taskId: String) {
        carRepository.deleteTask(carId, taskId)
    }
}
