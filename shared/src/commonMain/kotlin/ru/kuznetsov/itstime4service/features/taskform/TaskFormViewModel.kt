package ru.kuznetsov.itstime4service.features.taskform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import ru.kuznetsov.itstime4service.domain.usecase.AddTaskUseCase
import ru.kuznetsov.itstime4service.domain.usecase.DeleteTaskUseCase
import ru.kuznetsov.itstime4service.domain.usecase.GetTodayUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarByIdUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateTaskUseCase
import ru.kuznetsov.itstime4service.ui.error.attempt
import ru.kuznetsov.itstime4service.ui.format.TaskFields
import ru.kuznetsov.itstime4service.ui.format.TaskPreset
import ru.kuznetsov.itstime4service.ui.format.toFields

/**
 * Drives the task screen of one car.
 * Without a [taskId] it adds a task: the form starts empty, a preset chip fills the name, the interval
 * and the reminder window, and "add another" stores the task and clears the form for the next one.
 * With a [taskId] it edits that task and can delete it after a confirmation; its service history stays.
 * Saving with an empty required field stores nothing and switches the form to showing errors.
 * The icon is picked in a window; a task saved before icons could be picked shows one guessed from its name and
 * stays without a stored icon until the user picks one.
 * A new task can be stored together with a history record of its last service; turning that on fills an empty
 * last-done date with today.
 * Repeated taps on save or "add another" while a task is being stored are ignored.
 * A failed save or deletion keeps the form as it was and shows the "could not save" bar until [onSaveFailureShown];
 * save works again right away. A failed read of the car shows the load error until [onRetry] reads it again.
 * Distances are typed in the unit chosen in settings and stored in kilometers; a distance left as it was
 * keeps its stored value.
 */
class TaskFormViewModel(
    private val carId: String,
    private val taskId: String?,
    private val observeCarById: ObserveCarByIdUseCase,
    private val addTask: AddTaskUseCase,
    private val updateTask: UpdateTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val getToday: GetTodayUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskFormUiState(isEditing = taskId != null))
    val state: StateFlow<TaskFormUiState> = _state.asStateFlow()

    private var original: MaintenanceTask? = null
    private var isSaving = false

    init {
        load()
    }

    fun onFieldsChange(fields: TaskFields) = _state.update { it.copy(fields = fields.sanitized()) }

    fun onPreset(preset: TaskPreset, name: String) =
        _state.update { it.copy(fields = preset.toFields(name, it.distanceUnit, it.fields)) }

    fun onSave() = save(addAnother = false)

    fun onSaveAndAddAnother() = save(addAnother = true)

    fun onRecordToggle(on: Boolean) = _state.update { it.copy(fields = it.fields.withRecord(on, getToday())) }

    fun onIconPickerOpen() = _state.update { it.copy(isPickingIcon = true) }

    fun onIconPickerDismiss() = _state.update { it.copy(isPickingIcon = false) }

    fun onIconSelect(icon: TaskIcon) = _state.update { it.copy(fields = it.fields.copy(icon = icon), isPickingIcon = false) }

    fun onDeleteRequest() = _state.update { it.copy(isConfirmingDelete = true) }

    fun onDeleteDismiss() = _state.update { it.copy(isConfirmingDelete = false) }

    fun onDeleteConfirm() {
        val id = taskId ?: return
        _state.update { it.copy(isConfirmingDelete = false) }
        viewModelScope.launch {
            attempt { deleteTask(carId, id) }
                .onSuccess { _state.update { it.copy(isClosed = true) } }
                .onFailure { _state.update { it.copy(saveFailed = true) } }
        }
    }

    fun onSaveFailureShown() = _state.update { it.copy(saveFailed = false) }

    fun onRetry() {
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            attempt { read() }.onFailure { _state.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }

    private suspend fun read() {
        val car = observeCarById(carId).first()
        val unit = observeSettings().first().distanceUnit
        val task = taskId?.let { id -> car?.tasks?.firstOrNull { it.id == id } }
        original = task
        if (car == null || (taskId != null && task == null)) {
            _state.update { it.copy(isLoading = false, isClosed = true) }
            return
        }
        _state.update {
            it.copy(isLoading = false, carBrand = car.brand, fields = task?.toFields(unit) ?: TaskFields(), distanceUnit = unit)
        }
    }

    private fun save(addAnother: Boolean) {
        val form = _state.value
        val task = form.fields.toNewTask(form.distanceUnit, original)
        if (form.isLoading) return
        if (task == null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            val saved = attempt { if (taskId != null) updateTask(carId, taskId, task) else addTask(carId, task) }
                .getOrElse {
                    isSaving = false
                    _state.update { it.copy(saveFailed = true) }
                    return@launch
                }
            if (!saved) {
                isSaving = false
                return@launch
            }
            _state.update {
                if (addAnother) {
                    it.copy(fields = TaskFields(), showErrors = false, formKey = it.formKey + 1)
                } else {
                    it.copy(isClosed = true)
                }
            }
            if (addAnother) isSaving = false
        }
    }
}
