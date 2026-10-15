package ru.kuznetsov.itstime4service.data.notification

import kotlinx.datetime.LocalDateTime

/**
 * A ready-to-show local notification: texts are already localized, [fireAt] is in the device time zone.
 * [id] is unique within one plan.
 */
data class LocalNotification(
    val id: String,
    val title: String,
    val text: String,
    val fireAt: LocalDateTime,
)

/**
 * The platform's local notification queue.
 * [replaceAll] cancels every notification of the app that has not fired yet and schedules the given ones.
 */
expect class LocalNotifications {
    fun replaceAll(notifications: List<LocalNotification>)
}
