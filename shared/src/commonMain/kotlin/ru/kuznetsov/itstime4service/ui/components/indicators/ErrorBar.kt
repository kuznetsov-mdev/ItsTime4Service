package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.save_failed
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * The "could not save" bar: inverted colors — `ink` background, `bg` text — with an alert sign in `tierDue`.
 * Shown while [visible]; [onShown] is called [durationMillis] after it appears, so the screen can drop the flag and
 * the bar goes away by itself. A screen raises the bar above its bottom button or the tab bar, a window — above its
 * buttons. The screen reader announces the message as soon as it appears.
 */
@Composable
fun ErrorBar(
    visible: Boolean,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Long = SHOWN_MS,
) {
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(visible) {
        if (visible) {
            delay(durationMillis)
            currentOnShown()
        }
    }
    if (!visible) return
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .clip(shape)
            .background(colors.ink)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ItsTime4ServiceIcons.CircleAlert, contentDescription = null, tint = colors.tierDue, modifier = Modifier.size(18.dp))
        Text(
            stringResource(Res.string.save_failed),
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            color = colors.bg,
        )
    }
}

private const val SHOWN_MS = 4_000L

@Preview
@Composable
private fun ErrorBarLightPreview() = ComponentPreview(darkTheme = false) {
    ErrorBar(visible = true, onShown = {})
}

@Preview
@Composable
private fun ErrorBarDarkPreview() = ComponentPreview(darkTheme = true) {
    ErrorBar(visible = true, onShown = {})
}
