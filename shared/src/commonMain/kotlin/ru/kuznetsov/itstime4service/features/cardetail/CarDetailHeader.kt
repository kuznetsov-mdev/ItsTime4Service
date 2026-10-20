package ru.kuznetsov.itstime4service.features.cardetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_edit_name
import itstime4service.shared.generated.resources.action_edit_plate
import itstime4service.shared.generated.resources.action_edit_vin
import itstime4service.shared.generated.resources.action_hide_plate
import itstime4service.shared.generated.resources.action_hide_vin
import itstime4service.shared.generated.resources.action_show_plate
import itstime4service.shared.generated.resources.action_show_vin
import itstime4service.shared.generated.resources.plate_placeholder
import itstime4service.shared.generated.resources.plate_prefix
import itstime4service.shared.generated.resources.vin_add
import itstime4service.shared.generated.resources.vin_hidden
import itstime4service.shared.generated.resources.vin_placeholder
import itstime4service.shared.generated.resources.vin_prefix
import ru.kuznetsov.itstime4service.ui.components.buttons.SecretToggle
import ru.kuznetsov.itstime4service.ui.components.fields.InlineEditField
import ru.kuznetsov.itstime4service.ui.components.indicators.FlippablePlate
import ru.kuznetsov.itstime4service.ui.components.indicators.color
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
internal fun CarHeader(
    car: CarDetails,
    editingField: EditableField?,
    editBounds: Modifier,
    onEdit: (EditableField) -> Unit,
    onSaveBrand: (String) -> Unit,
    onSaveVin: (String) -> Unit,
    onSavePlate: (String) -> Unit,
    onPlateHiddenToggle: () -> Unit,
    onVinHiddenToggle: () -> Unit,
) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 11.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (editingField == EditableField.BRAND) {
            InlineEditField(
                initialValue = car.brand,
                onSave = onSaveBrand,
                keyboardType = KeyboardType.Text,
                textStyle = typography.display.copy(fontSize = 15.sp),
                canSave = { it.isNotBlank() },
                modifier = editBounds.fillMaxWidth(),
            )
        } else {
            Text(
                car.brand,
                style = typography.display.copy(fontSize = 20.sp, lineHeight = 26.sp),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(
                        onClickLabel = stringResource(Res.string.action_edit_name),
                        onClick = { onEdit(EditableField.BRAND) },
                    )
                    .dashedUnderline(colors.inkFaint),
            )
        }
        val idStyle = typography.mono.copy(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal)
        when {
            editingField == EditableField.PLATE -> InlineEditField(
                initialValue = car.plate.orEmpty(),
                onSave = onSavePlate,
                keyboardType = KeyboardType.Text,
                placeholder = stringResource(Res.string.plate_placeholder),
                transform = { it.uppercase().take(PLATE_LENGTH) },
                modifier = editBounds.fillMaxWidth(),
            )
            car.plateLook != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FlippablePlate(
                    look = car.plateLook,
                    hidden = car.plateHidden,
                    modifier = if (car.plateHidden) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            onClickLabel = stringResource(Res.string.action_edit_plate),
                            onClick = { onEdit(EditableField.PLATE) },
                        )
                    },
                )
                SecretToggle(
                    hidden = car.plateHidden,
                    label = stringResource(if (car.plateHidden) Res.string.action_show_plate else Res.string.action_hide_plate),
                    onToggle = onPlateHiddenToggle,
                )
            }
            else -> Row(
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(Res.string.action_edit_plate),
                    onClick = { onEdit(EditableField.PLATE) },
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(Res.string.plate_prefix), style = idStyle, color = colors.inkFaint)
                Text(
                    stringResource(Res.string.vin_add),
                    style = idStyle,
                    color = colors.inkFaint,
                    modifier = Modifier.dashedUnderline(colors.inkFaint),
                )
            }
        }
        if (editingField == EditableField.VIN) {
            InlineEditField(
                initialValue = car.vin.orEmpty(),
                onSave = onSaveVin,
                keyboardType = KeyboardType.Text,
                placeholder = stringResource(Res.string.vin_placeholder),
                transform = { it.uppercase().take(VIN_LENGTH) },
                modifier = editBounds.fillMaxWidth(),
            )
        } else {
            val vinStyle = typography.mono.copy(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal)
            val vinHidden = car.vin != null && car.vinHidden
            val hiddenDescription = stringResource(Res.string.vin_hidden)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = if (vinHidden) {
                        Modifier.clearAndSetSemantics { contentDescription = hiddenDescription }
                    } else {
                        Modifier.clickable(
                            onClickLabel = stringResource(Res.string.action_edit_vin),
                            onClick = { onEdit(EditableField.VIN) },
                        )
                    },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(Res.string.vin_prefix), style = vinStyle, color = colors.inkFaint)
                    Text(
                        when {
                            car.vin == null -> stringResource(Res.string.vin_add)
                            vinHidden -> HIDDEN_CHAR.toString().repeat(car.vin.length)
                            else -> car.vin
                        },
                        style = vinStyle,
                        color = colors.inkFaint,
                        modifier = if (vinHidden) Modifier else Modifier.dashedUnderline(colors.inkFaint),
                    )
                }
                if (car.vin != null) {
                    SecretToggle(
                        hidden = vinHidden,
                        label = stringResource(if (vinHidden) Res.string.action_show_vin else Res.string.action_hide_vin),
                        onToggle = onVinHiddenToggle,
                    )
                }
            }
        }
    }
}

private const val VIN_LENGTH = 17
private const val PLATE_LENGTH = 12
private const val HIDDEN_CHAR = 'x'
