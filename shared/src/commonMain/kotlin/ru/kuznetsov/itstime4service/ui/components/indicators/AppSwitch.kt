package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** On/off toggle — push notifications and other binary settings. */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val thumbOffset by animateDpAsState(if (checked) 17.dp else 2.dp, animationSpec = tween(150))
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 21.dp)
            .clip(CircleShape)
            .background(if (checked) colors.accent else colors.surface3)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(17.dp)
                .clip(CircleShape)
                .background(if (checked) colors.onAccent else colors.surface),
        )
    }
}

@Preview
@Composable
private fun AppSwitchLightPreview() = ComponentPreview(darkTheme = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSwitch(checked = true, onCheckedChange = {})
        AppSwitch(checked = false, onCheckedChange = {})
    }
}

@Preview
@Composable
private fun AppSwitchDarkPreview() = ComponentPreview(darkTheme = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSwitch(checked = true, onCheckedChange = {})
        AppSwitch(checked = false, onCheckedChange = {})
    }
}
