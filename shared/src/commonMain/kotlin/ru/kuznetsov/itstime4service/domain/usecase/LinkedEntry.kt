package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry

internal fun List<ServiceHistoryEntry>.linkedEntryOf(task: MaintenanceTask): ServiceHistoryEntry? =
    withIndex()
        .filter { (_, entry) -> entry.taskId == task.id && entry.mileageKm == task.lastDoneKm }
        .maxWithOrNull(compareBy({ it.value.date }, { it.index }))
        ?.value
