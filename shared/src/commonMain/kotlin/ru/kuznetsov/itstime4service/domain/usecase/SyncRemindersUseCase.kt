package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.ReminderRecord
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.repository.ReminderRepository
import ru.kuznetsov.itstime4service.domain.repository.ReminderScheduler

/**
 * Rebuilds the notification plan for the given cars and settings and hands it to the system.
 * Stored records that have already fired count as shown notifications, so a task is not repeated too soon;
 * the latest of them per task is kept, the rest of the stored plan is replaced with the new one.
 */
class SyncRemindersUseCase(
    private val planReminders: PlanRemindersUseCase,
    private val getNow: GetNowUseCase,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend operator fun invoke(cars: List<Car>, settings: Settings) {
        val now = getNow()
        val taskIds = cars.flatMap { car -> car.tasks.map { it.id } }.toSet()
        val lastShown = reminderRepository.getRecords()
            .filter { it.fireAt <= now && it.taskId in taskIds }
            .groupBy { it.taskId }
            .map { (_, records) -> records.maxBy { it.fireAt } }
        val plan = planReminders(cars, settings, now, lastShown.associate { it.taskId to it.fireAt.date })
        reminderRepository.replaceRecords(lastShown + plan.map { ReminderRecord(it.taskId, it.fireAt) })
        reminderScheduler.schedule(plan, settings.distanceUnit)
    }
}
