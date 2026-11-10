package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.ensureActive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferLimits
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalSerializationApi::class)
internal object StrictTransferJson {
    private val json = Json {
        exceptionsWithDebugInfo = false
        maxNestingDepth = TransferLimits.MAX_JSON_DEPTH
    }

    fun decode(bytes: ByteArray, context: CoroutineContext, maximum: Int = TransferLimits.MAX_PLAINTEXT_BYTES): JsonElement {
        if (bytes.size > maximum) throw SnapshotRejected(TransferFailure.LimitExceeded)
        return Reader(bytes.decodeToString(throwOnInvalidSequence = true), context).read()
    }

    fun encode(value: JsonElement, context: CoroutineContext, maximum: Int = TransferLimits.MAX_PLAINTEXT_BYTES): ByteArray {
        val output = StringBuilder()
        var bytes = 0
        fun append(token: String) {
            val size = token.encodeToByteArray(throwOnInvalidSequence = true).size
            if (size > maximum - bytes) throw SnapshotRejected(TransferFailure.LimitExceeded)
            bytes += size
            output.append(token)
        }
        fun write(element: JsonElement) {
            context.ensureActive()
            when (element) {
                is JsonObject -> {
                    append("{")
                    element.entries.forEachIndexed { index, (key, item) ->
                        if (index != 0) append(",")
                        append(JsonPrimitive(key).toString())
                        append(":")
                        write(item)
                    }
                    append("}")
                }
                is JsonArray -> {
                    append("[")
                    element.forEachIndexed { index, item ->
                        if (index != 0) append(",")
                        write(item)
                    }
                    append("]")
                }
                is JsonPrimitive -> append(element.toString())
            }
        }
        write(value)
        return output.toString().encodeToByteArray(throwOnInvalidSequence = true)
    }

    private class Reader(private val text: String, private val context: CoroutineContext) {
        private var position = 0
        private val totals = mutableMapOf<String, Int>()

        fun read(): JsonElement {
            val value = value(0, "")
            whitespace()
            valid(position == text.length)
            return value
        }

        private fun value(depth: Int, field: String): JsonElement {
            context.ensureActive()
            whitespace()
            valid(position < text.length)
            return when (text[position]) {
                '{' -> {
                    checkDepth(depth)
                    position++
                    val fields = linkedMapOf<String, JsonElement>()
                    whitespace()
                    if (!take('}')) {
                        do {
                            whitespace()
                            val key = string()
                            valid(key !in fields)
                            valid(fields.size < 10)
                            whitespace()
                            valid(take(':'))
                            fields[key] = value(depth + 1, key)
                            whitespace()
                        } while (take(','))
                        valid(take('}'))
                    }
                    JsonObject(fields)
                }
                '[' -> {
                    checkDepth(depth)
                    position++
                    val values = mutableListOf<JsonElement>()
                    whitespace()
                    if (!take(']')) {
                        do {
                            count(field, values.size)
                            values.add(value(depth + 1, ""))
                            whitespace()
                        } while (take(','))
                        valid(take(']'))
                    }
                    JsonArray(values)
                }
                '"' -> JsonPrimitive(string())
                't' -> literal("true", JsonPrimitive(true))
                'f' -> literal("false", JsonPrimitive(false))
                'n' -> literal("null", JsonNull)
                else -> {
                    val start = position
                    while (position < text.length && text[position] !in " \t\n\r,]}") {
                        valid(position - start < 11)
                        position++
                    }
                    val token = text.substring(start, position)
                    valid(INTEGER.matches(token))
                    JsonPrimitive(requireNotNull(token.toLongOrNull()))
                }
            }
        }

        private fun string(): String {
            val start = position
            valid(take('"'))
            while (position < text.length) {
                if (position - start > TransferLimits.MAX_DESCRIPTION_BYTES * 6 + 2) throw SnapshotRejected(TransferFailure.LimitExceeded)
                val char = text[position++]
                valid(char >= ' ')
                when (char) {
                    '\\' -> {
                        valid(position < text.length)
                        position++
                    }
                    '"' -> {
                        val result = json.parseToJsonElement(text.substring(start, position)) as JsonPrimitive
                        if (result.content.encodeToByteArray(throwOnInvalidSequence = true).size > TransferLimits.MAX_DESCRIPTION_BYTES) {
                            throw SnapshotRejected(TransferFailure.LimitExceeded)
                        }
                        return result.content
                    }
                }
            }
            throw SnapshotRejected(TransferFailure.InvalidSnapshot)
        }

        private fun literal(token: String, result: JsonElement): JsonElement {
            valid(text.startsWith(token, position))
            position += token.length
            return result
        }

        private fun count(field: String, localCount: Int) {
            val maximum = when (field) {
                "cars" -> TransferLimits.MAX_CARS
                "tasks" -> TransferLimits.MAX_TASKS
                "history" -> TransferLimits.MAX_HISTORY
                "reminderState" -> TransferLimits.MAX_REMINDERS
                else -> TransferLimits.MAX_HISTORY
            }
            val next = if (field in AGGREGATE_FIELDS) totals.getOrElse(field) { 0 } + 1 else localCount + 1
            if (next > maximum) throw SnapshotRejected(TransferFailure.LimitExceeded)
            totals[field] = next
        }

        private fun checkDepth(depth: Int) {
            if (depth >= TransferLimits.MAX_JSON_DEPTH) throw SnapshotRejected(TransferFailure.LimitExceeded)
        }

        private fun take(char: Char): Boolean = if (position < text.length && text[position] == char) {
            position++
            true
        } else false

        private fun whitespace() {
            while (position < text.length && text[position] in " \t\n\r") position++
        }

        private fun valid(condition: Boolean) {
            if (!condition) throw SnapshotRejected(TransferFailure.InvalidSnapshot)
        }

        private companion object {
            val INTEGER = Regex("-?(0|[1-9][0-9]*)")
            val AGGREGATE_FIELDS = setOf("cars", "tasks", "history", "reminderState")
        }
    }
}
