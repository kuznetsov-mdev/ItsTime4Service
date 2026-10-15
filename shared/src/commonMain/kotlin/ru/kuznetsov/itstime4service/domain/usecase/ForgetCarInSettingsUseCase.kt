package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.first
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository

/**
 * Clears a removed car from the settings; called after the car is gone.
 * The car stops being the last opened one. If it was the car chosen for "open by default", the last opened car
 * takes its place, or the first remaining car when there is none, and the option stays as it was. With no cars
 * left the choice is cleared and the option is switched off.
 */
class ForgetCarInSettingsUseCase(
    private val settingsRepository: SettingsRepository,
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(carId: String) {
        val carIds = carRepository.observeCars().first().map { it.id }.filter { it != carId }
        settingsRepository.updateSettings { settings ->
            val lastOpenedCarId = settings.lastOpenedCarId?.takeIf { it != carId }
            when {
                carIds.isEmpty() -> settings.copy(lastOpenedCarId = lastOpenedCarId, openDefaultCar = false, defaultCarId = null)
                settings.defaultCarId == carId -> settings.copy(
                    lastOpenedCarId = lastOpenedCarId,
                    defaultCarId = lastOpenedCarId?.takeIf { it in carIds } ?: carIds.first(),
                )
                else -> settings.copy(lastOpenedCarId = lastOpenedCarId)
            }
        }
    }
}
