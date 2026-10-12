package ru.kuznetsov.pitstop.features.carform

/**
 * State of the car form, which both adds a new car and edits an existing one ([isEditing]).
 * Every field is kept as the text the user typed; numbers and dates are parsed only on save.
 * [isLoading] is `true` while the edited car is being read; [isMissing] means it no longer exists.
 * [isSaved] flips to `true` once the car is stored; either of the last two tells the screen to close.
 */
data class CarFormUiState(
    val brand: String = "",
    val mileageKm: String = "",
    val vin: String = "",
    val tasks: List<TaskBlockState> = emptyList(),
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isMissing: Boolean = false,
    val isSaved: Boolean = false,
) {
    val canSave: Boolean get() = brand.isNotBlank() && (mileageKm.toIntOrNull() ?: 0) > 0
}

/**
 * One maintenance task block of the form.
 * [key] identifies the block while others are added and removed around it.
 * [taskId] is the id of the stored task the block edits, `null` for a block added in the form.
 */
data class TaskBlockState(
    val key: Int,
    val taskId: String? = null,
    val name: String = "",
    val intervalKm: String = "",
    val intervalMonths: String = "",
    val lastDoneKm: String = "",
    val lastDoneDate: String = "",
    val reminderWindowKm: String = "",
)

/**
 * Ready-made tasks behind the quick-add chips, with the intervals used by the design mockup.
 * The task name is localized, so it is supplied by the screen.
 */
enum class TaskPreset(val intervalKm: Int, val reminderWindowKm: Int) {
    OIL(10_000, 1_500),
    PADS(30_000, 3_000),
    AIR_FILTER(15_000, 2_000),
    TIMING_BELT(60_000, 5_000),
}
