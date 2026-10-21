package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [GetNowUseCase] and [GetTodayUseCase]: the moment is read from the given clock and shown in the given time
 * zone, so the same moment can be another day in another zone.
 */
class TimeUseCasesTest {

    private val lateEveningUtc = fixedClock(LocalDateTime(2026, 12, 31, 22, 30))
    private val moscow = TimeZone.of("Europe/Moscow")

    @Test
    fun nowIsTheClockMomentInTheTimeZone() {
        assertEquals(LocalDateTime(2026, 12, 31, 22, 30), GetNowUseCase(lateEveningUtc) { TimeZone.UTC }())
        assertEquals(LocalDateTime(2027, 1, 1, 1, 30), GetNowUseCase(lateEveningUtc) { moscow }())
    }

    @Test
    fun todayDependsOnTheTimeZoneAroundMidnight() {
        assertEquals(LocalDate(2026, 12, 31), GetTodayUseCase(lateEveningUtc) { TimeZone.UTC }())
        assertEquals(LocalDate(2027, 1, 1), GetTodayUseCase(lateEveningUtc) { moscow }())
    }

    @Test
    fun timeZoneIsReadOnEveryCall() {
        var zone: TimeZone = TimeZone.UTC
        val getToday = GetTodayUseCase(lateEveningUtc) { zone }

        assertEquals(LocalDate(2026, 12, 31), getToday())
        zone = moscow
        assertEquals(LocalDate(2027, 1, 1), getToday())
    }
}
