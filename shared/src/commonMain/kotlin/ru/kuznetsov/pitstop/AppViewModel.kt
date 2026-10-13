package ru.kuznetsov.pitstop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.kuznetsov.pitstop.domain.model.ThemeMode
import ru.kuznetsov.pitstop.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.pitstop.domain.usecase.SyncRemindersUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateSettingsUseCase

/**
 * App-wide state above the navigation graph.
 * Exposes the theme chosen in settings, so switching it there repaints every screen at once.
 * Keeps the notification plan in step with the data: it is rebuilt on start and after every change
 * of cars or settings. [needsNotificationPermission] is `true` while push is switched on and there is
 * at least one car — the moment the app asks for the system permission; a refusal switches push off.
 */
class AppViewModel(
    private val observeSettings: ObserveSettingsUseCase,
    observeCars: ObserveCarsUseCase,
    private val syncReminders: SyncRemindersUseCase,
    private val updateSettings: UpdateSettingsUseCase,
) : ViewModel() {

    private val permissionGrants = MutableStateFlow(0)

    val themeMode: StateFlow<ThemeMode> = observeSettings()
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThemeMode.SYSTEM)

    val needsNotificationPermission: StateFlow<Boolean> =
        combine(observeCars(), observeSettings()) { cars, settings -> settings.pushEnabled && cars.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    init {
        viewModelScope.launch {
            combine(observeCars(), observeSettings(), permissionGrants) { cars, settings, _ -> cars to settings }
                .collectLatest { (cars, settings) -> syncReminders(cars, settings) }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            permissionGrants.update { it + 1 }
            return
        }
        viewModelScope.launch { updateSettings(observeSettings().first().copy(pushEnabled = false)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
