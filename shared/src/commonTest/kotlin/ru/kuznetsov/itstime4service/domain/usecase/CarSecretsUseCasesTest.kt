package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers [SetPlateHiddenUseCase] and [SetVinHiddenUseCase]: the choice is stored with the car, one value at a time.
 */
class CarSecretsUseCasesTest {

    private val repository = FakeCarRepository(listOf(testCar("kia"), testCar("camry")))

    @Test
    fun plateIsHiddenAndShownAgain() = runTest {
        SetPlateHiddenUseCase(repository)("kia", true)

        assertTrue(repository.cars.first { it.id == "kia" }.plateHidden)
        assertFalse(repository.cars.first { it.id == "kia" }.vinHidden)
        assertFalse(repository.cars.first { it.id == "camry" }.plateHidden)

        SetPlateHiddenUseCase(repository)("kia", false)

        assertFalse(repository.cars.first { it.id == "kia" }.plateHidden)
    }

    @Test
    fun vinIsHiddenOnItsOwn() = runTest {
        SetVinHiddenUseCase(repository)("kia", true)

        assertTrue(repository.cars.first { it.id == "kia" }.vinHidden)
        assertFalse(repository.cars.first { it.id == "kia" }.plateHidden)
    }
}
