package ru.kuznetsov.itstime4service.ui.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.theme_dark
import itstime4service.shared.generated.resources.theme_light
import itstime4service.shared.generated.resources.theme_system
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Group of mutually-exclusive options — theme picker, unit picker (km/mi). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val dimens = ItsTime4ServiceTheme.dimens
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radiusM))
            .background(colors.surface2)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            Segment(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RowScope.Segment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusS)
    Box(
        modifier = modifier
            .then(
                if (selected) {
                    Modifier
                        .shadow(1.dp, shape, ambientColor = colors.ink.copy(alpha = 0.08f), spotColor = colors.ink.copy(alpha = 0.08f))
                        .clip(shape)
                        .background(colors.surface)
                } else {
                    Modifier.clip(shape)
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.ink else colors.inkMuted,
        )
    }
}

@Preview
@Composable
private fun SegmentedControlLightPreview() = ComponentPreview(darkTheme = false) {
    SegmentedControl(
        options = listOf(
            stringResource(Res.string.theme_light),
            stringResource(Res.string.theme_dark),
            stringResource(Res.string.theme_system),
        ),
        selectedIndex = 0,
        onSelect = {},
    )
}

@Preview
@Composable
private fun SegmentedControlDarkPreview() = ComponentPreview(darkTheme = true) {
    SegmentedControl(
        options = listOf(
            stringResource(Res.string.theme_light),
            stringResource(Res.string.theme_dark),
            stringResource(Res.string.theme_system),
        ),
        selectedIndex = 0,
        onSelect = {},
    )
}