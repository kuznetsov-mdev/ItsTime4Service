package ru.kuznetsov.itstime4service.features.carform

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add_task_button
import itstime4service.shared.generated.resources.action_collapse_task
import itstime4service.shared.generated.resources.action_expand_task
import itstime4service.shared.generated.resources.action_remove_task
import itstime4service.shared.generated.resources.action_save_car
import itstime4service.shared.generated.resources.car_name_placeholder
import itstime4service.shared.generated.resources.date_placeholder
import itstime4service.shared.generated.resources.field_brand
import itstime4service.shared.generated.resources.field_interval_months
import itstime4service.shared.generated.resources.field_last_done
import itstime4service.shared.generated.resources.field_last_done_date
import itstime4service.shared.generated.resources.field_mileage
import itstime4service.shared.generated.resources.field_plate
import itstime4service.shared.generated.resources.field_reminder_window
import itstime4service.shared.generated.resources.field_task_interval
import itstime4service.shared.generated.resources.field_vin
import itstime4service.shared.generated.resources.plate_placeholder
import itstime4service.shared.generated.resources.screen_add_car
import itstime4service.shared.generated.resources.section_tasks
import itstime4service.shared.generated.resources.task_block_title
import itstime4service.shared.generated.resources.task_every
import itstime4service.shared.generated.resources.task_interval_missing
import itstime4service.shared.generated.resources.task_last_done
import itstime4service.shared.generated.resources.task_last_done_missing
import itstime4service.shared.generated.resources.task_name_oil
import itstime4service.shared.generated.resources.task_name_placeholder
import itstime4service.shared.generated.resources.tasks_added
import itstime4service.shared.generated.resources.vin_placeholder
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.components.ScreenPreview
import ru.kuznetsov.itstime4service.ui.components.buttons.AppIconButton
import ru.kuznetsov.itstime4service.ui.components.buttons.OutlineButton
import ru.kuznetsov.itstime4service.ui.components.buttons.PresetChip
import ru.kuznetsov.itstime4service.ui.components.buttons.PrimaryButton
import ru.kuznetsov.itstime4service.ui.components.fields.AppNumberField
import ru.kuznetsov.itstime4service.ui.components.fields.AppTextField
import ru.kuznetsov.itstime4service.ui.components.fields.FieldLabel
import ru.kuznetsov.itstime4service.ui.components.fields.LabeledMiniField
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTitle
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTopBar
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import ru.kuznetsov.itstime4service.ui.format.TaskFields
import ru.kuznetsov.itstime4service.ui.format.TaskPreset
import ru.kuznetsov.itstime4service.ui.format.chipLabel
import ru.kuznetsov.itstime4service.ui.format.grouped
import ru.kuznetsov.itstime4service.ui.format.taskName
import ru.kuznetsov.itstime4service.ui.format.unitGenitive
import ru.kuznetsov.itstime4service.ui.format.unitLabel
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
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
    onSave: (defaultTaskName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
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
            FieldLabel(stringResource(Res.string.field_plate))
            AppTextField(
                value = state.plate,
                onValueChange = onPlateChange,
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
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
            }
        }
        val defaultTaskName = stringResource(Res.string.task_name_oil)
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
                onClick = { onSave(defaultTaskName) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskBlock(
    number: Int,
    fields: TaskFields,
    isCollapsed: Boolean,
    showErrors: Boolean,
    distanceUnit: DistanceUnit,
    onChange: (TaskFields) -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusL)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { bringIntoViewRequester.bringIntoView() }
    Column(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, if (isCollapsed && showErrors && !fields.isValid) colors.tierDue else colors.rule, shape)
            .then(if (isCollapsed) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCollapsed) 4.dp else 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCollapsed) {
                Text(
                    fields.name.ifBlank { stringResource(Res.string.task_block_title, number) },
                    style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    stringResource(Res.string.task_block_title, number).uppercase(),
                    style = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04f.em),
                    color = colors.inkMuted,
                    modifier = Modifier.weight(1f),
                )
            }
            AppIconButton(
                icon = ItsTime4ServiceIcons.ChevronDown,
                onClick = onToggle,
                contentDescription = stringResource(if (isCollapsed) Res.string.action_expand_task else Res.string.action_collapse_task),
                iconSize = 14.dp,
                modifier = Modifier.rotate(if (isCollapsed) 0f else 180f),
            )
            AppIconButton(
                icon = ItsTime4ServiceIcons.Trash,
                onClick = onRemove,
                contentDescription = stringResource(Res.string.action_remove_task),
                iconSize = 14.dp,
            )
        }
        if (isCollapsed) {
            Text(
                collapsedSummary(fields, distanceUnit),
                style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            return@Column
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
                keyboardType = KeyboardType.Number,
                visualTransformation = DateInputFormat.current.visualTransformation,
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
private fun collapsedSummary(fields: TaskFields, distanceUnit: DistanceUnit): String {
    val interval = fields.intervalKm.toIntOrNull()?.takeIf { it > 0 }
    val lastDone = fields.lastDoneKm.toIntOrNull()
    val unit = unitGenitive(distanceUnit)
    return listOf(
        interval?.let { stringResource(Res.string.task_every, "${it.grouped()} $unit") }
            ?: stringResource(Res.string.task_interval_missing),
        lastDone?.let { stringResource(Res.string.task_last_done, "${it.grouped()} $unit") }
            ?: stringResource(Res.string.task_last_done_missing),
    ).joinToString(" · ")
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
