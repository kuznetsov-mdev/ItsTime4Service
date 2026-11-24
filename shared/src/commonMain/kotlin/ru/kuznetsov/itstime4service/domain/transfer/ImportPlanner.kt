package ru.kuznetsov.itstime4service.domain.transfer

import ru.kuznetsov.itstime4service.domain.model.Settings

internal sealed interface ImportPreparation {
    data class NeedsDecisions(val decisions: List<PendingDecision>) : ImportPreparation
    data class Ready(val changes: ImportChangeSet, val summary: ImportSummary) : ImportPreparation
}

internal class ImportPlanner(
    private val newId: (String) -> String,
    private val decisionId: (String) -> String,
) {
    private val pending = linkedMapOf<String, PendingDecision>()
    private val disabled = linkedSetOf<EntityRef>()
    private val conflictedTasks = mutableSetOf<String>()
    private val mappings = mutableListOf<ImportProvenance>()
    private val taskMap = mutableMapOf<EntityRef, String>()
    private val carMap = mutableMapOf<String, String>()
    private val targetDetails = mutableMapOf<EntityRef, TransferTask>()
    private var skipped = 0
    private lateinit var source: TransferSnapshot
    private lateinit var digest: String
    private lateinit var decisions: ImportDecisions
    private lateinit var state: ImportState
    private lateinit var instance: String
    private val mappingKeys = mutableSetOf<List<String>>()
    private val relatedEntriesCache = mutableMapOf<Triple<String, String, String>, Set<String>>()
    private val relatedCache = mutableMapOf<Triple<String, String, String>, Boolean>()
    private var sourceHistoryByTask: Map<EntityRef, List<TransferHistory>> = emptyMap()
    private var localHistoryByTask: Map<EntityRef, List<TransferHistory>> = emptyMap()
    private var taskOwners: Map<String, Set<String>> = emptyMap()
    private var historyOwners: Map<String, String> = emptyMap()
    private var priorMappings: Map<Triple<String, String, String>, List<ImportProvenance>> = emptyMap()

    fun prepare(
        snapshot: TransferSnapshot,
        sourceDigest: String,
        mode: ImportMode,
        choices: ImportDecisions,
        current: ImportState,
    ): ImportPreparation {
        mappingKeys.clear()
        relatedCache.clear()
        relatedEntriesCache.clear()
        pending.clear()
        disabled.clear()
        conflictedTasks.clear()
        mappings.clear()
        taskMap.clear()
        carMap.clear()
        targetDetails.clear()
        skipped = 0
        source = snapshot
        digest = sourceDigest
        decisions = choices
        state = current
        sourceHistoryByTask = historyByTask(snapshot.cars)
        localHistoryByTask = historyByTask(current.cars)
        taskOwners = current.cars.flatMap { car ->
            (car.tasks.map { it.id } + car.history.required().mapNotNull { it.taskId }).map { it to car.id }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
        historyOwners = current.cars.flatMap { car -> car.history.required().map { it.id to car.id } }.toMap()
        priorMappings = current.provenance.filter { it.snapshotId == snapshot.snapshotId && it.sourceDigest == sourceDigest }
            .groupBy { Triple(it.kind, it.incomingId, it.targetCarId) }
        instance = newId("instance")
        val cars = when (mode) {
            ImportMode.REPLACE_BACKUP -> replace()
            ImportMode.MERGE_BACKUP, ImportMode.INTO_CAR -> merge()
        }
        if (pending.isNotEmpty()) return ImportPreparation.NeedsDecisions(pending.values.toList())
        val finalCars = cars.map(::validateRollback).sortedBy { it.sold.valueOrNull() == true }
        val settings = settings(finalCars)
        val reminders = reminders(finalCars, mode)
        val changes = ImportChangeSet(finalCars, settings, reminders, mappings.toList(), carMap.toMap(), targetDetails.toMap())
        return ImportPreparation.Ready(changes, summary(finalCars, mode))
    }

    private fun replace(): List<TransferCar> {
        if (decisions.backupSafety == BackupSafety.NOT_REQUIRED) {
            need("safety", emptySet(), ImportDecisionReason.BACKUP_SAFETY)
        }
        source.cars.forEach { car ->
            carMap[car.id] = car.id
            remember("car", car.id, car.id, car.id)
            car.tasks.forEach { task ->
                taskMap[EntityRef(car.id, task.id)] = task.id
                targetDetails[EntityRef(car.id, task.id)] = task
                remember("task", task.id, car.id, task.id)
            }
            car.history.valueOrNull().orEmpty().forEach { entry ->
                remember("history", entry.id, car.id, entry.id)
                entry.taskId?.let { remember("task", it, car.id, it) }
            }
        }
        return source.cars
    }

    private fun merge(): List<TransferCar> {
        val incomingIds = source.cars.map { it.id }.toSet()
        val duplicateTargets = decisions.carTargets.values.filterIsInstance<TargetCar.Existing>()
            .groupingBy { it.localCarId }.eachCount().filterValues { it > 1 }.keys
        if (decisions.carTargets.keys != incomingIds) {
            source.cars.filter { it.id !in decisions.carTargets }.forEach {
                need("car:${it.id}", setOf(EntityRef(it.id, it.id)), ImportDecisionReason.CAR_TARGET)
            }
            if (decisions.carTargets.keys.any { it !in incomingIds }) {
                need("car-targets", emptySet(), ImportDecisionReason.CAR_TARGET)
            }
        }
        val cars = state.cars.toMutableList()
        source.cars.forEach { incoming ->
            val target = decisions.carTargets[incoming.id] ?: return@forEach
            val original = when (target) {
                is TargetCar.Existing -> state.cars.find { it.id == target.localCarId }
                is TargetCar.New -> null
            }
            if (target is TargetCar.Existing && (original == null || target.localCarId in duplicateTargets)) {
                need("car:${incoming.id}", setOf(EntityRef(incoming.id, incoming.id)), ImportDecisionReason.CAR_TARGET)
                return@forEach
            }
            val local = original ?: newCar(incoming, target as TargetCar.New)
            carMap[incoming.id] = local.id
            remember("car", incoming.id, local.id, local.id)
            val merged = mergeCar(incoming, local, original == null)
            val index = cars.indexOfFirst { it.id == local.id }
            if (index < 0) cars.add(merged) else cars[index] = merged
        }
        return cars
    }

    private fun newCar(incoming: TransferCar, target: TargetCar.New): TransferCar = TransferCar(
        if (source.kind == TransferKind.BACKUP && state.cars.none { it.id == incoming.id }) incoming.id else newId("car:${incoming.id}"), target.brand, incoming.maintenanceDescription, Presence.Present(false),
        Presence.Present(target.confirmedMileageKm), Presence.Present(null), Presence.Present(null),
        Presence.Present(CarPreferences(false, false)), emptyList(), Presence.Present(emptyList()),
    )

    private fun mergeCar(incoming: TransferCar, local: TransferCar, isNew: Boolean): TransferCar {
        val carRef = EntityRef(incoming.id, incoming.id)
        val sold = incoming.sold.valueOrNull()?.let { value ->
            if (value == local.sold.valueOrNull()) value else {
                val key = key("sold:${incoming.id}:${local.id}")
                val choice = decisions.soldChoices[key]
                if (choice == null) pending[key] = PendingDecision(key, setOf(carRef), ImportDecisionReason.SOLD_STATE, "sold", local.tasks)
                if (choice == PersonalChoice.TAKE_INCOMING) value else local.sold.valueOrNull() == true
            }
        } ?: (local.sold.valueOrNull() == true)
        val mappedTasks = mergeTasks(incoming, local, sold)
        val history = mergeHistory(incoming, local, mappedTasks)
        if (sold) mappedTasks.forEach { conflictedTasks.add(it.id) }
        return local.copy(
            brand = if (isNew) local.brand else field(incoming, local, "brand", Presence.Present(incoming.brand), local.brand),
            maintenanceDescription = if (isNew) local.maintenanceDescription else field(incoming, local, "description", Presence.Present(incoming.maintenanceDescription), local.maintenanceDescription),
            sold = Presence.Present(sold),
            mileageKm = if (isNew) local.mileageKm else Presence.Present(field(incoming, local, "mileage", incoming.mileageKm, local.mileageKm.required())),
            vin = Presence.Present(field(incoming, local, "vin", incoming.vin, local.vin.required())),
            plate = Presence.Present(field(incoming, local, "plate", incoming.plate, local.plate.required())),
            preferences = Presence.Present(field(incoming, local, "preferences", incoming.preferences, local.preferences.required())),
            tasks = if (sold) emptyList() else mappedTasks,
            history = Presence.Present(history),
        )
    }

    private fun mergeTasks(incoming: TransferCar, local: TransferCar, sold: Boolean): List<TransferTask> {
        val tasks = local.tasks.toMutableList()
        val matcher = ImportTaskMatcher(local.tasks, source.kind)
        val positions = local.tasks.withIndex().associate { it.value.id to it.index }
        val used = mutableMapOf<String, EntityRef>()
        incoming.tasks.forEach { task ->
            val ref = EntityRef(incoming.id, task.id)
            if (ref !in decisions.selectedTasks) { skipped++; return@forEach }
            val provenance = previous("task", task.id, local.id)
            val previousTargets = matcher.previousTargets(provenance.map { it.localId })
            val candidates = matcher.candidates(task, previousTargets)
            val choice = decisions.taskChoices[ref] ?: TaskChoice.SKIP
            if (sold) { skipped++; return@forEach }
            val explicit = decisions.taskTargets[ref]
            if (explicit != null && (choice == TaskChoice.ADD_SEPARATE || candidates.none { it.id == explicit })) {
                need("task:${incoming.id}:${task.id}:${local.id}", setOf(ref), ImportDecisionReason.TASK_TARGET, candidates = candidates)
                return@forEach
            }
            if (provenance.isNotEmpty() && previousTargets.isEmpty() &&
                ref !in decisions.taskChoices) {
                need("missing:${incoming.id}:${task.id}:${local.id}", setOf(ref), ImportDecisionReason.MISSING_ENTITY)
                return@forEach
            }
            val foreignIdentity = taskOwners[task.id].orEmpty().any { it != local.id }
            if (source.kind == TransferKind.BACKUP && foreignIdentity && choice != TaskChoice.ADD_SEPARATE) {
                if (decisions.taskChoices[ref] == TaskChoice.SKIP) { skipped++; return@forEach }
                need("collision:${incoming.id}:${task.id}:${local.id}", setOf(ref), ImportDecisionReason.IDENTITY_COLLISION)
                return@forEach
            }
            val matches = candidates.isNotEmpty() || provenance.isNotEmpty()
            if (choice == TaskChoice.UPDATE_PARAMETERS && candidates.isEmpty()) {
                need("task:${incoming.id}:${task.id}:${local.id}", setOf(ref), ImportDecisionReason.TASK_TARGET)
                return@forEach
            }
            val add = choice == TaskChoice.ADD_SEPARATE || !matches
            if (add) {
                val id = if (source.kind == TransferKind.BACKUP && !matches && !foreignIdentity) task.id else newId("task:${incoming.id}:${task.id}:${local.id}")
                val base = task.copy(id = id, lastService = Presence.Present(LastService(null, null)),
                    reminderWindowKm = Presence.Present(state.settings.defaultReminderWindowKm))
                val service = taskService(incoming, local, task, base, false)
                tasks.add(base.copy(lastService = Presence.Present(service)))
                taskMap[ref] = id
                targetDetails[ref] = base
                remember("task", task.id, local.id, id)
                return@forEach
            }
            val needsFacts = task.lastService is Presence.Present
            val target = explicit?.let { id -> candidates.find { it.id == id } }
                ?: candidates.singleOrNull()
            if (choice == TaskChoice.SKIP && needsFacts && target == null) {
                val id = key("service-unmapped:${incoming.id}:${task.id}:${local.id}")
                when (decisions.personalChoices[id]) {
                    null -> {
                        pending[id] = PendingDecision(id, setOf(ref), ImportDecisionReason.PERSONAL_FIELD, "lastService", candidates)
                        return@forEach
                    }
                    PersonalChoice.KEEP_LOCAL -> { skipped++; return@forEach }
                    PersonalChoice.TAKE_INCOMING -> Unit
                }
            }
            if ((choice == TaskChoice.UPDATE_PARAMETERS || needsFacts) && target == null) {
                need("task:${incoming.id}:${task.id}:${local.id}", setOf(ref), ImportDecisionReason.TASK_TARGET, candidates = candidates)
                return@forEach
            }
            if (target == null) { skipped++; return@forEach }
            val service = taskService(incoming, local, task, target, true)
            val factsAccepted = decisions.personalChoices[key("service:${incoming.id}:${task.id}:${local.id}:${target.id}")] == PersonalChoice.TAKE_INCOMING ||
                decisions.relatedChoices[key("related:${incoming.id}:${task.id}:${local.id}:${target.id}")] == PersonalChoice.TAKE_INCOMING
            if (explicit != null || choice == TaskChoice.UPDATE_PARAMETERS ||
                (source.kind == TransferKind.BACKUP && task.id == target.id) || previousTargets.any { it.id == target.id } || factsAccepted) {
                taskMap[ref] = target.id
                targetDetails[ref] = target
                remember("task", task.id, local.id, target.id)
            }
            if (choice == TaskChoice.SKIP) {
                skipped++
                if (service == target.lastService.required()) return@forEach
            }
            val previous = used.put(target.id, ref)
            if (previous != null) {
                need("duplicate:${local.id}:${target.id}", setOf(previous, ref), ImportDecisionReason.DUPLICATE_TARGET, candidates = listOf(target))
                return@forEach
            }
            val updated = target.copy(lastService = Presence.Present(service))
            tasks[positions.getValue(target.id)] = if (choice == TaskChoice.UPDATE_PARAMETERS) {
                updated.copy(name = task.name, intervalKm = task.intervalKm, intervalMonths = task.intervalMonths, icon = task.icon)
            } else updated
        }
        return tasks
    }

    private fun taskService(incoming: TransferCar, local: TransferCar, task: TransferTask, target: TransferTask, existed: Boolean): LastService {
        val provided = task.lastService
        if (provided is Presence.Absent) return target.lastService.required()
        val value = provided.required()
        val current = target.lastService.required()
        val related = related(incoming, local, task, target)
        val choice = if (related) {
            relatedChoice(incoming, local, task, target)
        } else {
            val id = key("service:${incoming.id}:${task.id}:${local.id}:${target.id}")
            if (value != current && id !in decisions.personalChoices) {
                pending[id] = PendingDecision(id, setOf(EntityRef(incoming.id, task.id)), ImportDecisionReason.PERSONAL_FIELD, "lastService", listOf(target))
            }
            decisions.personalChoices[id]
        }
        if (existed && value != current) conflictedTasks.add(target.id)
        return if (choice == PersonalChoice.TAKE_INCOMING) value else current
    }

    private fun related(incoming: TransferCar, local: TransferCar, task: TransferTask, target: TransferTask): Boolean =
        relatedCache.getOrPut(Triple(incoming.id, task.id, target.id)) {
            if (local.tasks.none { it.id == target.id } || task.lastService is Presence.Absent) return@getOrPut false
            val entries = sourceHistoryByTask[EntityRef(incoming.id, task.id)].orEmpty()
            val localEntries = localHistoryByTask[EntityRef(local.id, target.id)].orEmpty()
            val localById = localEntries.associateBy { it.id }
            val serviceChanged = task.lastService != target.lastService
            val incomingService = task.lastService.required()
            val localService = target.lastService.required()
            val linked = entries.any { it.mileageKm == incomingService.mileageKm && it.date == incomingService.date } ||
                localEntries.any { it.mileageKm == localService.mileageKm && it.date == localService.date }
            val componentIds = relatedEntries(incoming, local, task, target)
            val historyChanged = entries.filter { it.id in componentIds }.any { entry ->
                val current = previous("history", entry.id, local.id).firstNotNullOfOrNull { localById[it.localId] }
                    ?: if (source.kind == TransferKind.BACKUP) localById[entry.id] else null
                current != null && !sameServiceFact(current, entry.copy(taskId = target.id))
            }
            linked && (serviceChanged || historyChanged)
        }

    private fun relatedChoice(incoming: TransferCar, local: TransferCar, task: TransferTask, target: TransferTask): PersonalChoice? {
        val id = key("related:${incoming.id}:${task.id}:${local.id}:${target.id}")
        val choice = decisions.relatedChoices[id]
        if (choice == null && id !in pending) {
            val refs = setOf(EntityRef(incoming.id, task.id)) + relatedEntries(incoming, local, task, target)
                .map { EntityRef(incoming.id, it) }
            pending[id] = PendingDecision(id, refs, ImportDecisionReason.RELATED_SERVICE_HISTORY, "lastServiceAndHistory", listOf(target))
        }
        conflictedTasks.add(target.id)
        return choice
    }

    private fun mergeHistory(incoming: TransferCar, local: TransferCar, tasks: List<TransferTask>): List<TransferHistory> {
        val provided = incoming.history
        if (provided is Presence.Absent) return local.history.required()
        val entries = provided.required()
        val history = local.history.required().toMutableList()
        val localById = history.associateBy { it.id }
        val positions = history.withIndex().associate { it.value.id to it.index }
        val incomingTasks = incoming.tasks.associateBy { it.id }
        val originalTasks = local.tasks.associateBy { it.id }
        val finalTasks = tasks.associateBy { it.id }
        val historyRefs = entries.map { EntityRef(incoming.id, it.id) }.toSet()
        entries.forEach { entry ->
            val sourceTask = incomingTasks[entry.taskId]
            val mappedTask = entry.taskId?.let { taskMap[EntityRef(incoming.id, it)] }
            val targetTask = originalTasks[mappedTask] ?: finalTasks[mappedTask]
            val grouped = sourceTask != null && targetTask != null && related(incoming, local, sourceTask, targetTask) &&
                entry.id in relatedEntries(incoming, local, sourceTask, targetTask)
            val previous = previous("history", entry.id, local.id)
            val current = previous.firstNotNullOfOrNull { localById[it.localId] }
                ?: if (source.kind == TransferKind.BACKUP) localById[entry.id] else null
            val incomingFact = entry.copy(id = current?.id ?: entry.id, taskId = mappedTask ?: entry.taskId,
                details = if (entry.details is Presence.Absent) current?.details ?: Presence.Present(HistoryDetails(null, null, null)) else entry.details)
            val choice = if (grouped) {
                relatedChoice(incoming, local, requireNotNull(sourceTask), requireNotNull(targetTask))
            } else {
                val unchanged = current != null && sameHistoryFact(current, incomingFact)
                val conflict = current != null && !unchanged
                val id = if (conflict) key("history-entry:${incoming.id}:${entry.id}:${local.id}:${current.id}") else key("history:${incoming.id}:${local.id}")
                if (!unchanged && id !in decisions.personalChoices && id !in pending) {
                    pending[id] = PendingDecision(id, if (conflict) setOf(EntityRef(incoming.id, entry.id)) else historyRefs, ImportDecisionReason.PERSONAL_FIELD, "history")
                }
                if (unchanged) PersonalChoice.KEEP_LOCAL else decisions.personalChoices[id]
            }
            if (current != null) remember("history", entry.id, local.id, current.id)
            if (choice != PersonalChoice.TAKE_INCOMING) return@forEach
            if (current == null && previous.isNotEmpty()) {
                val missingKey = key("missing-history:${incoming.id}:${entry.id}:${local.id}")
                when (decisions.personalChoices[missingKey]) {
                    null -> {
                        pending[missingKey] = PendingDecision(missingKey, setOf(EntityRef(incoming.id, entry.id)), ImportDecisionReason.MISSING_ENTITY, "history")
                        return@forEach
                    }
                    PersonalChoice.KEEP_LOCAL -> return@forEach
                    PersonalChoice.TAKE_INCOMING -> Unit
                }
            }
            val foreign = source.kind == TransferKind.BACKUP && historyOwners[entry.id]?.let { it != local.id } == true
            if (current == null && foreign) {
                val collisionKey = key("collision-history:${incoming.id}:${entry.id}:${local.id}")
                when (decisions.personalChoices[collisionKey]) {
                    null -> {
                        pending[collisionKey] = PendingDecision(collisionKey, setOf(EntityRef(incoming.id, entry.id)), ImportDecisionReason.IDENTITY_COLLISION, "history")
                        return@forEach
                    }
                    PersonalChoice.KEEP_LOCAL -> return@forEach
                    PersonalChoice.TAKE_INCOMING -> Unit
                }
            }
            val id = current?.id ?: if (source.kind == TransferKind.BACKUP && !foreign && previous.isEmpty()) entry.id else newId("history:${incoming.id}:${entry.id}:${local.id}")
            val taskId = entry.taskId?.let { incomingTaskId ->
                mappedTask ?: previous("task", incomingTaskId, local.id).firstOrNull { it.localId !in originalTasks }?.localId
                    ?: if (source.kind == TransferKind.BACKUP && taskOwners[incomingTaskId].orEmpty().all { it == local.id } &&
                        incomingTaskId !in originalTasks && incomingTaskId !in finalTasks) incomingTaskId else newId("tombstone:${incoming.id}:$incomingTaskId:${local.id}")
            }
            if (entry.taskId != null && taskId != null) {
                remember("task", entry.taskId, local.id, taskId)
                taskMap.getOrPut(EntityRef(incoming.id, entry.taskId)) { taskId }
            }
            var updated = incomingFact.copy(id = id, taskId = taskId,
                rollback = if (source.kind == TransferKind.CAR) Presence.Present(null) else entry.rollback)
            if (current != null && sameServiceFact(current, updated)) updated = updated.copy(rollback = current.rollback)
            if (current != null && !sameServiceFact(current, updated)) {
                current.taskId?.let { conflictedTasks.add(it) }
                taskId?.let { conflictedTasks.add(it) }
                if (current.rollback.valueOrNull() != null || updated.rollback.valueOrNull() != null) disabled.add(EntityRef(local.id, updated.id))
                updated = updated.copy(rollback = Presence.Present(null))
            }
            if (current == null) history.add(updated) else history[positions.getValue(current.id)] = updated
            remember("history", entry.id, local.id, id)
        }
        return history
    }

    private fun relatedEntries(incoming: TransferCar, local: TransferCar, task: TransferTask, target: TransferTask): Set<String> =
        relatedEntriesCache.getOrPut(Triple(incoming.id, task.id, target.id)) {
        val incomingService = task.lastService.valueOrNull()
        val incomingEntries = sourceHistoryByTask[EntityRef(incoming.id, task.id)].orEmpty()
        val localEntries = localHistoryByTask[EntityRef(local.id, target.id)].orEmpty()
        val sourceAnchor = incomingEntries.withIndex().filter { incomingService != null && it.value.mileageKm == incomingService.mileageKm && it.value.date == incomingService.date }
            .maxWithOrNull(compareBy({ it.value.date }, { it.index }))?.value
        val localService = target.lastService.required()
        val localAnchor = localEntries.withIndex().filter { it.value.mileageKm == localService.mileageKm && it.value.date == localService.date }
            .maxWithOrNull(compareBy({ it.value.date }, { it.index }))?.value
        incomingEntries.filter { entry -> entry.id == sourceAnchor?.id ||
            (source.kind == TransferKind.BACKUP && entry.id == localAnchor?.id) ||
            previous("history", entry.id, local.id).any { it.localId == localAnchor?.id }
        }.map { it.id }.toSet()
    }

    private fun sameServiceFact(a: TransferHistory, b: TransferHistory): Boolean =
        a.taskId == b.taskId && a.date == b.date && a.mileageKm == b.mileageKm

    private fun sameHistoryFact(a: TransferHistory, b: TransferHistory): Boolean =
        a.copy(id = b.id, rollback = Presence.Absent) == b.copy(rollback = Presence.Absent)

    private fun validateRollback(car: TransferCar): TransferCar {
        val history = car.history.required()
        val grouped = history.withIndex().groupBy { it.value.taskId }
        val tasks = car.tasks.associateBy { it.id }
        val valid = grouped.mapNotNull { (id, entries) ->
            val task = tasks[id] ?: return@mapNotNull null
            if (task.id in conflictedTasks) return@mapNotNull null
            val latest = entries.maxWithOrNull(compareBy({ it.value.date }, { it.index }))?.value ?: return@mapNotNull null
            val service = task.lastService.required()
            latest.id.takeIf { latest.rollback.valueOrNull() != null && service.mileageKm == latest.mileageKm && service.date == latest.date }
        }.toSet()
        return car.copy(history = Presence.Present(history.map { entry ->
            if (entry.rollback.valueOrNull() != null && entry.id !in valid) {
                disabled.add(EntityRef(car.id, entry.id))
                entry.copy(rollback = Presence.Present(null))
            } else entry
        }))
    }

    private fun <T> field(incoming: TransferCar, local: TransferCar, name: String, provided: Presence<T>, current: T): T {
        if (provided is Presence.Absent || provided.required() == current) return current
        val id = key("field:${incoming.id}:${local.id}:$name")
        val choice = decisions.personalChoices[id]
        if (choice == null) pending[id] = PendingDecision(id, setOf(EntityRef(incoming.id, incoming.id)), ImportDecisionReason.PERSONAL_FIELD, name)
        return if (choice == PersonalChoice.TAKE_INCOMING) provided.required() else current
    }

    private fun settings(cars: List<TransferCar>): Settings {
        val restoring = decisions.restoreSettings && source.kind == TransferKind.BACKUP
        val chosen = if (restoring) source.settings.required() else state.settings
        val active = cars.filter { it.sold.valueOrNull() != true }.map { it.id }.toSet()
        fun map(id: String?): String? = (if (restoring) carMap[id] else id)?.takeIf { it in active }
        val default = map(chosen.defaultCarId)
        return chosen.copy(lastOpenedCarId = map(chosen.lastOpenedCarId), defaultCarId = default,
            openDefaultCar = chosen.openDefaultCar && default != null)
    }

    private fun reminders(cars: List<TransferCar>, mode: ImportMode): List<TransferReminder> {
        val active = cars.flatMap { it.tasks }.map { it.id }.toSet()
        val sourceOwners = source.cars.flatMap { car -> car.tasks.map { it.id to car.id } }.toMap()
        val incoming = source.reminderState.valueOrNull().orEmpty().mapNotNull { row ->
            val carId = sourceOwners[row.taskId] ?: return@mapNotNull null
            taskMap[EntityRef(carId, row.taskId)]?.takeIf { it in active && it !in conflictedTasks }
                ?.let { row.copy(taskId = it) }
        }
        val retained = if (mode == ImportMode.REPLACE_BACKUP) emptyList() else state.reminders.filter { it.taskId in active && it.taskId !in conflictedTasks }
        return (retained + incoming).groupBy { it.taskId }.map { (_, rows) -> rows.maxBy { it.shownAt } }
    }

    private fun summary(cars: List<TransferCar>, mode: ImportMode): ImportSummary {
        fun <T> counts(before: List<T>, after: List<T>, id: (T) -> String): List<Int> {
            val old = before.associateBy(id)
            val new = after.associateBy(id)
            return listOf(new.keys.count { it !in old }, new.count { (key, value) -> key in old && old[key] != value }, old.keys.count { it !in new })
        }
        val c = counts(state.cars.map { it.copy(tasks = emptyList(), history = Presence.Present(emptyList())) },
            cars.map { it.copy(tasks = emptyList(), history = Presence.Present(emptyList())) }) { it.id }
        val t = counts(state.cars.flatMap { it.tasks }, cars.flatMap { it.tasks }) { it.id }
        val h = counts(state.cars.flatMap { it.history.required() }, cars.flatMap { it.history.required() }) { it.id }
        return ImportSummary(c[0], c[1], c[2], t[0], t[1], t[2], h[0], h[1], h[2], skipped,
            decisions.restoreSettings && mode != ImportMode.INTO_CAR, disabled.toSet())
    }

    private fun previous(kind: String, id: String, carId: String) = priorMappings[Triple(kind, id, carId)].orEmpty()

    private fun remember(kind: String, id: String, carId: String, localId: String) {
        if (mappingKeys.add(listOf(kind, id, carId, localId)) && previous(kind, id, carId).none { it.localId == localId }) {
            mappings.add(ImportProvenance(source.snapshotId, digest, kind, id, carId, localId, instance))
        }
    }

    private fun historyByTask(cars: List<TransferCar>): Map<EntityRef, List<TransferHistory>> = cars.flatMap { car ->
        car.history.valueOrNull().orEmpty().mapNotNull { entry -> entry.taskId?.let { EntityRef(car.id, it) to entry } }
    }.groupBy({ it.first }, { it.second })

    private fun key(value: String) = decisionId(value)

    private fun need(value: String, refs: Set<EntityRef>, reason: ImportDecisionReason, candidates: List<TransferTask> = emptyList()) {
        val id = key(value)
        pending[id] = PendingDecision(id, refs, reason, candidates = candidates)
    }
}

internal fun <T> Presence<T>.valueOrNull(): T? = when (this) {
    Presence.Absent -> null
    is Presence.Present -> value
}

internal fun <T> Presence<T>.required(): T = when (this) {
    Presence.Absent -> error("Missing validated group")
    is Presence.Present -> value
}
