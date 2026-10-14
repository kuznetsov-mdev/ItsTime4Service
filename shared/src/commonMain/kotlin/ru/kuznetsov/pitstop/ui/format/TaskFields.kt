package ru.kuznetsov.pitstop.ui.format

import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.MaintenanceTask
import ru.kuznetsov.pitstop.domain.model.NewMaintenanceTask

/**
 * The fields of one maintenance task as typed in a form — in a task block of the add-car form
 * and on the task screen. Everything is kept as text; numbers and the date are parsed only on save.
 * Distances are typed in the unit chosen in settings and converted to kilometers on save; a distance that was
 * shown and left as it was keeps its stored kilometers, so rounding back and forth does not shift it.
 * The name, the mileage interval and the last-done mileage are required; a last-done mileage of zero
 * is valid and means the task has not been done yet.
 */
data class TaskFields(
    val name: String = "",
    val intervalKm: String = "",
    val intervalMonths: String = "",
    val lastDoneKm: String = "",
    val lastDoneDate: String = "",
    val reminderWindowKm: String = "",
) {
    val isNameMissing: Boolean get() = name.isBlank()
    val isIntervalMissing: Boolean get() = (intervalKm.toIntOrNull() ?: 0) <= 0
    val isLastDoneMissing: Boolean get() = lastDoneKm.toIntOrNull() == null
    val isValid: Boolean get() = !isNameMissing && !isIntervalMissing && !isLastDoneMissing

    fun sanitized(): TaskFields = copy(
        intervalKm = intervalKm.digits(),
        intervalMonths = intervalMonths.digits(),
        lastDoneKm = lastDoneKm.digits(),
        reminderWindowKm = reminderWindowKm.digits(),
    )

    fun toNewTask(unit: DistanceUnit, original: MaintenanceTask? = null): NewMaintenanceTask? {
        if (!isValid) return null
        return NewMaintenanceTask(
            name = name,
            intervalKm = keepKm(original?.intervalKm, intervalKm.toInt(), unit),
            lastDoneKm = keepKm(original?.lastDoneKm, lastDoneKm.toInt(), unit),
            reminderWindowKm = reminderWindowKm.toIntOrNull()?.let { keepKm(original?.reminderWindowKm, it, unit) },
            intervalMonths = intervalMonths.toIntOrNull(),
            lastDoneDate = parseDisplayDate(lastDoneDate),
        )
    }

    private fun String.digits(): String = filter(Char::isDigit).take(MAX_NUMBER_LENGTH)

    private companion object {
        const val MAX_NUMBER_LENGTH = 7
    }
}

fun MaintenanceTask.toFields(unit: DistanceUnit): TaskFields = TaskFields(
    name = name,
    intervalKm = intervalKm.kmIn(unit).toString(),
    intervalMonths = intervalMonths?.toString().orEmpty(),
    lastDoneKm = lastDoneKm.kmIn(unit).toString(),
    lastDoneDate = lastDoneDate?.toDisplayText().orEmpty(),
    reminderWindowKm = reminderWindowKm.kmIn(unit).toString(),
)
