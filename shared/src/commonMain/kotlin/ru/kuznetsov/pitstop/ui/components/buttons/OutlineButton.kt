package ru.kuznetsov.pitstop.ui.components.buttons

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.action_add_more
import ru.kuznetsov.pitstop.ui.components.ComponentPreview
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

/** Secondary action next to [PrimaryButton] — same size and corners, accent text and border on the screen background (e.g. "Add another"). */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PitStopTheme.colors
    val shape = RoundedCornerShape(PitStopTheme.dimens.radiusL)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.bg)
            .border(1.5.dp, colors.accent, shape)
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 22.dp, vertical = 15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = PitStopTheme.typography.sans.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = colors.accent,
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
