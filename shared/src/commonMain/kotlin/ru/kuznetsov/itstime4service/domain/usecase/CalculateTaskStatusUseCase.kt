package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.TaskStat
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import kotlin.math.roundToInt

/**
 * Derives a task's state from the car's mileage and today's date.
 * The status is the worse of the mileage-based and the time-based one; the time-based one applies
 * only when the task has both an interval in months and a last-done date.
 * The reminder window in months is the mileage window scaled to the time interval, at least one month.
 */
class CalculateTaskStatusUseCase {
    operator fun invoke(mileageKm: Int, task: MaintenanceTask, today: LocalDate): TaskStat {
        val dueAtKm = task.lastDoneKm + task.intervalKm
        val remainingKm = dueAtKm - mileageKm
        val kmStatus = statusOf(remainingKm, task.reminderWindowKm)
        val progress = if (task.intervalKm > 0) {
            ((mileageKm - task.lastDoneKm).toFloat() / task.intervalKm).coerceIn(0f, 1f)
        } else {
            0f
        }

        val intervalMonths = task.intervalMonths
        val lastDoneDate = task.lastDoneDate
        if (intervalMonths == null || intervalMonths <= 0 || lastDoneDate == null) {
            return TaskStat(dueAtKm, remainingKm, remainingMonths = null, status = kmStatus, progress = progress)
        }

        val reminderMonths = if (task.intervalKm > 0) {
            maxOf(1, (intervalMonths * task.reminderWindowKm.toDouble() / task.intervalKm).roundToInt())
        } else {
            1
        }
        val remainingMonths = intervalMonths - monthsBetween(lastDoneDate, today)
        val timeStatus = statusOf(remainingMonths, reminderMonths)
        return TaskStat(dueAtKm, remainingKm, remainingMonths, maxOf(kmStatus, timeStatus), progress)
    }

    private fun statusOf(remaining: Int, reminderWindow: Int): TaskStatus = when {
        remaining <= 0 -> TaskStatus.DUE
        remaining <= reminderWindow -> TaskStatus.SOON
        else -> TaskStatus.OK
    }

    private fun monthsBetween(from: LocalDate, to: LocalDate): Int {
        var months = (to.year - from.year) * 12 + (to.month.number - from.month.number)
        if (to.day < from.day) months -= 1
        return maxOf(0, months)
    }
}
