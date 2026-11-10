package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.kuznetsov.itstime4service.data.local.Cars
import ru.kuznetsov.itstime4service.data.local.Maintenance_tasks
import ru.kuznetsov.itstime4service.data.local.Service_history
import ru.kuznetsov.itstime4service.data.mapper.toDomain
import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.domain.transfer.CarPreferences
import ru.kuznetsov.itstime4service.domain.transfer.ExportSelection
import ru.kuznetsov.itstime4service.domain.transfer.HistoryDetails
import ru.kuznetsov.itstime4service.domain.transfer.LastService
import ru.kuznetsov.itstime4service.domain.transfer.Presence
import ru.kuznetsov.itstime4service.domain.transfer.RollbackData
import ru.kuznetsov.itstime4service.domain.transfer.TransferCar
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferGroup
import ru.kuznetsov.itstime4service.domain.transfer.TransferHistory
import ru.kuznetsov.itstime4service.domain.transfer.TransferKind
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits
import ru.kuznetsov.itstime4service.domain.transfer.TransferReminder
import ru.kuznetsov.itstime4service.domain.transfer.TransferSnapshot
import ru.kuznetsov.itstime4service.domain.transfer.TransferTask
import kotlin.time.Instant
import ru.kuznetsov.itstime4service.data.local.Settings as SettingsRow

internal class TransferSnapshotMapper(private val selection: ExportSelection) {
    private val backup = selection == ExportSelection.FullBackup
    private val groups = when (selection) {
        ExportSelection.FullBackup -> TransferGroup.entries.toSet()
        is ExportSelection.Car -> selection.personalGroups
    }

    fun map(
        snapshotId: String,
        createdAt: Instant,
        cars: List<Cars>,
        tasks: List<Maintenance_tasks>,
        history: List<Service_history>,
        owners: Map<String, String>,
        settings: SettingsRow?,
        reminders: List<TransferReminder>,
    ): TransferSnapshot = snapshotValue {
        valid(createdAt.toLocalDateTime(TimeZone.UTC).year in 1..9999)
        val carIds = cars.map { it.id }.toSet()
        valid(tasks.all { it.car_id in carIds } && history.all { it.car_id in carIds })
        val tasksByCar = tasks.groupBy { it.car_id }
        val historyByCar = history.groupBy { it.car_id }
        val taskIds = tasks.withIndex().associate { (index, task) -> task.id to if (backup) uuid(task.id) else "t${index + 1}" }
        val deletedIds = mutableMapOf<String, String>()
        val mappedCars = cars.map { car ->
            val carTasks = tasksByCar[car.id].orEmpty()
            valid(!car.is_sold || carTasks.isEmpty())
            TransferCar(
                id = if (backup) uuid(car.id) else "c1",
                brand = text(car.brand, TransferLimits.MAX_NAME_BYTES),
                maintenanceDescription = when (val choice = selection) {
                    ExportSelection.FullBackup -> car.maintenance_description
                    is ExportSelection.Car -> choice.maintenanceDescription
                }?.let { text(it, TransferLimits.MAX_DESCRIPTION_BYTES) },
                sold = present(TransferGroup.PREFERENCES) { car.is_sold },
                mileageKm = present(TransferGroup.MILEAGE) { car.mileage_km.number() },
                vin = present(TransferGroup.VIN) { car.vin?.let { text(it, TransferLimits.MAX_IDENTIFIER_BYTES) } },
                plate = present(TransferGroup.PLATE) { car.plate?.let { text(it, TransferLimits.MAX_IDENTIFIER_BYTES) } },
                preferences = present(TransferGroup.PREFERENCES) { CarPreferences(car.plate_hidden, car.vin_hidden) },
                tasks = carTasks.map { task ->
                    TransferTask(
                        id = taskIds.getValue(task.id),
                        name = text(task.name, TransferLimits.MAX_NAME_BYTES),
                        intervalKm = task.interval_km.number(minimum = 1),
                        intervalMonths = task.interval_months?.number(1, TransferLimits.MAX_INTERVAL_MONTHS),
                        icon = task.icon?.let(TaskIcon::valueOf),
                        lastService = present(TransferGroup.LAST_SERVICE) { LastService(task.last_done_km?.number(), date(task.last_done_date)) },
                        reminderWindowKm = present(TransferGroup.PREFERENCES) { task.reminder_window_km.number() },
                    )
                },
                history = present(TransferGroup.HISTORY) {
                    historyByCar[car.id].orEmpty().mapIndexed { index, entry ->
                        val linkedTask = entry.task_id?.let { sourceId ->
                            valid(owners[sourceId] == null || owners[sourceId] == car.id)
                            if (backup) uuid(sourceId) else taskIds[sourceId]
                                ?: deletedIds.getOrPut(sourceId) { "d${deletedIds.size + 1}" }
                        }
                        TransferHistory(
                            id = if (backup) uuid(entry.id) else "h${index + 1}",
                            taskId = linkedTask,
                            taskName = text(entry.task_name, TransferLimits.MAX_NAME_BYTES),
                            date = requireNotNull(date(entry.date)),
                            mileageKm = entry.mileage_km.number(),
                            details = present(TransferGroup.HISTORY_DETAILS) {
                                HistoryDetails(
                                    entry.cost_rub?.number(),
                                    entry.service_name?.let { text(it, TransferLimits.MAX_NAME_BYTES) },
                                    entry.comment?.let { text(it, TransferLimits.MAX_DESCRIPTION_BYTES) },
                                )
                            },
                            rollback = present(TransferGroup.ROLLBACK) {
                                if (entry.rollback_available) RollbackData(LastService(entry.prev_last_done_km?.number(), date(entry.prev_last_done_date))) else null
                            },
                        )
                    }
                },
            )
        }
        TransferSnapshot(
            formatVersion = TransferLimits.FORMAT_VERSION,
            snapshotId = snapshotId,
            createdAt = createdAt,
            kind = if (backup) TransferKind.BACKUP else TransferKind.CAR,
            includedGroups = groups.toSet(),
            cars = mappedCars,
            settings = present(TransferGroup.PREFERENCES) { settings?.validated() ?: Settings.DEFAULT },
            reminderState = present(TransferGroup.REMINDER_STATE) { reminders.toList() },
        )
    }

    private fun SettingsRow.validated(): Settings {
        ThemeMode.valueOf(theme_mode)
        DistanceUnit.valueOf(distance_unit)
        CarsView.valueOf(cars_view)
        default_reminder_window_km.number()
        last_opened_car_id?.let(::uuid)
        default_car_id?.let(::uuid)
        val settings = toDomain()
        valid(settings.reminderTime.nanosecond == 0)
        return settings
    }

    private fun <T> present(group: TransferGroup, value: () -> T): Presence<T> =
        if (group in groups) Presence.Present(value()) else Presence.Absent

    private fun Long.number(minimum: Int = 0, maximum: Int = Int.MAX_VALUE): Int {
        valid(this in minimum.toLong()..maximum.toLong())
        return toInt()
    }

    private fun text(value: String, maximum: Int): String {
        if (value.length > maximum) throw SnapshotRejected(TransferFailure.LimitExceeded)
        if (value.encodeToByteArray(throwOnInvalidSequence = true).size > maximum) throw SnapshotRejected(TransferFailure.LimitExceeded)
        return value
    }

    private fun date(value: String?): LocalDate? = value?.let {
        LocalDate.parse(it).also { parsed -> valid(parsed.year in 1..9999) }
    }

    private fun uuid(value: String): String {
        valid(UUID_PATTERN.matches(value))
        return value
    }

    private fun valid(condition: Boolean) {
        if (!condition) throw SnapshotRejected(TransferFailure.InvalidSnapshot)
    }

    private companion object {
        val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

internal class SnapshotRejected(val reason: TransferFailure) : RuntimeException()

internal inline fun <T> snapshotValue(block: () -> T): T = try {
    block()
} catch (_: IllegalArgumentException) {
    throw SnapshotRejected(TransferFailure.InvalidSnapshot)
}
