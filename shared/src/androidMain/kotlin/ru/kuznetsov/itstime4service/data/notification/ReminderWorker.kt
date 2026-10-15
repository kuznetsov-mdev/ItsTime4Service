package ru.kuznetsov.itstime4service.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.kuznetsov.itstime4service.shared.R

/**
 * Shows one planned notification when its delay runs out.
 * Does nothing when notifications are disabled for the app; a tap on the notification opens the app.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val title = inputData.getString(KEY_TITLE).orEmpty()
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(applicationContext, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(inputData.getString(KEY_TITLE))
            .setContentText(inputData.getString(KEY_TEXT))
            .setStyle(NotificationCompat.BigTextStyle().bigText(inputData.getString(KEY_TEXT)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            return Result.success()
        }
        return Result.success()
    }

    companion object {
        const val TAG = "task-reminder"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        private const val CHANNEL_ID = "task-reminders"
    }
}
