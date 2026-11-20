package ru.kuznetsov.itstime4service.features.transfer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import itstime4service.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.domain.transfer.TransferGroup
import ru.kuznetsov.itstime4service.features.settings.SettingRow
import ru.kuznetsov.itstime4service.ui.components.buttons.OutlineButton
import ru.kuznetsov.itstime4service.ui.components.dialogs.ConfirmDialog
import ru.kuznetsov.itstime4service.ui.components.fields.AppTextField
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons

@Composable
internal fun TransferExportPurposeContent(state: TransferUiState, actions: TransferActions) {
    SettingRow(
        ItsTime4ServiceIcons.ScrollText,
        stringResource(Res.string.transfer_car_backup),
        stringResource(Res.string.transfer_car_backup_note),
        onClick = { actions.export(TransferExportPurpose.CAR_BACKUP) }.takeUnless { state.isBusy },
    ) {
        Icon(ItsTime4ServiceIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
    }
    SettingRow(
        ItsTime4ServiceIcons.Share,
        stringResource(Res.string.transfer_share_schedule),
        stringResource(Res.string.transfer_clean_note),
        onClick = { actions.export(TransferExportPurpose.SHARE_SCHEDULE) }.takeUnless { state.isBusy },
    ) {
        Icon(ItsTime4ServiceIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun TransferExportContent(state: TransferUiState, actions: TransferActions, modifier: Modifier = Modifier) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    if (state.isConfirmingPlaintext) ConfirmDialog(
        title = stringResource(Res.string.transfer_protect),
        text = stringResource(Res.string.transfer_plain_warning),
        confirmLabel = stringResource(Res.string.transfer_plain_confirm),
        dismissLabel = stringResource(Res.string.action_cancel),
        onConfirm = {
            password = ""
            confirmation = ""
            actions.plaintext(true)
        },
        onDismiss = { actions.plaintext(false) },
    )

    Column(modifier) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.isFullBackup) TransferNote(stringResource(Res.string.transfer_backup_note))
            TransferPanel(stringResource(Res.string.transfer_protection)) {
                TransferCheck(stringResource(Res.string.transfer_protect), state.isProtected, state.isBusy) { enabled ->
                    actions.protection(enabled)
                    if (enabled || !state.hasPersonalExport) {
                        password = ""
                        confirmation = ""
                    }
                }
                if (state.isProtected) {
                    TransferSecret(password, { password = it }, Res.string.transfer_password, state.isBusy)
                    TransferSecret(confirmation, { confirmation = it }, Res.string.transfer_password_confirm, state.isBusy)
                    TransferNote(stringResource(Res.string.transfer_password_note))
                }
            }
            if (!state.isFullBackup) TransferCarExportFields(state, actions)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            TransferButton(Res.string.transfer_continue, state.isBusy || (!state.isFullBackup && state.exportCar == null)) {
                actions.previewExport(password, confirmation)
                if (state.isProtected && password.isNotEmpty() && password == confirmation) {
                    password = ""
                    confirmation = ""
                }
            }
        }
    }
}

@Composable
private fun TransferCarExportFields(state: TransferUiState, actions: TransferActions) {
    if (state.cars.isEmpty() || (state.isExportCarFixed && state.exportCar == null)) {
        TransferNote(stringResource(Res.string.transfer_no_cars))
        return
    }
    if (state.isExportCarFixed) {
        state.exportCar?.let { TransferHeading(it.brand) }
    } else {
        TransferPanel(stringResource(Res.string.transfer_choose_car)) {
            state.cars.forEach { car ->
                TransferChoice(car.brand, car.id == state.exportCarId, state.isBusy) { actions.car(car.id) }
            }
        }
    }
    val car = state.exportCar ?: return
    if (car.tasks.isNotEmpty()) TransferPanel(stringResource(Res.string.transfer_tasks)) {
        TransferCheck(stringResource(Res.string.transfer_all_tasks), state.exportTaskIds.size == car.tasks.size, state.isBusy, actions.allTasks)
        car.tasks.forEach { task ->
            TransferCheck(task.name, task.id in state.exportTaskIds, state.isBusy) { actions.exportTask(task.id, it) }
        }
    }
    if (state.exportPurpose == TransferExportPurpose.CAR_BACKUP) {
        TransferPanel(stringResource(Res.string.transfer_car_data)) {
            val groups = listOf(TransferGroup.MILEAGE, TransferGroup.VIN, TransferGroup.PLATE, TransferGroup.LAST_SERVICE, TransferGroup.HISTORY)
            for (group in groups) {
                TransferCheck(groupLabel(group), group in state.exportGroups, state.isBusy) { actions.group(group, it) }
            }
            if (TransferGroup.HISTORY in state.exportGroups) {
                val group = TransferGroup.HISTORY_DETAILS
                TransferCheck(groupLabel(group), group in state.exportGroups, state.isBusy) { actions.group(group, it) }
            }
        }
    }
    AppTextField(state.description, actions.description, Modifier.fillMaxWidth(),
        placeholder = stringResource(Res.string.transfer_description), minLines = 3, enabled = !state.isBusy)
}

@Composable
internal fun TransferExportPreview(state: TransferUiState, actions: TransferActions) {
    state.exportPreview?.let { SnapshotDetails(it) }
    TransferNote(stringResource(if (state.isProtected) Res.string.transfer_protected_file else Res.string.transfer_unprotected_file))
    val shareFirst = state.exportPurpose == TransferExportPurpose.SHARE_SCHEDULE
    TransferButton(if (shareFirst) Res.string.transfer_share_action else Res.string.transfer_save, state.isBusy) { actions.write(shareFirst) }
    OutlineButton(
        stringResource(if (shareFirst) Res.string.transfer_save else Res.string.transfer_share),
        onClick = { actions.write(!shareFirst) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isBusy,
    )
}
