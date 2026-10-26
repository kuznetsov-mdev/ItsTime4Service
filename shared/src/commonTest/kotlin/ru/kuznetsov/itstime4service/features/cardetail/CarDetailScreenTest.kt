package ru.kuznetsov.itstime4service.features.cardetail

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add_task
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_delete
import itstime4service.shared.generated.resources.action_delete_car
import itstime4service.shared.generated.resources.action_done_unmark
import itstime4service.shared.generated.resources.action_edit_mileage
import itstime4service.shared.generated.resources.action_edit_name
import itstime4service.shared.generated.resources.action_edit_plate
import itstime4service.shared.generated.resources.action_edit_vin
import itstime4service.shared.generated.resources.action_hide_plate
import itstime4service.shared.generated.resources.action_hide_vin
import itstime4service.shared.generated.resources.action_mark_done
import itstime4service.shared.generated.resources.action_mileage_down
import itstime4service.shared.generated.resources.action_mileage_up
import itstime4service.shared.generated.resources.action_ok
import itstime4service.shared.generated.resources.action_save
import itstime4service.shared.generated.resources.action_show_plate
import itstime4service.shared.generated.resources.action_show_vin
import itstime4service.shared.generated.resources.dialog_delete_car_title
import itstime4service.shared.generated.resources.dialog_task_done_title
import itstime4service.shared.generated.resources.dialog_unmark_title
import itstime4service.shared.generated.resources.mileage_step_hint
import itstime4service.shared.generated.resources.retry
import itstime4service.shared.generated.resources.screen_history
import itstime4service.shared.generated.resources.tasks_empty
import itstime4service.shared.generated.resources.vin_hidden
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.ui.components.indicators.PlateLook
import ru.kuznetsov.itstime4service.ui.format.grouped
import ru.kuznetsov.itstime4service.ui.format.unitGenitive
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the car screen from a given state: the header and its in-place edits, hiding the plate and the VIN,
 * the mileage steps, the task cards and their done marks, the top bar buttons, the "task done", delete and
 * unmark windows, the empty task list and the load error.
 */
@OptIn(ExperimentalTestApi::class)
class CarDetailScreenTest : UiTest() {

    private val calls = mutableListOf<String>()
    private val texts = mutableMapOf<String, String>()

    private fun hasClickLabel(label: String) = SemanticsMatcher("click label is $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun ComposeUiTest.show(state: CarDetailUiState) = setContent {
        listOf(
            "history" to Res.string.screen_history,
            "deleteCar" to Res.string.action_delete_car,
            "addTask" to Res.string.action_add_task,
            "editName" to Res.string.action_edit_name,
            "editPlate" to Res.string.action_edit_plate,
            "editVin" to Res.string.action_edit_vin,
            "editMileage" to Res.string.action_edit_mileage,
            "vinHidden" to Res.string.vin_hidden,
            "showVin" to Res.string.action_show_vin,
            "hideVin" to Res.string.action_hide_vin,
            "showPlate" to Res.string.action_show_plate,
            "hidePlate" to Res.string.action_hide_plate,
            "save" to Res.string.action_save,
            "down" to Res.string.action_mileage_down,
            "up" to Res.string.action_mileage_up,
            "markDone" to Res.string.action_mark_done,
            "unmark" to Res.string.action_done_unmark,
            "doneTitle" to Res.string.dialog_task_done_title,
            "unmarkTitle" to Res.string.dialog_unmark_title,
            "delete" to Res.string.action_delete,
            "cancel" to Res.string.action_cancel,
            "ok" to Res.string.action_ok,
            "retry" to Res.string.retry,
            "empty" to Res.string.tasks_empty,
        ).forEach { (key, res) -> texts[key] = stringResource(res) }
        texts["deleteTitle"] = stringResource(Res.string.dialog_delete_car_title, "Kia Rio")
        texts["stepHint"] = stringResource(
            Res.string.mileage_step_hint,
            "${500.grouped()} ${unitGenitive(DistanceUnit.KILOMETERS)}",
        )
        ItsTime4ServiceTheme(darkTheme = false) {
            CarDetailContent(
                state = state,
                onBack = { calls += "back" },
                onOpenHistory = { calls += "history" },
                onAddTask = { calls += "addTask" },
                onOpenTask = { calls += "open $it" },
                onEdit = { calls += "edit $it" },
                onCancelEdit = { calls += "cancelEdit" },
                onSaveBrand = { calls += "brand $it" },
                onSaveVin = { calls += "vin $it" },
                onSavePlate = { calls += "plate $it" },
                onPlateHiddenToggle = { calls += "plateToggle" },
                onVinHiddenToggle = { calls += "vinToggle" },
                onSaveMileage = { calls += "mileage $it" },
                onMileageStep = { calls += "step $it" },
                onMultiplierSelect = { calls += "multiplier $it" },
                onMarkTaskDone = { calls += "mark $it" },
                onTaskDoneConfirm = { service, cost, comment -> calls += "done $service|$cost|$comment" },
                onTaskDoneDismiss = { calls += "doneDismiss" },
                onUnmarkRequest = { calls += "unmark $it" },
                onUnmarkConfirm = { calls += "unmarkConfirm" },
                onUnmarkDismiss = { calls += "unmarkDismiss" },
                onDeleteRequest = { calls += "deleteRequest" },
                onDeleteDismiss = { calls += "deleteDismiss" },
                onDeleteConfirm = { calls += "deleteConfirm" },
                onRetry = { calls += "retry" },
            )
        }
    }

    private val oil = TaskItem(
        id = "oil",
        name = "Масло",
        intervalKm = 10000,
        remainingKm = 3700,
        remainingMonths = 5,
        status = TaskStatus.OK,
        progress = 0.63f,
    )

    private fun car(
        plate: String? = "A123BC",
        plateHidden: Boolean = false,
        vin: String? = "XWEPC811BB0012345",
        vinHidden: Boolean = false,
        tasks: List<TaskItem> = listOf(oil),
    ) = CarDetails(
        brand = "Kia Rio",
        vin = vin,
        plate = plate,
        plateLook = plate?.let { PlateLook.Universal(it) },
        plateHidden = plateHidden,
        vinHidden = vinHidden,
        mileageKm = 84300,
        dueCount = 0,
        soonCount = 0,
        taskCount = tasks.size,
        tasks = tasks,
    )

    private fun loaded(car: CarDetails = car()) = CarDetailUiState(isLoading = false, car = car)

    @Test
    fun headerShowsTheCarAndItsTask() = runComposeUiTest {
        show(loaded())

        onNodeWithText("Kia Rio").assertExists()
        onNodeWithText("XWEPC811BB0012345").assertExists()
        onNodeWithText(84300.grouped()).assertExists()
        onNodeWithText("Масло").assertExists()
    }

    @Test
    fun topBarAndAddButtonReachTheViewModel() = runComposeUiTest {
        show(loaded())

        onNodeWithContentDescription(texts.getValue("history")).performClick()
        onNodeWithContentDescription(texts.getValue("deleteCar")).performClick()
        onNodeWithContentDescription(texts.getValue("addTask")).performClick()

        assertEquals(listOf("history", "deleteRequest", "addTask"), calls)
    }

    @Test
    fun headerValuesStartTheirEdits() = runComposeUiTest {
        show(loaded())

        onNode(hasClickLabel(texts.getValue("editName"))).performClick()
        onNode(hasClickLabel(texts.getValue("editPlate"))).performClick()
        onNode(hasClickLabel(texts.getValue("editVin"))).performClick()
        onNode(hasClickLabel(texts.getValue("editMileage"))).performClick()

        assertEquals(listOf("edit BRAND", "edit PLATE", "edit VIN", "edit MILEAGE"), calls)
    }

    @Test
    fun hiddenVinIsMaskedAndCannotBeEdited() = runComposeUiTest {
        show(loaded(car(vinHidden = true)))

        onNodeWithContentDescription(texts.getValue("vinHidden")).assertExists()
        onAllNodesWithText("XWEPC811BB0012345").assertCountEquals(0)
        onAllNodes(hasClickLabel(texts.getValue("editVin"))).assertCountEquals(0)
        onNodeWithContentDescription(texts.getValue("showVin")).performClick()

        assertEquals(listOf("vinToggle"), calls)
    }

    @Test
    fun hiddenPlateCannotBeEdited() = runComposeUiTest {
        show(loaded(car(plateHidden = true)))

        onAllNodes(hasClickLabel(texts.getValue("editPlate"))).assertCountEquals(0)
        onNodeWithContentDescription(texts.getValue("showPlate")).performClick()

        assertEquals(listOf("plateToggle"), calls)
    }

    @Test
    fun shownPlateAndVinOfferToHide() = runComposeUiTest {
        show(loaded())

        onNodeWithContentDescription(texts.getValue("hidePlate")).performClick()
        onNodeWithContentDescription(texts.getValue("hideVin")).performClick()

        assertEquals(listOf("plateToggle", "vinToggle"), calls)
    }

    @Test
    fun carWithoutPlateAndVinOffersToAddThem() = runComposeUiTest {
        show(loaded(car(plate = null, vin = null)))

        onNode(hasClickLabel(texts.getValue("editPlate"))).performClick()
        onNode(hasClickLabel(texts.getValue("editVin"))).performClick()

        assertEquals(listOf("edit PLATE", "edit VIN"), calls)
        onAllNodesWithContentDescription(texts.getValue("hideVin")).assertCountEquals(0)
        onAllNodesWithContentDescription(texts.getValue("showVin")).assertCountEquals(0)
    }

    @Test
    fun mileageEditSavesOnlyAPositiveNumber() = runComposeUiTest {
        show(loaded().copy(editingField = EditableField.MILEAGE))

        onNode(hasSetTextAction()).performTextReplacement("0")
        onNodeWithContentDescription(texts.getValue("save")).performClick()
        onNode(hasSetTextAction()).performTextReplacement("90000")
        onNodeWithContentDescription(texts.getValue("save")).performClick()

        assertEquals(listOf("mileage 90000"), calls)
    }

    @Test
    fun brandEditDoesNotSaveABlankName() = runComposeUiTest {
        show(loaded().copy(editingField = EditableField.BRAND))

        onNode(hasSetTextAction()).performTextReplacement(" ")
        onNodeWithContentDescription(texts.getValue("save")).performClick()
        onNode(hasSetTextAction()).performTextReplacement("Kia Ceed")
        onNodeWithContentDescription(texts.getValue("save")).performClick()

        assertEquals(listOf("brand Kia Ceed"), calls)
    }

    @Test
    fun vinEditIsUppercased() = runComposeUiTest {
        show(loaded(car(vin = null)).copy(editingField = EditableField.VIN))

        onNode(hasSetTextAction()).performTextInput("xwepc")
        onNodeWithContentDescription(texts.getValue("save")).performClick()

        assertEquals(listOf("vin XWEPC"), calls)
    }

    @Test
    fun tapOutsideTheEditCancelsIt() = runComposeUiTest {
        show(loaded().copy(editingField = EditableField.BRAND))

        onNodeWithContentDescription(texts.getValue("history")).performClick()

        assertEquals("cancelEdit", calls.first())
    }

    @Test
    fun stepButtonsAreOffWhileMileageIsEdited() = runComposeUiTest {
        show(loaded().copy(editingField = EditableField.MILEAGE))

        onNode(hasClickLabel(texts.getValue("editMileage"))).assertDoesNotExist()
        onNodeWithContentDescription(texts.getValue("down")).assertIsNotEnabled()
        onNodeWithContentDescription(texts.getValue("up")).assertIsNotEnabled()
    }

    @Test
    fun mileageStepsAndMultiplierReachTheViewModel() = runComposeUiTest {
        show(loaded().copy(mileageMultiplier = 5))

        onNodeWithText(texts.getValue("stepHint")).assertExists()
        onNodeWithContentDescription(texts.getValue("down")).performClick()
        onNodeWithContentDescription(texts.getValue("up")).performClick()
        onNodeWithText("×10").performClick()

        assertEquals(listOf("step -1", "step 1", "multiplier 10"), calls)
    }

    @Test
    fun taskCardOpensAndMarksDone() = runComposeUiTest {
        show(loaded())

        onNodeWithText("Масло").performClick()
        onNodeWithContentDescription(texts.getValue("markDone")).performScrollTo().performClick()

        assertEquals(listOf("open oil", "mark oil"), calls)
    }

    @Test
    fun doneTaskAsksToRemoveTheMark() = runComposeUiTest {
        show(loaded(car(tasks = listOf(oil.copy(isDone = true)))))

        onNodeWithContentDescription(texts.getValue("unmark")).performScrollTo().performClick()

        assertEquals(listOf("unmark oil"), calls)
    }

    @Test
    fun taskDoneWindowPassesTheTypedFields() = runComposeUiTest {
        show(loaded().copy(taskDoneDialog = oil))

        onNodeWithText(texts.getValue("doneTitle")).assertExists()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("Шинный центр")
        onNodeWithText(texts.getValue("save")).performClick()
        onNodeWithText(texts.getValue("cancel")).performClick()

        assertEquals(listOf("done Шинный центр||", "doneDismiss"), calls)
    }

    @Test
    fun deleteWindowNamesTheCarAndReportsTheChoice() = runComposeUiTest {
        show(loaded().copy(isConfirmingDelete = true))

        onNodeWithText(texts.getValue("deleteTitle")).assertExists()
        onNodeWithText(texts.getValue("delete")).performClick()
        onNodeWithText(texts.getValue("cancel")).performClick()

        assertEquals(listOf("deleteConfirm", "deleteDismiss"), calls)
    }

    @Test
    fun unmarkWindowReportsTheChoice() = runComposeUiTest {
        show(loaded().copy(isConfirmingUnmark = true))

        onNodeWithText(texts.getValue("unmarkTitle")).assertExists()
        onNodeWithText(texts.getValue("ok")).performClick()
        onNodeWithText(texts.getValue("cancel")).performClick()

        assertEquals(listOf("unmarkConfirm", "unmarkDismiss"), calls)
    }

    @Test
    fun carWithoutTasksShowsTheEmptyList() = runComposeUiTest {
        show(loaded(car(tasks = emptyList())))

        onNodeWithText(texts.getValue("empty")).assertExists()
        onNodeWithContentDescription(texts.getValue("addTask")).assertExists()
    }

    @Test
    fun loadErrorReplacesTheCarAndRetries() = runComposeUiTest {
        show(CarDetailUiState(isLoading = false, loadFailed = true))

        onNodeWithText(texts.getValue("retry")).performClick()

        assertEquals(listOf("retry"), calls)
        onAllNodesWithContentDescription(texts.getValue("addTask")).assertCountEquals(0)
        onAllNodesWithText("Kia Rio").assertCountEquals(0)
    }
}
