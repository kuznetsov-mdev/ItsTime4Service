package ru.kuznetsov.itstime4service.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_apply
import itstime4service.shared.generated.resources.action_filter
import itstime4service.shared.generated.resources.action_reset
import itstime4service.shared.generated.resources.date_placeholder
import itstime4service.shared.generated.resources.field_mileage_range
import itstime4service.shared.generated.resources.field_period
import itstime4service.shared.generated.resources.field_search_task
import itstime4service.shared.generated.resources.search_task_placeholder
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.components.dialogs.AppDialog
import ru.kuznetsov.itstime4service.ui.components.dialogs.DialogButton
import ru.kuznetsov.itstime4service.ui.components.dialogs.DialogCard
import ru.kuznetsov.itstime4service.ui.components.dialogs.DialogFields
import ru.kuznetsov.itstime4service.ui.components.fields.LabeledMiniField
import ru.kuznetsov.itstime4service.ui.format.DateInputFormat
import ru.kuznetsov.itstime4service.ui.format.unitLabel
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
internal fun HistoryFilterDialog(
    filter: HistoryFilter,
    distanceUnit: DistanceUnit,
    onChange: (HistoryFilter) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(onDismiss = onDismiss) {
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
        DialogFields {
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
private fun HistoryFilterPreviewContent() {
    HistoryFilterCard(
        filter = HistoryFilter(query = "масл", mileageFrom = "60000"),
        distanceUnit = DistanceUnit.KILOMETERS,
        onChange = {},
        onApply = {},
        onReset = {},
    )
}

@Preview
@Composable
private fun HistoryFilterLightPreview() = ComponentPreview(darkTheme = false) {
    HistoryFilterPreviewContent()
}

@Preview
@Composable
private fun HistoryFilterDarkPreview() = ComponentPreview(darkTheme = true) {
    HistoryFilterPreviewContent()
}
