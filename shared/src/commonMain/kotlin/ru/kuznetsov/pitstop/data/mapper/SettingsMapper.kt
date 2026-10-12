package ru.kuznetsov.pitstop.data.mapper

import kotlinx.datetime.LocalTime
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.Settings
import ru.kuznetsov.pitstop.domain.model.ThemeMode
import ru.kuznetsov.pitstop.data.local.Settings as SettingsRow

internal fun SettingsRow.toDomain() = Settings(
    themeMode = ThemeMode.valueOf(theme_mode),
    distanceUnit = DistanceUnit.valueOf(distance_unit),
    pushEnabled = push_enabled,
    defaultReminderWindowKm = default_reminder_window_km.toInt(),
    reminderTime = LocalTime.parse(reminder_time),
)
