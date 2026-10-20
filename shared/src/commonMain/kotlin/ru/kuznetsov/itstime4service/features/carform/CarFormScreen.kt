package ru.kuznetsov.itstime4service.features.carform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add_task_button
import itstime4service.shared.generated.resources.action_save_car
import itstime4service.shared.generated.resources.car_name_placeholder
import itstime4service.shared.generated.resources.field_brand
import itstime4service.shared.generated.resources.field_mileage
import itstime4service.shared.generated.resources.field_plate
import itstime4service.shared.generated.resources.field_vin
import itstime4service.shared.generated.resources.plate_placeholder
import itstime4service.shared.generated.resources.screen_add_car
import itstime4service.shared.generated.resources.section_tasks
import itstime4service.shared.generated.resources.task_name_oil
import itstime4service.shared.generated.resources.tasks_added
import itstime4service.shared.generated.resources.vin_placeholder
import ru.kuznetsov.itstime4service.ui.components.ScreenPreview
import ru.kuznetsov.itstime4service.ui.components.buttons.OutlineButton
import ru.kuznetsov.itstime4service.ui.components.buttons.PresetChip
import ru.kuznetsov.itstime4service.ui.components.buttons.PrimaryButton
import ru.kuznetsov.itstime4service.ui.components.fields.AppNumberField
import ru.kuznetsov.itstime4service.ui.components.fields.AppTextField
import ru.kuznetsov.itstime4service.ui.components.fields.FieldLabel
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTitle
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTopBar
import ru.kuznetsov.itstime4service.ui.format.TaskFields
import ru.kuznetsov.itstime4service.ui.format.TaskPreset
import ru.kuznetsov.itstime4service.ui.format.chipLabel
import ru.kuznetsov.itstime4service.ui.format.taskName
import ru.kuznetsov.itstime4service.ui.format.unitLabel
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
fun CarFormScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarFormViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }
    CarFormContent(
        state = state,
        onBack = onBack,
        onBrandChange = viewModel::onBrandChange,
        onMileageChange = viewModel::onMileageChange,
        onVinChange = viewModel::onVinChange,
        onPlateChange = viewModel::onPlateChange,
        onTaskChange = viewModel::onTaskChange,
        onAddTask = viewModel::onAddTask,
        onAddPreset = viewModel::onAddPreset,
        onToggleTask = viewModel::onToggleTask,
        onRemoveTask = viewModel::onRemoveTask,
        onTaskRecordToggle = viewModel::onTaskRecordToggle,
        onSave = viewModel::onSave,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CarFormContent(
    state: CarFormUiState,
    onBack: () -> Unit,
    onBrandChange: (String) -> Unit,
    onMileageChange: (String) -> Unit,
    onVinChange: (String) -> Unit,
    onPlateChange: (String) -> Unit,
    onTaskChange: (key: Int, fields: TaskFields) -> Unit,
    onAddTask: () -> Unit,
    onAddPreset: (TaskPreset, String) -> Unit,
    onToggleTask: (key: Int) -> Unit,
    onRemoveTask: (key: Int) -> Unit,
    onTaskRecordToggle: (key: Int, on: Boolean) -> Unit,
    onSave: (defaultTaskName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    val defaultTaskName = stringResource(Res.string.task_name_oil)
    val save = { onSave(defaultTaskName) }
    Column(modifier.fillMaxSize()) {
        ScreenTopBar(onBack = onBack)
        ScreenTitle(title = stringResource(Res.string.screen_add_car))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding)
                .padding(top = 18.dp, bottom = 8.dp),
        ) {
            FieldLabel(stringResource(Res.string.field_brand), required = true)
            AppTextField(
                value = state.brand,
                onValueChange = onBrandChange,
                onDone = save,
                placeholder = stringResource(Res.string.car_name_placeholder),
                isError = state.showErrors && state.isBrandMissing,
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            )
            Row(
                modifier = Modifier.padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(stringResource(Res.string.field_mileage, unitLabel(state.distanceUnit)), required = true)
                    AppNumberField(
                        value = state.mileageKm,
                        onValueChange = onMileageChange,
                        onDone = save,
                        placeholder = "84300",
                        isError = state.showErrors && state.isMileageMissing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel(stringResource(Res.string.field_vin))
                    AppTextField(
                        value = state.vin,
                        onValueChange = onVinChange,
                        onDone = save,
                        placeholder = stringResource(Res.string.vin_placeholder),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            FieldLabel(stringResource(Res.string.field_plate))
            AppTextField(
                value = state.plate,
                onValueChange = onPlateChange,
                onDone = save,
                placeholder = stringResource(Res.string.plate_placeholder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FieldLabel(stringResource(Res.string.section_tasks), bottomPadding = false)
                Text(
                    pluralStringResource(Res.plurals.tasks_added, state.tasks.size, state.tasks.size),
                    style = typography.mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal),
                    color = colors.inkFaint,
                )
            }
            FlowRow(
                modifier = Modifier.padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TaskPreset.entries.forEach { preset ->
                    val name = stringResource(preset.taskName)
                    PresetChip(label = stringResource(preset.chipLabel), onClick = { onAddPreset(preset, name) })
                }
            }
            state.tasks.forEachIndexed { index, task ->
                key(task.key) {
                    TaskBlock(
                        number = index + 1,
                        fields = task.fields,
                        isCollapsed = task.isCollapsed,
                        showErrors = state.showErrors,
                        distanceUnit = state.distanceUnit,
                        onChange = { onTaskChange(task.key, it) },
                        onToggle = { onToggleTask(task.key) },
                        onRemove = { onRemoveTask(task.key) },
                        onRecordToggle = { onTaskRecordToggle(task.key, it) },
                        onDone = save,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlineButton(
                text = stringResource(Res.string.action_add_task_button),
                onClick = onAddTask,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            PrimaryButton(
                text = stringResource(Res.string.action_save_car),
                onClick = save,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

private val previewState = CarFormUiState(
    brand = "Kia Rio",
    mileageKm = "84300",
    vin = "XWEPC811BB0012345",
    tasks = listOf(
        TaskBlockState(
            key = 0,
            fields = TaskFields(
                name = "Замена масла и фильтра",
                intervalKm = "10000",
                intervalMonths = "12",
                lastDoneKm = "78000",
                reminderWindowKm = "1500",
            ),
            isCollapsed = true,
        ),
        TaskBlockState(key = 1, fields = TaskFields(name = "Воздушный фильтр", intervalKm = "15000", lastDoneKm = "70000")),
    ),
)

@Composable
private fun CarFormPreviewContent() {
    CarFormContent(
        state = previewState,
        onBack = {},
        onBrandChange = {},
        onMileageChange = {},
        onVinChange = {},
        onPlateChange = {},
        onTaskChange = { _, _ -> },
        onAddTask = {},
        onAddPreset = { _, _ -> },
        onToggleTask = {},
        onRemoveTask = {},
        onTaskRecordToggle = { _, _ -> },
        onSave = {},
    )
}

@Preview
@Composable
private fun CarFormContentLightPreview() = ScreenPreview(darkTheme = false) {
    CarFormPreviewContent()
}

@Preview
@Composable
private fun CarFormContentDarkPreview() = ScreenPreview(darkTheme = true) {
    CarFormPreviewContent()
}
