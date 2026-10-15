package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Deletes a car completely — an active or a sold one — with its tasks and its whole service history, then
 * clears it from the settings (see [ForgetCarInSettingsUseCase]). This is the only way to remove history records.
 */
class PurgeCarUseCase(
    private val carRepository: CarRepository,
    private val forgetCarInSettings: ForgetCarInSettingsUseCase,
) {
    suspend operator fun invoke(carId: String) {
        carRepository.purgeCar(carId)
        forgetCarInSettings(carId)
    }
}
