package ru.kuznetsov.itstime4service.domain.model

/**
 * A service history record together with the car it belongs to, which may be sold.
 */
data class HistoryRecord(
    val entry: ServiceHistoryEntry,
    val car: CarSummary,
)
