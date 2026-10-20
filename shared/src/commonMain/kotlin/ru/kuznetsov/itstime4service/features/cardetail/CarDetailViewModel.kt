package ru.kuznetsov.itstime4service.features.cardetail

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.usecase.CarAlertsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.DeleteCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.DetectPlateStyleUseCase
import ru.kuznetsov.itstime4service.domain.usecase.DoneMarksUseCase
import ru.kuznetsov.itstime4service.domain.usecase.GetTodayUseCase
import ru.kuznetsov.itstime4service.domain.usecase.MarkTaskDoneUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarByIdUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveHistoryUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.RecordCarOpenedUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SetPlateHiddenUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SetVinHiddenUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SortedTaskStatsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UnmarkTaskDoneUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateMileageUseCase
import ru.kuznetsov.itstime4service.ui.format.deviceRegion
import ru.kuznetsov.itstime4service.ui.format.keepKm
import ru.kuznetsov.itstime4service.ui.format.kmIn
import ru.kuznetsov.itstime4service.ui.format.toLook
import ru.kuznetsov.itstime4service.ui.icons.taskIcon

/**
 * Drives the car screen: the task list sorted by urgency, in-place editing of the name, the licence plate, the VIN
 * and the mileage, marking a task as done and deleting the car after a confirmation.
 * Only one header value is edited at a time and only its check button saves it; [onCancelEdit] drops
 * the edit and the value stays as it was. The mileage is typed in the unit chosen in settings and stored in
 * kilometers; saved unchanged, it keeps its stored value.
 * The mileage can also be stepped up and down by buttons: 100 units of the chosen distance unit times the chosen
 * multiplier; every step is stored right away and the mileage never drops to zero or below. Steps and a mileage
 * typed by hand are applied one after another, each to the mileage as stored at that moment.
 * Marking a task as done first opens a window for the service name, the cost and a comment, which go into
 * the service history record. A task shows as done while its mark holds (see [DoneMarksUseCase]); removing
 * the mark after a confirmation rolls the task back and deletes the record.
 * Opening the screen remembers the car as the last opened one, which the app may start on next time.
 * The licence plate is drawn after its format, with the device region as a tie-breaker (see [DetectPlateStyleUseCase]).
 * The plate and the VIN can be hidden and shown again; the choice is stored with the car, and a hidden one is not edited.
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
    private val unmarkTaskDone: UnmarkTaskDoneUseCase,
    observeHistory: ObserveHistoryUseCase,
    private val doneMarks: DoneMarksUseCase,
    recordCarOpened: RecordCarOpenedUseCase,
    private val detectPlateStyle: DetectPlateStyleUseCase,
    private val deleteCar: DeleteCarUseCase,
    private val setPlateHidden: SetPlateHiddenUseCase,
    private val setVinHidden: SetVinHiddenUseCase,
) : ViewModel() {

    private val overlay = MutableStateFlow(Overlay())
    private val mileageMutex = Mutex()

    init {
        viewModelScope.launch { recordCarOpened(carId) }
    }

    val state: StateFlow<CarDetailUiState> = combine(
        observeCarById(carId),
        observeHistory(carId),
        observeSettings(),
        overlay,
    ) { car, history, settings, overlay ->
        val details = car?.toDetails(doneMarks(car, history).keys)
        CarDetailUiState(
            isLoading = false,
            car = details,
            distanceUnit = settings.distanceUnit,
            editingField = overlay.editingField,
            isConfirmingDelete = overlay.isConfirmingDelete,
            taskDoneDialog = details?.tasks?.firstOrNull { it.id == overlay.doneDialogTaskId },
            isConfirmingUnmark = overlay.unmarkTaskId != null,
            mileageMultiplier = overlay.mileageMultiplier,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CarDetailUiState())

    fun onEdit(field: EditableField) {
        val car = state.value.car ?: return
        val hidden = (field == EditableField.PLATE && car.plateHidden) || (field == EditableField.VIN && car.vinHidden)
        if (!hidden) overlay.update { it.copy(editingField = field) }
    }

    fun onPlateHiddenToggle() {
        val car = state.value.car ?: return
        onCancelEdit()
        viewModelScope.launch { setPlateHidden(carId, !car.plateHidden) }
    }

    fun onVinHiddenToggle() {
        val car = state.value.car ?: return
        onCancelEdit()
        viewModelScope.launch { setVinHidden(carId, !car.vinHidden) }
    }

    fun onCancelEdit() = overlay.update { it.copy(editingField = null) }

    fun onSaveBrand(text: String) {
        viewModelScope.launch {
            val car = observeCarById(carId).first() ?: return@launch
            if (updateCar(carId, text, car.vin, car.plate)) onCancelEdit()
        }
    }

    fun onSaveVin(text: String) {
        viewModelScope.launch {
            val car = observeCarById(carId).first() ?: return@launch
            if (updateCar(carId, car.brand, text, car.plate)) onCancelEdit()
        }
    }

    fun onSavePlate(text: String) {
        viewModelScope.launch {
            val car = observeCarById(carId).first() ?: return@launch
            if (updateCar(carId, car.brand, car.vin, text)) onCancelEdit()
        }
    }

    fun onSaveMileage(text: String) {
        val mileage = text.trim().toIntOrNull() ?: return
        val unit = state.value.distanceUnit
        viewModelScope.launch {
            mileageMutex.withLock {
                val car = observeCarById(carId).first() ?: return@withLock
                if (updateMileage(carId, keepKm(car.mileageKm, mileage, unit))) onCancelEdit()
            }
        }
    }

    fun onMultiplierSelect(multiplier: Int) = overlay.update { it.copy(mileageMultiplier = multiplier) }

    fun onMileageStep(direction: Int) {
        val unit = state.value.distanceUnit
        val step = direction * overlay.value.mileageMultiplier * CarDetailUiState.MILEAGE_STEP
        overlay.update { it.copy(editingField = null) }
        viewModelScope.launch {
            mileageMutex.withLock {
                val car = observeCarById(carId).first() ?: return@withLock
                val mileage = car.mileageKm.kmIn(unit) + step
                if (mileage > 0) updateMileage(carId, keepKm(car.mileageKm, mileage, unit))
            }
        }
    }

    fun onMarkTaskDone(taskId: String) = overlay.update { it.copy(doneDialogTaskId = taskId) }

    fun onTaskDoneDismiss() = overlay.update { it.copy(doneDialogTaskId = null) }

    fun onTaskDoneConfirm(serviceName: String, cost: String, comment: String) {
        val taskId = overlay.value.doneDialogTaskId ?: return
        overlay.update { it.copy(doneDialogTaskId = null) }
        viewModelScope.launch {
            mileageMutex.withLock { markTaskDone(carId, taskId, getToday(), serviceName, cost.toIntOrNull(), comment) }
        }
    }

    fun onUnmarkRequest(taskId: String) = overlay.update { it.copy(unmarkTaskId = taskId) }

    fun onUnmarkDismiss() = overlay.update { it.copy(unmarkTaskId = null) }

    fun onUnmarkConfirm() {
        val taskId = overlay.value.unmarkTaskId ?: return
        overlay.update { it.copy(unmarkTaskId = null) }
        viewModelScope.launch {
            mileageMutex.withLock { unmarkTaskDone(carId, taskId) }
        }
    }

    fun onDeleteRequest() = overlay.update { it.copy(isConfirmingDelete = true) }

    fun onDeleteDismiss() = overlay.update { it.copy(isConfirmingDelete = false) }

    fun onDeleteConfirm() {
        overlay.update { it.copy(isConfirmingDelete = false) }
        viewModelScope.launch { deleteCar(carId) }
    }

    private fun Car.toDetails(doneTaskIds: Set<String>): CarDetails {
        val today = getToday()
        val alerts = carAlerts(this, today)
        return CarDetails(
            brand = brand,
            vin = vin,
            plate = plate,
            plateLook = plate?.let { detectPlateStyle(it, deviceRegion()).toLook(it) },
            plateHidden = plateHidden,
            vinHidden = vinHidden,
            mileageKm = mileageKm,
            dueCount = alerts.due,
            soonCount = alerts.soon,
            taskCount = alerts.taskCount,
            tasks = sortedTaskStats(this, today).map { (task, stat) ->
                TaskItem(
                    id = task.id,
                    name = task.name,
                    intervalKm = task.intervalKm,
                    remainingKm = stat.remainingKm,
                    remainingMonths = stat.remainingMonths,
                    status = stat.status,
                    progress = stat.progress,
                    isDone = task.id in doneTaskIds,
                    icon = taskIcon(task.icon, task.name),
                )
            },
        )
    }

    /**
     * Screen-only state that is not stored anywhere: what is being edited, which windows are open
     * and the chosen mileage step multiplier.
     */
    private data class Overlay(
        val editingField: EditableField? = null,
        val isConfirmingDelete: Boolean = false,
        val doneDialogTaskId: String? = null,
        val unmarkTaskId: String? = null,
        val mileageMultiplier: Int = 1,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
