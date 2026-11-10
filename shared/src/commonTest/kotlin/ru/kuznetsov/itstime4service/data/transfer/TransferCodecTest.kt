package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.test.runTest
import ru.kuznetsov.itstime4service.domain.transfer.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class TransferCodecTest {
    private val codec = TransferCodecImpl()

    @Test
    fun bothKindsRoundTripWithoutChangingValuesOrOrder() = runTest {
        listOf(TransferFixtures.car(), TransferFixtures.backup()).forEach { source ->
            val bytes = assertIs<TransferOutcome.Success<ByteArray>>(codec.encode(source)).value
            assertEquals(TransferOutcome.Success(source), codec.decode(bytes))
            assertFalse(bytes.decodeToString().contains("Absent"))
            assertFalse(bytes.decodeToString().contains("Present"))
        }
    }

    @Test
    fun omittedPersonalFieldsAreAbsentAndIncludedNullIsExplicit() = runTest {
        val plain = encoded(TransferFixtures.car())
        listOf("mileageKm", "vin", "plate", "settings", "lastService", "history", "rollback", "reminderState").forEach {
            assertFalse(plain.contains('"' + it + '"'))
        }
        val source = TransferFixtures.car()
        val personal = source.copy(includedGroups = setOf(TransferGroup.VIN), cars = listOf(source.cars.single().copy(vin = Presence.Present(null))))
        assertEquals(TransferOutcome.Success(personal), codec.decode(encoded(personal).encodeToByteArray()))
    }

    @Test
    fun duplicateKeysIncludingEscapedKeysAreRejected() = runTest {
        val valid = encoded(TransferFixtures.car())
        listOf(
            valid.replace("\"formatVersion\":1", "\"formatVersion\":1,\"formatVersion\":1"),
            valid.replace("\"brand\":", "\"br\\u0061nd\":\"Other\",\"brand\":"),
            valid.replace("\"intervalKm\":10000", "\"intervalKm\":10000,\"intervalKm\":10000"),
        ).forEach { assertInvalid(it) }
    }

    @Test
    fun nonJsonAndNonIntegerSyntaxIsRejected() = runTest {
        val valid = encoded(TransferFixtures.car())
        listOf(
            valid.replace("10000", "1e4"), valid.replace("10000", "10000.0"),
            valid.replace("10000", "NaN"), valid.replace("10000", "Infinity"),
            valid.replace("10000", "010000"), valid.replace("10000", "\"10000\""),
            valid.dropLast(1) + ",}", "/* comment */$valid", "\uFEFF$valid", "$valid{}",
            valid.replace("Oil", "\\uD800"),
        ).forEach { assertInvalid(it) }
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), codec.decode(byteArrayOf(0xc3.toByte(), 0x28)))
    }

    @Test
    fun unknownVersionsFieldsEnumsAndForbiddenGroupsAreRejected() = runTest {
        val valid = encoded(TransferFixtures.car())
        assertEquals(TransferOutcome.Failure(TransferFailure.UnsupportedVersion), codec.decode(valid.replace("\"formatVersion\":1", "\"formatVersion\":2").encodeToByteArray()))
        listOf(
            valid.replace("\"brand\":", "\"unknown\":0,\"brand\":"),
            valid.replace("\"droplet\"", "\"unknown\""),
            valid.replace("\"includedGroups\":[]", "\"includedGroups\":[\"vin\"]"),
            valid.replace("\"brand\":", "\"vin\":null,\"brand\":"),
            valid.replace("\"includedGroups\":[]", "\"includedGroups\":[\"preferences\"]"),
            valid.replace("\"intervalKm\":10000", "\"intervalKm\":2147483648"),
            valid.replace("\"intervalKm\":10000", "\"intervalKm\":0"),
        ).forEach { assertInvalid(it) }
    }

    @Test
    fun logicalValidationRejectsInvalidLinksCountsAndPresenceBeforeEncoding() = runTest {
        val source = TransferFixtures.car()
        val invalid = listOf(
            source.copy(cars = emptyList()),
            source.copy(cars = source.cars + source.cars),
            source.copy(cars = listOf(source.cars.single().copy(vin = Presence.Present(null)))),
            source.copy(cars = listOf(source.cars.single().copy(tasks = listOf(source.cars.single().tasks.single().copy(id = "t2"))))),
        )
        invalid.forEach { assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), codec.encode(it)) }
    }

    @Test
    fun excessiveBytesAndDepthFailBeforeDomainConstruction() = runTest {
        assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), codec.decode(ByteArray(TransferLimits.MAX_PLAINTEXT_BYTES + 1)))
        assertEquals(TransferOutcome.Failure(TransferFailure.LimitExceeded), codec.decode(("[".repeat(25) + "0" + "]".repeat(25)).encodeToByteArray()))
    }

    @Test
    fun noncanonicalDatesAndTimesAreRejected() = runTest {
        val valid = encoded(TransferFixtures.backup())
        listOf(
            valid.replace("2026-10-01", "2026-02-30"),
            valid.replace("12:00:00.000Z", "24:00:00.000Z"),
            valid.replace("12:00:00.000Z", "12:00:60.000Z"),
            valid.replace("12:00:00.000Z", "12:00:00Z"),
            valid.replace("09:00:00", "09:00"),
        ).forEach { assertInvalid(it) }
    }

    private suspend fun encoded(source: TransferSnapshot): String = assertIs<TransferOutcome.Success<ByteArray>>(codec.encode(source)).value.decodeToString()

    private suspend fun assertInvalid(source: String) {
        assertEquals(TransferOutcome.Failure(TransferFailure.InvalidSnapshot), codec.decode(source.encodeToByteArray()))
    }
}
