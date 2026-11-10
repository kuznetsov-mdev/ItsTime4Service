package ru.kuznetsov.itstime4service.data.transfer

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.db.QueryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.data.repository.databaseDispatcher
import ru.kuznetsov.itstime4service.domain.transfer.ExportSelection
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferGroup
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits
import ru.kuznetsov.itstime4service.domain.transfer.TransferOutcome
import ru.kuznetsov.itstime4service.domain.transfer.TransferReminder
import ru.kuznetsov.itstime4service.domain.transfer.TransferRepository
import ru.kuznetsov.itstime4service.domain.transfer.TransferSnapshot
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class TransferRepositoryImpl(
    private val database: ItsTime4ServiceDatabase,
    private val dispatcher: CoroutineDispatcher = databaseDispatcher,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : TransferRepository {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun readSnapshot(selection: ExportSelection): TransferOutcome<TransferSnapshot> {
        val capturedSelection = when (selection) {
            ExportSelection.FullBackup -> selection
            is ExportSelection.Car -> {
                if (!selection.isValid()) return TransferOutcome.Failure(TransferFailure.InvalidSnapshot)
                if (selection.taskIds.size > TransferLimits.MAX_TASKS) return TransferOutcome.Failure(TransferFailure.LimitExceeded)
                selection.copy(taskIds = selection.taskIds.toSet(), personalGroups = selection.personalGroups.toSet())
            }
        }
        return withContext(dispatcher) {
            val context = currentCoroutineContext()
            try {
                val snapshot = database.transactionWithResult {
                    val capturedNow = clock.now()
                    val zone = timeZone()
                    val createdAt = Instant.fromEpochMilliseconds(capturedNow.toEpochMilliseconds())
                    val cars = when (capturedSelection) {
                        ExportSelection.FullBackup -> database.carQueries.selectAllWithSold().bounded(TransferLimits.MAX_CARS, context)
                        is ExportSelection.Car -> listOfNotNull(database.carQueries.selectByIdWithSold(capturedSelection.carId).executeAsOneOrNull())
                    }
                    if (capturedSelection is ExportSelection.Car && cars.isEmpty()) throw SnapshotRejected(TransferFailure.InvalidSnapshot)
                    val tasks = when (capturedSelection) {
                        ExportSelection.FullBackup -> database.maintenanceTaskQueries.selectAll().bounded(TransferLimits.MAX_TASKS, context)
                        is ExportSelection.Car -> database.maintenanceTaskQueries.selectByCar(capturedSelection.carId)
                            .bounded(TransferLimits.MAX_TASKS, context) { it.id in capturedSelection.taskIds }
                    }
                    if (capturedSelection is ExportSelection.Car && tasks.map { it.id }.toSet() != capturedSelection.taskIds) {
                        throw SnapshotRejected(TransferFailure.InvalidSnapshot)
                    }
                    val history = when (capturedSelection) {
                        ExportSelection.FullBackup -> database.serviceHistoryQueries.selectAll().bounded(TransferLimits.MAX_HISTORY, context)
                        is ExportSelection.Car -> if (TransferGroup.HISTORY in capturedSelection.personalGroups) {
                            database.serviceHistoryQueries.selectByCar(capturedSelection.carId).bounded(TransferLimits.MAX_HISTORY, context)
                        } else emptyList()
                    }
                    val references = history.mapNotNull { it.task_id }.toSet()
                    val owners = mutableMapOf<String, String>()
                    if (references.isNotEmpty()) {
                        database.maintenanceTaskQueries.selectOwnership().execute { cursor ->
                            while (cursor.next().value) {
                                context.ensureActive()
                                val id = requireNotNull(cursor.getString(0))
                                if (id in references) owners[id] = requireNotNull(cursor.getString(1))
                            }
                            QueryResult.Value(Unit)
                        }.value
                    }
                    val settings = if (capturedSelection == ExportSelection.FullBackup) database.settingsQueries.select().executeAsOneOrNull() else null
                    val reminders = if (capturedSelection == ExportSelection.FullBackup) {
                        val activeCars = cars.filterNot { it.is_sold }.map { it.id }.toSet()
                        readReminders(tasks.filter { it.car_id in activeCars }.map { it.id }, capturedNow, createdAt, zone, context)
                    } else emptyList()
                    context.ensureActive()
                    TransferSnapshotMapper(capturedSelection).map(
                        Uuid.random().toString(), createdAt, cars, tasks, history, owners, settings, reminders,
                    )
                }
                TransferOutcome.Success(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rejected: SnapshotRejected) {
                TransferOutcome.Failure(rejected.reason)
            } catch (_: Exception) {
                TransferOutcome.Failure(TransferFailure.DatabaseReadFailed)
            }
        }
    }

    private fun readReminders(
        taskIds: List<String>,
        now: Instant,
        createdAt: Instant,
        zone: TimeZone,
        context: CoroutineContext,
    ): List<TransferReminder> {
        val active = taskIds.toSet()
        val cutoff = now.toLocalDateTime(zone)
        val latest = mutableMapOf<String, LocalDateTime>()
        database.plannedReminderQueries.selectAll().execute { cursor ->
            while (cursor.next().value) {
                context.ensureActive()
                val task = requireNotNull(cursor.getString(0))
                if (task !in active) continue
                val local = snapshotValue {
                    LocalDateTime.parse(requireNotNull(cursor.getString(1))).also { require(it.year in 1..9999) }
                }
                if (local > cutoff) continue
                val instant = Instant.fromEpochMilliseconds(local.toInstant(zone).toEpochMilliseconds())
                if (instant > createdAt) continue
                snapshotValue { require(instant.toLocalDateTime(TimeZone.UTC).year in 1..9999) }
                val previous = latest[task]
                if (previous == null || local > previous) latest[task] = local
            }
            QueryResult.Value(Unit)
        }.value
        return taskIds.mapNotNull { task ->
            latest[task]?.let { TransferReminder(task, Instant.fromEpochMilliseconds(it.toInstant(zone).toEpochMilliseconds())) }
        }
    }

    private fun <T : Any> ExecutableQuery<T>.bounded(
        limit: Int,
        context: CoroutineContext,
        include: (T) -> Boolean = { true },
    ): List<T> = execute { cursor ->
        val result = mutableListOf<T>()
        while (cursor.next().value) {
            context.ensureActive()
            val row = mapper(cursor)
            if (!include(row)) continue
            if (result.size == limit) throw SnapshotRejected(TransferFailure.LimitExceeded)
            result.add(row)
        }
        QueryResult.Value(result.toList())
    }.value
}
