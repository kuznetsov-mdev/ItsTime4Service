package ru.kuznetsov.itstime4service.di

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.kuznetsov.itstime4service.data.local.DatabaseDriverFactory
import ru.kuznetsov.itstime4service.data.notification.LocalNotifications
import ru.kuznetsov.itstime4service.data.notification.NotificationQueue
import ru.kuznetsov.itstime4service.data.transfer.TransferCipherImpl
import ru.kuznetsov.itstime4service.domain.transfer.TransferCipher

actual val platformModule: Module = module {
    single { TransferCipherImpl(CryptographyProvider.JDK(BouncyCastleProvider())) } bind TransferCipher::class
    single { DatabaseDriverFactory(androidContext()) }
    single { LocalNotifications(androidContext()) } bind NotificationQueue::class
}
