package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the rule of [DoneMarksUseCase]: when a task counts as just marked done and which record holds the mark.
 */
class DoneMarksUseCaseTest {

    private val doneMarks = DoneMarksUseCase()
    private val date = LocalDate(2026, 10, 15)

    private fun car(mileageKm: Int = 84_300, lastDoneKm: Int = 84_300) = Car(
        id = "car",
        brand = "Kia Rio",
        mileageKm = mileageKm,
        vin = null,
        tasks = listOf(MaintenanceTask("oil", "Oil change", 10_000, lastDoneKm, 1_500)),
    )

    private fun entry(
        id: String = "mark",
        mileageKm: Int = 84_300,
        taskId: String? = "oil",
        prevLastDoneKm: Int? = 74_000,
    ) = ServiceHistoryEntry(
        id = id,
        carId = "car",
        taskName = "Oil change",
        date = date,
        mileageKm = mileageKm,
        taskId = taskId,
        prevLastDoneKm = prevLastDoneKm,
    )

    @Test
    fun taskMarkedAtCurrentMileageIsDone() {
        assertEquals(mapOf("oil" to entry()), doneMarks(car(), listOf(entry())))
    }

    @Test
    fun changedMileageEndsTheMark() {
        assertTrue(doneMarks(car(mileageKm = 84_400), listOf(entry())).isEmpty())
    }

    @Test
    fun editedTaskEndsTheMark() {
        assertTrue(doneMarks(car(lastDoneKm = 84_000), listOf(entry())).isEmpty())
    }

    @Test
    fun recordWithoutTaskIsNotAMark() {
        assertTrue(doneMarks(car(), listOf(entry(taskId = null, prevLastDoneKm = null))).isEmpty())
    }

    @Test
    fun recordWithoutPreviousValuesIsNotAMark() {
        assertTrue(doneMarks(car(), listOf(entry(prevLastDoneKm = null))).isEmpty())
    }

    @Test
    fun recordAtAnotherMileageIsNotAMark() {
        assertTrue(doneMarks(car(), listOf(entry(mileageKm = 74_000))).isEmpty())
    }

    @Test
    fun latestRecordAtCurrentMileageHoldsTheMark() {
        val old = entry(id = "old", mileageKm = 74_000)
        val mark = entry(id = "mark")

        assertEquals(mapOf("oil" to mark), doneMarks(car(), listOf(old, mark)))
    }
}
