package ru.kuznetsov.itstime4service.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.stat_mileage
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Stat block with an uppercase [label] and free-form [content] under it — e.g. mileage with inline editing. */
@Composable
fun StatCard(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusL)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label.uppercase(),
            style = ItsTime4ServiceTheme.typography.mono.copy(
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.06f.em,
            ),
            color = colors.inkFaint,
        )
        content()
    }
}

/** Stat block showing a single big [value], optionally with a leading [icon] — mileage, soon/overdue count. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ItsTime4ServiceTheme.colors.ink,
    icon: ImageVector? = null,
) {
    StatCard(label = label, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = valueColor, modifier = Modifier.size(16.dp))
            }
            Text(
                value,
                style = ItsTime4ServiceTheme.typography.display.copy(fontSize = 22.sp, lineHeight = 28.sp),
                color = valueColor,
                maxLines = 1,
            )
        }
    }
}

@Preview
@Composable
private fun StatCardLightPreview() = ComponentPreview(darkTheme = false) {
    StatCard(
        label = stringResource(Res.string.stat_mileage),
        value = "84 300",
        icon = ItsTime4ServiceIcons.Gauge,
        modifier = Modifier.width(160.dp),
    )
}

@Preview
@Composable
private fun StatCardDarkPreview() = ComponentPreview(darkTheme = true) {
    StatCard(
        label = stringResource(Res.string.stat_mileage),
        value = "84 300",
        icon = ItsTime4ServiceIcons.Gauge,
        modifier = Modifier.width(160.dp),
    )
}
