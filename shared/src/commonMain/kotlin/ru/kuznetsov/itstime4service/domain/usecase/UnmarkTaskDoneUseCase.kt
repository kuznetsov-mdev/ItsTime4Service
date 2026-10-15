package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.first
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository

/**
 * Removes a task's done mark: the task gets back its previous last-done mileage and date and the history
 * record created by the mark is deleted, in one transaction.
 * Returns `false` and changes nothing when the car does not exist or the task does not count as marked
 * (see [DoneMarksUseCase]).
 */
class UnmarkTaskDoneUseCase(
    private val carRepository: CarRepository,
    private val serviceHistoryRepository: ServiceHistoryRepository,
    private val doneMarks: DoneMarksUseCase,
) {
    suspend operator fun invoke(carId: String, taskId: String): Boolean {
        val car = carRepository.observeCarById(carId).first() ?: return false
        val history = serviceHistoryRepository.observeHistory(carId).first()
        val entry = doneMarks(car, history)[taskId] ?: return false
        return carRepository.unmarkTaskDone(carId, taskId, entry.id)
    }
}
