package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.kuznetsov.itstime4service.domain.transfer.*

internal object ImportFingerprint {
    fun snapshot(snapshot: TransferSnapshot): String = hash("snapshot-v1") { json(TransferDocument.encode(snapshot)) }

    fun decisions(mode: ImportMode, decisions: ImportDecisions): String = hash("decisions-v1") {
        text(mode.name)
        list(decisions.carTargets.entries.sortedBy { it.key }) { (id, target) ->
            text(id)
            when (target) {
                is TargetCar.Existing -> { text("existing"); text(target.localCarId) }
                is TargetCar.New -> { text("new"); text(target.brand); int(target.confirmedMileageKm) }
            }
        }
        list(decisions.selectedTasks.sortedWith(compareBy({ it.carId }, { it.entityId }))) { ref(it) }
        list(decisions.taskChoices.entries.sortedWith(compareBy({ it.key.carId }, { it.key.entityId }))) { ref(it.key); text(it.value.name) }
        list(decisions.taskTargets.entries.sortedWith(compareBy({ it.key.carId }, { it.key.entityId }))) { ref(it.key); text(it.value) }
        listOf(decisions.personalChoices, decisions.relatedChoices, decisions.soldChoices).forEach { choices ->
            list(choices.entries.sortedBy { it.key }) { text(it.key); text(it.value.name) }
        }
        bool(decisions.restoreSettings)
        text(decisions.backupSafety.name)
    }

    fun changes(changes: ImportChangeSet): String = hash("changes-v1") {
        list(changes.cars) { json(TransferDocument.encode(carSnapshot(it))) }
        json(TransferDocument.encode(settingsSnapshot(changes.settings)))
        list(changes.reminders) { text(it.taskId); long(it.shownAt.toEpochMilliseconds()) }
        provenance(changes.provenance)
        list(changes.carTargets.entries.sortedBy { it.key }) { text(it.key); text(it.value) }
        list(changes.taskTargets.entries.sortedWith(compareBy({ it.key.carId }, { it.key.entityId }))) {
            ref(it.key)
            json(TransferDocument.encode(carSnapshot(TransferCar("target", "", null, Presence.Present(false), Presence.Present(0),
                Presence.Present(null), Presence.Present(null), Presence.Present(CarPreferences(false, false)), listOf(it.value), Presence.Present(emptyList())))))
        }
    }

    fun localId(source: String, database: String, allocation: String): String {
        val hex = key(source, database, "allocation:$allocation")
        val variant = ((hex[16].digitToInt(16) and 3) or 8).toString(16)
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-4${hex.substring(13, 16)}-$variant${hex.substring(17, 20)}-${hex.substring(20, 32)}"
    }

    fun key(source: String, database: String, value: String): String = hash("decision-key-v1") {
        text(source); text(database); text(value)
    }

    fun database(rows: List<List<List<Any?>>>, provenance: List<ImportProvenance>, zone: String): String = hash("database-v1") {
        text(zone)
        list(rows) { table -> list(table) { row -> list(row) { scalar(it) } } }
        provenance(provenance)
    }

    private fun carSnapshot(car: TransferCar) = settingsSnapshot(ru.kuznetsov.itstime4service.domain.model.Settings.DEFAULT).copy(cars = listOf(car))

    private fun settingsSnapshot(settings: ru.kuznetsov.itstime4service.domain.model.Settings) = TransferSnapshot(
        1, "00000000-0000-4000-8000-000000000000", kotlin.time.Instant.fromEpochMilliseconds(0),
        TransferKind.BACKUP, TransferGroup.entries.toSet(), emptyList(), Presence.Present(settings), Presence.Present(emptyList()),
    )

    private fun hash(label: String, encode: Encoding.() -> Unit): String {
        val encoding = Encoding()
        encoding.text(label)
        encoding.encode()
        return transferSha256(encoding.bytes.toByteArray()).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    private class Encoding {
        val bytes = ByteAccumulator()
        fun int(value: Int) {
            for (shift in 24 downTo 0 step 8) bytes.add((value ushr shift).toByte())
        }
        fun long(value: Long) {
            for (shift in 56 downTo 0 step 8) bytes.add((value ushr shift).toByte())
        }
        fun text(value: String) {
            val encoded = value.encodeToByteArray(throwOnInvalidSequence = true)
            bytes.add(1); int(encoded.size); encoded.forEach(bytes::add)
        }
        fun bool(value: Boolean) { bytes.add(if (value) 3 else 2) }
        fun ref(ref: EntityRef) { text(ref.carId); text(ref.entityId) }
        fun <T> list(values: List<T>, encode: (T) -> Unit) {
            bytes.add(4); int(values.size); values.forEach(encode)
        }
        fun provenance(values: List<ImportProvenance>) {
            list(values) {
                text(it.snapshotId); text(it.sourceDigest); text(it.kind); text(it.incomingId)
                text(it.targetCarId); text(it.localId); text(it.instanceId)
            }
        }
        fun scalar(value: Any?) {
            when (value) {
                null -> bytes.add(0)
                is String -> text(value)
                is Long -> { bytes.add(5); long(value) }
                is Boolean -> bool(value)
                else -> error("Unsupported database scalar")
            }
        }
        fun json(value: JsonElement) {
            when (value) {
                is JsonNull -> bytes.add(0)
                is JsonObject -> {
                    bytes.add(6); int(value.size)
                    value.entries.sortedBy { it.key }.forEach { text(it.key); json(it.value) }
                }
                is JsonArray -> list(value) { json(it) }
                is JsonPrimitive -> when {
                    value.isString -> text(value.content)
                    value.content == "true" -> bool(true)
                    value.content == "false" -> bool(false)
                    else -> { bytes.add(5); long(value.content.toLong()) }
                }
            }
        }
    }
}

internal expect fun transferSha256(bytes: ByteArray): ByteArray

internal class ByteAccumulator {
    private var buffer = ByteArray(1024)
    private var size = 0
    fun add(value: Byte) {
        if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
        buffer[size++] = value
    }
    fun toByteArray(): ByteArray = buffer.copyOf(size)
}
