package ru.kuznetsov.pitstop.domain.usecase

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import ru.kuznetsov.pitstop.domain.model.Car
import ru.kuznetsov.pitstop.domain.model.PlannedReminder
import ru.kuznetsov.pitstop.domain.model.Settings
import ru.kuznetsov.pitstop.domain.model.TaskStatus

/**
 * Builds the notification plan for the coming days.
 * Returns nothing when push notifications are switched off.
 * Notifications fire at the reminder time from settings, starting today when that time is still ahead
 * and tomorrow otherwise, and cover [HORIZON_DAYS] days.
 * A task gets a notification on every day it is soon or due at the car's current mileage, but not sooner
 * than [REPEAT_DAYS] days after the previous one about it; [lastShown] holds the day of the last notification
 * already shown for a task id. The plan is ordered by time and cut to the first [MAX_REMINDERS].
 */
class PlanRemindersUseCase(
    private val calculateTaskStatus: CalculateTaskStatusUseCase,
) {
    operator fun invoke(
        cars: List<Car>,
        settings: Settings,
        now: LocalDateTime,
        lastShown: Map<String, LocalDate>,
    ): List<PlannedReminder> {
        if (!settings.pushEnabled) return emptyList()
        val firstDay = if (now.time < settings.reminderTime) now.date else now.date.plus(1, DateTimeUnit.DAY)
        val nextAllowedDay = lastShown.mapValues { it.value.plus(REPEAT_DAYS, DateTimeUnit.DAY) }.toMutableMap()
        val plan = mutableListOf<PlannedReminder>()
        for (offset in 0 until HORIZON_DAYS) {
            val day = firstDay.plus(offset, DateTimeUnit.DAY)
            for (car in cars) {
                for (task in car.tasks) {
                    val stat = calculateTaskStatus(car.mileageKm, task, day)
                    val allowedFrom = nextAllowedDay[task.id]
                    if (stat.status == TaskStatus.OK || (allowedFrom != null && day < allowedFrom)) continue
                    if (plan.size == MAX_REMINDERS) return plan
                    plan += PlannedReminder(
                        carId = car.id,
                        taskId = task.id,
                        carBrand = car.brand,
                        taskName = task.name,
                        status = stat.status,
                        remainingKm = stat.remainingKm,
                        remainingMonths = stat.remainingMonths,
                        fireAt = LocalDateTime(day, settings.reminderTime),
                    )
                    nextAllowedDay[task.id] = day.plus(REPEAT_DAYS, DateTimeUnit.DAY)
                }
            }
        }
        return plan
    }

    companion object {
        const val HORIZON_DAYS = 30
        const val REPEAT_DAYS = 3
        const val MAX_REMINDERS = 60
    }
}
