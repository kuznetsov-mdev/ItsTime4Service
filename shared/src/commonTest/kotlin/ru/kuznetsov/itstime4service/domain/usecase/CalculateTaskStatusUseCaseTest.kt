package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the status rules of [CalculateTaskStatusUseCase]: by mileage, by time and "whichever comes first".
 * Time-based cases keep the mileage far from due, so the resulting status comes from the dates alone.
 */
class CalculateTaskStatusUseCaseTest {

    private val calculateTaskStatus = CalculateTaskStatusUseCase()
    private val today = LocalDate(2026, 10, 8)

    private fun task(
        intervalKm: Int = 10_000,
        lastDoneKm: Int = 50_000,
        reminderWindowKm: Int = 1_500,
        intervalMonths: Int? = null,
        lastDoneDate: LocalDate? = null,
    ) = MaintenanceTask("t", "Oil change", intervalKm, lastDoneKm, reminderWindowKm, intervalMonths, lastDoneDate)

    private fun timedTask(reminderWindowKm: Int = 1_000) = task(
        reminderWindowKm = reminderWindowKm,
        intervalMonths = 12,
        lastDoneDate = LocalDate(2025, 11, 20),
    )

    @Test
    fun mileageFarFromDueIsOk() {
        val stat = calculateTaskStatus(55_000, task(), today)

        assertEquals(60_000, stat.dueAtKm)
        assertEquals(5_000, stat.remainingKm)
        assertEquals(TaskStatus.OK, stat.status)
        assertEquals(0.5f, stat.progress)
    }

    @Test
    fun mileageJustOutsideReminderWindowIsOk() {
        val stat = calculateTaskStatus(58_499, task(), today)

        assertEquals(1_501, stat.remainingKm)
        assertEquals(TaskStatus.OK, stat.status)
    }

    @Test
    fun mileageAtReminderWindowIsSoon() {
        val stat = calculateTaskStatus(58_500, task(), today)

        assertEquals(1_500, stat.remainingKm)
        assertEquals(TaskStatus.SOON, stat.status)
    }

    @Test
    fun mileageAtDueIsDue() {
        val stat = calculateTaskStatus(60_000, task(), today)

        assertEquals(0, stat.remainingKm)
        assertEquals(TaskStatus.DUE, stat.status)
        assertEquals(1f, stat.progress)
    }

    @Test
    fun mileagePastDueGivesNegativeRemainingAndFullProgress() {
        val stat = calculateTaskStatus(63_000, task(), today)

        assertEquals(-3_000, stat.remainingKm)
        assertEquals(TaskStatus.DUE, stat.status)
        assertEquals(1f, stat.progress)
    }

    @Test
    fun mileageBelowLastDoneGivesZeroProgress() {
        val stat = calculateTaskStatus(40_000, task(), today)

        assertEquals(20_000, stat.remainingKm)
        assertEquals(TaskStatus.OK, stat.status)
        assertEquals(0f, stat.progress)
    }

    @Test
    fun nonPositiveIntervalGivesZeroProgress() {
        val stat = calculateTaskStatus(55_000, task(intervalKm = 0), today)

        assertEquals(0f, stat.progress)
    }

    @Test
    fun timeIsIgnoredWithoutLastDoneDate() {
        val stat = calculateTaskStatus(55_000, task(intervalMonths = 1), today)

        assertNull(stat.remainingMonths)
        assertEquals(TaskStatus.OK, stat.status)
    }

    @Test
    fun timeIsIgnoredWithoutIntervalMonths() {
        val stat = calculateTaskStatus(55_000, task(lastDoneDate = LocalDate(2020, 1, 1)), today)

        assertNull(stat.remainingMonths)
        assertEquals(TaskStatus.OK, stat.status)
    }

    @Test
    fun timeIsIgnoredWithNonPositiveIntervalMonths() {
        val stat = calculateTaskStatus(
            55_000,
            task(intervalMonths = 0, lastDoneDate = LocalDate(2020, 1, 1)),
            today,
        )

        assertNull(stat.remainingMonths)
        assertEquals(TaskStatus.OK, stat.status)
    }

    @Test
    fun timeFarFromDueIsOk() {
        val stat = calculateTaskStatus(51_000, timedTask(), LocalDate(2026, 10, 8))

        assertEquals(2, stat.remainingMonths)
        assertEquals(TaskStatus.OK, stat.status)
    }

    @Test
    fun timeWithinReminderWindowIsSoon() {
        val stat = calculateTaskStatus(51_000, timedTask(), LocalDate(2026, 10, 20))

        assertEquals(1, stat.remainingMonths)
        assertEquals(TaskStatus.SOON, stat.status)
    }

    @Test
    fun incompleteMonthIsNotCounted() {
        val stat = calculateTaskStatus(51_000, timedTask(), LocalDate(2026, 11, 19))

        assertEquals(1, stat.remainingMonths)
        assertEquals(TaskStatus.SOON, stat.status)
    }

    @Test
    fun timeAtDueIsDue() {
        val stat = calculateTaskStatus(51_000, timedTask(), LocalDate(2026, 11, 20))

        assertEquals(0, stat.remainingMonths)
        assertEquals(TaskStatus.DUE, stat.status)
    }

    @Test
    fun timePastDueGivesNegativeRemainingMonths() {
        val stat = calculateTaskStatus(51_000, timedTask(), LocalDate(2027, 2, 1))

        assertEquals(-2, stat.remainingMonths)
        assertEquals(TaskStatus.DUE, stat.status)
    }

    @Test
    fun todayBeforeLastDoneDateCountsAsZeroElapsedMonths() {
        val stat = calculateTaskStatus(51_000, timedTask(), LocalDate(2025, 10, 1))

        assertEquals(12, stat.remainingMonths)
        assertEquals(TaskStatus.OK, stat.status)
    }

    @Test
    fun reminderWindowInMonthsIsScaledFromMileageWindow() {
        val task = timedTask(reminderWindowKm = 3_000)

        val outside = calculateTaskStatus(51_000, task, LocalDate(2026, 6, 20))
        val inside = calculateTaskStatus(51_000, task, LocalDate(2026, 7, 20))

        assertEquals(5, outside.remainingMonths)
        assertEquals(TaskStatus.OK, outside.status)
        assertEquals(4, inside.remainingMonths)
        assertEquals(TaskStatus.SOON, inside.status)
    }

    @Test
    fun reminderWindowInMonthsIsAtLeastOne() {
        val stat = calculateTaskStatus(51_000, timedTask(reminderWindowKm = 100), LocalDate(2026, 10, 20))

        assertEquals(1, stat.remainingMonths)
        assertEquals(TaskStatus.SOON, stat.status)
    }

    @Test
    fun dueByTimeWinsOverOkByMileage() {
        val stat = calculateTaskStatus(51_000, timedTask(), LocalDate(2026, 11, 20))

        assertEquals(9_000, stat.remainingKm)
        assertEquals(TaskStatus.DUE, stat.status)
    }

    @Test
    fun soonByMileageWinsOverOkByTime() {
        val stat = calculateTaskStatus(59_000, timedTask(), LocalDate(2026, 10, 8))

        assertEquals(1_000, stat.remainingKm)
        assertEquals(2, stat.remainingMonths)
        assertEquals(TaskStatus.SOON, stat.status)
    }

    @Test
    fun dueByMileageWinsOverOkByTime() {
        val stat = calculateTaskStatus(60_500, timedTask(), LocalDate(2026, 1, 20))

        assertEquals(-500, stat.remainingKm)
        assertEquals(10, stat.remainingMonths)
        assertEquals(TaskStatus.DUE, stat.status)
    }

    @Test
    fun dueByTimeWinsOverSoonByMileage() {
        val stat = calculateTaskStatus(59_500, timedTask(), LocalDate(2026, 12, 1))

        assertEquals(500, stat.remainingKm)
        assertEquals(0, stat.remainingMonths)
        assertEquals(TaskStatus.DUE, stat.status)
    }

    @Test
    fun specExampleToyotaCamry() {
        val mileageKm = 152_000
        val sparkPlugs = MaintenanceTask("t5", "Spark plugs", 40_000, 110_000, 2_000)
        val oil = MaintenanceTask("t6", "Oil and filter", 10_000, 143_000, 1_000, 12, LocalDate(2025, 11, 20))
        val cabinFilter = MaintenanceTask("t7", "Cabin filter", 20_000, 135_000, 2_000)

        val sparkPlugsStat = calculateTaskStatus(mileageKm, sparkPlugs, today)
        val oilStat = calculateTaskStatus(mileageKm, oil, today)
        val cabinFilterStat = calculateTaskStatus(mileageKm, cabinFilter, today)

        assertEquals(150_000, sparkPlugsStat.dueAtKm)
        assertEquals(-2_000, sparkPlugsStat.remainingKm)
        assertEquals(TaskStatus.DUE, sparkPlugsStat.status)

        assertEquals(153_000, oilStat.dueAtKm)
        assertEquals(1_000, oilStat.remainingKm)
        assertEquals(2, oilStat.remainingMonths)
        assertEquals(TaskStatus.SOON, oilStat.status)

        assertEquals(155_000, cabinFilterStat.dueAtKm)
        assertEquals(3_000, cabinFilterStat.remainingKm)
        assertEquals(TaskStatus.OK, cabinFilterStat.status)
    }
}
