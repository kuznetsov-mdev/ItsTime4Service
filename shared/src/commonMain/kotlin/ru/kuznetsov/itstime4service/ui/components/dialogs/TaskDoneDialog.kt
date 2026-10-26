package ru.kuznetsov.itstime4service.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_save
import itstime4service.shared.generated.resources.dialog_task_done_text
import itstime4service.shared.generated.resources.dialog_task_done_title
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.components.fields.ServiceRecordFields
import ru.kuznetsov.itstime4service.ui.components.indicators.ErrorBar
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

private const val MAX_COST_LENGTH = 7

/**
 * Modal shown when a task is marked as done — three optional fields: where it was serviced, the cost and a comment.
 * It stays open until the mark is stored; while [saveFailed] the "could not save" bar is shown above the buttons, and
 * [onSaveFailureShown] is called once it has been seen.
 */
@Composable
fun TaskDoneDialog(
    taskName: String,
    onConfirm: (serviceName: String, cost: String, comment: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    saveFailed: Boolean = false,
    onSaveFailureShown: () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        TaskDoneDialogCard(
            taskName = taskName,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            modifier = modifier,
            saveFailed = saveFailed,
            onSaveFailureShown = onSaveFailureShown,
        )
    }
}

@Composable
private fun TaskDoneDialogCard(
    taskName: String,
    onConfirm: (serviceName: String, cost: String, comment: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    saveFailed: Boolean = false,
    onSaveFailureShown: () -> Unit = {},
) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusXxl)
    var serviceName by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
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
            Icon(ItsTime4ServiceIcons.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(Res.string.dialog_task_done_title),
            style = typography.display.copy(fontSize = 17.sp, lineHeight = 21.sp),
            color = colors.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(Res.string.dialog_task_done_text, taskName),
            style = typography.sans.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
            color = colors.inkMuted,
        )
        DialogFields {
            ServiceRecordFields(
                serviceName = serviceName,
                cost = cost,
                comment = comment,
                onServiceNameChange = { serviceName = it },
                onCostChange = { cost = it.filter(Char::isDigit).take(MAX_COST_LENGTH) },
                onCommentChange = { comment = it },
                onDone = { onConfirm(serviceName, cost, comment) },
            )
        }
        ErrorBar(saveFailed, onSaveFailureShown, Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton(
                stringResource(Res.string.action_cancel),
                background = colors.surface2,
                contentColor = colors.ink,
                onClick = onDismiss,
            )
            DialogButton(
                stringResource(Res.string.action_save),
                background = colors.accent,
                contentColor = colors.onAccent,
                onClick = { onConfirm(serviceName, cost, comment) },
            )
        }
    }
}

@Composable
private fun TaskDoneDialogPreviewContent() {
    TaskDoneDialogCard(
        taskName = "Воздушный фильтр",
        onConfirm = { _, _, _ -> },
        onDismiss = {},
        modifier = Modifier.width(280.dp),
    )
}

@Preview
@Composable
private fun TaskDoneDialogLightPreview() = ComponentPreview(darkTheme = false) {
    TaskDoneDialogPreviewContent()
}

@Preview
@Composable
private fun TaskDoneDialogDarkPreview() = ComponentPreview(darkTheme = true) {
    TaskDoneDialogPreviewContent()
}
