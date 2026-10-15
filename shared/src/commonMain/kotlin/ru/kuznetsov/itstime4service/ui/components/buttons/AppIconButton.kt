package ru.kuznetsov.itstime4service.ui.components.buttons

import ru.kuznetsov.itstime4service.ui.components.ComponentPreview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * Circular secondary button — back navigation, edit, delete, history. Named `AppIconButton`
 * (not `IconButton`) to avoid colliding with `androidx.compose.material3.IconButton`.
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 31.dp,
    iconSize: Dp = 16.dp,
    tint: Color = ItsTime4ServiceTheme.colors.inkMuted,
) {
    val colors = ItsTime4ServiceTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Preview
@Composable
private fun AppIconButtonLightPreview() = ComponentPreview(darkTheme = false) {
    AppIconButton(icon = ItsTime4ServiceIcons.ChevronLeft, onClick = {})
}

@Preview
@Composable
private fun AppIconButtonDarkPreview() = ComponentPreview(darkTheme = true) {
    AppIconButton(icon = ItsTime4ServiceIcons.ChevronLeft, onClick = {})
}