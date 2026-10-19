package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [UpdateHistoryEntryUseCase]: how a record is checked and cleaned up before the storage writes it. When the
 * task follows the record is decided by [ru.kuznetsov.itstime4service.domain.rule.TaskHistoryRules], tested on its own.
 */
class UpdateHistoryEntryUseCaseTest {

    private val entry = ServiceHistoryEntry(
        id = "mark",
        carId = "car",
        taskName = "Oil change",
        date = LocalDate(2026, 10, 15),
        mileageKm = 84_300,
        taskId = "oil",
        prevLastDoneKm = 74_000,
    )

    @Test
    fun editIsHandedToTheStorage() = runTest {
        val repository = FakeTaskHistoryRepository()

        assertTrue(UpdateHistoryEntryUseCase(repository)(entry.copy(mileageKm = 84_000, serviceName = "Kia Center", costRub = 4_200)))

        assertEquals(entry.copy(mileageKm = 84_000, serviceName = "Kia Center", costRub = 4_200), repository.updatedEntries.single())
    }

    @Test
    fun blankTextsAndNonPositiveCostAreStoredAsNull() = runTest {
        val repository = FakeTaskHistoryRepository()

        UpdateHistoryEntryUseCase(repository)(entry.copy(serviceName = "  ", comment = "", costRub = 0))

        val stored = repository.updatedEntries.single()
        assertNull(stored.serviceName)
        assertNull(stored.comment)
        assertNull(stored.costRub)
    }

    @Test
    fun nonPositiveMileageIsRejected() = runTest {
        val repository = FakeTaskHistoryRepository()

        assertFalse(UpdateHistoryEntryUseCase(repository)(entry.copy(mileageKm = 0)))
        assertTrue(repository.updatedEntries.isEmpty())
    }

    @Test
    fun missingRecordIsReported() = runTest {
        assertFalse(UpdateHistoryEntryUseCase(FakeTaskHistoryRepository(result = false))(entry))
    }
}
