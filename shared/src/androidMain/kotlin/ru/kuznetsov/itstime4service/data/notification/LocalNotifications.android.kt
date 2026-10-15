package ru.kuznetsov.itstime4service.data.notification

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * Android notification queue built on WorkManager: every notification is a delayed one-time job
 * handled by [ReminderWorker], so the plan survives a reboot.
 */
actual class LocalNotifications(private val context: Context) {

    actual fun replaceAll(notifications: List<LocalNotification>) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(ReminderWorker.TAG)
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        notifications.forEach { notification ->
            val delayMs = (notification.fireAt.toInstant(timeZone) - now).inWholeMilliseconds.coerceAtLeast(0)
            workManager.enqueue(
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(
                            ReminderWorker.KEY_ID to notification.id,
                            ReminderWorker.KEY_TITLE to notification.title,
                            ReminderWorker.KEY_TEXT to notification.text,
                        ),
                    )
                    .addTag(ReminderWorker.TAG)
                    .build(),
            )
        }
    }
}
