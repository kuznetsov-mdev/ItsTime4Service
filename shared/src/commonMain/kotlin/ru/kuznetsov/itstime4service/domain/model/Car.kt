package ru.kuznetsov.itstime4service.domain.model

/**
 * A tracked car with its current odometer reading and the maintenance tasks scheduled for it.
 * [plate] is the licence plate as typed, or `null` when it was not entered.
 * [plateHidden] and [vinHidden] keep the plate and the VIN out of sight on every screen until the user shows them again.
 */
data class Car(
    val id: String,
    val brand: String,
    val mileageKm: Int,
    val vin: String?,
    val tasks: List<MaintenanceTask>,
    val plate: String? = null,
    val plateHidden: Boolean = false,
    val vinHidden: Boolean = false,
)
