package ru.kuznetsov.itstime4service.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry

/**
 * Source of truth for cars and their maintenance tasks.
 * [observeCars] and [observeCarById] see only cars that are not sold; [observeCarById] emits `null` when there
 * is no such car. [observeCarSummaries] lists every car, sold ones last.
 * [sellCar] deletes the car's tasks and keeps the car as sold, so its service history stays; a car without
 * history records is deleted completely instead. [purgeCar] deletes the car, sold or not, its tasks and its
 * whole history. Both run in a single transaction.
 * [addCar] assigns ids to the car and its tasks and returns the car's id; a task without a reminder window
 * must already have one resolved by the caller.
 * [markTaskDone] resets the task to the car's current mileage and, for tasks with a time interval, to [date],
 * and adds a service history record that remembers the task and its previous last-done values;
 * it returns `false` and changes nothing when the car or the task does not exist.
 * [unmarkTaskDone] gives the task back the previous values kept in the record [entryId] and deletes the record;
 * it returns `false` and changes nothing when that record was not created by marking this task.
 * Both run in a single transaction.
 * [addCar] and [addTask] store a task's [NewMaintenanceTask.record] as a history record of that task, at its last-done
 * mileage, without the values for rolling back a mark; [updateTask] with an `entry` also updates that history record.
 * Each runs in a single transaction.
 */
interface CarRepository {
    fun observeCars(): Flow<List<Car>>

    fun observeCarById(carId: String): Flow<Car?>

    fun observeCarSummaries(): Flow<List<CarSummary>>

    suspend fun addCar(car: NewCar): String

    suspend fun sellCar(carId: String)

    suspend fun purgeCar(carId: String)

    suspend fun updateMileage(carId: String, mileageKm: Int)

    suspend fun markTaskDone(
        carId: String,
        taskId: String,
        date: LocalDate,
        serviceName: String?,
        costRub: Int?,
        comment: String?,
    ): Boolean

    suspend fun unmarkTaskDone(carId: String, taskId: String, entryId: String): Boolean

    suspend fun updateCar(carId: String, brand: String, vin: String?, plate: String?)

    suspend fun addTask(carId: String, task: NewMaintenanceTask)

    suspend fun updateTask(carId: String, task: MaintenanceTask, entry: ServiceHistoryEntry? = null)

    suspend fun deleteTask(carId: String, taskId: String)
}
