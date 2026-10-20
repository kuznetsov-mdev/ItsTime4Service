package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Hides a car's VIN or shows it again. The choice is stored with the car, so every screen and the next launch show
 * the VIN the same way.
 */
class SetVinHiddenUseCase(
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(carId: String, hidden: Boolean) = carRepository.setVinHidden(carId, hidden)
}
