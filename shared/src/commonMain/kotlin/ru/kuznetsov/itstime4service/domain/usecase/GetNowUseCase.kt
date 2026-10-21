package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Returns the current date and time in the device time zone.
 * Together with [GetTodayUseCase] it is the only place the domain reads the clock. The [clock] and the [timeZone]
 * are the system ones; tests pass fixed ones. The time zone is read on every call, so a change of the device
 * time zone is picked up at once.
 */
class GetNowUseCase(
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    operator fun invoke(): LocalDateTime = clock.now().toLocalDateTime(timeZone())
}
