package ru.kuznetsov.pitstop.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import ru.kuznetsov.pitstop.domain.model.Car
import ru.kuznetsov.pitstop.domain.model.MaintenanceTask
import ru.kuznetsov.pitstop.domain.model.NewCar
import ru.kuznetsov.pitstop.domain.model.NewMaintenanceTask

/**
 * Source of truth for cars and their maintenance tasks.
 * [observeCarById] emits `null` when there is no car with the given id.
 * [addCar] assigns ids to the car and its tasks and returns the car's id; a task without a reminder window
 * must already have one resolved by the caller.
 * [markTaskDone] resets the task to the car's current mileage and, for tasks with a time interval, to [date],
 * and adds a service history record that remembers the task and its previous last-done values;
 * it returns `false` and changes nothing when the car or the task does not exist.
 * [unmarkTaskDone] gives the task back the previous values kept in the record [entryId] and deletes the record;
 * it returns `false` and changes nothing when that record was not created by marking this task.
 * Both run in a single transaction.
 */
interface CarRepository {
    fun observeCars(): Flow<List<Car>>

    fun observeCarById(carId: String): Flow<Car?>

    suspend fun addCar(car: NewCar): String

    suspend fun deleteCar(carId: String)

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

    suspend fun updateCar(carId: String, brand: String, vin: String?)

    suspend fun addTask(carId: String, task: NewMaintenanceTask)

    suspend fun updateTask(carId: String, task: MaintenanceTask)

    suspend fun deleteTask(carId: String, taskId: String)
}
