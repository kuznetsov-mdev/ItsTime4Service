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
import ru.kuznetsov.itstime4service.domain.usecase.AddTaskUseCase
import ru.kuznetsov.itstime4service.domain.usecase.DeleteTaskUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarByIdUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateTaskUseCase
import ru.kuznetsov.itstime4service.ui.format.TaskFields
import ru.kuznetsov.itstime4service.ui.format.TaskPreset
import ru.kuznetsov.itstime4service.ui.format.toFields

/**
 * Drives the task screen of one car.
 * Without a [taskId] it adds a task: the form starts empty, a preset chip fills the name, the interval
 * and the reminder window, and "add another" stores the task and clears the form for the next one.
 * With a [taskId] it edits that task and can delete it after a confirmation; its service history stays.
 * Saving with an empty required field stores nothing and switches the form to showing errors.
 * Repeated taps on save or "add another" while a task is being stored are ignored.
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
) : ViewModel() {

    private val _state = MutableStateFlow(TaskFormUiState(isEditing = taskId != null))
    val state: StateFlow<TaskFormUiState> = _state.asStateFlow()

    private var original: MaintenanceTask? = null
    private var isSaving = false

    init {
        viewModelScope.launch { load() }
    }

    fun onFieldsChange(fields: TaskFields) = _state.update { it.copy(fields = fields.sanitized()) }

    fun onPreset(preset: TaskPreset, name: String) =
        _state.update { it.copy(fields = preset.toFields(name, it.distanceUnit, it.fields)) }

    fun onSave() = save(addAnother = false)

    fun onSaveAndAddAnother() = save(addAnother = true)

    fun onDeleteRequest() = _state.update { it.copy(isConfirmingDelete = true) }

    fun onDeleteDismiss() = _state.update { it.copy(isConfirmingDelete = false) }

    fun onDeleteConfirm() {
        val id = taskId ?: return
        _state.update { it.copy(isConfirmingDelete = false) }
        viewModelScope.launch {
            deleteTask(carId, id)
            _state.update { it.copy(isClosed = true) }
        }
    }

    private suspend fun load() {
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
            val saved = if (taskId != null) updateTask(carId, taskId, task) else addTask(carId, task)
            isSaving = false
            if (!saved) return@launch
            _state.update {
                if (addAnother) {
                    it.copy(fields = TaskFields(), showErrors = false, formKey = it.formKey + 1)
                } else {
                    it.copy(isClosed = true)
                }
            }
        }
    }
}
