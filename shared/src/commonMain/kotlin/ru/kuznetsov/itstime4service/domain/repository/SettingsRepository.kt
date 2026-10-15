package ru.kuznetsov.itstime4service.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.kuznetsov.itstime4service.domain.model.Settings

/**
 * Storage of user preferences.
 * [updateSettings] replaces the stored value as a whole.
 */
interface SettingsRepository {
    fun observeSettings(): Flow<Settings>

    suspend fun updateSettings(settings: Settings)
}
