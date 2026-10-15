package ru.kuznetsov.itstime4service.features.cars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.usecase.CarAlertsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.GetTodayUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase

/**
 * Drives the "My cars" screen: every car with the gauge summarizing its soon and due tasks.
 * A car with nothing to attend to still gets a thin sliver of the ring, so the gauge never looks empty.
 */
class CarsViewModel(
    observeCars: ObserveCarsUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val carAlerts: CarAlertsUseCase,
    private val getToday: GetTodayUseCase,
) : ViewModel() {

    val state: StateFlow<CarsUiState> = combine(observeCars(), observeSettings()) { cars, settings ->
        val today = getToday()
        CarsUiState(
            isLoading = false,
            cars = cars.map { it.toItem(today) },
            distanceUnit = settings.distanceUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CarsUiState())

    private fun Car.toItem(today: LocalDate): CarItem {
        val alerts = carAlerts(this, today)
        val gaugeValue = when {
            alerts.total == 0 -> EMPTY_GAUGE_VALUE
            else -> alerts.total.toFloat() / alerts.taskCount
        }
        return CarItem(
            id = id,
            brand = brand,
            mileageKm = mileageKm,
            alertCount = alerts.total,
            gaugeValue = gaugeValue,
            status = alerts.worstStatus,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val EMPTY_GAUGE_VALUE = 0.06f
    }
}
