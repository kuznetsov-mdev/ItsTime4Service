package ru.kuznetsov.itstime4service.features.history

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add
import itstime4service.shared.generated.resources.action_add_entry
import itstime4service.shared.generated.resources.action_apply
import itstime4service.shared.generated.resources.action_filter
import itstime4service.shared.generated.resources.action_save
import itstime4service.shared.generated.resources.cost_rub
import itstime4service.shared.generated.resources.history_all_cars
import itstime4service.shared.generated.resources.history_empty
import itstime4service.shared.generated.resources.history_nothing_found
import itstime4service.shared.generated.resources.history_sold_car
import itstime4service.shared.generated.resources.months
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.ui.format.distanceText
import ru.kuznetsov.itstime4service.ui.format.grouped
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the history screen from a given state: records grouped by year and month with their car, mileage, cost,
 * service and comment, the car picker and the filter button, the add button that needs a car not sold, the empty
 * history and the empty result of a filter, and the record, filter and new record windows opened from the state.
 */
@OptIn(ExperimentalTestApi::class)
class HistoryScreenTest : UiTest() {

    private val calls = mutableListOf<String>()
    private val texts = mutableMapOf<String, String>()

    private val kia = HistoryCar("kia", "Kia")
    private val bmw = HistoryCar("bmw", "BMW")

    private val entry = ServiceHistoryEntry(
        id = "e1",
        carId = "kia",
        taskName = "Масло",
        date = LocalDate(2026, 8, 14),
        mileageKm = 61500,
        costRub = 2400,
        serviceName = "АвтоСервис Юг",
        comment = "Motul 5W-30",
    )

    private fun history(car: HistoryCar = kia) = listOf(
        HistoryYear(2026, listOf(HistoryMonth(8, listOf(HistoryRow(entry, car))))),
    )

    private fun ComposeUiTest.show(state: HistoryUiState) = setContent {
        texts["allCars"] = stringResource(Res.string.history_all_cars)
        texts["filter"] = stringResource(Res.string.action_filter)
        texts["addEntry"] = stringResource(Res.string.action_add_entry)
        texts["empty"] = stringResource(Res.string.history_empty)
        texts["nothingFound"] = stringResource(Res.string.history_nothing_found)
        texts["soldKia"] = stringResource(Res.string.history_sold_car, "Kia")
        texts["august"] = stringArrayResource(Res.array.months)[7].uppercase()
        texts["meta"] = listOf(
            distanceText(61500, DistanceUnit.KILOMETERS),
            stringResource(Res.string.cost_rub, 2400.grouped()),
            "АвтоСервис Юг",
        ).joinToString("   ")
        texts["apply"] = stringResource(Res.string.action_apply)
        texts["save"] = stringResource(Res.string.action_save)
        texts["add"] = stringResource(Res.string.action_add)
        ItsTime4ServiceTheme(darkTheme = false) {
            HistoryContent(
                state = state,
                onCarMenuToggle = { calls += "menuToggle" },
                onCarMenuDismiss = { calls += "menuDismiss" },
                onCarSelect = { calls += "select $it" },
                onFilterOpen = { calls += "filterOpen" },
                onFilterChange = {},
                onFilterApply = { calls += "filterApply" },
                onFilterReset = { calls += "filterReset" },
                onFilterDismiss = { calls += "filterDismiss" },
                onEntryOpen = { calls += "open ${it.entry.id}" },
                onEntryChange = {},
                onEntrySave = { calls += "entrySave" },
                onEntryDismiss = { calls += "entryDismiss" },
                onNewEntryOpen = { calls += "newEntryOpen" },
                onNewEntryChange = {},
                onNewEntrySave = { calls += "newEntrySave" },
                onNewEntryDismiss = { calls += "newEntryDismiss" },
                onRetry = { calls += "retry" },
            )
        }
    }

    private fun loaded(
        cars: List<HistoryCar> = listOf(kia, bmw),
        years: List<HistoryYear> = history(),
        selectedCarId: String? = null,
    ) = HistoryUiState(isLoading = false, cars = cars, years = years, hasEntries = years.isNotEmpty(), selectedCarId = selectedCarId)

    @Test
    fun recordShowsYearMonthCarAndDetails() = runComposeUiTest {
        show(loaded())

        onNodeWithText("2026").assertExists()
        onNodeWithText(texts.getValue("august")).assertExists()
        onNodeWithText("14.08.2026").assertExists()
        onNodeWithText(texts.getValue("meta")).assertExists()
        onNodeWithText("Motul 5W-30").assertExists()
        onAllNodesWithText("Kia").assertCountEquals(1)
    }

    @Test
    fun chosenCarIsInTheBarAndNotRepeatedInRows() = runComposeUiTest {
        show(loaded(selectedCarId = "kia"))

        onAllNodesWithText("Kia").assertCountEquals(1)
        onAllNodesWithText(texts.getValue("allCars")).assertCountEquals(0)
    }

    @Test
    fun soldCarIsMarkedInTheRow() = runComposeUiTest {
        show(loaded(cars = listOf(kia.copy(isSold = true)), years = history(kia.copy(isSold = true))))

        onNodeWithText(texts.getValue("soldKia")).assertExists()
    }

    @Test
    fun rowOpensTheRecord() = runComposeUiTest {
        show(loaded())

        onNodeWithText("Масло").performClick()

        assertEquals(listOf("open e1"), calls)
    }

    @Test
    fun barOpensTheCarListAndTheFilter() = runComposeUiTest {
        show(loaded())

        onNodeWithText(texts.getValue("allCars")).performClick()
        onNodeWithContentDescription(texts.getValue("filter")).performClick()

        assertEquals(listOf("menuToggle", "filterOpen"), calls)
    }

    @Test
    fun carListOffersEveryCarWithTheSoldMark() = runComposeUiTest {
        show(loaded(cars = listOf(kia.copy(isSold = true), bmw), years = emptyList()).copy(isCarMenuOpen = true))

        onAllNodesWithText(texts.getValue("allCars")).assertCountEquals(2)
        onNodeWithText(texts.getValue("soldKia")).performClick()
        onNodeWithText("BMW").performClick()

        assertEquals(listOf("select kia", "select bmw"), calls)
    }

    @Test
    fun emptyHistoryAndEmptyFilterResultSayDifferentThings() = runComposeUiTest {
        show(loaded(years = emptyList()))

        onNodeWithText(texts.getValue("empty")).assertExists()
        onAllNodesWithText(texts.getValue("nothingFound")).assertCountEquals(0)
    }

    @Test
    fun filterThatMatchedNothingSaysSo() = runComposeUiTest {
        show(loaded(years = emptyList()).copy(hasEntries = true))

        onNodeWithText(texts.getValue("nothingFound")).assertExists()
        onAllNodesWithText(texts.getValue("empty")).assertCountEquals(0)
    }

    @Test
    fun addButtonOpensANewRecord() = runComposeUiTest {
        show(loaded())

        onNodeWithContentDescription(texts.getValue("addEntry")).performClick()

        assertEquals(listOf("newEntryOpen"), calls)
    }

    @Test
    fun onlySoldCarsLeaveNoAddButton() = runComposeUiTest {
        show(loaded(cars = listOf(kia.copy(isSold = true)), years = history(kia.copy(isSold = true))))

        onAllNodesWithContentDescription(texts.getValue("addEntry")).assertCountEquals(0)
    }

    @Test
    fun loadingShowsNeitherTheEmptyTextNorTheAddButton() = runComposeUiTest {
        show(HistoryUiState(cars = listOf(kia)))

        onAllNodesWithText(texts.getValue("empty")).assertCountEquals(0)
        onAllNodesWithContentDescription(texts.getValue("addEntry")).assertCountEquals(0)
    }

    @Test
    fun filterWindowOpensFromTheState() = runComposeUiTest {
        show(loaded().copy(filterDraft = HistoryFilter()))

        onNodeWithText(texts.getValue("apply")).performClick()

        assertEquals(listOf("filterApply"), calls)
    }

    @Test
    fun recordWindowOpensFromTheState() = runComposeUiTest {
        show(loaded().copy(entryForm = HistoryEntryForm("e1", "Масло", kia, date = "14082026", mileage = "61500")))

        onNodeWithText(texts.getValue("save")).performClick()

        assertEquals(listOf("entrySave"), calls)
    }

    @Test
    fun newRecordWindowOpensFromTheState() = runComposeUiTest {
        show(loaded().copy(newEntry = NewHistoryEntryForm(carId = "kia")))

        onNodeWithText(texts.getValue("add")).performClick()

        assertEquals(listOf("newEntrySave"), calls)
    }
}
