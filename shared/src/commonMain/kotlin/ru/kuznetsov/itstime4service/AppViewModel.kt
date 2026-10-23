package ru.kuznetsov.itstime4service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.domain.usecase.GetLaunchCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.KeepRemindersInSyncUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateSettingsUseCase
import ru.kuznetsov.itstime4service.ui.error.attempt

/**
 * App-wide state above the navigation graph.
 * Exposes the theme chosen in settings, so switching it there repaints every screen at once.
 * Runs [KeepRemindersInSyncUseCase] for as long as it lives and tells it about every granted notification permission.
 * [needsNotificationPermission] is `true` while push is switched on and there is at least one car — the moment the
 * app asks for the system permission; a refusal switches push off.
 * [launch] tells once, on start, which car screen to open over the car list (see [GetLaunchCarUseCase]).
 * A failed read of the database never stops the app: the theme falls back to the system one, the permission is not
 * asked for and the app starts on the car list.
 */
class AppViewModel(
    private val observeSettings: ObserveSettingsUseCase,
    observeCars: ObserveCarsUseCase,
    keepRemindersInSync: KeepRemindersInSyncUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val getLaunchCar: GetLaunchCarUseCase,
) : ViewModel() {

    private val permissionGrants = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _launch = MutableStateFlow<Launch>(Launch.Pending)
    val launch: StateFlow<Launch> = _launch.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = observeSettings()
        .map { it.themeMode }
        .catch { emit(ThemeMode.SYSTEM) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThemeMode.SYSTEM)

    val needsNotificationPermission: StateFlow<Boolean> =
        combine(observeCars(), observeSettings()) { cars, settings -> settings.pushEnabled && cars.isNotEmpty() }
            .catch { emit(false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    init {
        viewModelScope.launch { _launch.value = Launch.Ready(attempt { getLaunchCar() }.getOrNull()) }
        viewModelScope.launch { keepRemindersInSync(permissionGrants) }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            permissionGrants.tryEmit(Unit)
            return
        }
        viewModelScope.launch { attempt { updateSettings { it.copy(pushEnabled = false) } } }
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
