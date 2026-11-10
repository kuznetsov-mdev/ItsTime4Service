package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import ru.kuznetsov.itstime4service.domain.transfer.HistoryDetails
import ru.kuznetsov.itstime4service.domain.transfer.LastService
import ru.kuznetsov.itstime4service.domain.transfer.Presence
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferGroup
import ru.kuznetsov.itstime4service.domain.transfer.TransferHistory
import ru.kuznetsov.itstime4service.domain.transfer.TransferOutcome
import ru.kuznetsov.itstime4service.domain.transfer.TransferReminder
import ru.kuznetsov.itstime4service.domain.transfer.TransferSnapshot
import ru.kuznetsov.itstime4service.domain.transfer.TransferTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class TransferValidationTest {
    private val codec = TransferCodecImpl()

    @Test
    fun maximumScalarValuesRoundTripWithoutOverflowOrLocaleConversion() = runTest {
        val source = TransferFixtures.backup()
        val car = source.cars.single()
        val history = (car.history as Presence.Present).value.first()
        val settings = (source.settings as Presence.Present).value
        val maximum = source.copy(
            createdAt = Instant.parse("9999-12-31T23:59:59.999Z"),
            cars = listOf(car.copy(
                brand = "я".repeat(512), maintenanceDescription = "я".repeat(8192),
                mileageKm = Presence.Present(Int.MAX_VALUE), vin = Presence.Present("я".repeat(128)), plate = Presence.Present("я".repeat(128)),
                tasks = listOf(car.tasks.single().copy(name = "я".repeat(512), intervalKm = Int.MAX_VALUE, intervalMonths = 120000,
                    lastService = Presence.Present(LastService(Int.MAX_VALUE, LocalDate(9999, 12, 31))), reminderWindowKm = Presence.Present(Int.MAX_VALUE))),
                history = Presence.Present(listOf(history.copy(date = LocalDate(1, 1, 1), mileageKm = Int.MAX_VALUE,
                    details = Presence.Present(HistoryDetails(Int.MAX_VALUE, "я".repeat(512), "я".repeat(8192)))))),
            )),
            settings = Presence.Present(settings.copy(defaultReminderWindowKm = Int.MAX_VALUE, reminderTime = LocalTime(23, 59, 59))),
            reminderState = Presence.Present(listOf(TransferReminder(TransferFixtures.id(11), Instant.parse("0001-01-01T00:00:00.000Z")))),
        )
        val bytes = assertIs<TransferOutcome.Success<ByteArray>>(codec.encode(maximum)).value
        assertEquals(TransferOutcome.Success(maximum), codec.decode(bytes))
    }

    @Test
    fun forbiddenPresenceDuplicateIdentitiesAndBrokenLinksAreRejected() = runTest {
        val source = TransferFixtures.backup()
        val car = source.cars.single()
        val history = (car.history as Presence.Present).value
        val reminder = (source.reminderState as Presence.Present).value.single()
        val invalid = listOf(
            source.copy(includedGroups = TransferGroup.entries.toSet() - TransferGroup.VIN),
            source.copy(cars = listOf(car.copy(sold = Presence.Present(true)))),
            source.copy(cars = listOf(car.copy(tasks = car.tasks + car.tasks))),
            source.copy(cars = listOf(car.copy(history = Presence.Present(history + history)))),
            source.withTask { it.copy(intervalMonths = 120001) },
            source.withTask { it.copy(reminderWindowKm = Presence.Present(-1)) },
            source.withTask { it.copy(lastService = Presence.Present(LastService(-1, null))) },
            source.withHistory { it.copy(date = LocalDate(0, 1, 1)) },
            source.withHistory { it.copy(details = Presence.Present(HistoryDetails(-1, null, null))) },
            source.withHistory { it.copy(rollback = Presence.Absent) },
            source.copy(reminderState = Presence.Present(listOf(reminder, reminder))),
            source.copy(reminderState = Presence.Present(listOf(reminder.copy(taskId = TransferFixtures.id(99))))),
            source.copy(reminderState = Presence.Present(listOf(reminder.copy(shownAt = Instant.parse("2026-11-10T12:00:00.001Z"))))),
            source.copy(createdAt = Instant.parse("2026-11-10T12:00:00.000001Z")),
            source.copy(snapshotId = TransferFixtures.id(100).replace("-4000-", "-3000-")),
        )
        invalid.forEach { assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), codec.encode(it)) }
    }

    @Test
    fun textAndCollectionLimitsRejectBoundaryPlusOne() = runTest {
        val source = TransferFixtures.car()
        val car = source.cars.single()
        val tooLarge = listOf(
            source.copy(cars = listOf(car.copy(brand = "я".repeat(513)))),
            source.copy(cars = listOf(car.copy(maintenanceDescription = "я".repeat(8193)))),
            source.copy(cars = listOf(car.copy(tasks = List(20001) { car.tasks.single() }))),
            source.copy(includedGroups = setOf(TransferGroup.VIN), cars = listOf(car.copy(vin = Presence.Present("я".repeat(129))))),
        )
        tooLarge.forEach { assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), codec.encode(it)) }
    }

    @Test
    fun historyCannotReferenceATaskOwnedByAnotherCar() = runTest {
        val source = TransferFixtures.backup()
        val first = source.cars.single()
        val history = (first.history as Presence.Present).value.first()
        val second = first.copy(id = TransferFixtures.id(2), tasks = emptyList(), history = Presence.Present(listOf(history.copy(id = TransferFixtures.id(103)))))
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), codec.encode(source.copy(cars = listOf(first, second))))
    }

    private fun TransferSnapshot.withTask(change: (TransferTask) -> TransferTask): TransferSnapshot {
        val car = cars.single()
        val changed = car.copy(tasks = listOf(change(car.tasks.single())))
        return copy(cars = listOf(changed))
    }

    private fun TransferSnapshot.withHistory(change: (TransferHistory) -> TransferHistory): TransferSnapshot {
        val car = cars.single()
        val first = (car.history as Presence.Present).value.first()
        val changed = car.copy(history = Presence.Present(listOf(change(first))))
        return copy(cars = listOf(changed))
    }
}
