package ru.kuznetsov.pitstop.data.notification

import org.jetbrains.compose.resources.getString
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.app_name
import pitstop.shared.generated.resources.distance_mi
import pitstop.shared.generated.resources.mileage_km
import pitstop.shared.generated.resources.notification_reminder_text
import pitstop.shared.generated.resources.task_overdue_by
import pitstop.shared.generated.resources.task_remaining
import pitstop.shared.generated.resources.task_time_expired
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.PlannedReminder
import ru.kuznetsov.pitstop.domain.repository.ReminderScheduler
import ru.kuznetsov.pitstop.ui.format.grouped
import ru.kuznetsov.pitstop.ui.format.kmIn

/**
 * [ReminderScheduler] that turns planned reminders into localized notifications and passes them to the platform.
 * The text reads "car: task — state", where the state is the distance left, the distance overdue
 * or a note that the time interval has run out.
 */
class ReminderSchedulerImpl(
    private val localNotifications: LocalNotifications,
) : ReminderScheduler {

    override suspend fun schedule(reminders: List<PlannedReminder>, distanceUnit: DistanceUnit) {
        val title = getString(Res.string.app_name)
        localNotifications.replaceAll(
            reminders.map { reminder ->
                LocalNotification(
                    id = "${reminder.taskId}@${reminder.fireAt}",
                    title = title,
                    text = getString(
                        Res.string.notification_reminder_text,
                        reminder.carBrand,
                        reminder.taskName,
                        stateText(reminder, distanceUnit),
                    ),
                    fireAt = reminder.fireAt,
                )
            },
        )
    }

    private suspend fun stateText(reminder: PlannedReminder, distanceUnit: DistanceUnit): String {
        val remainingMonths = reminder.remainingMonths
        return when {
            reminder.remainingKm <= 0 ->
                getString(Res.string.task_overdue_by, distanceText(-reminder.remainingKm, distanceUnit))
            remainingMonths != null && remainingMonths <= 0 -> getString(Res.string.task_time_expired)
            else -> getString(Res.string.task_remaining, distanceText(reminder.remainingKm, distanceUnit))
        }
    }

    private suspend fun distanceText(km: Int, distanceUnit: DistanceUnit): String {
        val template = when (distanceUnit) {
            DistanceUnit.KILOMETERS -> Res.string.mileage_km
            DistanceUnit.MILES -> Res.string.distance_mi
        }
        return getString(template, km.kmIn(distanceUnit).grouped())
    }
}
