package ru.kuznetsov.itstime4service.ui.components.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.load_failed_text
import itstime4service.shared.generated.resources.load_failed_title
import itstime4service.shared.generated.resources.retry
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.components.buttons.OutlineButton
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * What a screen shows instead of its data when the data could not be read: an alert sign in a circle,
 * "Could not load the data", a line saying the records are still there, and "Retry", which calls [onRetry].
 * Centred in [modifier]; the screen keeps its top bar and the tab bar around it.
 */
@Composable
fun LoadError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    Box(modifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.rule, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ItsTime4ServiceIcons.CircleAlert, contentDescription = null, tint = colors.tierDue, modifier = Modifier.size(28.dp))
            }
            Text(
                stringResource(Res.string.load_failed_title),
                style = typography.sans.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                stringResource(Res.string.load_failed_text),
                style = typography.sans.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.inkFaint,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            OutlineButton(stringResource(Res.string.retry), onClick = onRetry)
        }
    }
}

@Preview
@Composable
private fun LoadErrorLightPreview() = ComponentPreview(darkTheme = false) {
    LoadError(onRetry = {})
}

@Preview
@Composable
private fun LoadErrorDarkPreview() = ComponentPreview(darkTheme = true) {
    LoadError(onRetry = {})
}
