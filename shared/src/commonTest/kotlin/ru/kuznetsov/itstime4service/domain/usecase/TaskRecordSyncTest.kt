package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewTaskRecord
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the history record of a task's last service: [AddTaskUseCase] passes it on with the new task, and
 * [UpdateTaskUseCase] moves the linked record — the latest one of the task at its last-done mileage, the later added
 * on the same date — when the last-done mileage or date is edited, and leaves other records alone.
 */
class TaskRecordSyncTest {

    private val normalizeTask = NormalizeTaskUseCase()
    private val doneDate = LocalDate(2026, 3, 1)

    private val oil = MaintenanceTask("oil", "Oil change", 7_500, 80_000, 1_500, intervalMonths = 12, lastDoneDate = doneDate)
    private val car = Car(id = "kia", brand = "Kia Rio", mileageKm = 84_300, vin = null, tasks = listOf(oil))

    private fun record(id: String, mileageKm: Int, date: LocalDate = doneDate, taskId: String? = "oil") =
        ServiceHistoryEntry(id, "kia", "Oil change", date, mileageKm, taskId = taskId)

    private fun edit(lastDoneKm: Int = 80_000, lastDoneDate: LocalDate? = doneDate, name: String = "Oil change") =
        NewMaintenanceTask(name, 7_500, lastDoneKm, 1_500, intervalMonths = 12, lastDoneDate = lastDoneDate)

    private suspend fun update(task: NewMaintenanceTask, history: List<ServiceHistoryEntry>): FakeCarRepository {
        val repository = FakeCarRepository(car)
        UpdateTaskUseCase(repository, FakeHistoryRepository(history), normalizeTask)("kia", "oil", task)
        return repository
    }

    @Test
    fun newTaskKeepsItsRecord() = runTest {
        val repository = FakeCarRepository(car)
        val record = NewTaskRecord(doneDate, serviceName = "Kia Center", costRub = 4_200)

        AddTaskUseCase(repository, normalizeTask)("kia", edit().copy(record = record))

        assertEquals(record, repository.addedTasks.single().record)
    }

    @Test
    fun rejectedNewTaskIsNotStored() = runTest {
        val repository = FakeCarRepository(car)

        assertFalse(AddTaskUseCase(repository, normalizeTask)("kia", edit(name = " ")))
        assertFalse(AddTaskUseCase(repository, normalizeTask)("kia", edit(lastDoneKm = 0).copy(record = NewTaskRecord(doneDate))))
        assertTrue(repository.addedTasks.isEmpty())
    }

    @Test
    fun rejectedEditChangesNothing() = runTest {
        val repository = FakeCarRepository(car)

        val updateTask = UpdateTaskUseCase(repository, FakeHistoryRepository(listOf(record("r", 80_000))), normalizeTask)

        assertFalse(updateTask("kia", "oil", edit().copy(intervalKm = 0)))
        assertTrue(repository.updatedTasks.isEmpty())
    }

    @Test
    fun editedMileageAndDateMoveLinkedRecord() = runTest {
        val newDate = LocalDate(2026, 2, 20)

        val repository = update(edit(lastDoneKm = 79_500, lastDoneDate = newDate), listOf(record("r", 80_000)))

        assertEquals(record("r", 79_500, newDate), repository.updatedEntries.single())
    }

    @Test
    fun latestRecordAtLastDoneMileageIsTheLinkedOne() = runTest {
        val history = listOf(record("old", 80_000, LocalDate(2025, 1, 1)), record("new", 80_000))

        val repository = update(edit(lastDoneKm = 79_500), history)

        assertEquals("new", repository.updatedEntries.single()?.id)
    }

    @Test
    fun laterAddedRecordIsTheLinkedOneOnSameDate() = runTest {
        val history = listOf(record("first", 80_000), record("second", 80_000))

        val repository = update(edit(lastDoneKm = 79_500), history)

        assertEquals("second", repository.updatedEntries.single()?.id)
    }

    @Test
    fun recordsOfOtherMileageOrWithoutTaskAreNotTouched() = runTest {
        val history = listOf(record("earlier", 70_000), record("manual", 80_000, taskId = null))

        val repository = update(edit(lastDoneKm = 79_500), history)

        assertNull(repository.updatedEntries.single())
    }

    @Test
    fun editWithoutLastDoneChangesLeavesRecord() = runTest {
        val repository = update(edit(name = "Engine oil"), listOf(record("r", 80_000)))

        assertNull(repository.updatedEntries.single())
    }

    @Test
    fun clearedLastDoneMileageKeepsRecordMileage() = runTest {
        val newDate = LocalDate(2026, 2, 20)

        val repository = update(edit(lastDoneKm = 0, lastDoneDate = newDate), listOf(record("r", 80_000)))

        assertEquals(record("r", 80_000, newDate), repository.updatedEntries.single())
    }
}
