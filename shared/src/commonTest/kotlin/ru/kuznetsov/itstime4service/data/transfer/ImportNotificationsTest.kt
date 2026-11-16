package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import ru.kuznetsov.itstime4service.data.repository.*
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.PlannedReminder
import ru.kuznetsov.itstime4service.domain.repository.ReminderScheduler
import ru.kuznetsov.itstime4service.domain.transfer.*
import ru.kuznetsov.itstime4service.domain.usecase.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class ImportNotificationsTest {
    private val db = TestDatabase().create()
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-16T12:00:00.000Z") }
    private fun repository() = TransferRepositoryImpl(db.database, clock = clock, timeZone = { TimeZone.UTC })
    private val repository = repository()
    private var calls = 0
    private var fail = false
    private var cancel = false
    private val scheduler = object : ReminderScheduler {
        override suspend fun schedule(reminders: List<PlannedReminder>, distanceUnit: DistanceUnit) {
            assertNull(db.driver.currentTransaction())
            calls++
            if (cancel) throw CancellationException("Synthetic cancellation after commit")
            if (fail) error("Synthetic scheduler failure")
        }
    }

    @AfterTest
    fun close() = db.driver.close()

    private fun reconciler(repo: TransferRepository = repository): ReconcileImportNotificationsUseCase = ReconcileImportNotificationsUseCase(
        repo, ObserveCarsUseCase(CarRepositoryImpl(db.database)), ObserveSettingsUseCase(SettingsRepositoryImpl(db.database)),
        SyncRemindersUseCase(PlanRemindersUseCase(CalculateTaskStatusUseCase()), GetNowUseCase(clock) { TimeZone.UTC },
            ReminderRepositoryImpl(db.database), scheduler),
    )

    private suspend fun plan(): ImportPlan {
        val source = TransferFixtures.car()
        val digest = assertIs<TransferOutcome.Success<String>>(repository.fingerprint(source)).value
        val request = ImportRequest(source, digest, ImportMode.INTO_CAR, ImportDecisions(
            carTargets = mapOf("c1" to TargetCar.New("Confirmed", 90000)), selectedTasks = setOf(EntityRef("c1", "t1"))))
        return assertIs<PreparedImport.Ready>(assertIs<TransferOutcome.Success<PreparedImport>>(repository.prepareImport(request)).value).plan
    }

    @Test
    fun notificationFailureDoesNotReimportAndRetrySchedulesOnly() = runTest {
        val plan = plan()
        val reconcile = reconciler()
        fail = true
        val applied = assertIs<TransferOutcome.Success<ImportResult>>(ApplyImportUseCase(repository, reconcile)(plan)).value
        assertEquals(NotificationState.RETRY_REQUIRED, applied.notifications)
        assertFalse(applied.alreadyApplied)
        val before = db.database.carQueries.selectAllWithSold().executeAsList()
        fail = false
        val retry = assertIs<TransferOutcome.Success<ImportResult>>(reconcile(plan.operationId)).value
        assertEquals(NotificationState.APPLIED, retry.notifications)
        assertEquals(before, db.database.carQueries.selectAllWithSold().executeAsList())
        assertEquals(2, calls)
        assertIs<TransferOutcome.Success<ImportResult>>(reconcile(plan.operationId))
        assertEquals(2, calls)
    }

    @Test
    fun restartRecoversPendingMarkerWithoutApplyingAgain() = runTest {
        val plan = plan()
        assertIs<TransferOutcome.Success<ImportResult>>(repository.applyImport(plan))
        val restarted = repository()
        val results = assertIs<TransferOutcome.Success<List<ImportResult>>>(reconciler(restarted).retryPending()).value
        assertEquals(1, results.size)
        assertEquals(NotificationState.APPLIED, results.single().notifications)
        assertEquals(1, calls)
        assertEquals(1, db.database.carQueries.selectAllWithSold().executeAsList().size)
    }

    @Test
    fun cancellationAfterCommitLeavesRecoverableOperation() = runTest {
        val plan = plan()
        cancel = true
        assertFailsWith<CancellationException> { ApplyImportUseCase(repository, reconciler())(plan) }
        val saved = assertIs<TransferOutcome.Success<ImportResult?>>(repository.findOperation(plan.operationId)).value
        assertNotNull(saved)
        assertEquals(NotificationState.PENDING, saved.notifications)
        assertEquals(1, db.database.carQueries.selectAllWithSold().executeAsList().size)
        cancel = false
        assertIs<TransferOutcome.Success<List<ImportResult>>>(reconciler().retryPending())
        assertEquals(1, db.database.carQueries.selectAllWithSold().executeAsList().size)
    }

    @Test
    fun markerWriteFailureStillReportsCommittedImportAndKeepsPendingMarker() = runTest {
        val plan = plan()
        db.execute("CREATE TRIGGER synthetic_marker_failure BEFORE UPDATE ON transfer_operations BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        val result = assertIs<TransferOutcome.Success<ImportResult>>(ApplyImportUseCase(repository, reconciler())(plan)).value
        assertEquals(NotificationState.RETRY_REQUIRED, result.notifications)
        assertEquals(1, db.database.carQueries.selectAllWithSold().executeAsList().size)
        assertEquals(listOf(plan.operationId), assertIs<TransferOutcome.Success<List<String>>>(repository.pendingNotificationOperations()).value)
    }
    @Test
    fun reconciliationPreservesExactImportedShownInstant() = runTest {
        val original = TransferFixtures.backup()
        val exact = Instant.parse("2026-11-01T06:30:00.000Z")
        val source = original.copy(reminderState = Presence.Present(listOf(TransferReminder(TransferFixtures.id(11), exact))))
        val digest = assertIs<TransferOutcome.Success<String>>(repository.fingerprint(source)).value
        val request = ImportRequest(source, digest, ImportMode.REPLACE_BACKUP,
            ImportDecisions(selectedTasks = emptySet(), backupSafety = BackupSafety.EXPLICITLY_DECLINED))
        val plan = assertIs<PreparedImport.Ready>(assertIs<TransferOutcome.Success<PreparedImport>>(repository.prepareImport(request)).value).plan
        assertIs<TransferOutcome.Success<ImportResult>>(ApplyImportUseCase(repository, reconciler())(plan))
        val exported = assertIs<TransferOutcome.Success<TransferSnapshot>>(repository.readSnapshot(ExportSelection.FullBackup)).value
        assertEquals(exact, exported.reminderState.required().single().shownAt)
    }

}
