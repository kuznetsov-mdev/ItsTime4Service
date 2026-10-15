package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.repository.CarRepository

/**
 * Deletes a car from its screen: its tasks go, the car stays as sold so that its service history is kept;
 * a car without history records is deleted completely. Then the car is cleared from the settings
 * (see [ForgetCarInSettingsUseCase]).
 */
class DeleteCarUseCase(
    private val carRepository: CarRepository,
    private val forgetCarInSettings: ForgetCarInSettingsUseCase,
) {
    suspend operator fun invoke(carId: String) {
        carRepository.sellCar(carId)
        forgetCarInSettings(carId)
    }
}
