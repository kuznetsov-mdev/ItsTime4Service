package ru.kuznetsov.itstime4service.features.carform

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
import itstime4service.shared.generated.resources.action_add_task_button
import itstime4service.shared.generated.resources.action_collapse_task
import itstime4service.shared.generated.resources.action_expand_task
import itstime4service.shared.generated.resources.action_remove_task
import itstime4service.shared.generated.resources.action_save_car
import itstime4service.shared.generated.resources.retry
import itstime4service.shared.generated.resources.task_block_title
import itstime4service.shared.generated.resources.task_create_record
import itstime4service.shared.generated.resources.task_every
import itstime4service.shared.generated.resources.task_interval_missing
import itstime4service.shared.generated.resources.task_last_done
import itstime4service.shared.generated.resources.task_last_done_missing
import itstime4service.shared.generated.resources.task_name_oil
import itstime4service.shared.generated.resources.tasks_added
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.format.TaskFields
import ru.kuznetsov.itstime4service.ui.format.TaskPreset
import ru.kuznetsov.itstime4service.ui.format.chipLabel
import ru.kuznetsov.itstime4service.ui.format.grouped
import ru.kuznetsov.itstime4service.ui.format.taskName
import ru.kuznetsov.itstime4service.ui.format.unitGenitive
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the add-car form from a given state: the required car and task fields marked as errors for the screen
 * reader, the preset chips and the bottom buttons, the task blocks expanded and collapsed with their summary,
 * removing a block, the done key and typing reaching the view model, and the load error replacing the form.
 */
@OptIn(ExperimentalTestApi::class)
class CarFormScreenTest : UiTest() {

    private val calls = mutableListOf<String>()
    private val texts = mutableMapOf<String, String>()

    private val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    private fun ComposeUiTest.show(state: CarFormUiState) = setContent {
        val km = unitGenitive(DistanceUnit.KILOMETERS)
        texts["addTask"] = stringResource(Res.string.action_add_task_button)
        texts["save"] = stringResource(Res.string.action_save_car)
        texts["defaultName"] = stringResource(Res.string.task_name_oil)
        texts["oilChip"] = "+ ${stringResource(TaskPreset.OIL.chipLabel)}"
        texts["oilName"] = stringResource(TaskPreset.OIL.taskName)
        texts["remove"] = stringResource(Res.string.action_remove_task)
        texts["expand"] = stringResource(Res.string.action_expand_task)
        texts["collapse"] = stringResource(Res.string.action_collapse_task)
        texts["record"] = stringResource(Res.string.task_create_record)
        texts["retry"] = stringResource(Res.string.retry)
        texts["secondTitle"] = stringResource(Res.string.task_block_title, 2)
        texts["counter"] = pluralStringResource(Res.plurals.tasks_added, state.tasks.size, state.tasks.size)
        texts["filledSummary"] = listOf(
            stringResource(Res.string.task_every, "${10000.grouped()} $km"),
            stringResource(Res.string.task_last_done, "${78000.grouped()} $km"),
        ).joinToString(" · ")
        texts["emptySummary"] = listOf(
            stringResource(Res.string.task_interval_missing),
            stringResource(Res.string.task_last_done_missing),
        ).joinToString(" · ")
        ItsTime4ServiceTheme(darkTheme = false) {
            CarFormContent(
                state = state,
                onBack = { calls += "back" },
                onBrandChange = { calls += "brand $it" },
                onMileageChange = { calls += "mileage $it" },
                onVinChange = { calls += "vin $it" },
                onPlateChange = { calls += "plate $it" },
                onTaskChange = { key, fields -> calls += "task $key ${fields.name}" },
                onAddTask = { calls += "addTask" },
                onAddPreset = { preset, name -> calls += "preset $preset $name" },
                onToggleTask = { calls += "toggle $it" },
                onRemoveTask = { calls += "remove $it" },
                onTaskRecordToggle = { key, on -> calls += "record $key $on" },
                onSave = { calls += "save $it" },
                onRetry = { calls += "retry" },
            )
        }
    }

    private val filledTask = TaskFields(name = "Масло", intervalKm = "10000", lastDoneKm = "78000")

    @Test
    fun emptyCarFieldsAreMarkedAsErrors() = runComposeUiTest {
        show(CarFormUiState(showErrors = true))

        onAllNodes(hasError).assertCountEquals(2)
    }

    @Test
    fun errorsAreHiddenBeforeTheFirstSave() = runComposeUiTest {
        show(CarFormUiState(tasks = listOf(TaskBlockState(key = 0))))

        onAllNodes(hasError).assertCountEquals(0)
    }

    @Test
    fun emptyExpandedTaskAddsItsRequiredFieldsToTheErrors() = runComposeUiTest {
        show(CarFormUiState(brand = "Kia", mileageKm = "84300", tasks = listOf(TaskBlockState(key = 0)), showErrors = true))

        onAllNodes(hasError).assertCountEquals(2)
    }

    @Test
    fun recordMakesLastDoneMileageAndDateRequired() = runComposeUiTest {
        val task = TaskBlockState(key = 0, fields = TaskFields(name = "Масло", intervalKm = "10000", createsRecord = true))
        show(CarFormUiState(brand = "Kia", mileageKm = "84300", tasks = listOf(task), showErrors = true))

        onAllNodes(hasError).assertCountEquals(2)
    }

    @Test
    fun presetChipAndBottomButtonsReachTheViewModel() = runComposeUiTest {
        show(CarFormUiState())

        onNodeWithText(texts.getValue("oilChip")).performScrollTo().performClick()
        onNodeWithText(texts.getValue("addTask")).performClick()
        onNodeWithText(texts.getValue("save")).performClick()

        assertEquals(
            listOf("preset OIL ${texts.getValue("oilName")}", "addTask", "save ${texts.getValue("defaultName")}"),
            calls,
        )
    }

    @Test
    fun collapsedTaskShowsItsNameAndSummaryAndExpandsOnTap() = runComposeUiTest {
        show(CarFormUiState(tasks = listOf(TaskBlockState(key = 7, fields = filledTask, isCollapsed = true))))

        onNodeWithText(texts.getValue("filledSummary")).performScrollTo().assertExists()
        onNodeWithText("Масло").performClick()
        onNodeWithContentDescription(texts.getValue("expand")).performClick()

        assertEquals(listOf("toggle 7", "toggle 7"), calls)
        onAllNodesWithText(texts.getValue("record")).assertCountEquals(0)
    }

    @Test
    fun collapsedTaskWithoutNameShowsItsNumberAndMissingValues() = runComposeUiTest {
        show(
            CarFormUiState(
                tasks = listOf(
                    TaskBlockState(key = 0, fields = filledTask, isCollapsed = true),
                    TaskBlockState(key = 1, isCollapsed = true),
                ),
            ),
        )

        onNodeWithText(texts.getValue("secondTitle")).performScrollTo().assertExists()
        onNodeWithText(texts.getValue("emptySummary")).assertExists()
        onNodeWithText(texts.getValue("counter")).assertExists()
    }

    @Test
    fun expandedTaskCollapsesAndRemoves() = runComposeUiTest {
        show(CarFormUiState(tasks = listOf(TaskBlockState(key = 3, fields = filledTask))))

        onNodeWithContentDescription(texts.getValue("collapse")).performScrollTo().performClick()
        onNodeWithContentDescription(texts.getValue("remove")).performClick()
        onNodeWithText(texts.getValue("record")).performScrollTo().performClick()

        assertEquals(listOf("toggle 3", "remove 3", "record 3 true"), calls)
    }

    @Test
    fun typingReachesTheFormAndDoneKeySaves() = runComposeUiTest {
        show(CarFormUiState(brand = "Kia", tasks = listOf(TaskBlockState(key = 0, fields = filledTask))))

        onNode(hasText("Kia") and hasSetTextAction()).performTextInput(" Rio")
        onNode(hasText("Масло") and hasSetTextAction()).performScrollTo().performTextInput(" Motul")
        onNode(hasText("Kia") and hasSetTextAction()).performScrollTo().performImeAction()

        assertTrue(calls.any { it.startsWith("brand ") && "Kia" in it && "Rio" in it })
        assertTrue(calls.any { it.startsWith("task 0 ") && "Масло" in it && "Motul" in it })
        assertEquals("save ${texts.getValue("defaultName")}", calls.last())
        assertTrue(calls.dropLast(1).all { it.startsWith("brand ") || it.startsWith("task 0 ") })
    }

    @Test
    fun loadErrorReplacesTheFormAndRetries() = runComposeUiTest {
        show(CarFormUiState(loadFailed = true))

        onNodeWithText(texts.getValue("retry")).performClick()

        assertEquals(listOf("retry"), calls)
        onAllNodesWithText(texts.getValue("save")).assertCountEquals(0)
        onAllNodesWithContentDescription(texts.getValue("remove")).assertCountEquals(0)
    }
}
