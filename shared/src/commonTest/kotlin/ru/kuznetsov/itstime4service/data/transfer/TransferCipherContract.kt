package ru.kuznetsov.itstime4service.data.transfer

import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.kuznetsov.itstime4service.domain.transfer.SecretBytes
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits
import ru.kuznetsov.itstime4service.domain.transfer.TransferOutcome
import ru.kuznetsov.itstime4service.domain.transfer.Presence
import ru.kuznetsov.itstime4service.domain.transfer.TransferGroup
import ru.kuznetsov.itstime4service.domain.transfer.TransferSnapshot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

abstract class TransferCipherContract {
    protected abstract val provider: CryptographyProvider
    private val plaintext = "Test café 🔧".encodeToByteArray()

    @Test
    fun fullEncryptedEnvelopeRoundTripsWithFreshSaltAndNonce() = runTest {
        val cipher = TransferCipherImpl(provider)
        val password = SecretBytes.fromPassword(" Сервис café 🔧 e\u0301 ")
        try {
            val first = assertIs<TransferOutcome.Success<ByteArray>>(cipher.seal(plaintext, password)).value
            val second = assertIs<TransferOutcome.Success<ByteArray>>(cipher.seal(plaintext, password)).value
            assertFalse(first.contentEquals(second))
            assertFalse(first.decodeToString().contains("Test café"))
            assertContentEquals(plaintext, assertIs<TransferOutcome.Success<ByteArray>>(cipher.open(first, password)).value)
            assertContentEquals(plaintext, assertIs<TransferOutcome.Success<ByteArray>>(cipher.open(second, password)).value)
        } finally {
            password.close()
        }
    }

    @Test
    fun wrongPasswordAndAuthenticatedByteChangesHaveOneFailure() = runTest {
        val cipher = TransferCipherImpl(provider)
        val password = SecretBytes.fromPassword("password")
        val wrong = SecretBytes.fromPassword("wrong password")
        try {
            val sealed = assertIs<TransferOutcome.Success<ByteArray>>(cipher.seal(plaintext, password)).value
            val failure = TransferOutcome.Failure(TransferFailure.AuthenticationFailed)
            assertEquals(failure, cipher.open(sealed, wrong))
            assertEquals(failure, cipher.open(sealed.changed(sealed.lastIndex), password))
            assertEquals(failure, cipher.open(sealed.changed(sealed.lastIndex - 16), password))
            val metadata = sealed.copyOfRange(14, sealed.size - plaintext.size - 16).decodeToString()
            val saltOffset = metadata.indexOf("\"salt\":\"") + "\"salt\":\"".length + 14
            val saltChanged = sealed.copyOf().also { it[saltOffset] = if (it[saltOffset] == 'A'.code.toByte()) 'B'.code.toByte() else 'A'.code.toByte() }
            assertEquals(failure, cipher.open(saltChanged, password))
        } finally {
            password.close()
            wrong.close()
        }
    }

    @Test
    fun emptyPlaintextStillRequiresCorrectTag() = runTest {
        val cipher = TransferCipherImpl(provider)
        val password = SecretBytes.fromPassword("a\u0000b")
        try {
            val sealed = assertIs<TransferOutcome.Success<ByteArray>>(cipher.seal(byteArrayOf(), password)).value
            assertContentEquals(byteArrayOf(), assertIs<TransferOutcome.Success<ByteArray>>(cipher.open(sealed, password)).value)
            assertEquals(TransferOutcome.Failure(TransferFailure.AuthenticationFailed), cipher.open(sealed.changed(sealed.lastIndex), password))
        } finally {
            password.close()
        }
    }

    @Test
    fun completeEnvelopeMatchesTheIndependentCrossPlatformVector() = runTest {
        val cipher = TransferCipherImpl(provider, randomBytes = { size -> ByteArray(size) { it.toByte() } })
        val password = SecretBytes.fromPassword("Сервис café 🔧 e\u0301")
        val plaintext = "7b22666f726d617456657273696f6e223a312c226e6f7465223a22d09fd0bbd0b0d0bd20d0a2d09e20f09f94a720636166c3a920655c7530333031227d".hexBytes()
        val metadata = """{"cipher":"AES-256-GCM","kdf":"PBKDF2-HMAC-SHA256","iterations":600000,"salt":"AAECAwQFBgcICQoLDA0ODw==","nonce":"AAECAwQFBgcICQoL","tagBytes":16}""".encodeToByteArray()
        val body = "369a1d394222aa5cfc1e003dfb41d1c6f752e453078b85025e5b1e15b00ef193635ec0674ed0b2865bb7b30d59e88850e55b83d1aefb0f1f9c14532e9487fbbb092f9b5e6bce0f96d8b1b767cb".hexBytes()
        val expected = "495434530101000000920000004d".hexBytes() + metadata + body
        try {
            assertContentEquals(expected, assertIs<TransferOutcome.Success<ByteArray>>(cipher.seal(plaintext, password)).value)
            assertContentEquals(plaintext, assertIs<TransferOutcome.Success<ByteArray>>(cipher.open(expected, password)).value)
        } finally {
            password.close()
        }
    }

    @Test
    fun fullBackupAndPersonalCarRoundTripThroughCodecAndBothEnvelopes() = runTest {
        val codec = TransferCodecImpl()
        val cipher = TransferCipherImpl(provider)
        val car = TransferFixtures.car()
        val personal = car.copy(includedGroups = setOf(TransferGroup.VIN), cars = listOf(car.cars.single().copy(vin = Presence.Present("TEST-VIN"))))
        val password = SecretBytes.fromPassword("test password")
        try {
            listOf(TransferFixtures.backup(), personal).forEach { snapshot ->
                val payload = assertIs<TransferOutcome.Success<ByteArray>>(codec.encode(snapshot)).value
                val plain = TransferEnvelope.plain(payload)
                assertEquals(TransferOutcome.Success(snapshot), codec.decode(TransferEnvelope.parse(plain, currentCoroutineContext()).body))
                val encrypted = assertIs<TransferOutcome.Success<ByteArray>>(cipher.seal(payload, password)).value
                val decoded = assertIs<TransferOutcome.Success<ByteArray>>(cipher.open(encrypted, password)).value
                assertEquals(TransferOutcome.Success(snapshot), codec.decode(decoded))
                decoded.fill(0)
                payload.fill(0)
            }
        } finally {
            password.close()
        }
    }

    @Test
    fun originalMetadataBytesAreAuthenticatedWithoutReserialization() = runTest {
        val cipher = TransferCipherImpl(provider)
        val password = SecretBytes.fromPassword("password")
        try {
            val sealed = assertIs<TransferOutcome.Success<ByteArray>>(cipher.seal(plaintext, password)).value
            val parsed = TransferEnvelope.parse(sealed, currentCoroutineContext())
            val reordered = JsonObject(parsed.metadata.entries.reversed().associate { it.key to it.value })
            val metadata = StrictTransferJson.encode(reordered, currentCoroutineContext())
            val altered = TransferEnvelope.header(1, metadata, parsed.body.size) + parsed.body
            assertEquals(sealed.size, altered.size)
            assertEquals(TransferOutcome.Failure(TransferFailure.AuthenticationFailed), cipher.open(altered, password))
        } finally {
            password.close()
        }
    }

    @Test
    fun invalidParametersAreRejectedBeforeBorrowingThePassword() = runTest {
        val cipher = TransferCipherImpl(provider)
        val password = SecretBytes.fromPassword("password")
        val sealed = try { assertIs<TransferOutcome.Success<ByteArray>>(cipher.seal(plaintext, password)).value } finally { password.close() }
        val parsed = TransferEnvelope.parse(sealed, currentCoroutineContext())
        val cases = listOf(
            parsed.metadata + ("iterations" to JsonPrimitive(1)) to TransferFailure.UnsupportedProtection,
            parsed.metadata + ("cipher" to JsonPrimitive("AES-128-GCM")) to TransferFailure.UnsupportedProtection,
            parsed.metadata + ("salt" to JsonPrimitive("AA==")) to TransferFailure.InvalidEnvelope,
            parsed.metadata + ("salt" to JsonPrimitive(1)) to TransferFailure.InvalidEnvelope,
            parsed.metadata + ("extra" to JsonPrimitive(true)) to TransferFailure.InvalidEnvelope,
        )
        cases.forEach { (fields, failure) ->
            val metadata = StrictTransferJson.encode(JsonObject(fields), currentCoroutineContext())
            val bytes = TransferEnvelope.header(1, metadata, parsed.body.size) + parsed.body
            assertEquals(TransferOutcome.Failure(failure), cipher.open(bytes, password))
        }
    }

    @Test
    fun limitsAndMalformedHeadersFailBeforeDerivation() = runTest {
        val cipher = TransferCipherImpl(provider)
        val password = SecretBytes.fromPassword("password")
        try {
            assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), cipher.seal(ByteArray(TransferLimits.MAX_PLAINTEXT_BYTES + 1), password))
            assertEquals(TransferOutcome.Failure(TransferFailure.InvalidEnvelope), cipher.open(byteArrayOf(), password))
            val sealed = assertIs<TransferOutcome.Success<ByteArray>>(cipher.seal(plaintext, password)).value
            assertEquals(TransferOutcome.Failure(TransferFailure.InvalidEnvelope), cipher.open(sealed + 0, password))
            assertEquals(TransferOutcome.Failure(TransferFailure.UnsupportedVersion), cipher.open(sealed.copyOf().also { it[4] = 2 }, password))
            assertEquals(TransferOutcome.Failure(TransferFailure.UnsupportedProtection), cipher.open(sealed.copyOf().also { it[5] = 2 }, password))
        } finally {
            password.close()
        }
    }

    @Test
    fun passwordValidationPreservesExactUtf8AndClosePreventsReuse() {
        assertFailsWith<IllegalArgumentException> { SecretBytes.fromPassword("") }
        assertFailsWith<IllegalArgumentException> { SecretBytes.fromPassword("\uD800") }
        assertFailsWith<IllegalArgumentException> { SecretBytes.fromPassword("я".repeat(513)) }
        SecretBytes.fromPassword("я".repeat(512)).close()
        SecretBytes.fromPassword(" ").close()
        val password = SecretBytes.fromPassword(" password e\u0301 ")
        assertContentEquals(" password e\u0301 ".encodeToByteArray(), password.copyBytes())
        assertEquals("SecretBytes", password.toString())
        password.close()
        password.close()
        assertFailsWith<IllegalStateException> { password.copyBytes() }
    }

    private fun ByteArray.changed(index: Int): ByteArray = copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
