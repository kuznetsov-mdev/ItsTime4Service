package ru.kuznetsov.itstime4service.ui.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_save_car
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Full-width-capable primary action button — accent background, used for form submits (e.g. "Save car"). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ItsTime4ServiceTheme.colors
    val dimens = ItsTime4ServiceTheme.dimens
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(dimens.radiusL))
            .background(colors.accent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(PaddingValues(horizontal = 22.dp, vertical = 15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = colors.onAccent,
        )
    }
}

@Preview
@Composable
private fun PrimaryButtonLightPreview() = ComponentPreview(darkTheme = false) {
    PrimaryButton(stringResource(Res.string.action_save_car), onClick = {})
}

@Preview
@Composable
private fun PrimaryButtonDarkPreview() = ComponentPreview(darkTheme = true) {
    PrimaryButton(stringResource(Res.string.action_save_car), onClick = {})
}
