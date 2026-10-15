package ru.kuznetsov.itstime4service.domain.model

/**
 * A maintenance task paired with its derived state, as shown in a car's task list.
 */
data class TaskWithStat(
    val task: MaintenanceTask,
    val stat: TaskStat,
)
