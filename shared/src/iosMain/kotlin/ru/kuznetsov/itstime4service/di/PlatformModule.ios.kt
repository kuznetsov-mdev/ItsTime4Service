package ru.kuznetsov.itstime4service.di

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.openssl3.Openssl3
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.kuznetsov.itstime4service.data.local.DatabaseDriverFactory
import ru.kuznetsov.itstime4service.data.notification.LocalNotifications
import ru.kuznetsov.itstime4service.data.notification.NotificationQueue
import ru.kuznetsov.itstime4service.data.transfer.TransferCipherImpl
import ru.kuznetsov.itstime4service.domain.transfer.TransferCipher

actual val platformModule: Module = module {
    single { TransferCipherImpl(CryptographyProvider.Openssl3) } bind TransferCipher::class
    single { DatabaseDriverFactory() }
    single { LocalNotifications() } bind NotificationQueue::class
}
