package ru.kuznetsov.itstime4service.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry

/**
 * Service log of completed jobs, kept per car.
 * Records are created and removed together with a task's done mark or the car's full deletion, see [CarRepository];
 * [addEntry] adds a record typed by hand, not tied to any task.
 * [updateEntry] with [taskLastDoneKm] also sets that last-done mileage on the record's task, in one transaction.
 */
interface ServiceHistoryRepository {
    fun observeHistory(carId: String): Flow<List<ServiceHistoryEntry>>

    fun observeAllHistory(): Flow<List<ServiceHistoryEntry>>

    suspend fun updateEntry(entry: ServiceHistoryEntry, taskLastDoneKm: Int? = null)

    suspend fun addEntry(
        carId: String,
        taskName: String,
        date: LocalDate,
        mileageKm: Int,
        serviceName: String?,
        costRub: Int?,
        comment: String?,
    )
}
