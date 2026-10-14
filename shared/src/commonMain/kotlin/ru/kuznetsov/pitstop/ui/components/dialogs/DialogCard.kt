package ru.kuznetsov.pitstop.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

/** Shell of a modal window — rounded surface card with a tinted icon, a title, an optional line of text and free-form [content] below. */
@Composable
fun DialogCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PitStopTheme.colors
    val typography = PitStopTheme.typography
    val shape = RoundedCornerShape(PitStopTheme.dimens.radiusXxl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape)
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = typography.display.copy(fontSize = 17.sp, lineHeight = 21.sp), color = colors.ink)
        if (text != null) {
            Spacer(Modifier.height(6.dp))
            Text(text, style = typography.sans.copy(fontSize = 12.5.sp, lineHeight = 19.sp), color = colors.inkMuted)
        }
        content()
    }
}
