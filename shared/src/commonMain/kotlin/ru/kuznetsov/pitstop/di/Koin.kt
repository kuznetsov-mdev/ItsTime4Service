package ru.kuznetsov.pitstop.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin() = initKoin {}

fun initKoin(appDeclaration: KoinAppDeclaration) {
    startKoin {
        appDeclaration()
        modules(featureModule, domainModule, dataModule, platformModule)
    }
}
