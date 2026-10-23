package ru.kuznetsov.itstime4service.data.notification

import kotlinx.datetime.number
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS notification queue built on UNUserNotificationCenter: every notification is a pending request
 * with a one-shot calendar trigger. Requests are silently dropped by the system until the user allows notifications.
 */
actual class LocalNotifications : NotificationQueue {

    actual override fun replaceAll(notifications: List<LocalNotification>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllPendingNotificationRequests()
        notifications.forEach { notification ->
            val content = UNMutableNotificationContent().apply {
                setTitle(notification.title)
                setBody(notification.text)
                setSound(UNNotificationSound.defaultSound)
            }
            val fireAt = notification.fireAt
            val components = NSDateComponents().apply {
                year = fireAt.year.toLong()
                month = fireAt.month.number.toLong()
                day = fireAt.day.toLong()
                hour = fireAt.hour.toLong()
                minute = fireAt.minute.toLong()
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(notification.id, content, trigger),
                withCompletionHandler = null,
            )
        }
    }
}
