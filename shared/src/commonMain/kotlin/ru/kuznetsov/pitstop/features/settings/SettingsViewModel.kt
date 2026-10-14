package ru.kuznetsov.pitstop.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalTime
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.Settings
import ru.kuznetsov.pitstop.domain.model.ThemeMode
import ru.kuznetsov.pitstop.domain.usecase.DeleteCarUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateSettingsUseCase
import ru.kuznetsov.pitstop.ui.format.kmFrom

/**
 * Drives the settings screen: every change is stored right away, there is no save button.
 * The reminder window is edited in thousands of the chosen unit and stored in kilometers.
 * The reminder time is picked in a dialog and stored when the dialog is confirmed.
 * Changes are applied one after another, each to the settings as they are stored at that moment,
 * so two quick changes cannot overwrite each other.
 * "Delete a car" removes the car after a confirmation. With no cars left, "open by default" turns off and
 * stays off when a car is added again;
 * when the chosen car disappears, the first car in the list takes its place.
 * Temporary until the storage supports it: the "open by default" choice lives only in this view model,
 * sold cars are not listed, and deleting a car here is the same as deleting it from its screen.
 */
class SettingsViewModel(
    private val observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val observeCars: ObserveCarsUseCase,
    private val deleteCar: DeleteCarUseCase,
) : ViewModel() {

    private val view = MutableStateFlow(View())
    private val updateMutex = Mutex()

    init {
        viewModelScope.launch {
            observeCars().collect { cars ->
                if (cars.isEmpty()) view.update { it.copy(openDefault = false, defaultCarId = null) }
            }
        }
    }

    val state: StateFlow<SettingsUiState> = combine(observeSettings(), observeCars(), view) { settings, cars, view ->
        val owners = cars.map { SettingsCar(id = it.id, brand = it.brand, mileageKm = it.mileageKm) }
        val defaultCarId = view.defaultCarId?.takeIf { id -> owners.any { it.id == id } } ?: owners.firstOrNull()?.id
        SettingsUiState(
            settings = settings,
            isPickingReminderTime = view.isPickingReminderTime,
            cars = owners,
            openDefault = view.openDefault && owners.isNotEmpty(),
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

    fun onOpenDefaultToggle(enabled: Boolean) = view.update {
        it.copy(openDefault = enabled, defaultCarId = it.defaultCarId ?: state.value.defaultCarId)
    }

    fun onDefaultCarSelect(carId: String) = view.update { it.copy(defaultCarId = carId) }

    fun onPurgeRequest(carId: String) = view.update { it.copy(purgeCarId = carId) }

    fun onPurgeDismiss() = view.update { it.copy(purgeCarId = null) }

    fun onPurgeConfirm() {
        val carId = view.value.purgeCarId ?: return
        view.update { it.copy(purgeCarId = null) }
        viewModelScope.launch { deleteCar(carId) }
    }

    private fun update(change: (Settings) -> Settings) {
        viewModelScope.launch {
            updateMutex.withLock { updateSettings(change(observeSettings().first())) }
        }
    }

    /**
     * Screen-only state that is not stored anywhere yet: open windows and the "open by default" choice.
     */
    private data class View(
        val isPickingReminderTime: Boolean = false,
        val openDefault: Boolean = false,
        val defaultCarId: String? = null,
        val purgeCarId: String? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
