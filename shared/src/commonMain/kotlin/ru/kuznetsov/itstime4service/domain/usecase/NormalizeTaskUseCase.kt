package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import kotlin.math.roundToInt

/**
 * Validates a task entered in the car form and brings it to the shape that is stored.
 * Returns `null` when the name is blank or the mileage interval is not positive.
 * The name is trimmed; a task without a reminder window gets 15% of its interval;
 * a non-positive time interval counts as absent; a last-done date is kept only for tasks with a time interval.
 */
class NormalizeTaskUseCase {
    operator fun invoke(task: NewMaintenanceTask): NewMaintenanceTask? {
        val name = task.name.trim()
        if (name.isEmpty() || task.intervalKm <= 0) return null
        val intervalMonths = task.intervalMonths?.takeIf { it > 0 }
        return task.copy(
            name = name,
            reminderWindowKm = task.reminderWindowKm ?: (task.intervalKm * DEFAULT_REMINDER_SHARE).roundToInt(),
            intervalMonths = intervalMonths,
            lastDoneDate = task.lastDoneDate.takeIf { intervalMonths != null },
        )
    }

    private companion object {
        const val DEFAULT_REMINDER_SHARE = 0.15
    }
}
