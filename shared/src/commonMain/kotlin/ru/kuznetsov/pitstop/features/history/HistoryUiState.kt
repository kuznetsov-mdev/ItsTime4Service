package ru.kuznetsov.pitstop.features.history

import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.ServiceHistoryEntry
import ru.kuznetsov.pitstop.ui.format.parseDisplayDate

/**
 * State of the service history screen — one list for every car.
 * [cars] fills the car picker; [selectedCarId] is `null` for "all cars".
 * [years] holds the records that pass the car choice and the [filter], newest first, grouped by year
 * and month; [hasEntries] tells an empty history apart from a filter that matched nothing.
 * [filterDraft] is non-null while the filter window is open, [entryForm] while a record is open.
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val cars: List<HistoryCar> = emptyList(),
    val selectedCarId: String? = null,
    val isCarMenuOpen: Boolean = false,
    val filter: HistoryFilter = HistoryFilter(),
    val filterDraft: HistoryFilter? = null,
    val years: List<HistoryYear> = emptyList(),
    val hasEntries: Boolean = false,
    val entryForm: HistoryEntryForm? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
) {
    val selectedCar: HistoryCar? get() = cars.firstOrNull { it.id == selectedCarId }
}

/**
 * A car as an owner of history records.
 * [isSold] marks a car that was deleted while its history was kept.
 */
data class HistoryCar(
    val id: String,
    val brand: String,
    val isSold: Boolean = false,
)

/**
 * The history filter as typed in its window: a date range, a search string for the task name
 * and a mileage range in the unit chosen in settings. Empty fields do not filter; all filled conditions
 * must hold together.
 * A date that is typed but cannot be read makes the filter invalid: it is not applied, and once
 * [showErrors] is on such a date is outlined in red.
 */
data class HistoryFilter(
    val dateFrom: String = "",
    val dateTo: String = "",
    val query: String = "",
    val mileageFrom: String = "",
    val mileageTo: String = "",
    val showErrors: Boolean = false,
) {
    val isDateFromInvalid: Boolean get() = dateFrom.isNotBlank() && parseDisplayDate(dateFrom) == null
    val isDateToInvalid: Boolean get() = dateTo.isNotBlank() && parseDisplayDate(dateTo) == null
    val isValid: Boolean get() = !isDateFromInvalid && !isDateToInvalid

    val isActive: Boolean
        get() = dateFrom.isNotBlank() || dateTo.isNotBlank() || query.isNotBlank() ||
            mileageFrom.isNotBlank() || mileageTo.isNotBlank()
}

/**
 * The records of one year, months newest first.
 */
data class HistoryYear(
    val year: Int,
    val months: List<HistoryMonth>,
)

/**
 * The records of one month ([month] is 1..12), newest first.
 */
data class HistoryMonth(
    val month: Int,
    val rows: List<HistoryRow>,
)

/**
 * One history record together with the car it belongs to.
 */
data class HistoryRow(
    val entry: ServiceHistoryEntry,
    val car: HistoryCar,
)

/**
 * The editable copy of a record shown in its window; everything is kept as text, the mileage in the unit
 * chosen in settings.
 * The date and the mileage are required; [showErrors] turns on after a save attempt with either of them
 * invalid and outlines them in red.
 */
data class HistoryEntryForm(
    val entryId: String,
    val taskName: String,
    val car: HistoryCar,
    val date: String,
    val mileage: String,
    val serviceName: String = "",
    val cost: String = "",
    val showErrors: Boolean = false,
) {
    val isDateInvalid: Boolean get() = parseDisplayDate(date) == null
    val isMileageInvalid: Boolean get() = (mileage.toIntOrNull() ?: 0) <= 0
    val isValid: Boolean get() = !isDateInvalid && !isMileageInvalid
}
