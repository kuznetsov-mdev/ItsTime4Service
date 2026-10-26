package ru.kuznetsov.itstime4service.features.settings

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_decrease
import itstime4service.shared.generated.resources.action_delete
import itstime4service.shared.generated.resources.action_delete_car_with_history
import itstime4service.shared.generated.resources.action_increase
import itstime4service.shared.generated.resources.action_save
import itstime4service.shared.generated.resources.car_sold
import itstime4service.shared.generated.resources.dialog_purge_title
import itstime4service.shared.generated.resources.reminder_time_dialog_text
import itstime4service.shared.generated.resources.retry
import itstime4service.shared.generated.resources.settings_delete_cars
import itstime4service.shared.generated.resources.settings_launch
import itstime4service.shared.generated.resources.settings_licenses
import itstime4service.shared.generated.resources.settings_reminder_time
import itstime4service.shared.generated.resources.settings_theme
import itstime4service.shared.generated.resources.theme_dark
import itstime4service.shared.generated.resources.units_mi
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the settings screen from a given state: the theme and unit choice, the push switch, the "remind ahead"
 * stepper and its bounds, the reminder time row and window, the "open by default" switch and car list, the full
 * deletion list with sold cars and its confirmation, the licences row, and the loading and load error states.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest : UiTest() {

    private val calls = mutableListOf<String>()
    private val texts = mutableMapOf<String, String>()

    private val kia = SettingsCar("kia", "Kia Rio", 84300)
    private val bmw = SettingsCar("bmw", "BMW X1", 12000)
    private val soldLada = SettingsCar("lada", "Lada Vesta", 150000, isSold = true)

    private val settings = Settings(
        themeMode = ThemeMode.SYSTEM,
        distanceUnit = DistanceUnit.KILOMETERS,
        pushEnabled = true,
        defaultReminderWindowKm = 3000,
        reminderTime = LocalTime(9, 0),
    )

    private fun ComposeUiTest.show(state: SettingsUiState) = setContent {
        texts["dark"] = stringResource(Res.string.theme_dark)
        texts["miles"] = stringResource(Res.string.units_mi)
        texts["theme"] = stringResource(Res.string.settings_theme).uppercase()
        texts["launch"] = stringResource(Res.string.settings_launch).uppercase()
        texts["deleteCars"] = stringResource(Res.string.settings_delete_cars).uppercase()
        texts["decrease"] = stringResource(Res.string.action_decrease)
        texts["increase"] = stringResource(Res.string.action_increase)
        texts["reminderTime"] = stringResource(Res.string.settings_reminder_time)
        texts["timeDialogText"] = stringResource(Res.string.reminder_time_dialog_text)
        texts["licenses"] = stringResource(Res.string.settings_licenses)
        texts["sold"] = stringResource(Res.string.car_sold)
        texts["purgeKia"] = stringResource(Res.string.action_delete_car_with_history, "Kia Rio")
        texts["purgeTitle"] = stringResource(Res.string.dialog_purge_title, "Kia Rio")
        texts["delete"] = stringResource(Res.string.action_delete)
        texts["cancel"] = stringResource(Res.string.action_cancel)
        texts["save"] = stringResource(Res.string.action_save)
        texts["retry"] = stringResource(Res.string.retry)
        ItsTime4ServiceTheme(darkTheme = false) {
            SettingsContent(
                state = state,
                onThemeSelect = { calls += "theme $it" },
                onUnitSelect = { calls += "unit $it" },
                onPushToggle = { calls += "push $it" },
                onReminderWindowChange = { calls += "window $it" },
                onReminderTimeClick = { calls += "timeClick" },
                onReminderTimeDismiss = { calls += "timeDismiss" },
                onReminderTimeChange = { calls += "time $it" },
                onOpenDefaultToggle = { calls += "openDefault $it" },
                onDefaultCarSelect = { calls += "default $it" },
                onPurgeRequest = { calls += "purge $it" },
                onPurgeDismiss = { calls += "purgeDismiss" },
                onPurgeConfirm = { calls += "purgeConfirm" },
                onOpenLicenses = { calls += "licenses" },
                onRetry = { calls += "retry" },
            )
        }
    }

    private fun loaded(settings: Settings = this.settings) = SettingsUiState(settings = settings)

    @Test
    fun themeAndUnitChoicesReachTheViewModel() = runComposeUiTest {
        show(loaded())

        onNodeWithText(texts.getValue("dark")).performClick()
        onNodeWithText(texts.getValue("miles")).performClick()

        assertEquals(listOf("theme DARK", "unit MILES"), calls)
    }

    @Test
    fun pushSwitchShowsAndReportsItsState() = runComposeUiTest {
        show(loaded())

        onNode(isToggleable()).performScrollTo().assertIsOn().performClick()

        assertEquals(listOf("push false"), calls)
    }

    @Test
    fun withoutCarsTheLaunchAndDeletionGroupsAreHidden() = runComposeUiTest {
        show(loaded())

        onNodeWithText(texts.getValue("theme")).assertExists()
        onAllNodesWithText(texts.getValue("launch")).assertCountEquals(0)
        onAllNodesWithText(texts.getValue("deleteCars")).assertCountEquals(0)
    }

    @Test
    fun openByDefaultListsTheCarsAndPicksOne() = runComposeUiTest {
        show(loaded().copy(cars = listOf(kia, bmw), openDefault = true, defaultCarId = "kia"))

        onNodeWithText("Kia Rio").assertIsSelected()
        onNodeWithText("BMW X1").performScrollTo().assertIsNotSelected().performClick()
        onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()

        assertEquals(listOf("default bmw", "openDefault false"), calls)
    }

    @Test
    fun openByDefaultOffHidesTheCarList() = runComposeUiTest {
        show(loaded().copy(cars = listOf(kia, bmw)))

        onNodeWithText(texts.getValue("launch")).assertExists()
        onAllNodesWithText("Kia Rio").assertCountEquals(0)
        onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()

        assertEquals(listOf("openDefault true"), calls)
    }

    @Test
    fun remindAheadStepsByOneThousand() = runComposeUiTest {
        show(loaded())

        onNodeWithText("3").performScrollTo().assertExists()
        onNodeWithContentDescription(texts.getValue("increase")).performClick()
        onNodeWithContentDescription(texts.getValue("decrease")).performClick()

        assertEquals(listOf("window 4", "window 2"), calls)
    }

    @Test
    fun remindAheadStopsAtItsBounds() = runComposeUiTest {
        show(loaded(settings.copy(defaultReminderWindowKm = 9000)))

        onNodeWithContentDescription(texts.getValue("increase")).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun reminderTimeRowShowsTheTimeAndOpensTheWindow() = runComposeUiTest {
        show(loaded())

        onNodeWithText("09:00").performScrollTo().performClick()

        assertEquals(listOf("timeClick"), calls)
    }

    @Test
    fun reminderTimeWindowSavesAndCancels() = runComposeUiTest {
        show(loaded().copy(isPickingReminderTime = true))

        onNodeWithText(texts.getValue("timeDialogText")).assertExists()
        onNodeWithText(texts.getValue("save")).performClick()
        onNodeWithText(texts.getValue("cancel")).performClick()

        assertEquals(listOf("time 09:00", "timeDismiss"), calls)
    }

    @Test
    fun deletionListMarksSoldCarsAndAsksToDelete() = runComposeUiTest {
        show(loaded().copy(purgeCars = listOf(kia, soldLada)))

        onNodeWithText(texts.getValue("sold")).performScrollTo().assertExists()
        onNodeWithContentDescription(texts.getValue("purgeKia")).performScrollTo().performClick()

        assertEquals(listOf("purge kia"), calls)
    }

    @Test
    fun deletionWindowNamesTheCarAndReportsTheChoice() = runComposeUiTest {
        show(loaded().copy(purgeCars = listOf(kia), purgeCar = kia))

        onNodeWithText(texts.getValue("purgeTitle")).assertExists()
        onNodeWithText(texts.getValue("delete")).performClick()
        onNodeWithText(texts.getValue("cancel")).performClick()

        assertEquals(listOf("purgeConfirm", "purgeDismiss"), calls)
    }

    @Test
    fun licencesRowOpensTheLicences() = runComposeUiTest {
        show(loaded())

        onNodeWithText(texts.getValue("licenses")).performScrollTo().performClick()

        assertEquals(listOf("licenses"), calls)
    }

    @Test
    fun loadingShowsNoSettings() = runComposeUiTest {
        show(SettingsUiState())

        onAllNodesWithText(texts.getValue("theme")).assertCountEquals(0)
    }

    @Test
    fun loadErrorReplacesTheSettingsAndRetries() = runComposeUiTest {
        show(SettingsUiState(loadFailed = true))

        onNodeWithText(texts.getValue("retry")).performClick()

        assertEquals(listOf("retry"), calls)
        onAllNodesWithText(texts.getValue("theme")).assertCountEquals(0)
    }
}
