package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [KeepRemindersInSyncUseCase]: which changes rebuild the notification plan — start, cars, push, reminder time,
 * distance unit and a granted permission — and which leave it as it is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeepRemindersInSyncUseCaseTest {

    private val cars = FakeCarRepository(listOf(testCar("kia")))
    private val settings = FakeSettingsRepository()
    private val reminders = FakeReminders()
    private val permissionGrants = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun TestScope.start() {
        val syncReminders = SyncRemindersUseCase(PlanRemindersUseCase(CalculateTaskStatusUseCase()), GetNowUseCase(), reminders, reminders)
        val keepInSync = KeepRemindersInSyncUseCase(ObserveCarsUseCase(cars), ObserveSettingsUseCase(settings), syncReminders)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { keepInSync(permissionGrants) }
    }

    @Test
    fun planIsBuiltOnStart() = runTest {
        start()

        assertEquals(1, reminders.schedules)
    }

    @Test
    fun changedCarsRebuildPlan() = runTest {
        start()

        cars.cars = listOf(testCar("kia"), testCar("camry"))

        assertEquals(2, reminders.schedules)
    }

    @Test
    fun settingsThePlanDependsOnRebuildIt() = runTest {
        start()

        settings.settings = settings.settings.copy(pushEnabled = false)
        settings.settings = settings.settings.copy(reminderTime = LocalTime(20, 0))
        settings.settings = settings.settings.copy(distanceUnit = DistanceUnit.MILES)

        assertEquals(4, reminders.schedules)
    }

    @Test
    fun otherSettingsLeavePlanAlone() = runTest {
        start()

        settings.settings = settings.settings.copy(themeMode = ThemeMode.DARK, lastOpenedCarId = "kia")

        assertEquals(1, reminders.schedules)
    }

    @Test
    fun grantedPermissionRebuildsPlan() = runTest {
        start()

        permissionGrants.emit(Unit)

        assertEquals(2, reminders.schedules)
    }
}
