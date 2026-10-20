package ru.kuznetsov.itstime4service.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarSummary

/**
 * Source of truth for cars and their maintenance tasks; writes that also touch the service history go through
 * [TaskHistoryRepository].
 * [observeCars] and [observeCarById] see only cars that are not sold; [observeCarById] emits `null` when there
 * is no such car. [observeCarSummaries] lists every car, sold ones last.
 * [deleteTask] removes a task and keeps its history records.
 * [setPlateHidden] and [setVinHidden] store whether the plate or the VIN is kept out of sight.
 */
interface CarRepository {
    fun observeCars(): Flow<List<Car>>

    fun observeCarById(carId: String): Flow<Car?>

    fun observeCarSummaries(): Flow<List<CarSummary>>

    suspend fun updateMileage(carId: String, mileageKm: Int)

    suspend fun updateCar(carId: String, brand: String, vin: String?, plate: String?)

    suspend fun deleteTask(carId: String, taskId: String)

    suspend fun setPlateHidden(carId: String, hidden: Boolean)

    suspend fun setVinHidden(carId: String, hidden: Boolean)
}
