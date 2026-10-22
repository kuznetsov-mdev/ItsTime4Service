package ru.kuznetsov.itstime4service.di

import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.kuznetsov.itstime4service.data.local.DatabaseDriverFactory
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.data.notification.ReminderSchedulerImpl
import ru.kuznetsov.itstime4service.data.repository.CarRepositoryImpl
import ru.kuznetsov.itstime4service.data.repository.databaseDispatcher
import ru.kuznetsov.itstime4service.data.repository.ReminderRepositoryImpl
import ru.kuznetsov.itstime4service.data.repository.ServiceHistoryRepositoryImpl
import ru.kuznetsov.itstime4service.data.repository.SettingsRepositoryImpl
import ru.kuznetsov.itstime4service.data.repository.TaskHistoryRepositoryImpl
import ru.kuznetsov.itstime4service.domain.repository.CarRepository
import ru.kuznetsov.itstime4service.domain.repository.ReminderRepository
import ru.kuznetsov.itstime4service.domain.repository.ReminderScheduler
import ru.kuznetsov.itstime4service.domain.repository.ServiceHistoryRepository
import ru.kuznetsov.itstime4service.domain.repository.SettingsRepository
import ru.kuznetsov.itstime4service.domain.repository.TaskHistoryRepository

val dataModule = module {
    single<CoroutineDispatcher> { databaseDispatcher }
    single { ItsTime4ServiceDatabase(get<DatabaseDriverFactory>().create()) }
    single { CarRepositoryImpl(get(), get()) } bind CarRepository::class
    single { ServiceHistoryRepositoryImpl(get(), get()) } bind ServiceHistoryRepository::class
    single { TaskHistoryRepositoryImpl(get(), get()) } bind TaskHistoryRepository::class
    single { SettingsRepositoryImpl(get(), get()) } bind SettingsRepository::class
    single { ReminderRepositoryImpl(get(), get()) } bind ReminderRepository::class
    singleOf(::ReminderSchedulerImpl) bind ReminderScheduler::class
}
