package ru.kuznetsov.itstime4service.data.transfer

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.openssl3.Openssl3

class TransferCipherIosTest : TransferCipherContract() {
    override val provider = CryptographyProvider.Openssl3
}
