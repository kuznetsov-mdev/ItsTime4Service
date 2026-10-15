package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * Thin fill track — share of the maintenance interval already used up, [value] in 0..1.
 * The fill color follows the value: up to 60% — ok, up to 90% — soon, above — due;
 * pass [tier] to color the fill by an already known status instead.
 */
@Composable
fun ProgressBar(
    value: Float,
    modifier: Modifier = Modifier,
    tier: StatusTier? = null,
) {
    val fraction = value.coerceIn(0f, 1f)
    val fillTier = tier ?: when {
        fraction <= 0.6f -> StatusTier.Ok
        fraction <= 0.9f -> StatusTier.Soon
        else -> StatusTier.Due
    }
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(ItsTime4ServiceTheme.colors.surface2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(shape)
                .background(fillTier.color),
        )
    }
}

@Preview
@Composable
private fun ProgressBarLightPreview() = ComponentPreview(darkTheme = false) {
    Column(modifier = Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressBar(value = 0.4f)
        ProgressBar(value = 0.75f)
        ProgressBar(value = 1f)
    }
}

@Preview
@Composable
private fun ProgressBarDarkPreview() = ComponentPreview(darkTheme = true) {
    Column(modifier = Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressBar(value = 0.4f)
        ProgressBar(value = 0.75f)
        ProgressBar(value = 1f)
    }
}
