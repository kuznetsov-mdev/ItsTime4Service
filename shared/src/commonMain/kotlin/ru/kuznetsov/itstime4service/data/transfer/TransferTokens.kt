package ru.kuznetsov.itstime4service.data.transfer

import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferGroup
import ru.kuznetsov.itstime4service.domain.transfer.TransferKind

internal object TransferTokens {
    fun group(value: TransferGroup): String = when (value) {
        TransferGroup.MILEAGE -> "mileage"
        TransferGroup.VIN -> "vin"
        TransferGroup.PLATE -> "plate"
        TransferGroup.LAST_SERVICE -> "lastService"
        TransferGroup.HISTORY -> "history"
        TransferGroup.HISTORY_DETAILS -> "historyDetails"
        TransferGroup.PREFERENCES -> "preferences"
        TransferGroup.ROLLBACK -> "rollback"
        TransferGroup.REMINDER_STATE -> "reminderState"
    }

    fun group(value: String): TransferGroup = TransferGroup.entries.firstOrNull { group(it) == value } ?: invalid()

    fun kind(value: TransferKind): String = when (value) {
        TransferKind.BACKUP -> "backup"
        TransferKind.CAR -> "car"
    }

    fun kind(value: String): TransferKind = TransferKind.entries.firstOrNull { kind(it) == value } ?: invalid()

    fun icon(value: TaskIcon): String = when (value) {
        TaskIcon.WRENCH -> "wrench"
        TaskIcon.DROPLET -> "droplet"
        TaskIcon.DISC -> "disc"
        TaskIcon.FILTER -> "filter"
        TaskIcon.ZAP -> "zap"
        TaskIcon.BATTERY -> "battery"
        TaskIcon.THERMOMETER -> "thermometer"
        TaskIcon.TIRE -> "tire"
        TaskIcon.WIND -> "wind"
        TaskIcon.GEAR -> "gear"
        TaskIcon.GAUGE -> "gauge"
        TaskIcon.CAR -> "car"
    }

    fun icon(value: String): TaskIcon = TaskIcon.entries.firstOrNull { icon(it) == value } ?: invalid()

    fun theme(value: ThemeMode): String = when (value) {
        ThemeMode.LIGHT -> "light"
        ThemeMode.DARK -> "dark"
        ThemeMode.SYSTEM -> "system"
    }

    fun theme(value: String): ThemeMode = ThemeMode.entries.firstOrNull { theme(it) == value } ?: invalid()

    fun distance(value: DistanceUnit): String = when (value) {
        DistanceUnit.KILOMETERS -> "kilometers"
        DistanceUnit.MILES -> "miles"
    }

    fun distance(value: String): DistanceUnit = DistanceUnit.entries.firstOrNull { distance(it) == value } ?: invalid()

    fun carsView(value: CarsView): String = when (value) {
        CarsView.LIST -> "list"
        CarsView.TASKS -> "tasks"
        CarsView.TASKS_AND_HISTORY -> "tasksAndHistory"
    }

    fun carsView(value: String): CarsView = CarsView.entries.firstOrNull { carsView(it) == value } ?: invalid()

    private fun invalid(): Nothing = throw SnapshotRejected(TransferFailure.InvalidSnapshot)
}
