package ru.kuznetsov.pitstop.domain.model

import kotlinx.datetime.LocalDateTime

/**
 * One notification about a soon or due task, planned for [fireAt] in the device time zone.
 * [status], [remainingKm] and [remainingMonths] are the task's state on the day of [fireAt]
 * at the car's mileage known when the plan was built.
 */
data class PlannedReminder(
    val carId: String,
    val taskId: String,
    val carBrand: String,
    val taskName: String,
    val status: TaskStatus,
    val remainingKm: Int,
    val remainingMonths: Int?,
    val fireAt: LocalDateTime,
)
