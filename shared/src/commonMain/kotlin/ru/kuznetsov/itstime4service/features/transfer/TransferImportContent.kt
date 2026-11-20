package ru.kuznetsov.itstime4service.features.transfer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import itstime4service.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.domain.transfer.*
import ru.kuznetsov.itstime4service.ui.components.dialogs.ConfirmDialog
import ru.kuznetsov.itstime4service.ui.components.fields.AppNumberField
import ru.kuznetsov.itstime4service.ui.components.fields.AppTextField
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import androidx.compose.ui.text.AnnotatedString
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.ui.format.distanceText
import ru.kuznetsov.itstime4service.ui.format.unitGenitive

@Composable
internal fun TransferImportSetup(state: TransferUiState, actions: TransferActions) {
    val snapshot = state.incoming ?: return
    val title = when {
        snapshot.kind == TransferKind.BACKUP -> Res.string.transfer_backup
        snapshot.includedGroups.isNotEmpty() -> Res.string.transfer_car_data
        else -> Res.string.transfer_car
    }
    TransferHeading(stringResource(title))
    SnapshotDetails(snapshot)
    if (snapshot.kind == TransferKind.BACKUP) {
        TransferChoice(stringResource(Res.string.transfer_merge), state.importMode == ImportMode.MERGE_BACKUP, state.isBusy) {
            actions.mode(ImportMode.MERGE_BACKUP)
        }
        TransferChoice(stringResource(Res.string.transfer_replace), state.importMode == ImportMode.REPLACE_BACKUP, state.isBusy) {
            actions.mode(ImportMode.REPLACE_BACKUP)
        }
        TransferCheck(stringResource(Res.string.transfer_restore_settings), state.decisions.restoreSettings, state.isBusy, actions.settings)
    }
    if (state.importMode == ImportMode.REPLACE_BACKUP) TransferNote(stringResource(Res.string.transfer_replace_note))
    else snapshot.cars.forEach { car ->
        val draft = state.targetDrafts[car.id] ?: ImportTargetDraft()
        TransferPanel(stringResource(Res.string.transfer_target, car.brand)) {
            TransferChoice(stringResource(Res.string.transfer_new_car), draft.isNew, state.isBusy) {
                actions.target(car.id, draft.copy(isNew = true, existingId = null))
            }
            state.allCars.forEach { target ->
                TransferChoice(target.brand + if (target.isSold) " · " + stringResource(Res.string.car_sold) else "",
                    !draft.isNew && draft.existingId == target.id, state.isBusy) {
                    actions.target(car.id, draft.copy(isNew = false, existingId = target.id))
                }
            }
            if (draft.isNew) {
                AppTextField(draft.brand, { actions.target(car.id, draft.copy(brand = it)) }, Modifier.fillMaxWidth(),
                    placeholder = stringResource(Res.string.transfer_new_brand), enabled = !state.isBusy)
                TransferNote(stringResource(Res.string.transfer_confirm_mileage, unitGenitive(state.distanceUnit)))
                AppNumberField(draft.mileage, { actions.target(car.id, draft.copy(mileage = it)) }, Modifier.fillMaxWidth(),
                    placeholder = stringResource(Res.string.transfer_mileage), enabled = !state.isBusy)
            }
            car.tasks.forEach { task ->
                val ref = EntityRef(car.id, task.id)
                TransferCheck(task.name, ref in state.decisions.selectedTasks, state.isBusy) { actions.importTask(ref, it) }
                if (ref in state.decisions.selectedTasks && !draft.isNew) {
                    val localTasks = state.cars.firstOrNull { it.id == draft.existingId }?.tasks.orEmpty()
                    if (localTasks.any { it.id == task.id || it.name == task.name }) {
                        TaskChoiceContent(state, actions, ref)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskChoiceContent(state: TransferUiState, actions: TransferActions, ref: EntityRef) {
    val choice = state.decisions.taskChoices[ref] ?: TaskChoice.SKIP
    listOf(TaskChoice.SKIP to Res.string.transfer_skip, TaskChoice.UPDATE_PARAMETERS to Res.string.transfer_update_task,
        TaskChoice.ADD_SEPARATE to Res.string.transfer_add_task).forEach { (value, label) ->
        TransferChoice(stringResource(label), choice == value, state.isBusy) { actions.taskChoice(ref, value) }
    }
    TransferNote(stringResource(Res.string.transfer_task_note))
}

@Composable
internal fun TransferImportDecisions(state: TransferUiState, actions: TransferActions) {
    TransferHeading(stringResource(Res.string.transfer_decisions))
    state.pending.forEach { pending ->
        if (pending.reason == ImportDecisionReason.BACKUP_SAFETY) {
            SafetyBackupContent(state, actions)
        } else if (pending.reason == ImportDecisionReason.CAR_TARGET) {
            TransferNote(stringResource(Res.string.transfer_error_target))
        } else {
            val personal = pending.reason in setOf(ImportDecisionReason.PERSONAL_FIELD,
                ImportDecisionReason.RELATED_SERVICE_HISTORY, ImportDecisionReason.SOLD_STATE) || pending.field == "history"
            val car = state.incoming?.cars?.firstOrNull { incoming -> pending.affected.any { it.carId == incoming.id } }
            val names = pending.affected.mapNotNull { ref -> car?.tasks?.firstOrNull { it.id == ref.entityId }?.name }.distinct()
            TransferPanel((listOfNotNull(car?.brand) + names).joinToString(" · ")) {
                if (personal) {
                    TransferNote(stringResource(when (pending.reason) {
                        ImportDecisionReason.RELATED_SERVICE_HISTORY -> Res.string.transfer_related_note
                        ImportDecisionReason.SOLD_STATE -> Res.string.transfer_sold_note
                        else -> Res.string.transfer_personal_note
                    }))
                    PersonalValues(state, pending)
                    val choice = when (pending.reason) {
                        ImportDecisionReason.SOLD_STATE -> state.decisions.soldChoices[pending.id]
                        ImportDecisionReason.RELATED_SERVICE_HISTORY -> state.decisions.relatedChoices[pending.id]
                        else -> state.decisions.personalChoices[pending.id]
                    } ?: PersonalChoice.KEEP_LOCAL
                    TransferChoice(stringResource(Res.string.transfer_keep_local), choice == PersonalChoice.KEEP_LOCAL, state.isBusy) {
                        actions.personal(pending, PersonalChoice.KEEP_LOCAL)
                    }
                    TransferChoice(stringResource(Res.string.transfer_take_incoming), choice == PersonalChoice.TAKE_INCOMING, state.isBusy) {
                        actions.personal(pending, PersonalChoice.TAKE_INCOMING)
                    }
                } else {
                    TransferNote(stringResource(Res.string.transfer_missing_note))
                    pending.affected.forEach { ref ->
                        TaskChoiceContent(state, actions, ref)
                        if (state.decisions.taskChoices[ref] == TaskChoice.UPDATE_PARAMETERS) {
                            pending.candidates.forEach { target ->
                                TransferChoice(target.name, state.decisions.taskTargets[ref] == target.id, state.isBusy) {
                                    actions.taskTarget(ref, target.id)
                                }
                                TaskDetails(target, state)
                            }
                        }
                    }
                }
            }
        }
    }
    TransferButton(Res.string.transfer_continue, state.isBusy, actions.previewImport)
}

@Composable
private fun PersonalValues(state: TransferUiState, pending: PendingDecision) {
    val source = state.incoming?.cars?.firstOrNull { car -> pending.affected.any { it.carId == car.id } } ?: return
    val targetId = (state.decisions.carTargets[source.id] as? TargetCar.Existing)?.localCarId
    val local = state.localDetails.firstOrNull { it.id == targetId }
    val field = pending.field
    val label = when (field) {
        "vin" -> Res.string.transfer_vin
        "plate" -> Res.string.transfer_plate
        "mileage" -> Res.string.transfer_mileage
        "brand" -> Res.string.transfer_new_brand
        "description" -> Res.string.transfer_description
        "preferences" -> Res.string.transfer_field_settings
        "history", "lastServiceAndHistory" -> Res.string.transfer_field_history
        "sold" -> Res.string.car_sold
        else -> Res.string.transfer_field_service
    }
    TransferNote(stringResource(label))
    fun present(value: Presence<*>) = (value as? Presence.Present)?.value?.toString() ?: "—"
    if (field == "preferences") {
        val current = (local?.preferences as? Presence.Present)?.value
            ?: CarPreferences(false, false).takeIf { state.decisions.carTargets[source.id] is TargetCar.New }
        TransferNote(stringResource(Res.string.transfer_keep_local))
        VisibilityValues(current)
        TransferNote(stringResource(Res.string.transfer_take_incoming))
        VisibilityValues((source.preferences as? Presence.Present)?.value)
    } else if (field in setOf("vin", "plate", "mileage", "brand", "description", "sold")) {
        val incoming = when (field) { "vin" -> present(source.vin); "plate" -> present(source.plate); "brand" -> source.brand; "description" -> source.maintenanceDescription ?: "—";
            "sold" -> stringResource(if ((source.sold as? Presence.Present)?.value == true) Res.string.car_sold else Res.string.transfer_active_car);
            else -> (source.mileageKm as? Presence.Present)?.value?.let { distanceText(it, state.distanceUnit) } ?: "—" }
        val current = when (field) { "vin" -> local?.vin?.let(::present); "plate" -> local?.plate?.let(::present); "brand" -> local?.brand; "description" -> local?.maintenanceDescription ?: "—";
            "sold" -> stringResource(if (state.allCars.firstOrNull { it.id == targetId }?.isSold == true) Res.string.car_sold else Res.string.transfer_active_car);
            else -> (local?.mileageKm as? Presence.Present)?.value?.let { distanceText(it, state.distanceUnit) } }
        TransferNote(stringResource(Res.string.transfer_keep_local))
        if (field == "vin" || field == "plate") PrivateValue(current ?: "—", field == "vin", if (field == "vin") (local?.preferences as? Presence.Present)?.value?.vinHidden == true else (local?.preferences as? Presence.Present)?.value?.plateHidden == true)
        else TransferNote(current ?: "—")
        TransferNote(stringResource(Res.string.transfer_take_incoming))
        if (field == "vin" || field == "plate") {
            val preferences = (source.preferences as? Presence.Present)?.value
            PrivateValue(incoming, field == "vin", if (field == "vin") preferences?.vinHidden == true else preferences?.plateHidden == true)
        } else TransferNote(incoming)
    } else {
        TransferNote(stringResource(Res.string.transfer_take_incoming))
        source.tasks.filter { task -> pending.affected.any { it.entityId == task.id } }.forEach { TaskDetails(it, state) }
        if (pending.candidates.isNotEmpty()) TransferNote(stringResource(Res.string.transfer_keep_local))
        pending.candidates.forEach { TaskDetails(it, state) }
        if (field == "history" || field == "lastServiceAndHistory") {
            TransferNote(stringResource(Res.string.transfer_keep_local))
            local?.let { HistoryEntries(it, state) }
            TransferNote(stringResource(Res.string.transfer_take_incoming))
            HistoryEntries(source, state)
        }
    }
}

@Composable
private fun TaskDetails(task: TransferTask, state: TransferUiState) {
    TransferNote(stringResource(Res.string.transfer_task_interval, distanceText(task.intervalKm, state.distanceUnit),
        task.intervalMonths?.toString() ?: "—"))
    (task.lastService as? Presence.Present)?.value?.let { last ->
        TransferNote(stringResource(Res.string.transfer_task_last,
            last.mileageKm?.let { distanceText(it, state.distanceUnit) } ?: "—", last.date?.let(::displayDate) ?: "—"))
    }
}

@Composable
private fun SafetyBackupContent(state: TransferUiState, actions: TransferActions) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var declining by remember { mutableStateOf(false) }
    if (declining) ConfirmDialog(title = stringResource(Res.string.transfer_safety), text = stringResource(Res.string.transfer_safety_warning),
        confirmLabel = stringResource(Res.string.transfer_safety_decline), dismissLabel = stringResource(Res.string.action_cancel),
        onConfirm = { declining = false; actions.declineSafety(); actions.previewImport() }, onDismiss = { declining = false })
    TransferPanel(stringResource(Res.string.transfer_safety)) {
        TransferNote(stringResource(Res.string.transfer_replace_note))
        TransferSecret(password, { password = it }, Res.string.transfer_password, state.isBusy)
        TransferSecret(confirmation, { confirmation = it }, Res.string.transfer_password_confirm, state.isBusy)
        TransferButton(Res.string.transfer_safety_save, state.isBusy) {
            actions.safety(password, confirmation); password = ""; confirmation = ""
        }
        TransferButton(Res.string.transfer_safety_decline, state.isBusy) { declining = true }
    }
}

@Composable
internal fun TransferImportPreview(state: TransferUiState, actions: TransferActions) {
    var replacing by remember { mutableStateOf(false) }
    if (replacing) ConfirmDialog(title = stringResource(Res.string.transfer_replace_confirm), text = stringResource(Res.string.transfer_replace_note),
        confirmLabel = stringResource(Res.string.transfer_replace), dismissLabel = stringResource(Res.string.action_cancel),
        onConfirm = { replacing = false; actions.apply() }, onDismiss = { replacing = false })
    TransferHeading(stringResource(Res.string.transfer_preview))
    state.importSummary?.let { ImportSummaryContent(it) }
    state.importPreview?.let { preview ->
        preview.cars.forEach { car ->
            TransferPanel(car.brand) {
                (car.mileageKm as? Presence.Present)?.value?.let { TransferNote(distanceText(it, state.distanceUnit)) }
                car.maintenanceDescription?.let { TransferNote(it) }
                val preferences = (car.preferences as? Presence.Present)?.value
                (car.vin as? Presence.Present)?.value?.let { PrivateValue(it, true, preferences?.vinHidden == true) }
                (car.plate as? Presence.Present)?.value?.let { PrivateValue(it, false, preferences?.plateHidden == true) }
                if ((car.sold as? Presence.Present)?.value == true) TransferNote(stringResource(Res.string.car_sold))
                car.tasks.forEach { task -> TransferNote(task.name); TaskDetails(task, state) }
                HistoryEntries(car, state)
            }
        }
    }
    if (state.importMode == ImportMode.REPLACE_BACKUP) {
        TransferNote(stringResource(Res.string.transfer_replace_note))
        if (state.decisions.backupSafety == BackupSafety.SAVED) TransferNote(stringResource(Res.string.transfer_safety_saved))
    }
    TransferButton(Res.string.transfer_apply, state.isBusy) {
        if (state.importMode == ImportMode.REPLACE_BACKUP) replacing = true else actions.apply()
    }
}

@Composable
internal fun ImportSummaryContent(summary: ImportSummary) {
    TransferNote(stringResource(Res.string.transfer_summary_cars, summary.carsAdded, summary.carsUpdated, summary.carsRemoved))
    TransferNote(stringResource(Res.string.transfer_summary_tasks, summary.tasksAdded, summary.tasksUpdated, summary.tasksRemoved))
    TransferNote(stringResource(Res.string.transfer_summary_history, summary.historyAdded, summary.historyUpdated, summary.historyRemoved))
    TransferNote(stringResource(Res.string.transfer_summary_skipped, summary.skipped))
    if (summary.settingsRestored) TransferNote(stringResource(Res.string.transfer_restore_settings))
    if (summary.rollbackDisabled.isNotEmpty()) TransferNote(stringResource(Res.string.transfer_rollback_note))
}

private fun displayDate(date: LocalDate): String {
    val format = DateInputFormat.current
    return format.visualTransformation.filter(AnnotatedString(format.digitsOf(date))).text.text
}

@Composable
private fun PrivateValue(value: String, vin: Boolean, hidden: Boolean) {
    var revealed by remember(value, hidden) { mutableStateOf(!hidden) }
    if (revealed) TransferNote(value)
    else androidx.compose.material3.TextButton(onClick = { revealed = true }) {
        TransferNote(stringResource(if (vin) Res.string.action_show_vin else Res.string.action_show_plate))
    }
}

@Composable
private fun HistoryEntries(car: TransferCar, state: TransferUiState) {
    (car.history as? Presence.Present)?.value?.forEach { entry ->
        TransferNote("${entry.taskName} · ${displayDate(entry.date)} · ${distanceText(entry.mileageKm, state.distanceUnit)}")
        (entry.details as? Presence.Present)?.value?.let { details ->
            details.costRub?.let { TransferNote(stringResource(Res.string.cost_rub, it.toString())) }
            details.serviceName?.let { TransferNote(it) }
            details.comment?.let { TransferNote(it) }
        }
    }
}

@Composable
private fun VisibilityValues(preferences: CarPreferences?) {
    if (preferences == null) {
        TransferNote("—")
        return
    }
    TransferNote(stringResource(Res.string.transfer_vin) + ": " + stringResource(
        if (preferences.vinHidden) Res.string.transfer_hidden else Res.string.transfer_visible))
    TransferNote(stringResource(Res.string.transfer_plate) + ": " + stringResource(
        if (preferences.plateHidden) Res.string.transfer_hidden else Res.string.transfer_visible))
}
