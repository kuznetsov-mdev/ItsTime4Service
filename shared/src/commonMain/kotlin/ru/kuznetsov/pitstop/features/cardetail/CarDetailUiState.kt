package ru.kuznetsov.pitstop.features.cardetail

import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.TaskStatus

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
enum class EditableField { BRAND, VIN, MILEAGE }

/**
 * The car as shown in the screen header and its tasks, most urgent first.
 * [alertCount] of [taskCount] tasks are soon or due; [worstStatus] colors that counter.
 */
data class CarDetails(
    val brand: String,
    val vin: String?,
    val mileageKm: Int,
    val alertCount: Int,
    val taskCount: Int,
    val worstStatus: TaskStatus,
    val tasks: List<TaskItem>,
)

/**
 * One maintenance task row.
 * [remainingKm] is negative once overdue; [remainingMonths] is `null` for tasks without a time interval.
 * [isDone] is `true` while the task counts as just marked done, which turns its button into "done".
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
)
