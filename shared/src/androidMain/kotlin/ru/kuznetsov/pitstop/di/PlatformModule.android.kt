package ru.kuznetsov.pitstop.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import ru.kuznetsov.pitstop.data.local.DatabaseDriverFactory
import ru.kuznetsov.pitstop.data.notification.LocalNotifications

actual val platformModule: Module = module {
    single { DatabaseDriverFactory(androidContext()) }
    single { LocalNotifications(androidContext()) }
}
