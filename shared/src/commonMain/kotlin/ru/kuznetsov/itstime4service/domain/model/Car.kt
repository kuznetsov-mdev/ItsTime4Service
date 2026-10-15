package ru.kuznetsov.itstime4service.domain.model

/**
 * A tracked car with its current odometer reading and the maintenance tasks scheduled for it.
 */
data class Car(
    val id: String,
    val brand: String,
    val mileageKm: Int,
    val vin: String?,
    val tasks: List<MaintenanceTask>,
)
