package ru.kuznetsov.itstime4service.features.history

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.format.kmIn

/**
 * What the history list is narrowed to, already read from the filter fields: the chosen car (`null` — every car),
 * the date range, a part of the task name and the mileage range in [distanceUnit]. Every bound is inclusive and
 * a missing one does not limit the list.
 */
internal data class HistoryCriteria(
    val carId: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val query: String = "",
    val minMileage: Int? = null,
    val maxMileage: Int? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
)

/**
 * The pure part of the history screen: which records are shown, how they are grouped and which task names are
 * offered for a new record. Rows keep the order they come in — newest first.
 * The task name is searched as a substring, ignoring case; the mileage is compared in the unit chosen in settings.
 */
internal object HistoryRows {

    fun ofCar(rows: List<HistoryRow>, carId: String?): List<HistoryRow> =
        rows.filter { carId == null || it.car.id == carId }

    fun matching(rows: List<HistoryRow>, criteria: HistoryCriteria): List<HistoryRow> {
        val text = criteria.query.trim()
        return ofCar(rows, criteria.carId).filter { row ->
            val entry = row.entry
            val mileage = entry.mileageKm.kmIn(criteria.distanceUnit)
            (criteria.from == null || entry.date >= criteria.from) &&
                (criteria.to == null || entry.date <= criteria.to) &&
                (text.isEmpty() || entry.taskName.contains(text, ignoreCase = true)) &&
                (criteria.minMileage == null || mileage >= criteria.minMileage) &&
                (criteria.maxMileage == null || mileage <= criteria.maxMileage)
        }
    }

    fun grouped(rows: List<HistoryRow>): List<HistoryYear> =
        rows.groupBy { it.entry.date.year }.map { (year, ofYear) ->
            HistoryYear(
                year = year,
                months = ofYear.groupBy { it.entry.date.month.number }.map { (month, ofMonth) -> HistoryMonth(month, ofMonth) },
            )
        }

    fun suggestions(car: Car?): List<String> =
        car?.tasks?.map { it.name }?.distinct()?.take(SUGGESTION_COUNT).orEmpty()

    private const val SUGGESTION_COUNT = 3
}
