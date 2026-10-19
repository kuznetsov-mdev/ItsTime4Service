package ru.kuznetsov.itstime4service.data.repository

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.data.mapper.toDomain
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.repository.TaskHistoryRepository
import ru.kuznetsov.itstime4service.domain.rule.TaskHistoryRules

/**
 * [TaskHistoryRepository] backed by the SQLDelight database.
 * Each operation is one transaction: it reads the rows it decides by — the task before the edit, the car's history,
 * the done mark — and writes the change inside it, so a change made in between cannot be overwritten.
 * New cars, tasks and records get random ids; the car's history is read in the order the records were added.
 */
@OptIn(ExperimentalUuidApi::class)
class TaskHistoryRepositoryImpl(database: ItsTime4ServiceDatabase) : TaskHistoryRepository {

    private val carQueries = database.carQueries
    private val taskQueries = database.maintenanceTaskQueries
    private val historyQueries = database.serviceHistoryQueries
    private val dispatcher = Dispatchers.Default

    override suspend fun addCar(car: NewCar): String = withContext(dispatcher) {
        val carId = Uuid.random().toString()
        carQueries.transaction {
            carQueries.insert(carId, car.brand, car.mileageKm.toLong(), car.vin, car.plate)
            car.tasks.forEach { insertTask(carId, it) }
        }
        carId
    }

    override suspend fun sellCar(carId: String): Unit = withContext(dispatcher) {
        carQueries.transaction {
            taskQueries.deleteByCar(carId)
            if (historyQueries.countByCar(carId).executeAsOne() > 0) {
                carQueries.markSold(carId)
            } else {
                carQueries.deleteById(carId)
            }
        }
    }

    override suspend fun purgeCar(carId: String): Unit = withContext(dispatcher) {
        carQueries.transaction {
            historyQueries.deleteByCar(carId)
            taskQueries.deleteByCar(carId)
            carQueries.deleteById(carId)
        }
    }

    override suspend fun addTask(carId: String, task: NewMaintenanceTask): Unit = withContext(dispatcher) {
        taskQueries.transaction { insertTask(carId, task) }
    }

    override suspend fun updateTask(carId: String, task: MaintenanceTask, recordDate: LocalDate?): Unit = withContext(dispatcher) {
        taskQueries.transaction {
            val original = taskQueries.selectById(task.id, carId).executeAsOneOrNull()?.toDomain()
            val entry = original?.let { TaskHistoryRules.entryAfterTaskEdit(history(carId), it, task.lastDoneKm, recordDate) }
            taskQueries.update(
                name = task.name,
                interval_km = task.intervalKm.toLong(),
                last_done_km = task.lastDoneKm.toLong(),
                reminder_window_km = task.reminderWindowKm.toLong(),
                interval_months = task.intervalMonths?.toLong(),
                last_done_date = task.lastDoneDate?.toString(),
                icon = task.icon?.name,
                id = task.id,
                car_id = carId,
            )
            if (entry != null) updateEntryRow(entry)
        }
    }

    override suspend fun markTaskDone(
        carId: String,
        taskId: String,
        date: LocalDate,
        serviceName: String?,
        costRub: Int?,
        comment: String?,
    ): Boolean = withContext(dispatcher) {
        carQueries.transactionWithResult {
            val car = carQueries.selectById(carId).executeAsOneOrNull() ?: return@transactionWithResult false
            val task = taskQueries.selectById(taskId, carId).executeAsOneOrNull() ?: return@transactionWithResult false
            val lastDoneDate = if (task.interval_months != null) date.toString() else task.last_done_date
            taskQueries.markDone(car.mileage_km, lastDoneDate, taskId, carId)
            historyQueries.insert(
                id = Uuid.random().toString(),
                car_id = carId,
                task_name = task.name,
                date = date.toString(),
                mileage_km = car.mileage_km,
                cost_rub = costRub?.toLong(),
                service_name = serviceName,
                task_id = taskId,
                comment = comment,
                prev_last_done_km = task.last_done_km,
                prev_last_done_date = task.last_done_date,
            )
            true
        }
    }

    override suspend fun unmarkTaskDone(carId: String, taskId: String): Boolean = withContext(dispatcher) {
        carQueries.transactionWithResult {
            val car = carQueries.selectById(carId).executeAsOneOrNull() ?: return@transactionWithResult false
            val tasks = taskQueries.selectByCar(carId).executeAsList()
            val entry = TaskHistoryRules.doneMarks(car.toDomain(tasks), history(carId))[taskId]
                ?: return@transactionWithResult false
            val prevLastDoneKm = entry.prevLastDoneKm ?: return@transactionWithResult false
            taskQueries.markDone(prevLastDoneKm.toLong(), entry.prevLastDoneDate?.toString(), taskId, carId)
            historyQueries.deleteById(entry.id)
            true
        }
    }

    override suspend fun updateEntry(entry: ServiceHistoryEntry): Boolean = withContext(dispatcher) {
        historyQueries.transactionWithResult {
            val original = historyQueries.selectById(entry.id).executeAsOneOrNull()?.toDomain()
                ?: return@transactionWithResult false
            val stored = original.copy(
                date = entry.date,
                mileageKm = entry.mileageKm,
                costRub = entry.costRub,
                serviceName = entry.serviceName,
                comment = entry.comment,
            )
            val task = original.taskId?.let { taskQueries.selectById(it, original.carId).executeAsOneOrNull()?.toDomain() }
            val taskFollows = TaskHistoryRules.taskFollowsEntryEdit(history(original.carId), original, stored, task)
            updateEntryRow(stored)
            if (taskFollows && task != null) {
                val lastDoneDate = if (task.intervalMonths != null) stored.date else task.lastDoneDate
                taskQueries.markDone(stored.mileageKm.toLong(), lastDoneDate?.toString(), task.id, original.carId)
            }
            true
        }
    }

    private fun history(carId: String): List<ServiceHistoryEntry> =
        historyQueries.selectByCar(carId).executeAsList().map { it.toDomain() }

    private fun updateEntryRow(entry: ServiceHistoryEntry) {
        historyQueries.update(
            task_name = entry.taskName,
            date = entry.date.toString(),
            mileage_km = entry.mileageKm.toLong(),
            cost_rub = entry.costRub?.toLong(),
            service_name = entry.serviceName,
            comment = entry.comment,
            id = entry.id,
        )
    }

    private fun insertTask(carId: String, task: NewMaintenanceTask) {
        val taskId = Uuid.random().toString()
        taskQueries.insert(
            id = taskId,
            car_id = carId,
            name = task.name,
            interval_km = task.intervalKm.toLong(),
            last_done_km = task.lastDoneKm.toLong(),
            reminder_window_km = (task.reminderWindowKm ?: 0).toLong(),
            interval_months = task.intervalMonths?.toLong(),
            last_done_date = task.lastDoneDate?.toString(),
            icon = task.icon?.name,
        )
        val record = task.record ?: return
        historyQueries.insert(
            id = Uuid.random().toString(),
            car_id = carId,
            task_name = task.name,
            date = record.date.toString(),
            mileage_km = task.lastDoneKm.toLong(),
            cost_rub = record.costRub?.toLong(),
            service_name = record.serviceName,
            task_id = taskId,
            comment = record.comment,
            prev_last_done_km = null,
            prev_last_done_date = null,
        )
    }
}
