package ru.kuznetsov.itstime4service.features.taskform

import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.format.TaskFields

/**
 * State of the task screen, which both adds a task to a car and edits an existing one ([isEditing]).
 * [isLoading] is `true` until the car is read. [showErrors] turns on after a save attempt with a required
 * field left empty and outlines every such field in red until it is filled.
 * [formKey] changes when a saved task is replaced with a fresh empty form by "add another",
 * so the screen can scroll back to the top. [isClosed] tells the screen to close: the task was saved
 * or deleted, or the car or the task no longer exists. Distances are typed in [distanceUnit], the unit
 * chosen in settings.
 */
data class TaskFormUiState(
    val isLoading: Boolean = true,
    val carBrand: String = "",
    val isEditing: Boolean = false,
    val fields: TaskFields = TaskFields(),
    val showErrors: Boolean = false,
    val isConfirmingDelete: Boolean = false,
    val formKey: Int = 0,
    val isClosed: Boolean = false,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
)
