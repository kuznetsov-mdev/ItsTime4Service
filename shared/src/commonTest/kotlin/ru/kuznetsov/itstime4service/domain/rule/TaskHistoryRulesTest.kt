package ru.kuznetsov.itstime4service.domain.rule

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [TaskHistoryRules]: which record is a task's linked one — the latest of the task at its last-done mileage,
 * the later added on the same date — how it follows an edit of the task, and when an edit of a record moves the task.
 */
class TaskHistoryRulesTest {

    private val doneDate = LocalDate(2026, 3, 1)
    private val oil = MaintenanceTask("oil", "Oil change", 7_500, 80_000, 1_500, intervalMonths = 12, lastDoneDate = doneDate)

    private fun record(id: String, mileageKm: Int = 80_000, date: LocalDate = doneDate, taskId: String? = "oil") =
        ServiceHistoryEntry(id, "kia", "Oil change", date, mileageKm, taskId = taskId)

    @Test
    fun latestRecordAtLastDoneMileageIsTheLinkedOne() {
        val history = listOf(record("old", date = LocalDate(2025, 1, 1)), record("new"), record("older", date = LocalDate(2024, 6, 1)))

        assertEquals("new", TaskHistoryRules.linkedEntry(history, oil)?.id)
    }

    @Test
    fun laterAddedRecordIsTheLinkedOneOnSameDate() {
        assertEquals("second", TaskHistoryRules.linkedEntry(listOf(record("first"), record("second")), oil)?.id)
    }

    @Test
    fun recordsOfOtherMileageOrWithoutTaskAreNeverLinked() {
        val history = listOf(record("earlier", mileageKm = 70_000), record("manual", taskId = null), record("other", taskId = "pads"))

        assertNull(TaskHistoryRules.linkedEntry(history, oil))
    }

    @Test
    fun editedMileageAndDateMoveLinkedRecord() {
        val newDate = LocalDate(2026, 2, 20)

        val moved = TaskHistoryRules.entryAfterTaskEdit(listOf(record("r")), oil, lastDoneKm = 79_500, recordDate = newDate)

        assertEquals(record("r", 79_500, newDate), moved)
    }

    @Test
    fun taskEditMovesOnlyTheLatestRecordOnSameDate() {
        val moved = TaskHistoryRules.entryAfterTaskEdit(listOf(record("first"), record("second")), oil, 79_500, doneDate)

        assertEquals("second", moved?.id)
    }

    @Test
    fun editWithoutLastDoneChangesLeavesRecord() {
        assertNull(TaskHistoryRules.entryAfterTaskEdit(listOf(record("r")), oil, oil.lastDoneKm, oil.lastDoneDate))
    }

    @Test
    fun clearedLastDoneMileageKeepsRecordMileage() {
        val newDate = LocalDate(2026, 2, 20)

        assertEquals(record("r", 80_000, newDate), TaskHistoryRules.entryAfterTaskEdit(listOf(record("r")), oil, 0, newDate))
    }

    @Test
    fun typedDateReachesRecordOfTaskWithoutTimeInterval() {
        val mileageOnly = oil.copy(intervalMonths = null, lastDoneDate = null)
        val newDate = LocalDate(2026, 2, 20)

        assertEquals(newDate, TaskHistoryRules.entryAfterTaskEdit(listOf(record("r")), mileageOnly, 80_000, newDate)?.date)
    }

    @Test
    fun taskWithoutLinkedRecordMovesNothing() {
        assertNull(TaskHistoryRules.entryAfterTaskEdit(listOf(record("manual", taskId = null)), oil, 79_500, doneDate))
    }

    @Test
    fun newMileageOrDateOfLinkedRecordMovesTask() {
        val linked = record("r")

        assertTrue(TaskHistoryRules.taskFollowsEntryEdit(listOf(linked), linked, linked.copy(mileageKm = 79_000), oil))
        assertTrue(TaskHistoryRules.taskFollowsEntryEdit(listOf(linked), linked, linked.copy(date = LocalDate(2026, 2, 1)), oil))
    }

    @Test
    fun otherFieldsOfLinkedRecordDoNotMoveTask() {
        val linked = record("r")

        assertFalse(TaskHistoryRules.taskFollowsEntryEdit(listOf(linked), linked, linked.copy(comment = "Motul 5W-30"), oil))
    }

    @Test
    fun taskEditedAfterTheRecordIsNotMoved() {
        val linked = record("r")

        assertFalse(TaskHistoryRules.taskFollowsEntryEdit(listOf(linked), linked, linked.copy(mileageKm = 79_000), oil.copy(lastDoneKm = 81_000)))
    }

    @Test
    fun olderRecordAtSameMileageDoesNotMoveTask() {
        val older = record("older", date = LocalDate(2025, 4, 1))
        val history = listOf(older, record("latest"))

        assertFalse(TaskHistoryRules.taskFollowsEntryEdit(history, older, older.copy(date = LocalDate(2025, 5, 1)), oil))
    }

    @Test
    fun earlierAddedRecordOnSameDateDoesNotMoveTask() {
        val first = record("first")
        val second = record("second")
        val history = listOf(first, second)

        assertFalse(TaskHistoryRules.taskFollowsEntryEdit(history, first, first.copy(date = LocalDate(2026, 4, 1)), oil))
        assertTrue(TaskHistoryRules.taskFollowsEntryEdit(history, second, second.copy(date = LocalDate(2026, 4, 1)), oil))
    }

    @Test
    fun recordWithoutTaskMovesNothing() {
        val manual = record("manual", taskId = null)

        assertFalse(TaskHistoryRules.taskFollowsEntryEdit(listOf(manual), manual, manual.copy(mileageKm = 79_000), null))
    }
}
