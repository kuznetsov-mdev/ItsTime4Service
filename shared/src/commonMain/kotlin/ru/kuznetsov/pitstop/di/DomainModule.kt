package ru.kuznetsov.pitstop.di

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import ru.kuznetsov.pitstop.domain.usecase.AddCarUseCase
import ru.kuznetsov.pitstop.domain.usecase.AddTaskUseCase
import ru.kuznetsov.pitstop.domain.usecase.CalculateTaskStatusUseCase
import ru.kuznetsov.pitstop.domain.usecase.CarAlertsUseCase
import ru.kuznetsov.pitstop.domain.usecase.DeleteCarUseCase
import ru.kuznetsov.pitstop.domain.usecase.DeleteTaskUseCase
import ru.kuznetsov.pitstop.domain.usecase.DoneMarksUseCase
import ru.kuznetsov.pitstop.domain.usecase.GetNowUseCase
import ru.kuznetsov.pitstop.domain.usecase.GetTodayUseCase
import ru.kuznetsov.pitstop.domain.usecase.MarkTaskDoneUseCase
import ru.kuznetsov.pitstop.domain.usecase.NormalizeTaskUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveCarByIdUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveCarsUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveHistoryUseCase
import ru.kuznetsov.pitstop.domain.usecase.ObserveSettingsUseCase
import ru.kuznetsov.pitstop.domain.usecase.PlanRemindersUseCase
import ru.kuznetsov.pitstop.domain.usecase.SortedTaskStatsUseCase
import ru.kuznetsov.pitstop.domain.usecase.SyncRemindersUseCase
import ru.kuznetsov.pitstop.domain.usecase.UnmarkTaskDoneUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateCarUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateHistoryEntryUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateMileageUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateSettingsUseCase
import ru.kuznetsov.pitstop.domain.usecase.UpdateTaskUseCase

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
