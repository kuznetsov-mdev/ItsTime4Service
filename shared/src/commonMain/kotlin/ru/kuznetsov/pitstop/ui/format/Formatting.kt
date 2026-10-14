package ru.kuznetsov.pitstop.ui.format

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.distance_mi
import pitstop.shared.generated.resources.mileage_km
import pitstop.shared.generated.resources.unit_genitive_km
import pitstop.shared.generated.resources.unit_genitive_mi
import pitstop.shared.generated.resources.unit_label_km
import pitstop.shared.generated.resources.unit_label_mi
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import kotlin.math.abs
import kotlin.math.roundToInt

private const val KM_PER_MILE = 1.609344
private const val GROUP_SEPARATOR = " "

fun Int.grouped(): String {
    val digits = abs(toLong()).toString().reversed().chunked(3).joinToString(GROUP_SEPARATOR).reversed()
    return if (this < 0) "-$digits" else digits
}

fun Int.kmFrom(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETERS -> this
    DistanceUnit.MILES -> (this * KM_PER_MILE).roundToInt()
}

fun keepKm(originalKm: Int?, typed: Int, unit: DistanceUnit): Int =
    if (originalKm != null && originalKm.kmIn(unit) == typed) originalKm else typed.kmFrom(unit)

@Composable
fun unitLabel(unit: DistanceUnit): String = stringResource(
    when (unit) {
        DistanceUnit.KILOMETERS -> Res.string.unit_label_km
        DistanceUnit.MILES -> Res.string.unit_label_mi
    },
)

@Composable
fun unitGenitive(unit: DistanceUnit): String = stringResource(
    when (unit) {
        DistanceUnit.KILOMETERS -> Res.string.unit_genitive_km
        DistanceUnit.MILES -> Res.string.unit_genitive_mi
    },
)

fun Int.kmIn(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETERS -> this
    DistanceUnit.MILES -> (this / KM_PER_MILE).roundToInt()
}

@Composable
fun distanceText(km: Int, unit: DistanceUnit): String {
    val template = when (unit) {
        DistanceUnit.KILOMETERS -> Res.string.mileage_km
        DistanceUnit.MILES -> Res.string.distance_mi
    }
    return stringResource(template, km.kmIn(unit).grouped())
}

fun LocalDate.toDisplayText(): String = "${day.twoDigits()}.${month.number.twoDigits()}.$year"

fun LocalTime.toDisplayText(): String = "${hour.twoDigits()}:${minute.twoDigits()}"


private fun Int.twoDigits(): String = toString().padStart(2, '0')
