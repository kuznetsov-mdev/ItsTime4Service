package ru.kuznetsov.pitstop.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.pitstop.domain.model.Car
import ru.kuznetsov.pitstop.domain.model.MaintenanceTask
import ru.kuznetsov.pitstop.domain.model.ServiceHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [UpdateHistoryEntryUseCase]: how a record is cleaned up and when its task's last-done mileage follows
 * the record's new mileage.
 */
class UpdateHistoryEntryUseCaseTest {

    private fun car(lastDoneKm: Int = 84_300) = Car(
        id = "car",
        brand = "Kia Rio",
        mileageKm = 84_300,
        vin = null,
        tasks = listOf(MaintenanceTask("oil", "Oil change", 10_000, lastDoneKm, 1_500)),
    )

    private val mark = ServiceHistoryEntry(
        id = "mark",
        carId = "car",
        taskName = "Oil change",
        date = LocalDate(2026, 10, 15),
        mileageKm = 84_300,
        taskId = "oil",
        prevLastDoneKm = 74_000,
    )

    private suspend fun update(
        entry: ServiceHistoryEntry,
        car: Car = car(),
        history: List<ServiceHistoryEntry> = listOf(mark),
    ): FakeHistoryRepository {
        val repository = FakeHistoryRepository(history)
        assertTrue(UpdateHistoryEntryUseCase(repository, FakeCarRepository(car))(entry))
        return repository
    }

    @Test
    fun newMileageOfMarkRecordMovesItsTask() = runTest {
        val history = update(mark.copy(mileageKm = 84_000))

        assertEquals(84_000, history.updated.single().second)
        assertEquals(84_000, history.updated.single().first.mileageKm)
    }

    @Test
    fun taskEditedAfterTheMarkIsNotMoved() = runTest {
        val history = update(mark.copy(mileageKm = 84_000), car = car(lastDoneKm = 84_100))

        assertNull(history.updated.single().second)
    }

    @Test
    fun recordWithoutTaskDoesNotMoveAnything() = runTest {
        val old = mark.copy(taskId = null, prevLastDoneKm = null)
        val history = update(old.copy(mileageKm = 84_000), history = listOf(old))

        assertNull(history.updated.single().second)
    }

    @Test
    fun unchangedMileageDoesNotMoveTheTask() = runTest {
        val history = update(mark.copy(comment = "Motul 5W-30"))

        assertNull(history.updated.single().second)
    }

    @Test
    fun blankTextsAndNonPositiveCostAreStoredAsNull() = runTest {
        val history = update(mark.copy(serviceName = "  ", comment = "", costRub = 0))

        val stored = history.updated.single().first
        assertNull(stored.serviceName)
        assertNull(stored.comment)
        assertNull(stored.costRub)
    }

    @Test
    fun rollbackValuesAreKept() = runTest {
        val history = update(mark.copy(mileageKm = 84_000, taskId = null, prevLastDoneKm = null))

        val stored = history.updated.single().first
        assertEquals("oil", stored.taskId)
        assertEquals(74_000, stored.prevLastDoneKm)
    }

    @Test
    fun nonPositiveMileageIsRejected() = runTest {
        val history = FakeHistoryRepository(listOf(mark))

        assertFalse(UpdateHistoryEntryUseCase(history, FakeCarRepository(car()))(mark.copy(mileageKm = 0)))
        assertTrue(history.updated.isEmpty())
    }
}
