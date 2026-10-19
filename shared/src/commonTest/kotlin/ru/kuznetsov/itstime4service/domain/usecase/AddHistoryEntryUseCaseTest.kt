package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [AddHistoryEntryUseCase]: a record typed by hand is stored without a task and without rollback data,
 * only for a car that exists and is not sold, with a name and a positive mileage; texts are trimmed and
 * blank texts and a non-positive cost become `null`.
 */
class AddHistoryEntryUseCaseTest {

    private val history = FakeHistoryRepository(emptyList())
    private val cars = FakeCarRepository(
        cars = listOf(testCar("kia")),
        sold = listOf(CarSummary("lada", "Lada", 120_000, isSold = true)),
    )
    private val addEntry = AddHistoryEntryUseCase(history, cars)
    private val date = LocalDate(2024, 4, 12)

    @Test
    fun recordIsStoredWithoutTask() = runTest {
        val added = addEntry("kia", "Oil change", date, 61_500, serviceName = "Auto South", costRub = 2_400, comment = "Synthetic")

        assertTrue(added)
        assertEquals(
            ServiceHistoryEntry("added-0", "kia", "Oil change", date, 61_500, 2_400, "Auto South", "Synthetic"),
            history.added.single(),
        )
        assertNull(history.added.single().taskId)
        assertNull(history.added.single().prevLastDoneKm)
    }

    @Test
    fun textsAreTrimmedAndBlankOnesDropped() = runTest {
        addEntry("kia", "  Oil change  ", date, 61_500, serviceName = "  ", costRub = null, comment = "")

        val entry = history.added.single()
        assertEquals("Oil change", entry.taskName)
        assertNull(entry.serviceName)
        assertNull(entry.comment)
    }

    @Test
    fun nonPositiveCostIsDropped() = runTest {
        addEntry("kia", "Oil change", date, 61_500, costRub = 0)

        assertNull(history.added.single().costRub)
    }

    @Test
    fun blankNameIsRejected() = runTest {
        assertFalse(addEntry("kia", "   ", date, 61_500))
        assertTrue(history.added.isEmpty())
    }

    @Test
    fun nonPositiveMileageIsRejected() = runTest {
        assertFalse(addEntry("kia", "Oil change", date, 0))
        assertTrue(history.added.isEmpty())
    }

    @Test
    fun soldCarIsRejected() = runTest {
        assertFalse(addEntry("lada", "Oil change", date, 130_000))
        assertTrue(history.added.isEmpty())
    }

    @Test
    fun missingCarIsRejected() = runTest {
        assertFalse(addEntry("ghost", "Oil change", date, 10_000))
        assertTrue(history.added.isEmpty())
    }
}
