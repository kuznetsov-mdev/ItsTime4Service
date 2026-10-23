package ru.kuznetsov.itstime4service.data.notification

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.app_name
import itstime4service.shared.generated.resources.distance_mi
import itstime4service.shared.generated.resources.mileage_km
import itstime4service.shared.generated.resources.notification_reminder_text
import itstime4service.shared.generated.resources.task_overdue_by
import itstime4service.shared.generated.resources.task_remaining
import itstime4service.shared.generated.resources.task_time_expired
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.PlannedReminder
import ru.kuznetsov.itstime4service.domain.repository.ReminderScheduler
import ru.kuznetsov.itstime4service.ui.format.grouped
import ru.kuznetsov.itstime4service.ui.format.kmIn

/**
 * [ReminderScheduler] that turns planned reminders into localized notifications and passes them to the platform.
 * The text reads "car: task — state", where the state is the distance left, the distance overdue
 * or a note that the time interval has run out.
 * The notifications go to a [NotificationQueue] — the platform's [LocalNotifications] in the app; [text] reads a string
 * resource with its arguments and is replaced in tests, which have no resources to read.
 */
class ReminderSchedulerImpl(
    private val queue: NotificationQueue,
    private val text: suspend (StringResource, List<Any>) -> String = { resource, args -> getString(resource, *args.toTypedArray()) },
) : ReminderScheduler {

    override suspend fun schedule(reminders: List<PlannedReminder>, distanceUnit: DistanceUnit) {
        val title = text(Res.string.app_name, emptyList())
        queue.replaceAll(
            reminders.map { reminder ->
                LocalNotification(
                    id = "${reminder.taskId}@${reminder.fireAt}",
                    title = title,
                    text = text(
                        Res.string.notification_reminder_text,
                        listOf(reminder.carBrand, reminder.taskName, stateText(reminder, distanceUnit)),
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
                text(Res.string.task_overdue_by, listOf(distanceText(-reminder.remainingKm, distanceUnit)))
            remainingMonths != null && remainingMonths <= 0 -> text(Res.string.task_time_expired, emptyList())
            else -> text(Res.string.task_remaining, listOf(distanceText(reminder.remainingKm, distanceUnit)))
        }
    }

    private suspend fun distanceText(km: Int, distanceUnit: DistanceUnit): String {
        val template = when (distanceUnit) {
            DistanceUnit.KILOMETERS -> Res.string.mileage_km
            DistanceUnit.MILES -> Res.string.distance_mi
        }
        return text(template, listOf(km.kmIn(distanceUnit).grouped()))
    }
}
