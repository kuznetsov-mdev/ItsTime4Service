package ru.kuznetsov.itstime4service.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
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
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarSummariesUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.PurgeCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SetDefaultCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateSettingsUseCase
import ru.kuznetsov.itstime4service.ui.error.attempt
import ru.kuznetsov.itstime4service.ui.error.reloadable
import ru.kuznetsov.itstime4service.ui.format.kmFrom

/**
 * Drives the settings screen: every change is stored right away, there is no save button.
 * The reminder window is edited in thousands of the chosen unit and stored in kilometers.
 * The reminder time is picked in a dialog and stored when the dialog is confirmed.
 * Every change is applied to the settings as they are stored at that moment, so two quick changes cannot
 * overwrite each other.
 * "Open by default" and the chosen car are stored with the settings (see [SetDefaultCarUseCase]); the group is
 * shown only while there are cars. Deleting the chosen car picks another one (see [ForgetCarInSettingsUseCase]).
 * "Delete a car" lists every car, sold ones last, and deletes the chosen one completely — with its tasks and
 * its whole service history — after a confirmation (see [PurgeCarUseCase]).
 * A failed write shows the "could not save" bar until [onSaveFailureShown]; a failed read shows the load error until
 * [onRetry] reads the settings again.
 */
class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    observeCarSummaries: ObserveCarSummariesUseCase,
    private val purgeCar: PurgeCarUseCase,
    private val setDefaultCar: SetDefaultCarUseCase,
) : ViewModel() {

    private val view = MutableStateFlow(View())

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state: StateFlow<SettingsUiState> = reloadable(retries, SettingsUiState(loadFailed = true)) {
        combine(observeSettings(), observeCarSummaries(), view) { settings, cars, view ->
            val allCars = cars.map { SettingsCar(id = it.id, brand = it.brand, mileageKm = it.mileageKm, isSold = it.isSold) }
            val owners = allCars.filterNot { it.isSold }
            val defaultCarId = settings.defaultCarId?.takeIf { id -> owners.any { it.id == id } } ?: owners.firstOrNull()?.id
            SettingsUiState(
                settings = settings,
                isPickingReminderTime = view.isPickingReminderTime,
                cars = owners,
                openDefault = settings.openDefaultCar && owners.isNotEmpty(),
                defaultCarId = defaultCarId,
                purgeCars = allCars,
                purgeCar = allCars.firstOrNull { it.id == view.purgeCarId },
                saveFailed = view.saveFailed,
            )
        }
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

    fun onOpenDefaultToggle(enabled: Boolean) = save { setDefaultCar(enabled) }

    fun onDefaultCarSelect(carId: String) = save { setDefaultCar(enabled = true, carId = carId) }

    fun onPurgeRequest(carId: String) = view.update { it.copy(purgeCarId = carId) }

    fun onPurgeDismiss() = view.update { it.copy(purgeCarId = null) }

    fun onPurgeConfirm() {
        val carId = view.value.purgeCarId ?: return
        view.update { it.copy(purgeCarId = null) }
        save { purgeCar(carId) }
    }

    fun onSaveFailureShown() = view.update { it.copy(saveFailed = false) }

    fun onRetry() {
        retries.tryEmit(Unit)
    }

    private fun update(change: (Settings) -> Settings) = save { updateSettings(change) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { attempt(block).onFailure { view.update { it.copy(saveFailed = true) } } }
    }

    /**
     * Screen-only state that is not stored anywhere: the open windows and whether the last write failed.
     */
    private data class View(
        val isPickingReminderTime: Boolean = false,
        val purgeCarId: String? = null,
        val saveFailed: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
