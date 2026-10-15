package ru.kuznetsov.pitstop.data.mapper

import kotlinx.datetime.LocalDate
import ru.kuznetsov.pitstop.data.local.Service_history
import ru.kuznetsov.pitstop.domain.model.ServiceHistoryEntry

internal fun Service_history.toDomain() = ServiceHistoryEntry(
    id = id,
    carId = car_id,
    taskName = task_name,
    date = LocalDate.parse(date),
    mileageKm = mileage_km.toInt(),
    costRub = cost_rub?.toInt(),
    serviceName = service_name,
    comment = comment,
    taskId = task_id,
    prevLastDoneKm = prev_last_done_km?.toInt(),
    prevLastDoneDate = prev_last_done_date?.let(LocalDate::parse),
)
