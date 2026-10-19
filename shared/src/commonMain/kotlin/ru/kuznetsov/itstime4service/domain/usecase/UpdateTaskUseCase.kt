package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository

/**
 * Replaces every field of an existing maintenance task with the values entered on the task screen.
 * Returns `false` and changes nothing when [NormalizeTaskUseCase] rejects the task.
 * When the last-done mileage or date is changed and the task has a linked history record — the latest record of
 * this task at its previous last-done mileage, by date and then by the order the records were added — the record gets the new mileage (if positive) and the typed date,
 * in the same transaction. The date is taken as typed, so it reaches the record even for a task without a time
 * interval, which does not store it.
 */
class UpdateTaskUseCase(
    private val carRepository: CarRepository,
    private val serviceHistoryRepository: ServiceHistoryRepository,
    private val normalizeTask: NormalizeTaskUseCase,
) {
    suspend operator fun invoke(carId: String, taskId: String, task: NewMaintenanceTask): Boolean {
        val normalized = normalizeTask(task) ?: return false
        val original = carRepository.observeCarById(carId).first()?.tasks?.firstOrNull { it.id == taskId }
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
            entry = original?.let { linkedEntry(carId, it, normalized.lastDoneKm, task.lastDoneDate) },
        )
        return true
    }

    private suspend fun linkedEntry(carId: String, original: MaintenanceTask, lastDoneKm: Int, typedDate: LocalDate?) =
        serviceHistoryRepository.observeHistory(carId).first()
            .linkedEntryOf(original)
            ?.let { entry ->
                entry.copy(
                    mileageKm = lastDoneKm.takeIf { it > 0 && it != original.lastDoneKm } ?: entry.mileageKm,
                    date = typedDate?.takeIf { it != original.lastDoneDate } ?: entry.date,
                ).takeIf { it != entry }
            }
}
