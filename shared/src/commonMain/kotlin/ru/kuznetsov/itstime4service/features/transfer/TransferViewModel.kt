package ru.kuznetsov.itstime4service.features.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository
import ru.kuznetsov.itstime4service.domain.transfer.*
import ru.kuznetsov.itstime4service.ui.format.keepKm
import ru.kuznetsov.itstime4service.ui.format.kmIn
import ru.kuznetsov.itstime4service.ui.transfer.*

/** Owns one in-memory transfer session; secrets, file bytes and plans never enter saved state or routes. */
class TransferViewModel(
    private val entry: TransferEntry,
    private val repository: TransferRepository,
    private val documents: TransferDocumentCodec,
    private val files: TransferFiles,
    private val cars: CarRepository,
    private val settings: SettingsRepository,
    private val createSnapshot: CreateTransferSnapshotUseCase,
    private val prepareImport: PrepareImportUseCase,
    private val applyImport: ApplyImportUseCase,
    private val reconcileNotifications: ReconcileImportNotificationsUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TransferUiState(
        step = when (entry) {
            TransferEntry.Settings -> TransferStep.HUB
            TransferEntry.CarForm -> TransferStep.IMPORT_FILE
            is TransferEntry.CarDetail -> TransferStep.EXPORT_PURPOSE
        },
        exportCarId = (entry as? TransferEntry.CarDetail)?.carId,
        isExportCarFixed = entry is TransferEntry.CarDetail,
    ))
    val state = mutableState.asStateFlow()
    private var started = false
    private var operation: Job? = null
    private var generation = 0
    private var fileBytes: ByteArray? = null
    private var exportSecret: SecretBytes? = null
    private var sourceDigest: String? = null
    private var plan: ImportPlan? = null
    private var safetyDatabaseDigest: String? = null

    fun start() {
        if (started || state.value.isClosed) return
        started = true
        execute {
            loadCars()
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun onRetryLoad() {
        if (state.value.isBusy) return
        started = false
        mutableState.update { it.copy(isLoading = true, loadFailed = false) }
        start()
    }

    fun onChooseCarExport() {
        if (!editable() || state.value.step != TransferStep.HUB) return
        mutableState.update { it.copy(step = TransferStep.EXPORT_PURPOSE, failure = null, problem = null) }
    }

    fun onStartExport(purpose: TransferExportPurpose) {
        val current = state.value
        val full = purpose == TransferExportPurpose.FULL_BACKUP
        val expectedStep = if (full) TransferStep.HUB else TransferStep.EXPORT_PURPOSE
        if (!editable() || current.step != expectedStep) return
        val car = current.exportCar ?: current.cars.firstOrNull().takeUnless { current.isExportCarFixed }
        clearSession()
        mutableState.update {
            it.copy(
                step = TransferStep.EXPORT,
                exportPurpose = purpose,
                exportCarId = car?.id ?: current.exportCarId,
                exportTaskIds = car?.tasks?.map { task -> task.id }?.toSet().orEmpty(),
                exportGroups = if (purpose == TransferExportPurpose.CAR_BACKUP) selectableGroups else emptySet(),
                description = "",
                isProtected = purpose != TransferExportPurpose.SHARE_SCHEDULE,
                isConfirmingPlaintext = false,
                exportPreview = null,
                failure = null,
                problem = null,
                incoming = null,
                importResult = null,
                exportSucceeded = false,
            )
        }
    }

    fun onExportCarSelect(id: String) {
        val current = state.value
        if (!editable() || current.step != TransferStep.EXPORT || current.isFullBackup || current.isExportCarFixed) return
        val car = current.cars.firstOrNull { it.id == id } ?: return
        clearExport()
        mutableState.update { it.copy(exportCarId = id, exportTaskIds = car.tasks.map { task -> task.id }.toSet(), problem = null) }
    }

    fun onExportTask(id: String, selected: Boolean) = edit {
        it.copy(exportTaskIds = if (selected) it.exportTaskIds + id else it.exportTaskIds - id)
    }

    fun onExportAllTasks(selected: Boolean) = edit {
        it.copy(exportTaskIds = if (selected) it.exportCar?.tasks?.map { task -> task.id }?.toSet().orEmpty() else emptySet())
    }

    fun onDescription(value: String) = edit { it.copy(description = value) }

    fun onExportGroup(group: TransferGroup, selected: Boolean) = edit {
        if (it.step != TransferStep.EXPORT || it.exportPurpose != TransferExportPurpose.CAR_BACKUP || group !in selectableGroups) return@edit it
        var groups = if (selected) it.exportGroups + group else it.exportGroups - group
        if (selected && group == TransferGroup.HISTORY_DETAILS) groups = groups + TransferGroup.HISTORY
        if (!selected && group == TransferGroup.HISTORY) groups = groups - TransferGroup.HISTORY_DETAILS
        it.copy(exportGroups = groups, isProtected = if (selected) true else it.isProtected, isConfirmingPlaintext = false)
    }

    fun onProtectionChange(enabled: Boolean) = edit {
        if (!enabled && it.hasPersonalExport) it.copy(isConfirmingPlaintext = true)
        else it.copy(isProtected = enabled, isConfirmingPlaintext = false)
    }

    fun onConfirmPlaintext(confirmed: Boolean) = edit {
        it.copy(isProtected = if (confirmed) false else it.isProtected, isConfirmingPlaintext = false)
    }

    fun onPrepareExport(password: String, confirmation: String) {
        val current = state.value
        if (!editable() || current.step != TransferStep.EXPORT || current.isConfirmingPlaintext) return
        val selection = if (current.isFullBackup) ExportSelection.FullBackup else {
            val id = current.exportCar?.id ?: return problem(TransferFormProblem.CAR_REQUIRED)
            val groups = if (current.exportPurpose == TransferExportPurpose.CAR_BACKUP) current.exportGroups else emptySet()
            ExportSelection.Car(id, current.exportTaskIds, groups, current.description.takeIf { it.isNotBlank() })
        }
        val secret = if (current.isProtected) password(password, confirmation) ?: return else null
        clearExport()
        exportSecret = secret
        execute {
            when (val result = createSnapshot(selection)) {
                is TransferOutcome.Failure -> { clearExport(); fail(result.reason) }
                is TransferOutcome.Success -> mutableState.update { it.copy(exportPreview = result.value, step = TransferStep.EXPORT_PREVIEW) }
            }
        }
    }

    fun onWriteExport(share: Boolean) {
        if (!editable() || state.value.step != TransferStep.EXPORT_PREVIEW) return
        val snapshot = state.value.exportPreview ?: return
        execute {
            when (val encoded = documents.encode(snapshot, exportSecret)) {
                is TransferOutcome.Failure -> fail(encoded.reason)
                is TransferOutcome.Success -> try {
                    val result = if (share) files.share(encoded.value) else files.save(encoded.value)
                    when (result) {
                        is TransferFileResult.Failure -> fail(result.reason)
                        TransferFileResult.Cancelled -> mutableState.update { it.copy(step = TransferStep.EXPORT, exportPreview = null) }
                        is TransferFileResult.Success -> mutableState.update { it.copy(step = TransferStep.RESULT,
                            exportSucceeded = true, exportShared = share, exportPreview = null) }
                    }
                } finally { encoded.value.fill(0) }
            }
            clearExport()
            if (state.value.step == TransferStep.EXPORT_PREVIEW) {
                mutableState.update { it.copy(step = TransferStep.EXPORT, exportPreview = null) }
            }
        }
    }

    fun onStartImport() {
        if (!editable()) return
        clearSession()
        mutableState.update { it.copy(step = TransferStep.IMPORT_FILE, incoming = null, localDetails = emptyList(), importResult = null,
            importSummary = null, importPreview = null, pending = emptyList(), targetDrafts = emptyMap(),
            decisions = ImportDecisions(selectedTasks = emptySet()), failure = null, problem = null, exportSucceeded = false) }
    }

    fun onPickFile() {
        if (!editable()) return
        onStartImport()
        execute {
            when (val picked = files.pick()) {
                is TransferFileResult.Failure -> fail(picked.reason)
                TransferFileResult.Cancelled -> Unit
                is TransferFileResult.Success -> {
                    val result = try { files.read(picked.value) }
                    finally { withContext(NonCancellable) { files.release(picked.value) } }
                    when (result) {
                        is TransferFileResult.Failure -> fail(result.reason)
                        TransferFileResult.Cancelled -> Unit
                        is TransferFileResult.Success -> {
                            fileBytes = result.value
                            when (val protection = documents.inspect(result.value)) {
                                is TransferOutcome.Failure -> { clearFile(); fail(protection.reason) }
                                is TransferOutcome.Success -> if (protection.value == TransferProtection.PASSWORD) {
                                    mutableState.update { it.copy(step = TransferStep.UNLOCK) }
                                } else decode(null)
                            }
                        }
                    }
                }
            }
        }
    }

    fun onUnlock(value: String) {
        if (!editable() || state.value.step != TransferStep.UNLOCK) return
        val secret = password(value, value) ?: return
        execute { try { decode(secret) } finally { secret.close() } }
    }

    private suspend fun decode(secret: SecretBytes?) {
        val bytes = fileBytes ?: return
        when (val result = documents.decode(bytes, secret)) {
            is TransferOutcome.Failure -> fail(result.reason)
            is TransferOutcome.Success -> when (val fingerprint = repository.fingerprint(result.value)) {
                is TransferOutcome.Failure -> fail(fingerprint.reason)
                is TransferOutcome.Success -> {
                    loadCars()
                    sourceDigest = fingerprint.value
                    clearFile()
                    val snapshot = result.value
                    val decisions = ImportDecisions.initial(snapshot).copy(restoreSettings = snapshot.kind == TransferKind.BACKUP)
                    val current = state.value
                    val drafts = snapshot.cars.associate { car ->
                        car.id to ImportTargetDraft(
                            isNew = current.allCars.isEmpty(),
                            brand = car.brand,
                            mileage = (car.mileageKm as? Presence.Present)?.value?.kmIn(current.distanceUnit)?.toString().orEmpty(),
                        )
                    }
                    mutableState.update { it.copy(step = TransferStep.IMPORT_SETUP, incoming = snapshot,
                        importMode = if (snapshot.kind == TransferKind.BACKUP) ImportMode.MERGE_BACKUP else ImportMode.INTO_CAR,
                        decisions = decisions, targetDrafts = drafts, pending = emptyList(), importSummary = null, importPreview = null) }
                }
            }
        }
    }

    fun onTargetDraft(sourceId: String, draft: ImportTargetDraft) {
        val current = state.value
        if (!editable() || current.step != TransferStep.IMPORT_SETUP) return
        val previous = current.targetDrafts[sourceId] ?: return
        if (draft == previous) return
        val changedDestination = previous.isNew != draft.isNew || previous.existingId != draft.existingId
        edit {
            val decisions = it.decisions.copy(carTargets = it.decisions.carTargets - sourceId)
            it.copy(
                targetDrafts = it.targetDrafts + (sourceId to draft),
                decisions = if (changedDestination) decisions.copy(
                    taskChoices = decisions.taskChoices.filterKeys { ref -> ref.carId != sourceId },
                    taskTargets = decisions.taskTargets.filterKeys { ref -> ref.carId != sourceId },
                    personalChoices = emptyMap(),
                    relatedChoices = emptyMap(),
                    soldChoices = emptyMap(),
                ) else decisions,
                pending = emptyList(),
                importSummary = null,
                importPreview = null,
            )
        }
    }

    fun onImportMode(mode: ImportMode) = edit {
        val source = it.incoming ?: return@edit it
        if ((source.kind == TransferKind.CAR) != (mode == ImportMode.INTO_CAR)) return@edit it
        it.copy(importMode = mode, decisions = ImportDecisions.initial(source).copy(restoreSettings = it.decisions.restoreSettings),
            pending = emptyList(), importSummary = null, importPreview = null)
    }

    fun onRestoreSettings(restore: Boolean) = edit { it.copy(decisions = it.decisions.copy(restoreSettings = restore)) }

    fun onImportTask(ref: EntityRef, selected: Boolean) = edit {
        val chosen = if (selected) it.decisions.selectedTasks + ref else it.decisions.selectedTasks - ref
        it.copy(decisions = it.decisions.copy(selectedTasks = chosen,
            taskChoices = it.decisions.taskChoices.filterKeys { key -> key in chosen },
            taskTargets = it.decisions.taskTargets.filterKeys { key -> key in chosen }))
    }

    fun onTaskChoice(ref: EntityRef, choice: TaskChoice) = edit {
        if (ref !in it.decisions.selectedTasks) return@edit it
        it.copy(decisions = it.decisions.copy(taskChoices = it.decisions.taskChoices + (ref to choice),
            taskTargets = it.decisions.taskTargets - ref))
    }

    fun onTaskTarget(ref: EntityRef, id: String) = edit {
        it.copy(decisions = it.decisions.copy(taskTargets = it.decisions.taskTargets + (ref to id)))
    }

    fun onPersonalChoice(decision: PendingDecision, choice: PersonalChoice) = edit {
        val choices = it.decisions
        val updated = when (decision.reason) {
            ImportDecisionReason.SOLD_STATE -> choices.copy(soldChoices = choices.soldChoices + (decision.id to choice))
            ImportDecisionReason.RELATED_SERVICE_HISTORY -> choices.copy(relatedChoices = choices.relatedChoices + (decision.id to choice))
            else -> choices.copy(personalChoices = choices.personalChoices + (decision.id to choice))
        }
        it.copy(decisions = updated)
    }

    fun onDeclineBackup() = edit {
        it.copy(decisions = it.decisions.copy(backupSafety = BackupSafety.EXPLICITLY_DECLINED))
    }

    fun onSaveSafety(value: String, confirmation: String) {
        if (!editable() || state.value.importMode != ImportMode.REPLACE_BACKUP) return
        val secret = password(value, confirmation) ?: return
        execute {
            try {
                val current = state.value
                val source = current.incoming ?: return@execute
                val digest = sourceDigest ?: return@execute
                val prepared = prepareImport(ImportRequest(source, digest, current.importMode,
                    current.decisions.copy(backupSafety = BackupSafety.SAVED)))
                val captured = when (prepared) {
                    is TransferOutcome.Failure -> { fail(prepared.reason); return@execute }
                    is TransferOutcome.Success -> (prepared.value as? PreparedImport.Ready)?.plan ?: return@execute
                }
                when (val snapshot = createSnapshot(ExportSelection.FullBackup)) {
                    is TransferOutcome.Failure -> fail(snapshot.reason)
                    is TransferOutcome.Success -> when (val encoded = documents.encode(snapshot.value, secret)) {
                        is TransferOutcome.Failure -> fail(encoded.reason)
                        is TransferOutcome.Success -> try {
                            when (val saved = files.save(encoded.value)) {
                                is TransferFileResult.Failure -> fail(saved.reason)
                                TransferFileResult.Cancelled -> Unit
                                is TransferFileResult.Success -> {
                                    safetyDatabaseDigest = captured.databaseDigest
                                    mutableState.update { it.copy(decisions = it.decisions.copy(backupSafety = BackupSafety.SAVED)) }
                                    publishPlan(captured)
                                }
                            }
                        } finally { encoded.value.fill(0) }
                    }
                }
            } finally { secret.close() }
        }
    }

    fun onPrepareImport() {
        val current = state.value
        if (!editable() || (current.step != TransferStep.IMPORT_SETUP && current.step != TransferStep.IMPORT_DECISIONS)) return
        val snapshot = current.incoming ?: return
        if (current.step == TransferStep.IMPORT_SETUP && current.importMode != ImportMode.REPLACE_BACKUP) {
            val targets = mutableMapOf<String, TargetCar>()
            val usedExistingCars = mutableSetOf<String>()
            for (car in snapshot.cars) {
                val draft = current.targetDrafts[car.id] ?: return problem(TransferFormProblem.TARGET_REQUIRED)
                val target = if (draft.isNew) {
                    if (draft.brand.isBlank()) return problem(TransferFormProblem.BRAND_REQUIRED)
                    val mileage = draft.mileage.toIntOrNull()?.takeIf { it >= 0 }
                        ?: return problem(TransferFormProblem.MILEAGE_REQUIRED)
                    val original = (car.mileageKm as? Presence.Present)?.value
                    TargetCar.New(draft.brand, keepKm(original, mileage, current.distanceUnit))
                } else {
                    val id = draft.existingId?.takeIf { id -> current.allCars.any { it.id == id } }
                        ?: return problem(TransferFormProblem.TARGET_REQUIRED)
                    if (!usedExistingCars.add(id)) return problem(TransferFormProblem.DUPLICATE_CAR_TARGET)
                    TargetCar.Existing(id)
                }
                targets[car.id] = target
            }
            mutableState.update { it.copy(decisions = it.decisions.copy(carTargets = targets.toMap())) }
        }
        execute { prepare() }
    }

    private suspend fun prepare() {
        val current = state.value
        val snapshot = current.incoming ?: return
        val digest = sourceDigest ?: return
        when (val local = createSnapshot(ExportSelection.FullBackup)) {
            is TransferOutcome.Failure -> { fail(local.reason); return }
            is TransferOutcome.Success -> mutableState.update { it.copy(localDetails = local.value.cars) }
        }
        when (val result = prepareImport(ImportRequest(snapshot, digest, current.importMode, current.decisions))) {
            is TransferOutcome.Failure -> fail(result.reason)
            is TransferOutcome.Success -> when (val prepared = result.value) {
                is PreparedImport.NeedsDecisions -> {
                    plan = null
                    var choices = current.decisions
                    for (decision in prepared.decisions) {
                        choices = when (decision.reason) {
                            ImportDecisionReason.PERSONAL_FIELD -> choices.copy(personalChoices = choices.personalChoices +
                                (decision.id to (choices.personalChoices[decision.id] ?: PersonalChoice.KEEP_LOCAL)))
                            ImportDecisionReason.RELATED_SERVICE_HISTORY -> choices.copy(relatedChoices = choices.relatedChoices +
                                (decision.id to (choices.relatedChoices[decision.id] ?: PersonalChoice.KEEP_LOCAL)))
                            ImportDecisionReason.SOLD_STATE -> choices.copy(soldChoices = choices.soldChoices +
                                (decision.id to (choices.soldChoices[decision.id] ?: PersonalChoice.KEEP_LOCAL)))
                            ImportDecisionReason.MISSING_ENTITY, ImportDecisionReason.IDENTITY_COLLISION -> {
                                if (decision.field == "history") choices.copy(personalChoices = choices.personalChoices +
                                    (decision.id to (choices.personalChoices[decision.id] ?: PersonalChoice.KEEP_LOCAL)))
                                else choices.copy(taskChoices = choices.taskChoices + decision.affected.associateWith {
                                    choices.taskChoices[it] ?: TaskChoice.SKIP
                                })
                            }
                            else -> choices
                        }
                    }
                    val hasTaskTargetsToResolve = prepared.decisions.any { decision ->
                        decision.reason in setOf(ImportDecisionReason.TASK_TARGET, ImportDecisionReason.DUPLICATE_TARGET) &&
                            decision.affected.any { choices.taskChoices[it] == TaskChoice.UPDATE_PARAMETERS }
                    }
                    mutableState.update {
                        it.copy(
                            step = if (hasTaskTargetsToResolve) TransferStep.IMPORT_SETUP else TransferStep.IMPORT_DECISIONS,
                            pending = prepared.decisions,
                            decisions = choices,
                            importSummary = null,
                            importPreview = null,
                        )
                    }
                }
                is PreparedImport.Ready -> {
                    if (current.decisions.backupSafety == BackupSafety.SAVED &&
                        prepared.plan.databaseDigest != safetyDatabaseDigest) {
                        safetyDatabaseDigest = null
                        mutableState.update { it.copy(decisions = it.decisions.copy(backupSafety = BackupSafety.NOT_REQUIRED)) }
                        prepare()
                    } else publishPlan(prepared.plan)
                }
            }
        }
    }

    private fun publishPlan(prepared: ImportPlan) {
        plan = prepared
        mutableState.update { it.copy(step = TransferStep.IMPORT_PREVIEW,
            pending = if (it.step == TransferStep.IMPORT_SETUP) emptyList() else it.pending,
            importSummary = prepared.summary, importPreview = prepared.preview) }
    }

    fun onApplyImport() {
        if (!editable() || state.value.step != TransferStep.IMPORT_PREVIEW) return
        val captured = plan ?: return
        execute {
            when (val result = applyImport(captured)) {
                is TransferOutcome.Failure -> {
                    if (result.reason == TransferFailure.StalePlan) {
                        loadCars()
                        val source = state.value.incoming!!
                        plan = null
                        mutableState.update { it.copy(step = TransferStep.IMPORT_SETUP,
                            decisions = ImportDecisions.initial(source).copy(restoreSettings = it.decisions.restoreSettings),
                            pending = emptyList(), importSummary = null, importPreview = null) }
                    }
                    fail(result.reason)
                }
                is TransferOutcome.Success -> {
                    clearSession()
                    mutableState.update { it.copy(step = TransferStep.RESULT, importResult = result.value,
                        incoming = null, localDetails = emptyList(), pending = emptyList(), importPreview = null, targetDrafts = emptyMap(),
                        decisions = ImportDecisions(selectedTasks = emptySet())) }
                }
            }
        }
    }

    fun onRetryNotifications() {
        if (!editable()) return
        val result = state.value.importResult ?: return
        execute {
            when (val retried = reconcileNotifications(result.operationId)) {
                is TransferOutcome.Failure -> fail(retried.reason)
                is TransferOutcome.Success -> mutableState.update { it.copy(importResult = retried.value) }
            }
        }
    }

    fun onBack() {
        val current = state.value
        if (current.isClosed || current.isApplyingImport) return
        if (current.isConfirmingPlaintext) {
            onConfirmPlaintext(false)
            return
        }
        generation++
        operation?.cancel()
        operation = null
        clearFile()
        clearExport()
        plan = null
        val target = when (current.step) {
            TransferStep.HUB, TransferStep.RESULT -> { finish(); return }
            TransferStep.EXPORT_PURPOSE -> if (entry is TransferEntry.CarDetail) { finish(); return } else TransferStep.HUB
            TransferStep.EXPORT -> if (current.isFullBackup) TransferStep.HUB else TransferStep.EXPORT_PURPOSE
            TransferStep.EXPORT_PREVIEW -> TransferStep.EXPORT
            TransferStep.IMPORT_FILE -> if (entry == TransferEntry.CarForm) { finish(); return } else TransferStep.HUB
            TransferStep.UNLOCK -> TransferStep.IMPORT_FILE
            TransferStep.IMPORT_SETUP -> TransferStep.IMPORT_FILE
            TransferStep.IMPORT_DECISIONS -> TransferStep.IMPORT_SETUP
            TransferStep.IMPORT_PREVIEW -> if (current.pending.isEmpty()) TransferStep.IMPORT_SETUP else TransferStep.IMPORT_DECISIONS
        }
        mutableState.update { it.copy(step = target, isBusy = false, failure = null, problem = null,
            exportPreview = null, importPreview = null, importSummary = null,
            localDetails = if (target == TransferStep.IMPORT_FILE || target == TransferStep.HUB) emptyList() else it.localDetails,
            incoming = if (target == TransferStep.IMPORT_FILE || target == TransferStep.HUB) null else it.incoming) }
    }

    private fun finish() {
        generation++
        operation?.cancel()
        clearSession()
        mutableState.update { it.copy(isClosed = true, isBusy = false) }
    }

    private suspend fun loadCars() {
        val active = cars.observeCars().first()
        val all = cars.observeCarSummaries().first()
        val unit = settings.observeSettings().first().distanceUnit
        mutableState.update { it.copy(cars = active, allCars = all, distanceUnit = unit) }
    }

    private fun password(value: String, confirmation: String): SecretBytes? {
        if (value.isEmpty()) { problem(TransferFormProblem.PASSWORD_REQUIRED); return null }
        if (value != confirmation) { problem(TransferFormProblem.PASSWORD_MISMATCH); return null }
        return try { SecretBytes.fromPassword(value) }
        catch (_: IllegalArgumentException) { problem(TransferFormProblem.PASSWORD_INVALID); null }
    }

    private fun editable() = state.value.let { !it.isBusy && !it.isClosed && !it.isLoading && !it.loadFailed }
    private fun edit(change: (TransferUiState) -> TransferUiState) {
        if (!editable()) return
        plan = null
        mutableState.update { change(it).copy(failure = null, problem = null) }
    }
    private fun problem(value: TransferFormProblem) { mutableState.update { it.copy(problem = value) } }
    private fun fail(value: TransferFailure) { mutableState.update { it.copy(failure = value) } }
    private fun clearExport() { exportSecret?.close(); exportSecret = null }
    private fun clearFile() { fileBytes?.fill(0); fileBytes = null }
    private fun clearSession() { clearExport(); clearFile(); plan = null; sourceDigest = null; safetyDatabaseDigest = null }

    private fun execute(block: suspend () -> Unit) {
        if (state.value.isBusy || state.value.isClosed) return
        val token = ++generation
        mutableState.update { it.copy(isBusy = true, failure = null, problem = null) }
        operation = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.update { it.copy(failure = TransferFailure.DatabaseReadFailed, loadFailed = it.isLoading, isLoading = false) }
            }
            finally { if (generation == token) mutableState.update { it.copy(isBusy = false) } }
        }
    }

    override fun onCleared() { operation?.cancel(); clearSession(); super.onCleared() }

    private companion object {
        val selectableGroups = setOf(TransferGroup.MILEAGE, TransferGroup.VIN, TransferGroup.PLATE,
            TransferGroup.LAST_SERVICE, TransferGroup.HISTORY, TransferGroup.HISTORY_DETAILS)
    }
}
