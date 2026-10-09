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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

/**
 * Shared chrome (surface2 fill, 1dp rule border, placeholder handling) behind every field
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
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = PitStopTheme.colors
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surface2)
            .border(1.dp, colors.rule, shape)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, style = textStyle, color = colors.inkFaint)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                textStyle = textStyle.copy(color = colors.ink),
                singleLine = true,
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        }
        if (trailing != null) trailing()
    }
}
