package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Edits a car's brand, VIN and licence plate.
 * Returns `false` and changes nothing when the brand is blank.
 * The brand is trimmed; a blank VIN or plate is stored as `null`; the plate is cleaned up as in [normalizePlate].
 */
class UpdateCarUseCase(
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(carId: String, brand: String, vin: String?, plate: String?): Boolean {
        val trimmedBrand = brand.trim()
        if (trimmedBrand.isEmpty()) return false
        carRepository.updateCar(carId, trimmedBrand, vin?.trim()?.takeIf { it.isNotEmpty() }, normalizePlate(plate))
        return true
    }
}
