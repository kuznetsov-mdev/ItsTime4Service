package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.repository.TaskHistoryRepository

/**
 * Removes a task's done mark: the task gets back its previous last-done mileage and date and the history
 * record created by the mark is deleted, in one transaction.
 * Returns `false` and changes nothing when the car does not exist or the task does not count as marked
 * (see [ru.kuznetsov.itstime4service.domain.rule.TaskHistoryRules]); the mark is looked up inside the transaction.
 */
class UnmarkTaskDoneUseCase(
    private val taskHistoryRepository: TaskHistoryRepository,
) {
    suspend operator fun invoke(carId: String, taskId: String): Boolean = taskHistoryRepository.unmarkTaskDone(carId, taskId)
}
