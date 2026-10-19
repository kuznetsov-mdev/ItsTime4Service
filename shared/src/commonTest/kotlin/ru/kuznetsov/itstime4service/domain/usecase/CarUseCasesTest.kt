package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the car use cases that only check and clean up the input before handing it to the repository:
 * [AddCarUseCase], [UpdateCarUseCase], [UpdateMileageUseCase] and [DeleteTaskUseCase].
 */
class CarUseCasesTest {

    private val repository = FakeCarRepository(emptyList())
    private val addCar = AddCarUseCase(repository, NormalizeTaskUseCase())
    private val updateCar = UpdateCarUseCase(repository)
    private val updateMileage = UpdateMileageUseCase(repository)

    private fun newCar(
        brand: String = "Kia Rio",
        mileageKm: Int = 84_300,
        vin: String? = null,
        plate: String? = null,
        tasks: List<NewMaintenanceTask> = emptyList(),
    ) = NewCar(brand = brand, mileageKm = mileageKm, vin = vin, tasks = tasks, plate = plate)

    @Test
    fun carWithBlankBrandIsRejected() = runTest {
        assertNull(addCar(newCar(brand = "  ")))
        assertTrue(repository.addedCars.isEmpty())
    }

    @Test
    fun carWithNonPositiveMileageIsRejected() = runTest {
        assertNull(addCar(newCar(mileageKm = 0)))
        assertNull(addCar(newCar(mileageKm = -5)))
        assertTrue(repository.addedCars.isEmpty())
    }

    @Test
    fun addedCarIsCleanedUp() = runTest {
        val id = addCar(newCar(brand = "  Kia Rio ", vin = "  ", plate = " а123 вс  77 "))

        assertEquals("new-car", id)
        val stored = repository.addedCars.single()
        assertEquals("Kia Rio", stored.brand)
        assertNull(stored.vin)
        assertEquals("А123 ВС 77", stored.plate)
    }

    @Test
    fun rejectedTasksAreDroppedAndOthersNormalized() = runTest {
        val tasks = listOf(
            NewMaintenanceTask(name = " Oil change ", intervalKm = 7_500),
            NewMaintenanceTask(name = "", intervalKm = 7_500),
            NewMaintenanceTask(name = "Pads", intervalKm = 0),
        )

        addCar(newCar(tasks = tasks))

        val stored = repository.addedCars.single().tasks.single()
        assertEquals("Oil change", stored.name)
        assertEquals(1_125, stored.reminderWindowKm)
    }

    @Test
    fun editWithBlankBrandIsRejected() = runTest {
        assertFalse(updateCar("kia", " ", vin = null, plate = null))
        assertTrue(repository.updatedCars.isEmpty())
    }

    @Test
    fun editedCarIsCleanedUp() = runTest {
        assertTrue(updateCar("kia", " Kia Rio X ", vin = " XWEPC811BB0012345 ", plate = ""))

        assertEquals(listOf("kia", "Kia Rio X", "XWEPC811BB0012345", null), repository.updatedCars.single())
    }

    @Test
    fun nonPositiveMileageIsRejected() = runTest {
        assertFalse(updateMileage("kia", 0))
        assertFalse(updateMileage("kia", -1))
        assertTrue(repository.mileageUpdates.isEmpty())
    }

    @Test
    fun lowerMileageIsAcceptedToFixATypo() = runTest {
        assertTrue(updateMileage("kia", 8_430))

        assertEquals("kia" to 8_430, repository.mileageUpdates.single())
    }

    @Test
    fun taskIsDeletedFromItsCar() = runTest {
        DeleteTaskUseCase(repository)("kia", "oil")

        assertEquals("kia" to "oil", repository.deletedTasks.single())
    }
}
