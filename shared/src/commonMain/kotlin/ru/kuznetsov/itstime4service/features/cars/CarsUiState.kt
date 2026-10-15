package ru.kuznetsov.itstime4service.features.cars

import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.TaskStatus

/**
 * State of the "My cars" screen.
 * [isLoading] stays `true` until the first list arrives, so the empty state is not flashed on start.
 */
data class CarsUiState(
    val isLoading: Boolean = true,
    val cars: List<CarItem> = emptyList(),
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
)

/**
 * One car row of the list.
 * [alertCount] is the number of soon and due tasks shown inside the gauge ring,
 * [gaugeValue] is the filled share of the ring (0..1) and [status] picks its color.
 */
data class CarItem(
    val id: String,
    val brand: String,
    val mileageKm: Int,
    val alertCount: Int,
    val gaugeValue: Float,
    val status: TaskStatus,
)
