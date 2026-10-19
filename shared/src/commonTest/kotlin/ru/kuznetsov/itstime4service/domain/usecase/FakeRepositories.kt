package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewCar
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository

/**
 * [CarRepository] for use case tests: serves fixed active cars and [sold] cars, and records the done marks it was
 * asked to remove, the cars it was asked to sell or purge and the tasks it was asked to add or update.
 * Methods the tests do not need fail.
 */
internal class FakeCarRepository(
    private val cars: List<Car>,
    private val sold: List<CarSummary> = emptyList(),
) : CarRepository {
    constructor(car: Car?) : this(listOfNotNull(car))

    val unmarked = mutableListOf<Triple<String, String, String>>()
    val soldIds = mutableListOf<String>()
    val purgedIds = mutableListOf<String>()
    val addedTasks = mutableListOf<NewMaintenanceTask>()
    val updatedTasks = mutableListOf<MaintenanceTask>()

    override fun observeCars(): Flow<List<Car>> = flowOf(cars)

    override fun observeCarById(carId: String): Flow<Car?> = flowOf(cars.firstOrNull { it.id == carId })

    override suspend fun unmarkTaskDone(carId: String, taskId: String, entryId: String): Boolean {
        unmarked += Triple(carId, taskId, entryId)
        return true
    }

    override suspend fun addCar(car: NewCar): String = error("not used")

    override fun observeCarSummaries(): Flow<List<CarSummary>> =
        flowOf(cars.map { CarSummary(it.id, it.brand, it.mileageKm, isSold = false) } + sold)

    override suspend fun sellCar(carId: String) {
        soldIds += carId
    }

    override suspend fun purgeCar(carId: String) {
        purgedIds += carId
    }

    override suspend fun updateMileage(carId: String, mileageKm: Int) = error("not used")

    override suspend fun markTaskDone(
        carId: String,
        taskId: String,
        date: LocalDate,
        serviceName: String?,
        costRub: Int?,
        comment: String?,
    ): Boolean = error("not used")

    override suspend fun updateCar(carId: String, brand: String, vin: String?, plate: String?) = error("not used")

    override suspend fun addTask(carId: String, task: NewMaintenanceTask) {
        addedTasks += task
    }

    override suspend fun updateTask(carId: String, task: MaintenanceTask) {
        updatedTasks += task
    }

    override suspend fun deleteTask(carId: String, taskId: String) = error("not used")
}

/**
 * [ServiceHistoryRepository] for use case tests: serves fixed records and records every edit it was given
 * together with the task mileage passed along, and every record added by hand.
 */
internal class FakeHistoryRepository(private val entries: List<ServiceHistoryEntry>) : ServiceHistoryRepository {
    val updated = mutableListOf<Pair<ServiceHistoryEntry, Int?>>()
    val added = mutableListOf<ServiceHistoryEntry>()

    override fun observeHistory(carId: String): Flow<List<ServiceHistoryEntry>> =
        flowOf(entries.filter { it.carId == carId })

    override suspend fun updateEntry(entry: ServiceHistoryEntry, taskLastDoneKm: Int?) {
        updated += entry to taskLastDoneKm
    }

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
 * [SettingsRepository] for use case tests: keeps the settings in memory and counts the writes.
 */
internal class FakeSettingsRepository(var settings: Settings = defaultTestSettings) : SettingsRepository {
    var writes = 0

    override fun observeSettings(): Flow<Settings> = flowOf(settings)

    override suspend fun updateSettings(change: (Settings) -> Settings) {
        settings = change(settings)
        writes++
    }
}

internal val defaultTestSettings = Settings(
    themeMode = ThemeMode.SYSTEM,
    distanceUnit = DistanceUnit.KILOMETERS,
    pushEnabled = true,
    defaultReminderWindowKm = 2_000,
    reminderTime = LocalTime(9, 0),
)

internal fun testCar(id: String) = Car(id = id, brand = id, mileageKm = 10_000, vin = null, tasks = emptyList())
