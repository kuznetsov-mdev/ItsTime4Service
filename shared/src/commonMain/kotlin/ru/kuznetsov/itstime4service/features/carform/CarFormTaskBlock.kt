package ru.kuznetsov.itstime4service.features.carform

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_collapse_task
import itstime4service.shared.generated.resources.action_expand_task
import itstime4service.shared.generated.resources.action_remove_task
import itstime4service.shared.generated.resources.date_placeholder
import itstime4service.shared.generated.resources.field_interval_months
import itstime4service.shared.generated.resources.field_last_done
import itstime4service.shared.generated.resources.field_last_done_date
import itstime4service.shared.generated.resources.field_reminder_window
import itstime4service.shared.generated.resources.field_task_interval
import itstime4service.shared.generated.resources.task_block_title
import itstime4service.shared.generated.resources.task_create_record
import itstime4service.shared.generated.resources.task_every
import itstime4service.shared.generated.resources.task_interval_missing
import itstime4service.shared.generated.resources.task_last_done
import itstime4service.shared.generated.resources.task_last_done_missing
import itstime4service.shared.generated.resources.task_name_placeholder
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.components.buttons.AppIconButton
import ru.kuznetsov.itstime4service.ui.components.fields.AppTextField
import ru.kuznetsov.itstime4service.ui.components.fields.LabeledMiniField
import ru.kuznetsov.itstime4service.ui.components.fields.ServiceRecordFields
import ru.kuznetsov.itstime4service.ui.components.indicators.AppCheckbox
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import ru.kuznetsov.itstime4service.ui.format.TaskFields
import ru.kuznetsov.itstime4service.ui.format.grouped
import ru.kuznetsov.itstime4service.ui.format.unitGenitive
import ru.kuznetsov.itstime4service.ui.format.unitLabel
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TaskBlock(
    number: Int,
    fields: TaskFields,
    isCollapsed: Boolean,
    showErrors: Boolean,
    distanceUnit: DistanceUnit,
    onChange: (TaskFields) -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onRecordToggle: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusL)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { bringIntoViewRequester.bringIntoView() }
    Column(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, if (isCollapsed && showErrors && !fields.isValid) colors.tierDue else colors.rule, shape)
            .then(if (isCollapsed) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCollapsed) 4.dp else 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCollapsed) {
                Text(
                    fields.name.ifBlank { stringResource(Res.string.task_block_title, number) },
                    style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    stringResource(Res.string.task_block_title, number).uppercase(),
                    style = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04f.em),
                    color = colors.inkMuted,
                    modifier = Modifier.weight(1f),
                )
            }
            AppIconButton(
                icon = ItsTime4ServiceIcons.ChevronDown,
                onClick = onToggle,
                contentDescription = stringResource(if (isCollapsed) Res.string.action_expand_task else Res.string.action_collapse_task),
                iconSize = 14.dp,
                modifier = Modifier.rotate(if (isCollapsed) 0f else 180f),
            )
            AppIconButton(
                icon = ItsTime4ServiceIcons.Trash,
                onClick = onRemove,
                contentDescription = stringResource(Res.string.action_remove_task),
                iconSize = 14.dp,
            )
        }
        if (isCollapsed) {
            Text(
                collapsedSummary(fields, distanceUnit),
                style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            return@Column
        }
        AppTextField(
            value = fields.name,
            onValueChange = { onChange(fields.copy(name = it)) },
            onDone = onDone,
            placeholder = stringResource(Res.string.task_name_placeholder),
            isError = showErrors && fields.isNameMissing,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            LabeledMiniField(
                label = stringResource(Res.string.field_task_interval, unitLabel(distanceUnit)),
                value = fields.intervalKm,
                onValueChange = { onChange(fields.copy(intervalKm = it)) },
                onDone = onDone,
                placeholder = "10000",
                keyboardType = KeyboardType.Number,
                isError = showErrors && fields.isIntervalMissing,
                required = true,
                modifier = Modifier.weight(1f),
            )
            LabeledMiniField(
                label = stringResource(Res.string.field_interval_months),
                value = fields.intervalMonths,
                onValueChange = { onChange(fields.copy(intervalMonths = it)) },
                onDone = onDone,
                placeholder = "12",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            LabeledMiniField(
                label = stringResource(Res.string.field_last_done, unitLabel(distanceUnit)),
                value = fields.lastDoneKm,
                onValueChange = { onChange(fields.copy(lastDoneKm = it)) },
                onDone = onDone,
                placeholder = "78000",
                keyboardType = KeyboardType.Number,
                isError = showErrors && fields.isLastDoneMissing,
                required = fields.createsRecord,
                modifier = Modifier.weight(1f),
            )
            LabeledMiniField(
                label = stringResource(Res.string.field_last_done_date),
                value = fields.lastDoneDate,
                onValueChange = { onChange(fields.copy(lastDoneDate = it)) },
                onDone = onDone,
                placeholder = stringResource(Res.string.date_placeholder),
                keyboardType = KeyboardType.Number,
                isError = showErrors && fields.isLastDoneDateMissing,
                required = fields.createsRecord,
                visualTransformation = DateInputFormat.current.visualTransformation,
                modifier = Modifier.weight(1f),
            )
        }
        AppCheckbox(
            checked = fields.createsRecord,
            onCheckedChange = onRecordToggle,
            label = stringResource(Res.string.task_create_record),
        )
        if (fields.createsRecord) {
            ServiceRecordFields(
                serviceName = fields.serviceName,
                cost = fields.cost,
                comment = fields.comment,
                onServiceNameChange = { onChange(fields.copy(serviceName = it)) },
                onCostChange = { onChange(fields.copy(cost = it)) },
                onCommentChange = { onChange(fields.copy(comment = it)) },
                onDone = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabeledMiniField(
            label = stringResource(Res.string.field_reminder_window, unitGenitive(distanceUnit)),
            value = fields.reminderWindowKm,
            onValueChange = { onChange(fields.copy(reminderWindowKm = it)) },
            onDone = onDone,
            placeholder = "1500",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun collapsedSummary(fields: TaskFields, distanceUnit: DistanceUnit): String {
    val interval = fields.intervalKm.toIntOrNull()?.takeIf { it > 0 }
    val lastDone = fields.lastDoneKm.toIntOrNull()
    val unit = unitGenitive(distanceUnit)
    return listOf(
        interval?.let { stringResource(Res.string.task_every, "${it.grouped()} $unit") }
            ?: stringResource(Res.string.task_interval_missing),
        lastDone?.let { stringResource(Res.string.task_last_done, "${it.grouped()} $unit") }
            ?: stringResource(Res.string.task_last_done_missing),
    ).joinToString(" · ")
}
