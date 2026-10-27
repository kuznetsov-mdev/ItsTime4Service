package ru.kuznetsov.itstime4service.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.dialog_task_icon_text
import itstime4service.shared.generated.resources.dialog_task_icon_title
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.label
import ru.kuznetsov.itstime4service.ui.icons.vector
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
fun TaskIconDialog(
    selected: TaskIcon,
    onSelect: (TaskIcon) -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(onDismiss = onDismiss) {
        TaskIconDialogCard(selected = selected, onSelect = onSelect, onDismiss = onDismiss)
    }
}

@Composable
private fun TaskIconDialogCard(
    selected: TaskIcon,
    onSelect: (TaskIcon) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    DialogCard(
        icon = selected.vector,
        title = stringResource(Res.string.dialog_task_icon_title),
        text = stringResource(Res.string.dialog_task_icon_text),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TaskIcon.entries.chunked(ICONS_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { icon ->
                        IconTile(icon = icon, isSelected = icon == selected, onClick = { onSelect(icon) })
                    }
                }
            }
        }
        Row {
            DialogButton(
                stringResource(Res.string.action_cancel),
                background = colors.surface2,
                contentColor = colors.ink,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun RowScope.IconTile(icon: TaskIcon, isSelected: Boolean, onClick: () -> Unit) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(9.dp)
    val label = stringResource(icon.label)
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(shape)
            .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else colors.surface2)
            .border(1.5.dp, if (isSelected) colors.accent else Color.Transparent, shape)
            .semantics { selected = isSelected }
            .clickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon.vector, contentDescription = label, tint = if (isSelected) colors.accent else colors.inkMuted, modifier = Modifier.size(16.dp))
    }
}

private const val ICONS_PER_ROW = 6

@Preview
@Composable
private fun TaskIconDialogLightPreview() = ComponentPreview(darkTheme = false) {
    TaskIconDialogCard(selected = TaskIcon.DROPLET, onSelect = {}, onDismiss = {}, modifier = Modifier.width(300.dp))
}

@Preview
@Composable
private fun TaskIconDialogDarkPreview() = ComponentPreview(darkTheme = true) {
    TaskIconDialogCard(selected = TaskIcon.DROPLET, onSelect = {}, onDismiss = {}, modifier = Modifier.width(300.dp))
}
