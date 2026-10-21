package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.PlannedReminder
import ru.kuznetsov.itstime4service.domain.model.ReminderRecord
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.ReminderRepository
import ru.kuznetsov.itstime4service.domain.repository.ReminderScheduler
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository
import ru.kuznetsov.itstime4service.domain.repository.TaskHistoryRepository
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [CarRepository] for use case tests: serves active [cars], which a test may replace to emit a change, and [sold]
 * cars, and records the edits of a car, the mileage it was given and the tasks it was asked to delete. Hiding the plate
 * or the VIN changes the served car, so observers see it.
 */
internal class FakeCarRepository(
    cars: List<Car>,
    private val sold: List<CarSummary> = emptyList(),
) : CarRepository {
    constructor(car: Car?) : this(listOfNotNull(car))

    private val state = MutableStateFlow(cars)
    var cars: List<Car>
        get() = state.value
        set(value) {
            state.value = value
        }

    val updatedCars = mutableListOf<List<String?>>()
    val mileageUpdates = mutableListOf<Pair<String, Int>>()
    val deletedTasks = mutableListOf<Pair<String, String>>()

    override fun observeCars(): Flow<List<Car>> = state

    override fun observeCarById(carId: String): Flow<Car?> = state.map { cars -> cars.firstOrNull { it.id == carId } }

    override fun observeCarSummaries(): Flow<List<CarSummary>> =
        state.map { cars -> cars.map { CarSummary(it.id, it.brand, it.mileageKm, isSold = false) } + sold }

    override suspend fun updateMileage(carId: String, mileageKm: Int) {
        mileageUpdates += carId to mileageKm
    }

    override suspend fun updateCar(carId: String, brand: String, vin: String?, plate: String?) {
        updatedCars += listOf(carId, brand, vin, plate)
    }

    override suspend fun deleteTask(carId: String, taskId: String) {
        deletedTasks += carId to taskId
    }

    override suspend fun setPlateHidden(carId: String, hidden: Boolean) {
        cars = cars.map { if (it.id == carId) it.copy(plateHidden = hidden) else it }
    }

    override suspend fun setVinHidden(carId: String, hidden: Boolean) {
        cars = cars.map { if (it.id == carId) it.copy(vinHidden = hidden) else it }
    }
}

/**
 * [ServiceHistoryRepository] for use case tests: serves fixed records and records every record added by hand.
 */
internal class FakeHistoryRepository(private val entries: List<ServiceHistoryEntry>) : ServiceHistoryRepository {
    val added = mutableListOf<ServiceHistoryEntry>()

    override fun observeHistory(carId: String): Flow<List<ServiceHistoryEntry>> =
        flowOf(entries.filter { it.carId == carId })

    override fun observeAllHistory(): Flow<List<ServiceHistoryEntry>> = flowOf(entries)

    override suspend fun addEntry(
        carId: String,
        taskName: String,
        date: LocalDate,
        mileageKm: Int,
        serviceName: String?,
        costRub: Int?,
        comment: String?,
    ) {
        added += ServiceHistoryEntry("added-${added.size}", carId, taskName, date, mileageKm, costRub, serviceName, comment)
    }
}

/**
 * [TaskHistoryRepository] for use case tests: records every write it was asked for and answers [result] where an
 * operation reports whether it found what it changes. The rules it applies in the app are tested on their own.
 */
internal class FakeTaskHistoryRepository(private val result: Boolean = true) : TaskHistoryRepository {
    val addedCars = mutableListOf<NewCar>()
    val soldIds = mutableListOf<String>()
    val purgedIds = mutableListOf<String>()
    val addedTasks = mutableListOf<NewMaintenanceTask>()
    val updatedTasks = mutableListOf<Pair<MaintenanceTask, LocalDate?>>()
    val marked = mutableListOf<List<Any?>>()
    val unmarked = mutableListOf<Pair<String, String>>()
    val updatedEntries = mutableListOf<ServiceHistoryEntry>()

    override suspend fun addCar(car: NewCar): String {
        addedCars += car
        return "new-car"
    }

    override suspend fun sellCar(carId: String) {
        soldIds += carId
    }

    override suspend fun purgeCar(carId: String) {
        purgedIds += carId
    }

    override suspend fun addTask(carId: String, task: NewMaintenanceTask) {
        addedTasks += task
    }

    override suspend fun updateTask(carId: String, task: MaintenanceTask, recordDate: LocalDate?) {
        updatedTasks += task to recordDate
    }

    override suspend fun markTaskDone(
        carId: String,
        taskId: String,
        date: LocalDate,
        serviceName: String?,
        costRub: Int?,
        comment: String?,
    ): Boolean {
        marked += listOf(carId, taskId, date, serviceName, costRub, comment)
        return result
    }

    override suspend fun unmarkTaskDone(carId: String, taskId: String): Boolean {
        unmarked += carId to taskId
        return result
    }

    override suspend fun updateEntry(entry: ServiceHistoryEntry): Boolean {
        updatedEntries += entry
        return result
    }
}

/**
 * [SettingsRepository] for use case tests: keeps the settings in memory, emits every change and counts the writes.
 */
internal class FakeSettingsRepository(settings: Settings = defaultTestSettings) : SettingsRepository {
    private val state = MutableStateFlow(settings)
    var settings: Settings
        get() = state.value
        set(value) {
            state.value = value
        }
    var writes = 0

    override fun observeSettings(): Flow<Settings> = state

    override suspend fun updateSettings(change: (Settings) -> Settings) {
        settings = change(settings)
        writes++
    }
}

/**
 * [ReminderRepository] and [ReminderScheduler] for use case tests: keep the stored plan in memory, starting from
 * [records], and remember the last plan handed to the system and how many times one was handed.
 */
internal class FakeReminders(var records: List<ReminderRecord> = emptyList()) : ReminderRepository, ReminderScheduler {
    var schedules = 0
    var scheduled: List<PlannedReminder> = emptyList()
    var scheduledUnit: DistanceUnit? = null

    override suspend fun getRecords(): List<ReminderRecord> = records

    override suspend fun replaceRecords(records: List<ReminderRecord>) {
        this.records = records
    }

    override suspend fun schedule(reminders: List<PlannedReminder>, distanceUnit: DistanceUnit) {
        schedules++
        scheduled = reminders
        scheduledUnit = distanceUnit
    }
}

internal fun fixedClock(at: LocalDateTime, timeZone: TimeZone = TimeZone.UTC): Clock =
    object : Clock {
        override fun now(): Instant = at.toInstant(timeZone)
    }

internal val defaultTestSettings = Settings(
    themeMode = ThemeMode.SYSTEM,
    distanceUnit = DistanceUnit.KILOMETERS,
    pushEnabled = true,
    defaultReminderWindowKm = 2_000,
    reminderTime = LocalTime(9, 0),
)

internal fun testCar(id: String) = Car(id = id, brand = id, mileageKm = 10_000, vin = null, tasks = emptyList())
