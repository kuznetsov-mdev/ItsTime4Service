package ru.kuznetsov.itstime4service.ui.components.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_choose_date
import itstime4service.shared.generated.resources.action_clear
import itstime4service.shared.generated.resources.action_done
import itstime4service.shared.generated.resources.date_placeholder
import itstime4service.shared.generated.resources.field_required
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.ui.components.dialogs.AppDialog
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
fun LabeledDateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    required: Boolean = false,
    accessibilityLabel: String = label,
) {
    Column(modifier) {
        MiniFieldLabel(label, required)
        AppDateField(value, onValueChange, accessibilityLabel, Modifier.fillMaxWidth(), isError)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    var isOpen by rememberSaveable { mutableStateOf(false) }
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM)
    val format = DateInputFormat.current
    val focusManager = LocalFocusManager.current
    val chooseDate = stringResource(Res.string.action_choose_date)
    val requiredError = stringResource(Res.string.field_required)
    Row(
        modifier.clip(shape)
            .background(colors.surface2)
            .border(1.dp, if (isError) colors.tierDue else colors.rule, shape)
            .clickable(role = Role.Button, onClickLabel = chooseDate) {
                focusManager.clearFocus()
                isOpen = true
            }
            .semantics {
                contentDescription = label
                if (isError) error(requiredError)
            }
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (value.isEmpty()) stringResource(Res.string.date_placeholder)
            else format.visualTransformation.filter(AnnotatedString(value)).text.text,
            style = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 13.sp),
            color = if (value.isEmpty()) colors.inkFaint else colors.ink,
            modifier = Modifier.weight(1f),
        )
        Icon(ItsTime4ServiceIcons.Calendar, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
    }
    if (isOpen) {
        val windowSize = LocalWindowInfo.current.containerSize
        val density = LocalDensity.current
        val dialogWidth = minOf(360.dp, with(density) { windowSize.width.toDp() } - 16.dp)
        val dialogHeight = with(density) { windowSize.height.toDp() } - 16.dp
        val initial = format.parse(value)?.takeIf { it.year in 1..9999 }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
            yearRange = 1..9999,
        )
        AppDialog(onDismiss = { isOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.width(dialogWidth).heightIn(max = dialogHeight).semantics { paneTitle = chooseDate },
                shape = DatePickerDefaults.shape,
                color = colors.surface,
            ) {
                Column {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides minOf(48.dp, (dialogWidth - 24.dp) / 7)) {
                        DatePicker(
                            state = state,
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            colors = DatePickerDefaults.colors(containerColor = colors.surface),
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                        if (value.isNotEmpty()) {
                            TextButton(onClick = { onValueChange(""); isOpen = false }) {
                                Text(stringResource(Res.string.action_clear))
                            }
                        }
                        TextButton(onClick = { isOpen = false }) { Text(stringResource(Res.string.action_cancel)) }
                        TextButton(
                            enabled = state.selectedDateMillis != null,
                            onClick = {
                                state.selectedDateMillis?.let { millis ->
                                    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                                    onValueChange(format.digitsOf(date))
                                    isOpen = false
                                }
                            },
                        ) { Text(stringResource(Res.string.action_done)) }
                    }
                }
            }
        }
    }
}
