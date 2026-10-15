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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Circular floating action button — primary action of a list screen (e.g. add car). */
@Composable
fun Fab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = ItsTime4ServiceTheme.colors
    Box(
        modifier = modifier
            .size(48.dp)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = colors.accent.copy(alpha = 0.4f),
                spotColor = colors.accent.copy(alpha = 0.55f),
            )
            .clip(CircleShape)
            .background(colors.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = colors.onAccent,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Preview
@Composable
private fun FabLightPreview() = ComponentPreview(darkTheme = false) {
    Fab(icon = ItsTime4ServiceIcons.Plus, onClick = {})
}

@Preview
@Composable
private fun FabDarkPreview() = ComponentPreview(darkTheme = true) {
    Fab(icon = ItsTime4ServiceIcons.Plus, onClick = {})
}