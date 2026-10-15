package ru.kuznetsov.itstime4service.domain.model

import kotlinx.datetime.LocalTime

/**
 * User preferences from the settings screen.
 * [defaultReminderWindowKm] pre-fills the reminder window of newly created tasks.
 * [reminderTime] is the time of day of the daily status check behind push notifications.
 */
data class Settings(
    val themeMode: ThemeMode,
    val distanceUnit: DistanceUnit,
    val pushEnabled: Boolean,
    val defaultReminderWindowKm: Int,
    val reminderTime: LocalTime,
)
