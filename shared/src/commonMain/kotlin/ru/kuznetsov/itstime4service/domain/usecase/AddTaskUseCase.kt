package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.repository.TaskHistoryRepository

/**
 * Adds a maintenance task to an existing car.
 * Returns `false` and changes nothing when [NormalizeTaskUseCase] rejects the task.
 */
class AddTaskUseCase(
    private val taskHistoryRepository: TaskHistoryRepository,
    private val normalizeTask: NormalizeTaskUseCase,
) {
    suspend operator fun invoke(carId: String, task: NewMaintenanceTask): Boolean {
        val normalized = normalizeTask(task) ?: return false
        taskHistoryRepository.addTask(carId, normalized)
        return true
    }
}
