package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the calendar edges of the time interval in [CalculateTaskStatusUseCase]: only full months count, and a month
 * is full when the day of the month of the last service is reached — across a year, at the end of a short month and
 * on 29 February. The mileage is far from due, so the status comes from the time interval alone.
 */
class TimeIntervalBoundariesTest {

    private val calculate = CalculateTaskStatusUseCase()

    private fun remainingMonths(lastDone: LocalDate, today: LocalDate, intervalMonths: Int = 12): Int? {
        val task = MaintenanceTask("t", "Oil change", 10_000, 80_000, 1_000, intervalMonths, lastDone)
        return calculate(80_000, task, today).remainingMonths
    }

    private fun status(lastDone: LocalDate, today: LocalDate, intervalMonths: Int): TaskStatus {
        val task = MaintenanceTask("t", "Oil change", 10_000, 80_000, 1_000, intervalMonths, lastDone)
        return calculate(80_000, task, today).status
    }

    @Test
    fun sameDayHasNoElapsedMonths() {
        assertEquals(12, remainingMonths(LocalDate(2026, 3, 15), LocalDate(2026, 3, 15)))
    }

    @Test
    fun monthIsFullOnTheSameDayAcrossNewYear() {
        assertEquals(12, remainingMonths(LocalDate(2025, 12, 15), LocalDate(2026, 1, 14)))
        assertEquals(11, remainingMonths(LocalDate(2025, 12, 15), LocalDate(2026, 1, 15)))
    }

    @Test
    fun thirtyFirstIsNotReachedInShortMonth() {
        assertEquals(1, remainingMonths(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28), intervalMonths = 1))
        assertEquals(TaskStatus.SOON, status(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28), intervalMonths = 1))
        assertEquals(TaskStatus.DUE, status(LocalDate(2026, 1, 31), LocalDate(2026, 3, 1), intervalMonths = 1))
    }

    @Test
    fun thirtyFirstCountsOnlyInMonthsThatHaveIt() {
        val lastDone = LocalDate(2026, 1, 31)

        assertEquals(2, remainingMonths(lastDone, LocalDate(2026, 3, 30), intervalMonths = 3))
        assertEquals(1, remainingMonths(lastDone, LocalDate(2026, 3, 31), intervalMonths = 3))
        assertEquals(1, remainingMonths(lastDone, LocalDate(2026, 4, 30), intervalMonths = 3))
        assertEquals(0, remainingMonths(lastDone, LocalDate(2026, 5, 1), intervalMonths = 3))
    }

    @Test
    fun leapDayYearIsFullFromFirstOfMarch() {
        assertEquals(1, remainingMonths(LocalDate(2024, 2, 29), LocalDate(2025, 2, 28)))
        assertEquals(0, remainingMonths(LocalDate(2024, 2, 29), LocalDate(2025, 3, 1)))
        assertEquals(TaskStatus.DUE, status(LocalDate(2024, 2, 29), LocalDate(2025, 3, 1), intervalMonths = 12))
    }

    @Test
    fun leapDayToLeapDayIsWholeYears() {
        assertEquals(-36, remainingMonths(LocalDate(2024, 2, 29), LocalDate(2028, 2, 29)))
    }

    @Test
    fun severalYearsCountEveryMonth() {
        assertEquals(-60, remainingMonths(LocalDate(2020, 6, 15), LocalDate(2026, 6, 15)))
        assertEquals(-59, remainingMonths(LocalDate(2020, 6, 15), LocalDate(2026, 6, 14)))
    }
}
