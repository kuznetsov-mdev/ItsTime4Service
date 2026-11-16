package ru.kuznetsov.itstime4service.data.transfer

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.QueryResult
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import ru.kuznetsov.itstime4service.data.repository.TestDatabase
import ru.kuznetsov.itstime4service.data.repository.TaskHistoryRepositoryImpl
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.transfer.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class ApplyImportTest {
    private val db = TestDatabase().create()
    private val now = Instant.parse("2026-11-16T12:00:00.000Z")
    private val clock = object : Clock { override fun now() = now }
    private var zone: TimeZone = TimeZone.UTC
    private fun repo() = TransferRepositoryImpl(db.database, clock = clock, timeZone = { zone })
    private val repository = repo()
    private fun id(value: Int) = TransferFixtures.id(value)

    @AfterTest
    fun close() = db.driver.close()

    private fun local() {
        db.database.carQueries.insert(id(1), "Local", 90000, "TEST-LOCAL-VIN", null, null)
        db.database.maintenanceTaskQueries.insert(id(11), id(1), "Oil", 15000, 85000, 700, 24, "2026-10-01", null)
    }

    private fun decisions(source: TransferSnapshot, target: TargetCar = TargetCar.Existing(id(1))) = ImportDecisions(
        carTargets = source.cars.associate { it.id to target },
        selectedTasks = source.cars.flatMap { car -> car.tasks.map { EntityRef(car.id, it.id) } }.toSet(),
    )

    private suspend fun prepare(source: TransferSnapshot, choices: ImportDecisions, mode: ImportMode = ImportMode.INTO_CAR): PreparedImport {
        val digest = assertIs<TransferOutcome.Success<String>>(repository.fingerprint(source)).value
        return assertIs<TransferOutcome.Success<PreparedImport>>(repository.prepareImport(ImportRequest(source, digest, mode, choices))).value
    }

    private suspend fun ready(source: TransferSnapshot, choices: ImportDecisions, mode: ImportMode = ImportMode.INTO_CAR,
        incoming: Boolean = false): ImportPlan {
        var chosen = choices
        repeat(6) {
            when (val prepared = prepare(source, chosen, mode)) {
                is PreparedImport.Ready -> return prepared.plan
                is PreparedImport.NeedsDecisions -> {
                    val choice = if (incoming) PersonalChoice.TAKE_INCOMING else PersonalChoice.KEEP_LOCAL
                    chosen = chosen.copy(
                        personalChoices = chosen.personalChoices + prepared.decisions.filter { it.reason == ImportDecisionReason.PERSONAL_FIELD }.associate { it.id to choice },
                        relatedChoices = chosen.relatedChoices + prepared.decisions.filter { it.reason == ImportDecisionReason.RELATED_SERVICE_HISTORY }.associate { it.id to choice },
                        soldChoices = chosen.soldChoices + prepared.decisions.filter { it.reason == ImportDecisionReason.SOLD_STATE }.associate { it.id to choice },
                    )
                }
            }
        }
        fail("Unresolved import decisions")
    }

    private suspend fun apply(plan: ImportPlan) = assertIs<TransferOutcome.Success<ImportResult>>(repository.applyImport(plan)).value
    private fun tasks() = db.database.maintenanceTaskQueries.selectByCar(id(1)).executeAsList()
    private fun history() = db.database.serviceHistoryQueries.selectByCar(id(1)).executeAsList()
    private fun replacement() = ImportDecisions(selectedTasks = emptySet(), backupSafety = BackupSafety.EXPLICITLY_DECLINED)

    @Test
    fun missingGroupDoesNotClear() = runTest {
        local()
        val source = TransferFixtures.car()
        val plan = ready(source, decisions(source).copy(taskChoices = mapOf(EntityRef("c1", "t1") to TaskChoice.UPDATE_PARAMETERS)))
        apply(plan)
        assertEquals("TEST-LOCAL-VIN", db.database.carQueries.selectById(id(1)).executeAsOne().vin)
        val task = tasks().single()
        assertEquals(10000, task.interval_km)
        assertEquals(85000, task.last_done_km)
        assertEquals("2026-10-01", task.last_done_date)
        assertEquals(700, task.reminder_window_km)
        assertTrue(history().isEmpty())
    }

    @Test
    fun repeatedConfirmHasOneResultAndRestartDoesNotRepeat() = runTest {
        local()
        val source = TransferFixtures.car()
        val plan = ready(source, decisions(source).copy(taskChoices = mapOf(EntityRef("c1", "t1") to TaskChoice.ADD_SEPARATE)))
        assertFalse(apply(plan).alreadyApplied)
        assertTrue(apply(plan).alreadyApplied)
        val restarted = repo()
        assertTrue(assertIs<TransferOutcome.Success<ImportResult>>(restarted.applyImport(plan)).value.alreadyApplied)
        assertEquals(2, tasks().size)
        assertEquals(plan.summary, assertIs<TransferOutcome.Success<ImportResult?>>(restarted.findOperation(plan.operationId)).value?.summary)
    }

    @Test
    fun reopeningSameFileMatchesProvenanceAndExplicitSeparateIsNewOperation() = runTest {
        local()
        val source = TransferFixtures.car()
        val ref = EntityRef("c1", "t1")
        apply(ready(source, decisions(source).copy(taskChoices = mapOf(ref to TaskChoice.ADD_SEPARATE))))
        apply(ready(source, decisions(source)))
        assertEquals(2, tasks().size)
        apply(ready(source, decisions(source).copy(taskChoices = mapOf(ref to TaskChoice.ADD_SEPARATE))))
        assertEquals(3, tasks().size)
        assertEquals(2, db.database.transferQueries.selectProvenance().executeAsList().count { it.entity_kind == "task" })
    }

    @Test
    fun deletedMappedTaskIsNotSilentlyRecreated() = runTest {
        local()
        val source = TransferFixtures.car()
        val first = ready(source, decisions(source).copy(taskChoices = mapOf(EntityRef("c1", "t1") to TaskChoice.ADD_SEPARATE)))
        apply(first)
        val imported = tasks().last()
        db.database.maintenanceTaskQueries.deleteById(imported.id, id(1))
        val needs = assertIs<PreparedImport.NeedsDecisions>(prepare(source, decisions(source)))
        assertTrue(needs.decisions.any { it.reason == ImportDecisionReason.MISSING_ENTITY })
        assertEquals(1, tasks().size)
    }

    @Test
    fun stateChangedAfterPreviewRequiresReview() = runTest {
        local()
        val source = TransferFixtures.car()
        val plan = ready(source, decisions(source))
        db.database.carQueries.updateMileage(90001, id(1))
        assertEquals(TransferOutcome.Failure(TransferFailure.StalePlan), repository.applyImport(plan))
        assertNull(assertIs<TransferOutcome.Success<ImportResult?>>(repository.findOperation(plan.operationId)).value)
    }

    @Test
    fun futureReminderAndProvenanceChangesAlsoInvalidatePreview() = runTest {
        local()
        val source = TransferFixtures.car()
        val plan = ready(source, decisions(source))
        db.database.plannedReminderQueries.insert(id(11), "2026-12-01T09:00")
        assertEquals(TransferOutcome.Failure(TransferFailure.StalePlan), repository.applyImport(plan))
        val next = ready(source, decisions(source))
        db.database.transferQueries.insertSource(id(900), "synthetic")
        assertEquals(TransferOutcome.Failure(TransferFailure.StalePlan), repository.applyImport(next))
    }

    @Test
    fun writeFailureRollsBackEverythingIncludingJournalAndProvenance() = runTest {
        local()
        val source = TransferFixtures.backup()
        val plan = ready(source, replacement(), ImportMode.REPLACE_BACKUP)
        db.execute("CREATE TRIGGER synthetic_failure BEFORE INSERT ON service_history BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        assertEquals(TransferOutcome.Failure(TransferFailure.DatabaseWriteFailed), repository.applyImport(plan))
        assertEquals("Local", db.database.carQueries.selectById(id(1)).executeAsOne().brand)
        assertEquals(15000, tasks().single().interval_km)
        assertTrue(db.database.transferQueries.selectProvenance().executeAsList().isEmpty())
        assertTrue(db.database.transferQueries.selectSources().executeAsList().isEmpty())
        assertNull(assertIs<TransferOutcome.Success<ImportResult?>>(repository.findOperation(plan.operationId)).value)
    }

    @Test
    fun operationIdCannotBeReusedWithDifferentIdentity() = runTest {
        local()
        val source = TransferFixtures.car()
        val plan = ready(source, decisions(source))
        apply(plan)
        val forged = ImportPlan(plan.operationId, plan.snapshotId, "changed", plan.databaseDigest, plan.decisionsDigest,
            plan.mode, plan.summary, plan.validatedChanges, plan.changesDigest)
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidOperationReuse), repository.applyImport(forged))
    }

    @Test
    fun snapshotIdWithChangedContentIsIdentityConflict() = runTest {
        local()
        val source = TransferFixtures.car()
        apply(ready(source, decisions(source)))
        val changed = source.copy(cars = source.cars.map { it.copy(brand = "Changed") })
        val digest = assertIs<TransferOutcome.Success<String>>(repository.fingerprint(changed)).value
        assertEquals(TransferOutcome.Failure(TransferFailure.SourceIdentityConflict), repository.prepareImport(ImportRequest(changed, digest, ImportMode.INTO_CAR, decisions(changed))))
    }

    @Test
    fun replacementRestoresOrderAndKeepsJournalAcrossFurtherReplacement() = runTest {
        local()
        val original = TransferFixtures.backup()
        val secondCar = original.cars.single().copy(id = id(2), tasks = emptyList(), history = Presence.Present(emptyList()))
        val source = original.copy(cars = listOf(secondCar, original.cars.single()))
        val plan = ready(source, replacement(), ImportMode.REPLACE_BACKUP)
        apply(plan)
        assertEquals(listOf(id(2), id(1)), db.database.carQueries.selectAllWithSold().executeAsList().map { it.id })
        val next = source.copy(snapshotId = id(200), cars = emptyList(), reminderState = Presence.Present(emptyList()))
        apply(ready(next, replacement(), ImportMode.REPLACE_BACKUP))
        assertTrue(db.database.carQueries.selectAllWithSold().executeAsList().isEmpty())
        assertTrue(apply(plan).alreadyApplied)
        assertTrue(db.database.carQueries.selectAllWithSold().executeAsList().isEmpty())
    }

    @Test
    fun newHistoryDoesNotInferLastServiceOrCreateUnselectedTasks() = runTest {
        local()
        val original = TransferFixtures.backup()
        val source = original.copy(cars = original.cars.map { it.copy(id = id(2)) })
        val plan = ready(source, decisions(source).copy(selectedTasks = emptySet()), ImportMode.MERGE_BACKUP, incoming = true)
        apply(plan)
        assertEquals(1, tasks().size)
        assertEquals(85000, tasks().single().last_done_km)
        assertEquals(2, history().size)
        assertTrue(history().all { !it.rollback_available })
        assertTrue(history().none { it.task_id == id(11) })
    }

    @Test
    fun newCarDecisionsRemainStableBetweenPreviewPasses() = runTest {
        val source = TransferFixtures.backup()
        val plan = ready(source, decisions(source, TargetCar.New("Confirmed", 91000)), ImportMode.MERGE_BACKUP, incoming = true)
        apply(plan)
        val car = db.database.carQueries.selectAllWithSold().executeAsList().single()
        assertEquals(91000, car.mileage_km)
        assertEquals(2, db.database.serviceHistoryQueries.selectByCar(car.id).executeAsList().size)
    }

    @Test
    fun validUnknownPredecessorRollbackSurvivesReplacementAndNewMarksUndo() = runTest {
        val original = TransferFixtures.backup()
        val car = original.cars.single()
        val source = original.copy(cars = listOf(car.copy(mileageKm = Presence.Present(80000), tasks = car.tasks.map {
            it.copy(lastService = Presence.Present(LastService(80000, LocalDate(2026, 10, 1))))
        })))
        apply(ready(source, replacement(), ImportMode.REPLACE_BACKUP))
        assertTrue(history().first().rollback_available)
        assertTrue(TaskHistoryRepositoryImpl(db.database).unmarkTaskDone(id(1), id(11)))
        assertNull(tasks().single().last_done_km)
        val taskHistory = TaskHistoryRepositoryImpl(db.database)
        assertTrue(taskHistory.markTaskDone(id(1), id(11), LocalDate(2026, 11, 16), null, null, null))
        assertTrue(taskHistory.unmarkTaskDone(id(1), id(11)))
        assertNull(tasks().single().last_done_km)
    }

    @Test
    fun reminderInstantsMapToReceiverZoneAndTimezoneChangeInvalidatesPlan() = runTest {
        val source = TransferFixtures.backup()
        zone = TimeZone.of("Europe/Moscow")
        val stale = ready(source, replacement(), ImportMode.REPLACE_BACKUP)
        zone = TimeZone.of("America/New_York")
        assertEquals(TransferOutcome.Failure(TransferFailure.StalePlan), repository.applyImport(stale))
        apply(ready(source, replacement(), ImportMode.REPLACE_BACKUP))
        assertEquals("2026-11-10T06:00", db.database.plannedReminderQueries.selectAll().executeAsList().single().fire_at)
    }

    @Test
    fun notificationMarkerPersistsAndRetryStateDoesNotRepeatImport() = runTest {
        local()
        val source = TransferFixtures.car()
        val plan = ready(source, decisions(source))
        assertEquals(NotificationState.PENDING, apply(plan).notifications)
        assertEquals(listOf(plan.operationId), assertIs<TransferOutcome.Success<List<String>>>(repository.pendingNotificationOperations()).value)
        assertIs<TransferOutcome.Success<ImportResult>>(repository.recordNotificationState(plan.operationId, NotificationState.RETRY_REQUIRED))
        assertEquals(NotificationState.RETRY_REQUIRED, apply(plan).notifications)
        assertIs<TransferOutcome.Success<ImportResult>>(repository.recordNotificationState(plan.operationId, NotificationState.APPLIED))
        assertTrue(assertIs<TransferOutcome.Success<List<String>>>(repository.pendingNotificationOperations()).value.isEmpty())
        assertEquals(NotificationState.APPLIED, apply(plan).notifications)
        assertEquals(1, tasks().size)
    }

    @Test
    fun cancellationDuringCapturePropagatesAndDoesNotWrite() = runTest {
        local()
        val cancelled = TransferRepositoryImpl(db.database, clock = object : Clock {
            override fun now(): Instant = throw CancellationException("synthetic")
        })
        val source = TransferFixtures.car()
        val digest = assertIs<TransferOutcome.Success<String>>(repository.fingerprint(source)).value
        assertFailsWith<CancellationException> { cancelled.prepareImport(ImportRequest(source, digest, ImportMode.INTO_CAR, decisions(source))) }
        assertEquals(1, tasks().size)
    }
    @Test
    fun cancellationAfterLastWriteRollsBackAllDataAndLedger() = runTest {
        local()
        var cancelWrites = false
        val cancellingDriver = object : SqlDriver by db.driver {
            override fun execute(identifier: Int?, sql: String, parameters: Int,
                binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> {
                val result = db.driver.execute(identifier, sql, parameters, binders)
                if (cancelWrites && sql.startsWith("INSERT INTO transfer_operations")) throw CancellationException("Synthetic cancellation inside transaction")
                return result
            }
        }
        val cancelling = TransferRepositoryImpl(ItsTime4ServiceDatabase(cancellingDriver), clock = clock, timeZone = { zone })
        val source = TransferFixtures.backup()
        val plan = ready(source, replacement(), ImportMode.REPLACE_BACKUP)
        cancelWrites = true
        assertFailsWith<CancellationException> { cancelling.applyImport(plan) }
        assertEquals("Local", db.database.carQueries.selectById(id(1)).executeAsOne().brand)
        assertEquals(15000, tasks().single().interval_km)
        assertTrue(history().isEmpty())
        assertTrue(db.database.transferQueries.selectProvenance().executeAsList().isEmpty())
        assertNull(assertIs<TransferOutcome.Success<ImportResult?>>(repository.findOperation(plan.operationId)).value)
    }

    @Test
    fun backupMergeMapsEveryCarAndCommitsTogetherPreservingLocalOrder() = runTest {
        local()
        val original = TransferFixtures.backup()
        val first = original.cars.single()
        val second = first.copy(id = id(2), tasks = first.tasks.map { it.copy(id = id(21)) }, history = Presence.Present(emptyList()))
        val source = original.copy(cars = listOf(second, first))
        val choices = decisions(source).copy(carTargets = mapOf(id(1) to TargetCar.Existing(id(1)), id(2) to TargetCar.New("Second", 50000)),
            taskChoices = mapOf(EntityRef(id(1), id(11)) to TaskChoice.UPDATE_PARAMETERS))
        val plan = ready(source, choices, ImportMode.MERGE_BACKUP, incoming = true)
        assertEquals(setOf(id(1), id(2)), plan.preview.carTargets.keys)
        assertEquals(id(11), plan.preview.taskTargets[EntityRef(id(1), id(11))]?.id)
        assertEquals(15000, plan.preview.taskTargets[EntityRef(id(1), id(11))]?.intervalKm)
        apply(plan)
        assertEquals(listOf(id(1), id(2)), db.database.carQueries.selectAllWithSold().executeAsList().map { it.id })
        assertEquals(10000, tasks().single().interval_km)
        assertEquals(1, db.database.maintenanceTaskQueries.selectByCar(id(2)).executeAsList().size)
        assertEquals(2, history().size)
    }

    @Test
    fun lastCarFailureRollsBackEarlierCarsInBackupMerge() = runTest {
        local()
        val original = TransferFixtures.backup()
        val second = original.cars.single().copy(id = id(2), tasks = emptyList(), history = Presence.Present(emptyList()))
        val source = original.copy(cars = original.cars + second)
        val choices = decisions(source).copy(carTargets = mapOf(id(1) to TargetCar.Existing(id(1)), id(2) to TargetCar.New("Second", 50000)))
        val plan = ready(source, choices, ImportMode.MERGE_BACKUP, incoming = true)
        db.execute("CREATE TRIGGER synthetic_last_car_failure BEFORE INSERT ON cars WHEN NEW.id = '${id(2)}' BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        assertEquals(TransferOutcome.Failure(TransferFailure.DatabaseWriteFailed), repository.applyImport(plan))
        assertEquals("Local", db.database.carQueries.selectById(id(1)).executeAsOne().brand)
        assertEquals(1, db.database.carQueries.selectAllWithSold().executeAsList().size)
        assertTrue(history().isEmpty())
        assertTrue(db.database.transferQueries.selectSources().executeAsList().isEmpty())
    }

    @Test
    fun historyDetailsAbsentPreservesExistingDetailsOnUpdate() = runTest {
        local()
        val original = TransferFixtures.backup()
        apply(ready(original, replacement(), ImportMode.REPLACE_BACKUP))
        val selective = TransferFixtures.car().copy(snapshotId = id(200), includedGroups = setOf(TransferGroup.HISTORY), cars = TransferFixtures.car().cars.map {
            it.copy(tasks = emptyList(), history = Presence.Present(listOf(TransferHistory("h1", null, "Manual", LocalDate(2026, 10, 1), 80000, Presence.Absent, Presence.Absent))))
        })
        apply(ready(selective, decisions(selective), incoming = true))
        val importedId = history().last().id
        db.execute("UPDATE service_history SET comment = 'Synthetic local detail' WHERE id = '$importedId'")
        val plan = ready(selective, decisions(selective))
        apply(plan)
        assertEquals("Synthetic local detail", history().last().comment)
        assertEquals(3, history().size)
    }

    @Test
    fun settingsAreRestoredOnMergeAndKeptOnReplacementWhenDisabled() = runTest {
        local()
        db.database.settingsQueries.upsert("DARK", "MILES", false, 1234, "20:30", id(1), true, id(1), "TASKS")
        val source = TransferFixtures.backup()
        apply(ready(source, decisions(source).copy(restoreSettings = true), ImportMode.MERGE_BACKUP))
        assertEquals("SYSTEM", db.database.settingsQueries.select().executeAsOne().theme_mode)
        db.database.settingsQueries.upsert("DARK", "MILES", false, 1234, "20:30", id(1), true, id(1), "TASKS")
        val next = source.copy(snapshotId = id(200))
        apply(ready(next, replacement(), ImportMode.REPLACE_BACKUP))
        val settings = db.database.settingsQueries.select().executeAsOne()
        assertEquals("DARK", settings.theme_mode)
        assertEquals("MILES", settings.distance_unit)
        assertEquals(1234, settings.default_reminder_window_km)
        assertTrue(settings.open_default_car)
    }

    @Test
    fun mergedTaskLimitIsCheckedBeforeAnyWrite() = runTest {
        local()
        db.execute("""
            WITH RECURSIVE n(i) AS (SELECT 30000 UNION ALL SELECT i + 1 FROM n WHERE i < 49998)
            INSERT INTO maintenance_tasks (id, car_id, name, interval_km, last_done_km, reminder_window_km)
            SELECT '00000000-0000-4000-8000-' || printf('%012d', i), '${id(1)}', 'Existing work', 10000, NULL, 1000 FROM n
        """.trimIndent())
        val original = TransferFixtures.car()
        val source = original.copy(cars = original.cars.map { it.copy(brand = "Local", maintenanceDescription = null,
            tasks = it.tasks.map { task -> task.copy(name = "New work") }) })
        val digest = assertIs<TransferOutcome.Success<String>>(repository.fingerprint(source)).value
        assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), repository.prepareImport(ImportRequest(source, digest, ImportMode.INTO_CAR, decisions(source))))
        assertEquals(20000, tasks().size)
        assertTrue(db.database.transferQueries.selectSources().executeAsList().isEmpty())
    }

    @Test
    fun previewAndMutableRequestCannotChangePreparedWrites() = runTest {
        local()
        val source = TransferFixtures.car()
        val targets = mutableMapOf<String, TargetCar>("c1" to TargetCar.Existing(id(1)))
        val choices = mutableMapOf(EntityRef("c1", "t1") to TaskChoice.UPDATE_PARAMETERS)
        val plan = ready(source, decisions(source).copy(carTargets = targets, taskChoices = choices))
        targets.clear()
        choices.clear()
        (plan.preview.cars as MutableList).clear()
        assertEquals(1, plan.preview.cars.size)
        assertEquals(id(11), plan.preview.taskTargets[EntityRef("c1", "t1")]?.id)
        apply(plan)
        assertEquals(10000, tasks().single().interval_km)
    }

    @Test
    fun importedReminderInstantSurvivesDstOverlapAndTimezoneChanges() = runTest {
        val original = TransferFixtures.backup()
        val exact = Instant.parse("2026-11-01T06:30:00.000Z")
        val source = original.copy(reminderState = Presence.Present(listOf(TransferReminder(id(11), exact))))
        zone = TimeZone.of("America/New_York")
        apply(ready(source, replacement(), ImportMode.REPLACE_BACKUP))
        suspend fun shown(): Instant = assertIs<TransferOutcome.Success<TransferSnapshot>>(repository.readSnapshot(ExportSelection.FullBackup))
            .value.reminderState.required().single().shownAt
        assertEquals(exact, shown())
        zone = TimeZone.of("Europe/Moscow")
        assertEquals(exact, shown())
        val reminderRepository = ru.kuznetsov.itstime4service.data.repository.ReminderRepositoryImpl(db.database, timeZone = { zone })
        val records = reminderRepository.getRecords()
        reminderRepository.replaceRecords(records)
        assertEquals(exact, shown())
    }

}
