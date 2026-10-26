package ru.kuznetsov.itstime4service.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_save
import itstime4service.shared.generated.resources.date_placeholder
import itstime4service.shared.generated.resources.field_date
import itstime4service.shared.generated.resources.field_mileage
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
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
internal fun HistoryEntryDialog(
    form: HistoryEntryForm,
    distanceUnit: DistanceUnit,
    onChange: (HistoryEntryForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    saveFailed: Boolean = false,
    onSaveFailureShown: () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        HistoryEntryCard(
            form = form,
            distanceUnit = distanceUnit,
            onChange = onChange,
            onSave = onSave,
            onDismiss = onDismiss,
            saveFailed = saveFailed,
            onSaveFailureShown = onSaveFailureShown,
        )
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
    saveFailed: Boolean = false,
    onSaveFailureShown: () -> Unit = {},
) {
    val colors = ItsTime4ServiceTheme.colors
    DialogCard(icon = ItsTime4ServiceIcons.Wrench, title = form.taskName, text = form.car.label(), modifier = modifier) {
        DialogFields {
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
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun HistoryEntryPreviewContent() {
    HistoryEntryCard(
        form = HistoryEntryForm("h1", "Замена масла и фильтра", HistoryCar("c1", "Kia Rio"), "14092026", "82000", "Kia Center", "4200"),
        distanceUnit = DistanceUnit.KILOMETERS,
        onChange = {},
        onSave = {},
        onDismiss = {},
    )
}

@Preview
@Composable
private fun HistoryEntryLightPreview() = ComponentPreview(darkTheme = false) {
    HistoryEntryPreviewContent()
}

@Preview
@Composable
private fun HistoryEntryDarkPreview() = ComponentPreview(darkTheme = true) {
    HistoryEntryPreviewContent()
}
