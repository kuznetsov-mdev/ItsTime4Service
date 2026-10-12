package ru.kuznetsov.pitstop.features.cardetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.action_cancel
import pitstop.shared.generated.resources.action_delete
import pitstop.shared.generated.resources.action_delete_car
import pitstop.shared.generated.resources.action_edit_car
import pitstop.shared.generated.resources.action_edit_mileage
import pitstop.shared.generated.resources.action_mark_done
import pitstop.shared.generated.resources.count_of_total
import pitstop.shared.generated.resources.dialog_delete_car_text
import pitstop.shared.generated.resources.dialog_delete_car_title
import pitstop.shared.generated.resources.screen_history
import pitstop.shared.generated.resources.stat_mileage
import pitstop.shared.generated.resources.stat_soon_overdue
import pitstop.shared.generated.resources.task_every
import pitstop.shared.generated.resources.task_or_months
import pitstop.shared.generated.resources.task_overdue_by
import pitstop.shared.generated.resources.task_remaining
import pitstop.shared.generated.resources.task_time_expired
import pitstop.shared.generated.resources.tasks_empty
import pitstop.shared.generated.resources.vin_label
import pitstop.shared.generated.resources.vin_missing
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.TaskStatus
import ru.kuznetsov.pitstop.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.pitstop.ui.components.ScreenPreview
import ru.kuznetsov.pitstop.ui.components.screen.ScreenTitle
import ru.kuznetsov.pitstop.ui.components.screen.ScreenTopBar
import ru.kuznetsov.pitstop.ui.format.distanceText
import ru.kuznetsov.pitstop.ui.format.grouped
import ru.kuznetsov.pitstop.ui.format.kmIn
import ru.kuznetsov.pitstop.ui.format.toTier
import ru.kuznetsov.pitstop.ui.components.buttons.AppIconButton
import ru.kuznetsov.pitstop.ui.components.cards.StatCard
import ru.kuznetsov.pitstop.ui.components.dialogs.ConfirmDialog
import ru.kuznetsov.pitstop.ui.components.fields.InlineEditField
import ru.kuznetsov.pitstop.ui.components.indicators.ProgressBar
import ru.kuznetsov.pitstop.ui.components.indicators.StatusChip
import ru.kuznetsov.pitstop.ui.components.indicators.color
import ru.kuznetsov.pitstop.ui.icons.PitStopIcons
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

@Composable
fun CarDetailScreen(
    carId: String,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onEditCar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarDetailViewModel = koinViewModel { parametersOf(carId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isMissing) {
        if (state.isMissing) onBack()
    }
    CarDetailContent(
        state = state,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        onEditCar = onEditCar,
        onEditMileage = viewModel::onEditMileage,
        onSaveMileage = viewModel::onSaveMileage,
        onMarkTaskDone = viewModel::onMarkTaskDone,
        onDeleteRequest = viewModel::onDeleteRequest,
        onDeleteDismiss = viewModel::onDeleteDismiss,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        modifier = modifier,
    )
}

@Composable
internal fun CarDetailContent(
    state: CarDetailUiState,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onEditCar: () -> Unit,
    onEditMileage: () -> Unit,
    onSaveMileage: (String) -> Unit,
    onMarkTaskDone: (taskId: String) -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val car = state.car
    Column(modifier.fillMaxSize()) {
        ScreenTopBar(onBack = onBack) {
            AppIconButton(
                icon = PitStopIcons.Clock,
                onClick = onOpenHistory,
                contentDescription = stringResource(Res.string.screen_history),
            )
            AppIconButton(
                icon = PitStopIcons.Pencil,
                onClick = onEditCar,
                contentDescription = stringResource(Res.string.action_edit_car),
            )
            AppIconButton(
                icon = PitStopIcons.Trash,
                onClick = onDeleteRequest,
                contentDescription = stringResource(Res.string.action_delete_car),
            )
        }
        if (car != null) {
            ScreenTitle(
                title = car.brand,
                subtitle = stringResource(Res.string.vin_label, car.vin ?: stringResource(Res.string.vin_missing)),
                monoSubtitle = true,
            )
            StatPair(
                car = car,
                distanceUnit = state.distanceUnit,
                isEditingMileage = state.isEditingMileage,
                onEditMileage = onEditMileage,
                onSaveMileage = onSaveMileage,
            )
            if (car.tasks.isEmpty()) {
                EmptyTasks(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = ScreenHorizontalPadding,
                        end = ScreenHorizontalPadding,
                        top = 6.dp,
                        bottom = 18.dp,
                    ),
                ) {
                    itemsIndexed(car.tasks, key = { _, task -> task.id }) { index, task ->
                        if (index > 0) Divider()
                        TaskRow(
                            task = task,
                            distanceUnit = state.distanceUnit,
                            onMarkDone = { onMarkTaskDone(task.id) },
                        )
                    }
                }
            }
        }
    }
    if (car != null && state.isConfirmingDelete) {
        ConfirmDialog(
            title = stringResource(Res.string.dialog_delete_car_title, car.brand),
            text = stringResource(Res.string.dialog_delete_car_text),
            confirmLabel = stringResource(Res.string.action_delete),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }
}

@Composable
private fun StatPair(
    car: CarDetails,
    distanceUnit: DistanceUnit,
    isEditingMileage: Boolean,
    onEditMileage: () -> Unit,
    onSaveMileage: (String) -> Unit,
) {
    val colors = PitStopTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 16.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard(
            label = stringResource(Res.string.stat_mileage),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            if (isEditingMileage) {
                InlineEditField(initialValue = car.mileageKm.toString(), onSave = onSaveMileage)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(PitStopIcons.Gauge, contentDescription = null, tint = colors.ink, modifier = Modifier.size(16.dp))
                        Text(
                            car.mileageKm.kmIn(distanceUnit).grouped(),
                            style = PitStopTheme.typography.display.copy(fontSize = 22.sp, lineHeight = 28.sp),
                            color = colors.ink,
                            maxLines = 1,
                        )
                    }
                    AppIconButton(
                        icon = PitStopIcons.Pencil,
                        onClick = onEditMileage,
                        contentDescription = stringResource(Res.string.action_edit_mileage),
                        size = 23.dp,
                        iconSize = 11.dp,
                        tint = colors.inkFaint,
                    )
                }
            }
        }
        StatCard(
            label = stringResource(Res.string.stat_soon_overdue),
            value = stringResource(Res.string.count_of_total, car.alertCount, car.taskCount),
            valueColor = car.worstStatus.toTier().color,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    distanceUnit: DistanceUnit,
    onMarkDone: () -> Unit,
) {
    val colors = PitStopTheme.colors
    val typography = PitStopTheme.typography
    val tier = task.status.toTier()
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(PitStopTheme.dimens.radiusM))
                    .background(colors.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(taskIcon(task.name), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    task.name,
                    style = typography.sans.copy(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    taskMetaText(task, distanceUnit),
                    style = typography.mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
                    color = colors.inkFaint,
                )
            }
            StatusChip(status = tier)
        }
        ProgressBar(value = task.progress, tier = tier, modifier = Modifier.padding(top = 9.dp))
        Row(
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(PitStopTheme.dimens.radiusS))
                .clickable(onClick = onMarkDone)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PitStopIcons.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(12.dp))
            Text(
                stringResource(Res.string.action_mark_done),
                style = typography.sans.copy(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold),
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun taskMetaText(task: TaskItem, distanceUnit: DistanceUnit): String {
    val parts = mutableListOf<String>()
    parts += if (task.remainingKm <= 0) {
        stringResource(Res.string.task_overdue_by, distanceText(-task.remainingKm, distanceUnit))
    } else {
        stringResource(Res.string.task_remaining, distanceText(task.remainingKm, distanceUnit))
    }
    val remainingMonths = task.remainingMonths
    if (remainingMonths != null) {
        parts += if (remainingMonths <= 0) {
            stringResource(Res.string.task_time_expired)
        } else {
            stringResource(Res.string.task_or_months, remainingMonths)
        }
    }
    parts += stringResource(Res.string.task_every, distanceText(task.intervalKm, distanceUnit))
    return parts.joinToString(" · ")
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(PitStopTheme.colors.rule))
}

@Composable
private fun EmptyTasks(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Res.string.tasks_empty),
            style = PitStopTheme.typography.sans.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = PitStopTheme.colors.inkFaint,
            textAlign = TextAlign.Center,
        )
    }
}

private val previewState = CarDetailUiState(
    isLoading = false,
    car = CarDetails(
        brand = "Kia Rio",
        vin = "XWEPC811BB0012345",
        mileageKm = 84_300,
        alertCount = 1,
        taskCount = 3,
        worstStatus = TaskStatus.SOON,
        tasks = listOf(
            TaskItem("t3", "Воздушный фильтр", 15_000, 700, null, TaskStatus.SOON, 0.95f),
            TaskItem("t1", "Замена масла и фильтра", 10_000, 3_700, 4, TaskStatus.OK, 0.63f),
            TaskItem("t4", "Ремень ГРМ", 60_000, 5_700, null, TaskStatus.OK, 0.9f),
        ),
    ),
)

@Composable
private fun CarDetailPreviewContent() {
    CarDetailContent(
        state = previewState,
        onBack = {},
        onOpenHistory = {},
        onEditCar = {},
        onEditMileage = {},
        onSaveMileage = {},
        onMarkTaskDone = {},
        onDeleteRequest = {},
        onDeleteDismiss = {},
        onDeleteConfirm = {},
    )
}

@Preview
@Composable
private fun CarDetailContentLightPreview() = ScreenPreview(darkTheme = false) {
    CarDetailPreviewContent()
}

@Preview
@Composable
private fun CarDetailContentDarkPreview() = ScreenPreview(darkTheme = true) {
    CarDetailPreviewContent()
}
