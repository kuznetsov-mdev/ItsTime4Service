package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Returns today's date in the device time zone.
 * Together with [GetNowUseCase] it is the only place the domain reads the clock;
 * every other use case takes the date as a parameter.
 */
class GetTodayUseCase {
    operator fun invoke(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
