package ru.kuznetsov.pitstop.ui.components.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

/**
 * Shared chrome (surface2 fill, 1dp rule border — red when the field is in error — and placeholder handling) behind every field
 * component — [ru.kuznetsov.pitstop.ui.components.fields.AppTextField],
 * [ru.kuznetsov.pitstop.ui.components.fields.AppNumberField], [LabeledMiniField] and
 * [InlineEditField] all draw through this so the box styling stays in one place.
 */
@Composable
internal fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    shape: Shape,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    isError: Boolean = false,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = PitStopTheme.colors
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surface2)
            .border(1.dp, if (isError) colors.tierDue else colors.rule, shape)
            .padding(contentPadding),
        verticalAlignment = if (minLines > 1) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, style = textStyle, color = colors.inkFaint)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                enabled = enabled,
                textStyle = textStyle.copy(color = colors.ink),
                singleLine = minLines == 1,
                minLines = minLines,
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = visualTransformation,
            )
        }
        if (trailing != null) trailing()
    }
}
