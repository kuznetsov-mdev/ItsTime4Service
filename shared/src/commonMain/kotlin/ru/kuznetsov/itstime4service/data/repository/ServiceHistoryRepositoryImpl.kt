package ru.kuznetsov.itstime4service.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.data.mapper.toDomain
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository

/**
 * [ServiceHistoryRepository] backed by the SQLDelight database.
 * Records are emitted in the order they were added.
 * Editing a record may also set its task's last-done mileage; both rows change in one transaction.
 */
class ServiceHistoryRepositoryImpl(database: ItsTime4ServiceDatabase) : ServiceHistoryRepository {

    private val queries = database.serviceHistoryQueries
    private val taskQueries = database.maintenanceTaskQueries
    private val dispatcher = Dispatchers.Default

    override fun observeHistory(carId: String): Flow<List<ServiceHistoryEntry>> =
        queries.selectByCar(carId).asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllHistory(): Flow<List<ServiceHistoryEntry>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun updateEntry(entry: ServiceHistoryEntry, taskLastDoneKm: Int?): Unit = withContext(dispatcher) {
        queries.transaction {
            queries.update(
                task_name = entry.taskName,
                date = entry.date.toString(),
                mileage_km = entry.mileageKm.toLong(),
                cost_rub = entry.costRub?.toLong(),
                service_name = entry.serviceName,
                comment = entry.comment,
                id = entry.id,
            )
            val taskId = entry.taskId
            if (taskLastDoneKm != null && taskId != null) {
                taskQueries.updateLastDoneKm(taskLastDoneKm.toLong(), taskId, entry.carId)
            }
        }
    }
}
