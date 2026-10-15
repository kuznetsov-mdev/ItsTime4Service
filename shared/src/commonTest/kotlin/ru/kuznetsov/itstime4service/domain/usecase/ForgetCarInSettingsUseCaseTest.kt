package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [ForgetCarInSettingsUseCase]: how a removed car is cleared from the last opened car and from
 * the "open by default" choice.
 */
class ForgetCarInSettingsUseCaseTest {

    private suspend fun forget(carId: String, remaining: List<String>, settings: FakeSettingsRepository) {
        ForgetCarInSettingsUseCase(settings, FakeCarRepository(remaining.map(::testCar)))(carId)
    }

    @Test
    fun removedLastOpenedCarIsCleared() = runTest {
        val settings = FakeSettingsRepository(defaultTestSettings.copy(lastOpenedCarId = "kia", defaultCarId = "camry"))

        forget("kia", listOf("camry"), settings)

        assertNull(settings.settings.lastOpenedCarId)
        assertEquals("camry", settings.settings.defaultCarId)
    }

    @Test
    fun removedChosenCarIsReplacedByLastOpened() = runTest {
        val settings = FakeSettingsRepository(
            defaultTestSettings.copy(lastOpenedCarId = "solaris", openDefaultCar = true, defaultCarId = "kia"),
        )

        forget("kia", listOf("camry", "solaris"), settings)

        assertEquals("solaris", settings.settings.defaultCarId)
        assertTrue(settings.settings.openDefaultCar)
    }

    @Test
    fun removedChosenAndLastOpenedCarIsReplacedByFirst() = runTest {
        val settings = FakeSettingsRepository(
            defaultTestSettings.copy(lastOpenedCarId = "kia", openDefaultCar = true, defaultCarId = "kia"),
        )

        forget("kia", listOf("camry", "solaris"), settings)

        assertNull(settings.settings.lastOpenedCarId)
        assertEquals("camry", settings.settings.defaultCarId)
        assertTrue(settings.settings.openDefaultCar)
    }

    @Test
    fun lastCarRemovedSwitchesDefaultOff() = runTest {
        val settings = FakeSettingsRepository(
            defaultTestSettings.copy(lastOpenedCarId = "kia", openDefaultCar = true, defaultCarId = "kia"),
        )

        forget("kia", emptyList(), settings)

        assertFalse(settings.settings.openDefaultCar)
        assertNull(settings.settings.defaultCarId)
        assertNull(settings.settings.lastOpenedCarId)
    }

    @Test
    fun otherCarRemovedKeepsTheChoice() = runTest {
        val settings = FakeSettingsRepository(
            defaultTestSettings.copy(lastOpenedCarId = "camry", openDefaultCar = true, defaultCarId = "solaris"),
        )

        forget("kia", listOf("camry", "solaris"), settings)

        assertEquals("camry", settings.settings.lastOpenedCarId)
        assertEquals("solaris", settings.settings.defaultCarId)
        assertTrue(settings.settings.openDefaultCar)
    }

    @Test
    fun launchOpensTheReplacementAfterRemoval() = runTest {
        val remaining = FakeCarRepository(listOf(testCar("camry")))
        val settings = FakeSettingsRepository(
            defaultTestSettings.copy(lastOpenedCarId = "kia", openDefaultCar = true, defaultCarId = "kia"),
        )

        ForgetCarInSettingsUseCase(settings, remaining)("kia")

        assertEquals("camry", GetLaunchCarUseCase(settings, remaining)())
    }
}
