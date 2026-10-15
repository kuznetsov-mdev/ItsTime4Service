package ru.kuznetsov.itstime4service.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.app_name
import itstime4service.shared.generated.resources.notification_sample_text
import itstime4service.shared.generated.resources.notification_time_now
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Static look-alike of a push notification — the example shown in settings, with an optional [caption] note. */
@Composable
fun NotificationPreviewCard(
    title: String,
    time: String,
    text: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val colors = ItsTime4ServiceTheme.colors
    val sans = ItsTime4ServiceTheme.typography.sans
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusL)
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(29.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ItsTime4ServiceIcons.Bell, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = sans.copy(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    time,
                    style = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 9.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal),
                    color = colors.inkFaint,
                )
            }
            Text(
                text,
                style = sans.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                color = colors.inkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (caption != null) {
                Text(
                    caption,
                    style = sans.copy(fontSize = 10.5.sp, lineHeight = 15.sp),
                    color = colors.inkFaint,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun NotificationPreviewCardLightPreview() = ComponentPreview(darkTheme = false) {
    NotificationPreviewCard(
        title = stringResource(Res.string.app_name),
        time = stringResource(Res.string.notification_time_now),
        text = stringResource(Res.string.notification_sample_text),
        modifier = Modifier.width(300.dp),
    )
}

@Preview
@Composable
private fun NotificationPreviewCardDarkPreview() = ComponentPreview(darkTheme = true) {
    NotificationPreviewCard(
        title = stringResource(Res.string.app_name),
        time = stringResource(Res.string.notification_time_now),
        text = stringResource(Res.string.notification_sample_text),
        modifier = Modifier.width(300.dp),
    )
}
