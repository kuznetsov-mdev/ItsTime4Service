package ru.kuznetsov.pitstop.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.kuznetsov.pitstop.data.local.PitStopDatabase
import ru.kuznetsov.pitstop.data.mapper.toDomain
import ru.kuznetsov.pitstop.domain.model.NewServiceHistoryEntry
import ru.kuznetsov.pitstop.domain.model.ServiceHistoryEntry
import ru.kuznetsov.pitstop.domain.repository.ServiceHistoryRepository

/**
 * [ServiceHistoryRepository] backed by the SQLDelight database.
 * Records are emitted in the order they were added.
 */
@OptIn(ExperimentalUuidApi::class)
class ServiceHistoryRepositoryImpl(database: PitStopDatabase) : ServiceHistoryRepository {

    private val queries = database.serviceHistoryQueries
    private val dispatcher = Dispatchers.Default

    override fun observeHistory(carId: String): Flow<List<ServiceHistoryEntry>> =
        queries.selectByCar(carId).asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun addEntry(entry: NewServiceHistoryEntry): Unit = withContext(dispatcher) {
        queries.insert(
            id = Uuid.random().toString(),
            car_id = entry.carId,
            task_name = entry.taskName,
            date = entry.date.toString(),
            mileage_km = entry.mileageKm.toLong(),
            cost_rub = entry.costRub?.toLong(),
            service_name = entry.serviceName,
        )
    }

    override suspend fun updateEntry(entry: ServiceHistoryEntry): Unit = withContext(dispatcher) {
        queries.update(
            task_name = entry.taskName,
            date = entry.date.toString(),
            mileage_km = entry.mileageKm.toLong(),
            cost_rub = entry.costRub?.toLong(),
            service_name = entry.serviceName,
            id = entry.id,
        )
    }

    override suspend fun deleteHistory(carId: String): Unit = withContext(dispatcher) {
        queries.deleteByCar(carId)
    }
}
