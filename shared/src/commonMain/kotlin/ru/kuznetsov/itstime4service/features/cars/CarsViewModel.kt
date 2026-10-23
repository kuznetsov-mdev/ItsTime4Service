package ru.kuznetsov.itstime4service.features.cars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
import ru.kuznetsov.itstime4service.domain.usecase.SetPlateHiddenUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SortedTaskStatsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateSettingsUseCase
import ru.kuznetsov.itstime4service.ui.error.attempt
import ru.kuznetsov.itstime4service.ui.error.reloadable
import ru.kuznetsov.itstime4service.ui.format.deviceRegion
import ru.kuznetsov.itstime4service.ui.format.toLook

/**
 * Drives the "My cars" screen in one of three views (see [CarsView]); the chosen view is stored in settings.
 * A row shows the gauge summarizing a car's soon and due tasks; a car with nothing to attend to still gets
 * a thin sliver of the ring, so the gauge never looks empty. A card shows the status ring, the four most urgent
 * tasks, the four latest history records and the licence plate. A tap on the plate hides it or shows it again; the choice
 * is stored with the car (see [SetPlateHiddenUseCase]).
 * A failed write raises the "could not save" bar until [onSaveFailureShown]; a failed read shows the load error until
 * [onRetry] reads the data again.
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
    private val setPlateHidden: SetPlateHiddenUseCase,
) : ViewModel() {

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val saveFailed = MutableStateFlow(false)

    val state: StateFlow<CarsUiState> = combine(
        reloadable(retries, CarsUiState(isLoading = false, loadFailed = true)) {
            combine(observeCars(), observeAllHistory(), observeSettings()) { cars, history, settings ->
                val today = getToday()
                val historyByCar = history.groupBy { it.car.id }
                CarsUiState(
                    isLoading = false,
                    cars = cars.map { it.toItem(today, historyByCar[it.id].orEmpty()) },
                    distanceUnit = settings.distanceUnit,
                    view = settings.carsView,
                )
            }
        },
        saveFailed,
    ) { state, failed -> state.copy(saveFailed = failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CarsUiState())

    fun onViewSelect(view: CarsView) = save { updateSettings { it.copy(carsView = view) } }

    fun onPlateToggle(carId: String) {
        val car = state.value.cars.firstOrNull { it.id == carId } ?: return
        save { setPlateHidden(carId, !car.plateHidden) }
    }

    fun onSaveFailureShown() = saveFailed.update { false }

    fun onRetry() {
        retries.tryEmit(Unit)
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { attempt(block).onFailure { saveFailed.value = true } }
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
            plateHidden = plateHidden,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val EMPTY_GAUGE_VALUE = 0.06f
        const val CARD_LINES = 4
    }
}
