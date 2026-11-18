package ru.kuznetsov.itstime4service.features.taskform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.service_unknown
import itstime4service.shared.generated.resources.service_specify
import itstime4service.shared.generated.resources.action_save
import itstime4service.shared.generated.resources.field_comment
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import itstime4service.shared.generated.resources.action_done
import itstime4service.shared.generated.resources.field_last_done_date_short
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_pick_task_icon
import itstime4service.shared.generated.resources.action_remove_task
import itstime4service.shared.generated.resources.field_service_name
import itstime4service.shared.generated.resources.task_create_record
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import ru.kuznetsov.itstime4service.ui.format.TaskFields
import ru.kuznetsov.itstime4service.ui.format.TaskPreset
import ru.kuznetsov.itstime4service.ui.format.chipLabel
import ru.kuznetsov.itstime4service.ui.format.taskName
import ru.kuznetsov.itstime4service.ui.icons.label
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the task screen from a given state: what a new task and an edited one offer, the preset chips, the required
 * fields marked as errors for the screen reader, the "create a history record" box and its fields, the icon window,
 * and the done key and typing reaching the view model.
 */
@OptIn(ExperimentalTestApi::class)
class TaskFormScreenTest : UiTest() {

    private val calls = mutableListOf<String>()
    private val texts = mutableMapOf<String, String>()
    private var typed: TaskFields? = null

    private val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    private fun ComposeUiTest.show(state: TaskFormUiState, afterSave: (TaskFormUiState) -> TaskFormUiState = { it }) = setContent {
        var current by remember { mutableStateOf(state) }
        texts["date"] = stringResource(Res.string.field_last_done_date_short)
        texts["done"] = stringResource(Res.string.action_done)
        texts["save"] = stringResource(Res.string.action_save)
        texts["comment"] = stringResource(Res.string.field_comment)
        texts["unknown"] = stringResource(Res.string.service_unknown)
        texts["specify"] = stringResource(Res.string.service_specify).uppercase()
        texts["delete"] = stringResource(Res.string.action_remove_task)
        texts["record"] = stringResource(Res.string.task_create_record)
        texts["service"] = stringResource(Res.string.field_service_name)
        texts["pickIcon"] = stringResource(Res.string.action_pick_task_icon)
        texts["cancel"] = stringResource(Res.string.action_cancel)
        texts["oilChip"] = "+ ${stringResource(TaskPreset.OIL.chipLabel)}"
        texts["oilName"] = stringResource(TaskPreset.OIL.taskName)
        texts["disc"] = stringResource(TaskIcon.DISC.label)
        ItsTime4ServiceTheme(darkTheme = false) {
            TaskFormContent(
                state = current,
                onBack = { calls += "back" },
                onFieldsChange = { typed = it },
                onPreset = { preset, name -> calls += "preset $preset $name" },
                onIconPickerOpen = { calls += "pickIcon" },
                onRecordToggle = { calls += "record $it" },
                onIconPickerDismiss = { calls += "iconDismiss" },
                onIconSelect = { calls += "icon $it" },
                onSave = { calls += "save"; current = afterSave(current) },
                onSaveAndAddAnother = { calls += "addAnother" },
                onDeleteRequest = { calls += "delete" },
                onDeleteDismiss = {},
                onDeleteConfirm = {},
            )
        }
    }

    private fun newTask(fields: TaskFields = TaskFields(), showErrors: Boolean = false) =
        TaskFormUiState(isLoading = false, carBrand = "Kia", fields = fields, showErrors = showErrors)

    @Test
    fun newTaskDateOpensCalendarWithoutTextInput() = checkDateCalendar(isEditing = false)

    @Test
    fun editedTaskDateOpensCalendarWithoutTextInput() = checkDateCalendar(isEditing = true)

    private fun checkDateCalendar(isEditing: Boolean) = runComposeUiTest {
        val format = DateInputFormat.current
        show(newTask(TaskFields(name = "Oil", intervalKm = "7500", lastDoneDate = format.digitsOf(LocalDate(2024, 2, 15))))
            .copy(isEditing = isEditing))
        onNodeWithContentDescription(texts.getValue("date")).assert(hasSetTextAction().not()).performScrollTo().performClick()
        onNodeWithText(texts.getValue("done")).assertIsDisplayed()
        onNodeWithText("29", substring = true).performClick()
        onNodeWithText(texts.getValue("done")).performClick()
        assertEquals(format.digitsOf(LocalDate(2024, 2, 29)), typed?.lastDoneDate)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun repeatedInvalidSavesRevealNameAfterScrollingAway() = runComposeUiTest {
        show(newTask(fields = TaskFields(createsRecord = true))) {
            it.copy(showErrors = true, validationRequest = it.validationRequest + 1)
        }
        repeat(2) {
            onNodeWithText(texts.getValue("comment"), substring = true).performScrollTo()
            onNodeWithText(texts.getValue("save")).performClick()
            onAllNodes(hasError and hasSetTextAction())[0].assertIsDisplayed()
        }
    }

    @Test
    fun invalidSaveRevealsRequiredServiceDateWithoutOpeningCalendar() = runComposeUiTest {
        show(newTask(fields = TaskFields(name = "Oil", intervalKm = "7500", lastDoneKm = "84000", createsRecord = true))) {
            it.copy(showErrors = true, validationRequest = it.validationRequest + 1)
        }
        onNodeWithText(texts.getValue("oilChip")).performScrollTo()
        onNodeWithText(texts.getValue("save")).performClick()
        onAllNodes(hasError)[0].assertIsDisplayed()
        onAllNodes(hasError and hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun newTaskOffersPresetsAndRecordButNoDelete() = runComposeUiTest {
        show(newTask())

        onNodeWithText(texts.getValue("oilChip")).performClick()
        onNodeWithText(texts.getValue("record")).performScrollTo().performClick()

        assertEquals(listOf("preset OIL ${texts.getValue("oilName")}", "record true"), calls)
        onAllNodesWithContentDescription(texts.getValue("delete")).assertCountEquals(0)
    }

    @Test
    fun editedTaskOffersDeleteButNoPresetsOrRecord() = runComposeUiTest {
        show(newTask(TaskFields(name = "Масло", intervalKm = "7500")).copy(isEditing = true))

        onNodeWithContentDescription(texts.getValue("delete")).performClick()

        assertEquals(listOf("delete"), calls)
        onAllNodesWithText(texts.getValue("oilChip")).assertCountEquals(0)
        onAllNodesWithText(texts.getValue("record")).assertCountEquals(0)
    }

    @Test
    fun emptyRequiredFieldsAreMarkedAsErrors() = runComposeUiTest {
        show(newTask(showErrors = true))

        onAllNodes(hasError).assertCountEquals(2)
    }

    @Test
    fun recordMakesLastServiceRequiredAndShowsItsFields() = runComposeUiTest {
        show(newTask(TaskFields(name = "Масло", intervalKm = "7500", createsRecord = true), showErrors = true))

        onAllNodes(hasError).assertCountEquals(2)
        onNodeWithText(texts.getValue("service")).assertExists()
    }

    @Test
    fun serviceFieldsAreHiddenWithoutRecord() = runComposeUiTest {
        show(newTask())

        onAllNodesWithText(texts.getValue("service")).assertCountEquals(0)
    }

    @Test
    fun iconWindowReportsThePickedIconAndCancel() = runComposeUiTest {
        show(newTask().copy(isPickingIcon = true))

        onNodeWithContentDescription(texts.getValue("disc")).performClick()
        onNodeWithText(texts.getValue("cancel")).performClick()

        assertEquals(listOf("icon DISC", "iconDismiss"), calls)
    }

    @Test
    fun iconButtonOpensTheWindow() = runComposeUiTest {
        show(newTask())

        onNodeWithContentDescription(texts.getValue("pickIcon")).performClick()

        assertEquals(listOf("pickIcon"), calls)
    }

    @Test
    fun doneKeySavesAndTypingReachesTheForm() = runComposeUiTest {
        show(newTask(TaskFields(name = "Масло")))

        onNode(hasText("Масло") and hasSetTextAction()).performTextInput(" Motul")
        onNode(hasText("Масло") and hasSetTextAction()).performImeAction()

        assertTrue(typed!!.name.contains("Motul") && typed!!.name.contains("Масло"))
        assertEquals(listOf("save"), calls)
    }
    @Test
    fun unknownChoiceReachesFieldsAndDisablesRecordCreation() = runComposeUiTest {
        show(newTask(TaskFields(name = "Oil", intervalKm = "10000", createsRecord = true, lastDoneKm = "80000", lastDoneDate = "01032026")))
        onNodeWithText(texts.getValue("unknown")).performScrollTo().performClick()
        assertTrue(typed!!.unknownService)
        assertEquals(false, typed!!.createsRecord)
    }

    @Test
    fun unknownStateHidesFactInputsAndHistoryCreation() = runComposeUiTest {
        show(newTask(TaskFields(name = "Oil", intervalKm = "10000", unknownService = true)))
        onAllNodesWithText(texts.getValue("record")).assertCountEquals(0)
        onAllNodesWithText(texts.getValue("specify")).assertCountEquals(0)
        onNodeWithText(texts.getValue("unknown")).assertExists()
    }

}
