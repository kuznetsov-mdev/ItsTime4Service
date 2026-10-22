package ru.kuznetsov.itstime4service.ui.format

import androidx.compose.ui.text.AnnotatedString
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers [DateInputFormat] for each language's format: a date is typed as eight digits, read back only when it exists,
 * and shown with the separators in place, and the choice of the format by the app language.
 */
class DateInputFormatTest {

    @AfterTest
    fun tearDown() {
        DateInputFormat.language = systemLanguage
    }

    @Test
    fun formatFollowsTheLanguage() {
        assertEquals(DateInputFormat.DAY_MONTH_DOT, DateInputFormat.of("ru"))
        assertEquals(DateInputFormat.DAY_MONTH_SLASH, DateInputFormat.of("es"))
        assertEquals(DateInputFormat.MONTH_DAY_SLASH, DateInputFormat.of("en"))
        assertEquals(DateInputFormat.MONTH_DAY_SLASH, DateInputFormat.of("de"))
    }

    @Test
    fun currentFormatIsTheOneOfTheAppLanguage() {
        DateInputFormat.language = { "es" }

        assertEquals(DateInputFormat.DAY_MONTH_SLASH, DateInputFormat.current)
    }

    @Test
    fun digitsOfDateFollowTheFormat() {
        val date = LocalDate(2026, 3, 7)

        assertEquals("07032026", DateInputFormat.DAY_MONTH_DOT.digitsOf(date))
        assertEquals("07032026", DateInputFormat.DAY_MONTH_SLASH.digitsOf(date))
        assertEquals("03072026", DateInputFormat.MONTH_DAY_SLASH.digitsOf(date))
    }

    @Test
    fun typedDigitsAreReadBack() {
        assertEquals(LocalDate(2026, 3, 7), DateInputFormat.DAY_MONTH_DOT.parse("07032026"))
        assertEquals(LocalDate(2026, 7, 3), DateInputFormat.MONTH_DAY_SLASH.parse("07032026"))
    }

    @Test
    fun everyFormatReadsBackWhatItWrites() {
        val dates = listOf(LocalDate(2024, 2, 29), LocalDate(2026, 12, 31), LocalDate(2026, 1, 1))
        DateInputFormat.entries.forEach { format ->
            dates.forEach { assertEquals(it, format.parse(format.digitsOf(it))) }
        }
    }

    @Test
    fun dateThatDoesNotExistIsNotRead() {
        assertEquals(LocalDate(2024, 2, 29), DateInputFormat.DAY_MONTH_DOT.parse("29022024"))
        assertNull(DateInputFormat.DAY_MONTH_DOT.parse("29022026"))
        assertNull(DateInputFormat.DAY_MONTH_DOT.parse("31042026"))
        assertNull(DateInputFormat.DAY_MONTH_DOT.parse("00012026"))
        assertNull(DateInputFormat.MONTH_DAY_SLASH.parse("13012026"))
    }

    @Test
    fun incompleteInputIsNotRead() {
        assertNull(DateInputFormat.DAY_MONTH_DOT.parse(""))
        assertNull(DateInputFormat.DAY_MONTH_DOT.parse("0703202"))
        assertNull(DateInputFormat.DAY_MONTH_DOT.parse("07.03.26"))
    }

    @Test
    fun onlyEightDigitsAreKept() {
        assertEquals("07032026", DateInputFormat.DAY_MONTH_DOT.digits("07.03.2026 15"))
    }

    @Test
    fun separatorsAreShownAsDigitsAreTyped() {
        val shown = { digits: String -> DateInputFormat.DAY_MONTH_DOT.visualTransformation.filter(AnnotatedString(digits)).text.text }

        assertEquals("07", shown("07"))
        assertEquals("07.0", shown("070"))
        assertEquals("07.03.2", shown("07032"))
        assertEquals("07/03/2026", DateInputFormat.DAY_MONTH_SLASH.visualTransformation.filter(AnnotatedString("07032026")).text.text)
    }

    @Test
    fun cursorSkipsTheSeparators() {
        val mapping = DateInputFormat.DAY_MONTH_DOT.visualTransformation.filter(AnnotatedString("07032026")).offsetMapping

        assertEquals(2, mapping.originalToTransformed(2))
        assertEquals(4, mapping.originalToTransformed(3))
        assertEquals(10, mapping.originalToTransformed(8))
        assertEquals(3, mapping.transformedToOriginal(4))
        assertEquals(8, mapping.transformedToOriginal(10))
    }

    private companion object {
        val systemLanguage = DateInputFormat.language
    }
}
