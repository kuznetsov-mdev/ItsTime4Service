package ru.kuznetsov.itstime4service.ui.components.dialogs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.dialog_task_done_title
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Shell of a modal window — rounded surface card with a tinted icon, a title, an optional line of text and free-form [content] below. */
@Composable
fun DialogCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusXxl)
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

@Composable
private fun DialogCardPreviewContent() {
    DialogCard(
        icon = ItsTime4ServiceIcons.Check,
        title = stringResource(Res.string.dialog_task_done_title),
        text = "Воздушный фильтр",
        modifier = Modifier.width(280.dp),
    ) {}
}

@Preview
@Composable
private fun DialogCardLightPreview() = ComponentPreview(darkTheme = false) {
    DialogCardPreviewContent()
}

@Preview
@Composable
private fun DialogCardDarkPreview() = ComponentPreview(darkTheme = true) {
    DialogCardPreviewContent()
}
