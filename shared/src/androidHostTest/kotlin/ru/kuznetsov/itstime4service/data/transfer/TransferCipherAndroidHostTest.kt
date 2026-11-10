package ru.kuznetsov.itstime4service.data.transfer

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], manifest = Config.NONE)
class TransferCipherAndroidHostTest : TransferCipherContract() {
    override val provider = CryptographyProvider.JDK(BouncyCastleProvider())
}
