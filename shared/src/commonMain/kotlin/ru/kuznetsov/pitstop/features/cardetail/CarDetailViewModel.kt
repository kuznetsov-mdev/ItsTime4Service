package ru.kuznetsov.pitstop.features.cardetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.kuznetsov.pitstop.domain.model.Car
import ru.kuznetsov.pitstop.domain.usecase.CarAlertsUseCase
import ru.kuznetsov.pitstop.domain.usecase.DeleteCarUseCase
import ru.kuznetsov.pitstop.domain.usecase.GetTodayUseCase
import ru.kuznetsov.pitstop.domain.usecase.MarkTaskDoneUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveCarByIdUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.pitstop.domain.usecase.SortedTaskStatsUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateCarUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateMileageUseCase
import ru.kuznetsov.pitstop.ui.format.keepKm

/**
 * Drives the car screen: the task list sorted by urgency, in-place editing of the name, the VIN and
 * the mileage, marking a task as done and deleting the car after a confirmation.
 * Only one header value is edited at a time and only its check button saves it; [onCancelEdit] drops
 * the edit and the value stays as it was. The mileage is typed in the unit chosen in settings and stored in
 * kilometers; saved unchanged, it keeps its stored value.
 * Marking a task as done first opens a window for the service name, the cost and a comment.
 * Temporary until the storage supports it: the done marks and the values from that window live only in
 * this view model's memory — the window's fields are not written to the service history, and removing
 * a mark only forgets it, without rolling the task back or deleting the history record.
 */
class CarDetailViewModel(
    private val carId: String,
    private val observeCarById: ObserveCarByIdUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val sortedTaskStats: SortedTaskStatsUseCase,
    private val carAlerts: CarAlertsUseCase,
    private val getToday: GetTodayUseCase,
    private val updateCar: UpdateCarUseCase,
    private val updateMileage: UpdateMileageUseCase,
    private val markTaskDone: MarkTaskDoneUseCase,
    private val deleteCar: DeleteCarUseCase,
) : ViewModel() {

    private val overlay = MutableStateFlow(Overlay())

    val state: StateFlow<CarDetailUiState> = combine(
        observeCarById(carId),
        observeSettings(),
        overlay,
    ) { car, settings, overlay ->
        val details = car?.toDetails(overlay.doneMarks)
        CarDetailUiState(
            isLoading = false,
            car = details,
            distanceUnit = settings.distanceUnit,
            editingField = overlay.editingField,
            isConfirmingDelete = overlay.isConfirmingDelete,
            taskDoneDialog = details?.tasks?.firstOrNull { it.id == overlay.doneDialogTaskId },
            isConfirmingUnmark = overlay.unmarkTaskId != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CarDetailUiState())

    fun onEdit(field: EditableField) = overlay.update { it.copy(editingField = field) }

    fun onCancelEdit() = overlay.update { it.copy(editingField = null) }

    fun onSaveBrand(text: String) {
        viewModelScope.launch {
            val car = observeCarById(carId).first() ?: return@launch
            if (updateCar(carId, text, car.vin)) onCancelEdit()
        }
    }

    fun onSaveVin(text: String) {
        viewModelScope.launch {
            val car = observeCarById(carId).first() ?: return@launch
            if (updateCar(carId, car.brand, text)) onCancelEdit()
        }
    }

    fun onSaveMileage(text: String) {
        val mileage = text.trim().toIntOrNull() ?: return
        val unit = state.value.distanceUnit
        viewModelScope.launch {
            val car = observeCarById(carId).first() ?: return@launch
            if (updateMileage(carId, keepKm(car.mileageKm, mileage, unit))) onCancelEdit()
        }
    }

    fun onMarkTaskDone(taskId: String) = overlay.update { it.copy(doneDialogTaskId = taskId) }

    fun onTaskDoneDismiss() = overlay.update { it.copy(doneDialogTaskId = null) }

    @Suppress("UNUSED_PARAMETER")
    fun onTaskDoneConfirm(serviceName: String, cost: String, comment: String) {
        val taskId = overlay.value.doneDialogTaskId ?: return
        overlay.update { it.copy(doneDialogTaskId = null) }
        viewModelScope.launch {
            val car = observeCarById(carId).first() ?: return@launch
            if (markTaskDone(carId, taskId, getToday())) {
                overlay.update { it.copy(doneMarks = it.doneMarks + (taskId to car.mileageKm)) }
            }
        }
    }

    fun onUnmarkRequest(taskId: String) = overlay.update { it.copy(unmarkTaskId = taskId) }

    fun onUnmarkDismiss() = overlay.update { it.copy(unmarkTaskId = null) }

    fun onUnmarkConfirm() = overlay.update {
        it.copy(unmarkTaskId = null, doneMarks = it.doneMarks - it.unmarkTaskId.orEmpty())
    }

    fun onDeleteRequest() = overlay.update { it.copy(isConfirmingDelete = true) }

    fun onDeleteDismiss() = overlay.update { it.copy(isConfirmingDelete = false) }

    fun onDeleteConfirm() {
        overlay.update { it.copy(isConfirmingDelete = false) }
        viewModelScope.launch { deleteCar(carId) }
    }

    private fun Car.toDetails(doneMarks: Map<String, Int>): CarDetails {
        val today = getToday()
        val alerts = carAlerts(this, today)
        return CarDetails(
            brand = brand,
            vin = vin,
            mileageKm = mileageKm,
            alertCount = alerts.total,
            taskCount = alerts.taskCount,
            worstStatus = alerts.worstStatus,
            tasks = sortedTaskStats(this, today).map { (task, stat) ->
                TaskItem(
                    id = task.id,
                    name = task.name,
                    intervalKm = task.intervalKm,
                    remainingKm = stat.remainingKm,
                    remainingMonths = stat.remainingMonths,
                    status = stat.status,
                    progress = stat.progress,
                    isDone = doneMarks[task.id] == mileageKm,
                )
            },
        )
    }

    /**
     * Screen-only state that is not stored anywhere: what is being edited, which windows are open
     * and, until the storage keeps done marks, the mileage each task was marked done at.
     */
    private data class Overlay(
        val editingField: EditableField? = null,
        val isConfirmingDelete: Boolean = false,
        val doneDialogTaskId: String? = null,
        val unmarkTaskId: String? = null,
        val doneMarks: Map<String, Int> = emptyMap(),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
