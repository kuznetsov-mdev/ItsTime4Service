package ru.kuznetsov.pitstop.domain.usecase

import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.Settings
import ru.kuznetsov.pitstop.domain.repository.SettingsRepository

/**
 * Stores the user's settings.
 * The default reminder window is kept within 1..9 thousand of the chosen unit — the range of the stepper on the
 * settings screen: 1 000..9 000 km in kilometers, 1 609..14 484 km in miles. Switching the unit therefore also
 * pulls a window that no longer fits into the new range, so the stepper always shows what is stored.
 */
class UpdateSettingsUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(settings: Settings) {
        val range = when (settings.distanceUnit) {
            DistanceUnit.KILOMETERS -> MIN_WINDOW_KM..MAX_WINDOW_KM
            DistanceUnit.MILES -> MIN_WINDOW_IN_MILES_KM..MAX_WINDOW_IN_MILES_KM
        }
        settingsRepository.updateSettings(
            settings.copy(defaultReminderWindowKm = settings.defaultReminderWindowKm.coerceIn(range)),
        )
    }

    private companion object {
        const val MIN_WINDOW_KM = 1_000
        const val MAX_WINDOW_KM = 9_000
        const val MIN_WINDOW_IN_MILES_KM = 1_609
        const val MAX_WINDOW_IN_MILES_KM = 14_484
    }
}
