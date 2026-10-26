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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_more_entry_fields
import itstime4service.shared.generated.resources.action_select_car
import itstime4service.shared.generated.resources.date_placeholder
import itstime4service.shared.generated.resources.dialog_new_entry_text
import itstime4service.shared.generated.resources.dialog_new_entry_title
import itstime4service.shared.generated.resources.field_car
import itstime4service.shared.generated.resources.field_date
import itstime4service.shared.generated.resources.field_mileage
import itstime4service.shared.generated.resources.field_task
import itstime4service.shared.generated.resources.task_name_placeholder
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.components.buttons.PresetChip
import ru.kuznetsov.itstime4service.ui.components.dialogs.DialogButton
import ru.kuznetsov.itstime4service.ui.components.dialogs.DialogCard
import ru.kuznetsov.itstime4service.ui.components.dialogs.DialogFields
import ru.kuznetsov.itstime4service.ui.components.indicators.ErrorBar
import ru.kuznetsov.itstime4service.ui.components.fields.LabeledMiniField
import ru.kuznetsov.itstime4service.ui.components.fields.ServiceRecordFields
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import ru.kuznetsov.itstime4service.ui.format.unitLabel
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
internal fun NewHistoryEntryDialog(
    form: NewHistoryEntryForm,
    cars: List<HistoryCar>,
    suggestions: List<String>,
    distanceUnit: DistanceUnit,
    onChange: (NewHistoryEntryForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    saveFailed: Boolean = false,
    onSaveFailureShown: () -> Unit = {},
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
            saveFailed = saveFailed,
            onSaveFailureShown = onSaveFailureShown,
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
    saveFailed: Boolean = false,
    onSaveFailureShown: () -> Unit = {},
) {
    val colors = ItsTime4ServiceTheme.colors
    DialogCard(
        icon = ItsTime4ServiceIcons.Plus,
        title = stringResource(Res.string.dialog_new_entry_title),
        text = stringResource(Res.string.dialog_new_entry_text),
        modifier = modifier,
    ) {
        DialogFields {
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

@Composable
private fun NewHistoryEntryPreviewContent() {
    NewHistoryEntryCard(
        form = NewHistoryEntryForm(carId = "c1", mileage = "61500", isExpanded = true),
        cars = listOf(HistoryCar("c1", "Kia Rio"), HistoryCar("c2", "Toyota Camry")),
        suggestions = listOf("Замена масла и фильтра", "Тормозные колодки"),
        distanceUnit = DistanceUnit.KILOMETERS,
        onChange = {},
        onSave = {},
        onDismiss = {},
    )
}

@Preview
@Composable
private fun NewHistoryEntryLightPreview() = ComponentPreview(darkTheme = false) {
    NewHistoryEntryPreviewContent()
}

@Preview
@Composable
private fun NewHistoryEntryDarkPreview() = ComponentPreview(darkTheme = true) {
    NewHistoryEntryPreviewContent()
}
