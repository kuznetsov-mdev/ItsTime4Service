package ru.kuznetsov.itstime4service.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry

/**
 * Service log of completed jobs, kept per car.
 * Records are created and removed only together with a task's done mark or the car's full deletion,
 * see [CarRepository].
 * [updateEntry] with [taskLastDoneKm] also sets that last-done mileage on the record's task, in one transaction.
 */
interface ServiceHistoryRepository {
    fun observeHistory(carId: String): Flow<List<ServiceHistoryEntry>>

    fun observeAllHistory(): Flow<List<ServiceHistoryEntry>>

    suspend fun updateEntry(entry: ServiceHistoryEntry, taskLastDoneKm: Int? = null)
}
