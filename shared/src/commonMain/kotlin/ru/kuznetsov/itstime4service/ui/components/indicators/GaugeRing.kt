package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Ring on a car card — [value] (0..1) of the ring is filled with [color], [count] of soon/due tasks sits in the middle. */
@Composable
fun GaugeRing(
    value: Float,
    color: Color,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    Box(
        modifier = modifier
            .size(48.dp)
            .drawBehind {
                drawCircle(colors.surface3)
                drawArc(color, startAngle = -90f, sweepAngle = 360f * value.coerceIn(0f, 1f), useCenter = true)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                count.toString(),
                style = ItsTime4ServiceTheme.typography.display.copy(fontSize = 15.sp, lineHeight = 18.sp),
                color = color,
            )
        }
    }
}

@Preview
@Composable
private fun GaugeRingLightPreview() = ComponentPreview(darkTheme = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GaugeRing(value = 0.25f, color = StatusTier.Soon.color, count = 1)
        GaugeRing(value = 0.06f, color = StatusTier.Ok.color, count = 0)
    }
}

@Preview
@Composable
private fun GaugeRingDarkPreview() = ComponentPreview(darkTheme = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GaugeRing(value = 0.25f, color = StatusTier.Soon.color, count = 1)
        GaugeRing(value = 0.06f, color = StatusTier.Ok.color, count = 0)
    }
}
