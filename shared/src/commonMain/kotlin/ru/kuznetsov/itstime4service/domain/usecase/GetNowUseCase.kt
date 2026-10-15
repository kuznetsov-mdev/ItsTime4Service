package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Returns the current date and time in the device time zone.
 * Together with [GetTodayUseCase] it is the only place the domain reads the clock.
 */
class GetNowUseCase {
    operator fun invoke(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
}
