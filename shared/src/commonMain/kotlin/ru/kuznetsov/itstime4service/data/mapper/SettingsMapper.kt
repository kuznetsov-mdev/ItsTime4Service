package ru.kuznetsov.itstime4service.data.mapper

import kotlinx.datetime.LocalTime
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.data.local.Settings as SettingsRow

internal fun SettingsRow.toDomain() = Settings(
    themeMode = ThemeMode.valueOf(theme_mode),
    distanceUnit = DistanceUnit.valueOf(distance_unit),
    pushEnabled = push_enabled,
    defaultReminderWindowKm = default_reminder_window_km.toInt(),
    reminderTime = LocalTime.parse(reminder_time),
)
