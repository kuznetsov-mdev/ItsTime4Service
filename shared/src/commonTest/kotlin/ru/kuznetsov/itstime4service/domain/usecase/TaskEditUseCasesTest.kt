package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewTaskRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [AddTaskUseCase] and [UpdateTaskUseCase]: a rejected task is not stored, a new task keeps the record of its
 * last service, and an edit hands the typed last-done date to the storage, which moves the linked record by
 * [ru.kuznetsov.itstime4service.domain.rule.TaskHistoryRules] inside its transaction.
 */
class TaskEditUseCasesTest {

    private val normalizeTask = NormalizeTaskUseCase()
    private val repository = FakeTaskHistoryRepository()
    private val addTask = AddTaskUseCase(repository, normalizeTask)
    private val updateTask = UpdateTaskUseCase(repository, normalizeTask)
    private val doneDate = LocalDate(2026, 3, 1)

    private fun edit(lastDoneKm: Int = 80_000, lastDoneDate: LocalDate? = doneDate, name: String = "Oil change", intervalMonths: Int? = 12) =
        NewMaintenanceTask(name, 7_500, lastDoneKm, 1_500, intervalMonths = intervalMonths, lastDoneDate = lastDoneDate)

    @Test
    fun newTaskKeepsItsRecord() = runTest {
        val record = NewTaskRecord(doneDate, serviceName = "Kia Center", costRub = 4_200)

        assertTrue(addTask("kia", edit().copy(record = record)))

        assertEquals(record, repository.addedTasks.single().record)
    }

    @Test
    fun rejectedNewTaskIsNotStored() = runTest {
        assertFalse(addTask("kia", edit(name = " ")))
        assertFalse(addTask("kia", edit(lastDoneKm = 0).copy(record = NewTaskRecord(doneDate))))
        assertTrue(repository.addedTasks.isEmpty())
    }

    @Test
    fun rejectedEditChangesNothing() = runTest {
        assertFalse(updateTask("kia", "oil", edit().copy(intervalKm = 0)))
        assertTrue(repository.updatedTasks.isEmpty())
    }

    @Test
    fun editReplacesTheTaskAndPassesTypedDate() = runTest {
        assertTrue(updateTask("kia", "oil", edit(lastDoneKm = 79_500, lastDoneDate = LocalDate(2026, 2, 20))))

        val (task, recordDate) = repository.updatedTasks.single()
        assertEquals("oil", task.id)
        assertEquals(79_500, task.lastDoneKm)
        assertEquals(LocalDate(2026, 2, 20), task.lastDoneDate)
        assertEquals(LocalDate(2026, 2, 20), recordDate)
    }

    @Test
    fun typedDateReachesRecordEvenWhenTaskDropsIt() = runTest {
        updateTask("kia", "oil", edit(intervalMonths = null, lastDoneDate = LocalDate(2026, 2, 20)))

        val (task, recordDate) = repository.updatedTasks.single()
        assertNull(task.lastDoneDate)
        assertEquals(LocalDate(2026, 2, 20), recordDate)
    }
}
