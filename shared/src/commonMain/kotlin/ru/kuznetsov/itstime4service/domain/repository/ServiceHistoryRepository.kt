package ru.kuznetsov.itstime4service.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry

/**
 * Service log of completed jobs, kept per car. [observeHistory] and [observeAllHistory] list records in the order
 * they were added.
 * Records that go together with a task — done marks, records of a new task, edits that move the task — and the
 * removal of a car's history are written through [TaskHistoryRepository]; [addEntry] adds a record typed by hand,
 * not tied to any task.
 */
interface ServiceHistoryRepository {
    fun observeHistory(carId: String): Flow<List<ServiceHistoryEntry>>

    fun observeAllHistory(): Flow<List<ServiceHistoryEntry>>

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
