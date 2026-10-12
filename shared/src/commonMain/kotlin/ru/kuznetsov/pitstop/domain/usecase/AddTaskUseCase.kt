package ru.kuznetsov.pitstop.domain.usecase

import ru.kuznetsov.pitstop.domain.model.NewMaintenanceTask
import ru.kuznetsov.pitstop.domain.repository.CarRepository

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
