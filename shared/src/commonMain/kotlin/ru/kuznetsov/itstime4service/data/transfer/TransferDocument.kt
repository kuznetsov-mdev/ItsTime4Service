package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.transfer.CarPreferences
import ru.kuznetsov.itstime4service.domain.transfer.HistoryDetails
import ru.kuznetsov.itstime4service.domain.transfer.LastService
import ru.kuznetsov.itstime4service.domain.transfer.Presence
import ru.kuznetsov.itstime4service.domain.transfer.RollbackData
import ru.kuznetsov.itstime4service.domain.transfer.TransferCar
import ru.kuznetsov.itstime4service.domain.transfer.TransferFailure
import ru.kuznetsov.itstime4service.domain.transfer.TransferGroup
import ru.kuznetsov.itstime4service.domain.transfer.TransferHistory
import ru.kuznetsov.itstime4service.domain.transfer.TransferKind
import ru.kuznetsov.itstime4service.domain.transfer.TransferReminder
import ru.kuznetsov.itstime4service.domain.transfer.TransferSnapshot
import ru.kuznetsov.itstime4service.domain.transfer.TransferTask
import kotlin.time.Instant

internal object TransferDocument {
    fun encode(value: TransferSnapshot): JsonObject = obj(
        "formatVersion" to number(value.formatVersion), "snapshotId" to string(value.snapshotId),
        "createdAt" to string(instant(value.createdAt)), "kind" to string(TransferTokens.kind(value.kind)),
        "includedGroups" to JsonArray(TransferGroup.entries.filter { it in value.includedGroups }.map { string(TransferTokens.group(it)) }),
        "cars" to JsonArray(value.cars.map(::car)),
        "settings" to optional(value.settings, ::settings),
        "reminderState" to optional(value.reminderState) { JsonArray(it.map { reminder -> obj("taskId" to string(reminder.taskId), "shownAt" to string(instant(reminder.shownAt))) }) },
    )

    fun decode(value: JsonElement): TransferSnapshot {
        val root = value.objectValue()
        val version = root.required("formatVersion").intValue()
        if (version != 1) throw SnapshotRejected(TransferFailure.UnsupportedVersion)
        val kind = TransferTokens.kind(root.required("kind").stringValue())
        val groupList = root.required("includedGroups").arrayValue().map { TransferTokens.group(it.stringValue()) }
        valid(groupList.distinct().size == groupList.size)
        val groups = groupList.toSet()
        val backup = kind == TransferKind.BACKUP
        root.exact(setOf("formatVersion", "snapshotId", "createdAt", "kind", "includedGroups", "cars") + if (backup) setOf("settings", "reminderState") else emptySet())
        return TransferSnapshot(
            version, root.required("snapshotId").stringValue(), parseInstant(root.required("createdAt")), kind, groups,
            root.required("cars").arrayValue().map { readCar(it, groups, backup) },
            root.presence("settings", backup, ::readSettings),
            root.presence("reminderState", backup) { reminders -> reminders.arrayValue().map { item ->
                val reminder = item.objectValue()
                reminder.exact(setOf("taskId", "shownAt"))
                TransferReminder(reminder.required("taskId").stringValue(), parseInstant(reminder.required("shownAt")))
            } },
        )
    }

    private fun car(value: TransferCar): JsonObject = obj(
        "id" to string(value.id), "brand" to string(value.brand), "maintenanceDescription" to string(value.maintenanceDescription),
        "sold" to optional(value.sold) { JsonPrimitive(it) }, "mileageKm" to optional(value.mileageKm, ::number),
        "vin" to optional(value.vin, ::string), "plate" to optional(value.plate, ::string),
        "preferences" to optional(value.preferences) { obj("plateHidden" to JsonPrimitive(it.plateHidden), "vinHidden" to JsonPrimitive(it.vinHidden)) },
        "tasks" to JsonArray(value.tasks.map { task -> obj(
            "id" to string(task.id), "name" to string(task.name), "intervalKm" to number(task.intervalKm), "intervalMonths" to number(task.intervalMonths),
            "icon" to string(task.icon?.let(TransferTokens::icon)), "lastService" to optional(task.lastService, ::service),
            "reminderWindowKm" to optional(task.reminderWindowKm, ::number),
        ) }),
        "history" to optional(value.history) { JsonArray(it.map { entry -> obj(
            "id" to string(entry.id), "taskId" to string(entry.taskId), "taskName" to string(entry.taskName),
            "date" to string(entry.date.toString()), "mileageKm" to number(entry.mileageKm),
            "details" to optional(entry.details) { details -> obj("costRub" to number(details.costRub), "serviceName" to string(details.serviceName), "comment" to string(details.comment)) },
            "rollback" to optional(entry.rollback) { rollback -> if (rollback == null) JsonNull else obj("previous" to service(rollback.previous)) },
        ) }) },
    )

    private fun service(value: LastService): JsonObject = obj("mileageKm" to number(value.mileageKm), "date" to string(value.date?.toString()))

    private fun settings(value: Settings): JsonObject = obj(
        "themeMode" to string(TransferTokens.theme(value.themeMode)), "distanceUnit" to string(TransferTokens.distance(value.distanceUnit)),
        "pushEnabled" to JsonPrimitive(value.pushEnabled), "defaultReminderWindowKm" to number(value.defaultReminderWindowKm),
        "reminderTime" to string("${two(value.reminderTime.hour)}:${two(value.reminderTime.minute)}:${two(value.reminderTime.second)}"),
        "lastOpenedCarId" to string(value.lastOpenedCarId), "openDefaultCar" to JsonPrimitive(value.openDefaultCar),
        "defaultCarId" to string(value.defaultCarId), "carsView" to string(TransferTokens.carsView(value.carsView)),
    )

    private fun readCar(value: JsonElement, groups: Set<TransferGroup>, backup: Boolean): TransferCar {
        val car = value.objectValue()
        val names = mutableSetOf("id", "brand", "maintenanceDescription", "tasks")
        if (backup) names.addAll(listOf("sold", "preferences"))
        listOf("mileageKm" to TransferGroup.MILEAGE, "vin" to TransferGroup.VIN, "plate" to TransferGroup.PLATE, "history" to TransferGroup.HISTORY).forEach { (name, group) -> if (group in groups) names.add(name) }
        car.exact(names)
        return TransferCar(
            car.required("id").stringValue(), car.required("brand").stringValue(), car.required("maintenanceDescription").nullableString(),
            car.presence("sold", backup) { it.booleanValue() }, car.presence("mileageKm", TransferGroup.MILEAGE in groups) { it.intValue() },
            car.presence("vin", TransferGroup.VIN in groups) { it.nullableString() }, car.presence("plate", TransferGroup.PLATE in groups) { it.nullableString() },
            car.presence("preferences", backup) { element ->
                val prefs = element.objectValue()
                prefs.exact(setOf("plateHidden", "vinHidden"))
                CarPreferences(prefs.required("plateHidden").booleanValue(), prefs.required("vinHidden").booleanValue())
            },
            car.required("tasks").arrayValue().map { readTask(it, groups, backup) },
            car.presence("history", TransferGroup.HISTORY in groups) { it.arrayValue().map { entry -> readHistory(entry, groups, backup) } },
        )
    }

    private fun readTask(value: JsonElement, groups: Set<TransferGroup>, backup: Boolean): TransferTask {
        val task = value.objectValue()
        val names = mutableSetOf("id", "name", "intervalKm", "intervalMonths", "icon")
        if (TransferGroup.LAST_SERVICE in groups) names.add("lastService")
        if (backup) names.add("reminderWindowKm")
        task.exact(names)
        return TransferTask(
            task.required("id").stringValue(), task.required("name").stringValue(), task.required("intervalKm").intValue(),
            task.required("intervalMonths").nullableInt(), task.required("icon").nullableString()?.let(TransferTokens::icon),
            task.presence("lastService", TransferGroup.LAST_SERVICE in groups, ::readService), task.presence("reminderWindowKm", backup) { it.intValue() },
        )
    }

    private fun readHistory(value: JsonElement, groups: Set<TransferGroup>, backup: Boolean): TransferHistory {
        val history = value.objectValue()
        val names = mutableSetOf("id", "taskId", "taskName", "date", "mileageKm")
        if (TransferGroup.HISTORY_DETAILS in groups) names.add("details")
        if (backup) names.add("rollback")
        history.exact(names)
        return TransferHistory(
            history.required("id").stringValue(), history.required("taskId").nullableString(), history.required("taskName").stringValue(),
            parseDate(history.required("date")), history.required("mileageKm").intValue(),
            history.presence("details", TransferGroup.HISTORY_DETAILS in groups) { element ->
                val details = element.objectValue()
                details.exact(setOf("costRub", "serviceName", "comment"))
                HistoryDetails(details.required("costRub").nullableInt(), details.required("serviceName").nullableString(), details.required("comment").nullableString())
            },
            history.presence("rollback", backup) { element -> if (element == JsonNull) null else {
                val rollback = element.objectValue()
                rollback.exact(setOf("previous"))
                RollbackData(readService(rollback.required("previous")))
            } },
        )
    }

    private fun readService(value: JsonElement): LastService {
        val service = value.objectValue()
        service.exact(setOf("mileageKm", "date"))
        return LastService(service.required("mileageKm").nullableInt(), service.required("date").let { if (it == JsonNull) null else parseDate(it) })
    }

    private fun readSettings(value: JsonElement): Settings {
        val settings = value.objectValue()
        settings.exact(setOf("themeMode", "distanceUnit", "pushEnabled", "defaultReminderWindowKm", "reminderTime", "lastOpenedCarId", "openDefaultCar", "defaultCarId", "carsView"))
        val time = settings.required("reminderTime").stringValue()
        valid(TIME.matches(time))
        return Settings(
            TransferTokens.theme(settings.required("themeMode").stringValue()), TransferTokens.distance(settings.required("distanceUnit").stringValue()),
            settings.required("pushEnabled").booleanValue(), settings.required("defaultReminderWindowKm").intValue(), LocalTime.parse(time),
            settings.required("lastOpenedCarId").nullableString(), settings.required("openDefaultCar").booleanValue(),
            settings.required("defaultCarId").nullableString(), TransferTokens.carsView(settings.required("carsView").stringValue()),
        )
    }

    private fun instant(value: Instant): String {
        val local = value.toLocalDateTime(TimeZone.UTC)
        return "${local.date}T${two(local.hour)}:${two(local.minute)}:${two(local.second)}.${(local.nanosecond / 1_000_000).toString().padStart(3, '0')}Z"
    }

    private fun parseInstant(value: JsonElement): Instant {
        val text = value.stringValue()
        valid(INSTANT.matches(text))
        return LocalDateTime.parse(text.dropLast(1)).toInstant(TimeZone.UTC)
    }

    private fun parseDate(value: JsonElement): LocalDate {
        val text = value.stringValue()
        valid(DATE.matches(text))
        return LocalDate.parse(text)
    }

    private fun two(value: Int): String = value.toString().padStart(2, '0')
    private fun string(value: String?): JsonElement = value?.let { JsonPrimitive(it) } ?: JsonNull
    private fun number(value: Int?): JsonElement = value?.let { JsonPrimitive(it) } ?: JsonNull
    private fun obj(vararg fields: Pair<String, JsonElement?>): JsonObject = JsonObject(fields.mapNotNull { (key, value) -> value?.let { key to it } }.toMap())
    private fun <T> optional(value: Presence<T>, encode: (T) -> JsonElement): JsonElement? = when (value) {
        Presence.Absent -> null
        is Presence.Present -> encode(value.value)
    }

    private fun JsonElement.objectValue(): JsonObject = this as? JsonObject ?: invalid()
    private fun JsonElement.arrayValue(): JsonArray = this as? JsonArray ?: invalid()
    private fun JsonElement.stringValue(): String = (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()
    private fun JsonElement.nullableString(): String? = if (this == JsonNull) null else stringValue()
    private fun JsonElement.intValue(): Int = (this as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toIntOrNull() ?: invalid()
    private fun JsonElement.nullableInt(): Int? = if (this == JsonNull) null else intValue()
    private fun JsonElement.booleanValue(): Boolean = when ((this as? JsonPrimitive)?.takeUnless { it.isString }?.content) {
        "true" -> true
        "false" -> false
        else -> invalid()
    }
    private fun JsonObject.required(field: String): JsonElement = get(field) ?: invalid()
    private fun JsonObject.exact(fields: Set<String>) = valid(keys == fields)
    private fun <T> JsonObject.presence(field: String, included: Boolean, read: (JsonElement) -> T): Presence<T> =
        if (included) Presence.Present(read(required(field))) else Presence.Absent
    private fun valid(value: Boolean) { if (!value) invalid() }
    private fun invalid(): Nothing = throw SnapshotRejected(TransferFailure.InvalidSnapshot)

    private val DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val TIME = Regex("[0-9]{2}:[0-9]{2}:[0-9]{2}")
    private val INSTANT = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z")
}
