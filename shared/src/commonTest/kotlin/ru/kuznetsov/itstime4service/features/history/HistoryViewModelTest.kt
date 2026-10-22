package ru.kuznetsov.itstime4service.features.history

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.features.TestApp
import ru.kuznetsov.itstime4service.features.ViewModelTestSetup
import ru.kuznetsov.itstime4service.features.watch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [HistoryViewModel] on the app's own graph and a database in memory: the car picker with sold cars last,
 * the filter applied only with readable dates and mileage in the chosen unit, editing a record and adding one by hand.
 * Dates are typed in the Russian format `dd.MM.yyyy`.
 */
class HistoryViewModelTest {

    private val app = TestApp()

    @BeforeTest
    fun setUp() = ViewModelTestSetup.start()

    @AfterTest
    fun tearDown() {
        app.close()
        ViewModelTestSetup.finish()
    }

    private suspend fun TestScope.open(carId: String? = null): HistoryViewModel {
        val viewModel = app.viewModel<HistoryViewModel>(carId)
        watch(viewModel.state) { !it.isLoading }
        return viewModel
    }

    private suspend fun HistoryViewModel.until(predicate: (HistoryUiState) -> Boolean) = state.first(predicate)

    private val HistoryUiState.rows: List<HistoryRow> get() = years.flatMap { year -> year.months.flatMap { it.rows } }

    private suspend fun kiaWithRecords(): String {
        val kia = app.addCar("Kia", tasks = listOf(NewMaintenanceTask("Масло", 7_500), NewMaintenanceTask("Колодки", 30_000)))
        app.addRecord(kia, "Масло", LocalDate(2025, 11, 5), mileageKm = 50_000)
        app.addRecord(kia, "Колодки", LocalDate(2026, 3, 1), mileageKm = 70_000)
        app.addRecord(kia, "Масло", LocalDate(2026, 9, 12), mileageKm = 90_000)
        return kia
    }

    @Test
    fun recordsAreShownNewestFirstAndSoldCarsAreListedLast() = runTest {
        val sold = app.addCar("Lada")
        app.addRecord(sold, "Масло", LocalDate(2024, 6, 1), mileageKm = 20_000)
        app.taskHistory.sellCar(sold)
        val kia = kiaWithRecords()

        val state = open().until { it.rows.size == 4 }

        assertEquals(listOf(HistoryCar(kia, "Kia"), HistoryCar(sold, "Lada", isSold = true)), state.cars)
        assertEquals(listOf(2026, 2025, 2024), state.years.map { it.year })
        assertEquals(listOf(9, 3), state.years.first().months.map { it.month })
        assertEquals(listOf(HistoryCar(kia, "Kia")), state.activeCars)
        assertTrue(state.hasEntries)
    }

    @Test
    fun carOpenedFromItsScreenIsPreselected() = runTest {
        val kia = kiaWithRecords()
        val camry = app.addCar("Camry")

        assertEquals(kia, open(kia).until { it.cars.size == 2 }.selectedCarId)
        assertNull(open("missing").until { it.cars.size == 2 }.selectedCarId)

        val camryState = open(camry).until { it.cars.size == 2 }
        assertTrue(camryState.years.isEmpty())
        assertFalse(camryState.hasEntries)
    }

    @Test
    fun pickedCarClosesTheMenuAndNarrowsTheList() = runTest {
        val kia = kiaWithRecords()
        val camry = app.addCar("Camry")
        app.addRecord(camry, "Шины", LocalDate(2026, 10, 1))
        val viewModel = open()
        viewModel.until { it.rows.size == 4 }

        viewModel.onCarMenuToggle()
        assertTrue(viewModel.state.value.isCarMenuOpen)
        viewModel.onCarSelect(camry)

        val state = viewModel.until { it.selectedCarId == camry }
        assertFalse(state.isCarMenuOpen)
        assertEquals(listOf("Шины"), state.rows.map { it.entry.taskName })
        viewModel.onCarSelect(null)
        assertEquals(4, viewModel.until { it.selectedCarId == null }.rows.size)
        assertEquals(kia, state.cars.first().id)
    }

    @Test
    fun filterIsAppliedOnlyWhenItsDatesCanBeRead() = runTest {
        kiaWithRecords()
        val viewModel = open()
        viewModel.until { it.rows.size == 3 }

        viewModel.onFilterOpen()
        viewModel.onFilterChange(viewModel.state.value.filterDraft!!.copy(dateFrom = "31.02.2026"))
        viewModel.onFilterApply()
        val invalid = viewModel.state.value
        assertTrue(invalid.filterDraft!!.showErrors)
        assertTrue(invalid.filterDraft.isDateFromInvalid)
        assertFalse(invalid.filter.isActive)

        viewModel.onFilterChange(invalid.filterDraft.copy(dateFrom = "01.03.2026"))
        assertEquals("01032026", viewModel.state.value.filterDraft!!.dateFrom)
        viewModel.onFilterApply()

        val state = viewModel.until { it.filterDraft == null && it.rows.size == 2 }
        assertEquals(listOf(LocalDate(2026, 9, 12), LocalDate(2026, 3, 1)), state.rows.map { it.entry.date })
        assertFalse(state.filter.showErrors)

        viewModel.onFilterReset()
        assertEquals(3, viewModel.until { !it.filter.isActive }.rows.size)
    }

    @Test
    fun dismissedFilterIsNotApplied() = runTest {
        kiaWithRecords()
        val viewModel = open()

        viewModel.onFilterOpen()
        viewModel.onFilterChange(HistoryFilter(query = "Колод"))
        viewModel.onFilterDismiss()

        val state = viewModel.until { it.rows.size == 3 }
        assertNull(state.filterDraft)
        assertFalse(state.filter.isActive)
    }

    @Test
    fun mileageFilterIsTypedInTheChosenUnit() = runTest {
        app.changeSettings { it.copy(distanceUnit = DistanceUnit.MILES) }
        kiaWithRecords()
        val viewModel = open()

        viewModel.onFilterOpen()
        viewModel.onFilterChange(HistoryFilter(mileageFrom = "40 000", mileageTo = "50000"))
        viewModel.onFilterApply()

        val state = viewModel.until { it.filter.isActive && it.rows.isNotEmpty() }
        assertEquals("40000", state.filter.mileageFrom)
        assertEquals(listOf(70_000), state.rows.map { it.entry.mileageKm })
    }

    @Test
    fun editedRecordKeepsItsKilometersWhenTheMileageIsLeftAsItWas() = runTest {
        app.changeSettings { it.copy(distanceUnit = DistanceUnit.MILES) }
        kiaWithRecords()
        val viewModel = open()
        val row = viewModel.until { it.rows.size == 3 && it.distanceUnit == DistanceUnit.MILES }.rows.first()

        viewModel.onEntryOpen(row)
        val form = viewModel.state.value.entryForm!!
        assertEquals("12092026", form.date)
        assertEquals("55923", form.mileage)
        viewModel.onEntryChange(form.copy(date = "13.09.2026", cost = "3 500 ₽", serviceName = "Сервис"))
        viewModel.onEntrySave()

        viewModel.until { it.entryForm == null }
        val saved = app.records { records -> records.any { it.date == LocalDate(2026, 9, 13) } }.single { it.id == row.entry.id }
        assertEquals(90_000, saved.mileageKm)
        assertEquals(3_500, saved.costRub)
        assertEquals("Сервис", saved.serviceName)
    }

    @Test
    fun recordWithUnreadableDateOrNoMileageStaysOpen() = runTest {
        kiaWithRecords()
        val viewModel = open()
        val row = viewModel.until { it.rows.size == 3 }.rows.first()

        viewModel.onEntryOpen(row)
        viewModel.onEntryChange(viewModel.state.value.entryForm!!.copy(date = "3009", mileage = ""))
        viewModel.onEntrySave()

        val form = assertNotNull(viewModel.state.value.entryForm)
        assertTrue(form.showErrors)
        assertTrue(form.isDateInvalid)
        assertTrue(form.isMileageInvalid)
        viewModel.onEntryDismiss()
        assertNull(viewModel.state.value.entryForm)
    }

    @Test
    fun recordAddedByHandStartsOnTheChosenCarWithItsTaskNames() = runTest {
        val sold = app.addCar("Lada")
        app.addRecord(sold, "Масло", LocalDate(2024, 6, 1))
        app.taskHistory.sellCar(sold)
        val kia = kiaWithRecords()
        val camry = app.addCar("Camry", tasks = listOf(NewMaintenanceTask("Шины", 40_000)))
        val viewModel = open()
        viewModel.until { it.cars.size == 3 }

        viewModel.onNewEntryOpen()
        assertEquals(kia, viewModel.state.value.newEntry?.carId)
        assertEquals(listOf("Масло", "Колодки"), viewModel.until { it.newEntrySuggestions.isNotEmpty() }.newEntrySuggestions)
        viewModel.onNewEntryDismiss()

        viewModel.onCarSelect(camry)
        viewModel.onNewEntryOpen()
        assertEquals(camry, viewModel.state.value.newEntry?.carId)
        assertEquals(listOf("Шины"), viewModel.until { it.newEntrySuggestions == listOf("Шины") }.newEntrySuggestions)
        viewModel.onNewEntryDismiss()

        viewModel.onCarSelect(sold)
        viewModel.onNewEntryOpen()
        assertEquals(kia, viewModel.state.value.newEntry?.carId)
    }

    @Test
    fun recordAddedByHandIsStoredInKilometers() = runTest {
        app.changeSettings { it.copy(distanceUnit = DistanceUnit.MILES) }
        val kia = app.addCar("Kia")
        val viewModel = open()
        viewModel.until { it.cars.isNotEmpty() && it.distanceUnit == DistanceUnit.MILES }

        viewModel.onNewEntryOpen()
        viewModel.onNewEntryChange(
            viewModel.state.value.newEntry!!.copy(taskName = " Шины ", date = "15.09.2026", mileage = "10000", cost = "4000"),
        )
        viewModel.onNewEntrySave()

        viewModel.until { it.newEntry == null }
        val record = app.records { it.isNotEmpty() }.single()
        assertEquals(kia, record.carId)
        assertEquals("Шины", record.taskName)
        assertEquals(LocalDate(2026, 9, 15), record.date)
        assertEquals(16_093, record.mileageKm)
        assertEquals(4_000, record.costRub)
    }

    @Test
    fun recordAddedByHandWithoutNameShowsErrors() = runTest {
        app.addCar("Kia")
        val viewModel = open()
        viewModel.until { it.cars.isNotEmpty() }

        viewModel.onNewEntryOpen()
        viewModel.onNewEntryChange(viewModel.state.value.newEntry!!.copy(date = "15.09.2026", mileage = "10000"))
        viewModel.onNewEntrySave()

        val form = assertNotNull(viewModel.state.value.newEntry)
        assertTrue(form.showErrors)
        assertTrue(form.isNameMissing)
        assertFalse(form.isDateInvalid)
    }

    @Test
    fun noRecordCanBeAddedWithoutACar() = runTest {
        val viewModel = open()

        viewModel.onNewEntryOpen()

        assertNull(viewModel.state.value.newEntry)
    }
}
