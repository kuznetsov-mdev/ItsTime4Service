package ru.kuznetsov.itstime4service.domain.repository

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry

/**
 * Every write that changes a car's tasks and its service history together. Each runs in a single transaction,
 * and whatever it needs to decide — the linked record, a done mark, whether the car has history — is read inside
 * that transaction, by the rules of [ru.kuznetsov.itstime4service.domain.rule.TaskHistoryRules].
 * [addCar] stores the car and its tasks with their ids and returns the car's id; [addTask] adds a task to an existing
 * car. A task with a [NewMaintenanceTask.record] gets a history record of its last service at its last-done mileage,
 * without the values for rolling back a mark. A task without a reminder window must already have one resolved.
 * [sellCar] deletes the car's tasks and keeps the car as sold with its history; a car without history records is
 * deleted completely. [purgeCar] deletes the car, sold or not, its tasks and its whole history.
 * [updateTask] replaces the task with the same id; when its last-done mileage or [recordDate] — the date typed for
 * the last service, kept even for a task without a time interval — changes, the task's linked record follows.
 * [markTaskDone] sets the task to the car's current mileage and, for a task with a time interval, to [date], and
 * adds a record that remembers the task's previous last-done values; `false` when the car or the task is missing.
 * [unmarkTaskDone] gives the task back those values and deletes the record; `false` when the task is not marked.
 * [updateEntry] edits a record's date, mileage, cost, service name and comment and keeps its task name, task and
 * rollback values; when it is its task's linked record and its mileage or date changes, the task gets the record's
 * mileage and, for a task with a time interval, its date. `false` when there is no such record.
 */
interface TaskHistoryRepository {
    suspend fun addCar(car: NewCar): String

    suspend fun sellCar(carId: String)

    suspend fun purgeCar(carId: String)

    suspend fun addTask(carId: String, task: NewMaintenanceTask)

    suspend fun updateTask(carId: String, task: MaintenanceTask, recordDate: LocalDate?)

    suspend fun markTaskDone(
        carId: String,
        taskId: String,
        date: LocalDate,
        serviceName: String?,
        costRub: Int?,
        comment: String?,
    ): Boolean

    suspend fun unmarkTaskDone(carId: String, taskId: String): Boolean

    suspend fun updateEntry(entry: ServiceHistoryEntry): Boolean
}
