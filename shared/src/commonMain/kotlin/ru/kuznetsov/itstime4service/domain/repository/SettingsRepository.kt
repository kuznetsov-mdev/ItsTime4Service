package ru.kuznetsov.itstime4service.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.kuznetsov.itstime4service.domain.model.Settings

/**
 * Storage of user preferences.
 * [updateSettings] applies [change] to the stored value and stores the result in one step, so that changes
 * made at the same time from different places are not lost.
 */
interface SettingsRepository {
    fun observeSettings(): Flow<Settings>

    suspend fun updateSettings(change: (Settings) -> Settings)
}
