package ru.kuznetsov.itstime4service.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.usecase.AddHistoryEntryUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveAllHistoryUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarSummariesUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateHistoryEntryUseCase
import ru.kuznetsov.itstime4service.ui.error.attempt
import ru.kuznetsov.itstime4service.ui.error.reloadable
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import ru.kuznetsov.itstime4service.ui.format.keepKm
import ru.kuznetsov.itstime4service.ui.format.kmFrom
import ru.kuznetsov.itstime4service.ui.format.kmIn

/**
 * Drives the service history screen: the records of every car in one list, a car picker,
 * a filter by date range, task name and mileage range, and a window for one record.
 * [initialCarId] pre-selects a car when the screen is opened from that car's screen.
 * Which records are shown, how they are grouped and which task names are offered is decided by [HistoryRows]; the
 * view model reads the filter fields into [HistoryCriteria]. Mileage is filtered and edited in the unit chosen
 * in settings; a mileage left as it was keeps its stored kilometers.
 * A filter with a date that cannot be read is not applied: its window stays open and marks the date.
 * The record window saves the date, the mileage, the cost, the service name and the comment; an invalid date
 * or mileage keeps it open and marks the field.
 * Sold cars — deleted from their screen with the history kept — are listed after the others and marked;
 * their records can be edited like any other.
 * A record can also be added by hand for a car that is not sold: the window starts on the car chosen in the picker,
 * or the first one, and offers the car's task names as chips. Such a record is not tied to a task. Repeated taps
 * on "Add" while the record is being stored are ignored.
 * The drafts of the open windows are also given as [windows], straight from their [MutableStateFlow], so that the
 * screen's fields get typed text in the same frame. Both record windows close only once the record is stored; a failed write keeps them open with the "could not save"
 * bar until [onSaveFailureShown]. A failed read shows the load error until [onRetry] reads the data again.
 */
class HistoryViewModel(
    initialCarId: String?,
    observeCarSummaries: ObserveCarSummariesUseCase,
    observeAllHistory: ObserveAllHistoryUseCase,
    observeSettings: ObserveSettingsUseCase,
    observeCars: ObserveCarsUseCase,
    private val updateHistoryEntry: UpdateHistoryEntryUseCase,
    private val addHistoryEntry: AddHistoryEntryUseCase,
) : ViewModel() {

    private val view = MutableStateFlow(View(selectedCarId = initialCarId))
    private val _windows = MutableStateFlow(HistoryWindows())
    val windows: StateFlow<HistoryWindows> = _windows.asStateFlow()
    private var isAdding = false

    private val rows: Flow<List<HistoryRow>> = observeAllHistory().map { records ->
        records.map { HistoryRow(it.entry, it.car.toHistoryCar()) }
    }

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state: StateFlow<HistoryUiState> = reloadable(retries, HistoryUiState(isLoading = false, loadFailed = true)) {
        combine(
            observeCarSummaries(),
            rows,
            observeSettings(),
            observeCars(),
            combine(view, _windows, ::Pair),
        ) { cars, rows, settings, activeCars, (view, windows) ->
            uiState(cars, rows, settings.distanceUnit, activeCars, view, windows)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState())

    private fun uiState(
        cars: List<CarSummary>,
        rows: List<HistoryRow>,
        unit: DistanceUnit,
        activeCars: List<Car>,
        view: View,
        windows: HistoryWindows,
    ): HistoryUiState {
        val owners = cars.map { it.toHistoryCar() }
        val selectedCarId = view.selectedCarId?.takeIf { id -> owners.any { it.id == id } }
        return HistoryUiState(
            isLoading = false,
            cars = owners,
            selectedCarId = selectedCarId,
            isCarMenuOpen = view.isCarMenuOpen,
            filter = view.filter,
            filterDraft = windows.filterDraft,
            years = HistoryRows.grouped(HistoryRows.matching(rows, view.filter.toCriteria(selectedCarId, unit))),
            hasEntries = HistoryRows.ofCar(rows, selectedCarId).isNotEmpty(),
            entryForm = windows.entryForm,
            newEntry = windows.newEntry,
            newEntrySuggestions = HistoryRows.suggestions(windows.newEntry?.let { form -> activeCars.firstOrNull { it.id == form.carId } }),
            distanceUnit = unit,
            saveFailed = view.saveFailed,
        )
    }

    fun onCarMenuToggle() = view.update { it.copy(isCarMenuOpen = !it.isCarMenuOpen) }

    fun onCarMenuDismiss() = view.update { it.copy(isCarMenuOpen = false) }

    fun onCarSelect(carId: String?) = view.update { it.copy(selectedCarId = carId, isCarMenuOpen = false) }

    fun onFilterOpen() {
        view.update { it.copy(isCarMenuOpen = false) }
        _windows.update { it.copy(filterDraft = view.value.filter) }
    }

    fun onFilterChange(filter: HistoryFilter) = _windows.update {
        it.copy(
            filterDraft = filter.copy(
                dateFrom = DateInputFormat.current.digits(filter.dateFrom),
                dateTo = DateInputFormat.current.digits(filter.dateTo),
                mileageFrom = filter.mileageFrom.digits(),
                mileageTo = filter.mileageTo.digits(),
            ),
        )
    }

    fun onFilterApply() {
        val draft = _windows.value.filterDraft ?: return
        if (draft.isValid) {
            view.update { it.copy(filter = draft.copy(showErrors = false)) }
            _windows.update { it.copy(filterDraft = null) }
        } else {
            _windows.update { it.copy(filterDraft = draft.copy(showErrors = true)) }
        }
    }

    fun onFilterReset() {
        view.update { it.copy(filter = HistoryFilter()) }
        _windows.update { it.copy(filterDraft = null) }
    }

    fun onFilterDismiss() = _windows.update { it.copy(filterDraft = null) }

    fun onEntryOpen(row: HistoryRow) = _windows.update {
        it.copy(
            entryForm = HistoryEntryForm(
                entryId = row.entry.id,
                taskName = row.entry.taskName,
                car = row.car,
                date = DateInputFormat.current.digitsOf(row.entry.date),
                mileage = row.entry.mileageKm.kmIn(state.value.distanceUnit).toString(),
                serviceName = row.entry.serviceName.orEmpty(),
                cost = row.entry.costRub?.toString().orEmpty(),
                comment = row.entry.comment.orEmpty(),
            ),
        )
    }

    fun onEntryChange(form: HistoryEntryForm) = _windows.update {
        it.copy(
            entryForm = form.copy(
                date = DateInputFormat.current.digits(form.date),
                mileage = form.mileage.digits(),
                cost = form.cost.digits(),
            ),
        )
    }

    fun onEntrySave() {
        val form = _windows.value.entryForm ?: return
        val date = DateInputFormat.current.parse(form.date)
        val mileage = form.mileage.toIntOrNull()
        val original = state.value.years
            .flatMap { year -> year.months.flatMap { it.rows } }
            .firstOrNull { it.entry.id == form.entryId }
            ?.entry
        if (date == null || mileage == null || !form.isValid) {
            _windows.update { it.copy(entryForm = form.copy(showErrors = true)) }
            return
        }
        if (original == null) {
            onEntryDismiss()
            return
        }
        save {
            val saved = updateHistoryEntry(
                original.copy(
                    date = date,
                    mileageKm = keepKm(original.mileageKm, mileage, state.value.distanceUnit),
                    costRub = form.cost.toIntOrNull(),
                    serviceName = form.serviceName,
                    comment = form.comment,
                ),
            )
            if (saved) onEntryDismiss()
        }
    }

    fun onEntryDismiss() = _windows.update { it.copy(entryForm = null) }

    fun onNewEntryOpen() {
        val cars = state.value.activeCars
        val carId = cars.firstOrNull { it.id == state.value.selectedCarId }?.id ?: cars.firstOrNull()?.id ?: return
        view.update { it.copy(isCarMenuOpen = false) }
        _windows.update { it.copy(newEntry = NewHistoryEntryForm(carId = carId)) }
    }

    fun onNewEntryChange(form: NewHistoryEntryForm) = _windows.update {
        it.copy(
            newEntry = form.copy(
                date = DateInputFormat.current.digits(form.date),
                mileage = form.mileage.digits(),
                cost = form.cost.digits(),
            ),
        )
    }

    fun onNewEntrySave() {
        val form = _windows.value.newEntry ?: return
        val date = DateInputFormat.current.parse(form.date)
        val mileage = form.mileage.toIntOrNull()
        if (date == null || mileage == null || !form.isValid) {
            _windows.update { it.copy(newEntry = form.copy(showErrors = true)) }
            return
        }
        if (isAdding) return
        isAdding = true
        save(onFailure = { isAdding = false }) {
            val added = addHistoryEntry(
                carId = form.carId,
                taskName = form.taskName,
                date = date,
                mileageKm = mileage.kmFrom(state.value.distanceUnit),
                serviceName = form.serviceName,
                costRub = form.cost.toIntOrNull(),
                comment = form.comment,
            )
            isAdding = false
            if (added) onNewEntryDismiss()
        }
    }

    fun onNewEntryDismiss() = _windows.update { it.copy(newEntry = null) }

    fun onSaveFailureShown() = view.update { it.copy(saveFailed = false) }

    fun onRetry() {
        retries.tryEmit(Unit)
    }

    private fun save(onFailure: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            attempt(block).onFailure {
                onFailure()
                view.update { it.copy(saveFailed = true) }
            }
        }
    }

    private fun HistoryFilter.toCriteria(carId: String?, unit: DistanceUnit) = HistoryCriteria(
        carId = carId,
        from = DateInputFormat.current.parse(dateFrom),
        to = DateInputFormat.current.parse(dateTo),
        query = query,
        minMileage = mileageFrom.toIntOrNull(),
        maxMileage = mileageTo.toIntOrNull(),
        distanceUnit = unit,
    )

    private fun CarSummary.toHistoryCar() = HistoryCar(id = id, brand = brand, isSold = isSold)

    private fun String.digits(): String = filter(Char::isDigit).take(MAX_NUMBER_LENGTH)

    /**
     * Screen-only state that is not stored anywhere: the chosen car, the filter and whether the last write failed.
     * The drafts of the open windows live in [HistoryWindows].
     */
    private data class View(
        val selectedCarId: String? = null,
        val isCarMenuOpen: Boolean = false,
        val filter: HistoryFilter = HistoryFilter(),
        val saveFailed: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_NUMBER_LENGTH = 7
    }
}
