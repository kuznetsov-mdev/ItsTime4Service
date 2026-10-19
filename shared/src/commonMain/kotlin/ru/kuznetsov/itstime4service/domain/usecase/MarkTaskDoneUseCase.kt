package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.repository.TaskHistoryRepository

/**
 * Marks a task as done at the car's current mileage on the given date and adds a matching record
 * to the service history with the optional service name, cost and comment, in one transaction.
 * Blank texts and a non-positive cost are stored as `null`.
 * Returns `false` and changes nothing when the car or the task does not exist.
 */
class MarkTaskDoneUseCase(
    private val taskHistoryRepository: TaskHistoryRepository,
) {
    suspend operator fun invoke(
        carId: String,
        taskId: String,
        today: LocalDate,
        serviceName: String? = null,
        costRub: Int? = null,
        comment: String? = null,
    ): Boolean = taskHistoryRepository.markTaskDone(
        carId = carId,
        taskId = taskId,
        date = today,
        serviceName = serviceName.orNullIfBlank(),
        costRub = costRub?.takeIf { it > 0 },
        comment = comment.orNullIfBlank(),
    )
}
