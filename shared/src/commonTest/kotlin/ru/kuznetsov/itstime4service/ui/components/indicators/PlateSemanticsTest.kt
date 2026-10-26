package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.plate_hidden
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test

/**
 * Covers what the screen reader and the semantics tree get from a licence plate: the number of a shown plate,
 * and for a hidden one only "plate hidden" — the real characters are neither read out nor drawn, for every look.
 */
@OptIn(ExperimentalTestApi::class)
class PlateSemanticsTest : UiTest() {

    private var hidden = ""

    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        hidden = stringResource(Res.string.plate_hidden)
        ItsTime4ServiceTheme(darkTheme = false) { content() }
    }

    private fun ComposeUiTest.assertNoTrace(vararg parts: String) = parts.forEach { part ->
        onAllNodesWithText(part, substring = true, useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithContentDescription(part, substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun shownPlateIsReadAsItsNumber() = runComposeUiTest {
        show { LicensePlate(PlateLook.Universal("A123BC")) }

        onNodeWithContentDescription("A123BC").assertExists()
    }

    @Test
    fun maskedUniversalPlateHidesItsNumber() = runComposeUiTest {
        show { LicensePlate(PlateLook.Universal("A123BC"), masked = true) }

        onNodeWithContentDescription(hidden).assertExists()
        assertNoTrace("A123BC", "123")
    }

    @Test
    fun maskedRussianPlateHidesSeriesAndRegion() = runComposeUiTest {
        show { LicensePlate(PlateLook.Russian("А 123 ВС", "77", "А123ВС77"), masked = true) }

        onNodeWithContentDescription(hidden).assertExists()
        assertNoTrace("А123ВС77", "123", "ВС", "77")
    }

    @Test
    fun maskedEuropeanAndAmericanPlatesHideTheirNumbers() = runComposeUiTest {
        show {
            LicensePlate(PlateLook.European("D", "B AB 1234"), masked = true)
            LicensePlate(PlateLook.American("7ABC123"), masked = true)
        }

        onAllNodesWithContentDescription(hidden).assertCountEquals(2)
        assertNoTrace("1234", "7ABC123", "AB")
    }

    @Test
    fun hiddenFlippablePlateHidesItsNumber() = runComposeUiTest {
        show { FlippablePlate(PlateLook.Universal("A123BC"), hidden = true) }

        onNodeWithContentDescription(hidden).assertExists()
        assertNoTrace("A123BC", "123")
    }
}
