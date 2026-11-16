package ru.kuznetsov.itstime4service.data.transfer

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.providers.openssl3.Openssl3

internal actual fun transferSha256(bytes: ByteArray): ByteArray =
    CryptographyProvider.Openssl3.get(SHA256).hasher().hashBlocking(bytes)
