package ru.kuznetsov.pitstop.features.carform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.kuznetsov.pitstop.domain.model.NewCar
import ru.kuznetsov.pitstop.domain.model.NewMaintenanceTask
import ru.kuznetsov.pitstop.domain.usecase.AddCarUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.pitstop.ui.format.TaskFields
import ru.kuznetsov.pitstop.ui.format.TaskPreset
import ru.kuznetsov.pitstop.ui.format.kmFrom

/**
 * Drives the add-car form: the car fields and a list of task blocks filled by hand or from presets.
 * The form opens without task blocks; one appears on the add-task button or a preset chip.
 * Saving with an empty required field — in the car or in any task block — stores nothing and switches
 * the form to showing errors; an unwanted block has to be removed, it is not skipped silently.
 * When the form has no blocks at all, the car gets a default oil-change task counted from its current
 * mileage, as in the design mockup. Distances are typed in the unit chosen in settings and stored in kilometers.
 */
class CarFormViewModel(
    private val addCar: AddCarUseCase,
    observeSettings: ObserveSettingsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CarFormUiState())
    val state: StateFlow<CarFormUiState> = _state.asStateFlow()

    private var nextKey = 0

    init {
        viewModelScope.launch {
            observeSettings().collect { settings -> _state.update { it.copy(distanceUnit = settings.distanceUnit) } }
        }
    }

    fun onBrandChange(value: String) = _state.update { it.copy(brand = value) }

    fun onMileageChange(value: String) = _state.update { it.copy(mileageKm = value.digits()) }

    fun onVinChange(value: String) = _state.update { it.copy(vin = value.uppercase().take(VIN_LENGTH)) }

    fun onTaskChange(key: Int, fields: TaskFields) = _state.update { state ->
        state.copy(tasks = state.tasks.map { if (it.key == key) it.copy(fields = fields.sanitized()) else it })
    }

    fun onAddTask() = _state.update { it.copy(tasks = it.tasks + TaskBlockState(key = nextKey++)) }

    fun onAddPreset(preset: TaskPreset, name: String) = _state.update {
        it.copy(tasks = it.tasks + TaskBlockState(key = nextKey++, fields = preset.toFields(name, it.distanceUnit)))
    }

    fun onRemoveTask(key: Int) = _state.update { state ->
        state.copy(tasks = state.tasks.filterNot { it.key == key })
    }

    fun onSave(defaultTaskName: String) {
        val form = _state.value
        if (!form.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val mileageKm = form.mileageKm.toInt().kmFrom(form.distanceUnit)
        viewModelScope.launch {
            val tasks = form.tasks.mapNotNull { it.fields.toNewTask(form.distanceUnit) }.ifEmpty {
                listOf(
                    NewMaintenanceTask(
                        name = defaultTaskName,
                        intervalKm = DEFAULT_TASK_INTERVAL_KM,
                        lastDoneKm = mileageKm,
                        reminderWindowKm = DEFAULT_TASK_REMINDER_WINDOW_KM,
                    ),
                )
            }
            val carId = addCar(NewCar(brand = form.brand, mileageKm = mileageKm, vin = form.vin, tasks = tasks))
            if (carId != null) _state.update { it.copy(isSaved = true) }
        }
    }

    private fun String.digits(): String = filter(Char::isDigit).take(MAX_NUMBER_LENGTH)

    private companion object {
        const val VIN_LENGTH = 17
        const val MAX_NUMBER_LENGTH = 7
        const val DEFAULT_TASK_INTERVAL_KM = 10_000
        const val DEFAULT_TASK_REMINDER_WINDOW_KM = 1_500
    }
}
