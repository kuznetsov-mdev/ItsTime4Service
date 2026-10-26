package ru.kuznetsov.itstime4service.features.cars

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add_car
import itstime4service.shared.generated.resources.action_hide_plate
import itstime4service.shared.generated.resources.action_show_plate
import itstime4service.shared.generated.resources.car_card_no_history
import itstime4service.shared.generated.resources.car_card_no_tasks
import itstime4service.shared.generated.resources.car_card_recent
import itstime4service.shared.generated.resources.car_card_upcoming
import itstime4service.shared.generated.resources.cars_count
import itstime4service.shared.generated.resources.cars_empty
import itstime4service.shared.generated.resources.cars_gauge_hint
import itstime4service.shared.generated.resources.cars_view_list
import itstime4service.shared.generated.resources.cars_view_tasks
import itstime4service.shared.generated.resources.cars_view_tasks_history
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.ui.components.indicators.PlateLook
import ru.kuznetsov.itstime4service.ui.format.distanceText
import ru.kuznetsov.itstime4service.ui.format.grouped
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the "My cars" screen from a given state: the three list views and their switch, opening a car and its
 * history, adding a car, the task and history columns of a card with their empty texts, the plate tap that hides
 * and shows the number, and the empty list.
 */
@OptIn(ExperimentalTestApi::class)
class CarsScreenTest : UiTest() {

    private val calls = mutableListOf<String>()
    private val texts = mutableMapOf<String, String>()

    private fun hasClickLabel(label: String) = SemanticsMatcher("click label is $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun ComposeUiTest.show(state: CarsUiState) = setContent {
        texts["viewList"] = stringResource(Res.string.cars_view_list)
        texts["viewTasks"] = stringResource(Res.string.cars_view_tasks)
        texts["viewHistory"] = stringResource(Res.string.cars_view_tasks_history)
        texts["addCar"] = stringResource(Res.string.action_add_car)
        texts["upcoming"] = stringResource(Res.string.car_card_upcoming).uppercase()
        texts["recent"] = stringResource(Res.string.car_card_recent).uppercase()
        texts["noTasks"] = stringResource(Res.string.car_card_no_tasks)
        texts["noHistory"] = stringResource(Res.string.car_card_no_history)
        texts["showPlate"] = stringResource(Res.string.action_show_plate)
        texts["hidePlate"] = stringResource(Res.string.action_hide_plate)
        texts["empty"] = stringResource(Res.string.cars_empty)
        texts["subtitle"] = "${pluralStringResource(Res.plurals.cars_count, 2, 2)} · ${stringResource(Res.string.cars_gauge_hint)}"
        texts["mileage"] = distanceText(84300, DistanceUnit.KILOMETERS)
        ItsTime4ServiceTheme(darkTheme = false) {
            CarsContent(
                state = state,
                onViewSelect = { calls += "view $it" },
                onPlateToggle = { calls += "plate $it" },
                onOpenCar = { calls += "open $it" },
                onOpenHistory = { calls += "history $it" },
                onAddCar = { calls += "addCar" },
            )
        }
    }

    private val kia = CarItem(
        id = "kia",
        brand = "Kia Rio",
        mileageKm = 84300,
        alertCount = 1,
        gaugeValue = 0.5f,
        status = TaskStatus.DUE,
        dueCount = 1,
        taskCount = 2,
        upcomingTasks = listOf(
            UpcomingTask("Ремень ГРМ", -800, TaskStatus.DUE),
            UpcomingTask("Масло", 3700, TaskStatus.OK),
        ),
        recentRecords = listOf(RecentRecord("Колодки", LocalDate(2026, 8, 14))),
    )

    private val bare = CarItem(id = "bmw", brand = "BMW X1", mileageKm = 12000, alertCount = 0, gaugeValue = 0f, status = TaskStatus.OK)

    private fun cars(view: CarsView, items: List<CarItem> = listOf(kia)) =
        CarsUiState(isLoading = false, cars = items, view = view)

    @Test
    fun listShowsCountMileageAndOpensTheCar() = runComposeUiTest {
        show(cars(CarsView.LIST, listOf(kia, bare)))

        onNodeWithText(texts.getValue("subtitle")).assertExists()
        onNodeWithText(texts.getValue("mileage")).assertExists()
        onNodeWithText("Kia Rio").performClick()

        assertEquals(listOf("open kia"), calls)
        onAllNodesWithText(texts.getValue("upcoming")).assertCountEquals(0)
    }

    @Test
    fun viewButtonsReportTheChoice() = runComposeUiTest {
        show(cars(CarsView.LIST))

        onNodeWithContentDescription(texts.getValue("viewList")).performClick()
        onNodeWithContentDescription(texts.getValue("viewTasks")).performClick()
        onNodeWithContentDescription(texts.getValue("viewHistory")).performClick()

        assertEquals(listOf("view LIST", "view TASKS", "view TASKS_AND_HISTORY"), calls)
    }

    @Test
    fun addRowAddsACar() = runComposeUiTest {
        show(cars(CarsView.LIST))

        onNodeWithContentDescription(texts.getValue("addCar")).performScrollTo().performClick()

        assertEquals(listOf("addCar"), calls)
    }

    @Test
    fun tasksViewShowsUpcomingTasksWithoutHistory() = runComposeUiTest {
        show(cars(CarsView.TASKS))

        onNodeWithText(texts.getValue("upcoming")).assertExists()
        onNodeWithText("Ремень ГРМ").assertExists()
        onNodeWithText("−${800.grouped()}").assertExists()
        onAllNodesWithText(texts.getValue("recent")).assertCountEquals(0)
        onAllNodesWithText("Колодки").assertCountEquals(0)
        onAllNodesWithText(texts.getValue("subtitle")).assertCountEquals(0)
    }

    @Test
    fun cardTopAndTasksOpenTheCarAndHistoryOpensHistory() = runComposeUiTest {
        show(cars(CarsView.TASKS_AND_HISTORY))

        onNodeWithText("Kia Rio").performClick()
        onNodeWithText("Масло").performClick()
        onNodeWithText("Колодки").performClick()

        assertEquals(listOf("open kia", "open kia", "history kia"), calls)
        onNodeWithText("14.08.26").assertExists()
    }

    @Test
    fun cardWithoutTasksOrHistorySaysSo() = runComposeUiTest {
        show(cars(CarsView.TASKS_AND_HISTORY, listOf(bare)))

        onNodeWithText(texts.getValue("noTasks")).assertExists()
        onNodeWithText(texts.getValue("noHistory")).assertExists()
    }

    @Test
    fun plateTapHidesAndShowsTheNumber() = runComposeUiTest {
        show(
            cars(
                CarsView.TASKS,
                listOf(
                    kia.copy(plateLook = PlateLook.Universal("A123BC")),
                    bare.copy(plateLook = PlateLook.Universal("B456CD"), plateHidden = true),
                ),
            ),
        )

        onNode(hasScrollAction()).performScrollToNode(hasClickLabel(texts.getValue("hidePlate")))
        onNode(hasClickLabel(texts.getValue("hidePlate"))).performClick()
        onNode(hasScrollAction()).performScrollToNode(hasClickLabel(texts.getValue("showPlate")))
        onNode(hasClickLabel(texts.getValue("showPlate"))).performClick()

        assertEquals(listOf("plate kia", "plate bmw"), calls)
    }

    @Test
    fun listViewDrawsNoPlate() = runComposeUiTest {
        show(cars(CarsView.LIST, listOf(kia.copy(plateLook = PlateLook.Universal("A123BC")))))

        onAllNodes(hasClickLabel(texts.getValue("hidePlate"))).assertCountEquals(0)
    }

    @Test
    fun emptyListOffersToAddACar() = runComposeUiTest {
        show(CarsUiState(isLoading = false))

        onNodeWithText(texts.getValue("empty")).assertExists()
        onNodeWithContentDescription(texts.getValue("addCar")).performClick()

        assertEquals(listOf("addCar"), calls)
    }

    @Test
    fun loadingShowsNeitherTheEmptyTextNorTheAddButton() = runComposeUiTest {
        show(CarsUiState())

        onAllNodesWithText(texts.getValue("empty")).assertCountEquals(0)
        onAllNodesWithContentDescription(texts.getValue("addCar")).assertCountEquals(0)
    }
}
