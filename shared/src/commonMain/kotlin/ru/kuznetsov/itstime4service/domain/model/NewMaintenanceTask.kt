package ru.kuznetsov.itstime4service.domain.model

import kotlinx.datetime.LocalDate

/**
 * A maintenance task as entered in the add-car form, before it is stored and gets an id.
 * A `null` [reminderWindowKm] means "use the default share of the interval".
 * A new task gets [TaskIcon.WRENCH]; a `null` [icon] keeps an edited task without one, as it was saved.
 * With a [record] the new task is stored together with a service history record of its last service.
 */
data class NewMaintenanceTask(
    val name: String,
    val intervalKm: Int,
    val lastDoneKm: Int = 0,
    val reminderWindowKm: Int? = null,
    val intervalMonths: Int? = null,
    val lastDoneDate: LocalDate? = null,
    val icon: TaskIcon? = TaskIcon.WRENCH,
    val record: NewTaskRecord? = null,
)
