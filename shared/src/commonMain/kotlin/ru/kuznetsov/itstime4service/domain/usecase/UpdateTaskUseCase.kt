package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Replaces every field of an existing maintenance task with the values entered in the car form.
 * Returns `false` and changes nothing when [NormalizeTaskUseCase] rejects the task.
 */
class UpdateTaskUseCase(
    private val carRepository: CarRepository,
    private val normalizeTask: NormalizeTaskUseCase,
) {
    suspend operator fun invoke(carId: String, taskId: String, task: NewMaintenanceTask): Boolean {
        val normalized = normalizeTask(task) ?: return false
        carRepository.updateTask(
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
        )
        return true
    }
}
