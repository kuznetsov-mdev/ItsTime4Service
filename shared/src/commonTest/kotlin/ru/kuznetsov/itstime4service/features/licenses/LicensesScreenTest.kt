package ru.kuznetsov.itstime4service.features.licenses

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_back
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the open source licenses screen: every library is listed with its link, a tap on a library opens the full
 * text of its license read from the resources and a second tap closes it, and "back" leaves the screen.
 */
@OptIn(ExperimentalTestApi::class)
class LicensesScreenTest : UiTest() {

    @Test
    fun everyLibraryIsListedWithItsLink() = runComposeUiTest {
        setContent { ItsTime4ServiceTheme(darkTheme = false) { LicensesScreen(onBack = {}) } }

        openSourceLibraries.forEach { library ->
            onNode(hasScrollAction()).performScrollToNode(hasText(library.name))
            onNodeWithText(library.name).assertIsDisplayed()
            onNodeWithText(library.url).assertIsDisplayed()
        }
    }

    @Test
    fun tapOpensAndClosesTheLicenseText() = runComposeUiTest {
        setContent { ItsTime4ServiceTheme(darkTheme = false) { LicensesScreen(onBack = {}) } }
        val lucide = openSourceLibraries.first { it.name == "Lucide" }
        onNode(hasScrollAction()).performScrollToNode(hasText(lucide.name))

        onNodeWithText(lucide.name).performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText(LICENSE_LINE, substring = true)).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText(lucide.name).performClick()
        waitForIdle()
        assertEquals(0, onAllNodes(hasText(LICENSE_LINE, substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun backLeavesTheScreen() = runComposeUiTest {
        var backs = 0
        var backLabel = ""
        setContent {
            backLabel = stringResource(Res.string.action_back)
            ItsTime4ServiceTheme(darkTheme = false) { LicensesScreen(onBack = { backs++ }) }
        }

        onNodeWithContentDescription(backLabel).performClick()

        assertEquals(1, backs)
    }

    private companion object {
        const val LICENSE_LINE = "Permission to use, copy, modify"
        const val TIMEOUT_MS = 5_000L
    }
}
