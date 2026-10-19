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
 * A licence plate drawn after [look]: light in the light theme, dark in the dark theme.
 * [compact] draws it smaller — for a car card in the list.
 */
@Composable
fun LicensePlate(look: PlateLook, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = ItsTime4ServiceTheme.colors
    val fontSize = if (compact) 15.sp else 18.sp
    val described = modifier.semantics { contentDescription = look.text }
    when (look) {
        is PlateLook.Russian -> RussianPlate(look, compact, described)
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
                    PlateText(look.countryCode, fontSize * 0.44f, Color.White)
                }
                PlateText(look.text, fontSize, colors.plateInk, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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
                PlateText(look.text, fontSize, colors.plateUsInk)
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
                PlateText(look.text, fontSize * 0.94f, colors.ink)
            }
        }
    }
}

@Composable
private fun PlateText(text: String, fontSize: TextUnit, color: Color, modifier: Modifier = Modifier) {
    val style = ItsTime4ServiceTheme.typography.mono.copy(
        fontSize = fontSize,
        lineHeight = fontSize * 1.1f,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.03).em,
    )
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
