package ru.kuznetsov.itstime4service.domain.model

/**
 * How many of a car's tasks need attention.
 * [taskCount] is the total number of tasks on the car, [total] counts only the soon and due ones.
 */
data class CarAlerts(
    val soon: Int,
    val due: Int,
    val taskCount: Int,
) {
    val total: Int get() = soon + due

    val worstStatus: TaskStatus
        get() = when {
            due > 0 -> TaskStatus.DUE
            soon > 0 -> TaskStatus.SOON
            else -> TaskStatus.OK
        }
}
