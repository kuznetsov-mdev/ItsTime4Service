package ru.kuznetsov.itstime4service.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The three font roles from the design mockup. Both `display` and `sans` resolve to the
 * platform system font ([FontFamily.Default] — San Francisco on iOS, Roboto on Android),
 * matching the mockup's `-apple-system/Segoe UI/Roboto` stack; no custom font files needed.
 * `mono` is used for all numeric/tabular data — mileage, VIN, dates, field labels.
 */
data class ItsTime4ServiceTypography(
    val display: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = (-0.02f).em,
        lineHeight = 32.sp,
    ),
    val sans: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    val mono: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
)
