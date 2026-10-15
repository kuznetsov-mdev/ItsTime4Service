package ru.kuznetsov.itstime4service.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.kuznetsov.itstime4service.data.local.DatabaseDriverFactory
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.data.notification.ReminderSchedulerImpl
import ru.kuznetsov.itstime4service.data.repository.CarRepositoryImpl
import ru.kuznetsov.itstime4service.data.repository.ReminderRepositoryImpl
import ru.kuznetsov.itstime4service.data.repository.ServiceHistoryRepositoryImpl
import ru.kuznetsov.itstime4service.data.repository.SettingsRepositoryImpl
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.ReminderRepository
import ru.kuznetsov.itstime4service.domain.repository.ReminderScheduler
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository

val dataModule = module {
    single { ItsTime4ServiceDatabase(get<DatabaseDriverFactory>().create()) }
    singleOf(::CarRepositoryImpl) bind CarRepository::class
    singleOf(::ServiceHistoryRepositoryImpl) bind ServiceHistoryRepository::class
    singleOf(::SettingsRepositoryImpl) bind SettingsRepository::class
    singleOf(::ReminderRepositoryImpl) bind ReminderRepository::class
    singleOf(::ReminderSchedulerImpl) bind ReminderScheduler::class
}
