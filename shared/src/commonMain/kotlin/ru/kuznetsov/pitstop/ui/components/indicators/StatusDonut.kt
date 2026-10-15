package ru.kuznetsov.pitstop.ui.components.indicators

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
import ru.kuznetsov.pitstop.ui.components.ComponentPreview
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

/**
 * Ring of a car's tasks split by status: due, soon and ok arcs clockwise from the top, each as long as its share
 * of [total]; without tasks the ring is empty. The middle shows "soon and due of total" in the color of the worst
 * status present. [contentDescription] is read out instead of the numbers.
 */
@Composable
fun StatusDonut(
    due: Int,
    soon: Int,
    total: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = PitStopTheme.colors
    val dueColor = StatusTier.Due.color
    val soonColor = StatusTier.Soon.color
    val okColor = StatusTier.Ok.color
    val worst = when {
        due > 0 -> dueColor
        soon > 0 -> soonColor
        else -> okColor
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .drawBehind {
                drawCircle(colors.surface3)
                if (total > 0) {
                    var start = -90f
                    listOf(due to dueColor, soon to soonColor, (total - due - soon) to okColor).forEach { (count, color) ->
                        val sweep = 360f * count / total
                        if (sweep > 0f) drawArc(color, startAngle = start, sweepAngle = sweep, useCenter = true)
                        start += sweep
                    }
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
                style = PitStopTheme.typography.display.copy(fontSize = 13.sp, lineHeight = 16.sp),
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
