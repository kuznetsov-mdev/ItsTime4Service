package ru.kuznetsov.pitstop.domain.usecase

import ru.kuznetsov.pitstop.domain.model.ServiceHistoryEntry
import ru.kuznetsov.pitstop.domain.repository.ServiceHistoryRepository

/**
 * Edits a service history record: its date, mileage, cost and service name.
 * Returns `false` and changes nothing when the mileage is not positive.
 * A blank service name and a non-positive cost are stored as `null`.
 * The task itself is not touched: its last-done mileage and date stay as they were.
 */
class UpdateHistoryEntryUseCase(
    private val serviceHistoryRepository: ServiceHistoryRepository,
) {
    suspend operator fun invoke(entry: ServiceHistoryEntry): Boolean {
        if (entry.mileageKm <= 0) return false
        serviceHistoryRepository.updateEntry(
            entry.copy(
                costRub = entry.costRub?.takeIf { it > 0 },
                serviceName = entry.serviceName?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        return true
    }
}
