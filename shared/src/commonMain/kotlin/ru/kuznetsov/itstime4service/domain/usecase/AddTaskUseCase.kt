package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Adds a maintenance task to an existing car.
 * Returns `false` and changes nothing when [NormalizeTaskUseCase] rejects the task.
 */
class AddTaskUseCase(
    private val carRepository: CarRepository,
    private val normalizeTask: NormalizeTaskUseCase,
) {
    suspend operator fun invoke(carId: String, task: NewMaintenanceTask): Boolean {
        val normalized = normalizeTask(task) ?: return false
        carRepository.addTask(carId, normalized)
        return true
    }
}
