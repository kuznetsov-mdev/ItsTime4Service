package ru.kuznetsov.pitstop.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.number
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveHistoryUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateHistoryEntryUseCase
import ru.kuznetsov.pitstop.ui.format.DateInputFormat
import ru.kuznetsov.pitstop.ui.format.keepKm
import ru.kuznetsov.pitstop.ui.format.kmIn

/**
 * Drives the service history screen: the records of every car in one list, a car picker,
 * a filter by date range, task name and mileage range, and a window for one record.
 * [initialCarId] pre-selects a car when the screen is opened from that car's screen.
 * The task name is searched as a substring, ignoring case. Mileage is filtered and edited in the unit chosen
 * in settings; a mileage left as it was keeps its stored kilometers.
 * A filter with a date that cannot be read is not applied: its window stays open and marks the date.
 * The record window saves the date, the mileage, the cost, the service name and the comment; an invalid date
 * or mileage keeps it open and marks the field.
 * Temporary until the storage supports it: deleted cars are not listed, because deleting a car still
 * deletes its history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    initialCarId: String?,
    observeCars: ObserveCarsUseCase,
    private val observeHistory: ObserveHistoryUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val updateHistoryEntry: UpdateHistoryEntryUseCase,
) : ViewModel() {

    private val view = MutableStateFlow(View(selectedCarId = initialCarId))

    private val rows: Flow<List<HistoryRow>> = observeCars().flatMapLatest { cars ->
        if (cars.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                cars.map { car ->
                    val owner = HistoryCar(id = car.id, brand = car.brand)
                    observeHistory(car.id).map { entries -> entries.map { HistoryRow(it, owner) } }
                },
            ) { perCar -> perCar.flatMap { it }.sortedByDescending { it.entry.date } }
        }
    }

    val state: StateFlow<HistoryUiState> = combine(
        observeCars(),
        rows,
        observeSettings(),
        view,
    ) { cars, rows, settings, view ->
        val owners = cars.map { HistoryCar(id = it.id, brand = it.brand) }
        val selectedCarId = view.selectedCarId?.takeIf { id -> owners.any { it.id == id } }
        val ofCar = rows.filter { selectedCarId == null || it.car.id == selectedCarId }
        HistoryUiState(
            isLoading = false,
            cars = owners,
            selectedCarId = selectedCarId,
            isCarMenuOpen = view.isCarMenuOpen,
            filter = view.filter,
            filterDraft = view.filterDraft,
            years = ofCar.filter { view.filter.matches(it, settings.distanceUnit) }.grouped(),
            hasEntries = ofCar.isNotEmpty(),
            entryForm = view.entryForm,
            distanceUnit = settings.distanceUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState())

    fun onCarMenuToggle() = view.update { it.copy(isCarMenuOpen = !it.isCarMenuOpen) }

    fun onCarMenuDismiss() = view.update { it.copy(isCarMenuOpen = false) }

    fun onCarSelect(carId: String?) = view.update { it.copy(selectedCarId = carId, isCarMenuOpen = false) }

    fun onFilterOpen() = view.update { it.copy(isCarMenuOpen = false, filterDraft = it.filter) }

    fun onFilterChange(filter: HistoryFilter) = view.update {
        it.copy(
            filterDraft = filter.copy(
                dateFrom = DateInputFormat.current.digits(filter.dateFrom),
                dateTo = DateInputFormat.current.digits(filter.dateTo),
                mileageFrom = filter.mileageFrom.digits(),
                mileageTo = filter.mileageTo.digits(),
            ),
        )
    }

    fun onFilterApply() = view.update {
        val draft = it.filterDraft ?: return@update it
        if (draft.isValid) {
            it.copy(filter = draft.copy(showErrors = false), filterDraft = null)
        } else {
            it.copy(filterDraft = draft.copy(showErrors = true))
        }
    }

    fun onFilterReset() = view.update { it.copy(filter = HistoryFilter(), filterDraft = null) }

    fun onFilterDismiss() = view.update { it.copy(filterDraft = null) }

    fun onEntryOpen(row: HistoryRow) = view.update {
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

    fun onEntryChange(form: HistoryEntryForm) = view.update {
        it.copy(
            entryForm = form.copy(
                date = DateInputFormat.current.digits(form.date),
                mileage = form.mileage.digits(),
                cost = form.cost.digits(),
            ),
        )
    }

    fun onEntrySave() {
        val form = view.value.entryForm ?: return
        val date = DateInputFormat.current.parse(form.date)
        val mileage = form.mileage.toIntOrNull()
        val original = state.value.years
            .flatMap { year -> year.months.flatMap { it.rows } }
            .firstOrNull { it.entry.id == form.entryId }
            ?.entry
        if (date == null || mileage == null || !form.isValid) {
            view.update { it.copy(entryForm = form.copy(showErrors = true)) }
            return
        }
        if (original == null) {
            onEntryDismiss()
            return
        }
        viewModelScope.launch {
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

    fun onEntryDismiss() = view.update { it.copy(entryForm = null) }

    private fun HistoryFilter.matches(row: HistoryRow, unit: DistanceUnit): Boolean {
        val entry = row.entry
        val from = DateInputFormat.current.parse(dateFrom)
        val to = DateInputFormat.current.parse(dateTo)
        val minKm = mileageFrom.toIntOrNull()
        val maxKm = mileageTo.toIntOrNull()
        val text = query.trim()
        return (from == null || entry.date >= from) &&
            (to == null || entry.date <= to) &&
            (text.isEmpty() || entry.taskName.contains(text, ignoreCase = true)) &&
            (minKm == null || entry.mileageKm.kmIn(unit) >= minKm) &&
            (maxKm == null || entry.mileageKm.kmIn(unit) <= maxKm)
    }

    private fun List<HistoryRow>.grouped(): List<HistoryYear> =
        groupBy { it.entry.date.year }.map { (year, ofYear) ->
            HistoryYear(
                year = year,
                months = ofYear.groupBy { it.entry.date.month.number }.map { (month, ofMonth) -> HistoryMonth(month, ofMonth) },
            )
        }

    private fun String.digits(): String = filter(Char::isDigit).take(MAX_NUMBER_LENGTH)

    /**
     * Screen-only state that is not stored anywhere: the chosen car, the filter and the open windows.
     */
    private data class View(
        val selectedCarId: String? = null,
        val isCarMenuOpen: Boolean = false,
        val filter: HistoryFilter = HistoryFilter(),
        val filterDraft: HistoryFilter? = null,
        val entryForm: HistoryEntryForm? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_NUMBER_LENGTH = 7
    }
}
