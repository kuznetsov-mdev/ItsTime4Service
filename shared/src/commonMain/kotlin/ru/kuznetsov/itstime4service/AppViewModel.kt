package ru.kuznetsov.itstime4service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.domain.usecase.GetLaunchCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SyncRemindersUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateSettingsUseCase

/**
 * App-wide state above the navigation graph.
 * Exposes the theme chosen in settings, so switching it there repaints every screen at once.
 * Keeps the notification plan in step with the data: it is rebuilt on start and after every change of the cars
 * or of the settings the plan depends on — push, reminder time and distance unit; other settings, such as
 * the theme or the last opened car, leave it as it is. [needsNotificationPermission] is `true` while push is
 * switched on and there is at least one car — the moment the app asks for the system permission; a refusal
 * switches push off.
 * [launch] tells once, on start, which car screen to open over the car list (see [GetLaunchCarUseCase]).
 */
class AppViewModel(
    private val observeSettings: ObserveSettingsUseCase,
    observeCars: ObserveCarsUseCase,
    private val syncReminders: SyncRemindersUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val getLaunchCar: GetLaunchCarUseCase,
) : ViewModel() {

    private val permissionGrants = MutableStateFlow(0)

    private val _launch = MutableStateFlow<Launch>(Launch.Pending)
    val launch: StateFlow<Launch> = _launch.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = observeSettings()
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThemeMode.SYSTEM)

    val needsNotificationPermission: StateFlow<Boolean> =
        combine(observeCars(), observeSettings()) { cars, settings -> settings.pushEnabled && cars.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    init {
        viewModelScope.launch { _launch.value = Launch.Ready(getLaunchCar()) }
        viewModelScope.launch {
            val reminderSettings = observeSettings().distinctUntilChanged { old, new ->
                old.pushEnabled == new.pushEnabled &&
                    old.reminderTime == new.reminderTime &&
                    old.distanceUnit == new.distanceUnit
            }
            combine(observeCars().distinctUntilChanged(), reminderSettings, permissionGrants) { cars, settings, _ -> cars to settings }
                .collectLatest { (cars, settings) -> syncReminders(cars, settings) }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            permissionGrants.update { it + 1 }
            return
        }
        viewModelScope.launch { updateSettings { it.copy(pushEnabled = false) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Where the app starts: [Pending] until the stored settings are read, then [Ready] with the car whose screen
 * opens over the car list, or `null` to stay on the list.
 */
sealed interface Launch {
    data object Pending : Launch

    data class Ready(val carId: String?) : Launch
}
