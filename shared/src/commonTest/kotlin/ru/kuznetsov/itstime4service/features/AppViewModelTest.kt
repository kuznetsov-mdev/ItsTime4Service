package ru.kuznetsov.itstime4service.features

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import ru.kuznetsov.itstime4service.AppViewModel
import ru.kuznetsov.itstime4service.Launch
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [AppViewModel] on the app's own graph and a database in memory: the theme from settings, when the
 * notification permission is asked for and what its answer does, the notification plan kept in step with the data
 * and the car screen the app starts on.
 */
class AppViewModelTest {

    private val app = TestApp()

    @BeforeTest
    fun setUp() = ViewModelTestSetup.start()

    @AfterTest
    fun tearDown() {
        app.close()
        ViewModelTestSetup.finish()
    }

    @Test
    fun themeFollowsTheSettings() = runTest {
        val viewModel = app.viewModel<AppViewModel>()
        keep(viewModel.themeMode)

        app.changeSettings { it.copy(themeMode = ThemeMode.DARK) }

        viewModel.themeMode.first { it == ThemeMode.DARK }
    }

    @Test
    fun permissionIsNeededWhilePushIsOnAndThereIsACar() = runTest {
        val viewModel = app.viewModel<AppViewModel>()
        keep(viewModel.needsNotificationPermission)
        assertEquals(false, viewModel.needsNotificationPermission.value)

        app.addCar("Kia")
        viewModel.needsNotificationPermission.first { it }
        app.changeSettings { it.copy(pushEnabled = false) }
        viewModel.needsNotificationPermission.first { !it }
    }

    @Test
    fun refusedPermissionSwitchesPushOff() = runTest {
        val viewModel = app.viewModel<AppViewModel>()

        viewModel.onNotificationPermissionResult(false)

        app.settings { !it.pushEnabled }
    }

    @Test
    fun planIsRebuiltOnStartOnChangesAndOnAGrantedPermission() = runTest {
        val viewModel = app.viewModel<AppViewModel>()
        eventually { app.reminders.schedules >= 1 }

        app.changeSettings { it.copy(distanceUnit = DistanceUnit.MILES) }
        eventually { app.reminders.scheduledUnit == DistanceUnit.MILES }

        val beforeGrant = app.reminders.schedules
        viewModel.onNotificationPermissionResult(true)
        eventually { app.reminders.schedules > beforeGrant }

        val beforeTheme = app.reminders.schedules
        app.changeSettings { it.copy(themeMode = ThemeMode.DARK) }
        app.addCar("Kia")
        eventually { app.reminders.schedules > beforeTheme }
        assertEquals(beforeTheme + 1, app.reminders.schedules)
    }

    @Test
    fun appStartsOnTheLastOpenedCar() = runTest {
        val kia = app.addCar("Kia")
        app.changeSettings { it.copy(lastOpenedCarId = kia) }

        assertEquals(Launch.Ready(kia), app.viewModel<AppViewModel>().launch.first { it is Launch.Ready })
    }

    @Test
    fun appStartsOnTheChosenCarWhileOpenByDefaultIsOn() = runTest {
        val kia = app.addCar("Kia")
        val camry = app.addCar("Camry")
        app.changeSettings { it.copy(lastOpenedCarId = kia, openDefaultCar = true, defaultCarId = camry) }

        assertEquals(Launch.Ready(camry), app.viewModel<AppViewModel>().launch.first { it is Launch.Ready })
    }

    @Test
    fun appStartsOnTheListWhenTheCarIsGone() = runTest {
        app.changeSettings { it.copy(lastOpenedCarId = "sold") }

        assertEquals(Launch.Ready(null), app.viewModel<AppViewModel>().launch.first { it is Launch.Ready })
    }
}
