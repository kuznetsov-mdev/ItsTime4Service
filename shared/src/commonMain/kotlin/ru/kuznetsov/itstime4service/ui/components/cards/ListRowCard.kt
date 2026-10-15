package ru.kuznetsov.itstime4service.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.mileage_km
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.components.indicators.GaugeRing
import ru.kuznetsov.itstime4service.ui.components.indicators.StatusTier
import ru.kuznetsov.itstime4service.ui.components.indicators.color
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Row card with a leading icon tile, title/subtitle and an optional [trailing] slot — a car in the cars list. */
@Composable
fun ListRowCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusXl)
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(19.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
                    color = colors.inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Preview
@Composable
private fun ListRowCardLightPreview() = ComponentPreview(darkTheme = false) {
    ListRowCard(
        icon = ItsTime4ServiceIcons.Car,
        title = "Kia Rio",
        subtitle = stringResource(Res.string.mileage_km, "84 300"),
        modifier = Modifier.width(300.dp),
        trailing = { GaugeRing(value = 0.25f, color = StatusTier.Soon.color, count = 1) },
    )
}

@Preview
@Composable
private fun ListRowCardDarkPreview() = ComponentPreview(darkTheme = true) {
    ListRowCard(
        icon = ItsTime4ServiceIcons.Car,
        title = "Kia Rio",
        subtitle = stringResource(Res.string.mileage_km, "84 300"),
        modifier = Modifier.width(300.dp),
        trailing = { GaugeRing(value = 0.25f, color = StatusTier.Soon.color, count = 1) },
    )
}
