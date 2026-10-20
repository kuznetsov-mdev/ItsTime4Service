package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * Filled pill with the status name — shown next to each maintenance task.
 * Its color changes smoothly when the status changes (see [IndicatorAnimation]).
 */
@Composable
fun StatusChip(
    status: StatusTier,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(IndicatorAnimation.color(status.color))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            status.label,
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
            color = ItsTime4ServiceTheme.colors.onTier,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Preview
@Composable
private fun StatusChipLightPreview() = ComponentPreview(darkTheme = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusTier.entries.forEach { StatusChip(it) }
    }
}

@Preview
@Composable
private fun StatusChipDarkPreview() = ComponentPreview(darkTheme = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusTier.entries.forEach { StatusChip(it) }
    }
}
