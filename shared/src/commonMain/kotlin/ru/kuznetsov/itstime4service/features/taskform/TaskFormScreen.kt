package ru.kuznetsov.itstime4service.features.taskform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add_more
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_delete
import itstime4service.shared.generated.resources.action_pick_task_icon
import itstime4service.shared.generated.resources.action_remove_task
import itstime4service.shared.generated.resources.action_save
import itstime4service.shared.generated.resources.date_placeholder
import itstime4service.shared.generated.resources.dialog_delete_task_text
import itstime4service.shared.generated.resources.dialog_delete_task_title
import itstime4service.shared.generated.resources.field_interval
import itstime4service.shared.generated.resources.field_interval_months_short
import itstime4service.shared.generated.resources.field_last_done
import itstime4service.shared.generated.resources.field_last_done_date_short
import itstime4service.shared.generated.resources.field_reminder_window
import itstime4service.shared.generated.resources.field_task_name
import itstime4service.shared.generated.resources.screen_edit_task
import itstime4service.shared.generated.resources.screen_new_task
import itstime4service.shared.generated.resources.task_name_placeholder
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import ru.kuznetsov.itstime4service.ui.components.ScreenPreview
import ru.kuznetsov.itstime4service.ui.components.buttons.AppIconButton
import ru.kuznetsov.itstime4service.ui.components.buttons.OutlineButton
import ru.kuznetsov.itstime4service.ui.components.buttons.PresetChip
import ru.kuznetsov.itstime4service.ui.components.buttons.PrimaryButton
import ru.kuznetsov.itstime4service.ui.components.dialogs.ConfirmDialog
import ru.kuznetsov.itstime4service.ui.components.dialogs.TaskIconDialog
import ru.kuznetsov.itstime4service.ui.components.fields.AppNumberField
import ru.kuznetsov.itstime4service.ui.components.fields.AppTextField
import ru.kuznetsov.itstime4service.ui.components.fields.FieldLabel
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTitle
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTopBar
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import ru.kuznetsov.itstime4service.ui.format.TaskFields
import ru.kuznetsov.itstime4service.ui.format.TaskPreset
import ru.kuznetsov.itstime4service.ui.format.chipLabel
import ru.kuznetsov.itstime4service.ui.format.taskName
import ru.kuznetsov.itstime4service.ui.format.unitGenitive
import ru.kuznetsov.itstime4service.ui.format.unitLabel
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.icons.taskIcon
import ru.kuznetsov.itstime4service.ui.icons.vector
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

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
        onIconPickerOpen = viewModel::onIconPickerOpen,
        onIconPickerDismiss = viewModel::onIconPickerDismiss,
        onIconSelect = viewModel::onIconSelect,
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
    onIconPickerOpen: () -> Unit,
    onIconPickerDismiss: () -> Unit,
    onIconSelect: (TaskIcon) -> Unit,
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
                    icon = ItsTime4ServiceIcons.Trash,
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
                Row(
                    modifier = Modifier.padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskIconButton(icon = taskIcon(fields.icon, fields.name), onClick = onIconPickerOpen)
                    AppTextField(
                        value = fields.name,
                        onValueChange = { onFieldsChange(fields.copy(name = it)) },
                        placeholder = stringResource(Res.string.task_name_placeholder),
                        isError = state.showErrors && fields.isNameMissing,
                        modifier = Modifier.weight(1f),
                    )
                }
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
                        AppNumberField(
                            value = fields.lastDoneDate,
                            onValueChange = { onFieldsChange(fields.copy(lastDoneDate = it)) },
                            placeholder = stringResource(Res.string.date_placeholder),
                            visualTransformation = DateInputFormat.current.visualTransformation,
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
    if (state.isPickingIcon) {
        TaskIconDialog(
            selected = taskIcon(fields.icon, fields.name),
            onSelect = onIconSelect,
            onDismiss = onIconPickerDismiss,
        )
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

@Composable
private fun TaskIconButton(icon: TaskIcon, onClick: () -> Unit) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .background(colors.accent.copy(alpha = 0.12f))
            .border(1.dp, colors.accent.copy(alpha = 0.45f), shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon.vector,
            contentDescription = stringResource(Res.string.action_pick_task_icon),
            tint = colors.accent,
            modifier = Modifier.size(16.dp),
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
        icon = TaskIcon.DROPLET,
    ),
)

@Composable
private fun TaskFormPreviewContent(state: TaskFormUiState) {
    TaskFormContent(
        state = state,
        onBack = {},
        onFieldsChange = {},
        onPreset = { _, _ -> },
        onIconPickerOpen = {},
        onIconPickerDismiss = {},
        onIconSelect = {},
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
