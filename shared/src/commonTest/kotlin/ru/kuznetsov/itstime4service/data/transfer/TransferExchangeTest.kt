package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import ru.kuznetsov.itstime4service.data.repository.TestDatabase
import kotlin.time.Clock
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.domain.transfer.*
import ru.kuznetsov.itstime4service.ui.transfer.transferFileCheckCipher
import kotlin.test.*
import kotlin.time.Instant

internal expect val exchangePlatform: String
internal expect fun writeExchangeArtifact(name: String, bytes: ByteArray)

/** Emits synthetic files from each production provider for independent cross-platform import checks. */
class TransferExchangeTest {
    private fun peer(backup: Boolean): ByteArray {
        val encoded = if (exchangePlatform == "Android") {
            if (backup) TransferPeerFiles.iosBackup else TransferPeerFiles.iosCar
        } else {
            if (backup) TransferPeerFiles.androidBackup else TransferPeerFiles.androidCar
        }
        return encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private suspend fun readPeer(backup: Boolean): TransferSnapshot {
        val bytes = peer(backup)
        return SecretBytes.fromPassword(EXCHANGE_PASSWORD).use { secret ->
            val documents = TransferDocumentCodecImpl(TransferCodecImpl(), transferFileCheckCipher())
            try { assertIs<TransferOutcome.Success<TransferSnapshot>>(documents.decode(bytes, secret)).value }
            finally { bytes.fill(0) }
        }
    }

    @Test fun peerBackupRestoresCarsHistorySettingsAndValidRollbackThroughSQLite() = runTest {
        val source = readPeer(true)
        assertEquals(exchangeBackup(), source)
        val database = TestDatabase().create()
        val clock = object : Clock { override fun now() = Instant.parse("2026-11-16T12:00:00Z") }
        val repository = TransferRepositoryImpl(database.database, clock = clock, timeZone = { TimeZone.UTC })
        try {
            val digest = assertIs<TransferOutcome.Success<String>>(repository.fingerprint(source)).value
            val decisions = ImportDecisions.initial(source).copy(restoreSettings = true, backupSafety = BackupSafety.EXPLICITLY_DECLINED)
            val prepared = assertIs<TransferOutcome.Success<PreparedImport>>(repository.prepareImport(
                ImportRequest(source, digest, ImportMode.REPLACE_BACKUP, decisions))).value
            val plan = assertIs<PreparedImport.Ready>(prepared).plan
            assertTrue(plan.summary.rollbackDisabled.isEmpty())
            assertIs<TransferOutcome.Success<ImportResult>>(repository.applyImport(plan))
            val restored = assertIs<TransferOutcome.Success<TransferSnapshot>>(repository.readSnapshot(ExportSelection.FullBackup)).value
            assertEquals(source.cars, restored.cars)
            assertEquals(source.settings, restored.settings)
            assertEquals(source.reminderState, restored.reminderState)
        } finally { database.driver.close() }
    }

    @Test fun peerTemplateImportsWithExplicitMileageAndUnknownLastService() = runTest {
        val source = readPeer(false)
        assertEquals(TransferFixtures.car(), source)
        val database = TestDatabase().create()
        val repository = TransferRepositoryImpl(database.database)
        try {
            val digest = assertIs<TransferOutcome.Success<String>>(repository.fingerprint(source)).value
            val decisions = ImportDecisions.initial(source).copy(carTargets = mapOf(source.cars.single().id to TargetCar.New("Imported synthetic", 54321)))
            val prepared = assertIs<TransferOutcome.Success<PreparedImport>>(repository.prepareImport(
                ImportRequest(source, digest, ImportMode.INTO_CAR, decisions))).value
            val plan = assertIs<PreparedImport.Ready>(prepared).plan
            assertIs<TransferOutcome.Success<ImportResult>>(repository.applyImport(plan))
            val restored = assertIs<TransferOutcome.Success<TransferSnapshot>>(repository.readSnapshot(ExportSelection.FullBackup)).value
            val car = restored.cars.single()
            assertEquals("Imported synthetic", car.brand)
            assertEquals(Presence.Present(54321), car.mileageKm)
            assertEquals(Presence.Present(null), car.vin)
            assertEquals(Presence.Present(LastService(null, null)), car.tasks.single().lastService)
            assertEquals("Oil", car.tasks.single().name)
            assertEquals(Presence.Present(emptyList()), car.history)
        } finally { database.driver.close() }
    }

    @Test fun emitsBackupAndTemplateUsingTheProductionDocumentCodec() = runTest {
        val documents = TransferDocumentCodecImpl(TransferCodecImpl(), transferFileCheckCipher())
        SecretBytes.fromPassword(EXCHANGE_PASSWORD).use { secret ->
            for ((name, source) in listOf("Backup" to exchangeBackup(), "Car" to TransferFixtures.car())) {
                val bytes = assertIs<TransferOutcome.Success<ByteArray>>(documents.encode(source, secret)).value
                try {
                    assertEquals(TransferOutcome.Success(source), documents.decode(bytes, secret))
                    writeExchangeArtifact(name, bytes)
                } finally { bytes.fill(0) }
            }
        }
    }
}

internal const val EXCHANGE_PASSWORD = "Synthetic exchange ñ Тест"

internal fun exchangeBackup(): TransferSnapshot {
    val original = TransferFixtures.backup()
    val active = original.cars.single()
    val service = (active.tasks.single().lastService as Presence.Present).value
    val sold = active.copy(id = TransferFixtures.id(2), brand = "Synthetic sold car", sold = Presence.Present(true),
        tasks = emptyList(), history = Presence.Present(listOf(
            (active.history as Presence.Present).value.last().copy(id = TransferFixtures.id(103), taskId = TransferFixtures.id(199)))))
    val settings = (original.settings as Presence.Present).value.copy(themeMode = ThemeMode.DARK,
        distanceUnit = DistanceUnit.MILES, openDefaultCar = true, defaultCarId = active.id, lastOpenedCarId = active.id)
    return original.copy(cars = listOf(active.copy(tasks = listOf(active.tasks.single().copy(
        lastService = Presence.Present(service.copy(mileageKm = 80000))))), sold), settings = Presence.Present(settings),
        reminderState = Presence.Present(listOf(TransferReminder(TransferFixtures.id(11), Instant.parse("2026-11-10T00:00:00Z")))))
}
