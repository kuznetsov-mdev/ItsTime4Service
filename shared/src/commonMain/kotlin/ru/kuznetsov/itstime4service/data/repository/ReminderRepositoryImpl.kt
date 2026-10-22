package ru.kuznetsov.itstime4service.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.domain.model.ReminderRecord
import ru.kuznetsov.itstime4service.domain.repository.ReminderRepository

/**
 * [ReminderRepository] backed by the SQLDelight database.
 * Fire times are stored as ISO text; the list is replaced in a single transaction.
 */
class ReminderRepositoryImpl(
    database: ItsTime4ServiceDatabase,
    private val dispatcher: CoroutineDispatcher = databaseDispatcher,
) : ReminderRepository {

    private val queries = database.plannedReminderQueries

    override suspend fun getRecords(): List<ReminderRecord> = withContext(dispatcher) {
        queries.selectAll().executeAsList().map { ReminderRecord(it.task_id, LocalDateTime.parse(it.fire_at)) }
    }

    override suspend fun replaceRecords(records: List<ReminderRecord>): Unit = withContext(dispatcher) {
        queries.transaction {
            queries.deleteAll()
            records.forEach { queries.insert(it.taskId, it.fireAt.toString()) }
        }
    }
}
