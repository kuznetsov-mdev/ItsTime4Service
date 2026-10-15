package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the start-up car rules: which car [GetLaunchCarUseCase] opens, how [SetDefaultCarUseCase] picks
 * the chosen car and when [RecordCarOpenedUseCase] writes.
 */
class LaunchCarUseCasesTest {

    private val cars = FakeCarRepository(listOf(testCar("kia"), testCar("camry"), testCar("solaris")))

    @Test
    fun lastOpenedCarOpensWhileDefaultIsOff() = runTest {
        val settings = FakeSettingsRepository(defaultTestSettings.copy(lastOpenedCarId = "camry", defaultCarId = "kia"))

        assertEquals("camry", GetLaunchCarUseCase(settings, cars)())
    }

    @Test
    fun chosenCarOpensWhileDefaultIsOn() = runTest {
        val settings = FakeSettingsRepository(
            defaultTestSettings.copy(lastOpenedCarId = "camry", openDefaultCar = true, defaultCarId = "kia"),
        )

        assertEquals("kia", GetLaunchCarUseCase(settings, cars)())
    }

    @Test
    fun missingCarOpensTheList() = runTest {
        val settings = FakeSettingsRepository(defaultTestSettings.copy(lastOpenedCarId = "sold"))

        assertNull(GetLaunchCarUseCase(settings, cars)())
    }

    @Test
    fun nothingOpenedYetOpensTheList() = runTest {
        assertNull(GetLaunchCarUseCase(FakeSettingsRepository(), cars)())
    }

    @Test
    fun switchingOnPicksTheLastOpenedCar() = runTest {
        val settings = FakeSettingsRepository(defaultTestSettings.copy(lastOpenedCarId = "camry"))

        SetDefaultCarUseCase(settings, cars)(enabled = true)

        assertTrue(settings.settings.openDefaultCar)
        assertEquals("camry", settings.settings.defaultCarId)
    }

    @Test
    fun switchingOnWithoutLastOpenedPicksTheFirstCar() = runTest {
        val settings = FakeSettingsRepository()

        SetDefaultCarUseCase(settings, cars)(enabled = true)

        assertEquals("kia", settings.settings.defaultCarId)
    }

    @Test
    fun switchingOnKeepsAnExistingChoice() = runTest {
        val settings = FakeSettingsRepository(defaultTestSettings.copy(lastOpenedCarId = "camry", defaultCarId = "solaris"))

        SetDefaultCarUseCase(settings, cars)(enabled = true)

        assertEquals("solaris", settings.settings.defaultCarId)
    }

    @Test
    fun switchingOnReplacesAChoiceThatNoLongerExists() = runTest {
        val settings = FakeSettingsRepository(defaultTestSettings.copy(lastOpenedCarId = "camry", defaultCarId = "sold"))

        SetDefaultCarUseCase(settings, cars)(enabled = true)

        assertEquals("camry", settings.settings.defaultCarId)
    }

    @Test
    fun choosingACarStoresIt() = runTest {
        val settings = FakeSettingsRepository(defaultTestSettings.copy(openDefaultCar = true, defaultCarId = "kia"))

        SetDefaultCarUseCase(settings, cars)(enabled = true, carId = "solaris")

        assertEquals("solaris", settings.settings.defaultCarId)
    }

    @Test
    fun switchingOffKeepsTheChoice() = runTest {
        val settings = FakeSettingsRepository(defaultTestSettings.copy(openDefaultCar = true, defaultCarId = "solaris"))

        SetDefaultCarUseCase(settings, cars)(enabled = false)

        assertFalse(settings.settings.openDefaultCar)
        assertEquals("solaris", settings.settings.defaultCarId)
    }

    @Test
    fun openingACarRemembersIt() = runTest {
        val settings = FakeSettingsRepository()

        RecordCarOpenedUseCase(settings)("camry")

        assertEquals("camry", settings.settings.lastOpenedCarId)
    }

    @Test
    fun openingTheSameCarAgainWritesNothing() = runTest {
        val settings = FakeSettingsRepository(defaultTestSettings.copy(lastOpenedCarId = "camry"))

        RecordCarOpenedUseCase(settings)("camry")

        assertEquals(0, settings.writes)
    }
}
