package ru.kuznetsov.itstime4service.domain.transfer

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportSelectionTest {
    @Test
    fun defaultSelectionContainsNoPersonalFacts() {
        val selection = ExportSelection.Car("car", setOf("task"))
        assertTrue(selection.personalGroups.isEmpty())
        assertEquals(null, selection.maintenanceDescription)
    }

    @Test
    fun historyDoesNotIncludeLastService() {
        val selection = ExportSelection.Car("car", emptySet(), setOf(TransferGroup.HISTORY))
        assertTrue(selection.isValid())
        assertFalse(TransferGroup.LAST_SERVICE in selection.personalGroups)
    }

    @Test
    fun detailsRequireHistory() = runTest {
        val repository = RecordingRepository()
        val result = CreateTransferSnapshotUseCase(repository)(
            ExportSelection.Car("car", emptySet(), setOf(TransferGroup.HISTORY_DETAILS)),
        )
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), result)
        assertEquals(0, repository.calls)
    }

    @Test
    fun backupOnlyGroupsAreRejectedForCar() = runTest {
        val repository = RecordingRepository()
        val create = CreateTransferSnapshotUseCase(repository)
        listOf(TransferGroup.PREFERENCES, TransferGroup.ROLLBACK, TransferGroup.REMINDER_STATE).forEach { group ->
            assertEquals(
                TransferOutcome.Failure(TransferFailure.InvalidSnapshot),
                create(ExportSelection.Car("car", emptySet(), setOf(group))),
            )
        }
        assertEquals(0, repository.calls)
    }

    @Test
    fun validSelectionsReachRepository() = runTest {
        val repository = RecordingRepository()
        val create = CreateTransferSnapshotUseCase(repository)
        create(ExportSelection.FullBackup)
        create(ExportSelection.Car("car", setOf("task"), setOf(TransferGroup.MILEAGE, TransferGroup.VIN)))
        assertEquals(2, repository.calls)
    }

    private class RecordingRepository : TransferRepository {
        var calls = 0

        override suspend fun readSnapshot(selection: ExportSelection): TransferOutcome<TransferSnapshot> {
            calls++
            return TransferOutcome.Failure(TransferFailure.DatabaseReadFailed)
        }
    }
}
