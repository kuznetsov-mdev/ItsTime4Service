package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ReminderRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers [SyncRemindersUseCase] at a fixed moment: which stored records count as shown notifications, what replaces
 * the stored plan and what is handed to the system.
 */
class SyncRemindersUseCaseTest {

    private val now = LocalDateTime(2026, 10, 20, 10, 0)
    private val settings = defaultTestSettings.copy(reminderTime = LocalTime(9, 0))

    private val soon = MaintenanceTask("soon", "Oil change", 10_000, 75_000, 1_500)
    private val ok = MaintenanceTask("ok", "Timing belt", 60_000, 84_000, 5_000)
    private val car = Car("kia", "Kia Rio", 84_300, null, listOf(soon, ok))

    private suspend fun sync(records: List<ReminderRecord>, cars: List<Car> = listOf(car), unit: DistanceUnit = DistanceUnit.KILOMETERS): FakeReminders {
        val reminders = FakeReminders(records)
        val syncReminders = SyncRemindersUseCase(
            PlanRemindersUseCase(CalculateTaskStatusUseCase()),
            GetNowUseCase(fixedClock(now)) { TimeZone.UTC },
            reminders,
            reminders,
        )
        syncReminders(cars, settings.copy(distanceUnit = unit))
        return reminders
    }

    @Test
    fun planOfSoonTaskIsHandedToTheSystem() = runTest {
        val reminders = sync(emptyList())

        assertTrue(reminders.scheduled.isNotEmpty())
        assertTrue(reminders.scheduled.all { it.taskId == "soon" })
        assertEquals(LocalDateTime(2026, 10, 21, 9, 0), reminders.scheduled.first().fireAt)
        assertEquals(DistanceUnit.KILOMETERS, reminders.scheduledUnit)
    }

    @Test
    fun storedPlanBecomesLatestShownPlusNewPlan() = runTest {
        val records = listOf(
            ReminderRecord("soon", LocalDateTime(2026, 10, 14, 9, 0)),
            ReminderRecord("soon", LocalDateTime(2026, 10, 17, 9, 0)),
            ReminderRecord("soon", LocalDateTime(2026, 10, 23, 9, 0)),
            ReminderRecord("gone", LocalDateTime(2026, 10, 18, 9, 0)),
        )

        val reminders = sync(records)

        val shown = reminders.records.filter { it.fireAt <= now }
        assertEquals(listOf(ReminderRecord("soon", LocalDateTime(2026, 10, 17, 9, 0))), shown)
        assertEquals(reminders.scheduled.map { ReminderRecord(it.taskId, it.fireAt) }, reminders.records.filter { it.fireAt > now })
    }

    @Test
    fun recentlyShownTaskWaitsThreeDays() = runTest {
        val reminders = sync(listOf(ReminderRecord("soon", LocalDateTime(2026, 10, 19, 9, 0))))

        assertEquals(LocalDate(2026, 10, 22), reminders.scheduled.first().fireAt.date)
    }

    @Test
    fun withoutCarsEverythingIsCancelled() = runTest {
        val reminders = sync(listOf(ReminderRecord("soon", LocalDateTime(2026, 10, 25, 9, 0))), cars = emptyList())

        assertTrue(reminders.scheduled.isEmpty())
        assertTrue(reminders.records.isEmpty())
        assertEquals(1, reminders.schedules)
    }

    @Test
    fun distanceUnitReachesTheSystem() = runTest {
        assertEquals(DistanceUnit.MILES, sync(emptyList(), unit = DistanceUnit.MILES).scheduledUnit)
    }
}
