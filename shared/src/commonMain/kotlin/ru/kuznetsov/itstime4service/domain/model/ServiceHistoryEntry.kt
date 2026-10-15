package ru.kuznetsov.itstime4service.domain.model

import kotlinx.datetime.LocalDate

/**
 * One completed job in a car's service log.
 * Keeps the task name as text so the record survives edits to the task itself.
 * A record created by marking a task as done knows that task by [taskId] and keeps the task's previous
 * [prevLastDoneKm] and [prevLastDoneDate], so the mark can be rolled back; records created before
 * these fields existed have all three empty.
 */
data class ServiceHistoryEntry(
    val id: String,
    val carId: String,
    val taskName: String,
    val date: LocalDate,
    val mileageKm: Int,
    val costRub: Int? = null,
    val serviceName: String? = null,
    val comment: String? = null,
    val taskId: String? = null,
    val prevLastDoneKm: Int? = null,
    val prevLastDoneDate: LocalDate? = null,
)
