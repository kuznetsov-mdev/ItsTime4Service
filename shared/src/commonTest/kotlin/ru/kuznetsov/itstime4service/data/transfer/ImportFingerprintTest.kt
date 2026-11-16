package ru.kuznetsov.itstime4service.data.transfer

import ru.kuznetsov.itstime4service.domain.transfer.*
import kotlin.test.*

class ImportFingerprintTest {
    @Test
    fun nativeHashMatchesStandardSha256Vector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", transferSha256("abc".encodeToByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') })
    }

    @Test
    fun groupAndDecisionSetMapInsertionOrdersDoNotChangeDigest() {
        val source = TransferFixtures.backup()
        assertEquals(ImportFingerprint.snapshot(source), ImportFingerprint.snapshot(source.copy(includedGroups = source.includedGroups.reversed().toSet())))
        val a = EntityRef("a", "t")
        val b = EntityRef("b", "t")
        val one = ImportDecisions(carTargets = linkedMapOf("a" to TargetCar.Existing("1"), "b" to TargetCar.Existing("2")),
            selectedTasks = linkedSetOf(a, b), taskChoices = linkedMapOf(a to TaskChoice.SKIP, b to TaskChoice.UPDATE_PARAMETERS),
            taskTargets = linkedMapOf(a to "3", b to "4"), personalChoices = linkedMapOf("x" to PersonalChoice.KEEP_LOCAL, "y" to PersonalChoice.TAKE_INCOMING))
        val two = one.copy(carTargets = one.carTargets.entries.reversed().associate { it.toPair() }, selectedTasks = linkedSetOf(b, a),
            taskChoices = one.taskChoices.entries.reversed().associate { it.toPair() }, taskTargets = one.taskTargets.entries.reversed().associate { it.toPair() },
            personalChoices = one.personalChoices.entries.reversed().associate { it.toPair() })
        assertEquals(ImportFingerprint.decisions(ImportMode.MERGE_BACKUP, one), ImportFingerprint.decisions(ImportMode.MERGE_BACKUP, two))
    }

    @Test
    fun absentNullEmptyZeroAndListOrderHaveDifferentDigests() {
        val source = TransferFixtures.car()
        val nullVin = source.copy(includedGroups = setOf(TransferGroup.VIN), cars = source.cars.map { it.copy(vin = Presence.Present(null)) })
        val emptyVin = nullVin.copy(cars = nullVin.cars.map { it.copy(vin = Presence.Present("")) })
        assertNotEquals(ImportFingerprint.snapshot(source), ImportFingerprint.snapshot(nullVin))
        assertNotEquals(ImportFingerprint.snapshot(emptyVin), ImportFingerprint.snapshot(nullVin))
        val backup = TransferFixtures.backup()
        val car = backup.cars.single()
        val reversed = backup.copy(cars = listOf(car.copy(history = Presence.Present(car.history.required().reversed()))))
        assertNotEquals(ImportFingerprint.snapshot(backup), ImportFingerprint.snapshot(reversed))
        val explicitZero = backup.copy(cars = listOf(car.copy(tasks = car.tasks.map { it.copy(lastService = Presence.Present(LastService(0, null))) })))
        assertNotEquals(ImportFingerprint.snapshot(backup), ImportFingerprint.snapshot(explicitZero))
    }

    @Test
    fun lengthPrefixAndDatabaseProvenancePreventAmbiguousConcatenation() {
        assertNotEquals(ImportFingerprint.key("ab", "c", "d"), ImportFingerprint.key("a", "bc", "d"))
        val p = ImportProvenance("source", "digest", "task", "t1", "car", "local", "instance")
        val rows = listOf(listOf(listOf<Any?>("task", "2026-11-16T12:00")))
        val original = ImportFingerprint.database(rows, listOf(p), "UTC")
        assertNotEquals(original, ImportFingerprint.database(rows, listOf(p.copy(localId = "other")), "UTC"))
        assertNotEquals(original, ImportFingerprint.database(rows, listOf(p), "Europe/Moscow"))
        assertNotEquals(original, ImportFingerprint.database(listOf(listOf(listOf<Any?>("task", "2026-12-16T12:00"))), listOf(p), "UTC"))
    }

    @Test
    fun localAllocationsAreStableCanonicalUuidAndContextBound() {
        val first = ImportFingerprint.localId("source", "database", "1")
        assertEquals(first, ImportFingerprint.localId("source", "database", "1"))
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(first))
        assertNotEquals(first, ImportFingerprint.localId("source", "changed", "1"))
        assertNotEquals(first, ImportFingerprint.localId("source", "database", "2"))
    }
    @Test
    fun snapshotDigestMatchesIndependentVersionedEncodingVector() {
        assertEquals("703db4861518c018f33959618e1c2e1366e0728a290ce4c4bc3fe94ca7039287", ImportFingerprint.snapshot(TransferFixtures.car()))
    }

}
