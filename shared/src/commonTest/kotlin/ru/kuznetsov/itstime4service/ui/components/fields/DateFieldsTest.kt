package ru.kuznetsov.itstime4service.ui.components.fields

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_clear
import itstime4service.shared.generated.resources.action_done
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Covers calendar-only input, locale-preserving leap-day selection, cancellation and clearing. */
@OptIn(ExperimentalTestApi::class)
class DateFieldsTest : UiTest() {
    private val originalLanguage = DateInputFormat.language
    private val texts = mutableMapOf<String, String>()
    private val changes = mutableListOf<String>()

    @AfterTest
    fun restoreLanguage() { DateInputFormat.language = originalLanguage }

    private fun ComposeUiTest.showField(language: String = "en", dark: Boolean = false): MutableState<String> {
        DateInputFormat.language = { language }
        val value = mutableStateOf(DateInputFormat.current.digitsOf(LocalDate(2024, 2, 15)))
        setContent {
            texts["done"] = stringResource(Res.string.action_done)
            texts["cancel"] = stringResource(Res.string.action_cancel)
            texts["clear"] = stringResource(Res.string.action_clear)
            ItsTime4ServiceTheme(darkTheme = dark) {
                AppDateField(value.value, { value.value = it; changes += it }, "Service date")
            }
        }
        return value
    }

    @Test
    fun calendarHasNoEditableDateOrKeyboardMode() = runComposeUiTest {
        showField()
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
        onNodeWithContentDescription("Service date").performClick()
        onNodeWithText("29", substring = true).assertExists()
        onNodeWithText(texts.getValue("done")).assertExists()
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun russianCalendarSelectionKeepsDayMonthOrder() = selectLeapDay("ru", "29022024")

    @Test
    fun spanishCalendarSelectionKeepsDayMonthOrder() = selectLeapDay("es", "29022024")

    @Test
    fun englishCalendarSelectionKeepsMonthDayOrder() = selectLeapDay("en", "02292024")

    private fun selectLeapDay(language: String, expected: String) = runComposeUiTest {
        val value = showField(language)
        onNodeWithContentDescription("Service date").performClick()
        onNodeWithText("29", substring = true).performClick()
        onNodeWithText(texts.getValue("done")).performClick()
        runOnIdle {
            assertEquals(expected, value.value)
            assertEquals(listOf(expected), changes)
        }
    }

    @Test
    fun cancellingDoesNotPublishIntermediateSelection() = runComposeUiTest {
        val value = showField()
        onNodeWithContentDescription("Service date").performClick()
        onNodeWithText("29", substring = true).performClick()
        onNodeWithText(texts.getValue("cancel")).performClick()
        runOnIdle {
            assertEquals("02152024", value.value)
            assertTrue(changes.isEmpty())
        }
        onNodeWithText("02/15/2024").assertExists()
    }

    @Test
    fun clearEmptiesTheDateInDarkTheme() = runComposeUiTest {
        val value = showField(dark = true)
        onNodeWithContentDescription("Service date").performClick()
        onNodeWithText(texts.getValue("clear")).performClick()
        runOnIdle {
            assertEquals("", value.value)
            assertEquals(listOf(""), changes)
        }
    }
}
