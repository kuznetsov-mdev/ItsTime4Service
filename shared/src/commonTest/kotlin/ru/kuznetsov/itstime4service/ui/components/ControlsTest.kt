package ru.kuznetsov.itstime4service.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_decrease
import itstime4service.shared.generated.resources.action_done
import itstime4service.shared.generated.resources.action_increase
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.ui.components.buttons.OutlineButton
import ru.kuznetsov.itstime4service.ui.components.buttons.PrimaryButton
import ru.kuznetsov.itstime4service.ui.components.buttons.SegmentedControl
import ru.kuznetsov.itstime4service.ui.components.fields.AppNumberField
import ru.kuznetsov.itstime4service.ui.components.fields.AppTextField
import ru.kuznetsov.itstime4service.ui.components.fields.NumericKeyboard
import ru.kuznetsov.itstime4service.ui.components.indicators.AppCheckbox
import ru.kuznetsov.itstime4service.ui.components.indicators.AppSwitch
import ru.kuznetsov.itstime4service.ui.components.steppers.Stepper
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the shared controls by their public behaviour: the stepper and its bounds, the switch and the checkbox
 * as toggles for the screen reader, the segmented control reporting the tapped option, compact buttons in a row
 * keeping their text on one line, the cursor of a field whose value arrives a frame late, and the check button that
 * a focused numeric field shows where the numeric keyboard has no done key.
 */
@OptIn(ExperimentalTestApi::class)
class ControlsTest : UiTest() {

    private val calls = mutableListOf<String>()
    private val texts = mutableMapOf<String, String>()

    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        texts["increase"] = stringResource(Res.string.action_increase)
        texts["decrease"] = stringResource(Res.string.action_decrease)
        texts["done"] = stringResource(Res.string.action_done)
        ItsTime4ServiceTheme(darkTheme = false) { content() }
    }

    @Test
    fun stepperShowsItsValueAndSteps() = runComposeUiTest {
        show { Stepper(value = 5, onValueChange = { calls += "value $it" }, min = 1, max = 9, step = 2) }

        onNodeWithText("5").assertExists()
        onNodeWithContentDescription(texts.getValue("increase")).performClick()
        onNodeWithContentDescription(texts.getValue("decrease")).performClick()

        assertEquals(listOf("value 7", "value 3"), calls)
    }

    @Test
    fun stepperTurnsOffAButtonThatWouldLeaveTheRange() = runComposeUiTest {
        show { Stepper(value = 1, onValueChange = { calls += "value $it" }, min = 1, max = 2) }

        onNodeWithContentDescription(texts.getValue("decrease")).assertIsNotEnabled().performClick()
        onNodeWithContentDescription(texts.getValue("increase")).assertIsEnabled()

        assertEquals(emptyList(), calls)
    }

    @Test
    fun switchIsAToggleWithItsState() = runComposeUiTest {
        show { AppSwitch(checked = false, onCheckedChange = { calls += "switch $it" }) }

        onNode(isToggleable()).assertIsOff().performClick()

        assertEquals(listOf("switch true"), calls)
    }

    @Test
    fun checkboxIsAToggleNamedByItsLabel() = runComposeUiTest {
        show { AppCheckbox(checked = true, onCheckedChange = { calls += "check $it" }, label = "Создать запись") }

        onNodeWithText("Создать запись").assertIsOn().performClick()

        assertEquals(listOf("check false"), calls)
    }

    @Test
    fun segmentedControlReportsTheTappedOption() = runComposeUiTest {
        show { SegmentedControl(options = listOf("км", "мили"), selectedIndex = 0, onSelect = { calls += "option $it" }) }

        onNodeWithText("мили").performClick()
        onNodeWithText("км").performClick()

        assertEquals(listOf("option 1", "option 0"), calls)
    }

    @Test
    fun segmentedControlMarksOnlyTheSelectedOption() = runComposeUiTest {
        var selected by mutableStateOf(0)
        show { SegmentedControl(options = listOf("Светлая", "Тёмная", "Системная"), selectedIndex = selected, onSelect = { selected = it }) }

        onNodeWithText("Светлая").assertIsSelected()
        onNodeWithText("Тёмная").assertIsNotSelected()
        onNodeWithText("Системная").assertIsNotSelected()

        onNodeWithText("Тёмная").performClick()

        onNodeWithText("Светлая").assertIsNotSelected()
        onNodeWithText("Тёмная").assertIsSelected()
        onNodeWithText("Системная").assertIsNotSelected()
    }

    @Test
    fun focusedNumberFieldShowsACheckOnlyWithoutAKeyboardDoneKey() = runComposeUiTest {
        show { AppNumberField(value = "12", onValueChange = {}, onDone = { calls += "done" }) }

        onNodeWithContentDescription(texts.getValue("done")).assertDoesNotExist()
        onNode(hasSetTextAction()).performClick()

        if (NumericKeyboard.hasDoneKey) {
            onAllNodesWithContentDescription(texts.getValue("done")).assertCountEquals(0)
        } else {
            onNodeWithContentDescription(texts.getValue("done")).performClick()
            assertEquals(listOf("done"), calls)
        }
    }

    @Test
    fun compactButtonsInARowKeepTheirTextOnOneLine() = runComposeUiTest {
        show {
            Row(Modifier.width(284.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("+ Добавить работу", onClick = {}, modifier = Modifier.weight(1f), compact = true)
                PrimaryButton("Сохранить авто", onClick = {}, modifier = Modifier.weight(1f), compact = true)
            }
        }

        assertEquals(1, lineCount("+ Добавить работу"))
        assertEquals(1, lineCount("Сохранить авто"))
    }

    private fun ComposeUiTest.lineCount(text: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().lineCount
    }

    @Test
    fun valueArrivingLateKeepsTheCursorAfterTheTypedText() = runComposeUiTest {
        var value by mutableStateOf("")
        var typed = ""
        show { AppTextField(value = value, onValueChange = { typed = it }) }

        onNode(hasSetTextAction()).performTextInput("К")
        waitForIdle()
        value = typed
        waitForIdle()
        onNode(hasSetTextAction()).performTextInput("о")
        waitForIdle()

        assertEquals("Ко", typed)
    }

    @Test
    fun textFieldNeverShowsTheCheck() = runComposeUiTest {
        show { AppTextField(value = "Kia", onValueChange = {}, onDone = { calls += "done" }) }

        onNode(hasSetTextAction()).performClick()

        onAllNodesWithContentDescription(texts.getValue("done")).assertCountEquals(0)
    }
}
