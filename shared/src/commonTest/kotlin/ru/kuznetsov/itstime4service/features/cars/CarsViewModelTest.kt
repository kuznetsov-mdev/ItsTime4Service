package ru.kuznetsov.itstime4service.features.cars

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.domain.usecase.CalculateTaskStatusUseCase
import ru.kuznetsov.itstime4service.domain.usecase.CarAlertsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.DetectPlateStyleUseCase
import ru.kuznetsov.itstime4service.domain.usecase.FakeCarRepository
import ru.kuznetsov.itstime4service.domain.usecase.FakeHistoryRepository
import ru.kuznetsov.itstime4service.domain.usecase.FakeSettingsRepository
import ru.kuznetsov.itstime4service.domain.usecase.GetTodayUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveAllHistoryUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SortedTaskStatsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.defaultTestSettings
import ru.kuznetsov.itstime4service.ui.components.indicators.PlateLook
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [CarsViewModel]: the row gauge, the card contents (most urgent tasks, latest history, plate),
 * the view and unit taken from settings and storing the chosen view.
 * Tasks have no time interval, so their status depends on the mileage only and not on today's date.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CarsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun task(id: String, remainingKm: Int) = MaintenanceTask(
        id = id,
        name = id,
        intervalKm = INTERVAL_KM,
        lastDoneKm = MILEAGE_KM + remainingKm - INTERVAL_KM,
        reminderWindowKm = WINDOW_KM,
    )

    private fun car(id: String, tasks: List<MaintenanceTask> = emptyList(), plate: String? = null) =
        Car(id = id, brand = id, mileageKm = MILEAGE_KM, vin = null, tasks = tasks, plate = plate)

    private fun record(carId: String, taskName: String, date: LocalDate) =
        ServiceHistoryEntry(id = "$carId-$taskName", carId = carId, taskName = taskName, date = date, mileageKm = 90_000)

    private fun viewModel(
        cars: List<Car>,
        history: List<ServiceHistoryEntry> = emptyList(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): CarsViewModel {
        val carRepository = FakeCarRepository(cars)
        val calculateTaskStatus = CalculateTaskStatusUseCase()
        return CarsViewModel(
            observeCars = ObserveCarsUseCase(carRepository),
            observeAllHistory = ObserveAllHistoryUseCase(FakeHistoryRepository(history), carRepository),
            observeSettings = ObserveSettingsUseCase(settings),
            updateSettings = UpdateSettingsUseCase(settings),
            carAlerts = CarAlertsUseCase(calculateTaskStatus),
            sortedTaskStats = SortedTaskStatsUseCase(calculateTaskStatus),
            detectPlateStyle = DetectPlateStyleUseCase(),
            getToday = GetTodayUseCase(),
        )
    }

    private fun TestScope.loaded(viewModel: CarsViewModel): CarsUiState {
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel.state.value
    }

    private fun CarsUiState.car(id: String): CarItem = cars.first { it.id == id }

    @Test
    fun stateIsLoadingBeforeFirstList() {
        val state = viewModel(cars = listOf(car("kia"))).state.value

        assertTrue(state.isLoading)
        assertTrue(state.cars.isEmpty())
    }

    @Test
    fun carsAreListedInRepositoryOrder() = runTest {
        val state = loaded(viewModel(cars = listOf(car("kia"), car("camry"))))

        assertEquals(false, state.isLoading)
        assertEquals(listOf("kia", "camry"), state.cars.map { it.id })
        assertEquals(MILEAGE_KM, state.car("kia").mileageKm)
    }

    @Test
    fun gaugeAndCountsSummarizeSoonAndDueTasks() = runTest {
        val kia = car("kia", listOf(task("oil", -300), task("filter", 200), task("plugs", 700), task("belt", 5_000)))

        val item = loaded(viewModel(cars = listOf(kia))).car("kia")

        assertEquals(1, item.dueCount)
        assertEquals(2, item.soonCount)
        assertEquals(4, item.taskCount)
        assertEquals(3, item.alertCount)
        assertEquals(0.75f, item.gaugeValue)
        assertEquals(TaskStatus.DUE, item.status)
    }

    @Test
    fun carWithNothingToAttendGetsSliverOfGauge() = runTest {
        val kia = car("kia", listOf(task("oil", 5_000)))

        val item = loaded(viewModel(cars = listOf(kia))).car("kia")

        assertEquals(0, item.alertCount)
        assertEquals(0.06f, item.gaugeValue)
        assertEquals(TaskStatus.OK, item.status)
    }

    @Test
    fun carWithoutTasksHasEmptyCard() = runTest {
        val item = loaded(viewModel(cars = listOf(car("kia")))).car("kia")

        assertEquals(0, item.taskCount)
        assertEquals(0.06f, item.gaugeValue)
        assertTrue(item.upcomingTasks.isEmpty())
        assertTrue(item.recentRecords.isEmpty())
    }

    @Test
    fun cardShowsFourMostUrgentTasks() = runTest {
        val kia = car(
            "kia",
            listOf(task("belt", 5_000), task("oil", -300), task("plugs", 700), task("coolant", 3_000), task("filter", 200)),
        )

        val tasks = loaded(viewModel(cars = listOf(kia))).car("kia").upcomingTasks

        assertEquals(
            listOf(
                UpcomingTask("oil", -300, TaskStatus.DUE),
                UpcomingTask("filter", 200, TaskStatus.SOON),
                UpcomingTask("plugs", 700, TaskStatus.SOON),
                UpcomingTask("coolant", 3_000, TaskStatus.OK),
            ),
            tasks,
        )
    }

    @Test
    fun cardShowsFourLatestRecordsOfItsOwnCar() = runTest {
        val history = listOf(
            record("kia", "oil", LocalDate(2026, 3, 1)),
            record("kia", "filter", LocalDate(2026, 9, 12)),
            record("kia", "plugs", LocalDate(2025, 11, 5)),
            record("kia", "belt", LocalDate(2026, 6, 20)),
            record("kia", "pads", LocalDate(2026, 1, 15)),
            record("camry", "tyres", LocalDate(2026, 10, 1)),
        )

        val state = loaded(viewModel(cars = listOf(car("kia"), car("camry")), history = history))

        assertEquals(
            listOf(
                RecentRecord("filter", LocalDate(2026, 9, 12)),
                RecentRecord("belt", LocalDate(2026, 6, 20)),
                RecentRecord("oil", LocalDate(2026, 3, 1)),
                RecentRecord("pads", LocalDate(2026, 1, 15)),
            ),
            state.car("kia").recentRecords,
        )
        assertEquals(listOf(RecentRecord("tyres", LocalDate(2026, 10, 1))), state.car("camry").recentRecords)
    }

    @Test
    fun plateIsDrawnInDetectedStyle() = runTest {
        val cars = listOf(car("kia", plate = "А123ВС77"), car("seat", plate = "1234 BCD"), car("camry"))

        val state = loaded(viewModel(cars = cars))

        assertEquals(PlateLook.Russian(series = "А 123 ВС", region = "77", text = "А123ВС77"), state.car("kia").plateLook)
        assertEquals(PlateLook.European(countryCode = "E", text = "1234 BCD"), state.car("seat").plateLook)
        assertNull(state.car("camry").plateLook)
    }

    @Test
    fun viewAndUnitComeFromSettings() = runTest {
        val settings = FakeSettingsRepository(
            defaultTestSettings.copy(distanceUnit = DistanceUnit.MILES, carsView = CarsView.TASKS_AND_HISTORY),
        )

        val state = loaded(viewModel(cars = listOf(car("kia")), settings = settings))

        assertEquals(CarsView.TASKS_AND_HISTORY, state.view)
        assertEquals(DistanceUnit.MILES, state.distanceUnit)
    }

    @Test
    fun selectedViewIsStoredInSettings() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(cars = listOf(car("kia")), settings = settings)

        viewModel.onViewSelect(CarsView.TASKS)

        assertEquals(CarsView.TASKS, settings.settings.carsView)
        assertEquals(1, settings.writes)
    }

    private companion object {
        const val MILEAGE_KM = 100_000
        const val INTERVAL_KM = 10_000
        const val WINDOW_KM = 1_000
    }
}
