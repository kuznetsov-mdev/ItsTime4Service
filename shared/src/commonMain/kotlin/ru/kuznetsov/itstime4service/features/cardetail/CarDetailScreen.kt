package ru.kuznetsov.itstime4service.features.cardetail

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add_task
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_delete
import itstime4service.shared.generated.resources.action_delete_car
import itstime4service.shared.generated.resources.action_ok
import itstime4service.shared.generated.resources.dialog_delete_car_text
import itstime4service.shared.generated.resources.dialog_delete_car_title
import itstime4service.shared.generated.resources.dialog_unmark_text
import itstime4service.shared.generated.resources.dialog_unmark_title
import itstime4service.shared.generated.resources.screen_history
import itstime4service.shared.generated.resources.tasks_empty
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.ui.components.ScreenPreview
import ru.kuznetsov.itstime4service.ui.components.buttons.AppIconButton
import ru.kuznetsov.itstime4service.ui.components.buttons.Fab
import ru.kuznetsov.itstime4service.ui.components.dialogs.ConfirmDialog
import ru.kuznetsov.itstime4service.ui.components.dialogs.TaskDoneDialog
import ru.kuznetsov.itstime4service.ui.components.indicators.PlateLook
import ru.kuznetsov.itstime4service.ui.components.indicators.color
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTopBar
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
fun CarDetailScreen(
    carId: String,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (taskId: String) -> Unit,
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
        onAddTask = onAddTask,
        onOpenTask = onOpenTask,
        onEdit = viewModel::onEdit,
        onCancelEdit = viewModel::onCancelEdit,
        onSaveBrand = viewModel::onSaveBrand,
        onSaveVin = viewModel::onSaveVin,
        onSavePlate = viewModel::onSavePlate,
        onPlateHiddenToggle = viewModel::onPlateHiddenToggle,
        onVinHiddenToggle = viewModel::onVinHiddenToggle,
        onSaveMileage = viewModel::onSaveMileage,
        onMileageStep = viewModel::onMileageStep,
        onMultiplierSelect = viewModel::onMultiplierSelect,
        onMarkTaskDone = viewModel::onMarkTaskDone,
        onTaskDoneConfirm = viewModel::onTaskDoneConfirm,
        onTaskDoneDismiss = viewModel::onTaskDoneDismiss,
        onUnmarkRequest = viewModel::onUnmarkRequest,
        onUnmarkConfirm = viewModel::onUnmarkConfirm,
        onUnmarkDismiss = viewModel::onUnmarkDismiss,
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
    onAddTask: () -> Unit,
    onOpenTask: (taskId: String) -> Unit,
    onEdit: (EditableField) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveBrand: (String) -> Unit,
    onSaveVin: (String) -> Unit,
    onSavePlate: (String) -> Unit,
    onPlateHiddenToggle: () -> Unit,
    onVinHiddenToggle: () -> Unit,
    onSaveMileage: (String) -> Unit,
    onMileageStep: (direction: Int) -> Unit,
    onMultiplierSelect: (Int) -> Unit,
    onMarkTaskDone: (taskId: String) -> Unit,
    onTaskDoneConfirm: (serviceName: String, cost: String, comment: String) -> Unit,
    onTaskDoneDismiss: () -> Unit,
    onUnmarkRequest: (taskId: String) -> Unit,
    onUnmarkConfirm: () -> Unit,
    onUnmarkDismiss: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val car = state.car
    val taskListState = rememberLazyListState()
    val mileageKm = car?.mileageKm
    LaunchedEffect(mileageKm) {
        if (mileageKm != null) taskListState.animateScrollToItem(0)
    }
    val layout = remember { EditLayout() }
    val editBounds = Modifier.onGloballyPositioned { layout.edit = it }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { layout.root = it }
            .pointerInput(state.editingField) {
                if (state.editingField == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!layout.isInsideEdit(down.position)) onCancelEdit()
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(onBack = onBack) {
                AppIconButton(
                    icon = ItsTime4ServiceIcons.Clock,
                    onClick = onOpenHistory,
                    contentDescription = stringResource(Res.string.screen_history),
                )
                AppIconButton(
                    icon = ItsTime4ServiceIcons.Trash,
                    onClick = onDeleteRequest,
                    contentDescription = stringResource(Res.string.action_delete_car),
                )
            }
            if (car != null) {
                CarHeader(
                    car = car,
                    editingField = state.editingField,
                    editBounds = editBounds,
                    onEdit = onEdit,
                    onSaveBrand = onSaveBrand,
                    onSaveVin = onSaveVin,
                    onSavePlate = onSavePlate,
                    onPlateHiddenToggle = onPlateHiddenToggle,
                    onVinHiddenToggle = onVinHiddenToggle,
                )
                MileageCard(
                    car = car,
                    distanceUnit = state.distanceUnit,
                    multiplier = state.mileageMultiplier,
                    isEditing = state.editingField == EditableField.MILEAGE,
                    editBounds = editBounds,
                    onEdit = { onEdit(EditableField.MILEAGE) },
                    onSave = onSaveMileage,
                    onStep = onMileageStep,
                    onMultiplierSelect = onMultiplierSelect,
                    modifier = Modifier.padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 14.dp, bottom = 6.dp),
                )
                if (car.tasks.isEmpty()) {
                    EmptyTasks(Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = taskListState,
                        contentPadding = PaddingValues(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 4.dp, bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(car.tasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                distanceUnit = state.distanceUnit,
                                onOpen = { onOpenTask(task.id) },
                                onMarkDone = { if (task.isDone) onUnmarkRequest(task.id) else onMarkTaskDone(task.id) },
                            )
                        }
                    }
                }
            }
        }
        if (car != null) {
            Fab(
                icon = ItsTime4ServiceIcons.Plus,
                onClick = onAddTask,
                contentDescription = stringResource(Res.string.action_add_task),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = ScreenHorizontalPadding, bottom = 22.dp),
            )
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
    val doneTask = state.taskDoneDialog
    if (doneTask != null) {
        TaskDoneDialog(taskName = doneTask.name, onConfirm = onTaskDoneConfirm, onDismiss = onTaskDoneDismiss)
    }
    if (state.isConfirmingUnmark) {
        ConfirmDialog(
            title = stringResource(Res.string.dialog_unmark_title),
            text = stringResource(Res.string.dialog_unmark_text),
            confirmLabel = stringResource(Res.string.action_ok),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = onUnmarkConfirm,
            onDismiss = onUnmarkDismiss,
            icon = ItsTime4ServiceIcons.Check,
            destructive = false,
        )
    }
}

/**
 * Layout coordinates of the screen and of the field that is edited in place right now.
 * Lets a touch anywhere outside that field end the edit.
 */
private class EditLayout {
    var root: LayoutCoordinates? = null
    var edit: LayoutCoordinates? = null

    fun isInsideEdit(position: Offset): Boolean {
        val rootCoordinates = root ?: return false
        val editCoordinates = edit?.takeIf { it.isAttached } ?: return false
        return rootCoordinates.localBoundingBoxOf(editCoordinates).contains(position)
    }
}

@Composable
private fun EmptyTasks(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Res.string.tasks_empty),
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = ItsTime4ServiceTheme.colors.inkFaint,
            textAlign = TextAlign.Center,
        )
    }
}

private val previewState = CarDetailUiState(
    isLoading = false,
    car = CarDetails(
        brand = "Kia Rio",
        vin = "XWEPC811BB0012345",
        plate = "А123ВС77",
        plateLook = PlateLook.Russian("А 123 ВС", "77", "А123ВС77"),
        mileageKm = 84_300,
        dueCount = 0,
        soonCount = 1,
        taskCount = 3,
        tasks = listOf(
            TaskItem("t3", "Воздушный фильтр", 15_000, 700, null, TaskStatus.SOON, 0.95f, icon = TaskIcon.FILTER),
            TaskItem("t1", "Замена масла и фильтра", 10_000, 3_700, 4, TaskStatus.OK, 0.63f, icon = TaskIcon.DROPLET),
            TaskItem("t4", "Ремень ГРМ", 60_000, 5_700, null, TaskStatus.OK, 0.9f, isDone = true),
        ),
    ),
)

@Composable
private fun CarDetailPreviewContent() {
    CarDetailContent(
        state = previewState,
        onBack = {},
        onOpenHistory = {},
        onAddTask = {},
        onOpenTask = {},
        onEdit = {},
        onCancelEdit = {},
        onSaveBrand = {},
        onSaveVin = {},
        onSavePlate = {},
        onPlateHiddenToggle = {},
        onVinHiddenToggle = {},
        onSaveMileage = {},
        onMileageStep = {},
        onMultiplierSelect = {},
        onMarkTaskDone = {},
        onTaskDoneConfirm = { _, _, _ -> },
        onTaskDoneDismiss = {},
        onUnmarkRequest = {},
        onUnmarkConfirm = {},
        onUnmarkDismiss = {},
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
