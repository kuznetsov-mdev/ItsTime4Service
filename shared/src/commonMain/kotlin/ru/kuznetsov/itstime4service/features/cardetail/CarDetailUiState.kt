package ru.kuznetsov.itstime4service.features.cardetail

import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.ui.components.indicators.PlateLook

/**
 * State of the car screen.
 * [car] is `null` while loading and after the car has been deleted; [isMissing] tells the two apart
 * and is the signal for the screen to close itself.
 * [editingField] is the one header value currently edited in place, if any.
 * [taskDoneDialog] is the task whose "task done" window is open; [isConfirmingUnmark] means the window
 * asking to remove a done mark is open.
 */
data class CarDetailUiState(
    val isLoading: Boolean = true,
    val car: CarDetails? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val editingField: EditableField? = null,
    val isConfirmingDelete: Boolean = false,
    val taskDoneDialog: TaskItem? = null,
    val isConfirmingUnmark: Boolean = false,
    val mileageMultiplier: Int = 1,
) {
    val isMissing: Boolean get() = !isLoading && car == null

    companion object {
        const val MILEAGE_STEP = 100
        val MILEAGE_MULTIPLIERS = listOf(1, 2, 5, 10)
    }
}

/**
 * The header values of the car screen that are edited in place, one at a time.
 */
enum class EditableField { BRAND, PLATE, VIN, MILEAGE }

/**
 * The car as shown in the screen header and its tasks, most urgent first.
 * [dueCount] of [taskCount] tasks are due and [soonCount] are soon; together they fill the status ring.
 * [plate] is the licence plate as typed and [plateLook] how it is drawn; both are `null` without a plate.
 */
data class CarDetails(
    val brand: String,
    val vin: String?,
    val plate: String?,
    val plateLook: PlateLook?,
    val plateHidden: Boolean = false,
    val vinHidden: Boolean = false,
    val mileageKm: Int,
    val dueCount: Int,
    val soonCount: Int,
    val taskCount: Int,
    val tasks: List<TaskItem>,
)

/**
 * One maintenance task row.
 * [remainingKm] is negative once overdue; [remainingMonths] is `null` for tasks without a time interval.
 * [isDone] is `true` while the task counts as just marked done, which turns its button into "done".
 * [icon] is the one shown on the card, picked by the user or guessed from the name.
 */
data class TaskItem(
    val id: String,
    val name: String,
    val intervalKm: Int,
    val remainingKm: Int,
    val remainingMonths: Int?,
    val status: TaskStatus,
    val progress: Float,
    val isDone: Boolean = false,
    val icon: TaskIcon = TaskIcon.WRENCH,
)
