package ru.kuznetsov.itstime4service.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewTaskRecord
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [TaskHistoryRepositoryImpl] on a real SQLite database: what each operation writes to the tasks and the
 * history together, and what it decides inside its transaction — the linked record, the done mark, whether a car
 * has history. A trigger that aborts one of the writes checks that a failed operation leaves nothing half done.
 */
class TaskHistoryRepositoryImplTest {

    private val db = TestDatabase().create()
    private val taskHistory = TaskHistoryRepositoryImpl(db.database)
    private val cars = CarRepositoryImpl(db.database)
    private val history = ServiceHistoryRepositoryImpl(db.database)
    private val doneDate = LocalDate(2026, 3, 1)

    private fun oil(lastDoneKm: Int = 80_000, intervalMonths: Int? = 12, record: NewTaskRecord? = null) = NewMaintenanceTask(
        name = "Oil change",
        intervalKm = 7_500,
        lastDoneKm = lastDoneKm,
        reminderWindowKm = 1_500,
        intervalMonths = intervalMonths,
        lastDoneDate = doneDate.takeIf { intervalMonths != null },
        record = record,
    )

    private suspend fun addKia(vararg tasks: NewMaintenanceTask): String =
        taskHistory.addCar(NewCar(brand = "Kia Rio", mileageKm = 84_300, vin = null, tasks = tasks.toList()))

    private suspend fun car(id: String): Car = cars.observeCarById(id).first()!!

    private suspend fun records(carId: String): List<ServiceHistoryEntry> = history.observeHistory(carId).first()

    @Test
    fun newCarIsStoredWithItsTasksAndTheirRecords() = runTest {
        val id = addKia(oil(record = NewTaskRecord(doneDate, serviceName = "Kia Center", costRub = 4_200)), oil(lastDoneKm = 0))

        val stored = car(id)
        assertEquals(2, stored.tasks.size)
        val record = records(id).single()
        assertEquals(stored.tasks[0].id, record.taskId)
        assertEquals(80_000, record.mileageKm)
        assertEquals(doneDate, record.date)
        assertEquals("Kia Center", record.serviceName)
        assertNull(record.prevLastDoneKm)
    }

    @Test
    fun markingDoneMovesTaskAndRemembersPreviousValues() = runTest {
        val id = addKia(oil())
        val taskId = car(id).tasks.single().id

        assertTrue(taskHistory.markTaskDone(id, taskId, LocalDate(2026, 10, 20), "Kia Center", 4_200, null))

        val task = car(id).tasks.single()
        assertEquals(84_300, task.lastDoneKm)
        assertEquals(LocalDate(2026, 10, 20), task.lastDoneDate)
        val mark = records(id).single()
        assertEquals(84_300, mark.mileageKm)
        assertEquals(80_000, mark.prevLastDoneKm)
        assertEquals(doneDate, mark.prevLastDoneDate)
    }

    @Test
    fun markingDoneKeepsDateOfTaskWithoutTimeInterval() = runTest {
        val id = addKia(oil(intervalMonths = null))
        val taskId = car(id).tasks.single().id

        taskHistory.markTaskDone(id, taskId, LocalDate(2026, 10, 20), null, null, null)

        assertNull(car(id).tasks.single().lastDoneDate)
    }

    @Test
    fun markingMissingTaskChangesNothing() = runTest {
        val id = addKia(oil())

        assertFalse(taskHistory.markTaskDone(id, "ghost", LocalDate(2026, 10, 20), null, null, null))
        assertFalse(taskHistory.markTaskDone("ghost", "ghost", LocalDate(2026, 10, 20), null, null, null))
        assertTrue(records(id).isEmpty())
    }

    @Test
    fun unmarkingRollsBackAndDeletesTheRecord() = runTest {
        val id = addKia(oil())
        val taskId = car(id).tasks.single().id
        taskHistory.markTaskDone(id, taskId, LocalDate(2026, 10, 20), null, null, null)

        assertTrue(taskHistory.unmarkTaskDone(id, taskId))

        val task = car(id).tasks.single()
        assertEquals(80_000, task.lastDoneKm)
        assertEquals(doneDate, task.lastDoneDate)
        assertTrue(records(id).isEmpty())
    }

    @Test
    fun markIsGoneOnceMileageMoves() = runTest {
        val id = addKia(oil())
        val taskId = car(id).tasks.single().id
        taskHistory.markTaskDone(id, taskId, LocalDate(2026, 10, 20), null, null, null)
        cars.updateMileage(id, 84_500)

        assertFalse(taskHistory.unmarkTaskDone(id, taskId))
        assertEquals(1, records(id).size)
    }

    @Test
    fun editedLastServiceMovesLinkedRecord() = runTest {
        val id = addKia(oil(record = NewTaskRecord(doneDate)))
        val task = car(id).tasks.single()

        taskHistory.updateTask(id, task.copy(lastDoneKm = 79_500, lastDoneDate = LocalDate(2026, 2, 20)), LocalDate(2026, 2, 20))

        val record = records(id).single()
        assertEquals(79_500, record.mileageKm)
        assertEquals(LocalDate(2026, 2, 20), record.date)
        assertEquals(79_500, car(id).tasks.single().lastDoneKm)
    }

    @Test
    fun laterAddedRecordIsMovedOnSameDate() = runTest {
        val id = addKia(oil(record = NewTaskRecord(doneDate)))
        val task = car(id).tasks.single()
        taskHistory.markTaskDone(id, task.id, doneDate, null, null, null)
        taskHistory.updateTask(id, car(id).tasks.single().copy(lastDoneKm = 80_000), null)
        val before = records(id)

        taskHistory.updateTask(id, car(id).tasks.single().copy(lastDoneKm = 79_000), null)

        val after = records(id)
        assertEquals(before[0], after[0])
        assertEquals(79_000, after[1].mileageKm)
    }

    @Test
    fun editedLinkedRecordMovesTask() = runTest {
        val id = addKia(oil(record = NewTaskRecord(doneDate)))
        val record = records(id).single()

        assertTrue(taskHistory.updateEntry(record.copy(mileageKm = 79_800, date = LocalDate(2026, 2, 25), comment = "Motul")))

        val task = car(id).tasks.single()
        assertEquals(79_800, task.lastDoneKm)
        assertEquals(LocalDate(2026, 2, 25), task.lastDoneDate)
        assertEquals("Motul", records(id).single().comment)
    }

    @Test
    fun editKeepsNameTaskAndRollbackValues() = runTest {
        val id = addKia(oil())
        val taskId = car(id).tasks.single().id
        taskHistory.markTaskDone(id, taskId, LocalDate(2026, 10, 20), null, null, null)
        val mark = records(id).single()

        taskHistory.updateEntry(mark.copy(taskName = "Renamed", taskId = null, prevLastDoneKm = null, comment = "ok"))

        val stored = records(id).single()
        assertEquals("Oil change", stored.taskName)
        assertEquals(taskId, stored.taskId)
        assertEquals(80_000, stored.prevLastDoneKm)
    }

    @Test
    fun editOfOlderRecordLeavesTask() = runTest {
        val id = addKia(oil(record = NewTaskRecord(LocalDate(2025, 1, 1))))
        val taskId = car(id).tasks.single().id
        taskHistory.markTaskDone(id, taskId, LocalDate(2026, 10, 20), null, null, null)
        cars.updateMileage(id, 80_000)
        val task = car(id).tasks.single()
        taskHistory.updateTask(id, task.copy(lastDoneKm = 80_000), null)
        val older = records(id).first { it.date == LocalDate(2025, 1, 1) }

        taskHistory.updateEntry(older.copy(date = LocalDate(2025, 2, 1)))

        assertEquals(80_000, car(id).tasks.single().lastDoneKm)
        assertEquals(LocalDate(2026, 10, 20), car(id).tasks.single().lastDoneDate)
    }

    @Test
    fun editOfMissingRecordIsReported() = runTest {
        assertFalse(taskHistory.updateEntry(ServiceHistoryEntry("ghost", "kia", "Oil", doneDate, 80_000)))
    }

    @Test
    fun carWithHistoryIsKeptAsSoldWithoutTasks() = runTest {
        val id = addKia(oil(record = NewTaskRecord(doneDate)))

        taskHistory.sellCar(id)

        assertNull(cars.observeCarById(id).first())
        assertTrue(cars.observeCarSummaries().first().single { it.id == id }.isSold)
        assertEquals(1, records(id).size)
    }

    @Test
    fun carWithoutHistoryIsDeletedOnSale() = runTest {
        val id = addKia(oil())

        taskHistory.sellCar(id)

        assertTrue(cars.observeCarSummaries().first().none { it.id == id })
    }

    @Test
    fun purgeRemovesCarTasksAndHistory() = runTest {
        val id = addKia(oil(record = NewTaskRecord(doneDate)))
        val other = addKia(oil(record = NewTaskRecord(doneDate)))

        taskHistory.purgeCar(id)

        assertTrue(cars.observeCarSummaries().first().none { it.id == id })
        assertTrue(records(id).isEmpty())
        assertEquals(1, records(other).size)
        assertEquals(1, car(other).tasks.size)
    }

    private fun failBefore(event: String, table: String) {
        db.execute("CREATE TRIGGER fail_${event.lowercase()}_$table BEFORE $event ON $table BEGIN SELECT RAISE(ABORT, 'test failure'); END")
    }

    @Test
    fun failedRecordLeavesNoNewCar() = runTest {
        failBefore("INSERT", "service_history")

        assertFails { addKia(oil(record = NewTaskRecord(doneDate))) }

        assertTrue(cars.observeCarSummaries().first().isEmpty())
    }

    @Test
    fun failedRecordLeavesTaskUnmarked() = runTest {
        val id = addKia(oil())
        val taskId = car(id).tasks.single().id
        failBefore("INSERT", "service_history")

        assertFails { taskHistory.markTaskDone(id, taskId, LocalDate(2026, 10, 20), null, null, null) }

        val task = car(id).tasks.single()
        assertEquals(80_000, task.lastDoneKm)
        assertEquals(doneDate, task.lastDoneDate)
        assertTrue(records(id).isEmpty())
    }

    @Test
    fun failedRecordDeletionKeepsTheMark() = runTest {
        val id = addKia(oil())
        val taskId = car(id).tasks.single().id
        taskHistory.markTaskDone(id, taskId, LocalDate(2026, 10, 20), null, null, null)
        failBefore("DELETE", "service_history")

        assertFails { taskHistory.unmarkTaskDone(id, taskId) }

        assertEquals(84_300, car(id).tasks.single().lastDoneKm)
        assertEquals(1, records(id).size)
    }

    @Test
    fun failedTaskMoveKeepsTheRecordAsItWas() = runTest {
        val id = addKia(oil(record = NewTaskRecord(doneDate)))
        val record = records(id).single()
        failBefore("UPDATE", "maintenance_tasks")

        assertFails { taskHistory.updateEntry(record.copy(mileageKm = 79_800, comment = "Motul")) }

        assertEquals(record, records(id).single())
        assertEquals(80_000, car(id).tasks.single().lastDoneKm)
    }

    @Test
    fun failedSaleKeepsCarAndTasks() = runTest {
        val id = addKia(oil(record = NewTaskRecord(doneDate)))
        failBefore("UPDATE", "cars")

        assertFails { taskHistory.sellCar(id) }

        assertEquals(1, car(id).tasks.size)
    }

    @Test
    fun failedPurgeKeepsTasksAndHistory() = runTest {
        val id = addKia(oil(record = NewTaskRecord(doneDate)))
        failBefore("DELETE", "cars")

        assertFails { taskHistory.purgeCar(id) }

        assertEquals(1, car(id).tasks.size)
        assertEquals(1, records(id).size)
    }
}
