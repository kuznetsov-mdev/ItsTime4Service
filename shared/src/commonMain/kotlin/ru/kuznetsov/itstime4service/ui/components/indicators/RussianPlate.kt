package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * A Russian plate drawn to the proportions of the real one (520 × 112 mm): a thick frame, the series
 * with letters smaller than the digits on one baseline, and the region with "RUS" and the flag behind a divider.
 * Every size is a share of the plate height and does not follow the system font scale, so the drawing keeps its shape.
 * The gaps are measured between the glyph boxes, which already carry the font's side bearings.
 * A series that is not "letter digits letters" is drawn as one line in the digit size. The region section is wide
 * enough for three digits, as on the sample `docs/X999AM199.png`; a line that still does not fit its section is
 * narrowed, keeping its height.
 */
@Composable
internal fun RussianPlate(look: PlateLook.Russian, compact: Boolean, modifier: Modifier = Modifier) {
    val colors = ItsTime4ServiceTheme.colors
    val height = if (compact) 22.dp else 30.dp
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseStyle = ItsTime4ServiceTheme.typography.sans.copy(
        fontWeight = FontWeight.SemiBold,
        textGeometricTransform = TextGeometricTransform(scaleX = CONDENSE),
    )
    val ink = colors.plateInk
    Canvas(modifier.size(width = height * WIDTH, height = height)) {
        val h = size.height
        fun style(glyphHeight: Float) = baseStyle.copy(fontSize = (glyphHeight / CAP_HEIGHT / density.density / density.fontScale).sp)
        val stroke = h * FRAME
        val inset = stroke / 2
        drawRoundRect(colors.plateBg, cornerRadius = CornerRadius(h * CORNER))
        drawRoundRect(
            ink,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, h - stroke),
            cornerRadius = CornerRadius(h * CORNER - inset),
            style = Stroke(stroke),
        )
        val innerLeft = stroke
        val innerRight = size.width - stroke
        val innerTop = stroke
        val innerBottom = h - stroke
        val dividerX = innerLeft + (innerRight - innerLeft) * MAIN_SHARE
        drawRect(ink, topLeft = Offset(dividerX, innerTop), size = Size(stroke, innerBottom - innerTop))

        val parts = look.series.split(' ').filter { it.isNotEmpty() }
        val seriesBaseline = innerBottom - h * SERIES_BOTTOM
        val seriesLeft = innerLeft + h * SERIES_SIDE
        val seriesRight = dividerX - h * SERIES_SIDE
        val pieces = if (parts.size == 3 && parts[1].all(Char::isDigit)) {
            val letters = style(h * LETTER_HEIGHT)
            val digits = style(h * DIGIT_HEIGHT)
            glyphs(measurer, parts[0], letters, h * LETTER_GAP) +
                Gap(h * GROUP_GAP) +
                glyphs(measurer, parts[1], digits, h * DIGIT_GAP) +
                Gap(h * GROUP_GAP) +
                glyphs(measurer, parts[2], letters, h * LETTER_GAP)
        } else {
            glyphs(measurer, parts.joinToString(""), style(h * DIGIT_HEIGHT), h * DIGIT_GAP)
        }
        drawLine(pieces, seriesLeft, seriesRight, seriesBaseline, ink)

        val regionLeft = dividerX + stroke + h * REGION_PADDING
        val regionRight = innerRight - h * REGION_PADDING
        val regionBaseline = innerTop + h * (REGION_TOP + REGION_HEIGHT)
        drawLine(glyphs(measurer, look.region, style(h * REGION_HEIGHT), h * DIGIT_GAP), regionLeft, regionRight, regionBaseline, ink)

        val rus = listOf(
            Glyph(measurer.measure("RUS", style(h * RUS_HEIGHT))),
            Gap(h * FLAG_GAP),
            Flag(h * FLAG_WIDTH, h * FLAG_HEIGHT),
        )
        drawLine(rus, regionLeft, regionRight, innerBottom - h * RUS_BOTTOM, ink)
    }
}

private sealed interface Piece {
    val width: Float
}

private class Glyph(val layout: TextLayoutResult) : Piece {
    override val width: Float get() = layout.size.width.toFloat()
}

private class Gap(override val width: Float) : Piece

private class Flag(override val width: Float, val height: Float) : Piece

private fun glyphs(measurer: TextMeasurer, text: String, style: TextStyle, gap: Float): List<Piece> =
    text.flatMapIndexed { index, char ->
        val glyph = Glyph(measurer.measure(char.toString(), style))
        if (index == 0) listOf(glyph) else listOf(Gap(gap), glyph)
    }

private fun DrawScope.drawLine(pieces: List<Piece>, left: Float, right: Float, baseline: Float, color: Color) {
    val total = pieces.sumOf { it.width.toDouble() }.toFloat()
    val available = right - left
    val squeeze = if (total > available) available / total else 1f
    var x = left + (available - total * squeeze) / 2
    pieces.forEach { piece ->
        when (piece) {
            is Glyph -> {
                val topLeft = Offset(x, baseline - piece.layout.firstBaseline)
                drawContext.transform.scale(squeeze, 1f, topLeft)
                drawText(piece.layout, color, topLeft)
                drawContext.transform.scale(1 / squeeze, 1f, topLeft)
            }
            is Flag -> drawFlag(Offset(x, baseline - piece.height), Size(piece.width * squeeze, piece.height))
            is Gap -> Unit
        }
        x += piece.width * squeeze
    }
}

private fun DrawScope.drawFlag(topLeft: Offset, size: Size) {
    val stripe = Size(size.width, size.height / 3)
    drawRect(Color.White, topLeft, stripe)
    drawRect(FlagBlue, topLeft.copy(y = topLeft.y + stripe.height), stripe)
    drawRect(FlagRed, topLeft.copy(y = topLeft.y + stripe.height * 2), stripe)
    drawRect(FlagEdge, topLeft, size, style = Stroke(size.height * 0.06f))
}

private const val WIDTH = 4.64f
private const val CORNER = 0.12f
private const val FRAME = 0.06f
private const val MAIN_SHARE = 0.72f
private const val CAP_HEIGHT = 0.71f
private const val CONDENSE = 0.86f
private const val DIGIT_HEIGHT = 0.626f
private const val LETTER_HEIGHT = 0.51f
private const val SERIES_BOTTOM = 0.11f
private const val SERIES_SIDE = 0.12f
private const val GROUP_GAP = 0.1f
private const val DIGIT_GAP = 0f
private const val LETTER_GAP = 0.04f
private const val REGION_TOP = 0.08f
private const val REGION_PADDING = 0.07f
private const val REGION_HEIGHT = 0.48f
private const val RUS_HEIGHT = 0.16f
private const val RUS_BOTTOM = 0.11f
private const val FLAG_WIDTH = 0.34f
private const val FLAG_HEIGHT = 0.18f
private const val FLAG_GAP = 0.05f

private val FlagBlue = Color(0xFF1C57A5)
private val FlagRed = Color(0xFFD52B1E)
private val FlagEdge = Color(0xFF999999)
