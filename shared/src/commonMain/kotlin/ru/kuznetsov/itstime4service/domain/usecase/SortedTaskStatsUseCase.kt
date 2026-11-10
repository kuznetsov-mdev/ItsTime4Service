package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.TaskWithStat

/**
 * Lists a car's tasks with their derived state, most urgent first:
 * due, recommended, soon, then ok; within one status — the fewer km remaining, the higher.
 */
class SortedTaskStatsUseCase(
    private val calculateTaskStatus: CalculateTaskStatusUseCase,
) {
    operator fun invoke(car: Car, today: LocalDate): List<TaskWithStat> =
        car.tasks
            .map { task -> TaskWithStat(task, calculateTaskStatus(car.mileageKm, task, today)) }
            .sortedWith(
                compareBy<TaskWithStat> { it.stat.status.priority }.thenBy { it.stat.remainingKm ?: Int.MAX_VALUE },
            )
}
