package ru.kuznetsov.itstime4service.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.data.mapper.toDomain
import ru.kuznetsov.itstime4service.data.mapper.toSummary
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * [CarRepository] backed by the SQLDelight database.
 * Cars and tasks keep the order they were added in. A sold car stays in `cars` with `is_sold` set and is skipped
 * by the queries for active cars.
 */
class CarRepositoryImpl(database: ItsTime4ServiceDatabase) : CarRepository {

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

    override fun observeCarSummaries(): Flow<List<CarSummary>> =
        carQueries.selectAllWithSold().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toSummary() } }

    override suspend fun updateMileage(carId: String, mileageKm: Int): Unit = withContext(dispatcher) {
        carQueries.updateMileage(mileageKm.toLong(), carId)
    }

    override suspend fun updateCar(carId: String, brand: String, vin: String?, plate: String?): Unit = withContext(dispatcher) {
        carQueries.updateDetails(brand, vin, plate, carId)
    }

    override suspend fun deleteTask(carId: String, taskId: String): Unit = withContext(dispatcher) {
        taskQueries.deleteById(taskId, carId)
    }

    override suspend fun setPlateHidden(carId: String, hidden: Boolean): Unit = withContext(dispatcher) {
        carQueries.setPlateHidden(hidden, carId)
    }

    override suspend fun setVinHidden(carId: String, hidden: Boolean): Unit = withContext(dispatcher) {
        carQueries.setVinHidden(hidden, carId)
    }
}
