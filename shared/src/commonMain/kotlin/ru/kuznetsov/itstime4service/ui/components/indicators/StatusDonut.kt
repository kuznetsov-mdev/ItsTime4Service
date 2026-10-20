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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * Ring of a car's tasks split by status: due, soon and ok arcs clockwise from the top, each as long as its share
 * of [total]; without tasks the ring is empty. The middle shows "soon and due of total" in the color of the worst
 * status present. [contentDescription] is read out instead of the numbers.
 * The arcs grow and the color changes as described in [IndicatorAnimation].
 */
@Composable
fun StatusDonut(
    due: Int,
    soon: Int,
    total: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val dueColor = StatusTier.Due.color
    val soonColor = StatusTier.Soon.color
    val okColor = StatusTier.Ok.color
    val worst = IndicatorAnimation.color(
        when {
            due > 0 -> dueColor
            soon > 0 -> soonColor
            else -> okColor
        },
    )
    val dueShare = IndicatorAnimation.fraction(if (total > 0) due.toFloat() / total else 0f)
    val soonShare = IndicatorAnimation.fraction(if (total > 0) soon.toFloat() / total else 0f)
    val okShare = IndicatorAnimation.fraction(if (total > 0) (total - due - soon).toFloat() / total else 0f)
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .drawBehind {
                drawCircle(colors.surface3)
                var start = -90f
                listOf(dueShare to dueColor, soonShare to soonColor, okShare to okColor).forEach { (share, color) ->
                    val sweep = 360f * share
                    if (sweep > 0f) drawArc(color, startAngle = start, sweepAngle = sweep, useCenter = true)
                    start += sweep
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${due + soon}/$total",
                style = ItsTime4ServiceTheme.typography.display.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = worst,
            )
        }
    }
}

@Preview
@Composable
private fun StatusDonutLightPreview() = ComponentPreview(darkTheme = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusDonut(due = 1, soon = 1, total = 5, contentDescription = "")
        StatusDonut(due = 0, soon = 0, total = 3, contentDescription = "")
        StatusDonut(due = 0, soon = 0, total = 0, contentDescription = "")
    }
}

@Preview
@Composable
private fun StatusDonutDarkPreview() = ComponentPreview(darkTheme = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusDonut(due = 1, soon = 1, total = 5, contentDescription = "")
        StatusDonut(due = 0, soon = 0, total = 3, contentDescription = "")
        StatusDonut(due = 0, soon = 0, total = 0, contentDescription = "")
    }
}
