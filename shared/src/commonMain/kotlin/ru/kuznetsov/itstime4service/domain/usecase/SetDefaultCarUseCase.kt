package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.first
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository

/**
 * Switches "open by default" and chooses the car the app starts on.
 * A given [carId] becomes the chosen car. Switching on without a chosen car that still exists picks
 * the last opened car, or the first car in the list when there is none. Switching off keeps the choice,
 * so it comes back when the option is switched on again.
 */
class SetDefaultCarUseCase(
    private val settingsRepository: SettingsRepository,
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(enabled: Boolean, carId: String? = null) {
        val carIds = carRepository.observeCars().first().map { it.id }
        settingsRepository.updateSettings { settings ->
            val chosen = carId
                ?: settings.defaultCarId?.takeIf { it in carIds }
                ?: settings.lastOpenedCarId?.takeIf { it in carIds }
                ?: carIds.firstOrNull()
            settings.copy(openDefaultCar = enabled, defaultCarId = if (enabled) chosen else settings.defaultCarId)
        }
    }
}
