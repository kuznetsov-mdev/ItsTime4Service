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
 * A queue of local notifications.
 * [replaceAll] cancels every notification of the app that has not fired yet and schedules the given ones.
 */
interface NotificationQueue {
    fun replaceAll(notifications: List<LocalNotification>)
}

/**
 * The platform's local notification queue.
 */
expect class LocalNotifications : NotificationQueue {
    override fun replaceAll(notifications: List<LocalNotification>)
}
