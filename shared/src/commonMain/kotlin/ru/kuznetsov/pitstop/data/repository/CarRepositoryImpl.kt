package ru.kuznetsov.pitstop.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import ru.kuznetsov.pitstop.data.local.PitStopDatabase
import ru.kuznetsov.pitstop.data.mapper.toDomain
import ru.kuznetsov.pitstop.domain.model.Car
import ru.kuznetsov.pitstop.domain.model.MaintenanceTask
import ru.kuznetsov.pitstop.domain.model.NewCar
import ru.kuznetsov.pitstop.domain.model.NewMaintenanceTask
import ru.kuznetsov.pitstop.domain.repository.CarRepository

/**
 * [CarRepository] backed by the SQLDelight database.
 * Cars and tasks keep the order they were added in; every write that touches more than one row
 * runs in a single transaction.
 */
@OptIn(ExperimentalUuidApi::class)
class CarRepositoryImpl(database: PitStopDatabase) : CarRepository {

    private val carQueries = database.carQueries
    private val taskQueries = database.maintenanceTaskQueries
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
            carQueries.insert(carId, car.brand, car.mileageKm.toLong(), car.vin)
            car.tasks.forEach { insertTask(carId, it) }
        }
        carId
    }

    override suspend fun deleteCar(carId: String): Unit = withContext(dispatcher) {
        carQueries.transaction {
            taskQueries.deleteByCar(carId)
            carQueries.deleteById(carId)
        }
    }

    override suspend fun updateMileage(carId: String, mileageKm: Int): Unit = withContext(dispatcher) {
        carQueries.updateMileage(mileageKm.toLong(), carId)
    }

    override suspend fun markTaskDone(carId: String, taskId: String, date: LocalDate): Unit = withContext(dispatcher) {
        carQueries.transaction {
            val car = carQueries.selectById(carId).executeAsOneOrNull() ?: return@transaction
            val task = taskQueries.selectById(taskId, carId).executeAsOneOrNull() ?: return@transaction
            val lastDoneDate = if (task.interval_months != null) date.toString() else task.last_done_date
            taskQueries.markDone(car.mileage_km, lastDoneDate, taskId, carId)
        }
    }

    override suspend fun updateCar(carId: String, brand: String, vin: String?): Unit = withContext(dispatcher) {
        carQueries.updateDetails(brand, vin, carId)
    }

    override suspend fun addTask(carId: String, task: NewMaintenanceTask): Unit = withContext(dispatcher) {
        insertTask(carId, task)
    }

    override suspend fun updateTask(carId: String, task: MaintenanceTask): Unit = withContext(dispatcher) {
        taskQueries.update(
            name = task.name,
            interval_km = task.intervalKm.toLong(),
            last_done_km = task.lastDoneKm.toLong(),
            reminder_window_km = task.reminderWindowKm.toLong(),
            interval_months = task.intervalMonths?.toLong(),
            last_done_date = task.lastDoneDate?.toString(),
            id = task.id,
            car_id = carId,
        )
    }

    override suspend fun deleteTask(carId: String, taskId: String): Unit = withContext(dispatcher) {
        taskQueries.deleteById(taskId, carId)
    }

    private fun insertTask(carId: String, task: NewMaintenanceTask) {
        taskQueries.insert(
            id = Uuid.random().toString(),
            car_id = carId,
            name = task.name,
            interval_km = task.intervalKm.toLong(),
            last_done_km = task.lastDoneKm.toLong(),
            reminder_window_km = (task.reminderWindowKm ?: 0).toLong(),
            interval_months = task.intervalMonths?.toLong(),
            last_done_date = task.lastDoneDate?.toString(),
        )
    }
}
