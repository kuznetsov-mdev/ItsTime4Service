package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Edits a car's brand and VIN.
 * Returns `false` and changes nothing when the brand is blank.
 * The brand is trimmed; a blank VIN is stored as `null`.
 */
class UpdateCarUseCase(
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(carId: String, brand: String, vin: String?): Boolean {
        val trimmedBrand = brand.trim()
        if (trimmedBrand.isEmpty()) return false
        carRepository.updateCar(carId, trimmedBrand, vin?.trim()?.takeIf { it.isNotEmpty() })
        return true
    }
}
