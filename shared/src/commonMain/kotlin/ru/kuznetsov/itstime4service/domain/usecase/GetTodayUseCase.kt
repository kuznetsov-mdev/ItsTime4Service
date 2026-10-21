package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Returns today's date in the device time zone.
 * Together with [GetNowUseCase] it is the only place the domain reads the clock;
 * every other use case takes the date as a parameter. The [clock] and the [timeZone] are the system ones;
 * tests pass fixed ones.
 */
class GetTodayUseCase(
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    operator fun invoke(): LocalDate = clock.todayIn(timeZone())
}
