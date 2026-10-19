package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [UpdateSettingsUseCase]: a change is applied to the stored settings in one write, and the default reminder
 * window is kept within the stepper's 1..9 thousand of the chosen unit.
 */
class UpdateSettingsUseCaseTest {

    private suspend fun stored(windowKm: Int, unit: DistanceUnit = DistanceUnit.KILOMETERS): Int {
        val repository = FakeSettingsRepository()
        UpdateSettingsUseCase(repository).invoke { it.copy(distanceUnit = unit, defaultReminderWindowKm = windowKm) }
        return repository.settings.defaultReminderWindowKm
    }

    @Test
    fun changeIsAppliedInOneWrite() = runTest {
        val repository = FakeSettingsRepository()

        UpdateSettingsUseCase(repository).invoke { it.copy(themeMode = ThemeMode.DARK) }

        assertEquals(ThemeMode.DARK, repository.settings.themeMode)
        assertEquals(defaultTestSettings.defaultReminderWindowKm, repository.settings.defaultReminderWindowKm)
        assertEquals(1, repository.writes)
    }

    @Test
    fun windowInKilometersIsKeptWithinStepper() = runTest {
        assertEquals(1_000, stored(500))
        assertEquals(4_000, stored(4_000))
        assertEquals(9_000, stored(12_000))
    }

    @Test
    fun windowInMilesIsKeptWithinStepper() = runTest {
        assertEquals(1_609, stored(1_000, DistanceUnit.MILES))
        assertEquals(8_000, stored(8_000, DistanceUnit.MILES))
        assertEquals(14_484, stored(20_000, DistanceUnit.MILES))
    }

    @Test
    fun switchingUnitPullsWindowIntoNewRange() = runTest {
        val miles = defaultTestSettings.copy(distanceUnit = DistanceUnit.MILES, defaultReminderWindowKm = 14_484)
        val repository = FakeSettingsRepository(miles)

        UpdateSettingsUseCase(repository).invoke { it.copy(distanceUnit = DistanceUnit.KILOMETERS) }

        assertEquals(9_000, repository.settings.defaultReminderWindowKm)
    }
}
