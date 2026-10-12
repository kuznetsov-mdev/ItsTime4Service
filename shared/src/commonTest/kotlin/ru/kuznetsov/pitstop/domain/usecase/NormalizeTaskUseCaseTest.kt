package ru.kuznetsov.pitstop.domain.usecase

import kotlinx.datetime.LocalDate
import ru.kuznetsov.pitstop.domain.model.NewMaintenanceTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the rules of [NormalizeTaskUseCase]: which tasks are rejected and how the accepted ones are cleaned up.
 */
class NormalizeTaskUseCaseTest {

    private val normalizeTask = NormalizeTaskUseCase()
    private val lastDoneDate = LocalDate(2026, 1, 15)

    private fun task(
        name: String = "Oil change",
        intervalKm: Int = 10_000,
        reminderWindowKm: Int? = 1_500,
        intervalMonths: Int? = null,
        lastDoneDate: LocalDate? = null,
    ) = NewMaintenanceTask(name, intervalKm, 78_000, reminderWindowKm, intervalMonths, lastDoneDate)

    @Test
    fun validTaskIsKeptAsIs() {
        val task = task(intervalMonths = 12, lastDoneDate = lastDoneDate)

        assertEquals(task, normalizeTask(task))
    }

    @Test
    fun blankNameIsRejected() {
        assertNull(normalizeTask(task(name = "   ")))
    }

    @Test
    fun zeroIntervalIsRejected() {
        assertNull(normalizeTask(task(intervalKm = 0)))
    }

    @Test
    fun negativeIntervalIsRejected() {
        assertNull(normalizeTask(task(intervalKm = -5_000)))
    }

    @Test
    fun nameIsTrimmed() {
        assertEquals("Oil change", normalizeTask(task(name = "  Oil change "))?.name)
    }

    @Test
    fun missingReminderWindowBecomesShareOfInterval() {
        assertEquals(1_500, normalizeTask(task(reminderWindowKm = null))?.reminderWindowKm)
    }

    @Test
    fun defaultReminderWindowIsRounded() {
        assertEquals(1_875, normalizeTask(task(intervalKm = 12_500, reminderWindowKm = null))?.reminderWindowKm)
    }

    @Test
    fun explicitReminderWindowIsKept() {
        assertEquals(3_000, normalizeTask(task(reminderWindowKm = 3_000))?.reminderWindowKm)
    }

    @Test
    fun nonPositiveTimeIntervalCountsAsAbsent() {
        assertNull(normalizeTask(task(intervalMonths = 0))?.intervalMonths)
    }

    @Test
    fun lastDoneDateIsDroppedWithoutTimeInterval() {
        assertNull(normalizeTask(task(lastDoneDate = lastDoneDate))?.lastDoneDate)
    }

    @Test
    fun lastDoneDateIsDroppedWithNonPositiveTimeInterval() {
        assertNull(normalizeTask(task(intervalMonths = 0, lastDoneDate = lastDoneDate))?.lastDoneDate)
    }
}
