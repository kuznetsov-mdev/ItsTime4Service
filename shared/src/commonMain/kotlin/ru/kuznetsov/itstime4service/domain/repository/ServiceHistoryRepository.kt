package ru.kuznetsov.itstime4service.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry

/**
 * Service log of completed jobs, kept per car. [observeHistory] and [observeAllHistory] list records in the order
 * they were added.
 * Records are created and removed together with a task's done mark or the car's full deletion, see [CarRepository];
 * [addEntry] adds a record typed by hand, not tied to any task.
 * [updateEntry] with `syncTask` also gives the record's task the record's mileage as its last-done mileage and,
 * for a task with a time interval, the record's date as its last-done date, in one transaction.
 */
interface ServiceHistoryRepository {
    fun observeHistory(carId: String): Flow<List<ServiceHistoryEntry>>

    fun observeAllHistory(): Flow<List<ServiceHistoryEntry>>

    suspend fun updateEntry(entry: ServiceHistoryEntry, syncTask: Boolean = false)

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
