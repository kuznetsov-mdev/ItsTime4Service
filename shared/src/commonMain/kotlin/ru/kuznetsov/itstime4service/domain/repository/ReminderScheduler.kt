package ru.kuznetsov.itstime4service.domain.repository

import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.PlannedReminder

/**
 * Hands the notification plan over to the operating system.
 * [schedule] drops every notification that is still pending and schedules the given ones instead;
 * an empty list just cancels everything. Distances in the texts are shown in [distanceUnit].
 */
interface ReminderScheduler {
    suspend fun schedule(reminders: List<PlannedReminder>, distanceUnit: DistanceUnit)
}
