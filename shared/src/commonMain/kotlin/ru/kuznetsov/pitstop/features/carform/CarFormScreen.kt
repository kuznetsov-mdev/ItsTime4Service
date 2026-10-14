package ru.kuznetsov.pitstop.features.carform

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.action_add_custom_task
import pitstop.shared.generated.resources.action_remove_task
import pitstop.shared.generated.resources.action_save_car
import pitstop.shared.generated.resources.car_name_placeholder
import pitstop.shared.generated.resources.date_placeholder
import pitstop.shared.generated.resources.field_brand
import pitstop.shared.generated.resources.field_interval_months
import pitstop.shared.generated.resources.field_last_done_date
import pitstop.shared.generated.resources.field_last_done
import pitstop.shared.generated.resources.field_mileage
import pitstop.shared.generated.resources.field_reminder_window
import pitstop.shared.generated.resources.field_task_interval
import pitstop.shared.generated.resources.field_vin
import pitstop.shared.generated.resources.screen_add_car
import pitstop.shared.generated.resources.section_tasks
import pitstop.shared.generated.resources.task_block_title
import pitstop.shared.generated.resources.task_name_oil
import pitstop.shared.generated.resources.task_name_placeholder
import pitstop.shared.generated.resources.tasks_added
import pitstop.shared.generated.resources.vin_placeholder
import ru.kuznetsov.pitstop.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.ui.components.ScreenPreview
import ru.kuznetsov.pitstop.ui.components.screen.ScreenTitle
import ru.kuznetsov.pitstop.ui.components.screen.ScreenTopBar
import ru.kuznetsov.pitstop.ui.components.buttons.AppIconButton
import ru.kuznetsov.pitstop.ui.components.buttons.PresetChip
import ru.kuznetsov.pitstop.ui.components.buttons.PrimaryButton
import ru.kuznetsov.pitstop.ui.components.fields.AppNumberField
import ru.kuznetsov.pitstop.ui.components.fields.AppTextField
import ru.kuznetsov.pitstop.ui.components.fields.FieldLabel
import ru.kuznetsov.pitstop.ui.components.fields.LabeledMiniField
import ru.kuznetsov.pitstop.ui.format.TaskFields
import ru.kuznetsov.pitstop.ui.format.TaskPreset
import ru.kuznetsov.pitstop.ui.format.chipLabel
import ru.kuznetsov.pitstop.ui.format.taskName
import ru.kuznetsov.pitstop.ui.format.unitGenitive
import ru.kuznetsov.pitstop.ui.format.unitLabel
import ru.kuznetsov.pitstop.ui.icons.PitStopIcons
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

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
        onTaskChange = viewModel::onTaskChange,
        onAddTask = viewModel::onAddTask,
        onAddPreset = viewModel::onAddPreset,
        onRemoveTask = viewModel::onRemoveTask,
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
    onTaskChange: (key: Int, fields: TaskFields) -> Unit,
    onAddTask: () -> Unit,
    onAddPreset: (TaskPreset, String) -> Unit,
    onRemoveTask: (key: Int) -> Unit,
    onSave: (defaultTaskName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PitStopTheme.colors
    val typography = PitStopTheme.typography
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
                        placeholder = stringResource(Res.string.vin_placeholder),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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
                        showErrors = state.showErrors,
                        distanceUnit = state.distanceUnit,
                        onChange = { onTaskChange(task.key, it) },
                        onRemove = { onRemoveTask(task.key) },
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
            }
            AddTaskButton(onClick = onAddTask)
        }
        val defaultTaskName = stringResource(Res.string.task_name_oil)
        PrimaryButton(
            text = stringResource(Res.string.action_save_car),
            onClick = { onSave(defaultTaskName) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 12.dp, bottom = 20.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskBlock(
    number: Int,
    fields: TaskFields,
    showErrors: Boolean,
    distanceUnit: DistanceUnit,
    onChange: (TaskFields) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PitStopTheme.colors
    val shape = RoundedCornerShape(PitStopTheme.dimens.radiusL)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { bringIntoViewRequester.bringIntoView() }
    Column(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.task_block_title, number).uppercase(),
                style = PitStopTheme.typography.mono.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04f.em),
                color = colors.inkMuted,
            )
            AppIconButton(
                icon = PitStopIcons.Trash,
                onClick = onRemove,
                contentDescription = stringResource(Res.string.action_remove_task),
                iconSize = 14.dp,
            )
        }
        AppTextField(
            value = fields.name,
            onValueChange = { onChange(fields.copy(name = it)) },
            placeholder = stringResource(Res.string.task_name_placeholder),
            isError = showErrors && fields.isNameMissing,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            LabeledMiniField(
                label = stringResource(Res.string.field_task_interval, unitLabel(distanceUnit)),
                value = fields.intervalKm,
                onValueChange = { onChange(fields.copy(intervalKm = it)) },
                placeholder = "10000",
                keyboardType = KeyboardType.Number,
                isError = showErrors && fields.isIntervalMissing,
                required = true,
                modifier = Modifier.weight(1f),
            )
            LabeledMiniField(
                label = stringResource(Res.string.field_interval_months),
                value = fields.intervalMonths,
                onValueChange = { onChange(fields.copy(intervalMonths = it)) },
                placeholder = "12",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            LabeledMiniField(
                label = stringResource(Res.string.field_last_done, unitLabel(distanceUnit)),
                value = fields.lastDoneKm,
                onValueChange = { onChange(fields.copy(lastDoneKm = it)) },
                placeholder = "78000",
                keyboardType = KeyboardType.Number,
                isError = showErrors && fields.isLastDoneMissing,
                required = true,
                modifier = Modifier.weight(1f),
            )
            LabeledMiniField(
                label = stringResource(Res.string.field_last_done_date),
                value = fields.lastDoneDate,
                onValueChange = { onChange(fields.copy(lastDoneDate = it)) },
                placeholder = stringResource(Res.string.date_placeholder),
                modifier = Modifier.weight(1f),
            )
        }
        LabeledMiniField(
            label = stringResource(Res.string.field_reminder_window, unitGenitive(distanceUnit)),
            value = fields.reminderWindowKm,
            onValueChange = { onChange(fields.copy(reminderWindowKm = it)) },
            placeholder = "1500",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AddTaskButton(onClick: () -> Unit) {
    val colors = PitStopTheme.colors
    val radius = PitStopTheme.dimens.radiusL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(colors.surface2)
            .drawBehind {
                drawRoundRect(
                    color = colors.rule,
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PitStopIcons.Plus, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
        Text(
            stringResource(Res.string.action_add_custom_task),
            style = PitStopTheme.typography.sans.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
            color = colors.accent,
        )
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
        ),
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
        onTaskChange = { _, _ -> },
        onAddTask = {},
        onAddPreset = { _, _ -> },
        onRemoveTask = {},
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
