package ru.kuznetsov.itstime4service.features

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_save_car
import itstime4service.shared.generated.resources.screen_add_car
import itstime4service.shared.generated.resources.screen_cars
import org.jetbrains.compose.resources.stringResource
import org.robolectric.annotation.Config
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.features.cardetail.CarDetailContent
import ru.kuznetsov.itstime4service.features.cardetail.CarDetailUiState
import ru.kuznetsov.itstime4service.features.cardetail.CarDetails
import ru.kuznetsov.itstime4service.features.cardetail.TaskItem
import ru.kuznetsov.itstime4service.features.carform.CarFormContent
import ru.kuznetsov.itstime4service.features.carform.CarFormUiState
import ru.kuznetsov.itstime4service.features.cars.CarItem
import ru.kuznetsov.itstime4service.features.cars.CarsContent
import ru.kuznetsov.itstime4service.features.cars.CarsUiState
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test

/**
 * Covers the layouts of a phone turned sideways (640 × 360 dp, lower than the compact height): the whole screen
 * scrolls, so after scrolling to its end the top bar, the title and the car header are gone together with the content —
 * on the add-car form, the car screen and the car list.
 */
@OptIn(ExperimentalTestApi::class)
@Config(qualifiers = "w640dp-h360dp-land")
class LandscapeLayoutsTest : UiTest() {

    private val texts = mutableMapOf<String, String>()

    private fun ComposeUiTest.assertOffScreen(text: String) {
        val nodes = onAllNodesWithText(text)
        if (nodes.fetchSemanticsNodes().isNotEmpty()) nodes.onFirst().assertIsNotDisplayed()
    }

    @Test
    fun carFormButtonsScrollWithTheForm() = runComposeUiTest {
        setContent {
            texts["title"] = stringResource(Res.string.screen_add_car)
            texts["save"] = stringResource(Res.string.action_save_car)
            ItsTime4ServiceTheme(darkTheme = false) {
                CarFormContent(
                    state = CarFormUiState(),
                    onBack = {},
                    onBrandChange = {},
                    onMileageChange = {},
                    onVinChange = {},
                    onPlateChange = {},
                    onTaskChange = { _, _ -> },
                    onAddTask = {},
                    onAddPreset = { _, _ -> },
                    onToggleTask = {},
                    onRemoveTask = {},
                    onTaskRecordToggle = { _, _ -> },
                    onSave = {},
                )
            }
        }

        onNodeWithText(texts.getValue("save")).performScrollTo()

        assertOffScreen(texts.getValue("title"))
    }

    @Test
    fun carHeaderScrollsAwayWithTheTasks() = runComposeUiTest {
        val tasks = (1..5).map { index ->
            TaskItem(
                id = "t$index",
                name = "Работа $index",
                intervalKm = 10000,
                remainingKm = 1000 * index,
                remainingMonths = null,
                status = TaskStatus.OK,
                progress = 0.5f,
            )
        }
        val car = CarDetails(
            brand = "Kia Rio",
            vin = null,
            plate = null,
            plateLook = null,
            mileageKm = 84300,
            dueCount = 0,
            soonCount = 0,
            taskCount = tasks.size,
            tasks = tasks,
        )
        setContent {
            ItsTime4ServiceTheme(darkTheme = false) {
                CarDetailContent(
                    state = CarDetailUiState(isLoading = false, car = car),
                    onBack = {},
                    onOpenHistory = {},
                    onAddTask = {},
                    onOpenTask = {},
                    onEdit = {},
                    onCancelEdit = {},
                    onSaveBrand = {},
                    onSaveVin = {},
                    onSavePlate = {},
                    onPlateHiddenToggle = {},
                    onVinHiddenToggle = {},
                    onSaveMileage = {},
                    onMileageStep = {},
                    onMultiplierSelect = {},
                    onMarkTaskDone = {},
                    onTaskDoneConfirm = { _, _, _ -> },
                    onTaskDoneDismiss = {},
                    onUnmarkRequest = {},
                    onUnmarkConfirm = {},
                    onUnmarkDismiss = {},
                    onDeleteRequest = {},
                    onDeleteDismiss = {},
                    onDeleteConfirm = {},
                )
            }
        }

        onNode(hasScrollAction()).performScrollToNode(hasText("Работа 5"))

        assertOffScreen("Kia Rio")
    }

    @Test
    fun carListTitleScrollsAwayWithTheCars() = runComposeUiTest {
        val cars = (1..6).map { index ->
            CarItem(id = "c$index", brand = "Машина $index", mileageKm = 10000 * index, alertCount = 0, gaugeValue = 0f, status = TaskStatus.OK)
        }
        setContent {
            texts["title"] = stringResource(Res.string.screen_cars)
            ItsTime4ServiceTheme(darkTheme = false) {
                CarsContent(
                    state = CarsUiState(isLoading = false, cars = cars),
                    onViewSelect = {},
                    onPlateToggle = {},
                    onOpenCar = {},
                    onOpenHistory = {},
                    onAddCar = {},
                )
            }
        }

        onNode(hasScrollAction()).performScrollToNode(hasText("Машина 6"))

        assertOffScreen(texts.getValue("title"))
    }
}
