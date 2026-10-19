package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import ru.kuznetsov.itstime4service.data.local.Maintenance_tasks
import ru.kuznetsov.itstime4service.data.mapper.toDomain
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers how a task icon is kept: a new task starts with a wrench, a picked icon is stored on add and on update,
 * a task saved without an icon stays without one when edited, and an unknown stored name reads as no icon.
 */
class TaskIconStorageTest {

    private val repository = FakeCarRepository(emptyList())
    private val normalizeTask = NormalizeTaskUseCase()

    private fun task(icon: TaskIcon?) = NewMaintenanceTask(name = "Oil change", intervalKm = 10_000, icon = icon)

    private fun row(icon: String?) = Maintenance_tasks(
        id = "t",
        car_id = "kia",
        name = "Oil change",
        interval_km = 10_000,
        last_done_km = 80_000,
        reminder_window_km = 1_500,
        interval_months = null,
        last_done_date = null,
        icon = icon,
    )

    @Test
    fun newTaskStartsWithWrench() = runTest {
        AddTaskUseCase(repository, normalizeTask)("kia", NewMaintenanceTask(name = "Oil change", intervalKm = 10_000))

        assertEquals(TaskIcon.WRENCH, repository.addedTasks.single().icon)
    }

    @Test
    fun pickedIconIsStoredOnAdd() = runTest {
        AddTaskUseCase(repository, normalizeTask)("kia", task(TaskIcon.DROPLET))

        assertEquals(TaskIcon.DROPLET, repository.addedTasks.single().icon)
    }

    @Test
    fun pickedIconIsStoredOnUpdate() = runTest {
        UpdateTaskUseCase(repository, FakeHistoryRepository(emptyList()), normalizeTask)("kia", "t", task(TaskIcon.TIRE))

        assertEquals(TaskIcon.TIRE, repository.updatedTasks.single().icon)
    }

    @Test
    fun taskWithoutIconStaysWithoutOneWhenEdited() = runTest {
        UpdateTaskUseCase(repository, FakeHistoryRepository(emptyList()), normalizeTask)("kia", "t", task(icon = null))

        assertNull(repository.updatedTasks.single().icon)
    }

    @Test
    fun storedIconIsReadByName() {
        assertEquals(TaskIcon.BATTERY, row("BATTERY").toDomain().icon)
    }

    @Test
    fun missingOrUnknownStoredIconReadsAsNone() {
        assertNull(row(null).toDomain().icon)
        assertNull(row("ROCKET").toDomain().icon)
    }
}
