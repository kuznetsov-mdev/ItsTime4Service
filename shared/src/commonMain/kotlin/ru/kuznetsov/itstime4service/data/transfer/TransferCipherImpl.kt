package ru.kuznetsov.itstime4service.data.transfer

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.kuznetsov.itstime4service.domain.transfer.SecretBytes
import ru.kuznetsov.itstime4service.domain.transfer.TransferCipher
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits
import ru.kuznetsov.itstime4service.domain.transfer.TransferOutcome
import kotlin.io.encoding.Base64

@OptIn(DelicateCryptographyApi::class)
class TransferCipherImpl(
    private val provider: CryptographyProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val randomBytes: (Int) -> ByteArray = { CryptographyRandom.nextBytes(it) },
) : TransferCipher {
    override suspend fun seal(plaintext: ByteArray, password: SecretBytes): TransferOutcome<ByteArray> =
        onWorker(TransferFailure.WriteFailed) {
            if (plaintext.size > TransferLimits.MAX_PLAINTEXT_BYTES) throw SnapshotRejected(TransferFailure.LimitExceeded)
            val salt = randomBytes(16)
            val nonce = randomBytes(12)
            require(salt.size == 16 && nonce.size == 12)
            val metadata = JsonObject(linkedMapOf(
                "cipher" to JsonPrimitive("AES-256-GCM"), "kdf" to JsonPrimitive("PBKDF2-HMAC-SHA256"),
                "iterations" to JsonPrimitive(600000), "salt" to JsonPrimitive(Base64.Default.encode(salt)),
                "nonce" to JsonPrimitive(Base64.Default.encode(nonce)), "tagBytes" to JsonPrimitive(16),
            ))
            val context = currentCoroutineContext()
            val metadataBytes = StrictTransferJson.encode(metadata, context, TransferLimits.MAX_METADATA_BYTES)
            val header = TransferEnvelope.header(1, metadataBytes, plaintext.size + 16)
            withKey(password, salt) { key ->
                context.ensureActive()
                header + cipher(key).encryptWithIvBlocking(nonce, plaintext, header)
            }
        }

    override suspend fun open(envelope: ByteArray, password: SecretBytes): TransferOutcome<ByteArray> =
        onWorker(TransferFailure.AuthenticationFailed) {
            val context = currentCoroutineContext()
            val parsed = TransferEnvelope.parse(envelope, context)
            if (parsed.protection != 1) throw SnapshotRejected(TransferFailure.UnsupportedProtection)
            val (salt, nonce) = parameters(parsed.metadata)
            withKey(password, salt) { key ->
                context.ensureActive()
                cipher(key).decryptWithIvBlocking(nonce, parsed.body, parsed.authenticatedHeader)
            }
        }

    private inline fun <T> withKey(password: SecretBytes, salt: ByteArray, use: (ByteArray) -> T): T {
        val input = password.copyBytes()
        var derived: ByteArray? = null
        return try {
            val key = provider.get(PBKDF2).secretDerivation(SHA256, 600000, 32.bytes, salt).deriveSecretToByteArrayBlocking(input)
            derived = key
            use(key)
        } finally {
            input.fill(0)
            derived?.fill(0)
        }
    }

    private fun cipher(key: ByteArray) = provider.get(AES.GCM).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key).cipher()

    private fun parameters(metadata: JsonObject): Pair<ByteArray, ByteArray> {
        if (metadata.keys != setOf("cipher", "kdf", "iterations", "salt", "nonce", "tagBytes")) invalidMetadata()
        fun text(field: String): String = (metadata[field] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalidMetadata()
        fun number(field: String): Int = (metadata[field] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toIntOrNull() ?: invalidMetadata()
        if (text("cipher") != "AES-256-GCM" || text("kdf") != "PBKDF2-HMAC-SHA256" || number("iterations") != 600000 || number("tagBytes") != 16) {
            throw SnapshotRejected(TransferFailure.UnsupportedProtection)
        }
        fun base64(field: String, size: Int): ByteArray {
            val source = text(field)
            val decoded = try { Base64.Default.decode(source) } catch (_: IllegalArgumentException) { invalidMetadata() }
            if (decoded.size != size || Base64.Default.encode(decoded) != source) invalidMetadata()
            return decoded
        }
        return base64("salt", 16) to base64("nonce", 12)
    }

    private fun invalidMetadata(): Nothing = throw SnapshotRejected(TransferFailure.InvalidEnvelope)

    private suspend fun onWorker(failure: TransferFailure, block: suspend () -> ByteArray): TransferOutcome<ByteArray> {
        var output: ByteArray? = null
        return try {
            withContext(dispatcher) {
                try {
                    val result = block()
                    output = result
                    currentCoroutineContext().ensureActive()
                    TransferOutcome.Success(result)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (rejected: SnapshotRejected) {
                    TransferOutcome.Failure(rejected.reason)
                } catch (_: Exception) {
                    TransferOutcome.Failure(failure)
                }
            }
        } catch (cancelled: CancellationException) {
            output?.fill(0)
            throw cancelled
        }
    }
}
