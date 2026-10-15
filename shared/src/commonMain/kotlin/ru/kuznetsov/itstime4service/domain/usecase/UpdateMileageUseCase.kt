package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Sets a car's current odometer reading.
 * Returns `false` and changes nothing when the mileage is not positive.
 * A value lower than the current one is accepted, so a mistyped reading can be corrected.
 */
class UpdateMileageUseCase(
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(carId: String, mileageKm: Int): Boolean {
        if (mileageKm <= 0) return false
        carRepository.updateMileage(carId, mileageKm)
        return true
    }
}
