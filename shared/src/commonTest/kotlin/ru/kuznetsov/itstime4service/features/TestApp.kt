package ru.kuznetsov.itstime4service.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import ru.kuznetsov.itstime4service.data.repository.TestDatabase
import ru.kuznetsov.itstime4service.data.repository.databaseDispatcher
import ru.kuznetsov.itstime4service.di.dataModule
import ru.kuznetsov.itstime4service.di.domainModule
import ru.kuznetsov.itstime4service.di.featureModule
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.ReminderScheduler
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository
import ru.kuznetsov.itstime4service.domain.repository.TaskHistoryRepository
import ru.kuznetsov.itstime4service.domain.usecase.FakeReminders
import ru.kuznetsov.itstime4service.domain.usecase.GetNowUseCase
import ru.kuznetsov.itstime4service.domain.usecase.GetTodayUseCase
import ru.kuznetsov.itstime4service.domain.usecase.fixedClock
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat

/**
 * The app's own Koin graph for view model tests, on a fresh SQLite database: view models, use cases and repositories
 * are the ones the app runs, only the database, the notification scheduler ([reminders]) and the clock, fixed at noon
 * UTC of [TODAY], are replaced. Repository work uses the test dispatcher when [ViewModelTestSetup] is active. [close]
 * clears every view model made by [viewModel] and closes the Koin app so resources do not leak into another test.
 * [failWrites] makes every write to a table fail with a trigger until [allowWrites]; [hideTable] makes every read of a
 * table fail by renaming it until [restoreTable].
 */
internal class TestApp {
    private val db = TestDatabase().create()
    val reminders = FakeReminders()

    private val koinApp = koinApplication {
        allowOverride(true)
        modules(
            featureModule,
            domainModule,
            dataModule,
            module {
                val clock = fixedClock(TODAY.atTime(12, 0))
                single { db.database }
                single<CoroutineDispatcher> { ViewModelTestSetup.repositoryDispatcher() }
                single<ReminderScheduler> { reminders }
                factory { GetTodayUseCase(clock) { TimeZone.UTC } }
                factory { GetNowUseCase(clock) { TimeZone.UTC } }
            },
        )
    }
    val koin: Koin = koinApp.koin

    val cars: CarRepository get() = koin.get()
    val history: ServiceHistoryRepository get() = koin.get()
    val settings: SettingsRepository get() = koin.get()
    val taskHistory: TaskHistoryRepository get() = koin.get()

    val viewModels = ViewModelStore()

    inline fun <reified T : ViewModel> viewModel(vararg parameters: Any?): T {
        val viewModel = koin.get<T> { parametersOf(*parameters) }
        viewModels.put("view-model-${viewModels.keys().size}", viewModel)
        return viewModel
    }

    fun close() {
        viewModels.clear()
        koinApp.close()
    }

    fun failWrites(table: String) = WRITES.forEach { event ->
        db.execute("CREATE TRIGGER fail_${event.lowercase()}_$table BEFORE $event ON $table BEGIN SELECT RAISE(ABORT, 'test failure'); END")
    }

    fun allowWrites(table: String) = WRITES.forEach { event -> db.execute("DROP TRIGGER fail_${event.lowercase()}_$table") }

    fun hideTable(table: String) = db.execute("ALTER TABLE $table RENAME TO ${table}_hidden")

    fun restoreTable(table: String) = db.execute("ALTER TABLE ${table}_hidden RENAME TO $table")

    suspend fun addCar(
        brand: String,
        mileageKm: Int = MILEAGE_KM,
        tasks: List<NewMaintenanceTask> = emptyList(),
        plate: String? = null,
        vin: String? = null,
    ): String = taskHistory.addCar(NewCar(brand = brand, mileageKm = mileageKm, vin = vin, tasks = tasks, plate = plate))

    suspend fun addRecord(carId: String, taskName: String, date: LocalDate, mileageKm: Int = MILEAGE_KM) =
        history.addEntry(carId, taskName, date, mileageKm, serviceName = null, costRub = null, comment = null)

    suspend fun changeSettings(change: (Settings) -> Settings) = settings.updateSettings(change)

    suspend fun car(id: String): Car? = cars.observeCarById(id).first()

    suspend fun car(id: String, until: (Car?) -> Boolean): Car? = cars.observeCarById(id).first(until)

    suspend fun summaries(): List<CarSummary> = cars.observeCarSummaries().first()

    suspend fun records(until: (List<ServiceHistoryEntry>) -> Boolean = { true }): List<ServiceHistoryEntry> =
        history.observeAllHistory().first(until)

    suspend fun settings(until: (Settings) -> Boolean = { true }): Settings = settings.observeSettings().first(until)

    companion object {
        val TODAY = LocalDate(2026, 10, 21)
        const val MILEAGE_KM = 100_000
        private val WRITES = listOf("INSERT", "UPDATE", "DELETE")
    }
}

/**
 * Sets up what every view model test needs: the main dispatcher of the view models and the Russian date format
 * `dd.MM.yyyy`, whatever the language of the machine running the tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal object ViewModelTestSetup {
    private val systemLanguage = DateInputFormat.language
    private var testDispatcher: CoroutineDispatcher? = null

    fun start() {
        val dispatcher = UnconfinedTestDispatcher()
        testDispatcher = dispatcher
        Dispatchers.setMain(dispatcher)
        DateInputFormat.language = { "ru" }
    }

    fun finish() {
        Dispatchers.resetMain()
        testDispatcher = null
        DateInputFormat.language = systemLanguage
    }

    fun repositoryDispatcher(): CoroutineDispatcher = testDispatcher ?: databaseDispatcher
}

internal fun <T> TestScope.keep(state: StateFlow<T>) {
    backgroundScope.launch { state.collect {} }
}

internal suspend fun <T> TestScope.watch(state: StateFlow<T>, until: (T) -> Boolean): T {
    keep(state)
    return state.first(until)
}

internal suspend fun eventually(condition: () -> Boolean) = withContext(Dispatchers.Default) {
    while (!condition()) delay(POLL_MS)
}

private const val POLL_MS = 10L
