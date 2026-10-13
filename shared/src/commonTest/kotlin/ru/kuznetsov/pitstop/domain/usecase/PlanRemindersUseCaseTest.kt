package ru.kuznetsov.pitstop.domain.usecase

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import ru.kuznetsov.pitstop.domain.model.Car
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.MaintenanceTask
import ru.kuznetsov.pitstop.domain.model.Settings
import ru.kuznetsov.pitstop.domain.model.TaskStatus
import ru.kuznetsov.pitstop.domain.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the planning rules of [PlanRemindersUseCase]: which tasks get notifications, on which days
 * and at what time, the three-day gap between notifications about one task and the size limit of the plan.
 */
class PlanRemindersUseCaseTest {

    private val planReminders = PlanRemindersUseCase(CalculateTaskStatusUseCase())
    private val morning = LocalDateTime(2026, 10, 12, 8, 0)
    private val evening = LocalDateTime(2026, 10, 12, 20, 0)

    private fun settings(pushEnabled: Boolean = true) = Settings(
        themeMode = ThemeMode.SYSTEM,
        distanceUnit = DistanceUnit.KILOMETERS,
        pushEnabled = pushEnabled,
        defaultReminderWindowKm = 2_000,
        reminderTime = LocalTime(9, 0),
    )

    private fun task(
        id: String,
        lastDoneKm: Int,
        intervalMonths: Int? = null,
        lastDoneDate: LocalDate? = null,
    ) = MaintenanceTask(id, "Task $id", 10_000, lastDoneKm, 1_500, intervalMonths, lastDoneDate)

    private fun car(vararg tasks: MaintenanceTask) = Car("c1", "Kia Rio", 84_300, null, tasks.toList())

    private val soonTask = task("soon", lastDoneKm = 75_000)
    private val dueTask = task("due", lastDoneKm = 70_000)
    private val okTask = task("ok", lastDoneKm = 84_000)

    @Test
    fun nothingIsPlannedWhenPushIsOff() {
        assertTrue(planReminders(listOf(car(soonTask)), settings(pushEnabled = false), morning, emptyMap()).isEmpty())
    }

    @Test
    fun okTaskGetsNoNotifications() {
        assertTrue(planReminders(listOf(car(okTask)), settings(), morning, emptyMap()).isEmpty())
    }

    @Test
    fun firstNotificationIsTodayWhenReminderTimeIsAhead() {
        val plan = planReminders(listOf(car(soonTask)), settings(), morning, emptyMap())

        assertEquals(LocalDateTime(2026, 10, 12, 9, 0), plan.first().fireAt)
    }

    @Test
    fun firstNotificationIsTomorrowWhenReminderTimeHasPassed() {
        val plan = planReminders(listOf(car(soonTask)), settings(), evening, emptyMap())

        assertEquals(LocalDateTime(2026, 10, 13, 9, 0), plan.first().fireAt)
    }

    @Test
    fun taskIsRepeatedEveryThreeDaysWithinHorizon() {
        val plan = planReminders(listOf(car(soonTask)), settings(), morning, emptyMap())

        assertEquals(10, plan.size)
        assertEquals(LocalDate(2026, 10, 15), plan[1].fireAt.date)
        assertEquals(LocalDate(2026, 11, 8), plan.last().fireAt.date)
    }

    @Test
    fun reminderCarriesTaskState() {
        val reminder = planReminders(listOf(car(soonTask, dueTask)), settings(), morning, emptyMap())

        assertEquals(TaskStatus.SOON, reminder[0].status)
        assertEquals(700, reminder[0].remainingKm)
        assertEquals("Kia Rio", reminder[0].carBrand)
        assertEquals(TaskStatus.DUE, reminder[1].status)
        assertEquals(-4_300, reminder[1].remainingKm)
    }

    @Test
    fun recentlyShownTaskWaitsForThreeDays() {
        val lastShown = mapOf("soon" to LocalDate(2026, 10, 11))

        val plan = planReminders(listOf(car(soonTask)), settings(), morning, lastShown)

        assertEquals(LocalDate(2026, 10, 14), plan.first().fireAt.date)
    }

    @Test
    fun taskShownLongAgoIsNotDelayed() {
        val lastShown = mapOf("soon" to LocalDate(2026, 10, 1))

        val plan = planReminders(listOf(car(soonTask)), settings(), morning, lastShown)

        assertEquals(LocalDate(2026, 10, 12), plan.first().fireAt.date)
    }

    @Test
    fun timeBasedTaskIsPlannedFromTheDayItBecomesSoon() {
        val timed = task("timed", lastDoneKm = 84_000, intervalMonths = 12, lastDoneDate = LocalDate(2025, 12, 20))

        val plan = planReminders(listOf(car(timed)), settings(), morning, emptyMap())

        assertEquals(LocalDate(2026, 10, 20), plan.first().fireAt.date)
        assertEquals(TaskStatus.SOON, plan.first().status)
    }

    @Test
    fun planIsCutToTheEarliestNotifications() {
        val tasks = (1..10).map { task("t$it", lastDoneKm = 70_000) }.toTypedArray()

        val plan = planReminders(listOf(car(*tasks)), settings(), morning, emptyMap())

        assertEquals(PlanRemindersUseCase.MAX_REMINDERS, plan.size)
        assertEquals(LocalDate(2026, 10, 27), plan.last().fireAt.date)
    }
}
