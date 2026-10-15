package ru.kuznetsov.itstime4service.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ru.kuznetsov.itstime4service.data.local.DatabaseDriverFactory
import ru.kuznetsov.itstime4service.data.notification.LocalNotifications

actual val platformModule: Module = module {
    single { DatabaseDriverFactory() }
    single { LocalNotifications() }
}
