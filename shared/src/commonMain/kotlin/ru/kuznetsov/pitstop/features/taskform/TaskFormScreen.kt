package ru.kuznetsov.pitstop.features.taskform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.action_add_more
import pitstop.shared.generated.resources.action_cancel
import pitstop.shared.generated.resources.action_delete
import pitstop.shared.generated.resources.action_remove_task
import pitstop.shared.generated.resources.action_save
import pitstop.shared.generated.resources.date_placeholder
import pitstop.shared.generated.resources.dialog_delete_task_text
import pitstop.shared.generated.resources.dialog_delete_task_title
import pitstop.shared.generated.resources.field_interval
import pitstop.shared.generated.resources.field_interval_months_short
import pitstop.shared.generated.resources.field_last_done_date_short
import pitstop.shared.generated.resources.field_last_done
import pitstop.shared.generated.resources.field_reminder_window
import pitstop.shared.generated.resources.field_task_name
import pitstop.shared.generated.resources.screen_edit_task
import pitstop.shared.generated.resources.screen_new_task
import pitstop.shared.generated.resources.task_name_placeholder
import ru.kuznetsov.pitstop.ui.components.ScreenPreview
import ru.kuznetsov.pitstop.ui.components.buttons.AppIconButton
import ru.kuznetsov.pitstop.ui.components.buttons.OutlineButton
import ru.kuznetsov.pitstop.ui.components.buttons.PresetChip
import ru.kuznetsov.pitstop.ui.components.buttons.PrimaryButton
import ru.kuznetsov.pitstop.ui.components.dialogs.ConfirmDialog
import ru.kuznetsov.pitstop.ui.components.fields.AppNumberField
import ru.kuznetsov.pitstop.ui.components.fields.AppTextField
import ru.kuznetsov.pitstop.ui.components.fields.FieldLabel
import ru.kuznetsov.pitstop.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.pitstop.ui.components.screen.ScreenTitle
import ru.kuznetsov.pitstop.ui.components.screen.ScreenTopBar
import ru.kuznetsov.pitstop.ui.format.TaskFields
import ru.kuznetsov.pitstop.ui.format.TaskPreset
import ru.kuznetsov.pitstop.ui.format.chipLabel
import ru.kuznetsov.pitstop.ui.format.taskName
import ru.kuznetsov.pitstop.ui.format.unitGenitive
import ru.kuznetsov.pitstop.ui.format.unitLabel
import ru.kuznetsov.pitstop.ui.icons.PitStopIcons

@Composable
fun TaskFormScreen(
    carId: String,
    taskId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskFormViewModel = koinViewModel { parametersOf(carId, taskId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isClosed) {
        if (state.isClosed) onBack()
    }
    TaskFormContent(
        state = state,
        onBack = onBack,
        onFieldsChange = viewModel::onFieldsChange,
        onPreset = viewModel::onPreset,
        onSave = viewModel::onSave,
        onSaveAndAddAnother = viewModel::onSaveAndAddAnother,
        onDeleteRequest = viewModel::onDeleteRequest,
        onDeleteDismiss = viewModel::onDeleteDismiss,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskFormContent(
    state: TaskFormUiState,
    onBack: () -> Unit,
    onFieldsChange: (TaskFields) -> Unit,
    onPreset: (TaskPreset, String) -> Unit,
    onSave: () -> Unit,
    onSaveAndAddAnother: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fields = state.fields
    Column(modifier.fillMaxSize()) {
        ScreenTopBar(onBack = onBack) {
            if (state.isEditing) {
                AppIconButton(
                    icon = PitStopIcons.Trash,
                    onClick = onDeleteRequest,
                    contentDescription = stringResource(Res.string.action_remove_task),
                )
            }
        }
        ScreenTitle(
            title = stringResource(if (state.isEditing) Res.string.screen_edit_task else Res.string.screen_new_task),
            subtitle = state.carBrand,
            monoSubtitle = true,
        )
        if (state.isLoading) return@Column
        key(state.formKey) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenHorizontalPadding)
                    .padding(top = 16.dp, bottom = 8.dp),
            ) {
                if (!state.isEditing) {
                    FlowRow(
                        modifier = Modifier.padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        TaskPreset.entries.forEach { preset ->
                            val name = stringResource(preset.taskName)
                            PresetChip(label = stringResource(preset.chipLabel), onClick = { onPreset(preset, name) })
                        }
                    }
                }
                FieldLabel(stringResource(Res.string.field_task_name), required = true)
                AppTextField(
                    value = fields.name,
                    onValueChange = { onFieldsChange(fields.copy(name = it)) },
                    placeholder = stringResource(Res.string.task_name_placeholder),
                    isError = state.showErrors && fields.isNameMissing,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                )
                Row(
                    modifier = Modifier.padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel(stringResource(Res.string.field_interval, unitLabel(state.distanceUnit)), required = true)
                        AppNumberField(
                            value = fields.intervalKm,
                            onValueChange = { onFieldsChange(fields.copy(intervalKm = it)) },
                            placeholder = "10000",
                            isError = state.showErrors && fields.isIntervalMissing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel(stringResource(Res.string.field_interval_months_short))
                        AppNumberField(
                            value = fields.intervalMonths,
                            onValueChange = { onFieldsChange(fields.copy(intervalMonths = it)) },
                            placeholder = "12",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel(stringResource(Res.string.field_last_done, unitLabel(state.distanceUnit)), required = true)
                        AppNumberField(
                            value = fields.lastDoneKm,
                            onValueChange = { onFieldsChange(fields.copy(lastDoneKm = it)) },
                            placeholder = "78000",
                            isError = state.showErrors && fields.isLastDoneMissing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel(stringResource(Res.string.field_last_done_date_short))
                        AppTextField(
                            value = fields.lastDoneDate,
                            onValueChange = { onFieldsChange(fields.copy(lastDoneDate = it)) },
                            placeholder = stringResource(Res.string.date_placeholder),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                FieldLabel(stringResource(Res.string.field_reminder_window, unitGenitive(state.distanceUnit)))
                AppNumberField(
                    value = fields.reminderWindowKm,
                    onValueChange = { onFieldsChange(fields.copy(reminderWindowKm = it)) },
                    placeholder = "1500",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.isEditing) {
                OutlineButton(
                    text = stringResource(Res.string.action_add_more),
                    onClick = onSaveAndAddAnother,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PrimaryButton(
                text = stringResource(Res.string.action_save),
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (state.isConfirmingDelete) {
        ConfirmDialog(
            title = stringResource(Res.string.dialog_delete_task_title),
            text = stringResource(Res.string.dialog_delete_task_text, fields.name),
            confirmLabel = stringResource(Res.string.action_delete),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }
}

private val previewState = TaskFormUiState(
    isLoading = false,
    carBrand = "Kia Rio",
    fields = TaskFields(
        name = "Замена масла и фильтра",
        intervalKm = "10000",
        intervalMonths = "12",
        lastDoneKm = "78000",
        lastDoneDate = "15.01.2026",
        reminderWindowKm = "1500",
    ),
)

@Composable
private fun TaskFormPreviewContent(state: TaskFormUiState) {
    TaskFormContent(
        state = state,
        onBack = {},
        onFieldsChange = {},
        onPreset = { _, _ -> },
        onSave = {},
        onSaveAndAddAnother = {},
        onDeleteRequest = {},
        onDeleteDismiss = {},
        onDeleteConfirm = {},
    )
}

@Preview
@Composable
private fun TaskFormContentLightPreview() = ScreenPreview(darkTheme = false) {
    TaskFormPreviewContent(previewState)
}

@Preview
@Composable
private fun TaskFormContentDarkPreview() = ScreenPreview(darkTheme = true) {
    TaskFormPreviewContent(previewState.copy(isEditing = true))
}
