package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.datetime.TimeZone
import ru.kuznetsov.itstime4service.domain.transfer.TransferReminder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TransferReminderCollectorTest {
    private val now = Instant.parse("2026-11-16T12:00:00.000Z")

    @Test
    fun exactOverlapInstantsKeepTheirOrderAndFutureRowsAreIgnored() {
        val collector = TransferReminderCollector(listOf("t"), now, now, TimeZone.of("America/New_York"))
        collector.add("t", "2026-11-01T01:50", "2026-11-01T05:50:00Z")
        collector.add("t", "2026-11-01T01:10", "2026-11-01T06:10:00Z")
        collector.add("t", "2026-12-01T01:10", "2026-12-01T06:10:00Z")
        assertEquals(listOf(TransferReminder("t", Instant.parse("2026-11-01T06:10:00Z"))), collector.result())
    }

    @Test
    fun legacyGapAndOverlapUseExistingConversionPolicy() {
        listOf("2026-03-08T02:30" to "2026-03-08T07:30:00Z", "2026-11-01T01:30" to "2026-11-01T05:30:00Z").forEach { (local, utc) ->
            val collector = TransferReminderCollector(listOf("t"), now, now, TimeZone.of("America/New_York"))
            collector.add("t", local, null)
            assertEquals(Instant.parse(utc), collector.result().single().shownAt)
        }
    }

    @Test
    fun exactTraceSurvivesBothDirectionsAndGapBoundaryWithoutReinterpretingLocalText() {
        listOf("America/New_York", "Europe/Moscow").forEach { zone ->
            val collector = TransferReminderCollector(listOf("t"), now, now, TimeZone.of(zone))
            collector.add("t", "2026-03-08T02:30", "2026-03-08T07:30:00Z")
            assertEquals(Instant.parse("2026-03-08T07:30:00Z"), collector.result().single().shownAt)
        }
    }
}
