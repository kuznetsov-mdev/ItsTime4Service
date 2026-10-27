package ru.kuznetsov.itstime4service.features.carform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.usecase.AddCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.GetTodayUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.ui.error.attempt
import ru.kuznetsov.itstime4service.ui.format.TaskFields
import ru.kuznetsov.itstime4service.ui.format.TaskPreset
import ru.kuznetsov.itstime4service.ui.format.kmFrom

/**
 * Drives the add-car form: the car fields and a list of task blocks filled by hand or from presets.
 * The form opens without task blocks; one appears on the add-task button or a preset chip, always expanded.
 * A block can be collapsed to two lines and expanded back. The add-task button adds a block only while every block is
 * filled; otherwise it outlines the empty required fields of the unfinished blocks, without expanding collapsed ones,
 * and scrolls to the first of them. A preset chip fills the topmost block whose name and interval are both empty,
 * keeping what else was typed there, and adds a new block only when there is no such block.
 * Saving with an empty required field — in the car or in any task block — stores nothing, switches
 * the form to showing errors and expands every collapsed block with such a field; an unwanted block has to be removed, it is not skipped silently.
 * When the form has no blocks at all, the car gets a default oil-change task counted from its current
 * mileage, as in the design mockup. Repeated taps on save while the car is being stored are ignored. Distances are typed in the unit chosen in settings and stored in kilometers.
 * A task block can be stored together with a history record of the task's last service; turning that on fills an
 * empty last-done date with today.
 * The licence plate is optional and typed in upper case, up to 12 characters.
 * A failed save keeps the form as it was and shows the "could not save" bar until [onSaveFailureShown]; save works
 * again right away. A failed read of the distance unit shows the load error until [onRetry] reads it again.
 */
class CarFormViewModel(
    private val addCar: AddCarUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val getToday: GetTodayUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CarFormUiState())
    val state: StateFlow<CarFormUiState> = _state.asStateFlow()

    private var nextKey = 0
    private var isSaving = false

    init {
        observeUnit()
    }

    fun onBrandChange(value: String) = _state.update { it.copy(brand = value) }

    fun onMileageChange(value: String) = _state.update { it.copy(mileageKm = value.digits()) }

    fun onVinChange(value: String) = _state.update { it.copy(vin = value.uppercase().take(VIN_LENGTH)) }

    fun onPlateChange(value: String) = _state.update { it.copy(plate = value.uppercase().take(PLATE_LENGTH)) }

    fun onTaskChange(key: Int, fields: TaskFields) = _state.update { state ->
        state.copy(tasks = state.tasks.map { if (it.key == key) it.copy(fields = fields.sanitized()) else it })
    }

    fun onTaskRecordToggle(key: Int, on: Boolean) = _state.update { state ->
        state.copy(tasks = state.tasks.map { if (it.key == key) it.copy(fields = it.fields.withRecord(on, getToday())) else it })
    }

    fun onAddTask() = _state.update { state ->
        val firstUnfinished = state.tasks.firstOrNull { !it.fields.isValid }
        if (firstUnfinished == null) {
            state.copy(tasks = state.tasks + TaskBlockState(key = nextKey++))
        } else {
            state.copy(
                tasks = state.tasks.map { block ->
                    when {
                        block.key == firstUnfinished.key -> block.copy(showErrors = true, revealRequest = block.revealRequest + 1)
                        !block.fields.isValid -> block.copy(showErrors = true)
                        else -> block
                    }
                },
            )
        }
    }

    fun onAddPreset(preset: TaskPreset, name: String) = _state.update { state ->
        val empty = state.tasks.firstOrNull { it.fields.isNameMissing && it.fields.isIntervalMissing }
        if (empty == null) {
            state.copy(tasks = state.tasks + TaskBlockState(key = nextKey++, fields = preset.toFields(name, state.distanceUnit)))
        } else {
            state.copy(
                tasks = state.tasks.map { block ->
                    if (block.key == empty.key) {
                        block.copy(
                            fields = preset.toFields(name, state.distanceUnit, block.fields),
                            showErrors = false,
                            revealRequest = block.revealRequest + 1,
                        )
                    } else {
                        block
                    }
                },
            )
        }
    }

    fun onToggleTask(key: Int) = _state.update { state ->
        state.copy(tasks = state.tasks.map { if (it.key == key) it.copy(isCollapsed = !it.isCollapsed) else it })
    }

    fun onRemoveTask(key: Int) = _state.update { state ->
        state.copy(tasks = state.tasks.filterNot { it.key == key })
    }

    fun onSave(defaultTaskName: String) {
        val form = _state.value
        if (!form.isValid) {
            _state.update { state ->
                state.copy(
                    showErrors = true,
                    tasks = state.tasks.map { if (it.fields.isValid) it else it.copy(isCollapsed = false) },
                )
            }
            return
        }
        if (isSaving) return
        isSaving = true
        val mileageKm = form.mileageKm.toInt().kmFrom(form.distanceUnit)
        viewModelScope.launch {
            val tasks = form.tasks.mapNotNull { it.fields.toNewTask(form.distanceUnit) }.ifEmpty {
                listOf(
                    NewMaintenanceTask(
                        name = defaultTaskName,
                        intervalKm = TaskPreset.OIL.intervalKm,
                        lastDoneKm = mileageKm,
                        reminderWindowKm = TaskPreset.OIL.reminderWindowKm,
                    ),
                )
            }
            val carId = attempt { addCar(NewCar(brand = form.brand, mileageKm = mileageKm, vin = form.vin, tasks = tasks, plate = form.plate)) }
                .getOrElse {
                    isSaving = false
                    _state.update { it.copy(saveFailed = true) }
                    return@launch
                }
            if (carId != null) _state.update { it.copy(isSaved = true) } else isSaving = false
        }
    }

    fun onSaveFailureShown() = _state.update { it.copy(saveFailed = false) }

    fun onRetry() {
        _state.update { it.copy(loadFailed = false) }
        observeUnit()
    }

    private fun observeUnit() {
        viewModelScope.launch {
            attempt { observeSettings().collect { settings -> _state.update { it.copy(distanceUnit = settings.distanceUnit) } } }
                .onFailure { _state.update { it.copy(loadFailed = true) } }
        }
    }

    private fun String.digits(): String = filter(Char::isDigit).take(MAX_NUMBER_LENGTH)

    private companion object {
        const val VIN_LENGTH = 17
        const val PLATE_LENGTH = 12
        const val MAX_NUMBER_LENGTH = 7
    }
}
