package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.repository.TaskHistoryRepository

/**
 * Replaces every field of an existing maintenance task with the values entered on the task screen.
 * Returns `false` and changes nothing when [NormalizeTaskUseCase] rejects the task.
 * A changed last-done mileage or date also moves the task's linked history record, in the same transaction
 * (see [TaskHistoryRepository.updateTask]). The date goes to the record as typed, before normalization, so it
 * reaches the record even for a task without a time interval, which does not store it.
 */
class UpdateTaskUseCase(
    private val taskHistoryRepository: TaskHistoryRepository,
    private val normalizeTask: NormalizeTaskUseCase,
) {
    suspend operator fun invoke(carId: String, taskId: String, task: NewMaintenanceTask): Boolean {
        val normalized = normalizeTask(task) ?: return false
        taskHistoryRepository.updateTask(
            carId,
            MaintenanceTask(
                id = taskId,
                name = normalized.name,
                intervalKm = normalized.intervalKm,
                lastDoneKm = normalized.lastDoneKm,
                reminderWindowKm = normalized.reminderWindowKm ?: 0,
                intervalMonths = normalized.intervalMonths,
                lastDoneDate = normalized.lastDoneDate,
                icon = normalized.icon,
            ),
            recordDate = task.lastDoneDate,
        )
        return true
    }
}
