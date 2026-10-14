package ru.kuznetsov.pitstop.ui.components.fields

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
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.unit_km
import ru.kuznetsov.pitstop.ui.components.ComponentPreview
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

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
    val colors = PitStopTheme.colors
    FieldBox(
        value = value,
        onValueChange = onValueChange,
        textStyle = PitStopTheme.typography.mono.copy(fontSize = 13.sp),
        shape = RoundedCornerShape(PitStopTheme.dimens.radiusM),
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
                    style = PitStopTheme.typography.mono.copy(fontSize = 12.sp),
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
