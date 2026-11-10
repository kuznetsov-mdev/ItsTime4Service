package ru.kuznetsov.itstime4service.data.transfer

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

@OptIn(DelicateCryptographyApi::class)
abstract class TransferCryptoCompatibilityProof {
    protected abstract val provider: CryptographyProvider

    @Test
    fun pbkdf2MatchesIndependentUtf8References() {
        val vectors = listOf(
            "test-password" to "cae9c801374596f17de48ba4ed7061692b5d0ab433932a7d3cdf698dfd8bb3e8",
            "Сервис café 🔧 e\u0301" to "2a3b7f04213df04e79f07f5d4584c8261db95c6836c675450f5f73dd525009c8",
            "a\u0000b" to "38b4da2e52f2e5c97dda9620bb28f1b204be3a436d284e830af78676e45169f9",
        )
        vectors.forEach { (password, expected) ->
            val derived = derive(password)
            try {
                assertContentEquals(expected.hexBytes(), derived)
            } finally {
                derived.fill(0)
            }
        }
    }

    @Test
    fun aes256GcmMatchesNistVector() {
        val cipher = cipher(ByteArray(32))
        val body = "cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919".hexBytes()
        assertContentEquals(body, cipher.encryptWithIvBlocking(ByteArray(12), ByteArray(16)))
        assertContentEquals(ByteArray(16), cipher.decryptWithIvBlocking(ByteArray(12), body))
    }

    @Test
    fun unicodeEnvelopeMatchesIndependentReferenceInBothDirections() {
        val derived = derive("Сервис café 🔧 e\u0301")
        try {
            val cipher = cipher(derived)
            assertContentEquals(referenceBody, cipher.encryptWithIvBlocking(nonce, plaintext, aad))
            assertContentEquals(plaintext, cipher.decryptWithIvBlocking(nonce, referenceBody, aad))
            assertEquals(plaintext.size + 16, referenceBody.size)
        } finally {
            derived.fill(0)
        }
    }

    @Test
    fun wrongPasswordAndTamperedAuthenticatedBytesAreRejected() {
        val derived = derive("Сервис café 🔧 e\u0301")
        val wrongKey = derive("wrong-password")
        try {
            val cipher = cipher(derived)
            assertFails { cipher(wrongKey).decryptWithIvBlocking(nonce, referenceBody, aad) }
            assertFails { cipher.decryptWithIvBlocking(nonce, referenceBody, aad.changed(0)) }
            assertFails { cipher.decryptWithIvBlocking(nonce, referenceBody, aad.changed(aad.lastIndex)) }
            assertFails { cipher.decryptWithIvBlocking(nonce, referenceBody.changed(0), aad) }
            assertFails { cipher.decryptWithIvBlocking(nonce, referenceBody.changed(referenceBody.lastIndex), aad) }
        } finally {
            derived.fill(0)
            wrongKey.fill(0)
        }
    }

    @Test
    fun emptyCiphertextRequiresValidAuthentication() {
        val cipher = cipher(ByteArray(32))
        listOf(byteArrayOf(), aad).forEach { authenticatedData ->
            val body = cipher.encryptWithIvBlocking(nonce, byteArrayOf(), authenticatedData)
            assertEquals(16, body.size)
            assertContentEquals(byteArrayOf(), cipher.decryptWithIvBlocking(nonce, body, authenticatedData))
            assertFails { cipher.decryptWithIvBlocking(nonce, body.changed(0), authenticatedData) }
            assertFails { cipher.decryptWithIvBlocking(nonce, body.changed(body.lastIndex), authenticatedData) }
            assertFails { cipher.decryptWithIvBlocking(nonce, body.copyOf(15), authenticatedData) }
            assertFails { cipher.decryptWithIvBlocking(nonce.changed(0), body, authenticatedData) }
            assertFails { cipher.decryptWithIvBlocking(nonce, body, authenticatedData + 1.toByte()) }
            assertFails { cipher(ByteArray(32) { 1 }).decryptWithIvBlocking(nonce, body, authenticatedData) }
        }
    }

    @Test
    fun cryptographicRandomProvidesFreshSaltAndNonce() {
        val salt = CryptographyRandom.nextBytes(16)
        val nextSalt = CryptographyRandom.nextBytes(16)
        val nonce = CryptographyRandom.nextBytes(12)
        val nextNonce = CryptographyRandom.nextBytes(12)
        assertEquals(16, salt.size)
        assertEquals(12, nonce.size)
        assertFalse(salt.contentEquals(nextSalt))
        assertFalse(nonce.contentEquals(nextNonce))
    }

    private fun derive(password: String): ByteArray {
        val bytes = password.encodeToByteArray(throwOnInvalidSequence = true)
        return try {
            provider.get(PBKDF2).secretDerivation(
                digest = SHA256,
                iterations = 600_000,
                outputSize = 32.bytes,
                salt = ByteArray(16) { it.toByte() },
            ).deriveSecretToByteArrayBlocking(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun cipher(key: ByteArray) = provider.get(AES.GCM).keyDecoder()
        .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key).cipher()

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.changed(index: Int): ByteArray = copyOf().also {
        it[index] = (it[index].toInt() xor 1).toByte()
    }

    private val nonce = ByteArray(12) { it.toByte() }
    private val plaintext = "7b22666f726d617456657273696f6e223a312c226e6f7465223a22d09fd0bbd0b0d0bd20d0a2d09e20f09f94a720636166c3a920655c7530333031227d".hexBytes()
    private val aad = "495434530101000000920000004d7b22636970686572223a224145532d3235362d47434d222c226b6466223a2250424b4446322d484d41432d534841323536222c22697465726174696f6e73223a3630303030302c2273616c74223a2241414543417751464267634943516f4c4441304f44773d3d222c226e6f6e6365223a2241414543417751464267634943516f4c222c227461674279746573223a31367d".hexBytes()
    private val referenceBody = "369a1d394222aa5cfc1e003dfb41d1c6f752e453078b85025e5b1e15b00ef193635ec0674ed0b2865bb7b30d59e88850e55b83d1aefb0f1f9c14532e9487fbbb092f9b5e6bce0f96d8b1b767cb".hexBytes()
}
