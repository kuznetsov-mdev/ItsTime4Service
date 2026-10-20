package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Hides a car's licence plate or shows it again. The choice is stored with the car, so every screen and the next
 * launch show the plate the same way.
 */
class SetPlateHiddenUseCase(
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(carId: String, hidden: Boolean) = carRepository.setPlateHidden(carId, hidden)
}
