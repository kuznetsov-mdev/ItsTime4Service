package ru.kuznetsov.pitstop.data.mapper

import kotlinx.datetime.LocalDate
import ru.kuznetsov.pitstop.data.local.Cars
import ru.kuznetsov.pitstop.data.local.Maintenance_tasks
import ru.kuznetsov.pitstop.domain.model.Car
import ru.kuznetsov.pitstop.domain.model.MaintenanceTask

internal fun Cars.toDomain(tasks: List<Maintenance_tasks>) = Car(
    id = id,
    brand = brand,
    mileageKm = mileage_km.toInt(),
    vin = vin,
    tasks = tasks.map { it.toDomain() },
)

internal fun Maintenance_tasks.toDomain() = MaintenanceTask(
    id = id,
    name = name,
    intervalKm = interval_km.toInt(),
    lastDoneKm = last_done_km.toInt(),
    reminderWindowKm = reminder_window_km.toInt(),
    intervalMonths = interval_months?.toInt(),
    lastDoneDate = last_done_date?.let(LocalDate::parse),
)
