package ru.kuznetsov.itstime4service.ui.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.preset_oil
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Dashed pill used to quick-add a preset maintenance task (e.g. "+ Oil") when adding a car. */
@Composable
fun PresetChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface2)
            .drawBehind {
                val strokeWidthPx = 1.dp.toPx()
                drawRoundRect(
                    color = colors.rule,
                    cornerRadius = CornerRadius(size.minDimension / 2f),
                    style = Stroke(
                        width = strokeWidthPx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            "+ $label",
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            color = colors.inkMuted,
        )
    }
}

@Preview
@Composable
private fun PresetChipLightPreview() = ComponentPreview(darkTheme = false) {
    PresetChip(stringResource(Res.string.preset_oil), onClick = {})
}

@Preview
@Composable
private fun PresetChipDarkPreview() = ComponentPreview(darkTheme = true) {
    PresetChip(stringResource(Res.string.preset_oil), onClick = {})
}