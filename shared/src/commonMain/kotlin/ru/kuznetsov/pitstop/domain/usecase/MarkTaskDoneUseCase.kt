package ru.kuznetsov.pitstop.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import ru.kuznetsov.pitstop.domain.model.NewServiceHistoryEntry
import ru.kuznetsov.pitstop.domain.repository.CarRepository
import ru.kuznetsov.pitstop.domain.repository.ServiceHistoryRepository

/**
 * Marks a task as done at the car's current mileage on the given date
 * and adds a matching record to the service history with the optional service name and cost.
 * A blank service name and a non-positive cost are stored as `null`.
 * Returns `false` and changes nothing when the car or the task does not exist.
 */
class MarkTaskDoneUseCase(
    private val carRepository: CarRepository,
    private val serviceHistoryRepository: ServiceHistoryRepository,
) {
    suspend operator fun invoke(
        carId: String,
        taskId: String,
        today: LocalDate,
        serviceName: String? = null,
        costRub: Int? = null,
    ): Boolean {
        val car = carRepository.observeCarById(carId).first() ?: return false
        val task = car.tasks.firstOrNull { it.id == taskId } ?: return false
        carRepository.markTaskDone(carId, taskId, today)
        serviceHistoryRepository.addEntry(
            NewServiceHistoryEntry(
                carId = carId,
                taskName = task.name,
                date = today,
                mileageKm = car.mileageKm,
                costRub = costRub?.takeIf { it > 0 },
                serviceName = serviceName?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        return true
    }
}
