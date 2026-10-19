package ru.kuznetsov.itstime4service.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.data.mapper.toDomain
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository

/**
 * [SettingsRepository] backed by a single-row table of the SQLDelight database.
 * Until the user changes anything the table is empty and the defaults from the design mockup are emitted.
 * [updateSettings] reads the stored row (or the defaults) and writes the changed one in a single transaction,
 * so concurrent changes of different fields do not overwrite each other.
 */
class SettingsRepositoryImpl(database: ItsTime4ServiceDatabase) : SettingsRepository {

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

    override suspend fun updateSettings(change: (Settings) -> Settings): Unit = withContext(dispatcher) {
        queries.transaction {
            val settings = change(queries.select().executeAsOneOrNull()?.toDomain() ?: defaultSettings)
            queries.upsert(
                theme_mode = settings.themeMode.name,
                distance_unit = settings.distanceUnit.name,
                push_enabled = settings.pushEnabled,
                default_reminder_window_km = settings.defaultReminderWindowKm.toLong(),
                reminder_time = settings.reminderTime.toString(),
                last_opened_car_id = settings.lastOpenedCarId,
                open_default_car = settings.openDefaultCar,
                default_car_id = settings.defaultCarId,
                cars_view = settings.carsView.name,
            )
        }
    }
}
