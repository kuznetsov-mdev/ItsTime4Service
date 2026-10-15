package ru.kuznetsov.pitstop.domain.usecase

import kotlinx.coroutines.flow.first
import ru.kuznetsov.pitstop.domain.model.ServiceHistoryEntry
import ru.kuznetsov.pitstop.domain.repository.CarRepository
import ru.kuznetsov.pitstop.domain.repository.ServiceHistoryRepository

/**
 * Edits a service history record: its date, mileage, cost, service name and comment.
 * Returns `false` and changes nothing when the mileage is not positive or the record does not exist.
 * Blank texts and a non-positive cost are stored as `null`; the values kept for rolling back a done mark stay.
 * When the mileage of a record created by a done mark changes and its task still has the record's old mileage
 * as its last-done mileage, the task gets the new mileage too, in the same transaction. The record then no
 * longer counts as a done mark (see [DoneMarksUseCase]). Any other task is not touched.
 */
class UpdateHistoryEntryUseCase(
    private val serviceHistoryRepository: ServiceHistoryRepository,
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(entry: ServiceHistoryEntry): Boolean {
        if (entry.mileageKm <= 0) return false
        val original = serviceHistoryRepository.observeHistory(entry.carId).first().firstOrNull { it.id == entry.id }
            ?: return false
        val task = original.taskId
            ?.takeIf { original.mileageKm != entry.mileageKm }
            ?.let { taskId -> carRepository.observeCarById(entry.carId).first()?.tasks?.firstOrNull { it.id == taskId } }
            ?.takeIf { it.lastDoneKm == original.mileageKm }
        serviceHistoryRepository.updateEntry(
            entry = original.copy(
                date = entry.date,
                mileageKm = entry.mileageKm,
                costRub = entry.costRub?.takeIf { it > 0 },
                serviceName = entry.serviceName.orNullIfBlank(),
                comment = entry.comment.orNullIfBlank(),
            ),
            taskLastDoneKm = task?.let { entry.mileageKm },
        )
        return true
    }
}
