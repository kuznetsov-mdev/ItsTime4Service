package ru.kuznetsov.itstime4service.domain.model

import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

/**
 * A stored trace of a planned notification: which task it is about and when it fires.
 * A record whose [fireAt] is in the past counts as a notification already shown.
 * [shownAt] preserves an imported shown instant across time zone changes and ambiguous local times.
 */
data class ReminderRecord(
    val taskId: String,
    val fireAt: LocalDateTime,
    val shownAt: Instant? = null,
)
