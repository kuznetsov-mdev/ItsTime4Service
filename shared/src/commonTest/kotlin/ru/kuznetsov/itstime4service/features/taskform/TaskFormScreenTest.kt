package ru.kuznetsov.itstime4service.features.taskform

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
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

    private fun ComposeUiTest.show(state: TaskFormUiState) = setContent {
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
                state = state,
                onBack = { calls += "back" },
                onFieldsChange = { typed = it },
                onPreset = { preset, name -> calls += "preset $preset $name" },
                onIconPickerOpen = { calls += "pickIcon" },
                onRecordToggle = { calls += "record $it" },
                onIconPickerDismiss = { calls += "iconDismiss" },
                onIconSelect = { calls += "icon $it" },
                onSave = { calls += "save" },
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
}
