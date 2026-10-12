package ru.kuznetsov.pitstop.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.kuznetsov.pitstop.data.local.DatabaseDriverFactory
import ru.kuznetsov.pitstop.data.local.PitStopDatabase
import ru.kuznetsov.pitstop.data.repository.CarRepositoryImpl
import ru.kuznetsov.pitstop.data.repository.ServiceHistoryRepositoryImpl
import ru.kuznetsov.pitstop.data.repository.SettingsRepositoryImpl
import ru.kuznetsov.pitstop.domain.repository.CarRepository
import ru.kuznetsov.pitstop.domain.repository.ServiceHistoryRepository
import ru.kuznetsov.pitstop.domain.repository.SettingsRepository

val dataModule = module {
    single { PitStopDatabase(get<DatabaseDriverFactory>().create()) }
    singleOf(::CarRepositoryImpl) bind CarRepository::class
    singleOf(::ServiceHistoryRepositoryImpl) bind ServiceHistoryRepository::class
    singleOf(::SettingsRepositoryImpl) bind SettingsRepository::class
}
