package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.first
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository

/**
 * Edits a service history record: its date, mileage, cost, service name and comment.
 * Returns `false` and changes nothing when the mileage is not positive or the record does not exist.
 * Blank texts and a non-positive cost are stored as `null`; the values kept for rolling back a done mark stay.
 * When the mileage or the date of a record changes and the record is its task's linked one — the latest record of
 * the task at the task's last-done mileage, by date and then by the order the records were added — the task gets the new mileage and, if it has a time interval, the new date too, in the
 * same transaction. A done mark whose mileage moves away from the car's mileage no longer counts as one
 * (see [DoneMarksUseCase]). Any other task is not touched.
 */
class UpdateHistoryEntryUseCase(
    private val serviceHistoryRepository: ServiceHistoryRepository,
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(entry: ServiceHistoryEntry): Boolean {
        if (entry.mileageKm <= 0) return false
        val history = serviceHistoryRepository.observeHistory(entry.carId).first()
        val original = history.firstOrNull { it.id == entry.id } ?: return false
        val task = original.taskId
            ?.takeIf { original.mileageKm != entry.mileageKm || original.date != entry.date }
            ?.let { taskId -> carRepository.observeCarById(entry.carId).first()?.tasks?.firstOrNull { it.id == taskId } }
            ?.takeIf { history.linkedEntryOf(it)?.id == original.id }
        serviceHistoryRepository.updateEntry(
            entry = original.copy(
                date = entry.date,
                mileageKm = entry.mileageKm,
                costRub = entry.costRub?.takeIf { it > 0 },
                serviceName = entry.serviceName.orNullIfBlank(),
                comment = entry.comment.orNullIfBlank(),
            ),
            syncTask = task != null,
        )
        return true
    }
}
