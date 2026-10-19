package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.rule.TaskHistoryRules

/**
 * Finds the car's tasks that count as just marked done and, for each, the history record the mark created.
 * A task is marked while it has a record from a done mark at the car's current mileage and the task's
 * last-done mileage still equals it, so changing the mileage or editing the task ends the mark.
 * Records without a task or without the previous values to roll back to are never a mark.
 * The rule itself is [TaskHistoryRules.doneMarks], shared with the storage that removes a mark.
 */
class DoneMarksUseCase {
    operator fun invoke(car: Car, history: List<ServiceHistoryEntry>): Map<String, ServiceHistoryEntry> =
        TaskHistoryRules.doneMarks(car, history)
}
