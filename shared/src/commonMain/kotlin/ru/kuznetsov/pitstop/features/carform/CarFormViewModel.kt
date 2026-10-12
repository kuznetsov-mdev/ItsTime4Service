package ru.kuznetsov.pitstop.features.carform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.kuznetsov.pitstop.domain.model.Car
import ru.kuznetsov.pitstop.domain.model.MaintenanceTask
import ru.kuznetsov.pitstop.domain.model.NewCar
import ru.kuznetsov.pitstop.domain.model.NewMaintenanceTask
import ru.kuznetsov.pitstop.domain.usecase.AddCarUseCase
import ru.kuznetsov.pitstop.domain.usecase.AddTaskUseCase
import ru.kuznetsov.pitstop.domain.usecase.DeleteTaskUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveCarByIdUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateCarUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateMileageUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateTaskUseCase
import ru.kuznetsov.pitstop.ui.format.parseDisplayDate
import ru.kuznetsov.pitstop.ui.format.toDisplayText

/**
 * Drives the car form: the car fields and a list of task blocks filled by hand or from presets.
 * Without a [carId] the form adds a car: it opens without task blocks, and when no valid block is left
 * on save the car gets a default oil-change task counted from its current mileage, as in the design mockup.
 * With a [carId] the form edits that car: it opens filled with the car and its tasks, and saving updates
 * the car, removes the tasks whose blocks were deleted, rewrites the remaining ones and adds the new ones;
 * no default task is added, so the car may end up without tasks.
 * In both modes a block without a name or an interval is skipped on save; when it belongs to a stored task,
 * that task stays as it was. The mileage is always entered in kilometers.
 */
class CarFormViewModel(
    private val carId: String?,
    private val observeCarById: ObserveCarByIdUseCase,
    private val addCar: AddCarUseCase,
    private val updateCar: UpdateCarUseCase,
    private val updateMileage: UpdateMileageUseCase,
    private val addTask: AddTaskUseCase,
    private val updateTask: UpdateTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CarFormUiState(isEditing = carId != null, isLoading = carId != null))
    val state: StateFlow<CarFormUiState> = _state.asStateFlow()

    private var nextKey = 0
    private var storedMileageKm = 0
    private var storedTaskIds = emptySet<String>()

    init {
        if (carId != null) viewModelScope.launch { load(carId) }
    }


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
        if (!form.canSave || form.isLoading) return
        viewModelScope.launch {
            val saved = if (carId == null) create(form, mileageKm, defaultTaskName) else update(carId, form, mileageKm)
            if (saved) _state.update { it.copy(isSaved = true) }
        }
    }

    private suspend fun load(carId: String) {
        val car = observeCarById(carId).first()
        if (car == null) {
            _state.update { it.copy(isLoading = false, isMissing = true) }
            return
        }
        storedMileageKm = car.mileageKm
        storedTaskIds = car.tasks.map { it.id }.toSet()
        _state.update { it.filledWith(car) }
    }

    private fun CarFormUiState.filledWith(car: Car) = copy(
        brand = car.brand,
        mileageKm = car.mileageKm.toString(),
        vin = car.vin.orEmpty(),
        tasks = car.tasks.map { it.toBlock() },
        isLoading = false,
    )

    private fun MaintenanceTask.toBlock() = TaskBlockState(
        key = nextKey++,
        taskId = id,
        name = name,
        intervalKm = intervalKm.toString(),
        intervalMonths = intervalMonths?.toString().orEmpty(),
        lastDoneKm = lastDoneKm.toString(),
        lastDoneDate = lastDoneDate?.toDisplayText().orEmpty(),
        reminderWindowKm = reminderWindowKm.toString(),
    )

    private suspend fun create(form: CarFormUiState, mileageKm: Int, defaultTaskName: String): Boolean {
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
        return addCar(NewCar(brand = form.brand, mileageKm = mileageKm, vin = form.vin, tasks = tasks)) != null
    }

    private suspend fun update(carId: String, form: CarFormUiState, mileageKm: Int): Boolean {
        if (!updateCar(carId, form.brand, form.vin)) return false
        if (mileageKm != storedMileageKm) updateMileage(carId, mileageKm)
        val keptTaskIds = form.tasks.mapNotNull { it.taskId }.toSet()
        (storedTaskIds - keptTaskIds).forEach { deleteTask(carId, it) }
        form.tasks.forEach { block ->
            val task = block.toNewTask() ?: return@forEach
            if (block.taskId != null) updateTask(carId, block.taskId, task) else addTask(carId, task)
        }
        return true
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
