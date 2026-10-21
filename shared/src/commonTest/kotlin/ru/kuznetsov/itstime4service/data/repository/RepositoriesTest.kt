package ru.kuznetsov.itstime4service.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewTaskRecord
import ru.kuznetsov.itstime4service.domain.model.ReminderRecord
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [CarRepositoryImpl], [ServiceHistoryRepositoryImpl], [SettingsRepositoryImpl] and [ReminderRepositoryImpl] on
 * a real SQLite database: every field makes the round trip, `null` stays `null`, rows keep the order they were added
 * in, and observers see each write.
 */
class RepositoriesTest {

    private val db = TestDatabase().create()
    private val cars = CarRepositoryImpl(db.database)
    private val history = ServiceHistoryRepositoryImpl(db.database)
    private val settings = SettingsRepositoryImpl(db.database)
    private val reminders = ReminderRepositoryImpl(db.database)
    private val taskHistory = TaskHistoryRepositoryImpl(db.database)

    private fun task(name: String, record: NewTaskRecord? = null) =
        NewMaintenanceTask(name = name, intervalKm = 10_000, lastDoneKm = 80_000, reminderWindowKm = 1_500, record = record)

    private suspend fun addCar(brand: String, vararg tasks: NewMaintenanceTask) =
        taskHistory.addCar(NewCar(brand = brand, mileageKm = 84_300, vin = null, tasks = tasks.toList()))

    @Test
    fun carsAndTasksKeepTheirFieldsAndOrder() = runTest {
        val id = taskHistory.addCar(
            NewCar(
                brand = "Kia Rio",
                mileageKm = 84_300,
                vin = "XWEPC811BB0012345",
                tasks = listOf(
                    NewMaintenanceTask("Oil", 7_500, 80_000, 1_500, intervalMonths = 12, lastDoneDate = LocalDate(2026, 3, 1)),
                    NewMaintenanceTask("Pads", 30_000, 0, 3_000, icon = null),
                ),
                plate = "А123ВС77",
            ),
        )
        addCar("Camry")

        val listed = cars.observeCars().first()
        assertEquals(listOf("Kia Rio", "Camry"), listed.map { it.brand })
        val kia = listed.first()
        assertEquals(id, kia.id)
        assertEquals("XWEPC811BB0012345", kia.vin)
        assertEquals("А123ВС77", kia.plate)
        assertEquals(listOf("Oil", "Pads"), kia.tasks.map { it.name })
        assertEquals(12, kia.tasks[0].intervalMonths)
        assertEquals(LocalDate(2026, 3, 1), kia.tasks[0].lastDoneDate)
        assertNull(kia.tasks[1].intervalMonths)
        assertNull(kia.tasks[1].icon)
        assertNull(listed[1].vin)
    }

    @Test
    fun carEditsReachObservers() = runTest {
        val id = addCar("Kia Rio")

        cars.updateMileage(id, 85_000)
        cars.updateCar(id, "Kia Rio X", "VIN", null)
        cars.setPlateHidden(id, true)
        cars.setVinHidden(id, true)

        val car = cars.observeCarById(id).first()!!
        assertEquals(85_000, car.mileageKm)
        assertEquals("Kia Rio X", car.brand)
        assertEquals("VIN", car.vin)
        assertNull(car.plate)
        assertTrue(car.plateHidden)
        assertTrue(car.vinHidden)
    }

    @Test
    fun deletedTaskKeepsItsHistory() = runTest {
        val id = addCar("Kia Rio", task("Oil", NewTaskRecord(LocalDate(2026, 3, 1))))
        val taskId = cars.observeCarById(id).first()!!.tasks.single().id

        cars.deleteTask(id, taskId)

        assertTrue(cars.observeCarById(id).first()!!.tasks.isEmpty())
        assertEquals("Oil", history.observeHistory(id).first().single().taskName)
    }

    @Test
    fun missingCarIsNull() = runTest {
        assertNull(cars.observeCarById("ghost").first())
    }

    @Test
    fun recordAddedByHandHasNoTask() = runTest {
        val id = addCar("Kia Rio")

        history.addEntry(id, "Wipers", LocalDate(2026, 5, 2), 82_000, null, 900, "Bosch")

        val entry = history.observeHistory(id).first().single()
        assertEquals("Wipers", entry.taskName)
        assertEquals(900, entry.costRub)
        assertNull(entry.serviceName)
        assertEquals("Bosch", entry.comment)
        assertNull(entry.taskId)
        assertNull(entry.prevLastDoneKm)
    }

    @Test
    fun historyOfEveryCarIsListedInOrderOfAdding() = runTest {
        val kia = addCar("Kia Rio")
        val camry = addCar("Camry")
        history.addEntry(kia, "First", LocalDate(2026, 5, 2), 82_000, null, null, null)
        history.addEntry(camry, "Second", LocalDate(2025, 1, 1), 150_000, null, null, null)
        history.addEntry(kia, "Third", LocalDate(2024, 1, 1), 70_000, null, null, null)

        assertEquals(listOf("First", "Second", "Third"), history.observeAllHistory().first().map { it.taskName })
        assertEquals(listOf("First", "Third"), history.observeHistory(kia).first().map { it.taskName })
    }

    @Test
    fun settingsStartWithDefaults() = runTest {
        val stored = settings.observeSettings().first()

        assertEquals(ThemeMode.SYSTEM, stored.themeMode)
        assertEquals(DistanceUnit.KILOMETERS, stored.distanceUnit)
        assertTrue(stored.pushEnabled)
        assertEquals(LocalTime(9, 0), stored.reminderTime)
        assertEquals(CarsView.LIST, stored.carsView)
    }

    @Test
    fun everySettingMakesTheRoundTrip() = runTest {
        settings.updateSettings {
            it.copy(
                themeMode = ThemeMode.DARK,
                distanceUnit = DistanceUnit.MILES,
                pushEnabled = false,
                defaultReminderWindowKm = 3_000,
                reminderTime = LocalTime(20, 30),
                lastOpenedCarId = "kia",
                openDefaultCar = true,
                defaultCarId = "camry",
                carsView = CarsView.TASKS_AND_HISTORY,
            )
        }

        val stored = settings.observeSettings().first()
        assertEquals(ThemeMode.DARK, stored.themeMode)
        assertEquals(DistanceUnit.MILES, stored.distanceUnit)
        assertFalse(stored.pushEnabled)
        assertEquals(3_000, stored.defaultReminderWindowKm)
        assertEquals(LocalTime(20, 30), stored.reminderTime)
        assertEquals("kia", stored.lastOpenedCarId)
        assertTrue(stored.openDefaultCar)
        assertEquals("camry", stored.defaultCarId)
        assertEquals(CarsView.TASKS_AND_HISTORY, stored.carsView)
    }

    @Test
    fun unknownStoredCarsViewReadsAsList() = runTest {
        settings.updateSettings { it }
        db.execute("UPDATE settings SET cars_view = 'GRID'")

        assertEquals(CarsView.LIST, settings.observeSettings().first().carsView)
    }

    @Test
    fun reminderPlanIsReplacedAsAWhole() = runTest {
        reminders.replaceRecords(listOf(ReminderRecord("oil", LocalDateTime(2026, 10, 21, 9, 0))))
        reminders.replaceRecords(
            listOf(
                ReminderRecord("pads", LocalDateTime(2026, 10, 22, 9, 0)),
                ReminderRecord("oil", LocalDateTime(2026, 10, 24, 9, 0)),
            ),
        )

        assertEquals(
            listOf(ReminderRecord("pads", LocalDateTime(2026, 10, 22, 9, 0)), ReminderRecord("oil", LocalDateTime(2026, 10, 24, 9, 0))),
            reminders.getRecords(),
        )
    }
}
