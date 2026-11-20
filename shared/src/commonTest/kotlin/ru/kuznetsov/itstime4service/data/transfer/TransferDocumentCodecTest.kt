package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.test.runTest
import ru.kuznetsov.itstime4service.domain.transfer.*
import ru.kuznetsov.itstime4service.ui.transfer.TransferFileCheckFixtures
import ru.kuznetsov.itstime4service.ui.transfer.transferFileCheckCipher
import kotlin.test.*

/** Verifies whole-file protection detection, validation and cross-provider document round trips. */
class TransferDocumentCodecTest {
    private fun documents() = TransferDocumentCodecImpl(TransferCodecImpl(), transferFileCheckCipher())

    @Test
    fun plainFixtureIsDetectedAndDecodedWithoutPassword() = runTest {
        val bytes = TransferFileCheckFixtures.plain()
        assertEquals(TransferOutcome.Success(TransferProtection.PLAIN), documents().inspect(bytes))
        val snapshot = assertIs<TransferOutcome.Success<TransferSnapshot>>(documents().decode(bytes, null)).value
        assertEquals(TransferKind.CAR, snapshot.kind)
        assertEquals("Synthetic car", snapshot.cars.single().brand)
        assertEquals(TransferOutcome.Success(TransferProtection.PLAIN), documents().inspect(bytes))
    }

    @Test
    fun protectedFixtureDoesNotRevealItsSnapshotBeforeUnlock() = runTest {
        val bytes = TransferFileCheckFixtures.protected()
        assertEquals(TransferOutcome.Success(TransferProtection.PASSWORD), documents().inspect(bytes))
        assertEquals(TransferOutcome.Failure(TransferFailure.AuthenticationFailed), documents().decode(bytes, null))
        SecretBytes.fromPassword("Synthetic-file-check").use { secret ->
            val snapshot = assertIs<TransferOutcome.Success<TransferSnapshot>>(documents().decode(bytes, secret)).value
            assertEquals("Synthetic car", snapshot.cars.single().brand)
        }
    }

    @Test
    fun exportedPlainAndProtectedDocumentsRoundTrip() = runTest {
        val codec = documents()
        val source = assertIs<TransferOutcome.Success<TransferSnapshot>>(codec.decode(TransferFileCheckFixtures.plain(), null)).value
        val plain = assertIs<TransferOutcome.Success<ByteArray>>(codec.encode(source, null)).value
        assertEquals(TransferOutcome.Success(source), codec.decode(plain, null))
        SecretBytes.fromPassword("Synthetic unicode ñ Тест").use { secret ->
            val protected = assertIs<TransferOutcome.Success<ByteArray>>(codec.encode(source, secret)).value
            assertEquals(TransferOutcome.Success(TransferProtection.PASSWORD), codec.inspect(protected))
            assertEquals(TransferOutcome.Success(source), codec.decode(protected, secret))
            protected.fill(0)
        }
        plain.fill(0)
    }

    @Test
    fun invalidAndFutureHeadersAreRejectedBeforeDecoding() = runTest {
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidEnvelope), documents().inspect(byteArrayOf(1, 2, 3)))
        val future = TransferFileCheckFixtures.plain().also { it[4] = 2 }
        assertEquals(TransferOutcome.Failure(TransferFailure.UnsupportedVersion), documents().inspect(future))
        val oversized = ByteArray(TransferLimits.MAX_FILE_BYTES + 1)
        assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), documents().inspect(oversized))
    }
}
