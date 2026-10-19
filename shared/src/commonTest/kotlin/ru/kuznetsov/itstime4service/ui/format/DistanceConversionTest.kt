package ru.kuznetsov.itstime4service.ui.format

import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the km ↔ miles conversions of the forms and [keepKm]: a distance left as it was shown keeps its stored
 * kilometers, so switching units back and forth does not shift it.
 */
class DistanceConversionTest {

    @Test
    fun kilometersAreShownAsTheyAre() {
        assertEquals(84_300, 84_300.kmIn(DistanceUnit.KILOMETERS))
        assertEquals(84_300, 84_300.kmFrom(DistanceUnit.KILOMETERS))
    }

    @Test
    fun milesAreRounded() {
        assertEquals(6_214, 10_000.kmIn(DistanceUnit.MILES))
        assertEquals(10_000, 6_214.kmFrom(DistanceUnit.MILES))
        assertEquals(1, 1.kmIn(DistanceUnit.MILES))
        assertEquals(0, 0.kmIn(DistanceUnit.MILES))
    }

    @Test
    fun untouchedValueKeepsStoredKilometers() {
        val stored = 10_001
        val shown = stored.kmIn(DistanceUnit.MILES)

        assertEquals(stored, keepKm(stored, shown, DistanceUnit.MILES))
    }

    @Test
    fun changedValueIsConverted() {
        assertEquals(8_047, keepKm(10_001, 5_000, DistanceUnit.MILES))
        assertEquals(5_000, keepKm(10_001, 5_000, DistanceUnit.KILOMETERS))
    }

    @Test
    fun newValueWithoutStoredOneIsConverted() {
        assertEquals(16_093, keepKm(null, 10_000, DistanceUnit.MILES))
    }
}
