package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository

/**
 * Deletes a car together with its tasks and its service history.
 */
class DeleteCarUseCase(
    private val carRepository: CarRepository,
    private val serviceHistoryRepository: ServiceHistoryRepository,
) {
    suspend operator fun invoke(carId: String) {
        carRepository.deleteCar(carId)
        serviceHistoryRepository.deleteHistory(carId)
    }
}
