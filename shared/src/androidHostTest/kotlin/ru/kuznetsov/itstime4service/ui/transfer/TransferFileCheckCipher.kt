package ru.kuznetsov.itstime4service.ui.transfer

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK
import org.bouncycastle.jce.provider.BouncyCastleProvider
import ru.kuznetsov.itstime4service.data.transfer.TransferCipherImpl
import ru.kuznetsov.itstime4service.domain.transfer.TransferCipher

internal actual fun transferFileCheckCipher(): TransferCipher = TransferCipherImpl(CryptographyProvider.JDK(BouncyCastleProvider()))
