package ru.kuznetsov.itstime4service.di

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import ru.kuznetsov.itstime4service.domain.usecase.AddCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.AddTaskUseCase
import ru.kuznetsov.itstime4service.domain.usecase.CalculateTaskStatusUseCase
import ru.kuznetsov.itstime4service.domain.usecase.CarAlertsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.DeleteCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.DeleteTaskUseCase
import ru.kuznetsov.itstime4service.domain.usecase.DoneMarksUseCase
import ru.kuznetsov.itstime4service.domain.usecase.GetNowUseCase
import ru.kuznetsov.itstime4service.domain.usecase.GetTodayUseCase
import ru.kuznetsov.itstime4service.domain.usecase.MarkTaskDoneUseCase
import ru.kuznetsov.itstime4service.domain.usecase.NormalizeTaskUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarByIdUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveHistoryUseCase
import ru.kuznetsov.itstime4service.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.PlanRemindersUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SortedTaskStatsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.SyncRemindersUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UnmarkTaskDoneUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateCarUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateHistoryEntryUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateMileageUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateSettingsUseCase
import ru.kuznetsov.itstime4service.domain.usecase.UpdateTaskUseCase

val domainModule = module {
    factoryOf(::CalculateTaskStatusUseCase)
    factoryOf(::SortedTaskStatsUseCase)
    factoryOf(::CarAlertsUseCase)
    factoryOf(::GetTodayUseCase)
    factoryOf(::ObserveCarsUseCase)
    factoryOf(::ObserveCarByIdUseCase)
    factoryOf(::NormalizeTaskUseCase)
    factoryOf(::AddCarUseCase)
    factoryOf(::DeleteCarUseCase)
    factoryOf(::UpdateMileageUseCase)
    factoryOf(::MarkTaskDoneUseCase)
    factoryOf(::DoneMarksUseCase)
    factoryOf(::UnmarkTaskDoneUseCase)
    factoryOf(::ObserveHistoryUseCase)
    factoryOf(::ObserveSettingsUseCase)
    factoryOf(::UpdateSettingsUseCase)
    factoryOf(::UpdateCarUseCase)
    factoryOf(::AddTaskUseCase)
    factoryOf(::UpdateTaskUseCase)
    factoryOf(::DeleteTaskUseCase)
    factoryOf(::UpdateHistoryEntryUseCase)
    factoryOf(::GetNowUseCase)
    factoryOf(::PlanRemindersUseCase)
    factoryOf(::SyncRemindersUseCase)
}
