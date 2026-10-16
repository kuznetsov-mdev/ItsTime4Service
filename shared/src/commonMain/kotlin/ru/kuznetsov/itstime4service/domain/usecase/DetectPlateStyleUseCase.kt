package ru.kuznetsov.itstime4service.domain.usecase

import ru.kuznetsov.itstime4service.domain.model.PlateStyle

/**
 * Picks the kind of licence plate to draw from the format of the typed number — a soft check that never rejects
 * anything. A Russian number (letter, three digits, two letters, a two- or three-digit region; Latin look-alike
 * letters count as Cyrillic) is shown as Russian. Spanish, German and French or Italian formats are shown as
 * European with that country's code; the French and Italian formats are the same, so an Italian [deviceRegion]
 * picks "I". A number in no known format falls back to [deviceRegion] (an ISO country code): the United States
 * gives the American plate, which has no fixed format, an EU country gives the European plate with its code,
 * anything else the universal plate.
 */
class DetectPlateStyleUseCase {
    operator fun invoke(plate: String, deviceRegion: String?): PlateStyle {
        val text = plate.trim().uppercase().replace(Regex("\\s+"), " ")
        val compact = text.replace(" ", "").replace("-", "")
        val cyrillic = compact.map { LATIN_TO_CYRILLIC[it] ?: it }.joinToString("")
        RUSSIAN.matchEntire(cyrillic)?.let { match ->
            val (letter, digits, letters, region) = match.destructured
            return PlateStyle.Russian(series = "$letter $digits $letters", region = region)
        }
        val region = deviceRegion?.uppercase()
        return when {
            SPANISH.matches(text) -> PlateStyle.European("E")
            GERMAN.matches(text) -> PlateStyle.European("D")
            FRENCH_OR_ITALIAN.matches(text) -> PlateStyle.European(if (region == "IT") "I" else "F")
            region == "US" -> PlateStyle.American
            region != null && region in EU_PLATE_CODES -> PlateStyle.European(EU_PLATE_CODES.getValue(region))
            else -> PlateStyle.Universal
        }
    }

    private companion object {
        const val RUSSIAN_LETTERS = "АВЕКМНОРСТУХ"
        val RUSSIAN = Regex("^([$RUSSIAN_LETTERS])(\\d{3})([$RUSSIAN_LETTERS]{2})(\\d{2,3})$")
        val LATIN_TO_CYRILLIC = "ABEKMHOPCTYX".zip(RUSSIAN_LETTERS).toMap()
        val SPANISH = Regex("^\\d{4} ?[BCDFGHJKLMNPRSTVWXYZ]{3}$")
        val GERMAN = Regex("^[A-ZÄÖÜ]{1,3}[ -][A-Z]{1,2} ?\\d{1,4}[EH]?$")
        val FRENCH_OR_ITALIAN = Regex("^[A-Z]{2}[ -]?\\d{3}[ -]?[A-Z]{2}$")
        val EU_PLATE_CODES = mapOf(
            "AT" to "A", "BE" to "B", "BG" to "BG", "HR" to "HR", "CY" to "CY", "CZ" to "CZ", "DK" to "DK",
            "EE" to "EST", "FI" to "FIN", "FR" to "F", "DE" to "D", "GR" to "GR", "HU" to "H", "IE" to "IRL",
            "IT" to "I", "LV" to "LV", "LT" to "LT", "LU" to "L", "MT" to "M", "NL" to "NL", "PL" to "PL",
            "PT" to "P", "RO" to "RO", "SK" to "SK", "SI" to "SLO", "ES" to "E", "SE" to "S",
        )
    }
}
