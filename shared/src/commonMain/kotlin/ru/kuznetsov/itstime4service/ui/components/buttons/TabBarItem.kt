package ru.kuznetsov.itstime4service.ui.components.buttons

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.tab_cars
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * One entry of the bottom tab bar — icon + label stacked, accent when selected.
 * The selected entry is outlined by a rounded frame in the icon's color; the others keep the same frame invisible,
 * so switching tabs moves nothing. The whole cell is tappable and reads as a selected or unselected tab.
 */
@Composable
fun TabBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val color = if (selected) colors.accent else colors.inkFaint
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM)
    Box(
        modifier = modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(top = 8.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .border(1.5.dp, if (selected) color else Color.Transparent, shape)
                .padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
            Text(label, style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 11.sp), color = color)
        }
    }
}

@Preview
@Composable
private fun TabBarItemLightPreview() = ComponentPreview(darkTheme = false) {
    TabBarItem(icon = ItsTime4ServiceIcons.Car, label = stringResource(Res.string.tab_cars), selected = true, onClick = {})
}

@Preview
@Composable
private fun TabBarItemDarkPreview() = ComponentPreview(darkTheme = true) {
    TabBarItem(icon = ItsTime4ServiceIcons.Car, label = stringResource(Res.string.tab_cars), selected = true, onClick = {})
}