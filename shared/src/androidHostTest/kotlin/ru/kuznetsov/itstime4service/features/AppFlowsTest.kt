package ru.kuznetsov.itstime4service.features

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add_car
import itstime4service.shared.generated.resources.action_add_entry
import itstime4service.shared.generated.resources.action_apply
import itstime4service.shared.generated.resources.action_back
import itstime4service.shared.generated.resources.action_delete
import itstime4service.shared.generated.resources.action_delete_car
import itstime4service.shared.generated.resources.action_done_unmark
import itstime4service.shared.generated.resources.action_edit_mileage
import itstime4service.shared.generated.resources.action_edit_name
import itstime4service.shared.generated.resources.action_filter
import itstime4service.shared.generated.resources.action_mark_done
import itstime4service.shared.generated.resources.action_ok
import itstime4service.shared.generated.resources.action_save
import itstime4service.shared.generated.resources.action_save_car
import itstime4service.shared.generated.resources.car_card_recent
import itstime4service.shared.generated.resources.cars_empty
import itstime4service.shared.generated.resources.cars_view_tasks_history
import itstime4service.shared.generated.resources.dialog_new_entry_title
import itstime4service.shared.generated.resources.history_nothing_found
import itstime4service.shared.generated.resources.history_sold_car
import itstime4service.shared.generated.resources.licenses_subtitle
import itstime4service.shared.generated.resources.screen_history
import itstime4service.shared.generated.resources.settings_licenses
import itstime4service.shared.generated.resources.tab_cars
import itstime4service.shared.generated.resources.tab_history
import itstime4service.shared.generated.resources.tab_settings
import itstime4service.shared.generated.resources.task_name_oil
import itstime4service.shared.generated.resources.units_mi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinIsolatedContext
import ru.kuznetsov.itstime4service.App
import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import ru.kuznetsov.itstime4service.ui.format.distanceText
import ru.kuznetsov.itstime4service.ui.format.grouped
import ru.kuznetsov.itstime4service.ui.test.UiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end flows of the whole app on Robolectric: the real navigation, view models, use cases and repositories on
 * a fresh SQLite database ([TestApp]), each flow on its own data. Covers adding a car with required-field errors,
 * the tabs and back, in-place edits on the car screen, editing a task, the done mark and its removal with the history
 * record, editing a record from the car's history, a filter that finds nothing, miles, the car opened at launch,
 * deleting a car while its history stays as sold, the car list view kept in settings with a record added by hand
 * from the car's history and saved with the done key, and the licences screen. Push is off, so the system permission
 * prompt never shows.
 */
@OptIn(ExperimentalTestApi::class)
class AppFlowsTest : UiTest() {

    private lateinit var app: TestApp
    private val systemLanguage = DateInputFormat.language
    private val texts = mutableMapOf<String, String>()

    private val strings = mapOf(
        "addCar" to Res.string.action_add_car,
        "saveCar" to Res.string.action_save_car,
        "save" to Res.string.action_save,
        "back" to Res.string.action_back,
        "editName" to Res.string.action_edit_name,
        "editMileage" to Res.string.action_edit_mileage,
        "markDone" to Res.string.action_mark_done,
        "unmark" to Res.string.action_done_unmark,
        "ok" to Res.string.action_ok,
        "deleteCar" to Res.string.action_delete_car,
        "delete" to Res.string.action_delete,
        "filter" to Res.string.action_filter,
        "apply" to Res.string.action_apply,
        "carsEmpty" to Res.string.cars_empty,
        "nothingFound" to Res.string.history_nothing_found,
        "historyTitle" to Res.string.screen_history,
        "licensesRow" to Res.string.settings_licenses,
        "licensesSubtitle" to Res.string.licenses_subtitle,
        "tabCars" to Res.string.tab_cars,
        "tabHistory" to Res.string.tab_history,
        "tabSettings" to Res.string.tab_settings,
        "oilTask" to Res.string.task_name_oil,
        "miles" to Res.string.units_mi,
        "viewHistory" to Res.string.cars_view_tasks_history,
        "addEntry" to Res.string.action_add_entry,
        "newEntryTitle" to Res.string.dialog_new_entry_title,
    )

    private val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    private fun hasClickLabel(label: String) = SemanticsMatcher("click label is $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun isTab(label: String) = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @BeforeTest
    fun setUp() {
        app = TestApp()
        DateInputFormat.language = { "ru" }
        db { app.changeSettings { it.copy(pushEnabled = false) } }
    }

    @AfterTest
    fun tearDown() {
        app.close()
        DateInputFormat.language = systemLanguage
    }

    private fun ComposeUiTest.startApp() = setContent {
        strings.forEach { (key, res: StringResource) -> texts[key] = stringResource(res) }
        texts["soldKia"] = stringResource(Res.string.history_sold_car, "Kia Rio")
        texts["milesKia"] = distanceText(84300, DistanceUnit.MILES)
        texts["recent"] = stringResource(Res.string.car_card_recent).uppercase()
        KoinIsolatedContext(context = app.koinApp) { App() }
    }

    private fun ComposeUiTest.waitFor(matcher: SemanticsMatcher) =
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun <T> db(block: suspend () -> T): T = runBlocking { withTimeout(TIMEOUT_MS) { block() } }

    private fun ComposeUiTest.waitForText(text: String) = waitFor(hasText(text))

    private fun ComposeUiTest.waitForEditClosed() =
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.text(key: String) = texts.getValue(key)

    private fun seedCar(brand: String = "Kia Rio", record: Boolean = false): String = db {
        val id = app.addCar(brand, mileageKm = 84300, tasks = listOf(NewMaintenanceTask("Масло", intervalKm = 10000, lastDoneKm = 80000)))
        if (record) app.addRecord(id, "Колодки", TestApp.TODAY, mileageKm = 80000)
        id
    }

    @Test
    fun carIsAddedAfterTheRequiredFieldsAreFilled() = runComposeUiTest {
        startApp()
        waitForText(text("carsEmpty"))

        onNodeWithContentDescription(text("addCar")).performClick()
        waitForText(text("saveCar"))
        onNodeWithText(text("saveCar")).performClick()
        onAllNodes(hasError).assertCountEquals(2)
        onAllNodes(hasSetTextAction())[0].performTextInput("Kia Rio")
        onAllNodes(hasSetTextAction())[1].performTextInput("84300")
        onNodeWithText(text("saveCar")).performClick()
        waitForText("Kia Rio")

        val car = db { app.summaries().single() }
        assertEquals("Kia Rio", car.brand)
        val stored = db { app.car(car.id) }!!
        assertEquals(listOf(text("oilTask")), stored.tasks.map { it.name })
    }

    @Test
    fun tabsSwitchScreensAndBackReturnsToTheList() = runComposeUiTest {
        seedCar()
        startApp()
        waitForText("Kia Rio")

        onNode(isTab(text("tabHistory"))).performClick()
        waitForText(text("historyTitle"))
        onNode(isTab(text("tabSettings"))).performClick()
        waitForText(text("licensesRow"))
        onNode(isTab(text("tabCars"))).performClick()
        waitForText("Kia Rio")
        onNodeWithText("Kia Rio").performClick()
        waitFor(hasContentDescription(text("back")))
        onNodeWithContentDescription(text("back")).performClick()
        waitFor(isTab(text("tabCars")))

        onNodeWithText("Kia Rio").assertExists()
    }

    @Test
    fun brandAndMileageAreEditedInPlace() = runComposeUiTest {
        val id = seedCar()
        startApp()
        waitForText("Kia Rio")
        onNodeWithText("Kia Rio").performClick()
        waitFor(hasClickLabel(text("editName")))

        onNode(hasClickLabel(text("editName"))).performClick()
        onNode(hasSetTextAction()).performTextReplacement("Kia Rio X")
        onNodeWithContentDescription(text("save")).performClick()
        waitForEditClosed()
        waitForText("Kia Rio X")
        onNode(hasClickLabel(text("editMileage"))).performClick()
        onNode(hasSetTextAction()).performTextReplacement("85000")
        onNodeWithContentDescription(text("save")).performClick()
        waitForEditClosed()
        waitForText(85000.grouped())

        val car = db { app.car(id) { it?.mileageKm == 85000 } }!!
        assertEquals("Kia Rio X", car.brand)
    }

    @Test
    fun taskIntervalIsEditedFromTheCarScreen() = runComposeUiTest {
        val id = seedCar()
        startApp()
        waitForText("Kia Rio")
        onNodeWithText("Kia Rio").performClick()
        waitForText("Масло")

        onNodeWithText("Масло").performClick()
        waitFor(hasText("10000") and hasSetTextAction())
        onNode(hasText("10000") and hasSetTextAction()).performTextReplacement("12000")
        onNodeWithText(text("save")).performClick()
        waitFor(hasClickLabel(text("editName")))

        val task = db { app.car(id) { car -> car?.tasks?.single()?.intervalKm == 12000 } }!!.tasks.single()
        assertEquals("Масло", task.name)
    }

    @Test
    fun doneMarkAddsARecordAndRemovingItDeletesTheRecord() = runComposeUiTest {
        seedCar()
        startApp()
        waitForText("Kia Rio")
        onNodeWithText("Kia Rio").performClick()
        waitFor(hasContentDescription(text("markDone")))

        onNodeWithContentDescription(text("markDone")).performScrollTo().performClick()
        waitForText(text("save"))
        onNodeWithText(text("save")).performClick()
        waitFor(hasContentDescription(text("unmark")))
        assertEquals(1, db { app.records { it.size == 1 } }.size)

        onNodeWithContentDescription(text("unmark")).performScrollTo().performClick()
        waitForText(text("ok"))
        onNodeWithText(text("ok")).performClick()
        waitFor(hasContentDescription(text("markDone")))

        assertTrue(db { app.records { it.isEmpty() } }.isEmpty())
    }

    @Test
    fun recordOpenedFromTheCarHistoryIsSaved() = runComposeUiTest {
        seedCar(record = true)
        startApp()
        waitForText("Kia Rio")
        onNodeWithText("Kia Rio").performClick()
        waitFor(hasContentDescription(text("historyTitle")))

        onNodeWithContentDescription(text("historyTitle")).performClick()
        waitForText("Колодки")
        onNodeWithText("Колодки").performClick()
        waitFor(hasText("80000") and hasSetTextAction())
        onNode(hasText("80000") and hasSetTextAction()).performTextReplacement("80100")
        onNodeWithText(text("save")).performClick()

        val record = db { app.records { it.singleOrNull()?.mileageKm == 80100 } }.single()
        assertEquals("Колодки", record.taskName)
    }

    @Test
    fun filterThatMatchesNothingSaysSo() = runComposeUiTest {
        seedCar(record = true)
        startApp()
        waitFor(isTab(text("tabHistory")))

        onNode(isTab(text("tabHistory"))).performClick()
        waitForText("Колодки")
        onNodeWithContentDescription(text("filter")).performClick()
        waitForText(text("apply"))
        onAllNodes(hasSetTextAction())[2].performTextInput("zzz")
        onNodeWithText(text("apply")).performClick()

        waitForText(text("nothingFound"))
        onAllNodesWithText("Колодки").assertCountEquals(0)
    }

    @Test
    fun milesChosenInSettingsAreShownInTheList() = runComposeUiTest {
        seedCar()
        startApp()
        waitFor(isTab(text("tabSettings")))

        onNode(isTab(text("tabSettings"))).performClick()
        waitForText(text("miles"))
        onNodeWithText(text("miles")).performClick()
        onNode(isTab(text("tabCars"))).performClick()

        waitForText(text("milesKia"))
        assertEquals(DistanceUnit.MILES, db { app.settings { it.distanceUnit == DistanceUnit.MILES } }.distanceUnit)
    }

    @Test
    fun carChosenToOpenByDefaultOpensAtLaunchOverTheList() = runComposeUiTest {
        val id = seedCar()
        db { app.changeSettings { it.copy(openDefaultCar = true, defaultCarId = id) } }
        startApp()

        waitFor(hasClickLabel(text("editName")))
        onNodeWithContentDescription(text("back")).performClick()
        waitFor(isTab(text("tabCars")))

        onNodeWithText("Kia Rio").assertExists()
    }

    @Test
    fun deletedCarLeavesItsHistoryAsSold() = runComposeUiTest {
        seedCar(record = true)
        startApp()
        waitForText("Kia Rio")
        onNodeWithText("Kia Rio").performClick()
        waitFor(hasContentDescription(text("deleteCar")))

        onNodeWithContentDescription(text("deleteCar")).performClick()
        waitForText(text("delete"))
        onNodeWithText(text("delete")).performClick()
        waitForText(text("carsEmpty"))
        onNode(isTab(text("tabHistory"))).performClick()

        waitForText(text("soldKia"))
        assertTrue(db { app.summaries() }.single().isSold)
    }

    @Test
    fun carViewIsStoredAndARecordAddedByHandBelongsToTheCar() = runComposeUiTest {
        val id = seedCar()
        startApp()
        waitForText("Kia Rio")

        onNodeWithContentDescription(text("viewHistory")).performClick()
        waitForText(text("recent"))
        assertEquals(CarsView.TASKS_AND_HISTORY, db { app.settings { it.carsView == CarsView.TASKS_AND_HISTORY } }.carsView)
        onNodeWithText(text("recent")).performClick()
        waitFor(hasContentDescription(text("addEntry")))
        onNodeWithContentDescription(text("addEntry")).performClick()
        waitForText(text("newEntryTitle"))
        onAllNodes(hasSetTextAction())[0].performTextInput("Колодки")
        onAllNodes(hasSetTextAction())[1].performTextInput("01102026")
        onAllNodes(hasSetTextAction())[2].performTextInput("84000")
        onAllNodes(hasSetTextAction())[2].performImeAction()
        waitForEditClosed()

        val record = db { app.records { it.size == 1 } }.single()
        assertEquals(id, record.carId)
        assertEquals(LocalDate(2026, 10, 1), record.date)
        assertEquals(84000, record.mileageKm)
        assertEquals(80000, db { app.car(id) }!!.tasks.single().lastDoneKm)
    }

    @Test
    fun typingKeyByKeyInARecordWindowKeepsTheOrder() = runComposeUiTest {
        seedCar()
        startApp()
        waitFor(isTab(text("tabHistory")))
        onNode(isTab(text("tabHistory"))).performClick()
        waitFor(hasContentDescription(text("addEntry")))
        onNodeWithContentDescription(text("addEntry")).performClick()
        waitForText(text("newEntryTitle"))

        "Колодки".forEach { key ->
            onAllNodes(hasSetTextAction())[0].performTextInput(key.toString())
            waitForIdle()
        }

        val typed = onAllNodes(hasSetTextAction())[0].fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text
        assertEquals("Колодки", typed)
    }

    @Test
    fun licencesOpenFromSettingsAndGoBack() = runComposeUiTest {
        startApp()
        waitFor(isTab(text("tabSettings")))

        onNode(isTab(text("tabSettings"))).performClick()
        waitForText(text("licensesRow"))
        onNodeWithText(text("licensesRow")).performScrollTo().performClick()
        waitForText(text("licensesSubtitle"))
        onNodeWithContentDescription(text("back")).performClick()

        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodesWithText(text("licensesSubtitle")).fetchSemanticsNodes().isEmpty() }
        waitFor(isTab(text("tabSettings")))
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
