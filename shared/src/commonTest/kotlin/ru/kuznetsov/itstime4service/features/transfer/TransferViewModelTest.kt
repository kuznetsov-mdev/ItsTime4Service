package ru.kuznetsov.itstime4service.features.transfer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.data.transfer.TransferCodecImpl
import ru.kuznetsov.itstime4service.data.transfer.TransferDocumentCodecImpl
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.transfer.*
import ru.kuznetsov.itstime4service.features.TestApp
import ru.kuznetsov.itstime4service.features.ViewModelTestSetup
import ru.kuznetsov.itstime4service.features.watch
import ru.kuznetsov.itstime4service.ui.transfer.*
import kotlin.test.*

/** Exercises transfer session ownership against the real graph/database and synthetic file IO. */
class TransferViewModelTest {
    private val app = TestApp()
    private val files = FakeTransferFiles()
    private val documents = TransferDocumentCodecImpl(TransferCodecImpl(), transferFileCheckCipher())

    @BeforeTest fun start() = ViewModelTestSetup.start()
    @AfterTest fun close() { app.close(); ViewModelTestSetup.finish() }

    private suspend fun TestScope.open(entry: TransferEntry = TransferEntry.Settings): TransferViewModel {
        app.koin.declare<TransferFiles>(files)
        app.koin.declare<TransferCipher>(transferFileCheckCipher())
        val model = app.viewModel<TransferViewModel>(entry)
        model.start()
        watch(model.state) { !it.isLoading && !it.isBusy }
        return model
    }
    private suspend fun TransferViewModel.at(step: TransferStep) = state.first { !it.isBusy && it.step == step }

    @Test fun leavingExportFromCarKeepsTheExportScreenDuringNavigation() = runTest {
        val id = app.addCar("Test car", tasks = listOf(NewMaintenanceTask("Oil", 10000)))
        val model = open(TransferEntry.CarDetail(id))
        model.onStartExport(TransferExportPurpose.SHARE_SCHEDULE)
        model.onBack()
        val chooser = model.at(TransferStep.EXPORT_PURPOSE)

        model.onBack()
        assertEquals(chooser.copy(isClosed = true), model.state.value)
        model.onBack()
        assertEquals(chooser.copy(isClosed = true), model.state.value)
    }

    @Test fun sharingAfterEditingABackupResetsTheDraftAndExcludesPersonalData() = runTest {
        val id = app.addCar("Test car", tasks = listOf(NewMaintenanceTask("Oil", 10000, lastDoneKm = 30000)), vin = "TEST")
        val model = open()
        val tasks = app.car(id)!!.tasks.map { it.id }.toSet()
        model.onChooseCarExport()
        model.onStartExport(TransferExportPurpose.CAR_BACKUP)
        model.onDescription("Private backup draft")
        model.onExportAllTasks(false)
        model.onBack()
        model.onStartExport(TransferExportPurpose.SHARE_SCHEDULE)
        val export = model.at(TransferStep.EXPORT)
        assertFalse(export.isProtected)
        assertEquals(tasks, export.exportTaskIds)
        assertEquals("", export.description)
        assertTrue(export.exportGroups.isEmpty())
        model.onExportGroup(TransferGroup.LAST_SERVICE, true)
        model.onPrepareExport("", "")
        model.at(TransferStep.EXPORT_PREVIEW)
        model.onWriteExport(false)
        model.at(TransferStep.RESULT)
        val snapshot = assertIs<TransferOutcome.Success<TransferSnapshot>>(documents.decode(files.saved.single(), null)).value
        assertTrue(snapshot.includedGroups.isEmpty())
        assertEquals(Presence.Absent, snapshot.cars.single().mileageKm)
        assertEquals(Presence.Absent, snapshot.cars.single().vin)
        assertEquals(Presence.Absent, snapshot.cars.single().tasks.single().lastService)
    }

    @Test fun carBackupFileKeepsTheLastServiceAndHistoryOfTheChosenCar() = runTest {
        val date = LocalDate(2026, 10, 1)
        app.addCar("Other car")
        val id = app.addCar(
            "Test car",
            mileageKm = 44000,
            tasks = listOf(NewMaintenanceTask("Oil", 10000, lastDoneKm = 41000, intervalMonths = 12, lastDoneDate = date)),
        )
        app.addRecord(id, "Oil", date, mileageKm = 41000)
        val model = open(TransferEntry.CarDetail(id))
        model.onStartExport(TransferExportPurpose.CAR_BACKUP)
        val export = model.at(TransferStep.EXPORT)
        assertEquals(id, export.exportCarId)
        assertTrue(export.isProtected)
        assertEquals(setOf(TransferGroup.MILEAGE, TransferGroup.VIN, TransferGroup.PLATE,
            TransferGroup.LAST_SERVICE, TransferGroup.HISTORY, TransferGroup.HISTORY_DETAILS), export.exportGroups)
        model.onPrepareExport("Synthetic backup password", "Synthetic backup password")
        model.at(TransferStep.EXPORT_PREVIEW)
        model.onBack()
        assertEquals(export, model.at(TransferStep.EXPORT))
        model.onPrepareExport("Synthetic backup password", "Synthetic backup password")
        model.at(TransferStep.EXPORT_PREVIEW)
        model.onWriteExport(false)
        model.at(TransferStep.RESULT)
        val snapshot = SecretBytes.fromPassword("Synthetic backup password").use { secret ->
            assertIs<TransferOutcome.Success<TransferSnapshot>>(documents.decode(files.saved.single(), secret)).value
        }
        assertEquals(TransferKind.CAR, snapshot.kind)
        val car = snapshot.cars.single()
        assertEquals("Test car", car.brand)
        assertEquals(Presence.Present(44000), car.mileageKm)
        assertEquals(Presence.Present(LastService(41000, date)), car.tasks.single().lastService)
        assertEquals(1, assertIs<Presence.Present<List<TransferHistory>>>(car.history).value.size)
    }

    @Test fun personalGroupsEnableProtectionAndHistoryDetailsDependOnHistory() = runTest {
        val id = app.addCar("Test car")
        val model = open()
        model.onChooseCarExport()
        model.onStartExport(TransferExportPurpose.CAR_BACKUP)
        model.onExportCarSelect(id)
        model.onExportGroup(TransferGroup.HISTORY, false)
        assertFalse(TransferGroup.HISTORY_DETAILS in model.state.value.exportGroups)
        model.onExportGroup(TransferGroup.MILEAGE, true)
        model.onProtectionChange(false)
        assertTrue(model.state.value.isConfirmingPlaintext)
        model.onConfirmPlaintext(false)
        assertTrue(model.state.value.isProtected)
    }

    @Test fun encryptedFileShowsNoPreviewUntilCorrectPassword() = runTest {
        val model = open()
        files.input = TransferFileCheckFixtures.protected()
        model.onStartImport(); model.onPickFile()
        model.at(TransferStep.UNLOCK)
        assertNull(model.state.value.incoming)
        model.onUnlock("Synthetic wrong password")
        model.state.first { !it.isBusy && it.failure == TransferFailure.AuthenticationFailed }
        assertNull(model.state.value.incoming)
        assertTrue(app.summaries().isEmpty())
        model.onUnlock("Synthetic-file-check")
        val state = model.at(TransferStep.IMPORT_SETUP)
        assertEquals("Synthetic car", state.incoming!!.cars.single().brand)
        assertEquals(1, files.released)
    }

    @Test fun changingDestinationResetsWorkChoicesAndContinueUsesTheNewDestination() = runTest {
        val first = app.addCar("First", tasks = listOf(NewMaintenanceTask("Oil", 10000)))
        val second = app.addCar("Synthetic car")
        val model = open()
        model.onStartImport(); model.onPickFile(); model.at(TransferStep.IMPORT_SETUP)
        val source = model.state.value.incoming!!.cars.single()
        val ref = EntityRef(source.id, source.tasks.single().id)
        model.onPrepareImport()
        assertEquals(TransferFormProblem.TARGET_REQUIRED, model.state.value.problem)
        model.onTargetDraft(source.id, ImportTargetDraft(false, first))
        model.onTaskChoice(ref, TaskChoice.UPDATE_PARAMETERS)
        model.onTaskTarget(ref, app.car(first)!!.tasks.single().id)
        model.onTargetDraft(source.id, ImportTargetDraft(false, second))
        assertTrue(model.state.value.decisions.taskTargets.isEmpty())
        assertTrue(model.state.value.decisions.taskChoices.isEmpty())
        assertTrue(model.state.value.decisions.carTargets.isEmpty())
        model.onPrepareImport()
        val preview = model.state.first { !it.isBusy }
        assertEquals(TransferStep.IMPORT_PREVIEW, preview.step)
        assertEquals(TargetCar.Existing(second), preview.decisions.carTargets[source.id])
    }

    @Test fun choosingAnAmbiguousWorkKeepsResolutionOnSetupAndPreviewsTheChosenTarget() = runTest {
        val car = app.addCar("Synthetic car", tasks = listOf(
            NewMaintenanceTask("Oil", 8000),
            NewMaintenanceTask("Oil", 16000),
        ))
        val target = app.car(car)!!.tasks.single { it.intervalKm == 16000 }
        val model = open()
        model.onPickFile()
        val source = model.at(TransferStep.IMPORT_SETUP).incoming!!.cars.single()
        val ref = EntityRef(source.id, source.tasks.single().id)
        model.onTargetDraft(source.id, ImportTargetDraft(existingId = car))
        model.onTaskChoice(ref, TaskChoice.UPDATE_PARAMETERS)
        model.onPrepareImport()
        val unresolved = model.state.first { !it.isBusy }
        assertEquals(TransferStep.IMPORT_SETUP, unresolved.step)
        assertEquals(ImportDecisionReason.TASK_TARGET, unresolved.pending.single().reason)

        model.onTaskTarget(ref, target.id)
        model.onPrepareImport()
        val preview = model.state.first { !it.isBusy }
        assertEquals(TransferStep.IMPORT_PREVIEW, preview.step)
        assertEquals(target.id, preview.importPreview!!.taskTargets.getValue(ref).id)
        assertTrue(preview.pending.isEmpty())
    }

    @Test fun skippingAllMatchesOnAnExistingCarAppliesWithoutChangingItsData() = runTest {
        val id = app.addCar("Synthetic car", tasks = listOf(NewMaintenanceTask("Oil", 8000, lastDoneKm = 30000)))
        val original = app.car(id)
        val model = open()
        model.onPickFile()
        val source = model.at(TransferStep.IMPORT_SETUP).incoming!!.cars.single()
        model.onTargetDraft(source.id, ImportTargetDraft(existingId = id))
        model.onTaskChoice(EntityRef(source.id, source.tasks.single().id), TaskChoice.SKIP)
        model.onPrepareImport()
        val preview = model.at(TransferStep.IMPORT_PREVIEW)
        assertEquals(TargetCar.Existing(id), preview.decisions.carTargets[source.id])
        assertEquals(1, preview.importSummary!!.skipped)
        model.onApplyImport()
        model.at(TransferStep.RESULT)
        assertEquals(id, app.summaries().single().id)
        assertEquals(original, app.car(id))
    }

    @Test fun aConfirmedPreviewAppliesOnceAndBackFromSuccessEndsTheSession() = runTest {
        val model = open()
        model.onStartImport(); model.onPickFile(); model.at(TransferStep.IMPORT_SETUP)
        val source = model.state.value.incoming!!.cars.single()
        model.onTargetDraft(source.id, ImportTargetDraft(isNew = true, brand = "Imported test car", mileage = "12000"))
        model.onPrepareImport(); model.at(TransferStep.IMPORT_PREVIEW)
        model.onApplyImport(); model.onApplyImport()
        model.at(TransferStep.RESULT)
        assertEquals(1, app.summaries().size)
        model.onBack()
        assertTrue(model.state.value.isClosed)
        assertNull(model.state.value.incoming)
        model.onApplyImport()
        assertEquals(1, app.summaries().size)
    }

    @Test fun anAuthenticatedFileCanBeRetriedAfterATemporaryDatabaseReadFailure() = runTest {
        val model = open()
        files.input = TransferFileCheckFixtures.protected()
        model.onPickFile(); model.at(TransferStep.UNLOCK)
        app.hideTable("cars")
        model.onUnlock("Synthetic-file-check")
        model.state.first { !it.isBusy && it.failure == TransferFailure.DatabaseReadFailed }
        assertNull(model.state.value.incoming)
        app.restoreTable("cars")
        model.onUnlock("Synthetic-file-check")
        val retried = model.state.first { !it.isBusy }
        assertEquals(TransferStep.IMPORT_SETUP, retried.step)
        assertEquals("Synthetic car", retried.incoming!!.cars.single().brand)
        assertEquals(1, files.released)
    }

    @Test fun changedDatabaseInvalidatesPreviewWithoutApplyingIt() = runTest {
        val model = open()
        model.onStartImport(); model.onPickFile(); model.at(TransferStep.IMPORT_SETUP)
        val source = model.state.value.incoming!!.cars.single()
        model.onTargetDraft(source.id, ImportTargetDraft(isNew = true, brand = "Imported", mileage = "12000"))
        model.onPrepareImport(); model.at(TransferStep.IMPORT_PREVIEW)
        app.addCar("Created elsewhere")
        model.onApplyImport()
        val state = model.state.first { !it.isBusy && it.failure == TransferFailure.StalePlan }
        assertEquals(TransferStep.IMPORT_SETUP, state.step)
        assertTrue(state.decisions.carTargets.isEmpty())
        assertEquals(1, app.summaries().size)
    }

    @Test fun cancelledSafetySaveNeverEnablesReplacement() = runTest {
        app.addCar("Existing")
        val repository = app.koin.get<TransferRepository>()
        val snapshot = assertIs<TransferOutcome.Success<TransferSnapshot>>(repository.readSnapshot(ExportSelection.FullBackup)).value
        files.input = assertIs<TransferOutcome.Success<ByteArray>>(documents.encode(snapshot, null)).value
        val model = open()
        model.onStartImport(); model.onPickFile(); model.at(TransferStep.IMPORT_SETUP)
        model.onImportMode(ImportMode.REPLACE_BACKUP)
        files.cancelSave = true
        model.onSaveSafety("Synthetic safety password", "Synthetic safety password")
        model.state.first { !it.isBusy }
        assertEquals(BackupSafety.NOT_REQUIRED, model.state.value.decisions.backupSafety)
        model.onApplyImport()
        assertNull(model.state.value.importResult)
        assertEquals(1, app.summaries().size)
    }

    @Test fun encryptedMultiCarBackupValidatesAndConfirmsAllDestinationsTogether() = runTest {
        val first = app.addCar("First synthetic", tasks = listOf(NewMaintenanceTask("Oil", 10000)))
        app.addCar("Second synthetic")
        val original = assertIs<TransferOutcome.Success<TransferSnapshot>>(
            app.koin.get<TransferRepository>().readSnapshot(ExportSelection.FullBackup)).value
        SecretBytes.fromPassword("Synthetic multi password").use { secret ->
            files.input = assertIs<TransferOutcome.Success<ByteArray>>(documents.encode(original, secret)).value
        }
        val model = open()
        model.onPickFile(); model.at(TransferStep.UNLOCK)
        model.onUnlock("Synthetic multi password"); model.at(TransferStep.IMPORT_SETUP)
        val source = model.state.value.incoming!!.cars
        source.forEach { model.onTargetDraft(it.id, ImportTargetDraft(existingId = first)) }
        model.onPrepareImport()
        assertEquals(TransferFormProblem.DUPLICATE_CAR_TARGET, model.state.value.problem)
        assertTrue(model.state.value.decisions.carTargets.isEmpty())
        source.forEachIndexed { index, car ->
            model.onTargetDraft(car.id, ImportTargetDraft(isNew = true, brand = "Copy $index", mileage = "12000"))
        }
        model.onPrepareImport()
        assertEquals(source.map { it.id }.toSet(), model.state.value.decisions.carTargets.keys)
        for (round in 0..3) {
            val current = model.state.first { !it.isBusy }
            assertNull(current.failure)
            if (current.step == TransferStep.IMPORT_PREVIEW) break
            assertEquals(TransferStep.IMPORT_DECISIONS, current.step)
            current.pending.filter { it.reason == ImportDecisionReason.IDENTITY_COLLISION }.forEach { pending ->
                pending.affected.forEach { model.onTaskChoice(it, TaskChoice.ADD_SEPARATE) }
            }
            model.onPrepareImport()
        }
        model.state.first { !it.isBusy }
        assertEquals(TransferStep.IMPORT_PREVIEW, model.state.value.step)
        assertEquals(2, model.state.value.importSummary!!.carsAdded)
        model.onApplyImport(); model.at(TransferStep.RESULT)
        assertEquals(4, app.summaries().size)
    }

    @Test fun aDisplayedDefaultSkipResolvesIdentityChoicesWithoutRequiringARadioTap() = runTest {
        app.addCar("Original", tasks = listOf(NewMaintenanceTask("Oil", 10000)))
        val original = assertIs<TransferOutcome.Success<TransferSnapshot>>(
            app.koin.get<TransferRepository>().readSnapshot(ExportSelection.FullBackup)).value
        files.input = assertIs<TransferOutcome.Success<ByteArray>>(documents.encode(original, null)).value
        val model = open()
        model.onPickFile(); model.at(TransferStep.IMPORT_SETUP)
        val source = model.state.value.incoming!!.cars.single()
        model.onTargetDraft(source.id, ImportTargetDraft(isNew = true, brand = "Copy", mileage = "100000"))
        model.onPrepareImport()
        val pending = model.at(TransferStep.IMPORT_DECISIONS)
        assertEquals(ImportDecisionReason.IDENTITY_COLLISION, pending.pending.single().reason)
        assertNull(pending.decisions.taskChoices[EntityRef(source.id, source.tasks.single().id)])
        model.onPrepareImport(); model.at(TransferStep.IMPORT_PREVIEW)
        assertEquals(0, model.state.value.importSummary!!.tasksAdded)
    }

    @Test fun confirmingUnchangedPrefilledMilesPreservesTheExactIncomingKilometers() = runTest {
        app.addCar("Synthetic mileage", mileageKm = 30400)
        app.changeSettings { it.copy(distanceUnit = ru.kuznetsov.itstime4service.domain.model.DistanceUnit.MILES) }
        val snapshot = assertIs<TransferOutcome.Success<TransferSnapshot>>(
            app.koin.get<TransferRepository>().readSnapshot(ExportSelection.FullBackup)).value
        files.input = assertIs<TransferOutcome.Success<ByteArray>>(documents.encode(snapshot, null)).value
        val model = open()
        model.onPickFile(); model.at(TransferStep.IMPORT_SETUP)
        val source = model.state.value.incoming!!.cars.single()
        val draft = model.state.value.targetDrafts.getValue(source.id)
        assertEquals("18890", draft.mileage)
        model.onTargetDraft(source.id, draft.copy(isNew = true))
        model.onPrepareImport()
        assertEquals(30400, assertIs<TargetCar.New>(model.state.value.decisions.carTargets[source.id]).confirmedMileageKm)
    }

    @Test fun failedNotificationSetupRetriesWithoutRepeatingDatabaseImport() = runTest {
        var failing = true
        var schedules = 0
        app.koin.declare<ru.kuznetsov.itstime4service.domain.repository.ReminderScheduler>(
            object : ru.kuznetsov.itstime4service.domain.repository.ReminderScheduler {
                override suspend fun schedule(reminders: List<ru.kuznetsov.itstime4service.domain.model.PlannedReminder>,
                    distanceUnit: ru.kuznetsov.itstime4service.domain.model.DistanceUnit) {
                    schedules++
                    if (failing) error("Synthetic scheduler failure")
                }
            })
        val model = open()
        model.onPickFile(); model.at(TransferStep.IMPORT_SETUP)
        val source = model.state.value.incoming!!.cars.single()
        model.onTargetDraft(source.id, ImportTargetDraft(isNew = true, brand = "Imported", mileage = "12000"))
        model.onPrepareImport(); model.at(TransferStep.IMPORT_PREVIEW)
        model.onApplyImport(); model.at(TransferStep.RESULT)
        assertEquals(NotificationState.RETRY_REQUIRED, model.state.value.importResult!!.notifications)
        val carId = app.summaries().single().id
        failing = false
        model.onRetryNotifications(); model.at(TransferStep.RESULT)
        assertEquals(NotificationState.APPLIED, model.state.value.importResult!!.notifications)
        assertEquals(carId, app.summaries().single().id)
        assertEquals(2, schedules)
    }

    @Test fun backDuringPostCommitNotificationSyncPreservesImportResult() = runTest {
        val scheduling = CompletableDeferred<Unit>()
        val finishScheduling = CompletableDeferred<Unit>()
        app.koin.declare<ru.kuznetsov.itstime4service.domain.repository.ReminderScheduler>(
            object : ru.kuznetsov.itstime4service.domain.repository.ReminderScheduler {
                override suspend fun schedule(reminders: List<ru.kuznetsov.itstime4service.domain.model.PlannedReminder>,
                    distanceUnit: ru.kuznetsov.itstime4service.domain.model.DistanceUnit) {
                    scheduling.complete(Unit)
                    finishScheduling.await()
                }
            })
        val model = open(TransferEntry.CarForm)
        model.onPickFile(); model.at(TransferStep.IMPORT_SETUP)
        val source = model.state.value.incoming!!.cars.single()
        model.onTargetDraft(source.id, ImportTargetDraft(isNew = true, brand = "Imported", mileage = "12000"))
        model.onPrepareImport(); model.at(TransferStep.IMPORT_PREVIEW)

        model.onApplyImport()
        scheduling.await()
        assertEquals("Imported", app.summaries().single().brand)
        model.onBack()
        assertEquals(TransferStep.IMPORT_PREVIEW, model.state.value.step)
        assertTrue(model.state.value.isBusy)

        finishScheduling.complete(Unit)
        assertEquals(NotificationState.APPLIED, model.at(TransferStep.RESULT).importResult?.notifications)
        assertEquals(1, app.summaries().size)
        model.onBack()
        assertTrue(model.state.value.isClosed)
    }

    @Test fun cancellingReadReleasesTheHandleAndCannotPublishLateData() = runTest {
        files.readGate = CompletableDeferred()
        val model = open()
        model.onStartImport(); model.onPickFile()
        files.readStarted.await()
        model.onBack()
        files.readGate!!.complete(Unit)
        model.state.first { !it.isBusy }
        assertNull(model.state.value.incoming)
        assertEquals(1, files.released)
    }

    @Test fun databaseChangesDuringSafetySaveCannotBeReplaced() = runTest {
        app.addCar("Before safety save")
        val snapshot = assertIs<TransferOutcome.Success<TransferSnapshot>>(
            app.koin.get<TransferRepository>().readSnapshot(ExportSelection.FullBackup)).value
        files.input = assertIs<TransferOutcome.Success<ByteArray>>(documents.encode(snapshot, null)).value
        val model = open()
        model.onPickFile(); model.at(TransferStep.IMPORT_SETUP)
        model.onImportMode(ImportMode.REPLACE_BACKUP)
        files.beforeSave = { app.addCar("Added while saving") }
        model.onSaveSafety("Synthetic safety password", "Synthetic safety password")
        model.at(TransferStep.IMPORT_PREVIEW)
        model.onApplyImport()
        model.state.first { !it.isBusy && it.failure == TransferFailure.StalePlan }
        assertEquals(2, app.summaries().size)
        assertNull(model.state.value.importResult)
    }
}

internal class FakeTransferFiles : TransferFiles {
    var input = TransferFileCheckFixtures.plain()
    var cancelPick = false
    var cancelSave = false
    var beforeSave: suspend () -> Unit = {}
    var readGate: CompletableDeferred<Unit>? = null
    val readStarted = CompletableDeferred<Unit>()
    val saved = mutableListOf<ByteArray>()
    var released = 0
    override suspend fun pick(): TransferFileResult<PickedFileHandle> =
        if (cancelPick) TransferFileResult.Cancelled else TransferFileResult.Success(PickedFileHandle("test", "fixture"))
    override suspend fun read(handle: PickedFileHandle): TransferFileResult<ByteArray> {
        readStarted.complete(Unit); readGate?.await()
        return TransferFileResult.Success(input.copyOf())
    }
    override suspend fun save(bytes: ByteArray, fileName: String): TransferFileResult<SavedFile> {
        if (cancelSave) return TransferFileResult.Cancelled
        beforeSave()
        saved += bytes.copyOf()
        return TransferFileResult.Success(SavedFile)
    }
    override suspend fun share(bytes: ByteArray, fileName: String): TransferFileResult<SharedFile> {
        saved += bytes.copyOf(); return TransferFileResult.Success(SharedFile)
    }
    override suspend fun release(handle: PickedFileHandle) { released++ }
}
