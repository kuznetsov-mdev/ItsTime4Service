package ru.kuznetsov.itstime4service.domain.rule

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry

/**
 * How a maintenance task is tied to its service history records. The rules are pure, so the screens and the storage,
 * which applies them inside the transaction that writes the change, share one definition.
 * `history` is a car's records in the order they were added.
 *
 * The linked record of a task is the latest record of that task at its last-done mileage, by date and then by the
 * order the records were added: the record of its last service, made by a done mark or together with the new task.
 * A done mark is a linked record made by marking the task done, with the values to roll back to, while the task
 * and the record still stand at the car's current mileage.
 */
object TaskHistoryRules {

    fun linkedEntry(history: List<ServiceHistoryEntry>, task: MaintenanceTask): ServiceHistoryEntry? =
        history.withIndex()
            .filter { (_, entry) -> entry.taskId == task.id && entry.mileageKm == task.lastDoneKm }
            .maxWithOrNull(compareBy({ it.value.date }, { it.index }))
            ?.value

    fun doneMarks(car: Car, history: List<ServiceHistoryEntry>): Map<String, ServiceHistoryEntry> {
        val marks = history.filter { it.carId == car.id && it.mileageKm == car.mileageKm && it.prevLastDoneKm != null }
        return car.tasks
            .filter { task -> task.lastDoneKm == car.mileageKm }
            .mapNotNull { task -> marks.lastOrNull { it.taskId == task.id }?.let { task.id to it } }
            .toMap()
    }

    fun entryAfterTaskEdit(
        history: List<ServiceHistoryEntry>,
        original: MaintenanceTask,
        lastDoneKm: Int,
        recordDate: LocalDate?,
    ): ServiceHistoryEntry? {
        val entry = linkedEntry(history, original) ?: return null
        return entry.copy(
            mileageKm = lastDoneKm.takeIf { it > 0 && it != original.lastDoneKm } ?: entry.mileageKm,
            date = recordDate?.takeIf { it != original.lastDoneDate } ?: entry.date,
        ).takeIf { it != entry }
    }

    fun taskFollowsEntryEdit(
        history: List<ServiceHistoryEntry>,
        original: ServiceHistoryEntry,
        edited: ServiceHistoryEntry,
        task: MaintenanceTask?,
    ): Boolean {
        if (task == null || original.taskId != task.id) return false
        if (original.mileageKm == edited.mileageKm && original.date == edited.date) return false
        return linkedEntry(history, task)?.id == original.id
    }
}
