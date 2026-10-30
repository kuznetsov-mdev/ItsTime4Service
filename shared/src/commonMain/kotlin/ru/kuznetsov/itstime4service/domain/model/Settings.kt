package ru.kuznetsov.itstime4service.domain.model

import kotlinx.datetime.LocalTime

/**
 * User preferences from the settings screen.
 * [defaultReminderWindowKm] pre-fills the reminder window of newly created tasks.
 * [reminderTime] is the time of day of the daily status check behind push notifications.
 * [lastOpenedCarId] is the car whose screen was opened last; with [openDefaultCar] on the app starts on
 * [defaultCarId] instead. Either may point to a car that no longer exists.
 * [carsView] is how the "My cars" list is shown.
 */
data class Settings(
    val themeMode: ThemeMode,
    val distanceUnit: DistanceUnit,
    val pushEnabled: Boolean,
    val defaultReminderWindowKm: Int,
    val reminderTime: LocalTime,
    val lastOpenedCarId: String? = null,
    val openDefaultCar: Boolean = false,
    val defaultCarId: String? = null,
    val carsView: CarsView = CarsView.LIST,
) {
    companion object {
        const val DEFAULT_REMINDER_WINDOW_KM = 2_000
    }
}
