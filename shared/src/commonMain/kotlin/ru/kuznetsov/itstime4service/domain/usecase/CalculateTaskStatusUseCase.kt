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
        val dueAtKm = task.lastDoneKm?.let { it + task.intervalKm }
        val remainingKm = dueAtKm?.minus(mileageKm)
        val progress = task.lastDoneKm?.let { lastDone ->
            if (task.intervalKm > 0) ((mileageKm - lastDone).toFloat() / task.intervalKm).coerceIn(0f, 1f) else 0f
        }
        val intervalMonths = task.intervalMonths?.takeIf { it > 0 }
        val remainingMonths = intervalMonths?.let { months -> task.lastDoneDate?.let { months - monthsBetween(it, today) } }
        val reminderMonths = if (intervalMonths != null && task.intervalKm > 0) {
            maxOf(1, (intervalMonths * task.reminderWindowKm.toDouble() / task.intervalKm).roundToInt())
        } else 1
        val kmStatus = remainingKm?.let { statusOf(it, task.reminderWindowKm) }
        val timeStatus = remainingMonths?.let { statusOf(it, reminderMonths) }
        val missing = task.lastDoneKm == null || (intervalMonths != null && task.lastDoneDate == null)
        val status = when {
            kmStatus == TaskStatus.DUE || timeStatus == TaskStatus.DUE -> TaskStatus.DUE
            missing -> TaskStatus.RECOMMENDED
            kmStatus == TaskStatus.SOON || timeStatus == TaskStatus.SOON -> TaskStatus.SOON
            else -> TaskStatus.OK
        }
        val knownReminder = kmStatus == TaskStatus.SOON || kmStatus == TaskStatus.DUE ||
            timeStatus == TaskStatus.SOON || timeStatus == TaskStatus.DUE
        return TaskStat(dueAtKm, remainingKm, remainingMonths, status, progress, knownReminder)
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
