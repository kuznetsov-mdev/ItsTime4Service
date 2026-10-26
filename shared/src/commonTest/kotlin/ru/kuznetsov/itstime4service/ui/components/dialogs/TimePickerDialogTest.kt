package ru.kuznetsov.itstime4service.ui.components.dialogs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_decrease
import itstime4service.shared.generated.resources.action_increase
import itstime4service.shared.generated.resources.action_save
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the reminder time window: hours step by one and minutes by five, both wrap around the day and the hour,
 * the time is reported only on save, and cancel reports nothing but the dismissal.
 */
@OptIn(ExperimentalTestApi::class)
class TimePickerDialogTest : UiTest() {

    private val calls = mutableListOf<String>()
    private val texts = mutableMapOf<String, String>()

    private fun ComposeUiTest.show(time: LocalTime) = setContent {
        texts["increase"] = stringResource(Res.string.action_increase)
        texts["decrease"] = stringResource(Res.string.action_decrease)
        texts["save"] = stringResource(Res.string.action_save)
        texts["cancel"] = stringResource(Res.string.action_cancel)
        ItsTime4ServiceTheme(darkTheme = false) {
            TimePickerDialog(
                title = "Время",
                text = "",
                time = time,
                onConfirm = { calls += "time $it" },
                onDismiss = { calls += "dismiss" },
            )
        }
    }

    private fun ComposeUiTest.press(key: String, row: Int) =
        onAllNodesWithContentDescription(texts.getValue(key))[row].performClick()

    @Test
    fun hoursAndMinutesWrapForward() = runComposeUiTest {
        show(LocalTime(23, 55))

        press("increase", row = 0)
        press("increase", row = 1)
        onNodeWithText(texts.getValue("save")).performClick()

        assertEquals(listOf("time 00:00"), calls)
    }

    @Test
    fun hoursAndMinutesWrapBackward() = runComposeUiTest {
        show(LocalTime(0, 0))

        press("decrease", row = 0)
        press("decrease", row = 1)

        onNodeWithText("23").assertExists()
        onNodeWithText("55").assertExists()
        onNodeWithText(texts.getValue("save")).performClick()
        assertEquals(listOf("time 23:55"), calls)
    }

    @Test
    fun cancelReportsOnlyTheDismissal() = runComposeUiTest {
        show(LocalTime(9, 0))

        press("increase", row = 0)
        onNodeWithText(texts.getValue("cancel")).performClick()

        assertEquals(listOf("dismiss"), calls)
    }
}
