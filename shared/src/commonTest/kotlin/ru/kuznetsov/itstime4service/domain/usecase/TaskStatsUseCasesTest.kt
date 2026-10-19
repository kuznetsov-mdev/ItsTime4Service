package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers [CarAlertsUseCase] and [SortedTaskStatsUseCase]: counting soon and due tasks for the gauge and listing
 * tasks most urgent first. Tasks have no time interval, so only the mileage decides.
 */
class TaskStatsUseCasesTest {

    private val calculateTaskStatus = CalculateTaskStatusUseCase()
    private val carAlerts = CarAlertsUseCase(calculateTaskStatus)
    private val sortedTaskStats = SortedTaskStatsUseCase(calculateTaskStatus)
    private val today = LocalDate(2026, 10, 19)

    private fun task(id: String, remainingKm: Int) = MaintenanceTask(
        id = id,
        name = id,
        intervalKm = INTERVAL_KM,
        lastDoneKm = MILEAGE_KM + remainingKm - INTERVAL_KM,
        reminderWindowKm = WINDOW_KM,
    )

    private fun car(vararg tasks: MaintenanceTask) =
        Car(id = "kia", brand = "Kia Rio", mileageKm = MILEAGE_KM, vin = null, tasks = tasks.toList())

    @Test
    fun alertsCountSoonAndDueTasks() {
        val kia = car(task("due", 0), task("late", -500), task("soon", WINDOW_KM), task("ok", WINDOW_KM + 1))

        val alerts = carAlerts(kia, today)

        assertEquals(2, alerts.due)
        assertEquals(1, alerts.soon)
        assertEquals(3, alerts.total)
        assertEquals(4, alerts.taskCount)
        assertEquals(TaskStatus.DUE, alerts.worstStatus)
    }

    @Test
    fun carWithoutTasksHasNoAlerts() {
        val alerts = carAlerts(car(), today)

        assertEquals(0, alerts.total)
        assertEquals(0, alerts.taskCount)
        assertEquals(TaskStatus.OK, alerts.worstStatus)
    }

    @Test
    fun soonIsWorstWhenNothingIsDue() {
        assertEquals(TaskStatus.SOON, carAlerts(car(task("soon", 10), task("ok", 5_000)), today).worstStatus)
    }

    @Test
    fun tasksAreSortedByStatusThenRemainingKm() {
        val kia = car(task("ok-far", 9_000), task("soon", 400), task("due", -100), task("ok-near", 3_000), task("late", -900))

        val sorted = sortedTaskStats(kia, today)

        assertEquals(listOf("late", "due", "soon", "ok-near", "ok-far"), sorted.map { it.task.id })
    }

    @Test
    fun tasksWithSameStateKeepTheirOrder() {
        val sorted = sortedTaskStats(car(task("first", 2_000), task("second", 2_000)), today)

        assertEquals(listOf("first", "second"), sorted.map { it.task.id })
    }

    @Test
    fun carWithoutTasksHasEmptyList() {
        assertTrue(sortedTaskStats(car(), today).isEmpty())
    }

    private companion object {
        const val MILEAGE_KM = 100_000
        const val INTERVAL_KM = 10_000
        const val WINDOW_KM = 1_000
    }
}
