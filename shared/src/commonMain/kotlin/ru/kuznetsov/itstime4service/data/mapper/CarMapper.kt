package ru.kuznetsov.itstime4service.data.mapper

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.data.local.Cars
import ru.kuznetsov.itstime4service.data.local.Maintenance_tasks
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.TaskIcon

internal fun Cars.toDomain(tasks: List<Maintenance_tasks>) = Car(
    id = id,
    brand = brand,
    mileageKm = mileage_km.toInt(),
    vin = vin,
    tasks = tasks.map { it.toDomain() },
    plate = plate,
)

internal fun Cars.toSummary() = CarSummary(
    id = id,
    brand = brand,
    mileageKm = mileage_km.toInt(),
    isSold = is_sold,
)

internal fun Maintenance_tasks.toDomain() = MaintenanceTask(
    id = id,
    name = name,
    intervalKm = interval_km.toInt(),
    lastDoneKm = last_done_km.toInt(),
    reminderWindowKm = reminder_window_km.toInt(),
    intervalMonths = interval_months?.toInt(),
    lastDoneDate = last_done_date?.let(LocalDate::parse),
    icon = TaskIcon.entries.firstOrNull { it.name == icon },
)
