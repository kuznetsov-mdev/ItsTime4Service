package ru.kuznetsov.itstime4service.features.history

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers [HistoryRows]: narrowing the history to a car and the filter — inclusive bounds, a case-insensitive part
 * of the task name, mileage in the chosen unit — grouping by year and month, and the task names offered for
 * a new record.
 */
class HistoryRowsTest {

    private val kia = HistoryCar("kia", "Kia Rio")
    private val camry = HistoryCar("camry", "Toyota Camry")

    private fun row(id: String, date: LocalDate, mileageKm: Int = 50_000, task: String = "Замена масла", car: HistoryCar = kia) =
        HistoryRow(ServiceHistoryEntry(id, car.id, task, date, mileageKm), car)

    private val rows = listOf(
        row("oct", LocalDate(2026, 10, 1), 84_000, "Замена масла"),
        row("sep", LocalDate(2026, 9, 12), 82_000, "Воздушный фильтр"),
        row("camry", LocalDate(2026, 9, 3), 150_000, "Тормозные колодки", camry),
        row("old", LocalDate(2025, 12, 31), 60_000, "Замена масла"),
    )

    private fun ids(criteria: HistoryCriteria) = HistoryRows.matching(rows, criteria).map { it.entry.id }

    @Test
    fun withoutCriteriaEveryRowIsShownInOrder() {
        assertEquals(listOf("oct", "sep", "camry", "old"), ids(HistoryCriteria()))
    }

    @Test
    fun chosenCarNarrowsTheList() {
        assertEquals(listOf("camry"), ids(HistoryCriteria(carId = "camry")))
        assertEquals(3, HistoryRows.ofCar(rows, "kia").size)
    }

    @Test
    fun dateBoundsAreInclusive() {
        assertEquals(listOf("sep", "camry"), ids(HistoryCriteria(from = LocalDate(2026, 9, 3), to = LocalDate(2026, 9, 12))))
    }

    @Test
    fun taskNameIsSearchedAsSubstringIgnoringCase() {
        assertEquals(listOf("oct", "old"), ids(HistoryCriteria(query = "  МАСЛ ")))
        assertTrue(ids(HistoryCriteria(query = "масло")).isEmpty())
    }

    @Test
    fun mileageBoundsAreInclusive() {
        assertEquals(listOf("oct", "sep"), ids(HistoryCriteria(minMileage = 82_000, maxMileage = 84_000)))
    }

    @Test
    fun mileageIsComparedInChosenUnit() {
        assertEquals(listOf("oct"), ids(HistoryCriteria(minMileage = 52_000, maxMileage = 53_000, distanceUnit = DistanceUnit.MILES)))
    }

    @Test
    fun rowsAreGroupedByYearAndMonthKeepingOrder() {
        val years = HistoryRows.grouped(rows)

        assertEquals(listOf(2026, 2025), years.map { it.year })
        assertEquals(listOf(10, 9), years[0].months.map { it.month })
        assertEquals(listOf("sep", "camry"), years[0].months[1].rows.map { it.entry.id })
        assertEquals(listOf(12), years[1].months.map { it.month })
    }

    @Test
    fun suggestionsAreFirstThreeDistinctTaskNames() {
        val car = Car(
            id = "kia",
            brand = "Kia Rio",
            mileageKm = 84_000,
            vin = null,
            tasks = listOf("Масло", "Колодки", "Масло", "Фильтр", "Ремень").mapIndexed { index, name ->
                MaintenanceTask("t$index", name, 10_000, 80_000, 1_000)
            },
        )

        assertEquals(listOf("Масло", "Колодки", "Фильтр"), HistoryRows.suggestions(car))
        assertTrue(HistoryRows.suggestions(null).isEmpty())
    }
}
