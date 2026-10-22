package ru.kuznetsov.itstime4service.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.data.mapper.toDomain
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository

/**
 * [ServiceHistoryRepository] backed by the SQLDelight database.
 * Records are emitted in the order they were added.
 * A record added by hand gets a new random id and no task or rollback data.
 */
@OptIn(ExperimentalUuidApi::class)
class ServiceHistoryRepositoryImpl(
    database: ItsTime4ServiceDatabase,
    private val dispatcher: CoroutineDispatcher = databaseDispatcher,
) : ServiceHistoryRepository {

    private val queries = database.serviceHistoryQueries

    override fun observeHistory(carId: String): Flow<List<ServiceHistoryEntry>> =
        queries.selectByCar(carId).asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllHistory(): Flow<List<ServiceHistoryEntry>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun addEntry(
        carId: String,
        taskName: String,
        date: LocalDate,
        mileageKm: Int,
        serviceName: String?,
        costRub: Int?,
        comment: String?,
    ): Unit = withContext(dispatcher) {
        queries.insert(
            id = Uuid.random().toString(),
            car_id = carId,
            task_name = taskName,
            date = date.toString(),
            mileage_km = mileageKm.toLong(),
            cost_rub = costRub?.toLong(),
            service_name = serviceName,
            task_id = null,
            comment = comment,
            prev_last_done_km = null,
            prev_last_done_date = null,
        )
    }
}
