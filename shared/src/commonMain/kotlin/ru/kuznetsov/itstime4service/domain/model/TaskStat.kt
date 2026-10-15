package ru.kuznetsov.itstime4service.domain.model

/**
 * Derived state of a [MaintenanceTask] for the car's current mileage and today's date.
 * [remainingKm] goes negative once the task is overdue by mileage.
 * [remainingMonths] is `null` when the task has no time interval, zero or negative once it is overdue by time.
 * [progress] is the share of the mileage interval already driven, clamped to 0..1.
 */
data class TaskStat(
    val dueAtKm: Int,
    val remainingKm: Int,
    val remainingMonths: Int?,
    val status: TaskStatus,
    val progress: Float,
)
