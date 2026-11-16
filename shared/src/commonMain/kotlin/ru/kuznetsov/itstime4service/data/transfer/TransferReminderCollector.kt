package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import ru.kuznetsov.itstime4service.domain.transfer.TransferReminder
import kotlin.time.Instant

internal class TransferReminderCollector(
    private val taskIds: List<String>,
    private val now: Instant,
    private val createdAt: Instant,
    private val zone: TimeZone,
) {
    private val active = taskIds.toSet()
    private val cutoff = now.toLocalDateTime(zone)
    private val legacy = mutableMapOf<String, LocalDateTime>()
    private val exact = mutableMapOf<String, Instant>()

    fun add(task: String, fireAt: String, shownAt: String?) {
        if (task !in active) return
        if (shownAt != null) {
            val instant = snapshotValue {
                Instant.parse(shownAt).also {
                    require(it.toLocalDateTime(TimeZone.UTC).year in 1..9999)
                    require(it.nanosecondsOfSecond % 1_000_000 == 0)
                }
            }
            if (instant <= now && instant <= createdAt && (exact[task]?.let { instant > it } != false)) exact[task] = instant
            return
        }
        val local = snapshotValue { LocalDateTime.parse(fireAt).also { require(it.year in 1..9999) } }
        if (local > cutoff) return
        val instant = Instant.fromEpochMilliseconds(local.toInstant(zone).toEpochMilliseconds())
        if (instant > createdAt) return
        snapshotValue { require(instant.toLocalDateTime(TimeZone.UTC).year in 1..9999) }
        if (legacy[task]?.let { local > it } != false) legacy[task] = local
    }

    fun result(): List<TransferReminder> = taskIds.mapNotNull { task ->
        val local = legacy[task]?.let { Instant.fromEpochMilliseconds(it.toInstant(zone).toEpochMilliseconds()) }
        listOfNotNull(local, exact[task]).maxOrNull()?.let { TransferReminder(task, it) }
    }
}
