package ru.kuznetsov.itstime4service.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the database migrations: a database of the first version with real data and a database of the previous
 * version are brought to the current one, keep every stored value and read the added columns with their defaults.
 * The old schemas are the current tables without the columns and the table the migrations added.
 */
class MigrationTest {

    private val schema = ItsTime4ServiceDatabase.Schema

    private fun versionOne(db: TestDatabase) {
        db.execute("CREATE TABLE cars (id TEXT NOT NULL PRIMARY KEY, brand TEXT NOT NULL, mileage_km INTEGER NOT NULL, vin TEXT)")
        db.execute(
            "CREATE TABLE maintenance_tasks (id TEXT NOT NULL PRIMARY KEY, car_id TEXT NOT NULL, name TEXT NOT NULL, " +
                "interval_km INTEGER NOT NULL, last_done_km INTEGER NOT NULL, reminder_window_km INTEGER NOT NULL, " +
                "interval_months INTEGER, last_done_date TEXT)",
        )
        db.execute("CREATE INDEX maintenance_tasks_car_id ON maintenance_tasks (car_id)")
        db.execute(
            "CREATE TABLE service_history (id TEXT NOT NULL PRIMARY KEY, car_id TEXT NOT NULL, task_name TEXT NOT NULL, " +
                "date TEXT NOT NULL, mileage_km INTEGER NOT NULL, cost_rub INTEGER, service_name TEXT)",
        )
        db.execute("CREATE INDEX service_history_car_id ON service_history (car_id)")
        db.execute(
            "CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1), theme_mode TEXT NOT NULL, " +
                "distance_unit TEXT NOT NULL, push_enabled INTEGER NOT NULL, default_reminder_window_km INTEGER NOT NULL, " +
                "reminder_time TEXT NOT NULL)",
        )
        db.execute("INSERT INTO cars VALUES ('kia', 'Kia Rio', 84300, 'XWEPC811BB0012345')")
        db.execute("INSERT INTO cars VALUES ('camry', 'Toyota Camry', 152000, NULL)")
        db.execute("INSERT INTO maintenance_tasks VALUES ('oil', 'kia', 'Oil change', 10000, 80000, 1500, 12, '2026-03-01')")
        db.execute("INSERT INTO maintenance_tasks VALUES ('pads', 'kia', 'Brake pads', 30000, 60000, 3000, NULL, NULL)")
        db.execute("INSERT INTO service_history VALUES ('h1', 'kia', 'Oil change', '2026-03-01', 80000, 4200, 'Kia Center')")
        db.execute("INSERT INTO settings VALUES (1, 'DARK', 'MILES', 0, 3000, '20:30')")
    }

    @Test
    fun currentVersionIsFive() {
        assertEquals(5L, schema.version)
    }

    @Test
    fun firstVersionIsBroughtToTheCurrentOneKeepingData() = runTest {
        val db = TestDatabase()
        versionOne(db)

        schema.migrate(db.driver, 1, schema.version)

        val cars = CarRepositoryImpl(db.database).observeCars().first()
        assertEquals(listOf("kia", "camry"), cars.map { it.id })
        val kia = cars.first()
        assertEquals(84_300, kia.mileageKm)
        assertEquals("XWEPC811BB0012345", kia.vin)
        assertNull(kia.plate)
        assertFalse(kia.plateHidden)
        assertFalse(kia.vinHidden)
        assertEquals(listOf("Oil change", "Brake pads"), kia.tasks.map { it.name })
        assertEquals(LocalDate(2026, 3, 1), kia.tasks[0].lastDoneDate)
        assertNull(kia.tasks[0].icon)

        val entry = ServiceHistoryRepositoryImpl(db.database).observeHistory("kia").first().single()
        assertEquals(4_200, entry.costRub)
        assertEquals("Kia Center", entry.serviceName)
        assertNull(entry.taskId)
        assertNull(entry.comment)
        assertNull(entry.prevLastDoneKm)

        val settings = SettingsRepositoryImpl(db.database).observeSettings().first()
        assertEquals(DistanceUnit.MILES, settings.distanceUnit)
        assertFalse(settings.pushEnabled)
        assertNull(settings.lastOpenedCarId)
        assertFalse(settings.openDefaultCar)
        assertEquals(CarsView.LIST, settings.carsView)

        assertTrue(ReminderRepositoryImpl(db.database).getRecords().isEmpty())
        assertTrue(CarRepositoryImpl(db.database).observeCarSummaries().first().none { it.isSold })
    }

    @Test
    fun previousVersionGetsHiddenFlagsOff() = runTest {
        val db = TestDatabase()
        versionOne(db)
        schema.migrate(db.driver, 1, 4)
        db.execute("UPDATE cars SET plate = 'А123ВС77' WHERE id = 'kia'")

        schema.migrate(db.driver, 4, schema.version)

        val kia = CarRepositoryImpl(db.database).observeCarById("kia").first()!!
        assertEquals("А123ВС77", kia.plate)
        assertFalse(kia.plateHidden)
        assertFalse(kia.vinHidden)
        CarRepositoryImpl(db.database).setPlateHidden("kia", true)
        assertTrue(CarRepositoryImpl(db.database).observeCarById("kia").first()!!.plateHidden)
    }

    @Test
    fun migratedDatabaseAcceptsNewWrites() = runTest {
        val db = TestDatabase()
        versionOne(db)
        schema.migrate(db.driver, 1, schema.version)
        val taskHistory = TaskHistoryRepositoryImpl(db.database)

        assertTrue(taskHistory.markTaskDone("kia", "oil", LocalDate(2026, 10, 20), null, null, null))
        assertTrue(taskHistory.unmarkTaskDone("kia", "oil"))
        assertEquals(1, ServiceHistoryRepositoryImpl(db.database).observeHistory("kia").first().size)
    }
}
