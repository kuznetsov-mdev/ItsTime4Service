package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.data.repository.TestDatabase
import ru.kuznetsov.itstime4service.data.repository.SettingsRepositoryImpl
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import ru.kuznetsov.itstime4service.domain.transfer.CarPreferences
import ru.kuznetsov.itstime4service.domain.transfer.ExportSelection
import ru.kuznetsov.itstime4service.domain.transfer.HistoryDetails
import ru.kuznetsov.itstime4service.domain.transfer.LastService
import ru.kuznetsov.itstime4service.domain.transfer.Presence
import ru.kuznetsov.itstime4service.domain.transfer.RollbackData
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferGroup
import ru.kuznetsov.itstime4service.domain.transfer.TransferHistory
import ru.kuznetsov.itstime4service.domain.transfer.TransferKind
import ru.kuznetsov.itstime4service.domain.transfer.TransferOutcome
import ru.kuznetsov.itstime4service.domain.transfer.TransferReminder
import ru.kuznetsov.itstime4service.domain.transfer.TransferSnapshot
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class TransferSnapshotTest {
    private val db = TestDatabase().create()
    private val now = Instant.parse("2026-11-10T12:00:00.123999Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val repository = TransferRepositoryImpl(db.database, clock = clock, timeZone = { TimeZone.UTC })

    @AfterTest
    fun close() = db.driver.close()

    @Test
    fun defaultSelectionContainsNoPersonalFacts() = runTest {
        populate()
        val snapshot = snapshot(ExportSelection.Car(id(1), setOf(id(11))))
        assertEquals(TransferKind.CAR, snapshot.kind)
        assertEquals(emptySet(), snapshot.includedGroups)
        assertEquals(Presence.Absent, snapshot.settings)
        assertEquals(Presence.Absent, snapshot.reminderState)
        val car = snapshot.cars.single()
        assertEquals("c1", car.id)
        assertEquals("Test brand", car.brand)
        assertNull(car.maintenanceDescription)
        assertEquals(Presence.Absent, car.mileageKm)
        assertEquals(Presence.Absent, car.vin)
        assertEquals(Presence.Absent, car.plate)
        assertEquals(Presence.Absent, car.sold)
        assertEquals(Presence.Absent, car.preferences)
        assertEquals(Presence.Absent, car.history)
        val task = car.tasks.single()
        assertEquals("t1", task.id)
        assertEquals(10_000, task.intervalKm)
        assertEquals(TaskIcon.FILTER, task.icon)
        assertEquals(Presence.Absent, task.lastService)
        assertEquals(Presence.Absent, task.reminderWindowKm)
        listOf(id(1), id(11), "TEST-VIN-PRIVATE", "TEST-PLATE-PRIVATE", "stored private description", "private comment").forEach {
            assertFalse(snapshot.toString().contains(it))
        }
    }

    @Test
    fun eachPersonalGroupIsIndependentAndNullRemainsPresent() = runTest {
        populate()
        listOf(TransferGroup.MILEAGE, TransferGroup.VIN, TransferGroup.PLATE, TransferGroup.LAST_SERVICE, TransferGroup.HISTORY).forEach { group ->
            val car = snapshot(ExportSelection.Car(id(1), setOf(id(11)), setOf(group))).cars.single()
            assertEquals(group == TransferGroup.MILEAGE, car.mileageKm is Presence.Present)
            assertEquals(group == TransferGroup.VIN, car.vin is Presence.Present)
            assertEquals(group == TransferGroup.PLATE, car.plate is Presence.Present)
            assertEquals(group == TransferGroup.HISTORY, car.history is Presence.Present)
            assertEquals(group == TransferGroup.LAST_SERVICE, car.tasks.single().lastService is Presence.Present)
        }
        db.database.carQueries.updateDetails("Test brand", null, null, id(1))
        val car = snapshot(ExportSelection.Car(id(1), emptySet(), setOf(TransferGroup.VIN, TransferGroup.PLATE))).cars.single()
        assertEquals(Presence.Present(null), car.vin)
        assertEquals(Presence.Present(null), car.plate)
    }

    @Test
    fun historyDoesNotIncludeLastServiceOrUnselectedTasks() = runTest {
        populate()
        val car = snapshot(ExportSelection.Car(id(1), setOf(id(11)), setOf(TransferGroup.HISTORY))).cars.single()
        assertEquals(listOf("t1"), car.tasks.map { it.id })
        assertEquals(Presence.Absent, car.tasks.single().lastService)
        val history = assertIs<Presence.Present<List<TransferHistory>>>(car.history).value
        assertEquals(listOf("h1", "h2", "h3", "h4", "h5"), history.map { it.id })
        assertEquals(listOf("t1", "t1", "d1", "d2", null), history.map { it.taskId })
        history.forEach {
            assertEquals(Presence.Absent, it.details)
            assertEquals(Presence.Absent, it.rollback)
        }
        assertFalse(car.toString().contains("private comment"))
        assertFalse(car.toString().contains(id(99)))
        assertEquals(5, assertIs<Presence.Present<List<*>>>(snapshot(
            ExportSelection.Car(id(1), emptySet(), setOf(TransferGroup.HISTORY)),
        ).cars.single().history).value.size)
    }

    @Test
    fun historyDetailsRequireExplicitSelectionAndKeepNullableFields() = runTest {
        populate()
        val car = snapshot(ExportSelection.Car(id(1), emptySet(), setOf(TransferGroup.HISTORY, TransferGroup.HISTORY_DETAILS))).cars.single()
        val history = assertIs<Presence.Present<List<TransferHistory>>>(car.history).value
        assertEquals(Presence.Present(HistoryDetails(2500, "Test service", "private comment")), history.first().details)
        assertEquals(Presence.Present(HistoryDetails(null, null, null)), history.last().details)
        assertEquals(Presence.Absent, history.first().rollback)
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), repository.readSnapshot(
            ExportSelection.Car(id(1), emptySet(), setOf(TransferGroup.HISTORY_DETAILS)),
        ))
    }

    @Test
    fun soldCarsDanglingHistoryOrderSettingsAndRollbackSurviveBackup() = runTest {
        populate()
        db.database.settingsQueries.upsert("DARK", "MILES", false, 1234, "17:30:00", id(2), true, id(3), "TASKS_AND_HISTORY")
        val snapshot = snapshot(ExportSelection.FullBackup)
        assertEquals(TransferKind.BACKUP, snapshot.kind)
        assertEquals(TransferGroup.entries.toSet(), snapshot.includedGroups)
        assertEquals(listOf(id(1), id(3), id(2)), snapshot.cars.map { it.id })
        val car = snapshot.cars.first()
        assertEquals("stored private description", car.maintenanceDescription)
        assertEquals(Presence.Present("TEST-VIN-PRIVATE"), car.vin)
        assertEquals(Presence.Present("TEST-PLATE-PRIVATE"), car.plate)
        assertEquals(Presence.Present(CarPreferences(true, true)), car.preferences)
        assertEquals(listOf(id(11), id(12)), car.tasks.map { it.id })
        assertEquals(Presence.Present(LastService(null, LocalDate(2026, 10, 1))), car.tasks.first().lastService)
        assertEquals(Presence.Present(LastService(0, null)), car.tasks.last().lastService)
        assertEquals(Presence.Present(1700), car.tasks.first().reminderWindowKm)
        val history = assertIs<Presence.Present<List<TransferHistory>>>(car.history).value
        assertEquals(listOf(id(101), id(102), id(103), id(104), id(105)), history.map { it.id })
        assertEquals(history[0].date, history[1].date)
        assertEquals(id(99), history[2].taskId)
        assertEquals(Presence.Present(RollbackData(LastService(null, null))), history.first().rollback)
        assertEquals(Presence.Present(null), history.last().rollback)
        assertEquals(Presence.Present(true), snapshot.cars.last().sold)
        assertEquals(emptyList(), snapshot.cars.last().tasks)
        assertEquals(1, assertIs<Presence.Present<List<*>>>(snapshot.cars.last().history).value.size)
        assertEquals(
            Presence.Present(SettingsRepositoryImpl(db.database).observeSettings().first()),
            snapshot.settings,
        )
    }

    @Test
    fun reminderSnapshotDropsFutureAndDanglingRowsAndFloorsTimestamps() = runTest {
        populate()
        db.database.plannedReminderQueries.insert(id(11), "2026-11-10T11:00:00")
        db.database.plannedReminderQueries.insert(id(11), "2026-11-10T12:00:00.123499")
        db.database.plannedReminderQueries.insert(id(11), "2026-11-10T12:00:00.124")
        db.database.plannedReminderQueries.insert(id(99), "2026-11-10T11:00:00")
        val snapshot = snapshot(ExportSelection.FullBackup)
        assertEquals(Instant.parse("2026-11-10T12:00:00.123Z"), snapshot.createdAt)
        val reminders = assertIs<Presence.Present<List<TransferReminder>>>(snapshot.reminderState).value
        assertEquals(listOf(id(11)), reminders.map { it.taskId })
        assertEquals(Instant.parse("2026-11-10T12:00:00.123Z"), reminders.single().shownAt)
    }

    @Test
    fun remindersUseCapturedZoneIncludingDstGapAndOverlap() = runTest {
        car(1)
        task(11, 1)
        val cases = listOf(
            Triple("Europe/Berlin", "2026-03-29T02:30:00", "2026-03-29T01:30:00Z"),
            Triple("Europe/Berlin", "2026-10-25T02:30:00", "2026-10-25T00:30:00Z"),
            Triple("America/New_York", "2026-10-25T02:30:00", "2026-10-25T06:30:00Z"),
            Triple("Asia/Tokyo", "2026-10-25T02:30:00", "2026-10-24T17:30:00Z"),
        )
        cases.forEach { (zone, local, expected) ->
            db.database.plannedReminderQueries.deleteAll()
            db.database.plannedReminderQueries.insert(id(11), local)
            var zoneReads = 0
            val source = TransferRepositoryImpl(db.database, clock = clock, timeZone = {
                zoneReads++
                TimeZone.of(zone)
            })
            val snapshot = assertIs<TransferOutcome.Success<TransferSnapshot>>(source.readSnapshot(ExportSelection.FullBackup)).value
            val reminder = assertIs<Presence.Present<List<TransferReminder>>>(snapshot.reminderState).value.single()
            assertEquals(Instant.parse(expected), reminder.shownAt)
            assertEquals(1, zoneReads)
        }
    }

    @Test
    fun selectionOrderDoesNotReorderTasksAndDescriptionIsAuthored() = runTest {
        populate()
        val first = snapshot(ExportSelection.Car(id(1), linkedSetOf(id(12), id(11)), maintenanceDescription = "Confirmed template"))
        val second = snapshot(ExportSelection.Car(id(1), setOf(id(11), id(12)), maintenanceDescription = "Confirmed template"))
        assertEquals(first.cars, second.cars)
        assertEquals(listOf("Oil", "Pads"), first.cars.single().tasks.map { it.name })
        assertEquals("Confirmed template", first.cars.single().maintenanceDescription)
        assertNotEquals(first.snapshotId, second.snapshotId)
    }

    @Test
    fun emptyDatabaseExportsEffectiveDefaults() = runTest {
        val snapshot = snapshot(ExportSelection.FullBackup)
        assertEquals(emptyList(), snapshot.cars)
        assertEquals(Presence.Present(emptyList()), snapshot.reminderState)
        assertEquals(
            SettingsRepositoryImpl(db.database).observeSettings().first(),
            assertIs<Presence.Present<Settings>>(snapshot.settings).value,
        )
    }

    @Test
    fun staleSelectionAndInvalidStoredNumbersFailWithoutChangingDatabase() = runTest {
        populate()
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), repository.readSnapshot(ExportSelection.Car(id(1), setOf(id(31)))))
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), repository.readSnapshot(ExportSelection.Car(id(9), emptySet())))
        db.execute("UPDATE maintenance_tasks SET interval_km = 2147483648 WHERE id = '${id(11)}'")
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), repository.readSnapshot(ExportSelection.FullBackup))
        assertEquals(2_147_483_648L, db.database.maintenanceTaskQueries.selectById(id(11), id(1)).executeAsOne().interval_km)
    }

    @Test
    fun limitsDoNotTruncateAndExcludedDetailsAreNotValidated() = runTest {
        populate()
        val longText = "я".repeat(8193)
        db.execute("UPDATE service_history SET comment = '$longText'")
        snapshot(ExportSelection.Car(id(1), emptySet(), setOf(TransferGroup.HISTORY)))
        assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), repository.readSnapshot(ExportSelection.FullBackup))
        assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), repository.readSnapshot(
            ExportSelection.Car(id(1), emptySet(), maintenanceDescription = longText),
        ))
    }

    @Test
    fun tooManyCarsFailInsteadOfReturningPartialBackup() = runTest {
        db.database.transaction { (1..1001).forEach { car(it) } }
        assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), repository.readSnapshot(ExportSelection.FullBackup))
        assertEquals(1001, db.database.carQueries.selectAllWithSold().executeAsList().size)
        assertEquals(1, snapshot(ExportSelection.Car(id(1), emptySet())).cars.size)
    }

    @Test
    fun taskCountBoundaryIsAppliedAfterSelection() = runTest {
        car(1)
        db.execute("""
            WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 20000)
            INSERT INTO maintenance_tasks (id, car_id, name, interval_km, last_done_km, reminder_window_km)
            SELECT '00000000-0000-4000-8000-' || printf('%012d', i), '${id(1)}', 'Work', 10000, NULL, 1000 FROM n
        """.trimIndent())
        assertEquals(20000, snapshot(ExportSelection.FullBackup).cars.single().tasks.size)
        task(20001, 1)
        assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), repository.readSnapshot(ExportSelection.FullBackup))
        val car = snapshot(ExportSelection.Car(id(1), setOf(id(1), id(20001)))).cars.single()
        assertEquals(listOf("t1", "t2"), car.tasks.map { it.id })
    }

    @Test
    fun historyCountBoundaryDoesNotReturnPartialHistory() = runTest {
        car(1)
        db.execute("""
            WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 50000)
            INSERT INTO service_history (id, car_id, task_name, date, mileage_km)
            SELECT '00000000-0000-4000-8000-' || printf('%012d', i), '${id(1)}', 'Work', '2026-01-01', 0 FROM n
        """.trimIndent())
        assertEquals(50000, assertIs<Presence.Present<List<TransferHistory>>>(
            snapshot(ExportSelection.FullBackup).cars.single().history,
        ).value.size)
        history(50001, 1, null)
        assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), repository.readSnapshot(ExportSelection.FullBackup))
        assertEquals(Presence.Absent, snapshot(ExportSelection.Car(id(1), emptySet())).cars.single().history)
    }

    @Test
    fun unicodeByteBoundariesAndLargestNumbersArePreserved() = runTest {
        car(1)
        task(11, 1)
        db.database.carQueries.updateDetails("я".repeat(512), "в".repeat(128), "н".repeat(128), id(1))
        db.execute("UPDATE maintenance_tasks SET interval_km = 2147483647, last_done_km = 2147483647, interval_months = 120000")
        val car = snapshot(ExportSelection.FullBackup).cars.single()
        assertEquals(1024, car.brand.encodeToByteArray().size)
        assertEquals(Int.MAX_VALUE, car.tasks.single().intervalKm)
        assertEquals(120000, car.tasks.single().intervalMonths)
        assertEquals(Presence.Present(LastService(Int.MAX_VALUE, null)), car.tasks.single().lastService)
        db.database.carQueries.updateDetails("я".repeat(513), null, null, id(1))
        assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), repository.readSnapshot(ExportSelection.FullBackup))
    }

    @Test
    fun crossCarHistoryLinksAndTasksUnderSoldCarsAreRejected() = runTest {
        populate()
        history(106, 1, 31)
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), repository.readSnapshot(ExportSelection.FullBackup))
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), repository.readSnapshot(
            ExportSelection.Car(id(1), emptySet(), setOf(TransferGroup.HISTORY)),
        ))
        snapshot(ExportSelection.Car(id(1), setOf(id(11))))
        db.database.serviceHistoryQueries.deleteById(id(106))
        task(29, 2)
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), repository.readSnapshot(ExportSelection.FullBackup))
    }

    @Test
    fun readsRunInsideTransactionAndCancellationIsNotAnErrorResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val checkingClock = object : Clock {
            override fun now(): Instant {
                assertNotNull(db.driver.currentTransaction())
                return now
            }
        }
        val source = TransferRepositoryImpl(db.database, dispatcher, checkingClock, { TimeZone.UTC })
        assertIs<TransferOutcome.Success<TransferSnapshot>>(source.readSnapshot(ExportSelection.FullBackup))
        val cancelledClock = object : Clock {
            override fun now(): Instant = throw CancellationException("synthetic cancellation")
        }
        assertFailsWith<CancellationException> {
            TransferRepositoryImpl(db.database, dispatcher, cancelledClock).readSnapshot(ExportSelection.FullBackup)
        }
    }

    @Test
    fun databaseFailureHasNoRawDiagnosticOrPartialSnapshot() = runTest {
        db.execute("ALTER TABLE cars RENAME TO unavailable_cars")
        assertEquals(TransferOutcome.Failure(TransferFailure.DatabaseReadFailed), repository.readSnapshot(ExportSelection.FullBackup))
    }

    private suspend fun snapshot(selection: ExportSelection): TransferSnapshot =
        assertIs<TransferOutcome.Success<TransferSnapshot>>(repository.readSnapshot(selection)).value

    private fun populate() {
        car(1)
        car(2)
        car(3)
        db.database.carQueries.markSold(id(2))
        db.database.carQueries.setPlateHidden(true, id(1))
        db.database.carQueries.setVinHidden(true, id(1))
        task(11, 1, lastDone = null, date = "2026-10-01")
        task(12, 1, "Pads", lastDone = 0)
        task(31, 3)
        history(101, 1, 11, rollback = true, details = true)
        history(102, 1, 11)
        history(103, 1, 99)
        history(104, 1, 12)
        history(105, 1, null)
        history(201, 2, 29)
    }

    private fun car(number: Int) = db.database.carQueries.insert(
        id(number), "Test brand", 84300, "TEST-VIN-PRIVATE", "TEST-PLATE-PRIVATE", "stored private description",
    )

    private fun task(number: Int, car: Int, name: String = "Oil", lastDone: Long? = 80000, date: String? = null) =
        db.database.maintenanceTaskQueries.insert(id(number), id(car), name, 10000, lastDone, 1700, 12, date, "FILTER")

    private fun history(number: Int, car: Int, task: Int?, rollback: Boolean = false, details: Boolean = false) =
        db.database.serviceHistoryQueries.insert(
            id(number), id(car), "History work", "2026-10-01", 80000,
            if (details) 2500 else null, if (details) "Test service" else null,
            task?.let(::id), if (details) "private comment" else null, null, null, rollback,
        )

    private fun id(number: Int): String = "00000000-0000-4000-8000-${number.toString().padStart(12, '0')}"
}
