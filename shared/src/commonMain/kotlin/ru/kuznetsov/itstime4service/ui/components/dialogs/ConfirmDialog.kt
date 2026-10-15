package ru.kuznetsov.itstime4service.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_delete
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = ItsTime4ServiceIcons.Trash,
    destructive: Boolean = true,
) {
    Dialog(onDismissRequest = onDismiss) {
        ConfirmDialogCard(
            title = title,
            text = text,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            icon = icon,
            destructive = destructive,
            modifier = modifier,
        )
    }
}

@Composable
private fun ConfirmDialogCard(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    destructive: Boolean = true,
) {
    val colors = ItsTime4ServiceTheme.colors
    val actionColor = if (destructive) colors.tierDue else colors.accent
    val onActionColor = if (destructive) colors.onTier else colors.onAccent
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
                .background(actionColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = actionColor, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = typography.display.copy(fontSize = 17.sp, lineHeight = 21.sp), color = colors.ink)
        Spacer(Modifier.height(6.dp))
        Text(text, style = typography.sans.copy(fontSize = 12.5.sp, lineHeight = 19.sp), color = colors.inkMuted)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton(dismissLabel, background = colors.surface2, contentColor = colors.ink, onClick = onDismiss)
            DialogButton(confirmLabel, background = actionColor, contentColor = onActionColor, onClick = onConfirm)
        }
    }
}

@Composable
internal fun RowScope.DialogButton(
    label: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusL))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConfirmDialogPreviewContent() {
    ConfirmDialogCard(
        title = "Kia Rio",
        text = "VIN XWEPC811BB0012345",
        confirmLabel = stringResource(Res.string.action_delete),
        dismissLabel = stringResource(Res.string.action_cancel),
        onConfirm = {},
        onDismiss = {},
        icon = ItsTime4ServiceIcons.Trash,
        modifier = Modifier.width(280.dp),
    )
}

@Preview
@Composable
private fun ConfirmDialogLightPreview() = ComponentPreview(darkTheme = false) {
    ConfirmDialogPreviewContent()
}

@Preview
@Composable
private fun ConfirmDialogDarkPreview() = ComponentPreview(darkTheme = true) {
    ConfirmDialogPreviewContent()
}
