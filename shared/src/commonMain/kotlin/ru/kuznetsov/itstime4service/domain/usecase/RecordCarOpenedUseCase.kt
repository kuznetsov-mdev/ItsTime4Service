package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.first
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository

/**
 * Remembers the car whose screen was just opened as the last opened one; the app may start on it next time.
 * Writes nothing when that car is already the last opened one.
 */
class RecordCarOpenedUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(carId: String) {
        if (settingsRepository.observeSettings().first().lastOpenedCarId == carId) return
        settingsRepository.updateSettings { it.copy(lastOpenedCarId = carId) }
    }
}
