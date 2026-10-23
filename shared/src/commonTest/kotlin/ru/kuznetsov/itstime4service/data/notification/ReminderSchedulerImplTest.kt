package ru.kuznetsov.itstime4service.data.notification

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.PlannedReminder
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.ui.format.grouped
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [ReminderSchedulerImpl]: every planned reminder becomes one notification with an id unique within the plan,
 * the app name as its title and the text "car: task — state", where the state is the distance left, the distance
 * overdue or the run-out time interval, in the chosen unit; the whole plan replaces the queue at once.
 * Strings are not read from resources: each one is written as its resource key with the arguments in brackets.
 */
class ReminderSchedulerImplTest {

    private val queue = RecordingQueue()
    private val scheduler = ReminderSchedulerImpl(queue) { resource, args -> "${resource.key}(${args.joinToString()})" }
    private val fireAt = LocalDateTime(2026, 10, 24, 9, 0)

    private fun reminder(taskId: String = "oil", remainingKm: Int, remainingMonths: Int? = null) = PlannedReminder(
        carId = "kia",
        taskId = taskId,
        carBrand = "Kia",
        taskName = "Oil",
        status = TaskStatus.SOON,
        remainingKm = remainingKm,
        remainingMonths = remainingMonths,
        fireAt = fireAt,
    )

    private suspend fun textOf(reminder: PlannedReminder, unit: DistanceUnit = DistanceUnit.KILOMETERS): String {
        scheduler.schedule(listOf(reminder), unit)
        return queue.last.single().text
    }

    @Test
    fun notificationIsNamedAfterTaskAndTimeAndTitledWithTheAppName() = runTest {
        scheduler.schedule(listOf(reminder(remainingKm = 1_200)), DistanceUnit.KILOMETERS)

        val notification = queue.last.single()
        assertEquals("oil@2026-10-24T09:00", notification.id)
        assertEquals("app_name()", notification.title)
        assertEquals(fireAt, notification.fireAt)
    }

    @Test
    fun distanceLeftIsShown() = runTest {
        assertEquals(
            "notification_reminder_text(Kia, Oil, task_remaining(mileage_km(${1_200.grouped()})))",
            textOf(reminder(remainingKm = 1_200)),
        )
    }

    @Test
    fun distanceOverdueIsShownWithoutSign() = runTest {
        assertEquals(
            "notification_reminder_text(Kia, Oil, task_overdue_by(mileage_km(300)))",
            textOf(reminder(remainingKm = -300)),
        )
    }

    @Test
    fun runOutTimeIsShownWhileDistanceIsLeft() = runTest {
        assertEquals("notification_reminder_text(Kia, Oil, task_time_expired())", textOf(reminder(remainingKm = 500, remainingMonths = 0)))
    }

    @Test
    fun reachedDistanceWinsOverRunOutTime() = runTest {
        assertEquals(
            "notification_reminder_text(Kia, Oil, task_overdue_by(mileage_km(0)))",
            textOf(reminder(remainingKm = 0, remainingMonths = -1)),
        )
    }

    @Test
    fun distanceIsShownInMiles() = runTest {
        assertEquals(
            "notification_reminder_text(Kia, Oil, task_remaining(distance_mi(${1_000.grouped()})))",
            textOf(reminder(remainingKm = 1_609), DistanceUnit.MILES),
        )
    }

    @Test
    fun wholePlanReplacesTheQueueInOrder() = runTest {
        val later = reminder(taskId = "pads", remainingKm = 800).copy(fireAt = LocalDateTime(2026, 10, 27, 9, 0))

        scheduler.schedule(listOf(reminder(remainingKm = 1_200), later), DistanceUnit.KILOMETERS)
        scheduler.schedule(emptyList(), DistanceUnit.KILOMETERS)

        assertEquals(
            listOf(listOf("oil@2026-10-24T09:00", "pads@2026-10-27T09:00"), emptyList()),
            queue.calls.map { plan -> plan.map { it.id } },
        )
    }

    /**
     * A queue that remembers every plan it was given.
     */
    private class RecordingQueue : NotificationQueue {
        val calls = mutableListOf<List<LocalNotification>>()
        val last: List<LocalNotification> get() = calls.last()

        override fun replaceAll(notifications: List<LocalNotification>) {
            calls.add(notifications)
        }
    }
}
