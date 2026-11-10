package ru.kuznetsov.itstime4service.domain.transfer

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import kotlin.time.Instant

sealed interface Presence<out T> {
    data object Absent : Presence<Nothing>
    data class Present<T>(val value: T) : Presence<T>
}

enum class TransferKind { BACKUP, CAR }

enum class TransferGroup {
    MILEAGE, VIN, PLATE, LAST_SERVICE, HISTORY, HISTORY_DETAILS, PREFERENCES, ROLLBACK, REMINDER_STATE
}

data class LastService(val mileageKm: Int?, val date: LocalDate?)

data class HistoryDetails(val costRub: Int?, val serviceName: String?, val comment: String?)

data class RollbackData(val previous: LastService)

data class TransferTask(
    val id: String,
    val name: String,
    val intervalKm: Int,
    val intervalMonths: Int?,
    val icon: TaskIcon?,
    val lastService: Presence<LastService>,
    val reminderWindowKm: Presence<Int>,
)

data class TransferHistory(
    val id: String,
    val taskId: String?,
    val taskName: String,
    val date: LocalDate,
    val mileageKm: Int,
    val details: Presence<HistoryDetails>,
    val rollback: Presence<RollbackData?>,
)

data class CarPreferences(val plateHidden: Boolean, val vinHidden: Boolean)

data class TransferCar(
    val id: String,
    val brand: String,
    val maintenanceDescription: String?,
    val sold: Presence<Boolean>,
    val mileageKm: Presence<Int>,
    val vin: Presence<String?>,
    val plate: Presence<String?>,
    val preferences: Presence<CarPreferences>,
    val tasks: List<TransferTask>,
    val history: Presence<List<TransferHistory>>,
)

data class TransferReminder(val taskId: String, val shownAt: Instant)

data class TransferSnapshot(
    val formatVersion: Int,
    val snapshotId: String,
    val createdAt: Instant,
    val kind: TransferKind,
    val includedGroups: Set<TransferGroup>,
    val cars: List<TransferCar>,
    val settings: Presence<Settings>,
    val reminderState: Presence<List<TransferReminder>>,
)
