package ru.kuznetsov.itstime4service.data.transfer

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.openssl3.Openssl3
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.OpenSSL_version
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlin.test.Test
import kotlin.test.assertTrue

class TransferCryptoIosProofTest : TransferCryptoCompatibilityProof() {
    override val provider = CryptographyProvider.Openssl3

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun linkedLibraryUsesPinnedPatchVersion() {
        val version = OpenSSL_version(0)?.toKString()
        assertTrue(version?.startsWith("OpenSSL 3.6.5 ") == true)
    }
}
