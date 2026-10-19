package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

/**
 * Keeps the notification plan in step with the data for as long as it runs: it never returns by itself and stops
 * when the calling scope is cancelled — for the app, the scope of the app's view model.
 * The plan is rebuilt by [SyncRemindersUseCase] on start, after every change of the cars, after a change of the
 * settings the plan depends on — push, reminder time and distance unit — and after every event of
 * `permissionGrants`, sent when the system permission for notifications is granted. Other settings, such as the theme
 * or the last opened car, leave the plan as it is. A rebuild still running when the next change comes is dropped.
 */
class KeepRemindersInSyncUseCase(
    private val observeCars: ObserveCarsUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val syncReminders: SyncRemindersUseCase,
) {
    suspend operator fun invoke(permissionGrants: Flow<Unit>) {
        val reminderSettings = observeSettings().distinctUntilChanged { old, new ->
            old.pushEnabled == new.pushEnabled &&
                old.reminderTime == new.reminderTime &&
                old.distanceUnit == new.distanceUnit
        }
        combine(observeCars().distinctUntilChanged(), reminderSettings, permissionGrants.onStart { emit(Unit) }) { cars, settings, _ ->
            cars to settings
        }.collectLatest { (cars, settings) -> syncReminders(cars, settings) }
    }
}
