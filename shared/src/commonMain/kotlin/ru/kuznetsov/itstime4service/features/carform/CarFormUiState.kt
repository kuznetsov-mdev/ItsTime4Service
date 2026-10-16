package ru.kuznetsov.itstime4service.features.carform

import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.format.TaskFields

/**
 * State of the add-car form.
 * Every field is kept as the text the user typed; numbers and dates are parsed only on save.
 * The brand and the mileage are required, and so are the required fields of every task block.
 * [showErrors] turns on after a save attempt with a required field left empty: from then on
 * every such field is outlined in red until it is filled.
 * [isSaved] flips to `true` once the car is stored and tells the screen to close.
 * Distances are typed in [distanceUnit], the unit chosen in settings.
 */
data class CarFormUiState(
    val brand: String = "",
    val mileageKm: String = "",
    val vin: String = "",
    val plate: String = "",
    val tasks: List<TaskBlockState> = emptyList(),
    val showErrors: Boolean = false,
    val isSaved: Boolean = false,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
) {
    val isBrandMissing: Boolean get() = brand.isBlank()
    val isMileageMissing: Boolean get() = (mileageKm.toIntOrNull() ?: 0) <= 0
    val isValid: Boolean get() = !isBrandMissing && !isMileageMissing && tasks.all { it.fields.isValid }
}

/**
 * One maintenance task block of the form.
 * [key] identifies the block while others are added and removed around it.
 * A collapsed block ([isCollapsed]) shows only the task name and a line with the interval and the last-done mileage.
 */
data class TaskBlockState(
    val key: Int,
    val fields: TaskFields = TaskFields(),
    val isCollapsed: Boolean = false,
)
