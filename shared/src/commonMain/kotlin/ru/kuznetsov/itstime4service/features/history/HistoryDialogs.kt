package ru.kuznetsov.itstime4service.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add
import itstime4service.shared.generated.resources.action_apply
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_filter
import itstime4service.shared.generated.resources.action_more_entry_fields
import itstime4service.shared.generated.resources.action_reset
import itstime4service.shared.generated.resources.action_save
import itstime4service.shared.generated.resources.action_select_car
import itstime4service.shared.generated.resources.date_placeholder
import itstime4service.shared.generated.resources.dialog_new_entry_text
import itstime4service.shared.generated.resources.dialog_new_entry_title
import itstime4service.shared.generated.resources.field_car
import itstime4service.shared.generated.resources.field_date
import itstime4service.shared.generated.resources.field_mileage
import itstime4service.shared.generated.resources.field_mileage_range
import itstime4service.shared.generated.resources.field_period
import itstime4service.shared.generated.resources.field_search_task
import itstime4service.shared.generated.resources.field_task
import itstime4service.shared.generated.resources.history_sold_car
import itstime4service.shared.generated.resources.search_task_placeholder
import itstime4service.shared.generated.resources.task_name_placeholder
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.components.buttons.PresetChip
import ru.kuznetsov.itstime4service.ui.components.dialogs.DialogButton
import ru.kuznetsov.itstime4service.ui.components.dialogs.DialogCard
import ru.kuznetsov.itstime4service.ui.components.fields.LabeledMiniField
import ru.kuznetsov.itstime4service.ui.components.fields.ServiceRecordFields
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import ru.kuznetsov.itstime4service.ui.format.unitLabel
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

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
    val colors = ItsTime4ServiceTheme.colors
    DialogCard(icon = ItsTime4ServiceIcons.Filter, title = stringResource(Res.string.action_filter), modifier = modifier) {
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                LabeledMiniField(
                    label = stringResource(Res.string.field_period),
                    value = filter.dateFrom,
                    onValueChange = { onChange(filter.copy(dateFrom = it)) },
                    onDone = onApply,
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
                    onDone = onApply,
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
                onDone = onApply,
                placeholder = stringResource(Res.string.search_task_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                LabeledMiniField(
                    label = stringResource(Res.string.field_mileage_range, unitLabel(distanceUnit)),
                    value = filter.mileageFrom,
                    onValueChange = { onChange(filter.copy(mileageFrom = it)) },
                    onDone = onApply,
                    placeholder = "0",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                LabeledMiniField(
                    label = "",
                    value = filter.mileageTo,
                    onValueChange = { onChange(filter.copy(mileageTo = it)) },
                    onDone = onApply,
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
    val colors = ItsTime4ServiceTheme.colors
    DialogCard(icon = ItsTime4ServiceIcons.Wrench, title = form.taskName, text = form.car.label(), modifier = modifier) {
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                LabeledMiniField(
                    label = stringResource(Res.string.field_date),
                    value = form.date,
                    onValueChange = { onChange(form.copy(date = it)) },
                    onDone = onSave,
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
                    onDone = onSave,
                    placeholder = "78000",
                    keyboardType = KeyboardType.Number,
                    isError = form.showErrors && form.isMileageInvalid,
                    required = true,
                    modifier = Modifier.weight(1f),
                )
            }
            ServiceRecordFields(
                serviceName = form.serviceName,
                cost = form.cost,
                comment = form.comment,
                onServiceNameChange = { onChange(form.copy(serviceName = it)) },
                onCostChange = { onChange(form.copy(cost = it)) },
                onCommentChange = { onChange(form.copy(comment = it)) },
                onDone = onSave,
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

@Composable
internal fun NewHistoryEntryDialog(
    form: NewHistoryEntryForm,
    cars: List<HistoryCar>,
    suggestions: List<String>,
    distanceUnit: DistanceUnit,
    onChange: (NewHistoryEntryForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NewHistoryEntryCard(
            form = form,
            cars = cars,
            suggestions = suggestions,
            distanceUnit = distanceUnit,
            onChange = onChange,
            onSave = onSave,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewHistoryEntryCard(
    form: NewHistoryEntryForm,
    cars: List<HistoryCar>,
    suggestions: List<String>,
    distanceUnit: DistanceUnit,
    onChange: (NewHistoryEntryForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    DialogCard(
        icon = ItsTime4ServiceIcons.Plus,
        title = stringResource(Res.string.dialog_new_entry_title),
        text = stringResource(Res.string.dialog_new_entry_text),
        modifier = modifier,
    ) {
        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CarPickerField(form = form, cars = cars, onChange = onChange)
            Column {
                LabeledMiniField(
                    label = stringResource(Res.string.field_task),
                    value = form.taskName,
                    onValueChange = { onChange(form.copy(taskName = it)) },
                    onDone = onSave,
                    placeholder = stringResource(Res.string.task_name_placeholder),
                    isError = form.showErrors && form.isNameMissing,
                    required = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        suggestions.forEach { name ->
                            PresetChip(label = name, onClick = { onChange(form.copy(taskName = name)) })
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                LabeledMiniField(
                    label = stringResource(Res.string.field_date),
                    value = form.date,
                    onValueChange = { onChange(form.copy(date = it)) },
                    onDone = onSave,
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
                    onDone = onSave,
                    placeholder = "61500",
                    keyboardType = KeyboardType.Number,
                    isError = form.showErrors && form.isMileageInvalid,
                    required = true,
                    modifier = Modifier.weight(1f),
                )
            }
            if (form.isExpanded) {
                ServiceRecordFields(
                    serviceName = form.serviceName,
                    cost = form.cost,
                    comment = form.comment,
                    onServiceNameChange = { onChange(form.copy(serviceName = it)) },
                    onCostChange = { onChange(form.copy(cost = it)) },
                    onCommentChange = { onChange(form.copy(comment = it)) },
                    onDone = onSave,
                )
            } else {
                Text(
                    stringResource(Res.string.action_more_entry_fields),
                    style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM))
                        .clickable(role = Role.Button) { onChange(form.copy(isExpanded = true)) }
                        .padding(vertical = 6.dp),
                )
            }
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
                stringResource(Res.string.action_add),
                background = colors.accent,
                contentColor = colors.onAccent,
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun CarPickerField(
    form: NewHistoryEntryForm,
    cars: List<HistoryCar>,
    onChange: (NewHistoryEntryForm) -> Unit,
) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM)
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    Column {
        Text(
            buildAnnotatedString {
                append(stringResource(Res.string.field_car))
                withStyle(SpanStyle(color = colors.tierDue)) { append(" *") }
            },
            style = typography.sans.copy(fontSize = 10.5.sp),
            color = colors.inkFaint,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box(Modifier.fillMaxWidth().onSizeChanged { fieldWidthPx = it.width }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface2)
                    .border(1.dp, colors.rule, shape)
                    .clickable(onClickLabel = stringResource(Res.string.action_select_car)) {
                        onChange(form.copy(isCarMenuOpen = !form.isCarMenuOpen))
                    }
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    cars.firstOrNull { it.id == form.carId }?.brand.orEmpty(),
                    style = typography.sans.copy(fontSize = 13.sp),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    ItsTime4ServiceIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.inkFaint,
                    modifier = Modifier.size(14.dp).rotate(if (form.isCarMenuOpen) 180f else 0f),
                )
            }
            DropdownMenu(
                expanded = form.isCarMenuOpen,
                onDismissRequest = { onChange(form.copy(isCarMenuOpen = false)) },
                containerColor = colors.surface,
                modifier = Modifier.width(with(LocalDensity.current) { fieldWidthPx.toDp() }),
            ) {
                cars.forEach { car ->
                    val selected = car.id == form.carId
                    DropdownMenuItem(
                        text = {
                            Text(
                                car.brand,
                                style = typography.sans.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (selected) colors.accent else colors.ink,
                            )
                        },
                        onClick = { onChange(form.copy(carId = car.id, isCarMenuOpen = false)) },
                    )
                }
            }
        }
    }
}
