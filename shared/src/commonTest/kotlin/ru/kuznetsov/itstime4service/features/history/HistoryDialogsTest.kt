package ru.kuznetsov.itstime4service.features.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add
import itstime4service.shared.generated.resources.action_apply
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_more_entry_fields
import itstime4service.shared.generated.resources.action_reset
import itstime4service.shared.generated.resources.action_save
import itstime4service.shared.generated.resources.field_service_name
import itstime4service.shared.generated.resources.history_sold_car
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the cards of the history windows from a given state: the record window, the filter window and the window
 * of a record added by hand. Checks the required fields marked as errors for the screen reader, the buttons,
 * the suggestion chips, the optional fields, the car picker, and typing and the done key reaching the view model.
 */
@OptIn(ExperimentalTestApi::class)
class HistoryDialogsTest : UiTest() {

    private val calls = mutableListOf<String>()
    private val texts = mutableMapOf<String, String>()
    private val entryChanges = mutableListOf<HistoryEntryForm>()
    private val filterChanges = mutableListOf<HistoryFilter>()
    private val newEntryChanges = mutableListOf<NewHistoryEntryForm>()

    private val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    private val kia = HistoryCar("kia", "Kia")
    private val bmw = HistoryCar("bmw", "BMW")

    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        texts["cancel"] = stringResource(Res.string.action_cancel)
        texts["save"] = stringResource(Res.string.action_save)
        texts["add"] = stringResource(Res.string.action_add)
        texts["reset"] = stringResource(Res.string.action_reset)
        texts["apply"] = stringResource(Res.string.action_apply)
        texts["more"] = stringResource(Res.string.action_more_entry_fields)
        texts["service"] = stringResource(Res.string.field_service_name)
        texts["soldKia"] = stringResource(Res.string.history_sold_car, "Kia")
        ItsTime4ServiceTheme(darkTheme = false) {
            Box(Modifier.testTag(SCROLL).verticalScroll(rememberScrollState())) { content() }
        }
    }

    private fun ComposeUiTest.showEntry(form: HistoryEntryForm) = show {
        HistoryEntryCard(
            form = form,
            distanceUnit = DistanceUnit.KILOMETERS,
            onChange = { entryChanges += it },
            onSave = { calls += "save" },
            onDismiss = { calls += "dismiss" },
        )
    }

    private fun ComposeUiTest.showFilter(filter: HistoryFilter) = show {
        HistoryFilterCard(
            filter = filter,
            distanceUnit = DistanceUnit.KILOMETERS,
            onChange = { filterChanges += it },
            onApply = { calls += "apply" },
            onReset = { calls += "reset" },
        )
    }

    private fun ComposeUiTest.showNewEntry(form: NewHistoryEntryForm, suggestions: List<String> = emptyList()) = show {
        NewHistoryEntryCard(
            form = form,
            cars = listOf(kia, bmw),
            suggestions = suggestions,
            distanceUnit = DistanceUnit.KILOMETERS,
            onChange = { newEntryChanges += it },
            onSave = { calls += "save" },
            onDismiss = { calls += "dismiss" },
        )
    }

    private fun record(date: String = "", mileage: String = "", showErrors: Boolean = false) = HistoryEntryForm(
        entryId = "e1",
        taskName = "Масло",
        car = kia.copy(isSold = true),
        date = date,
        mileage = mileage,
        showErrors = showErrors,
    )

    @Test
    fun recordShowsTaskAndSoldCarAndMarksInvalidFields() = runComposeUiTest {
        showEntry(record(showErrors = true))

        onNodeWithText("Масло").assertExists()
        onNodeWithText(texts.getValue("soldKia")).assertExists()
        onAllNodes(hasError).assertCountEquals(2)
    }

    @Test
    fun recordErrorsAreHiddenBeforeTheFirstSave() = runComposeUiTest {
        showEntry(record())

        onAllNodes(hasError).assertCountEquals(0)
    }

    @Test
    fun recordButtonsAndTypingReachTheViewModel() = runComposeUiTest {
        showEntry(record(mileage = "61500"))

        onNode(hasText("61500") and hasSetTextAction()).performTextInput("7")
        onNode(hasText("61500") and hasSetTextAction()).performImeAction()
        onNodeWithText(texts.getValue("cancel")).performScrollTo().performClick()
        onNodeWithText(texts.getValue("save")).performScrollTo().performClick()

        assertTrue(entryChanges.any { it.mileage.length == 6 && "61500" in it.mileage && "7" in it.mileage })
        assertEquals(listOf("save", "dismiss", "save"), calls)
    }

    @Test
    fun filterMarksUnreadableDatesAfterApply() = runComposeUiTest {
        showFilter(HistoryFilter(dateFrom = "12", dateTo = "3", showErrors = true))

        onAllNodes(hasError).assertCountEquals(2)
    }

    @Test
    fun filterErrorsAreHiddenBeforeApply() = runComposeUiTest {
        showFilter(HistoryFilter(dateFrom = "12", dateTo = "3"))

        onAllNodes(hasError).assertCountEquals(0)
    }

    @Test
    fun filterButtonsDoneKeyAndTypingReachTheViewModel() = runComposeUiTest {
        showFilter(HistoryFilter(query = "мас"))

        onNode(hasText("мас") and hasSetTextAction()).performTextInput("ло")
        onNode(hasText("мас") and hasSetTextAction()).performImeAction()
        onNodeWithText(texts.getValue("reset")).performScrollTo().performClick()
        onNodeWithText(texts.getValue("apply")).performScrollTo().performClick()

        assertTrue(filterChanges.any { "мас" in it.query && "ло" in it.query })
        assertEquals(listOf("apply", "reset", "apply"), calls)
    }

    @Test
    fun newEntryMarksEmptyRequiredFields() = runComposeUiTest {
        showNewEntry(NewHistoryEntryForm(carId = "kia", showErrors = true))

        onAllNodes(hasError).assertCountEquals(3)
    }

    @Test
    fun newEntrySuggestionFillsTheTaskName() = runComposeUiTest {
        showNewEntry(NewHistoryEntryForm(carId = "kia"), suggestions = listOf("Масло", "Колодки"))

        onNodeWithText("+ Колодки").performScrollTo().performClick()

        assertEquals("Колодки", newEntryChanges.last().taskName)
    }

    @Test
    fun newEntryHidesOptionalFieldsUntilAsked() = runComposeUiTest {
        showNewEntry(NewHistoryEntryForm(carId = "kia"))

        onAllNodesWithText(texts.getValue("service")).assertCountEquals(0)
        onNodeWithTag(SCROLL).performTouchInput { swipeUp() }
        onNodeWithText(texts.getValue("more")).performScrollTo().performClick()

        assertTrue(newEntryChanges.last().isExpanded)
    }

    @Test
    fun expandedNewEntryShowsOptionalFields() = runComposeUiTest {
        showNewEntry(NewHistoryEntryForm(carId = "kia", isExpanded = true))

        onNodeWithText(texts.getValue("service")).performScrollTo().assertExists()
        onAllNodesWithText(texts.getValue("more")).assertCountEquals(0)
    }

    @Test
    fun carFieldOpensTheCarList() = runComposeUiTest {
        showNewEntry(NewHistoryEntryForm(carId = "kia"))

        onNodeWithText("Kia").performClick()

        assertTrue(newEntryChanges.last().isCarMenuOpen)
        onAllNodesWithText("BMW").assertCountEquals(0)
    }

    @Test
    fun pickedCarReplacesTheChoiceAndClosesTheList() = runComposeUiTest {
        showNewEntry(NewHistoryEntryForm(carId = "kia", isCarMenuOpen = true))

        onNodeWithText("BMW").performClick()

        assertEquals("bmw", newEntryChanges.last().carId)
        assertEquals(false, newEntryChanges.last().isCarMenuOpen)
    }

    @Test
    fun newEntryButtonsReachTheViewModel() = runComposeUiTest {
        showNewEntry(NewHistoryEntryForm(carId = "kia"))

        onNodeWithText(texts.getValue("cancel")).performScrollTo().performClick()
        onNodeWithText(texts.getValue("add")).performScrollTo().performClick()

        assertEquals(listOf("dismiss", "save"), calls)
    }
}

private const val SCROLL = "scroll"
