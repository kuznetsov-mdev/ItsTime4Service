package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.first
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository

/**
 * Decides which car screen the app opens on start: the car chosen in settings while "open by default" is on,
 * otherwise the last opened car. Returns `null` — start on the car list — when that car no longer exists
 * or none was chosen or opened yet.
 */
class GetLaunchCarUseCase(
    private val settingsRepository: SettingsRepository,
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(): String? {
        val settings = settingsRepository.observeSettings().first()
        val carId = if (settings.openDefaultCar) settings.defaultCarId else settings.lastOpenedCarId
        return carId?.takeIf { id -> carRepository.observeCars().first().any { it.id == id } }
    }
}
