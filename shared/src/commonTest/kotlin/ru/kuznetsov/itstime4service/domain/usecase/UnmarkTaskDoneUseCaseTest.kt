package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers [UnmarkTaskDoneUseCase]: a marked task is rolled back through the record that holds its mark,
 * anything else is left alone.
 */
class UnmarkTaskDoneUseCaseTest {

    private val car = Car(
        id = "car",
        brand = "Kia Rio",
        mileageKm = 84_300,
        vin = null,
        tasks = listOf(MaintenanceTask("oil", "Oil change", 10_000, 84_300, 1_500)),
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

    @Test
    fun markedTaskIsRolledBackThroughItsRecord() = runTest {
        val cars = FakeCarRepository(car)
        val unmark = UnmarkTaskDoneUseCase(cars, FakeHistoryRepository(listOf(mark)), DoneMarksUseCase())

        assertTrue(unmark("car", "oil"))
        assertEquals(listOf(Triple("car", "oil", "mark")), cars.unmarked)
    }

    @Test
    fun unmarkedTaskIsLeftAlone() = runTest {
        val cars = FakeCarRepository(car.copy(mileageKm = 84_400))
        val unmark = UnmarkTaskDoneUseCase(cars, FakeHistoryRepository(listOf(mark)), DoneMarksUseCase())

        assertFalse(unmark("car", "oil"))
        assertTrue(cars.unmarked.isEmpty())
    }

    @Test
    fun missingCarIsLeftAlone() = runTest {
        val cars = FakeCarRepository(null)
        val unmark = UnmarkTaskDoneUseCase(cars, FakeHistoryRepository(listOf(mark)), DoneMarksUseCase())

        assertFalse(unmark("car", "oil"))
        assertTrue(cars.unmarked.isEmpty())
    }
}
