package ru.kuznetsov.itstime4service.ui.components.fields

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.unit_km
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Numeric input (mono digits) — mileage, intervals, reminder window. Optional trailing [unit] label (e.g. "km"). */
@Composable
fun AppNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    unit: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = ItsTime4ServiceTheme.colors
    FieldBox(
        value = value,
        onValueChange = onValueChange,
        textStyle = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 13.sp),
        shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 12.dp),
        modifier = modifier,
        placeholder = placeholder,
        keyboardType = KeyboardType.Number,
        enabled = enabled,
        isError = isError,
        visualTransformation = visualTransformation,
        trailing = unit?.let {
            {
                Text(
                    it,
                    style = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 12.sp),
                    color = colors.inkFaint,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        },
    )
}

@Preview
@Composable
private fun AppNumberFieldLightPreview() = ComponentPreview(darkTheme = false) {
    AppNumberField(value = "84300", onValueChange = {}, unit = stringResource(Res.string.unit_km))
}

@Preview
@Composable
private fun AppNumberFieldDarkPreview() = ComponentPreview(darkTheme = true) {
    AppNumberField(value = "84300", onValueChange = {}, unit = stringResource(Res.string.unit_km))
}
