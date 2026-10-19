package ru.kuznetsov.itstime4service.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.data.mapper.toDomain
import ru.kuznetsov.itstime4service.data.mapper.toSummary
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * [CarRepository] backed by the SQLDelight database.
 * Cars and tasks keep the order they were added in; every write that touches more than one row
 * runs in a single transaction. A sold car stays in `cars` with `is_sold` set and is skipped by the queries
 * for active cars.
 */
@OptIn(ExperimentalUuidApi::class)
class CarRepositoryImpl(database: ItsTime4ServiceDatabase) : CarRepository {

    private val carQueries = database.carQueries
    private val taskQueries = database.maintenanceTaskQueries
    private val historyQueries = database.serviceHistoryQueries
    private val dispatcher = Dispatchers.Default

    override fun observeCars(): Flow<List<Car>> = combine(
        carQueries.selectAll().asFlow().mapToList(dispatcher),
        taskQueries.selectAll().asFlow().mapToList(dispatcher),
    ) { cars, tasks ->
        val tasksByCar = tasks.groupBy { it.car_id }
        cars.map { it.toDomain(tasksByCar[it.id].orEmpty()) }
    }

    override fun observeCarById(carId: String): Flow<Car?> = combine(
        carQueries.selectById(carId).asFlow().mapToOneOrNull(dispatcher),
        taskQueries.selectByCar(carId).asFlow().mapToList(dispatcher),
    ) { car, tasks -> car?.toDomain(tasks) }

    override suspend fun addCar(car: NewCar): String = withContext(dispatcher) {
        val carId = Uuid.random().toString()
        carQueries.transaction {
            carQueries.insert(carId, car.brand, car.mileageKm.toLong(), car.vin, car.plate)
            car.tasks.forEach { insertTask(carId, it) }
        }
        carId
    }

    override fun observeCarSummaries(): Flow<List<CarSummary>> =
        carQueries.selectAllWithSold().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toSummary() } }

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

    override suspend fun updateMileage(carId: String, mileageKm: Int): Unit = withContext(dispatcher) {
        carQueries.updateMileage(mileageKm.toLong(), carId)
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

    override suspend fun unmarkTaskDone(carId: String, taskId: String, entryId: String): Boolean = withContext(dispatcher) {
        carQueries.transactionWithResult {
            val entry = historyQueries.selectById(entryId).executeAsOneOrNull() ?: return@transactionWithResult false
            val prevLastDoneKm = entry.prev_last_done_km
            if (entry.car_id != carId || entry.task_id != taskId || prevLastDoneKm == null) return@transactionWithResult false
            taskQueries.markDone(prevLastDoneKm, entry.prev_last_done_date, taskId, carId)
            historyQueries.deleteById(entryId)
            true
        }
    }

    override suspend fun updateCar(carId: String, brand: String, vin: String?, plate: String?): Unit = withContext(dispatcher) {
        carQueries.updateDetails(brand, vin, plate, carId)
    }

    override suspend fun addTask(carId: String, task: NewMaintenanceTask): Unit = withContext(dispatcher) {
        taskQueries.transaction { insertTask(carId, task) }
    }

    override suspend fun updateTask(carId: String, task: MaintenanceTask, entry: ServiceHistoryEntry?): Unit =
        withContext(dispatcher) {
            taskQueries.transaction {
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
                if (entry != null) {
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
            }
        }

    override suspend fun deleteTask(carId: String, taskId: String): Unit = withContext(dispatcher) {
        taskQueries.deleteById(taskId, carId)
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
