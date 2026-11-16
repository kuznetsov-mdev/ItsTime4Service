package ru.kuznetsov.itstime4service.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
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
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ReminderRepository {

    private val queries = database.plannedReminderQueries

    override suspend fun getRecords(): List<ReminderRecord> = withContext(dispatcher) {
        val zone = timeZone()
        queries.selectAll().executeAsList().map {
            val shown = it.shown_at_utc?.let(Instant::parse)
            ReminderRecord(it.task_id, shown?.toLocalDateTime(zone) ?: LocalDateTime.parse(it.fire_at), shown)
        }
    }

    override suspend fun replaceRecords(records: List<ReminderRecord>): Unit = withContext(dispatcher) {
        queries.transaction {
            queries.deleteAll()
            records.forEach { queries.insertShown(it.taskId, it.fireAt.toString(), it.shownAt?.toString()) }
        }
    }
}
