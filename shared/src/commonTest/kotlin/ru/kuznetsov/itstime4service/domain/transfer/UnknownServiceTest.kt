package ru.kuznetsov.itstime4service.domain.transfer

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.usecase.SortedTaskStatsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.CarAlertsUseCase
import ru.kuznetsov.itstime4service.domain.model.MaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.domain.usecase.CalculateTaskStatusUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnknownServiceTest {
    private val calculate = CalculateTaskStatusUseCase()
    private val today = LocalDate(2026, 11, 10)
    private val unknown = MaintenanceTask("t", "Oil", 10000, null, 1500)

    @Test
    fun unknownServiceDoesNotUseZero() {
        val stat = calculate(80000, unknown, today)
        assertEquals(TaskStatus.RECOMMENDED, stat.status)
        assertNull(stat.dueAtKm)
        assertNull(stat.remainingKm)
        assertNull(stat.progress)
    }

    @Test
    fun knownDueCriterionWinsOverMissingCriterion() {
        val timed = calculate(80000, unknown.copy(intervalMonths = 12, lastDoneDate = LocalDate(2025, 1, 1)), today)
        assertEquals(TaskStatus.DUE, timed.status)
        assertEquals(-10, timed.remainingMonths)
        assertNull(timed.remainingKm)
        val mileage = calculate(80000, unknown.copy(lastDoneKm = 60000, intervalMonths = 12), today)
        assertEquals(TaskStatus.DUE, mileage.status)
        assertEquals(-10000, mileage.remainingKm)
        assertNull(mileage.remainingMonths)
    }

    @Test
    fun legacyZeroIsPreserved() {
        val stat = calculate(80000, unknown.copy(lastDoneKm = 0), today)
        assertEquals(10000, stat.dueAtKm)
        assertEquals(-70000, stat.remainingKm)
        assertEquals(TaskStatus.DUE, stat.status)
    }
    @Test
    fun urgencyAndCountersIncludeRecommendationExplicitly() {
        val tasks = listOf(
            unknown.copy(id = "ok", lastDoneKm = 79000),
            unknown.copy(id = "recommended"),
            unknown.copy(id = "soon", lastDoneKm = 71000),
            unknown.copy(id = "due", lastDoneKm = 60000),
        )
        val car = Car("c", "Kia", 80000, null, tasks)
        assertEquals(listOf("due", "recommended", "soon", "ok"), SortedTaskStatsUseCase(calculate)(car, today).map { it.task.id })
        val alerts = CarAlertsUseCase(calculate)(car, today)
        assertEquals(1, alerts.recommended)
        assertEquals(3, alerts.total)
        assertEquals(TaskStatus.DUE, alerts.worstStatus)
    }

}
