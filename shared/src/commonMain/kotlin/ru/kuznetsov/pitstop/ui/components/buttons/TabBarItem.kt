package ru.kuznetsov.pitstop.ui.components.buttons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.tab_cars
import ru.kuznetsov.pitstop.ui.components.ComponentPreview
import ru.kuznetsov.pitstop.ui.icons.PitStopIcons
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

/** One entry of the bottom tab bar — icon + label stacked, accent when selected. */
@Composable
fun TabBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PitStopTheme.colors
    val color = if (selected) colors.accent else colors.inkFaint
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(top = 13.dp, bottom = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(19.dp))
        Text(label, style = PitStopTheme.typography.sans.copy(fontSize = 11.sp), color = color)
    }
}

@Preview
@Composable
private fun TabBarItemLightPreview() = ComponentPreview(darkTheme = false) {
    TabBarItem(icon = PitStopIcons.Car, label = stringResource(Res.string.tab_cars), selected = true, onClick = {})
}

@Preview
@Composable
private fun TabBarItemDarkPreview() = ComponentPreview(darkTheme = true) {
    TabBarItem(icon = PitStopIcons.Car, label = stringResource(Res.string.tab_cars), selected = true, onClick = {})
}