package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.PlateStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the soft plate check of [DetectPlateStyleUseCase]: which plate kind a typed number is drawn as,
 * and when the device region decides.
 */
class DetectPlateStyleUseCaseTest {

    private val detect = DetectPlateStyleUseCase()

    @Test
    fun russianNumberSplitsSeriesAndRegion() {
        assertEquals(PlateStyle.Russian("А 123 ВС", "77"), detect("а123вс77", null))
    }

    @Test
    fun russianNumberWithSpacesAndThreeDigitRegion() {
        assertEquals(PlateStyle.Russian("М 456 ОР", "199"), detect("М 456 ОР 199", "US"))
    }

    @Test
    fun latinLookAlikeLettersCountAsRussian() {
        assertEquals(PlateStyle.Russian("А 123 ВС", "77"), detect("A123BC77", "DE"))
    }

    @Test
    fun spanishFormatIsEuropeanWithE() {
        assertEquals(PlateStyle.European("E"), detect("1234 BCD", "RU"))
    }

    @Test
    fun germanFormatIsEuropeanWithD() {
        assertEquals(PlateStyle.European("D"), detect("B-AB 1234", null))
    }

    @Test
    fun frenchFormatIsEuropeanWithF() {
        assertEquals(PlateStyle.European("F"), detect("AB-123-CD", "ES"))
    }

    @Test
    fun sameFormatInItalyIsEuropeanWithI() {
        assertEquals(PlateStyle.European("I"), detect("AB 123 CD", "IT"))
    }

    @Test
    fun unknownFormatInTheUsIsAmerican() {
        assertEquals(PlateStyle.American, detect("7ABC123", "US"))
    }

    @Test
    fun unknownFormatInAnEuCountryUsesItsCode() {
        assertEquals(PlateStyle.European("NL"), detect("12-ABC-3", "NL"))
    }

    @Test
    fun unknownFormatElsewhereIsUniversal() {
        assertEquals(PlateStyle.Universal, detect("01 A 123 BC", "KZ"))
    }

    @Test
    fun unknownFormatWithoutRegionIsUniversal() {
        assertEquals(PlateStyle.Universal, detect("XYZ", null))
    }
}
