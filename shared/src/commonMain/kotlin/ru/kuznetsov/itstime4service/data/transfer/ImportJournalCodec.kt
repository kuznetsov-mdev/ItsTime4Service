package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.serialization.json.*
import ru.kuznetsov.itstime4service.domain.transfer.EntityRef
import ru.kuznetsov.itstime4service.domain.transfer.ImportSummary

internal object ImportJournalCodec {
    fun encode(value: ImportSummary): String = JsonArray(listOf(
        JsonArray(listOf(value.carsAdded, value.carsUpdated, value.carsRemoved, value.tasksAdded, value.tasksUpdated,
            value.tasksRemoved, value.historyAdded, value.historyUpdated, value.historyRemoved, value.skipped).map(::JsonPrimitive)),
        JsonPrimitive(value.settingsRestored),
        JsonArray(value.rollbackDisabled.sortedWith(compareBy({ it.carId }, { it.entityId }))
            .map { JsonArray(listOf(JsonPrimitive(it.carId), JsonPrimitive(it.entityId))) }),
    )).toString()

    fun decode(value: String): ImportSummary {
        val root = Json.parseToJsonElement(value).jsonArray
        val n = root[0].jsonArray.map { it.jsonPrimitive.int }
        val refs = root[2].jsonArray.map { it.jsonArray.let { pair -> EntityRef(pair[0].jsonPrimitive.content, pair[1].jsonPrimitive.content) } }.toSet()
        return ImportSummary(n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7], n[8], n[9], root[1].jsonPrimitive.boolean, refs)
    }
}
