package ru.kuznetsov.itstime4service.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.domain.usecase.DeleteCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SetDefaultCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateSettingsUseCase
import ru.kuznetsov.itstime4service.ui.format.kmFrom

/**
 * Drives the settings screen: every change is stored right away, there is no save button.
 * The reminder window is edited in thousands of the chosen unit and stored in kilometers.
 * The reminder time is picked in a dialog and stored when the dialog is confirmed.
 * Every change is applied to the settings as they are stored at that moment, so two quick changes cannot
 * overwrite each other.
 * "Open by default" and the chosen car are stored with the settings (see [SetDefaultCarUseCase]); the group is
 * shown only while there are cars. Deleting the chosen car picks another one (see [ForgetCarInSettingsUseCase]).
 * "Delete a car" removes the car after a confirmation.
 * Temporary until the storage supports it: sold cars are not listed and deleting a car here is the same as
 * deleting it from its screen.
 */
class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    observeCars: ObserveCarsUseCase,
    private val deleteCar: DeleteCarUseCase,
    private val setDefaultCar: SetDefaultCarUseCase,
) : ViewModel() {

    private val view = MutableStateFlow(View())

    val state: StateFlow<SettingsUiState> = combine(observeSettings(), observeCars(), view) { settings, cars, view ->
        val owners = cars.map { SettingsCar(id = it.id, brand = it.brand, mileageKm = it.mileageKm) }
        val defaultCarId = settings.defaultCarId?.takeIf { id -> owners.any { it.id == id } } ?: owners.firstOrNull()?.id
        SettingsUiState(
            settings = settings,
            isPickingReminderTime = view.isPickingReminderTime,
            cars = owners,
            openDefault = settings.openDefaultCar && owners.isNotEmpty(),
            defaultCarId = defaultCarId,
            purgeCar = owners.firstOrNull { it.id == view.purgeCarId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun onThemeSelect(themeMode: ThemeMode) = update { it.copy(themeMode = themeMode) }

    fun onUnitSelect(distanceUnit: DistanceUnit) = update { it.copy(distanceUnit = distanceUnit) }

    fun onPushToggle(enabled: Boolean) = update { it.copy(pushEnabled = enabled) }

    fun onReminderWindowChange(steps: Int) =
        update { it.copy(defaultReminderWindowKm = (steps * SettingsUiState.KM_PER_STEP).kmFrom(it.distanceUnit)) }

    fun onReminderTimeClick() = view.update { it.copy(isPickingReminderTime = true) }

    fun onReminderTimeDismiss() = view.update { it.copy(isPickingReminderTime = false) }

    fun onReminderTimeChange(time: LocalTime) {
        view.update { it.copy(isPickingReminderTime = false) }
        update { it.copy(reminderTime = time) }
    }

    fun onOpenDefaultToggle(enabled: Boolean) {
        viewModelScope.launch { setDefaultCar(enabled) }
    }

    fun onDefaultCarSelect(carId: String) {
        viewModelScope.launch { setDefaultCar(enabled = true, carId = carId) }
    }

    fun onPurgeRequest(carId: String) = view.update { it.copy(purgeCarId = carId) }

    fun onPurgeDismiss() = view.update { it.copy(purgeCarId = null) }

    fun onPurgeConfirm() {
        val carId = view.value.purgeCarId ?: return
        view.update { it.copy(purgeCarId = null) }
        viewModelScope.launch { deleteCar(carId) }
    }

    private fun update(change: (Settings) -> Settings) {
        viewModelScope.launch { updateSettings(change) }
    }

    /**
     * Screen-only state that is not stored anywhere: the open windows.
     */
    private data class View(
        val isPickingReminderTime: Boolean = false,
        val purgeCarId: String? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
