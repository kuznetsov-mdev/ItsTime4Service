package ru.kuznetsov.pitstop.features.addcar

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
import ru.kuznetsov.pitstop.ui.format.parseDisplayDate

/**
 * Drives the add-car form: the car fields and a list of task blocks filled by hand or from presets.
 * The form opens without task blocks; one appears on the add-task button or a preset chip.
 * Blocks without a name or an interval are skipped on save; when none is left, the car gets
 * a default oil-change task counted from its current mileage, as in the design mockup.
 */
class AddCarViewModel(
    private val addCar: AddCarUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AddCarUiState())
    val state: StateFlow<AddCarUiState> = _state.asStateFlow()

    private var nextKey = 0

    fun onBrandChange(value: String) = _state.update { it.copy(brand = value) }

    fun onMileageChange(value: String) = _state.update { it.copy(mileageKm = value.digits()) }

    fun onVinChange(value: String) = _state.update { it.copy(vin = value.uppercase().take(VIN_LENGTH)) }

    fun onTaskChange(task: TaskBlockState) = _state.update { state ->
        val cleaned = task.copy(
            intervalKm = task.intervalKm.digits(),
            intervalMonths = task.intervalMonths.digits(),
            lastDoneKm = task.lastDoneKm.digits(),
            reminderWindowKm = task.reminderWindowKm.digits(),
        )
        state.copy(tasks = state.tasks.map { if (it.key == task.key) cleaned else it })
    }

    fun onAddTask() = _state.update { it.copy(tasks = it.tasks + TaskBlockState(key = nextKey++)) }

    fun onAddPreset(preset: TaskPreset, name: String) = _state.update {
        it.copy(
            tasks = it.tasks + TaskBlockState(
                key = nextKey++,
                name = name,
                intervalKm = preset.intervalKm.toString(),
                reminderWindowKm = preset.reminderWindowKm.toString(),
            ),
        )
    }

    fun onRemoveTask(key: Int) = _state.update { state ->
        state.copy(tasks = state.tasks.filterNot { it.key == key })
    }

    fun onSave(defaultTaskName: String) {
        val form = _state.value
        val mileageKm = form.mileageKm.toIntOrNull() ?: return
        if (!form.canSave) return
        viewModelScope.launch {
            val tasks = form.tasks.mapNotNull { it.toNewTask() }.ifEmpty {
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

    private fun TaskBlockState.toNewTask(): NewMaintenanceTask? {
        val interval = intervalKm.toIntOrNull() ?: return null
        if (name.isBlank() || interval <= 0) return null
        return NewMaintenanceTask(
            name = name,
            intervalKm = interval,
            lastDoneKm = lastDoneKm.toIntOrNull() ?: 0,
            reminderWindowKm = reminderWindowKm.toIntOrNull(),
            intervalMonths = intervalMonths.toIntOrNull(),
            lastDoneDate = parseDisplayDate(lastDoneDate),
        )
    }

    private fun String.digits(): String = filter(Char::isDigit).take(MAX_NUMBER_LENGTH)

    private companion object {
        const val VIN_LENGTH = 17
        const val MAX_NUMBER_LENGTH = 7
        const val DEFAULT_TASK_INTERVAL_KM = 10_000
        const val DEFAULT_TASK_REMINDER_WINDOW_KM = 1_500
    }
}
