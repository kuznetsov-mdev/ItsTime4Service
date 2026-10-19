package ru.kuznetsov.itstime4service.features.cars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.HistoryRecord
import ru.kuznetsov.itstime4service.domain.usecase.CarAlertsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.DetectPlateStyleUseCase
import ru.kuznetsov.itstime4service.domain.usecase.GetTodayUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveAllHistoryUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SortedTaskStatsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateSettingsUseCase
import ru.kuznetsov.itstime4service.ui.format.deviceRegion
import ru.kuznetsov.itstime4service.ui.format.toLook

/**
 * Drives the "My cars" screen in one of three views (see [CarsView]); the chosen view is stored in settings.
 * A row shows the gauge summarizing a car's soon and due tasks; a car with nothing to attend to still gets
 * a thin sliver of the ring, so the gauge never looks empty. A card shows the status ring, the four most urgent
 * tasks, the four latest history records and the licence plate.
 */
class CarsViewModel(
    observeCars: ObserveCarsUseCase,
    observeAllHistory: ObserveAllHistoryUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val carAlerts: CarAlertsUseCase,
    private val sortedTaskStats: SortedTaskStatsUseCase,
    private val detectPlateStyle: DetectPlateStyleUseCase,
    private val getToday: GetTodayUseCase,
) : ViewModel() {

    val state: StateFlow<CarsUiState> = combine(observeCars(), observeAllHistory(), observeSettings()) { cars, history, settings ->
        val today = getToday()
        val historyByCar = history.groupBy { it.car.id }
        CarsUiState(
            isLoading = false,
            cars = cars.map { it.toItem(today, historyByCar[it.id].orEmpty()) },
            distanceUnit = settings.distanceUnit,
            view = settings.carsView,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CarsUiState())

    fun onViewSelect(view: CarsView) {
        viewModelScope.launch { updateSettings { it.copy(carsView = view) } }
    }

    private fun Car.toItem(today: LocalDate, history: List<HistoryRecord>): CarItem {
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
            dueCount = alerts.due,
            soonCount = alerts.soon,
            taskCount = alerts.taskCount,
            upcomingTasks = sortedTaskStats(this, today).take(CARD_LINES).map { (task, stat) ->
                UpcomingTask(name = task.name, remainingKm = stat.remainingKm, status = stat.status)
            },
            recentRecords = history.take(CARD_LINES).map { RecentRecord(taskName = it.entry.taskName, date = it.entry.date) },
            plateLook = plate?.let { detectPlateStyle(it, deviceRegion()).toLook(it) },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val EMPTY_GAUGE_VALUE = 0.06f
        const val CARD_LINES = 4
    }
}
