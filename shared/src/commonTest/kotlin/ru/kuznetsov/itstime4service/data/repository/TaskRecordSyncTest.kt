package ru.kuznetsov.itstime4service.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewTaskRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Keeps parameter edits separate from real service facts and history. */
class TaskRecordSyncTest {
    @Test
    fun intervalEditPreservesIndependentFactsAndDoesNotCreateService() = runTest {
        val db = TestDatabase().create()
        val tasks = TaskHistoryRepositoryImpl(db.database)
        val cars = CarRepositoryImpl(db.database)
        val history = ServiceHistoryRepositoryImpl(db.database)
        val date = LocalDate(2026, 3, 1)
        val id = tasks.addCar(NewCar("Kia", 84300, null, listOf(
            NewMaintenanceTask("Oil", 10000, 80000, intervalMonths = 12, lastDoneDate = date, record = NewTaskRecord(date)),
            NewMaintenanceTask("Filter", 15000, lastDoneKm = null, lastDoneDate = date),
        )))
        val original = cars.observeCarById(id).first()!!
        val before = history.observeHistory(id).first()
        tasks.updateTask(id, original.tasks.first().copy(intervalKm = 7500, intervalMonths = null), date)
        tasks.updateTask(id, original.tasks.last().copy(intervalMonths = 12), date)
        val edited = cars.observeCarById(id).first()!!
        assertEquals(date, edited.tasks.first().lastDoneDate)
        assertNull(edited.tasks.last().lastDoneKm)
        assertEquals(date, edited.tasks.last().lastDoneDate)
        assertEquals(before, history.observeHistory(id).first())
        assertTrue(history.observeHistory(id).first().none { it.taskName == "Filter" })
    }
}
