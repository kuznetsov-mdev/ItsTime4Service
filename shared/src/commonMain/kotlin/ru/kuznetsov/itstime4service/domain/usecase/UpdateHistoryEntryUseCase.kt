package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.repository.TaskHistoryRepository

/**
 * Edits a service history record: its date, mileage, cost, service name and comment.
 * Returns `false` and changes nothing when the mileage is not positive or the record does not exist.
 * Blank texts and a non-positive cost are stored as `null`; the task name, the task and the values kept for rolling
 * back a done mark stay. When the record is its task's linked one and its mileage or date changes, the task follows
 * in the same transaction (see [TaskHistoryRepository.updateEntry]); a done mark whose mileage moves away from the
 * car's mileage no longer counts as one. Works for the records of a sold car too.
 */
class UpdateHistoryEntryUseCase(
    private val taskHistoryRepository: TaskHistoryRepository,
) {
    suspend operator fun invoke(entry: ServiceHistoryEntry): Boolean {
        if (entry.mileageKm <= 0) return false
        return taskHistoryRepository.updateEntry(
            entry.copy(
                costRub = entry.costRub?.takeIf { it > 0 },
                serviceName = entry.serviceName.orNullIfBlank(),
                comment = entry.comment.orNullIfBlank(),
            ),
        )
    }
}
