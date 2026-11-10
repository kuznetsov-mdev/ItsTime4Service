package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.kuznetsov.itstime4service.domain.transfer.ExportSelection
import ru.kuznetsov.itstime4service.domain.transfer.LastService
import ru.kuznetsov.itstime4service.domain.transfer.Presence
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferGroup
import ru.kuznetsov.itstime4service.domain.transfer.TransferKind
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits
import ru.kuznetsov.itstime4service.domain.transfer.TransferSnapshot
import kotlin.time.Instant

internal object TransferValidator {
    fun validate(snapshot: TransferSnapshot) {
        if (snapshot.formatVersion != 1) throw SnapshotRejected(TransferFailure.UnsupportedVersion)
        valid(SNAPSHOT_UUID.matches(snapshot.snapshotId))
        instant(snapshot.createdAt)
        val backup = snapshot.kind == TransferKind.BACKUP
        if (backup) valid(snapshot.includedGroups == TransferGroup.entries.toSet()) else {
            valid(snapshot.cars.size == 1)
            valid(ExportSelection.Car("", emptySet(), snapshot.includedGroups).isValid())
        }
        limit(snapshot.cars.size.toLong(), TransferLimits.MAX_CARS)
        limit(snapshot.cars.sumOf { it.tasks.size.toLong() }, TransferLimits.MAX_TASKS)
        limit(snapshot.cars.sumOf { car -> when (val history = car.history) {
            Presence.Absent -> 0L
            is Presence.Present -> history.value.size.toLong()
        } }, TransferLimits.MAX_HISTORY)
        val groups = snapshot.includedGroups
        val carIds = mutableSetOf<String>()
        val taskIds = mutableMapOf<String, String>()
        val historyIds = mutableSetOf<String>()
        snapshot.cars.forEach { car ->
            valid(carIds.add(car.id))
            car.tasks.forEach { task -> valid(taskIds.put(task.id, car.id) == null) }
        }
        val deletedIds = linkedSetOf<String>()
        var seenSold = false
        snapshot.cars.forEach { car ->
            if (backup) uuid(car.id) else valid(car.id == "c1")
            text(car.brand, TransferLimits.MAX_NAME_BYTES)
            car.maintenanceDescription?.let { text(it, TransferLimits.MAX_DESCRIPTION_BYTES) }
            presence(car.sold, backup) { sold ->
                if (sold) {
                    seenSold = true
                    valid(car.tasks.isEmpty())
                } else valid(!seenSold)
            }
            presence(car.mileageKm, TransferGroup.MILEAGE in groups) { valid(it >= 0) }
            presence(car.vin, TransferGroup.VIN in groups) { it?.let { text(it, TransferLimits.MAX_IDENTIFIER_BYTES) } }
            presence(car.plate, TransferGroup.PLATE in groups) { it?.let { text(it, TransferLimits.MAX_IDENTIFIER_BYTES) } }
            presence(car.preferences, backup) { }
            car.tasks.forEachIndexed { index, task ->
                if (backup) uuid(task.id) else valid(task.id == "t${index + 1}")
                text(task.name, TransferLimits.MAX_NAME_BYTES)
                valid(task.intervalKm > 0)
                valid(task.intervalMonths == null || task.intervalMonths in 1..TransferLimits.MAX_INTERVAL_MONTHS)
                presence(task.lastService, TransferGroup.LAST_SERVICE in groups, ::service)
                presence(task.reminderWindowKm, backup) { valid(it >= 0) }
            }
            presence(car.history, TransferGroup.HISTORY in groups) { history ->
                history.forEachIndexed { index, entry ->
                    valid(historyIds.add(entry.id))
                    if (backup) uuid(entry.id) else valid(entry.id == "h${index + 1}")
                    entry.taskId?.let { id ->
                        if (backup) {
                            uuid(id)
                            valid(taskIds[id] == null || taskIds[id] == car.id)
                        } else if (id in taskIds) valid(taskIds[id] == car.id) else {
                            if (deletedIds.add(id)) valid(id == "d${deletedIds.size}")
                        }
                    }
                    text(entry.taskName, TransferLimits.MAX_NAME_BYTES)
                    date(entry.date)
                    valid(entry.mileageKm >= 0)
                    presence(entry.details, TransferGroup.HISTORY_DETAILS in groups) { details ->
                        valid(details.costRub == null || details.costRub >= 0)
                        details.serviceName?.let { text(it, TransferLimits.MAX_NAME_BYTES) }
                        details.comment?.let { text(it, TransferLimits.MAX_DESCRIPTION_BYTES) }
                    }
                    presence(entry.rollback, backup) { it?.let { rollback -> service(rollback.previous) } }
                }
            }
        }
        presence(snapshot.settings, backup) { settings ->
            valid(settings.defaultReminderWindowKm >= 0 && settings.reminderTime.nanosecond == 0)
            settings.lastOpenedCarId?.let(::uuid)
            settings.defaultCarId?.let(::uuid)
        }
        presence(snapshot.reminderState, backup) { reminders ->
            limit(reminders.size.toLong(), TransferLimits.MAX_REMINDERS)
            val ids = mutableSetOf<String>()
            reminders.forEach { reminder ->
                valid(ids.add(reminder.taskId) && reminder.taskId in taskIds)
                instant(reminder.shownAt)
                valid(reminder.shownAt <= snapshot.createdAt)
            }
        }
    }

    private fun service(value: LastService) {
        valid(value.mileageKm == null || value.mileageKm >= 0)
        value.date?.let(::date)
    }

    private fun date(value: LocalDate) = valid(value.year in 1..9999)

    private fun instant(value: Instant) {
        valid(value.nanosecondsOfSecond % 1_000_000 == 0)
        valid(value.toLocalDateTime(TimeZone.UTC).year in 1..9999)
    }

    private fun uuid(value: String) = valid(UUID.matches(value))

    private fun text(value: String, maximum: Int) {
        if (value.length > maximum || value.encodeToByteArray(throwOnInvalidSequence = true).size > maximum) {
            throw SnapshotRejected(TransferFailure.LimitExceeded)
        }
    }

    private fun <T> presence(value: Presence<T>, required: Boolean, validate: (T) -> Unit) {
        when (value) {
            Presence.Absent -> valid(!required)
            is Presence.Present -> {
                valid(required)
                validate(value.value)
            }
        }
    }

    private fun limit(value: Long, maximum: Int) {
        if (value > maximum) throw SnapshotRejected(TransferFailure.LimitExceeded)
    }

    private fun valid(condition: Boolean) {
        if (!condition) throw SnapshotRejected(TransferFailure.InvalidSnapshot)
    }

    private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val SNAPSHOT_UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
}
