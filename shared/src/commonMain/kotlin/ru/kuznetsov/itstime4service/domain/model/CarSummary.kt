package ru.kuznetsov.itstime4service.domain.model

/**
 * A car as an owner of history records and an entry in the settings lists, without its tasks.
 * [isSold] marks a car deleted from its screen while its service history was kept; such a car has no tasks
 * and appears only in the history and in the full deletion list.
 */
data class CarSummary(
    val id: String,
    val brand: String,
    val mileageKm: Int,
    val isSold: Boolean,
)
