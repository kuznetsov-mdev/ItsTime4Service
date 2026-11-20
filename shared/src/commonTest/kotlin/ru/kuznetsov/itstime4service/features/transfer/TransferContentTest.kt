package ru.kuznetsov.itstime4service.features.transfer

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import itstime4service.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.domain.transfer.*
import ru.kuznetsov.itstime4service.data.transfer.TransferFixtures
import ru.kuznetsov.itstime4service.data.transfer.TransferCodecImpl
import ru.kuznetsov.itstime4service.data.transfer.TransferDocumentCodecImpl
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import ru.kuznetsov.itstime4service.ui.transfer.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Verifies the transfer screen contract on a small screen without native picker or saved secrets. */
@OptIn(ExperimentalTestApi::class)
class TransferContentTest : UiTest() {
    private val texts = mutableMapOf<String, String>()
    private fun ComposeUiTest.show(state: () -> TransferUiState, actions: TransferActions = TransferActions()) = setContent {
        texts["vinHidden"] = stringResource(Res.string.transfer_vin) + ": " + stringResource(Res.string.transfer_hidden)
        texts["vinVisible"] = stringResource(Res.string.transfer_vin) + ": " + stringResource(Res.string.transfer_visible)
        texts["plateHidden"] = stringResource(Res.string.transfer_plate) + ": " + stringResource(Res.string.transfer_hidden)
        texts["plateVisible"] = stringResource(Res.string.transfer_plate) + ": " + stringResource(Res.string.transfer_visible)
        texts["incoming"] = stringResource(Res.string.transfer_take_incoming)
        texts["pick"] = stringResource(Res.string.transfer_pick)
        texts["password"] = stringResource(Res.string.transfer_password)
        texts["confirm"] = stringResource(Res.string.transfer_password_confirm)
        texts["continue"] = stringResource(Res.string.transfer_continue)
        texts["protection"] = stringResource(Res.string.transfer_protection)
        texts["confirmPlaintext"] = stringResource(Res.string.transfer_plain_confirm)
        texts["lastService"] = stringResource(Res.string.transfer_last_service)
        texts["save"] = stringResource(Res.string.transfer_save)
        texts["share"] = stringResource(Res.string.transfer_share)
        texts["apply"] = stringResource(Res.string.transfer_apply)
        texts["replace"] = stringResource(Res.string.transfer_replace)
        texts["warning"] = stringResource(Res.string.transfer_replace_confirm)
        texts["cancel"] = stringResource(Res.string.action_cancel)
        texts["done"] = stringResource(Res.string.action_done)
        texts["retry"] = stringResource(Res.string.transfer_retry_notifications)
        ItsTime4ServiceTheme { TransferContent(state(), actions) }
    }

    @Test fun visibilityConflictsShowBothValuesBeforeTheChoice() = runComposeUiTest {
        val snapshot = TransferFixtures.backup()
        val source = snapshot.cars.single().copy(preferences = Presence.Present(CarPreferences(true, false)))
        val local = source.copy(id = "local", preferences = Presence.Present(CarPreferences(false, true)))
        val pending = PendingDecision("preferences", setOf(EntityRef(source.id, source.id)), ImportDecisionReason.PERSONAL_FIELD, "preferences")
        var chosen: PersonalChoice? = null
        show({ TransferUiState(isLoading = false, step = TransferStep.IMPORT_DECISIONS,
            incoming = snapshot.copy(cars = listOf(source)), localDetails = listOf(local), pending = listOf(pending),
            decisions = ImportDecisions(selectedTasks = emptySet(), carTargets = mapOf(source.id to TargetCar.Existing(local.id)))) },
            TransferActions(personal = { _, choice -> chosen = choice }))
        for (key in listOf("vinHidden", "vinVisible", "plateHidden", "plateVisible")) {
            onNodeWithText(texts.getValue(key)).performScrollTo().assertIsDisplayed()
        }
        onNode(hasText(texts.getValue("incoming")) and isSelectable()).performScrollTo().performClick()
        assertEquals(PersonalChoice.TAKE_INCOMING, chosen)
    }

    @Test fun aNewDestinationShowsVisibleDefaultsAgainstIncomingHiddenFlags() = runComposeUiTest {
        val snapshot = TransferFixtures.backup()
        val source = snapshot.cars.single().copy(preferences = Presence.Present(CarPreferences(true, true)))
        val pending = PendingDecision("preferences", setOf(EntityRef(source.id, source.id)), ImportDecisionReason.PERSONAL_FIELD, "preferences")
        show({ TransferUiState(isLoading = false, step = TransferStep.IMPORT_DECISIONS,
            incoming = snapshot.copy(cars = listOf(source)), pending = listOf(pending),
            decisions = ImportDecisions(selectedTasks = emptySet(), carTargets = mapOf(source.id to TargetCar.New("New", 12000)))) })
        for (key in listOf("vinHidden", "vinVisible", "plateHidden", "plateVisible")) {
            onNodeWithText(texts.getValue(key)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun chooseFileActionIsReachableAndBusyDisablesIt() = runComposeUiTest {
        var state by mutableStateOf(TransferUiState(isLoading = false, step = TransferStep.IMPORT_FILE))
        var picks = 0
        show({ state }, TransferActions(pick = { picks++ }))
        onNodeWithText(texts.getValue("pick")).performScrollTo().performClick()
        assertEquals(1, picks)
        runOnIdle { state = state.copy(isBusy = true) }
        onNodeWithText(texts.getValue("pick")).assertIsNotEnabled()
    }

    @Test fun protectedFileShowsNoContentsAndClearsPasswordOnSubmit() = runComposeUiTest {
        var password: String? = null
        show({ TransferUiState(isLoading = false, step = TransferStep.UNLOCK) }, TransferActions(unlock = { password = it }))
        onNodeWithContentDescription(texts.getValue("password")).performScrollTo().performTextInput("Synthetic UI password")
        onNodeWithText(texts.getValue("continue")).performScrollTo().performClick()
        assertEquals("Synthetic UI password", password)
        onNodeWithContentDescription(texts.getValue("password")).assertTextEquals("")
        onNodeWithText("Synthetic car").assertDoesNotExist()
    }

    @Test fun leavingExportForPreviewDisposesPasswords() = runComposeUiTest {
        var state by mutableStateOf(TransferUiState(isLoading = false, step = TransferStep.EXPORT))
        var values: Pair<String, String>? = null
        show({ state }, TransferActions(previewExport = { a, b -> values = a to b }))
        onNodeWithContentDescription(texts.getValue("password")).performScrollTo().performTextInput("Synthetic export password")
        onNodeWithContentDescription(texts.getValue("confirm")).performScrollTo().performTextInput("Synthetic export password")
        onNodeWithText(texts.getValue("continue")).performClick()
        assertEquals("Synthetic export password" to "Synthetic export password", values)
        runOnIdle { state = state.copy(step = TransferStep.EXPORT_PREVIEW) }
        runOnIdle { state = state.copy(step = TransferStep.EXPORT) }
        onNodeWithContentDescription(texts.getValue("password")).performScrollTo().assertTextEquals("")
    }

    @Test fun continueStaysVisibleWhileBothExportFormsScroll() = runComposeUiTest {
        val tasks = List(32) { index -> MaintenanceTask("task-$index", "Task $index", 10000, null, 1500) }
        val car = Car("car", "Synthetic car", 12000, null, tasks)
        var state by mutableStateOf(TransferUiState(
            isLoading = false,
            step = TransferStep.EXPORT,
            exportPurpose = TransferExportPurpose.CAR_BACKUP,
            cars = listOf(car),
            exportCarId = car.id,
            isExportCarFixed = true,
            exportTaskIds = tasks.map { it.id }.toSet(),
            exportGroups = setOf(TransferGroup.LAST_SERVICE),
        ))
        show({ state })
        val next = onNodeWithText(texts.getValue("continue"))
        val protection = onNodeWithText(texts.getValue("protection"))
        protection.assertIsDisplayed()
        next.assertIsDisplayed()
        val position = next.fetchSemanticsNode().boundsInRoot
        onNodeWithText(texts.getValue("lastService")).performScrollTo().assertIsDisplayed()
        next.assertIsDisplayed()
        assertEquals(position, next.fetchSemanticsNode().boundsInRoot)

        runOnIdle {
            state = state.copy(exportPurpose = TransferExportPurpose.SHARE_SCHEDULE, isProtected = false, exportGroups = emptySet())
        }
        onNodeWithText(texts.getValue("lastService")).assertDoesNotExist()
        protection.performScrollTo().assertIsDisplayed()
        onNodeWithText("Task 31").performScrollTo().assertIsDisplayed()
        next.assertIsDisplayed()
        assertEquals(position, next.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun replacementNeedsFinalConfirmationAndCancelDoesNotApply() = runComposeUiTest {
        var applied = 0
        show({ TransferUiState(isLoading = false, step = TransferStep.IMPORT_PREVIEW, importMode = ImportMode.REPLACE_BACKUP) },
            TransferActions(apply = { applied++ }))
        onNodeWithText(texts.getValue("apply")).performScrollTo().performClick()
        onNodeWithText(texts.getValue("warning")).assertIsDisplayed()
        onNodeWithText(texts.getValue("cancel")).performClick()
        assertEquals(0, applied)
        onNodeWithText(texts.getValue("apply")).performClick()
        onNodeWithText(texts.getValue("replace")).performClick()
        assertEquals(1, applied)
    }

    @Test fun disablingExportProtectionShowsACenteredConfirmAction() = runComposeUiTest {
        var confirmed = false
        show({ TransferUiState(isLoading = false, step = TransferStep.EXPORT, isConfirmingPlaintext = true) },
            TransferActions(plaintext = { confirmed = it }))

        val label = texts.getValue("confirmPlaintext")
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(label, useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse(layout.hasVisualOverflow)
        assertEquals(layout.size.width / 2f, (layout.getLineLeft(0) + layout.getLineRight(0)) / 2f, absoluteTolerance = 1f)
        onNodeWithText(label).performClick()
        assertTrue(confirmed)
    }

    @Test fun resultRetriesOnlyNotificationsAndDoneCloses() = runComposeUiTest {
        var retries = 0
        var closed = 0
        val summary = ImportSummary(1,0,0,1,0,0,0,0,0,0,false,emptySet())
        show({ TransferUiState(isLoading = false, step = TransferStep.RESULT,
            importResult = ImportResult("operation", summary, false, NotificationState.RETRY_REQUIRED)) },
            TransferActions(retryNotifications = { retries++ }, back = { closed++ }))
        onNodeWithText(texts.getValue("retry")).performScrollTo().performClick()
        onNodeWithText(texts.getValue("done")).performScrollTo().performClick()
        assertEquals(1, retries)
        assertEquals(1, closed)
        onNodeWithText(texts.getValue("apply")).assertDoesNotExist()
    }
}
