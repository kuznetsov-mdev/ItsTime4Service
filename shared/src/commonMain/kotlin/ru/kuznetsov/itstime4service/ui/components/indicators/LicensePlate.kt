package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.plate_hidden
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * What a licence plate looks like: [Russian] splits the series and the region and adds "RUS" with the flag;
 * [European] adds the blue band with the stars and [European.countryCode]; [American] adds "USA" above the number;
 * [Universal] is a neutral frame. [text] is what is read out instead of the drawing.
 */
sealed interface PlateLook {
    val text: String

    data class Russian(val series: String, val region: String, override val text: String) : PlateLook

    data class European(val countryCode: String, override val text: String) : PlateLook

    data class American(override val text: String) : PlateLook

    data class Universal(override val text: String) : PlateLook
}

/**
 * Placeholders for a hidden plate: the same look with every character replaced and the length kept — letters by «Х»
 * on a Russian plate and «X» on the others, digits by «0» — so a blurred or not blurred drawing never shows the real
 * number.
 */
fun PlateLook.masked(): PlateLook = when (this) {
    is PlateLook.Russian -> PlateLook.Russian(series.mask('Х'), region.mask('Х'), text.mask('Х'))
    is PlateLook.European -> copy(text = text.mask('X'))
    is PlateLook.American -> PlateLook.American(text.mask('X'))
    is PlateLook.Universal -> PlateLook.Universal(text.mask('X'))
}

private fun String.mask(letter: Char): String =
    map { char -> when { char.isDigit() -> '0'; char.isLetter() -> letter; else -> char } }.joinToString("")

/**
 * A licence plate drawn after [look]: light in the light theme, dark in the dark theme.
 * [compact] draws it smaller — for a car card in the list. [masked] draws a hidden plate: placeholders instead of the
 * characters (see [masked]), blurred, with the frame and the decorations sharp; it is read out as a hidden plate.
 */
@Composable
fun LicensePlate(look: PlateLook, modifier: Modifier = Modifier, compact: Boolean = false, masked: Boolean = false) {
    val colors = ItsTime4ServiceTheme.colors
    val fontSize = if (compact) 15.sp else 18.sp
    val description = if (masked) stringResource(Res.string.plate_hidden) else look.text
    val described = modifier.semantics { contentDescription = description }
    when (val shown = if (masked) look.masked() else look) {
        is PlateLook.Russian -> RussianPlate(shown, compact, described, blurGlyphs = masked)
        is PlateLook.European -> {
            val shape = RoundedCornerShape(5.dp)
            Row(
                modifier = described
                    .height(IntrinsicSize.Min)
                    .clip(shape)
                    .background(colors.plateBg)
                    .border(1.5.dp, colors.plateEdge, shape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight().background(colors.plateEuBand).padding(horizontal = 3.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    EuStars()
                    PlateText(shown.countryCode, fontSize * 0.44f, Color.White)
                }
                PlateText(shown.text, fontSize, colors.plateInk, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), blurred = masked)
            }
        }
        is PlateLook.American -> {
            val shape = RoundedCornerShape(7.dp)
            Column(
                modifier = described
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(colors.plateBg, colors.plateBg.copy(alpha = 0.92f))))
                    .border(1.5.dp, colors.plateUsInk, shape)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlateText("USA", fontSize * 0.36f, UsRed)
                PlateText(shown.text, fontSize, colors.plateUsInk, blurred = masked)
            }
        }
        is PlateLook.Universal -> {
            val shape = RoundedCornerShape(7.dp)
            Box(
                modifier = described
                    .clip(shape)
                    .background(colors.surface2)
                    .border(1.5.dp, colors.inkFaint, shape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                PlateText(shown.text, fontSize * 0.94f, colors.ink, blurred = masked)
            }
        }
    }
}

@Composable
private fun PlateText(text: String, fontSize: TextUnit, color: Color, modifier: Modifier = Modifier, blurred: Boolean = false) {
    val style = ItsTime4ServiceTheme.typography.mono.copy(
        fontSize = fontSize,
        lineHeight = fontSize * 1.1f,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.03).em,
    )
    Row(
        modifier = modifier.then(if (blurred) Modifier.blur(3.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text.split(' ').filter { it.isNotEmpty() }.forEach { group ->
            Text(group, style = style, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun EuStars() {
    Box(
        Modifier.size(9.dp).drawBehind {
            drawCircle(
                color = EuYellow,
                radius = size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 1.5.dp.toPx()))),
            )
        },
    )
}

private val UsRed = Color(0xFFB8202F)
private val EuYellow = Color(0xFFF7D117)

@Composable
private fun LicensePlatePreviewContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LicensePlate(PlateLook.Russian("А 123 ВС", "77", "А123ВС77"))
        LicensePlate(PlateLook.Russian("С 065 МК", "323", "С065МК323"))
        LicensePlate(PlateLook.Russian("С 065 МК", "323", "С065МК323"), masked = true)
        LicensePlate(PlateLook.European("E", "1234 BCD"), masked = true)
        LicensePlate(PlateLook.European("E", "1234 BCD"))
        LicensePlate(PlateLook.American("7ABC123"))
        LicensePlate(PlateLook.Universal("01 A 123 BC"))
        LicensePlate(PlateLook.Russian("М 456 ОР", "199", "М456ОР199"), compact = true)
    }
}

@Preview
@Composable
private fun LicensePlateLightPreview() = ComponentPreview(darkTheme = false) {
    LicensePlatePreviewContent()
}

@Preview
@Composable
private fun LicensePlateDarkPreview() = ComponentPreview(darkTheme = true) {
    LicensePlatePreviewContent()
}
