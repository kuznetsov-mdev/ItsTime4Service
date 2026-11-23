package ru.kuznetsov.itstime4service.domain.transfer

import ru.kuznetsov.itstime4service.data.transfer.TransferFixtures
import ru.kuznetsov.itstime4service.domain.model.Settings
import kotlin.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrepareImportTest {
    private val source = TransferFixtures.car()
    private val ref = EntityRef("c1", "t1")
    private val empty = ImportState(emptyList(), Settings.DEFAULT, emptyList(), emptyList())
    private var next = 500

    private fun prepare(
        decisions: ImportDecisions,
        state: ImportState = empty,
        snapshot: TransferSnapshot = source,
        mode: ImportMode = ImportMode.INTO_CAR,
    ): ImportPreparation {
        next = 500
        return ImportPlanner({ TransferFixtures.id(next++) }, { "bound:$it" }).prepare(snapshot, "digest", mode, decisions, state)
    }

    @Test
    fun sameBrandDoesNotMergeSilently() {
        val local = TransferFixtures.backup().cars.single().copy(brand = source.cars.single().brand)
        val result = prepare(ImportDecisions(selectedTasks = setOf(ref)), empty.copy(cars = listOf(local)))
        assertEquals(ImportDecisionReason.CAR_TARGET, assertIs<ImportPreparation.NeedsDecisions>(result).decisions.single().reason)
    }

    @Test
    fun newTaskHasUnknownServiceAndRecipientsWindow() {
        val result = prepare(ImportDecisions(mapOf("c1" to TargetCar.New("Confirmed", 90000)), setOf(ref)),
            empty.copy(settings = Settings.DEFAULT.copy(defaultReminderWindowKm = 1234)))
        val changes = assertIs<ImportPreparation.Ready>(result).changes
        val car = changes.cars.single()
        assertEquals(Presence.Present(90000), car.mileageKm)
        assertEquals(Presence.Present(LastService(null, null)), car.tasks.single().lastService)
        assertEquals(Presence.Present(1234), car.tasks.single().reminderWindowKm)
        assertNull((car.vin as Presence.Present).value)
    }

    @Test
    fun replacementRequiresSafetyAndIgnoresMergeChoices() {
        val snapshot = TransferFixtures.backup()
        val needs = prepare(ImportDecisions(selectedTasks = emptySet()), snapshot = snapshot, mode = ImportMode.REPLACE_BACKUP)
        assertEquals(ImportDecisionReason.BACKUP_SAFETY, assertIs<ImportPreparation.NeedsDecisions>(needs).decisions.single().reason)
        val ready = prepare(ImportDecisions(selectedTasks = emptySet(), backupSafety = BackupSafety.EXPLICITLY_DECLINED),
            snapshot = snapshot, mode = ImportMode.REPLACE_BACKUP)
        assertEquals(1, assertIs<ImportPreparation.Ready>(ready).changes.cars.single().tasks.size)
        assertTrue(ready.summary.rollbackDisabled.isNotEmpty())
    }
    private fun local() = TransferFixtures.backup().cars.single().copy(
        brand = source.cars.single().brand,
        maintenanceDescription = source.cars.single().maintenanceDescription,
        vin = Presence.Present(null), plate = Presence.Present(null),
        preferences = Presence.Present(CarPreferences(false, false)),
        history = Presence.Present(emptyList()),
    )

    private fun choices(local: TransferCar = local(), snapshot: TransferSnapshot = source) = ImportDecisions(
        carTargets = snapshot.cars.associate { it.id to TargetCar.Existing(local.id) },
        selectedTasks = snapshot.cars.flatMap { car -> car.tasks.map { EntityRef(car.id, it.id) } }.toSet(),
    )

    private fun settle(choices: ImportDecisions, state: ImportState, snapshot: TransferSnapshot = source,
        mode: ImportMode = ImportMode.INTO_CAR, take: Boolean = false): ImportPreparation.Ready {
        var current = choices
        repeat(6) {
            when (val prepared = prepare(current, state, snapshot, mode)) {
                is ImportPreparation.Ready -> return prepared
                is ImportPreparation.NeedsDecisions -> {
                    val value = if (take) PersonalChoice.TAKE_INCOMING else PersonalChoice.KEEP_LOCAL
                    current = current.copy(
                        personalChoices = current.personalChoices + prepared.decisions.filter { it.reason == ImportDecisionReason.PERSONAL_FIELD }.associate { it.id to value },
                        relatedChoices = current.relatedChoices + prepared.decisions.filter { it.reason == ImportDecisionReason.RELATED_SERVICE_HISTORY }.associate { it.id to value },
                        soldChoices = current.soldChoices + prepared.decisions.filter { it.reason == ImportDecisionReason.SOLD_STATE }.associate { it.id to value },
                    )
                }
            }
        }
        fail("Unexpected pending decision")
    }

    @Test
    fun multipleSameNameTasksRequireConcreteUpdateTarget() {
        val original = local()
        val local = original.copy(tasks = original.tasks + original.tasks.single().copy(id = TransferFixtures.id(12), intervalKm = 20000))
        val result = prepare(choices(local).copy(taskChoices = mapOf(ref to TaskChoice.UPDATE_PARAMETERS)), empty.copy(cars = listOf(local)))
        val pending = assertIs<ImportPreparation.NeedsDecisions>(result).decisions.single()
        assertEquals(ImportDecisionReason.TASK_TARGET, pending.reason)
        assertEquals(listOf(10000, 20000), pending.candidates.map { it.intervalKm })
        val selected = settle(choices(local).copy(taskChoices = mapOf(ref to TaskChoice.UPDATE_PARAMETERS),
            taskTargets = mapOf(ref to TransferFixtures.id(12))), empty.copy(cars = listOf(local)))
        assertEquals(listOf(10000, 10000), selected.changes.cars.single().tasks.map { it.intervalKm })
        assertEquals(local.tasks.first(), selected.changes.cars.single().tasks.first())
    }

    @Test
    fun staleCrossCarAndMissingTargetsRequireNewChoice() {
        val local = local()
        listOf(TransferFixtures.id(99), "missing").forEach { id ->
            val result = prepare(choices(local).copy(taskChoices = mapOf(ref to TaskChoice.UPDATE_PARAMETERS), taskTargets = mapOf(ref to id)),
                empty.copy(cars = listOf(local)))
            assertEquals(ImportDecisionReason.TASK_TARGET, assertIs<ImportPreparation.NeedsDecisions>(result).decisions.single().reason)
        }
    }

    @Test
    fun twoIncomingTasksCannotUpdateOneLocalTask() {
        val local = local()
        val second = EntityRef("c1", "t2")
        val snapshot = source.copy(cars = source.cars.map { it.copy(tasks = it.tasks + it.tasks.single().copy(id = "t2")) })
        val result = prepare(choices(local, snapshot).copy(taskChoices = mapOf(ref to TaskChoice.UPDATE_PARAMETERS, second to TaskChoice.UPDATE_PARAMETERS),
            taskTargets = mapOf(ref to local.tasks.single().id, second to local.tasks.single().id)), empty.copy(cars = listOf(local)), snapshot)
        assertTrue(assertIs<ImportPreparation.NeedsDecisions>(result).decisions.any { it.reason == ImportDecisionReason.DUPLICATE_TARGET })
    }

    @Test
    fun skippingOneMatchDoesNotUndoAnotherMatchUpdate() {
        val local = local()
        val incoming = source.cars.single()
        val update = incoming.tasks.single().copy(intervalKm = 25000)
        val skip = update.copy(id = "t2", intervalKm = 30000)
        for (tasks in listOf(listOf(update, skip), listOf(skip, update))) {
            val snapshot = source.copy(cars = listOf(incoming.copy(tasks = tasks)))
            val decisions = choices(local, snapshot).copy(
                taskChoices = mapOf(ref to TaskChoice.UPDATE_PARAMETERS, EntityRef(incoming.id, skip.id) to TaskChoice.SKIP),
            )
            val ready = settle(decisions, empty.copy(cars = listOf(local)), snapshot)
            assertEquals(25000, ready.changes.cars.single().tasks.single().intervalKm)
            assertEquals(1, ready.summary.skipped)
        }
    }

    @Test
    fun skipParametersCanIndependentlyAcceptLastService() {
        val local = local()
        val snapshot = source.copy(includedGroups = setOf(TransferGroup.LAST_SERVICE), cars = source.cars.map { car ->
            car.copy(tasks = car.tasks.map { it.copy(intervalKm = 25000, lastService = Presence.Present(LastService(82000, LocalDate(2026, 10, 2)))) })
        })
        val result = settle(choices(local, snapshot), empty.copy(cars = listOf(local)), snapshot, take = true)
        val task = result.changes.cars.single().tasks.single()
        assertEquals(10000, task.intervalKm)
        assertEquals(Presence.Present(LastService(82000, LocalDate(2026, 10, 2))), task.lastService)
        assertEquals(Presence.Present(emptyList()), result.changes.cars.single().history)
    }

    @Test
    fun absentLastServiceDoesNotOverwritePartialKnownFacts() {
        val local = local()
        val result = settle(choices(local).copy(taskChoices = mapOf(ref to TaskChoice.UPDATE_PARAMETERS)), empty.copy(cars = listOf(local)))
        assertEquals(local.tasks.single().lastService, result.changes.cars.single().tasks.single().lastService)
    }

    @Test
    fun nullIncomingVinRequiresChoiceAndCanClearOnlyWhenAccepted() {
        val local = local().copy(vin = Presence.Present("TEST-VIN"))
        val snapshot = source.copy(includedGroups = setOf(TransferGroup.VIN), cars = source.cars.map { it.copy(vin = Presence.Present(null)) })
        val pending = prepare(choices(local, snapshot), empty.copy(cars = listOf(local)), snapshot)
        assertEquals("vin", assertIs<ImportPreparation.NeedsDecisions>(pending).decisions.single().field)
        assertEquals(Presence.Present("TEST-VIN"), settle(choices(local, snapshot), empty.copy(cars = listOf(local)), snapshot).changes.cars.single().vin)
        assertEquals(Presence.Present(null), settle(choices(local, snapshot), empty.copy(cars = listOf(local)), snapshot, take = true).changes.cars.single().vin)
    }

    @Test
    fun soldStateListsRemovedTasksBeforeApply() {
        val local = local()
        val backup = TransferFixtures.backup().copy(cars = listOf(local.copy(sold = Presence.Present(true), tasks = emptyList())))
        val pending = prepare(choices(local, backup), empty.copy(cars = listOf(local)), backup, ImportMode.MERGE_BACKUP)
        val sold = assertIs<ImportPreparation.NeedsDecisions>(pending).decisions.single()
        assertEquals(ImportDecisionReason.SOLD_STATE, sold.reason)
        assertEquals(local.tasks, sold.candidates)
        val ready = settle(choices(local, backup), empty.copy(cars = listOf(local)), backup, ImportMode.MERGE_BACKUP, take = true)
        assertTrue(ready.changes.cars.single().tasks.isEmpty())
        assertEquals(1, ready.summary.tasksRemoved)
    }

    @Test
    fun conflictedUndoIsDisabledWithoutLosingHistory() {
        val day = LocalDate(2026, 10, 1)
        val original = TransferFixtures.backup().cars.single()
        val local = original.copy(tasks = original.tasks.map { it.copy(lastService = Presence.Present(LastService(80000, day))) })
        val incoming = local.copy(tasks = local.tasks.map { it.copy(lastService = Presence.Present(LastService(82000, day))) },
            history = Presence.Present(local.history.required().map { if (it.id == TransferFixtures.id(101)) it.copy(mileageKm = 82000) else it }))
        val snapshot = TransferFixtures.backup().copy(cars = listOf(incoming))
        val decision = choices(local, snapshot)
        val pending = assertIs<ImportPreparation.NeedsDecisions>(prepare(decision, empty.copy(cars = listOf(local)), snapshot, ImportMode.MERGE_BACKUP))
        assertTrue(pending.decisions.any { it.reason == ImportDecisionReason.RELATED_SERVICE_HISTORY })
        listOf(false, true).forEach { take ->
            val ready = settle(decision, empty.copy(cars = listOf(local)), snapshot, ImportMode.MERGE_BACKUP, take)
            val car = ready.changes.cars.single()
            assertEquals(2, car.history.required().size)
            assertTrue(car.history.required().all { it.rollback.valueOrNull() == null })
            assertEquals(if (take) 82000 else 80000, car.tasks.single().lastService.required().mileageKm)
            assertEquals(if (take) 82000 else 80000, car.history.required().first().mileageKm)
            assertTrue(ready.summary.rollbackDisabled.isNotEmpty() || take)
        }
    }

    @Test
    fun everyBackupCarNeedsOwnMappingAndDuplicateTargetsAreRejected() {
        val local = local()
        val original = TransferFixtures.backup()
        val other = original.cars.single().copy(id = TransferFixtures.id(2), tasks = emptyList(), history = Presence.Present(emptyList()))
        val snapshot = original.copy(cars = original.cars + other)
        val pending = prepare(choices(local, original), empty.copy(cars = listOf(local)), snapshot, ImportMode.MERGE_BACKUP)
        assertTrue(assertIs<ImportPreparation.NeedsDecisions>(pending).decisions.any { it.reason == ImportDecisionReason.CAR_TARGET })
        val duplicate = prepare(choices(local, snapshot), empty.copy(cars = listOf(local)), snapshot, ImportMode.MERGE_BACKUP)
        assertTrue(assertIs<ImportPreparation.NeedsDecisions>(duplicate).decisions.all { it.reason == ImportDecisionReason.CAR_TARGET })
    }

    @Test
    fun settingsRestoreUsesMappedTargetsInBothBackupModes() {
        val original = TransferFixtures.backup()
        val chosen = Settings.DEFAULT.copy(lastOpenedCarId = TransferFixtures.id(1), defaultCarId = TransferFixtures.id(1), openDefaultCar = true)
        val snapshot = original.copy(settings = Presence.Present(chosen))
        val local = local().copy(id = TransferFixtures.id(9))
        val ready = settle(choices(local, snapshot).copy(restoreSettings = true), empty.copy(cars = listOf(local)), snapshot, ImportMode.MERGE_BACKUP)
        assertEquals(local.id, ready.changes.settings.defaultCarId)
        assertEquals(local.id, ready.changes.settings.lastOpenedCarId)
        assertTrue(ready.changes.settings.openDefaultCar)
        val replacement = assertIs<ImportPreparation.Ready>(prepare(ImportDecisions(selectedTasks = emptySet(), restoreSettings = true,
            backupSafety = BackupSafety.SAVED), snapshot = snapshot, mode = ImportMode.REPLACE_BACKUP))
        assertEquals(chosen, replacement.changes.settings)
    }

    @Test
    fun retainedSettingsDropMissingAndSoldStartupTargets() {
        val current = Settings.DEFAULT.copy(lastOpenedCarId = TransferFixtures.id(8), defaultCarId = TransferFixtures.id(9), openDefaultCar = true)
        val snapshot = TransferFixtures.backup().copy(cars = emptyList(), reminderState = Presence.Present(emptyList()))
        val result = assertIs<ImportPreparation.Ready>(prepare(ImportDecisions(selectedTasks = emptySet(), backupSafety = BackupSafety.SAVED),
            empty.copy(settings = current), snapshot, ImportMode.REPLACE_BACKUP))
        assertNull(result.changes.settings.defaultCarId)
        assertNull(result.changes.settings.lastOpenedCarId)
        assertFalse(result.changes.settings.openDefaultCar)
        assertEquals(current.themeMode, result.changes.settings.themeMode)
    }

    @Test
    fun defaultSkipForTwoSameNameIncomingTasksDoesNotRequireUpdateMapping() {
        val local = local()
        val snapshot = source.copy(cars = source.cars.map { it.copy(tasks = it.tasks + it.tasks.single().copy(id = "t2")) })
        val result = assertIs<ImportPreparation.Ready>(prepare(choices(local, snapshot), empty.copy(cars = listOf(local)), snapshot))
        assertEquals(1, result.changes.cars.single().tasks.size)
        assertEquals(2, result.summary.skipped)
    }

    @Test
    fun ambiguousPersonalFactsCanBeDeclinedWithoutChoosingLocalWork() {
        val original = local()
        val local = original.copy(tasks = original.tasks + original.tasks.single().copy(id = TransferFixtures.id(12)))
        val snapshot = source.copy(includedGroups = setOf(TransferGroup.LAST_SERVICE), cars = source.cars.map { car ->
            car.copy(tasks = car.tasks.map { it.copy(lastService = Presence.Present(LastService(82000, null))) })
        })
        val result = settle(choices(local, snapshot), empty.copy(cars = listOf(local)), snapshot)
        assertEquals(local.tasks, result.changes.cars.single().tasks)
    }

    @Test
    fun separateHistoryConflictsCanChooseDifferentOutcomes() {
        val original = TransferFixtures.backup()
        val local = original.cars.single().copy(history = Presence.Present(original.cars.single().history.required().map {
            it.copy(taskId = null, rollback = Presence.Present(null))
        }))
        val incomingHistory = local.history.required().map { it.copy(mileageKm = it.mileageKm + 1) }
        val snapshot = original.copy(cars = listOf(local.copy(history = Presence.Present(incomingHistory))))
        val current = empty.copy(cars = listOf(local))
        val decisions = choices(local, snapshot)
        val pending = assertIs<ImportPreparation.NeedsDecisions>(prepare(decisions, current, snapshot, ImportMode.MERGE_BACKUP)).decisions
        assertEquals(2, pending.size)
        assertTrue(pending.all { it.field == "history" && it.affected.size == 1 })
        val changed = decisions.copy(personalChoices = mapOf(pending[0].id to PersonalChoice.TAKE_INCOMING, pending[1].id to PersonalChoice.KEEP_LOCAL))
        val ready = assertIs<ImportPreparation.Ready>(prepare(changed, current, snapshot, ImportMode.MERGE_BACKUP))
        assertEquals(listOf(80001, 80000), ready.changes.cars.single().history.required().map { it.mileageKm })
    }

    @Test
    fun historyCostChangeDoesNotGroupServiceOrDisableValidRollback() {
        val original = TransferFixtures.backup()
        val local = original.cars.single().copy(tasks = original.cars.single().tasks.map {
            it.copy(lastService = Presence.Present(LastService(80000, LocalDate(2026, 10, 1))))
        })
        val history = local.history.required().map { entry ->
            if (entry.id == TransferFixtures.id(101)) entry.copy(details = Presence.Present(entry.details.required().copy(costRub = 100))) else entry
        }
        val snapshot = original.copy(cars = listOf(local.copy(history = Presence.Present(history))))
        val pending = assertIs<ImportPreparation.NeedsDecisions>(prepare(choices(local, snapshot), empty.copy(cars = listOf(local)), snapshot, ImportMode.MERGE_BACKUP))
        assertTrue(pending.decisions.all { it.reason == ImportDecisionReason.PERSONAL_FIELD && it.field == "history" })
        val ready = settle(choices(local, snapshot), empty.copy(cars = listOf(local)), snapshot, ImportMode.MERGE_BACKUP, true)
        assertEquals(100, ready.changes.cars.single().history.required().first().details.required().costRub)
        assertNotNull(ready.changes.cars.single().history.required().first().rollback.valueOrNull())
        assertTrue(ready.summary.rollbackDisabled.isEmpty())
    }

    @Test
    fun unrelatedOldHistoryRemainsIndependentFromRejectedServiceComponent() {
        val original = TransferFixtures.backup()
        val local = original.cars.single().copy(tasks = original.cars.single().tasks.map {
            it.copy(lastService = Presence.Present(LastService(80000, LocalDate(2026, 10, 1))))
        })
        val anchor = local.history.required().first().copy(mileageKm = 82000)
        val older = anchor.copy(id = TransferFixtures.id(103), date = LocalDate(2026, 9, 1), mileageKm = 70000, rollback = Presence.Present(null))
        val incoming = local.copy(tasks = local.tasks.map { it.copy(lastService = Presence.Present(LastService(82000, LocalDate(2026, 10, 1)))) },
            history = Presence.Present(listOf(anchor, local.history.required().last(), older)))
        val snapshot = original.copy(cars = listOf(incoming))
        val current = empty.copy(cars = listOf(local))
        val initial = choices(local, snapshot)
        val pending = assertIs<ImportPreparation.NeedsDecisions>(prepare(initial, current, snapshot, ImportMode.MERGE_BACKUP)).decisions
        val related = pending.single { it.reason == ImportDecisionReason.RELATED_SERVICE_HISTORY }
        assertTrue(related.affected.none { it.entityId == older.id })
        val choices = initial.copy(relatedChoices = mapOf(related.id to PersonalChoice.KEEP_LOCAL),
            personalChoices = pending.filter { it.reason == ImportDecisionReason.PERSONAL_FIELD }.associate { it.id to PersonalChoice.TAKE_INCOMING })
        val ready = assertIs<ImportPreparation.Ready>(prepare(choices, current, snapshot, ImportMode.MERGE_BACKUP))
        val result = ready.changes.cars.single()
        assertEquals(80000, result.tasks.single().lastService.required().mileageKm)
        assertEquals(3, result.history.required().size)
        assertEquals(older.id, result.history.required().last().id)
    }

    @Test
    fun acceptingHistoryDoesNotIdentifyWorkByNameAlone() {
        val local = local()
        val snapshot = source.copy(includedGroups = setOf(TransferGroup.HISTORY), cars = source.cars.map { car ->
            car.copy(history = Presence.Present(listOf(TransferHistory("h1", "t1", "Oil", LocalDate(2026, 10, 1), 80000,
                Presence.Absent, Presence.Absent))))
        })
        val ready = settle(choices(local, snapshot), empty.copy(cars = listOf(local)), snapshot, take = true)
        val entry = ready.changes.cars.single().history.required().single()
        assertNotEquals(local.tasks.single().id, entry.taskId)
        assertEquals(local.tasks, ready.changes.cars.single().tasks)
    }

    @Test
    fun initialDecisionsSelectAllTasksWithoutChoosingTargets() {
        val snapshot = TransferFixtures.backup()
        val decisions = ImportDecisions.initial(snapshot)
        assertEquals(setOf(EntityRef(TransferFixtures.id(1), TransferFixtures.id(11))), decisions.selectedTasks)
        assertTrue(decisions.carTargets.isEmpty())
        assertTrue(decisions.taskTargets.isEmpty())
        assertTrue(decisions.taskChoices.isEmpty())
    }

}
