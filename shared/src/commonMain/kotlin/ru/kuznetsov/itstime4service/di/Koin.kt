package ru.kuznetsov.itstime4service.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin() = initKoin {}

fun initKoin(appDeclaration: KoinAppDeclaration) {
    startKoin {
        appDeclaration()
        modules(featureModule, domainModule, dataModule, platformModule)
    }
}
