package ru.kuznetsov.pitstop.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import ru.kuznetsov.pitstop.data.local.PitStopDatabase
import ru.kuznetsov.pitstop.data.mapper.toDomain
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.Settings
import ru.kuznetsov.pitstop.domain.model.ThemeMode
import ru.kuznetsov.pitstop.domain.repository.SettingsRepository

/**
 * [SettingsRepository] backed by a single-row table of the SQLDelight database.
 * Until the user changes anything the table is empty and the defaults from the design mockup are emitted.
 */
class SettingsRepositoryImpl(database: PitStopDatabase) : SettingsRepository {

    private val queries = database.settingsQueries
    private val dispatcher = Dispatchers.Default

    private val defaultSettings = Settings(
        themeMode = ThemeMode.SYSTEM,
        distanceUnit = DistanceUnit.KILOMETERS,
        pushEnabled = true,
        defaultReminderWindowKm = 2_000,
        reminderTime = LocalTime(9, 0),
    )

    override fun observeSettings(): Flow<Settings> =
        queries.select().asFlow().mapToOneOrNull(dispatcher).map { it?.toDomain() ?: defaultSettings }

    override suspend fun updateSettings(settings: Settings): Unit = withContext(dispatcher) {
        queries.upsert(
            theme_mode = settings.themeMode.name,
            distance_unit = settings.distanceUnit.name,
            push_enabled = settings.pushEnabled,
            default_reminder_window_km = settings.defaultReminderWindowKm.toLong(),
            reminder_time = settings.reminderTime.toString(),
        )
    }
}
