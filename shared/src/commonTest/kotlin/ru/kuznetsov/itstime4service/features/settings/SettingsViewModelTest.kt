package ru.kuznetsov.itstime4service.features.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.features.TestApp
import ru.kuznetsov.itstime4service.features.ViewModelTestSetup
import ru.kuznetsov.itstime4service.features.watch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [SettingsViewModel] on the app's own graph and a database in memory: changes stored right away, the reminder
 * window in thousands of the chosen unit, the reminder time dialog, "open by default" and deleting a car completely.
 */
class SettingsViewModelTest {

    private val app = TestApp()

    @BeforeTest
    fun setUp() = ViewModelTestSetup.start()

    @AfterTest
    fun tearDown() {
        app.close()
        ViewModelTestSetup.finish()
    }

    private suspend fun TestScope.open(): SettingsViewModel {
        val viewModel = app.viewModel<SettingsViewModel>()
        watch(viewModel.state) { it.settings != null }
        return viewModel
    }

    private suspend fun SettingsViewModel.until(predicate: (SettingsUiState) -> Boolean) = state.first(predicate)

    @Test
    fun quickChangesAreAllStored() = runTest {
        val viewModel = open()

        viewModel.onThemeSelect(ThemeMode.DARK)
        viewModel.onUnitSelect(DistanceUnit.MILES)
        viewModel.onPushToggle(false)

        val settings = app.settings { it.themeMode == ThemeMode.DARK && it.distanceUnit == DistanceUnit.MILES && !it.pushEnabled }
        assertEquals(settings, viewModel.until { it.settings == settings }.settings)
    }

    @Test
    fun reminderWindowIsEditedInThousandsOfTheChosenUnit() = runTest {
        app.changeSettings { it.copy(distanceUnit = DistanceUnit.MILES) }
        val viewModel = open()

        viewModel.onReminderWindowChange(3)

        val state = viewModel.until { it.settings?.defaultReminderWindowKm == 4_828 }
        assertEquals(3, state.reminderSteps)
    }

    @Test
    fun reminderTimeIsStoredWhenTheDialogIsConfirmed() = runTest {
        val viewModel = open()

        viewModel.onReminderTimeClick()
        assertTrue(viewModel.state.value.isPickingReminderTime)
        viewModel.onReminderTimeDismiss()
        assertFalse(viewModel.state.value.isPickingReminderTime)
        viewModel.onReminderTimeClick()
        viewModel.onReminderTimeChange(LocalTime(18, 30))

        assertFalse(viewModel.state.value.isPickingReminderTime)
        assertEquals(LocalTime(18, 30), app.settings { it.reminderTime != LocalTime(9, 0) }.reminderTime)
    }

    @Test
    fun openByDefaultStartsOnTheLastOpenedCarAndKeepsTheChoiceWhenOff() = runTest {
        val kia = app.addCar("Kia")
        val camry = app.addCar("Camry")
        app.changeSettings { it.copy(lastOpenedCarId = camry) }
        val viewModel = open()

        viewModel.onOpenDefaultToggle(true)
        assertEquals(camry, viewModel.until { it.openDefault }.defaultCarId)

        viewModel.onDefaultCarSelect(kia)
        viewModel.until { it.settings?.defaultCarId == kia }

        viewModel.onOpenDefaultToggle(false)
        val state = viewModel.until { !it.openDefault }
        assertEquals(kia, state.defaultCarId)
        assertEquals(kia, state.settings?.defaultCarId)
    }

    @Test
    fun openByDefaultIsOffWithoutCars() = runTest {
        app.changeSettings { it.copy(openDefaultCar = true) }

        val state = open().state.value

        assertFalse(state.openDefault)
        assertNull(state.defaultCarId)
        assertTrue(state.cars.isEmpty())
    }

    @Test
    fun soldCarsCanOnlyBeDeletedCompletely() = runTest {
        val sold = app.addCar("Lada")
        app.addRecord(sold, "Масло", LocalDate(2024, 6, 1))
        app.taskHistory.sellCar(sold)
        val kia = app.addCar("Kia")

        val state = open().until { it.purgeCars.size == 2 }

        assertEquals(listOf(kia), state.cars.map { it.id })
        assertEquals(listOf(kia, sold), state.purgeCars.map { it.id })
        assertTrue(state.purgeCars.last().isSold)
        assertEquals(kia, state.defaultCarId)
    }

    @Test
    fun confirmedPurgeDeletesTheCarWithItsHistory() = runTest {
        val kia = app.addCar("Kia")
        app.addRecord(kia, "Масло", LocalDate(2026, 3, 1))
        val viewModel = open()
        viewModel.until { it.purgeCars.isNotEmpty() }

        viewModel.onPurgeRequest(kia)
        assertEquals(kia, viewModel.state.value.purgeCar?.id)
        viewModel.onPurgeDismiss()
        assertNull(viewModel.state.value.purgeCar)
        viewModel.onPurgeRequest(kia)
        viewModel.onPurgeConfirm()
        assertNull(viewModel.state.value.purgeCar)

        viewModel.until { it.purgeCars.isEmpty() }
        app.records { it.isEmpty() }
        assertTrue(app.summaries().isEmpty())
    }
}
