package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository

/**
 * Emits the user's settings and re-emits them on every change.
 */
class ObserveSettingsUseCase(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<Settings> = settingsRepository.observeSettings()
}
