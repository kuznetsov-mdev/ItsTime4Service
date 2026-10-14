package ru.kuznetsov.pitstop.ui.format

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * How a date is typed in the app's date fields, chosen by the app language: Russian `dd.MM.yyyy`,
 * Spanish `dd/MM/yyyy`, English (and any other language) `MM/dd/yyyy`.
 * A date field keeps only the typed digits, at most eight; [visualTransformation] shows them with the
 * separators already in place, so the user types digits only and never the separators.
 */
enum class DateInputFormat(val separator: Char, private val dayFirst: Boolean) {
    DAY_MONTH_DOT('.', dayFirst = true),
    DAY_MONTH_SLASH('/', dayFirst = true),
    MONTH_DAY_SLASH('/', dayFirst = false),
    ;

    val visualTransformation: VisualTransformation = DateVisualTransformation(separator)

    fun digits(text: String): String = text.filter(Char::isDigit).take(DIGITS)

    fun digitsOf(date: LocalDate): String {
        val day = date.day.toString().padStart(2, '0')
        val month = date.month.number.toString().padStart(2, '0')
        return (if (dayFirst) day + month else month + day) + date.year.toString().padStart(4, '0')
    }

    fun parse(digits: String): LocalDate? {
        if (digits.length != DIGITS || !digits.all(Char::isDigit)) return null
        val first = digits.substring(0, 2).toInt()
        val second = digits.substring(2, 4).toInt()
        val year = digits.substring(4).toInt()
        return try {
            if (dayFirst) LocalDate(year, second, first) else LocalDate(year, first, second)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        const val DIGITS = 8

        val current: DateInputFormat
            get() = when (Locale.current.language) {
                "ru" -> DAY_MONTH_DOT
                "es" -> DAY_MONTH_SLASH
                else -> MONTH_DAY_SLASH
            }
    }
}

/**
 * Shows up to eight date digits with [separator] after the first and the second pair, as soon as the next
 * digit is typed; the cursor moves over the separators as if they were not there.
 */
private class DateVisualTransformation(private val separator: Char) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = buildString {
            digits.forEachIndexed { index, char ->
                if (index == FIRST_BREAK || index == SECOND_BREAK) append(separator)
                append(char)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= FIRST_BREAK -> offset
                offset <= SECOND_BREAK -> offset + 1
                else -> offset + 2
            }.coerceAtMost(shown.length)

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= FIRST_BREAK -> offset
                offset <= SECOND_BREAK + 1 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }

    private companion object {
        const val FIRST_BREAK = 2
        const val SECOND_BREAK = 4
    }
}
