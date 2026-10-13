package ru.kuznetsov.pitstop.ui.components.dialogs

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.action_cancel
import pitstop.shared.generated.resources.action_save
import pitstop.shared.generated.resources.field_hours
import pitstop.shared.generated.resources.field_minutes
import pitstop.shared.generated.resources.reminder_time_dialog_text
import pitstop.shared.generated.resources.settings_reminder_time
import ru.kuznetsov.pitstop.ui.components.ComponentPreview
import ru.kuznetsov.pitstop.ui.components.steppers.Stepper
import ru.kuznetsov.pitstop.ui.icons.PitStopIcons
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

private const val HOURS_IN_DAY = 24
private const val MINUTES_IN_HOUR = 60
private const val MINUTE_STEP = 5

/** Modal picker of a time of day — hours and minutes on two steppers that wrap around, minutes in steps of five. */
@Composable
fun TimePickerDialog(
    title: String,
    text: String,
    time: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        TimePickerDialogCard(
            title = title,
            text = text,
            time = time,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }
}

@Composable
private fun TimePickerDialogCard(
    title: String,
    text: String,
    time: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PitStopTheme.colors
    val typography = PitStopTheme.typography
    val shape = RoundedCornerShape(PitStopTheme.dimens.radiusXxl)
    var hour by remember { mutableIntStateOf(time.hour) }
    var minute by remember { mutableIntStateOf(time.minute) }
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
            Icon(PitStopIcons.Clock, contentDescription = null, tint = colors.accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = typography.display.copy(fontSize = 17.sp, lineHeight = 21.sp), color = colors.ink)
        Spacer(Modifier.height(6.dp))
        Text(text, style = typography.sans.copy(fontSize = 12.5.sp, lineHeight = 19.sp), color = colors.inkMuted)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeRow(
                label = stringResource(Res.string.field_hours),
                value = hour,
                step = 1,
                onValueChange = { hour = it.mod(HOURS_IN_DAY) },
            )
            TimeRow(
                label = stringResource(Res.string.field_minutes),
                value = minute,
                step = MINUTE_STEP,
                onValueChange = { minute = it.mod(MINUTES_IN_HOUR) },
            )
        }
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
                onClick = { onConfirm(LocalTime(hour, minute)) },
            )
        }
    }
}

@Composable
private fun TimeRow(label: String, value: Int, step: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = PitStopTheme.typography.sans.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = PitStopTheme.colors.ink,
        )
        Stepper(
            value = value,
            onValueChange = onValueChange,
            step = step,
            valueText = { it.toString().padStart(2, '0') },
        )
    }
}

@Composable
private fun TimePickerDialogPreviewContent() {
    TimePickerDialogCard(
        title = stringResource(Res.string.settings_reminder_time),
        text = stringResource(Res.string.reminder_time_dialog_text),
        time = LocalTime(9, 0),
        onConfirm = {},
        onDismiss = {},
        modifier = Modifier.width(280.dp),
    )
}

@Preview
@Composable
private fun TimePickerDialogLightPreview() = ComponentPreview(darkTheme = false) {
    TimePickerDialogPreviewContent()
}

@Preview
@Composable
private fun TimePickerDialogDarkPreview() = ComponentPreview(darkTheme = true) {
    TimePickerDialogPreviewContent()
}
