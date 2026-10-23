package ru.kuznetsov.itstime4service.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.kuznetsov.itstime4service.data.local.DatabaseDriverFactory
import ru.kuznetsov.itstime4service.data.notification.LocalNotifications
import ru.kuznetsov.itstime4service.data.notification.NotificationQueue

actual val platformModule: Module = module {
    single { DatabaseDriverFactory(androidContext()) }
    single { LocalNotifications(androidContext()) } bind NotificationQueue::class
}
