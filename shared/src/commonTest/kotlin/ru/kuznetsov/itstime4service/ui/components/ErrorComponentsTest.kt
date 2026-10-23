package ru.kuznetsov.itstime4service.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.load_failed_text
import itstime4service.shared.generated.resources.load_failed_title
import itstime4service.shared.generated.resources.retry
import itstime4service.shared.generated.resources.save_failed
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.ui.components.indicators.ErrorBar
import ru.kuznetsov.itstime4service.ui.components.screen.LoadError
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the two database error components: the "could not save" bar is drawn only while visible and reports itself
 * shown after four seconds, so the screen can hide it; the load error shows its title, its explanation and "Retry".
 */
@OptIn(ExperimentalTestApi::class)
class ErrorComponentsTest : UiTest() {

    @Test
    fun hiddenBarDrawsNothing() = runComposeUiTest {
        var message = ""
        setContent {
            message = stringResource(Res.string.save_failed)
            ItsTime4ServiceTheme(darkTheme = false) { ErrorBar(visible = false, onShown = {}) }
        }

        onAllNodesWithText(message).assertCountEquals(0)
    }

    @Test
    fun barGoesAwayAfterFourSeconds() = runComposeUiTest {
        mainClock.autoAdvance = false
        var message = ""
        var visible by mutableStateOf(true)
        var shown = 0
        setContent {
            message = stringResource(Res.string.save_failed)
            ItsTime4ServiceTheme(darkTheme = false) {
                ErrorBar(visible = visible, onShown = {
                    shown++
                    visible = false
                })
            }
        }

        onNodeWithText(message).assertIsDisplayed()
        mainClock.advanceTimeBy(3_900)
        assertEquals(0, shown)
        onNodeWithText(message).assertIsDisplayed()
        mainClock.advanceTimeBy(200)
        assertEquals(1, shown)
        onAllNodesWithText(message).assertCountEquals(0)
    }

    @Test
    fun loadErrorExplainsAndRetries() = runComposeUiTest {
        var title = ""
        var text = ""
        var retry = ""
        var retries = 0
        setContent {
            title = stringResource(Res.string.load_failed_title)
            text = stringResource(Res.string.load_failed_text)
            retry = stringResource(Res.string.retry)
            ItsTime4ServiceTheme(darkTheme = false) { LoadError(onRetry = { retries++ }) }
        }

        onNodeWithText(title).assertIsDisplayed()
        onNodeWithText(text).assertIsDisplayed()
        onNodeWithText(retry).performClick()

        assertEquals(1, retries)
    }
}
