package ru.kuznetsov.pitstop.features.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.action_apply
import pitstop.shared.generated.resources.action_cancel
import pitstop.shared.generated.resources.action_filter
import pitstop.shared.generated.resources.action_reset
import pitstop.shared.generated.resources.action_save
import pitstop.shared.generated.resources.comment_placeholder
import pitstop.shared.generated.resources.date_placeholder
import pitstop.shared.generated.resources.field_comment
import pitstop.shared.generated.resources.field_cost_rub
import pitstop.shared.generated.resources.field_date
import pitstop.shared.generated.resources.field_mileage
import pitstop.shared.generated.resources.field_mileage_range
import pitstop.shared.generated.resources.field_period
import pitstop.shared.generated.resources.field_search_task
import pitstop.shared.generated.resources.field_service_name
import pitstop.shared.generated.resources.history_sold_car
import pitstop.shared.generated.resources.search_task_placeholder
import pitstop.shared.generated.resources.service_name_placeholder
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.ui.components.dialogs.DialogButton
import ru.kuznetsov.pitstop.ui.components.dialogs.DialogCard
import ru.kuznetsov.pitstop.ui.components.fields.LabeledMiniField
import ru.kuznetsov.pitstop.ui.format.DateInputFormat
import ru.kuznetsov.pitstop.ui.format.unitLabel
import ru.kuznetsov.pitstop.ui.icons.PitStopIcons
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

@Composable
internal fun HistoryCar.label(): String = if (isSold) stringResource(Res.string.history_sold_car, brand) else brand

@Composable
internal fun HistoryFilterDialog(
    filter: HistoryFilter,
    distanceUnit: DistanceUnit,
    onChange: (HistoryFilter) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        HistoryFilterCard(filter = filter, distanceUnit = distanceUnit, onChange = onChange, onApply = onApply, onReset = onReset)
    }
}

@Composable
internal fun HistoryFilterCard(
    filter: HistoryFilter,
    distanceUnit: DistanceUnit,
    onChange: (HistoryFilter) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PitStopTheme.colors
    DialogCard(icon = PitStopIcons.Filter, title = stringResource(Res.string.action_filter), modifier = modifier) {
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                LabeledMiniField(
                    label = stringResource(Res.string.field_period),
                    value = filter.dateFrom,
                    onValueChange = { onChange(filter.copy(dateFrom = it)) },
                    placeholder = stringResource(Res.string.date_placeholder),
                    keyboardType = KeyboardType.Number,
                    visualTransformation = DateInputFormat.current.visualTransformation,
                    isError = filter.showErrors && filter.isDateFromInvalid,
                    modifier = Modifier.weight(1f),
                )
                LabeledMiniField(
                    label = "",
                    value = filter.dateTo,
                    onValueChange = { onChange(filter.copy(dateTo = it)) },
                    placeholder = stringResource(Res.string.date_placeholder),
                    keyboardType = KeyboardType.Number,
                    visualTransformation = DateInputFormat.current.visualTransformation,
                    isError = filter.showErrors && filter.isDateToInvalid,
                    modifier = Modifier.weight(1f),
                )
            }
            LabeledMiniField(
                label = stringResource(Res.string.field_search_task),
                value = filter.query,
                onValueChange = { onChange(filter.copy(query = it)) },
                placeholder = stringResource(Res.string.search_task_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                LabeledMiniField(
                    label = stringResource(Res.string.field_mileage_range, unitLabel(distanceUnit)),
                    value = filter.mileageFrom,
                    onValueChange = { onChange(filter.copy(mileageFrom = it)) },
                    placeholder = "0",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                LabeledMiniField(
                    label = "",
                    value = filter.mileageTo,
                    onValueChange = { onChange(filter.copy(mileageTo = it)) },
                    placeholder = "100000",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton(
                stringResource(Res.string.action_reset),
                background = colors.surface2,
                contentColor = colors.ink,
                onClick = onReset,
            )
            DialogButton(
                stringResource(Res.string.action_apply),
                background = colors.accent,
                contentColor = colors.onAccent,
                onClick = onApply,
            )
        }
    }
}

@Composable
internal fun HistoryEntryDialog(
    form: HistoryEntryForm,
    distanceUnit: DistanceUnit,
    onChange: (HistoryEntryForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        HistoryEntryCard(form = form, distanceUnit = distanceUnit, onChange = onChange, onSave = onSave, onDismiss = onDismiss)
    }
}

@Composable
internal fun HistoryEntryCard(
    form: HistoryEntryForm,
    distanceUnit: DistanceUnit,
    onChange: (HistoryEntryForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PitStopTheme.colors
    DialogCard(icon = PitStopIcons.Wrench, title = form.taskName, text = form.car.label(), modifier = modifier) {
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                LabeledMiniField(
                    label = stringResource(Res.string.field_date),
                    value = form.date,
                    onValueChange = { onChange(form.copy(date = it)) },
                    placeholder = stringResource(Res.string.date_placeholder),
                    keyboardType = KeyboardType.Number,
                    visualTransformation = DateInputFormat.current.visualTransformation,
                    isError = form.showErrors && form.isDateInvalid,
                    required = true,
                    modifier = Modifier.weight(1f),
                )
                LabeledMiniField(
                    label = stringResource(Res.string.field_mileage, unitLabel(distanceUnit)),
                    value = form.mileage,
                    onValueChange = { onChange(form.copy(mileage = it)) },
                    placeholder = "78000",
                    keyboardType = KeyboardType.Number,
                    isError = form.showErrors && form.isMileageInvalid,
                    required = true,
                    modifier = Modifier.weight(1f),
                )
            }
            LabeledMiniField(
                label = stringResource(Res.string.field_service_name),
                value = form.serviceName,
                onValueChange = { onChange(form.copy(serviceName = it)) },
                placeholder = stringResource(Res.string.service_name_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledMiniField(
                label = stringResource(Res.string.field_cost_rub),
                value = form.cost,
                onValueChange = { onChange(form.copy(cost = it)) },
                placeholder = "2400",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledMiniField(
                label = stringResource(Res.string.field_comment),
                value = form.comment,
                onValueChange = { onChange(form.copy(comment = it)) },
                placeholder = stringResource(Res.string.comment_placeholder),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
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
                onClick = onSave,
            )
        }
    }
}
