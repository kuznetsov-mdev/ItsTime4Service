package ru.kuznetsov.itstime4service.domain.model

import kotlinx.datetime.LocalDate

/**
 * A recurring job on a car, due every [intervalKm] and, optionally, every [intervalMonths] — whichever comes first.
 * The time interval is tracked only when both [intervalMonths] and [lastDoneDate] are set.
 * [reminderWindowKm] is how many km before the due mileage the task starts counting as [TaskStatus.SOON].
 * [icon] is `null` for a task saved before icons could be picked; the screen then picks one by the name.
 */
data class MaintenanceTask(
    val id: String,
    val name: String,
    val intervalKm: Int,
    val lastDoneKm: Int,
    val reminderWindowKm: Int,
    val intervalMonths: Int? = null,
    val lastDoneDate: LocalDate? = null,
    val icon: TaskIcon? = null,
)
