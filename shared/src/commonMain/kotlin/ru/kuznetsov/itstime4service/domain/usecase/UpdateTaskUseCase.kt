package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.repository.TaskHistoryRepository

/**
 * Replaces every field of an existing maintenance task with the values entered on the task screen.
 * Returns `false` and changes nothing when [NormalizeTaskUseCase] rejects the task.
 * A changed last-done mileage or date also moves the task's linked history record, in the same transaction
 * (see [TaskHistoryRepository.updateTask]). The known date is kept independently of the time interval and synchronized with the linked record.
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
