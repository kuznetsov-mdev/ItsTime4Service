package ru.kuznetsov.itstime4service.features.transfer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.*
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinIsolatedContext
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.features.TestApp
import ru.kuznetsov.itstime4service.features.navigation.ItsTime4ServiceNavGraph
import ru.kuznetsov.itstime4service.domain.transfer.TransferCipher
import ru.kuznetsov.itstime4service.ui.transfer.*
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.*

/** Runs the real navigation, DI, presentation and SQLite with a synthetic native file boundary. */
@OptIn(ExperimentalTestApi::class)
class TransferNavigationTest : UiTest() {
    @Test fun existingDestinationWithSkippedMatchesContinuesAndAppliesFromSettings() {
        val app = TestApp()
        val files = FakeTransferFiles()
        app.koin.declare<TransferFiles>(files)
        app.koin.declare<TransferCipher>(transferFileCheckCipher())
        runTest { app.addCar("Synthetic car", tasks = listOf(NewMaintenanceTask("Oil", 8000))) }
        val texts = mutableMapOf<String, String>()
        try {
            runComposeUiTest {
                setContent {
                    texts["settings"] = stringResource(Res.string.tab_settings)
                    texts["transfer"] = stringResource(Res.string.transfer_title)
                    texts["import"] = stringResource(Res.string.transfer_import)
                    texts["pick"] = stringResource(Res.string.transfer_pick)
                    texts["skip"] = stringResource(Res.string.transfer_skip)
                    texts["continue"] = stringResource(Res.string.transfer_continue)
                    texts["apply"] = stringResource(Res.string.transfer_apply)
                    texts["done"] = stringResource(Res.string.action_done)
                    texts["success"] = stringResource(Res.string.transfer_imported)
                    KoinIsolatedContext(app.koinApp) {
                        ItsTime4ServiceTheme { ItsTime4ServiceNavGraph(null) }
                    }
                }
                onNodeWithText(texts.getValue("settings")).performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("transfer")), 10000)
                onNodeWithText(texts.getValue("transfer")).performScrollTo().performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("import")), 10000)
                onNodeWithText(texts.getValue("import")).performScrollTo().performClick()
                onNodeWithText(texts.getValue("pick")).performScrollTo().performClick()
                val destination = hasText("Synthetic car") and isSelectable()
                waitUntilAtLeastOneExists(destination, 10000)
                val next = onNodeWithText(texts.getValue("continue"))
                next.assertIsDisplayed().assertIsNotEnabled()
                onNode(destination).performScrollTo().performClick()
                onNode(hasText(texts.getValue("skip")) and isSelectable()).performScrollTo().assertIsSelected()
                next.assertIsDisplayed().performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("apply")), 10000)
                onNodeWithText(texts.getValue("apply")).performScrollTo().performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("success")), 10000)
                onNodeWithText(texts.getValue("done")).performScrollTo().performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("settings")), 10000)
                onNodeWithText(texts.getValue("apply")).assertDoesNotExist()
            }
            runTest {
                assertEquals("Synthetic car", app.summaries().single().brand)
                assertEquals(8000, app.car(app.summaries().single().id)!!.tasks.single().intervalKm)
            }
        } finally { app.close() }
    }

    @Test fun sharingFromACarOffersBothPurposesAndKeepsTheCarFixed() {
        val app = TestApp()
        app.koin.declare<TransferFiles>(FakeTransferFiles())
        app.koin.declare<TransferCipher>(transferFileCheckCipher())
        var id = ""
        runTest {
            app.addCar("Another navigation car")
            id = app.addCar("Synthetic navigation car")
        }
        val texts = mutableMapOf<String, String>()
        try {
            runComposeUiTest {
                setContent {
                    texts["share"] = stringResource(Res.string.transfer_share_action)
                    texts["export"] = stringResource(Res.string.transfer_export)
                    texts["backup"] = stringResource(Res.string.transfer_car_backup)
                    texts["schedule"] = stringResource(Res.string.transfer_share_schedule)
                    texts["protection"] = stringResource(Res.string.transfer_protection)
                    texts["back"] = stringResource(Res.string.action_back)
                    KoinIsolatedContext(app.koinApp) {
                        ItsTime4ServiceTheme { ItsTime4ServiceNavGraph(id) }
                    }
                }
                waitUntilAtLeastOneExists(hasContentDescription(texts.getValue("share")), 10000)
                onNodeWithContentDescription(texts.getValue("share")).performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("backup")), 10000)
                onNodeWithText(texts.getValue("export")).assertIsDisplayed()
                onNodeWithText(texts.getValue("schedule")).assertIsDisplayed()
                onNodeWithText(texts.getValue("backup")).performClick()
                onNodeWithText(texts.getValue("protection")).assertIsDisplayed()
                onNodeWithText("Synthetic navigation car").performScrollTo().assertIsDisplayed()
                onNodeWithText("Another navigation car").assertDoesNotExist()
                onNodeWithContentDescription(texts.getValue("back")).performClick()
                onNodeWithText(texts.getValue("backup")).assertIsDisplayed()
                onNodeWithContentDescription(texts.getValue("back")).performClick()
                waitUntilAtLeastOneExists(hasContentDescription(texts.getValue("share")), 10000)
                onNodeWithText(texts.getValue("backup")).assertDoesNotExist()
            }
        } finally { app.close() }
    }

    @Test fun importFromAddCarKeepsTheDraftOnCancelAndReturnsToCarsOnSuccess() {
        val app = TestApp()
        val files = FakeTransferFiles().apply { cancelPick = true }
        app.koin.declare<TransferFiles>(files)
        app.koin.declare<TransferCipher>(transferFileCheckCipher())
        val texts = mutableMapOf<String, String>()
        try {
            runComposeUiTest {
                setContent {
                    texts["addCar"] = stringResource(Res.string.action_add_car)
                    texts["form"] = stringResource(Res.string.screen_add_car)
                    texts["import"] = stringResource(Res.string.transfer_import)
                    texts["pick"] = stringResource(Res.string.transfer_pick)
                    texts["back"] = stringResource(Res.string.action_back)
                    texts["newTarget"] = stringResource(Res.string.transfer_new_car)
                    texts["continue"] = stringResource(Res.string.transfer_continue)
                    texts["apply"] = stringResource(Res.string.transfer_apply)
                    texts["done"] = stringResource(Res.string.action_done)
                    texts["success"] = stringResource(Res.string.transfer_imported)
                    KoinIsolatedContext(app.koinApp) {
                        ItsTime4ServiceTheme { ItsTime4ServiceNavGraph(null) }
                    }
                }
                waitUntilAtLeastOneExists(hasContentDescription(texts.getValue("addCar")), 10000)
                onNodeWithContentDescription(texts.getValue("addCar")).performClick()
                waitUntilAtLeastOneExists(hasSetTextAction(), 10000)
                onAllNodes(hasSetTextAction())[0].performTextInput("Draft car")
                onAllNodes(hasSetTextAction())[1].performTextInput("50000")
                onNodeWithText(texts.getValue("import")).performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("pick")), 10000)
                onNodeWithText(texts.getValue("pick")).performClick()
                onNodeWithText(texts.getValue("pick")).assertIsDisplayed()
                onNodeWithContentDescription(texts.getValue("back")).performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("form")), 10000)
                onAllNodes(hasSetTextAction())[0].assertTextContains("Draft car")
                onAllNodes(hasSetTextAction())[1].assertTextContains("50000")

                runOnIdle { files.cancelPick = false }
                onNodeWithText(texts.getValue("import")).performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("pick")), 10000)
                onNodeWithText(texts.getValue("pick")).performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("newTarget")), 10000)
                onAllNodes(hasSetTextAction())[1].performScrollTo().performTextInput("12000")
                onNodeWithText(texts.getValue("continue")).performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("apply")), 10000)
                onNodeWithText(texts.getValue("apply")).performScrollTo().performClick()
                waitUntilAtLeastOneExists(hasText(texts.getValue("success")), 10000)
                onNodeWithText(texts.getValue("done")).performScrollTo().performClick()
                waitUntilAtLeastOneExists(hasText("Synthetic car"), 10000)
                onNodeWithText(texts.getValue("form")).assertDoesNotExist()
            }
            runTest { assertEquals("Synthetic car", app.summaries().single().brand) }
        } finally { app.close() }
    }
}
