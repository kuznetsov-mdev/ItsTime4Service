package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers deleting a car from its screen ([DeleteCarUseCase]), deleting it completely ([PurgeCarUseCase]) and
 * the history of every car with sold ones ([ObserveAllHistoryUseCase]).
 */
class CarRemovalAndHistoryTest {

    private fun entry(id: String, carId: String, date: LocalDate) =
        ServiceHistoryEntry(id = id, carId = carId, taskName = "Oil change", date = date, mileageKm = 50_000)

    @Test
    fun deletingFromTheScreenSellsTheCarAndClearsSettings() = runTest {
        val cars = FakeCarRepository(listOf(testCar("camry")))
        val taskHistory = FakeTaskHistoryRepository()
        val settings = FakeSettingsRepository(defaultTestSettings.copy(lastOpenedCarId = "kia"))

        DeleteCarUseCase(taskHistory, ForgetCarInSettingsUseCase(settings, cars))("kia")

        assertEquals(listOf("kia"), taskHistory.soldIds)
        assertTrue(taskHistory.purgedIds.isEmpty())
        assertNull(settings.settings.lastOpenedCarId)
    }

    @Test
    fun deletingCompletelyPurgesTheCarAndClearsSettings() = runTest {
        val cars = FakeCarRepository(listOf(testCar("camry")))
        val taskHistory = FakeTaskHistoryRepository()
        val settings = FakeSettingsRepository(defaultTestSettings.copy(openDefaultCar = true, defaultCarId = "kia"))

        PurgeCarUseCase(taskHistory, ForgetCarInSettingsUseCase(settings, cars))("kia")

        assertEquals(listOf("kia"), taskHistory.purgedIds)
        assertEquals("camry", settings.settings.defaultCarId)
    }

    @Test
    fun historyIncludesSoldCarsNewestFirst() = runTest {
        val cars = FakeCarRepository(
            cars = listOf(testCar("kia")),
            sold = listOf(CarSummary("lada", "Lada Granta", 120_000, isSold = true)),
        )
        val history = FakeHistoryRepository(
            listOf(
                entry("old", "kia", LocalDate(2024, 5, 1)),
                entry("sold", "lada", LocalDate(2025, 3, 10)),
                entry("new", "kia", LocalDate(2026, 9, 1)),
            ),
        )

        val records = ObserveAllHistoryUseCase(history, cars)().first()

        assertEquals(listOf("new", "sold", "old"), records.map { it.entry.id })
        assertTrue(records.first { it.entry.id == "sold" }.car.isSold)
    }

    @Test
    fun recordsOfMissingCarsAreLeftOut() = runTest {
        val history = FakeHistoryRepository(listOf(entry("orphan", "gone", LocalDate(2026, 1, 1))))

        assertTrue(ObserveAllHistoryUseCase(history, FakeCarRepository(listOf(testCar("kia"))))().first().isEmpty())
    }
}
