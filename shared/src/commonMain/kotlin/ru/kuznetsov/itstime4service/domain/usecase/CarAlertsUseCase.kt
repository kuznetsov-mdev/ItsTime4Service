package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarAlerts
import ru.kuznetsov.itstime4service.domain.model.TaskStatus

/**
 * Counts how many of a car's tasks are soon and how many are due,
 * which drives the car's gauge and badge in the car list.
 */
class CarAlertsUseCase(
    private val calculateTaskStatus: CalculateTaskStatusUseCase,
) {
    operator fun invoke(car: Car, today: LocalDate): CarAlerts {
        val statuses = car.tasks.map { task -> calculateTaskStatus(car.mileageKm, task, today).status }
        return CarAlerts(
            soon = statuses.count { it == TaskStatus.SOON },
            due = statuses.count { it == TaskStatus.DUE },
            taskCount = statuses.size,
        )
    }
}
