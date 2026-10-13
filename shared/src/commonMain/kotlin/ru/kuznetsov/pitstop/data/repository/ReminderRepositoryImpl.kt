package ru.kuznetsov.pitstop.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import ru.kuznetsov.pitstop.data.local.PitStopDatabase
import ru.kuznetsov.pitstop.domain.model.ReminderRecord
import ru.kuznetsov.pitstop.domain.repository.ReminderRepository

/**
 * [ReminderRepository] backed by the SQLDelight database.
 * Fire times are stored as ISO text; the list is replaced in a single transaction.
 */
class ReminderRepositoryImpl(database: PitStopDatabase) : ReminderRepository {

    private val queries = database.plannedReminderQueries
    private val dispatcher = Dispatchers.Default

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
