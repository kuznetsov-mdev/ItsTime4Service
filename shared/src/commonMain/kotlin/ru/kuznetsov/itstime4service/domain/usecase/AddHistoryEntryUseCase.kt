package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository

/**
 * Adds a service history record typed by hand, for work done earlier — for example before the app was installed.
 * The record is not tied to a task: it does not change any task's last-done mileage or date and never counts
 * as a done mark. Returns `false` and adds nothing when the car does not exist or is sold, the task name is blank
 * or the mileage is not positive. The task name is trimmed; blank texts and a non-positive cost are stored as `null`.
 */
class AddHistoryEntryUseCase(
    private val serviceHistoryRepository: ServiceHistoryRepository,
    private val carRepository: CarRepository,
) {
    suspend operator fun invoke(
        carId: String,
        taskName: String,
        date: LocalDate,
        mileageKm: Int,
        serviceName: String? = null,
        costRub: Int? = null,
        comment: String? = null,
    ): Boolean {
        val name = taskName.orNullIfBlank() ?: return false
        if (mileageKm <= 0) return false
        carRepository.observeCarById(carId).first() ?: return false
        serviceHistoryRepository.addEntry(
            carId = carId,
            taskName = name,
            date = date,
            mileageKm = mileageKm,
            serviceName = serviceName.orNullIfBlank(),
            costRub = costRub?.takeIf { it > 0 },
            comment = comment.orNullIfBlank(),
        )
        return true
    }
}
