package ru.kuznetsov.itstime4service.domain.model

import kotlinx.datetime.LocalDate

/**
 * The service history record added together with a new task, when the task is entered as already done.
 * The record's mileage is the task's last-done mileage; [date] is kept even for a task without a time interval,
 * which does not store its last-done date.
 */
data class NewTaskRecord(
    val date: LocalDate,
    val serviceName: String? = null,
    val costRub: Int? = null,
    val comment: String? = null,
)
