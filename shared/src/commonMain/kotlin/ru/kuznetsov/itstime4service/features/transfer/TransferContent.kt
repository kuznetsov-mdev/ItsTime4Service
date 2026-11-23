package ru.kuznetsov.itstime4service.features.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import itstime4service.shared.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.kuznetsov.itstime4service.domain.transfer.*
import ru.kuznetsov.itstime4service.features.settings.SettingRow
import ru.kuznetsov.itstime4service.ui.components.buttons.PrimaryButton
import ru.kuznetsov.itstime4service.ui.components.fields.FieldBox
import ru.kuznetsov.itstime4service.ui.components.indicators.AppCheckbox
import ru.kuznetsov.itstime4service.ui.components.indicators.Preloader
import ru.kuznetsov.itstime4service.ui.components.screen.LoadError
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTopBar
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTitle
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
internal fun TransferContent(state: TransferUiState, actions: TransferActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(onBack = actions.back.takeUnless { state.isApplyingImport })
        val title = when (state.step) {
            TransferStep.EXPORT_PURPOSE -> Res.string.transfer_export
            TransferStep.EXPORT -> if (state.isFullBackup) Res.string.transfer_backup else Res.string.transfer_export
            TransferStep.EXPORT_PREVIEW -> Res.string.transfer_preview
            TransferStep.IMPORT_FILE -> Res.string.transfer_import
            else -> Res.string.transfer_title
        }
        val subtitle = state.exportCar?.brand.takeIf { state.step == TransferStep.EXPORT_PURPOSE && state.isExportCarFixed }
        ScreenTitle(stringResource(title), subtitle = subtitle)
        if (state.isLoading) {
            Preloader(Modifier.weight(1f).fillMaxWidth())
            return@Column
        }
        if (state.loadFailed) {
            LoadError(onRetry = actions.retry, modifier = Modifier.weight(1f))
            return@Column
        }
        state.failure?.let { TransferNote(stringResource(failureText(it)), Modifier.padding(18.dp), true) }
        state.problem?.let { TransferNote(stringResource(problemText(it)), Modifier.padding(18.dp), true) }
        if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        key(state.step) {
            val scroll = if (state.step == TransferStep.EXPORT) Modifier
                else Modifier.verticalScroll(rememberScrollState()).padding(18.dp)
            Column(Modifier.weight(1f).then(scroll),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (state.step) {
                    TransferStep.HUB -> {
                        TransferPanel(stringResource(Res.string.transfer_backup)) {
                            TransferNote(stringResource(Res.string.transfer_backup_note))
                            TransferButton(Res.string.transfer_export, state.isBusy) { actions.export(TransferExportPurpose.FULL_BACKUP) }
                        }
                        TransferButton(Res.string.transfer_import, state.isBusy, actions.startImport)
                        SettingRow(
                            ItsTime4ServiceIcons.Car,
                            stringResource(Res.string.transfer_export_car),
                            stringResource(Res.string.transfer_export_car_note),
                            onClick = actions.chooseCarExport,
                        ) {
                            Icon(ItsTime4ServiceIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    TransferStep.EXPORT_PURPOSE -> TransferExportPurposeContent(state, actions)
                    TransferStep.EXPORT -> TransferExportContent(state, actions, Modifier.fillMaxSize())
                    TransferStep.EXPORT_PREVIEW -> TransferExportPreview(state, actions)
                    TransferStep.IMPORT_FILE -> {
                        TransferNote(stringResource(Res.string.transfer_pick_note))
                        TransferButton(Res.string.transfer_pick, state.isBusy, actions.pick)
                    }
                    TransferStep.UNLOCK -> {
                        TransferHeading(stringResource(Res.string.transfer_unlock))
                        TransferNote(stringResource(Res.string.transfer_unlock_note))
                        var password by remember { mutableStateOf("") }
                        TransferSecret(password, { password = it }, Res.string.transfer_password, state.isBusy)
                        TransferButton(Res.string.transfer_continue, state.isBusy) {
                            actions.unlock(password); password = ""
                        }
                    }
                    TransferStep.IMPORT_SETUP -> TransferImportSetup(state, actions)
                    TransferStep.IMPORT_DECISIONS -> TransferImportDecisions(state, actions)
                    TransferStep.IMPORT_PREVIEW -> TransferImportPreview(state, actions)
                    TransferStep.RESULT -> {
                        TransferHeading(stringResource(if (state.exportSucceeded) {
                            if (state.exportShared) Res.string.transfer_shared else Res.string.transfer_saved
                        } else Res.string.transfer_imported))
                        state.importResult?.let { result ->
                            ImportSummaryContent(result.summary)
                            if (result.notifications != NotificationState.APPLIED) {
                                TransferNote(stringResource(Res.string.transfer_notifications_retry))
                                TransferButton(Res.string.transfer_retry_notifications, state.isBusy, actions.retryNotifications)
                            }
                        }
                        TransferButton(Res.string.action_done, state.isBusy, actions.back)
                    }
                }
            }
        }
        if (state.step == TransferStep.IMPORT_SETUP) {
            val missingTarget = state.importMode != ImportMode.REPLACE_BACKUP &&
                state.incoming?.cars.orEmpty().any { state.targetDrafts[it.id]?.hasTarget != true }
            TransferContinueButton(state.isBusy || missingTarget, actions.previewImport)
        }
    }
}

@Composable
internal fun TransferPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusL)
    Column(Modifier.fillMaxWidth().background(ItsTime4ServiceTheme.colors.surface, shape)
        .border(1.dp, ItsTime4ServiceTheme.colors.rule, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TransferHeading(title)
        content()
    }
}

@Composable
internal fun TransferHeading(text: String) {
    Text(text, style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 16.sp), color = ItsTime4ServiceTheme.colors.ink)
}

@Composable
internal fun TransferNote(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(text, modifier, style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.sp, lineHeight = 19.sp),
        color = if (error) ItsTime4ServiceTheme.colors.tierDue else ItsTime4ServiceTheme.colors.inkMuted)
}

@Composable
internal fun TransferButton(text: StringResource, busy: Boolean, action: () -> Unit) {
    PrimaryButton(stringResource(text), action, Modifier.fillMaxWidth(), enabled = !busy)
}

@Composable
internal fun TransferContinueButton(disabled: Boolean, action: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        TransferButton(Res.string.transfer_continue, disabled, action)
    }
}

@Composable
internal fun TransferChoice(label: String, selected: Boolean, busy: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, enabled = !busy, role = Role.RadioButton, onClick = action)
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null, enabled = !busy)
        Text(label, Modifier.weight(1f), style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.sp),
            color = ItsTime4ServiceTheme.colors.ink)
    }
}

@Composable
internal fun TransferCheck(label: String, selected: Boolean, busy: Boolean, action: (Boolean) -> Unit) {
    AppCheckbox(selected, action, label, Modifier.fillMaxWidth(), enabled = !busy)
}

@Composable
internal fun TransferSecret(value: String, change: (String) -> Unit, label: StringResource, busy: Boolean) {
    val text = stringResource(label)
    TransferNote(text)
    FieldBox(value, change, ItsTime4ServiceTheme.typography.sans.copy(fontSize = 14.sp),
        RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM), PaddingValues(14.dp),
        modifier = Modifier.fillMaxWidth(), label = text,
        keyboardType = KeyboardType.Password, enabled = !busy, visualTransformation = PasswordVisualTransformation())
}

@Composable
internal fun SnapshotDetails(snapshot: TransferSnapshot, showCars: Boolean = true) {
    if (snapshot.kind == TransferKind.BACKUP) {
        val sold = snapshot.cars.count { (it.sold as? Presence.Present)?.value == true }
        val date = snapshot.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
        TransferNote(stringResource(Res.string.transfer_backup_date, displayDate(date)))
        TransferNote(stringResource(Res.string.transfer_backup_car_counts, snapshot.cars.size - sold, sold))
    }
    TransferNote(stringResource(Res.string.transfer_counts, snapshot.cars.size, snapshot.cars.sumOf { it.tasks.size },
        snapshot.cars.sumOf { (it.history as? Presence.Present)?.value?.size ?: 0 }))
    if (snapshot.includedGroups.isEmpty()) TransferNote(stringResource(Res.string.transfer_groups_none))
    else TransferNote(snapshot.includedGroups.map { groupLabel(it) }.joinToString(" · "))
    if (!showCars) return
    snapshot.cars.forEach { car ->
        TransferPanel(car.brand) {
            car.maintenanceDescription?.let { TransferNote(it) }
            car.tasks.forEach { TransferNote(it.name) }
        }
    }
}

@Composable
internal fun groupLabel(group: TransferGroup): String = stringResource(when (group) {
    TransferGroup.MILEAGE -> Res.string.transfer_mileage
    TransferGroup.VIN -> Res.string.transfer_vin
    TransferGroup.PLATE -> Res.string.transfer_plate
    TransferGroup.LAST_SERVICE -> Res.string.transfer_last_service
    TransferGroup.HISTORY -> Res.string.transfer_history
    TransferGroup.HISTORY_DETAILS -> Res.string.transfer_history_details
    TransferGroup.PREFERENCES -> Res.string.transfer_field_settings
    TransferGroup.ROLLBACK -> Res.string.transfer_field_service
    TransferGroup.REMINDER_STATE -> Res.string.settings_notifications
})

private fun problemText(problem: TransferFormProblem) = when (problem) {
    TransferFormProblem.PASSWORD_REQUIRED, TransferFormProblem.PASSWORD_INVALID -> Res.string.transfer_error_password
    TransferFormProblem.PASSWORD_MISMATCH -> Res.string.transfer_error_password_match
    TransferFormProblem.CAR_REQUIRED, TransferFormProblem.TARGET_REQUIRED -> Res.string.transfer_error_target
    TransferFormProblem.DUPLICATE_CAR_TARGET -> Res.string.transfer_error_duplicate_car_target
    TransferFormProblem.BRAND_REQUIRED -> Res.string.transfer_error_brand
    TransferFormProblem.MILEAGE_REQUIRED -> Res.string.transfer_error_mileage
}

private fun failureText(reason: TransferFailure) = when (reason) {
    TransferFailure.AuthenticationFailed -> Res.string.transfer_error_auth
    TransferFailure.UnsupportedVersion, TransferFailure.UnsupportedProtection -> Res.string.transfer_error_version
    TransferFailure.LimitExceeded -> Res.string.transfer_error_limit
    TransferFailure.InvalidEnvelope, TransferFailure.InvalidSnapshot -> Res.string.transfer_error_format
    TransferFailure.StalePlan -> Res.string.transfer_error_stale
    TransferFailure.InsufficientSpace -> Res.string.transfer_error_space
    TransferFailure.DatabaseReadFailed, TransferFailure.DatabaseWriteFailed -> Res.string.transfer_error_database
    TransferFailure.SourceIdentityConflict, TransferFailure.InvalidOperationReuse -> Res.string.transfer_error_identity
    else -> Res.string.transfer_error_file
}
