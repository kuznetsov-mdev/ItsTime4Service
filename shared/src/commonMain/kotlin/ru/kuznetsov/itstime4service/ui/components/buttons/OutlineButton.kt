package ru.kuznetsov.itstime4service.ui.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add_more
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Secondary action next to [PrimaryButton] — same size and corners, accent text and border on the screen background (e.g. "Add another"). */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusL)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.bg)
            .border(1.5.dp, colors.accent, shape)
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = if (compact) 6.dp else 22.dp, vertical = 15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Bold),
            color = colors.accent,
            maxLines = if (compact) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun OutlineButtonLightPreview() = ComponentPreview(darkTheme = false) {
    OutlineButton(stringResource(Res.string.action_add_more), onClick = {})
}

@Preview
@Composable
private fun OutlineButtonDarkPreview() = ComponentPreview(darkTheme = true) {
    OutlineButton(stringResource(Res.string.action_add_more), onClick = {})
}
