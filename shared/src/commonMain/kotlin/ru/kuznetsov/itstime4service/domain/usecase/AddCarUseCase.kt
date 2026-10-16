package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Validates and stores a car from the add-car form.
 * Returns the new car's id, or `null` when the brand is blank or the mileage is not positive.
 * Tasks go through [NormalizeTaskUseCase]; the ones it rejects are dropped.
 * A blank VIN or plate is stored as `null`; the plate is cleaned up as in [normalizePlate].
 */
class AddCarUseCase(
    private val carRepository: CarRepository,
    private val normalizeTask: NormalizeTaskUseCase,
) {
    suspend operator fun invoke(car: NewCar): String? {
        val brand = car.brand.trim()
        if (brand.isEmpty() || car.mileageKm <= 0) return null
        return carRepository.addCar(
            car.copy(
                brand = brand,
                vin = car.vin?.trim()?.takeIf { it.isNotEmpty() },
                plate = normalizePlate(car.plate),
                tasks = car.tasks.mapNotNull { normalizeTask(it) },
            ),
        )
    }
}
