package ru.kuznetsov.itstime4service.ui.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * The eye button next to a licence plate or a VIN that hides it or shows it again: a crossed-out eye while the value
 * is shown — tap to hide, an open eye while it is hidden — tap to show. [label] is read out and should name the action.
 */
@Composable
fun SecretToggle(hidden: Boolean, label: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    AppIconButton(
        icon = if (hidden) ItsTime4ServiceIcons.Eye else ItsTime4ServiceIcons.EyeOff,
        onClick = onToggle,
        contentDescription = label,
        size = 27.dp,
        iconSize = 15.dp,
        tint = ItsTime4ServiceTheme.colors.inkFaint,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun SecretToggleLightPreview() = ComponentPreview(darkTheme = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecretToggle(hidden = false, label = "Hide", onToggle = {})
        SecretToggle(hidden = true, label = "Show", onToggle = {})
    }
}

@Preview
@Composable
private fun SecretToggleDarkPreview() = ComponentPreview(darkTheme = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecretToggle(hidden = false, label = "Hide", onToggle = {})
        SecretToggle(hidden = true, label = "Show", onToggle = {})
    }
}
