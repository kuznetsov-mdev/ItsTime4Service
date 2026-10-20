package ru.kuznetsov.itstime4service.ui.components.indicators

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [masked]: a hidden plate keeps its look and length, and no real character is left in it.
 */
class PlateMaskTest {

    @Test
    fun russianPlateGetsPlaceholders() {
        assertEquals(
            PlateLook.Russian("Х 000 ХХ", "00", "Х000ХХ00"),
            PlateLook.Russian("А 123 ВС", "77", "А123ВС77").masked(),
        )
    }

    @Test
    fun threeDigitRegionKeepsItsLength() {
        assertEquals("000", (PlateLook.Russian("С 065 МК", "323", "С065МК323").masked() as PlateLook.Russian).region)
    }

    @Test
    fun otherPlatesKeepSpacesAndLength() {
        assertEquals(PlateLook.European("E", "0000 XXX"), PlateLook.European("E", "1234 BCD").masked())
        assertEquals(PlateLook.Universal("00 X 000 XX"), PlateLook.Universal("01 A 123 BC").masked())
        assertEquals(PlateLook.American("0XXX000"), PlateLook.American("7ABC123").masked())
    }
}
