package ru.kuznetsov.pitstop.ui.components.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.field_interval_km
import ru.kuznetsov.pitstop.ui.components.ComponentPreview
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

/** Compact labeled field used in paired rows inside a maintenance-task block (interval/km, last-done date, etc). */
@Composable
fun LabeledMiniField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    required: Boolean = false,
    minLines: Int = 1,
) {
    val colors = PitStopTheme.colors
    Column(modifier = modifier) {
        Text(
            buildAnnotatedString {
                append(label)
                if (required) {
                    withStyle(SpanStyle(color = colors.tierDue)) { append(" *") }
                }
            },
            style = PitStopTheme.typography.sans.copy(fontSize = 10.5.sp),
            color = colors.inkFaint,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        FieldBox(
            value = value,
            onValueChange = onValueChange,
            textStyle = (if (keyboardType == KeyboardType.Number) PitStopTheme.typography.mono else PitStopTheme.typography.sans)
                .copy(fontSize = 13.sp),
            shape = RoundedCornerShape(PitStopTheme.dimens.radiusM),
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 12.dp),
            placeholder = placeholder,
            keyboardType = keyboardType,
            isError = isError,
            minLines = minLines,
        )
    }
}

@Preview
@Composable
private fun LabeledMiniFieldLightPreview() = ComponentPreview(darkTheme = false) {
    Row {
        LabeledMiniField(
            label = stringResource(Res.string.field_interval_km),
            value = "10000",
            onValueChange = {},
            keyboardType = KeyboardType.Number,
        )
    }
}

@Preview
@Composable
private fun LabeledMiniFieldDarkPreview() = ComponentPreview(darkTheme = true) {
    Row {
        LabeledMiniField(
            label = stringResource(Res.string.field_interval_km),
            value = "10000",
            onValueChange = {},
            keyboardType = KeyboardType.Number,
        )
    }
}
